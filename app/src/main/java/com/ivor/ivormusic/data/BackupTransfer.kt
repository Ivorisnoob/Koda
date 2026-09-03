package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The whole-install backup file: what is in it, and how it is read and written.
 *
 * **This half is deliberately format only.** Nothing here touches a Context,
 * a preference file or the filesystem - [BackupRepository] collects a
 * [BackupSnapshot] off the device and applies one back onto it, and this
 * object turns that snapshot into bytes and back. The same split
 * [SubscriptionTransfer] uses, and for the same reason: a format that can be
 * exercised without a device is a format whose edge cases can actually be
 * reasoned about.
 *
 * **It is a zip rather than one JSON document**, for two reasons that both
 * matter. Playlist covers are binary - a chosen one is a photo the user
 * cropped and is genuinely irreplaceable - and base64 in JSON inflates them by
 * a third while making the file unreadable to anything else. And the text side
 * compresses hard: a full play history is five thousand entries, and deflate
 * takes a typical backup from megabytes to tens of kilobytes. A zip also means
 * the file can be opened by any file manager, which is the honest thing for
 * something a user is expected to keep for a year.
 *
 * **The format is versioned from the first release** ([FORMAT_VERSION]),
 * because a backup taken today has to restore into next year's build. The rule
 * is: a reader accepts any [formatVersion] at or below its own and ignores
 * entries it does not recognise, so adding a store later costs nothing; a
 * *breaking* change bumps the version and old readers refuse the file with a
 * message instead of restoring half of it.
 *
 * ## Layout
 *
 * ```
 * manifest.json       what this file is, when it was made, and what is in it
 * preferences.json    device-wide preference files, key by key, typed
 * profiles.json       the profile roster - never the cookies (see below)
 * profile-data.json   the per-profile stores, keyed by profile id
 * files/...           verbatim copies of the filesDir entries in scope
 * ```
 *
 * ## The profile split is carried structurally, not as raw keys
 *
 * Local subscriptions and the not-recommended blocklist are keyed per profile
 * through [ProfileManager.profileScopedKey], while playlists, liked songs,
 * stats and theme are device-wide. A restore that flattened that would put one
 * account's blocklist onto another.
 *
 * Dumping the raw scoped keys would not survive the trip either, because the
 * suffix is a device-local UUID and the profile migrated from a pre-profiles
 * install deliberately keeps the key *un-suffixed*. Restoring
 * `subscriptions` verbatim onto another device would hand it to whichever
 * profile happened to be that device's legacy one. So [BackupProfileData] is
 * keyed by backup profile id, and the restore recomputes the real key with
 * that device's own legacy id once it knows which local profile each backup
 * profile maps to.
 *
 * ## Cookies are never in the file
 *
 * A session cookie string is a credential for a Google account. A backup is
 * something people keep in Drive and mail to themselves, so [BackupProfile]
 * carries the identity and not the session, and a restored YouTube profile
 * comes back through the existing [Profile.expired] badge asking to be signed
 * into again. Everything else about it - its id, and therefore its
 * subscriptions and blocklist - survives intact.
 */
object BackupTransfer {

    private const val TAG = "BackupTransfer"

    /** Marks the file as ours before anything else is trusted. */
    const val FORMAT = "koda-backup"

    /**
     * Bump only for a change a previous reader would get *wrong*. Adding a
     * preference file, a stored file or a whole new section does not qualify:
     * readers skip what they do not know, so those are free.
     */
    const val FORMAT_VERSION = 1

    const val MIME_TYPE = "application/zip"

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_PREFERENCES = "preferences.json"
    private const val ENTRY_PROFILES = "profiles.json"
    private const val ENTRY_PROFILE_DATA = "profile-data.json"
    private const val FILES_PREFIX = "files/"

    /**
     * Ceiling on a single unpacked entry, and on the unpacked total.
     *
     * A backup is read from a file the user picked, which is not the same as a
     * file this app wrote - a hostile or corrupt zip can otherwise claim a
     * compression ratio that exhausts memory before anything gets a chance to
     * validate it. Generous against real data: the largest thing in a real
     * backup is a play history of five thousand entries, well under a
     * megabyte, plus a cover per playlist.
     */
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    // ---------------- Writing ----------------

    /**
     * Writes [snapshot] to [out] as a backup zip.
     *
     * Closes [out] on the way, since the deflater has to be finished before
     * the bytes are complete and there is no useful state left afterwards.
     * Callers hand it a stream they opened inside a `use` anyway.
     */
    fun write(snapshot: BackupSnapshot, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putText(ENTRY_MANIFEST, snapshot.manifest.toJson().toString(2))
            zip.putText(ENTRY_PREFERENCES, preferencesToJson(snapshot.preferences).toString())
            zip.putText(ENTRY_PROFILES, profilesToJson(snapshot).toString())
            zip.putText(ENTRY_PROFILE_DATA, profileDataToJson(snapshot.profileData).toString())
            for (file in snapshot.files) {
                zip.putEntry(FILES_PREFIX + file.path, file.bytes)
            }
            // Flush the deflater before the caller's stream is closed under it.
            zip.finish()
        }
    }

    // ---------------- Reading ----------------

    /**
     * Reads a backup out of [input].
     *
     * Nothing is applied here - the whole file is parsed into memory so the
     * caller can show what is in it and then refuse or proceed. A file that is
     * not a zip yields no entries and therefore no manifest, and a zip with no
     * manifest is not ours; both come back null. A file that *is* a Koda
     * backup this build is too old to read throws
     * [UnsupportedBackupException] instead, because "we cannot read this yet"
     * and "this is not a backup" are different things to tell someone.
     *
     * The extension is never consulted. Providers lie about both it and the
     * MIME type, which is the rule [SubscriptionTransfer] already follows.
     */
    fun read(input: InputStream): BackupSnapshot? {
        var manifest: BackupManifest? = null
        var preferences: Map<String, Map<String, PreferenceValue>> = emptyMap()
        var profiles: List<BackupProfile> = emptyList()
        var activeProfileId: String? = null
        var profileData: Map<String, BackupProfileData> = emptyMap()
        val files = mutableListOf<BackupFile>()
        var total = 0L

        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry: ZipEntry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    val bytes = zip.readEntry(remaining = MAX_TOTAL_BYTES - total) ?: return null
                    total += bytes.size
                    when {
                        name == ENTRY_MANIFEST ->
                            manifest = parseManifest(JSONObject(bytes.toString(Charsets.UTF_8)))

                        name == ENTRY_PREFERENCES ->
                            preferences = parsePreferences(JSONObject(bytes.toString(Charsets.UTF_8)))

                        name == ENTRY_PROFILES -> {
                            val obj = JSONObject(bytes.toString(Charsets.UTF_8))
                            profiles = parseProfiles(obj)
                            activeProfileId = obj.optString("activeProfileId")
                                .takeIf { it.isNotBlank() }
                        }

                        name == ENTRY_PROFILE_DATA ->
                            profileData = parseProfileData(JSONObject(bytes.toString(Charsets.UTF_8)))

                        name.startsWith(FILES_PREFIX) -> {
                            // Entry names are never trusted to build a path -
                            // the same rule SubscriptionTransfer's archive
                            // reader follows. A traversal attempt is dropped,
                            // not sanitised into something plausible.
                            val path = name.removePrefix(FILES_PREFIX)
                            if (isSafeRelativePath(path)) files.add(BackupFile(path, bytes))
                        }
                        // Anything else is from a newer writer. Skipped, not fatal.
                    }
                }
            }
        } catch (e: UnsupportedBackupException) {
            throw e
        } catch (e: Exception) {
            KLog.w(TAG, "Could not read that backup", e)
            return null
        }

        val resolved = manifest ?: return null
        return BackupSnapshot(
            manifest = resolved,
            preferences = preferences,
            profiles = profiles,
            activeProfileId = activeProfileId,
            profileData = profileData,
            files = files
        )
    }

    private fun parseManifest(obj: JSONObject): BackupManifest {
        if (obj.optString("format") != FORMAT) {
            throw UnsupportedBackupException("That file is not a Koda backup.")
        }
        val version = obj.optInt("formatVersion", 0)
        if (version > FORMAT_VERSION) {
            throw UnsupportedBackupException(
                "That backup was written by a newer version of Koda. Update the app and try again."
            )
        }
        return BackupManifest(
            formatVersion = version,
            appVersionName = obj.optString("appVersionName").takeIf { it.isNotBlank() },
            appVersionCode = obj.optInt("appVersionCode", 0),
            createdAt = obj.optLong("createdAt", 0L),
            device = obj.optString("device").takeIf { it.isNotBlank() },
            contents = obj.optJSONObject("contents")?.let { counts ->
                counts.keys().asSequence().associateWith { counts.optInt(it, 0) }
            } ?: emptyMap()
        )
    }

    // ---------------- Preferences ----------------

    /**
     * Preferences are stored typed rather than stringified.
     *
     * A preference file mixes booleans, ints, longs, floats, strings and
     * string sets, and `SharedPreferences` throws [ClassCastException] on a
     * read whose type does not match what was written - not on the write. So a
     * backup that flattened everything to strings would restore cleanly and
     * then crash the first screen that read a boolean back, a long way from
     * the cause. Carrying the type is one character per entry.
     */
    private fun preferencesToJson(
        preferences: Map<String, Map<String, PreferenceValue>>
    ): JSONObject = JSONObject().apply {
        preferences.forEach { (fileName, entries) ->
            put(fileName, JSONObject().apply {
                entries.forEach { (key, value) ->
                    put(key, JSONObject().apply {
                        put("t", value.tag)
                        when (value) {
                            is PreferenceValue.StringSet ->
                                put("v", JSONArray().also { array ->
                                    value.value.forEach(array::put)
                                })

                            else -> put("v", value.raw)
                        }
                    })
                }
            })
        }
    }

    private fun parsePreferences(obj: JSONObject): Map<String, Map<String, PreferenceValue>> =
        obj.keys().asSequence().associateWith { fileName ->
            val entries = obj.optJSONObject(fileName) ?: return@associateWith emptyMap()
            entries.keys().asSequence().mapNotNull { key ->
                val entry = entries.optJSONObject(key) ?: return@mapNotNull null
                val value = when (entry.optString("t")) {
                    PreferenceValue.TAG_STRING ->
                        PreferenceValue.Text(entry.optString("v"))

                    PreferenceValue.TAG_INT ->
                        PreferenceValue.Integer(entry.optInt("v"))

                    PreferenceValue.TAG_LONG ->
                        PreferenceValue.LongNumber(entry.optLong("v"))

                    PreferenceValue.TAG_FLOAT ->
                        PreferenceValue.FloatNumber(entry.optDouble("v").toFloat())

                    PreferenceValue.TAG_BOOLEAN ->
                        PreferenceValue.Flag(entry.optBoolean("v"))

                    PreferenceValue.TAG_STRING_SET -> {
                        val array = entry.optJSONArray("v")
                        PreferenceValue.StringSet(
                            (0 until (array?.length() ?: 0)).mapNotNull { i ->
                                array?.optString(i)
                            }.toSet()
                        )
                    }

                    else -> return@mapNotNull null
                }
                key to value
            }.toMap()
        }

    // ---------------- Profiles ----------------

    private fun profilesToJson(snapshot: BackupSnapshot): JSONObject = JSONObject().apply {
        put("activeProfileId", snapshot.activeProfileId ?: JSONObject.NULL)
        put("profiles", JSONArray().also { array ->
            snapshot.profiles.forEach { profile ->
                array.put(JSONObject().apply {
                    put("id", profile.id)
                    put("kind", profile.kind)
                    put("name", profile.name)
                    put("handle", profile.handle ?: JSONObject.NULL)
                    put("avatarUrl", profile.avatarUrl ?: JSONObject.NULL)
                    put("datasyncId", profile.datasyncId ?: JSONObject.NULL)
                    put("addedAt", profile.addedAt)
                })
            }
        })
    }

    private fun parseProfiles(obj: JSONObject): List<BackupProfile> {
        val array = obj.optJSONArray("profiles") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val id = entry.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            fun str(key: String) =
                entry.optString(key).takeIf { it.isNotBlank() && it != "null" }
            BackupProfile(
                id = id,
                kind = str("kind") ?: ProfileKind.LOCAL.name,
                name = str("name") ?: "Profile",
                handle = str("handle"),
                avatarUrl = str("avatarUrl"),
                datasyncId = str("datasyncId"),
                addedAt = entry.optLong("addedAt", 0L)
            )
        }
    }

    private fun profileDataToJson(data: Map<String, BackupProfileData>): JSONObject =
        JSONObject().apply {
            data.forEach { (profileId, entry) ->
                put(profileId, JSONObject().apply {
                    put("subscriptions", entry.subscriptions ?: JSONObject.NULL)
                    put("subscriptionGroups", entry.subscriptionGroups ?: JSONObject.NULL)
                    put("hiddenVideos", entry.hiddenVideos ?: JSONObject.NULL)
                    put("blockedChannels", entry.blockedChannels ?: JSONObject.NULL)
                    put("watchHistory", entry.watchHistory ?: JSONObject.NULL)
                    put(
                        "removedFromHistory",
                        entry.removedFromHistory?.let { JSONArray(it.toList()) } ?: JSONObject.NULL
                    )
                })
            }
        }

    private fun parseProfileData(obj: JSONObject): Map<String, BackupProfileData> =
        obj.keys().asSequence().mapNotNull { profileId ->
            val entry = obj.optJSONObject(profileId) ?: return@mapNotNull null
            fun str(key: String) =
                entry.optString(key).takeIf { it.isNotBlank() && it != "null" }
            profileId to BackupProfileData(
                subscriptions = str("subscriptions"),
                subscriptionGroups = str("subscriptionGroups"),
                hiddenVideos = str("hiddenVideos"),
                blockedChannels = str("blockedChannels"),
                watchHistory = str("watchHistory"),
                // Absent in files written before history became per-profile,
                // which is the normal case for an older backup rather than an
                // error: it simply restores with nothing removed.
                removedFromHistory = entry.optJSONArray("removedFromHistory")?.let { array ->
                    (0 until array.length())
                        .mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                        .toSet()
                }
            )
        }.toMap()

    // ---------------- Zip plumbing ----------------

    private fun ZipOutputStream.putText(name: String, text: String) {
        putEntry(name, text.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    /**
     * Reads the current entry, refusing anything past the budget rather than
     * trusting the header's declared size, which a zip is free to lie about.
     */
    private fun ZipInputStream.readEntry(remaining: Long): ByteArray? {
        val cap = minOf(remaining, MAX_ENTRY_BYTES)
        if (cap <= 0) return null
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var read = read(buffer)
        while (read > 0) {
            if (out.size() + read > cap) return null
            out.write(buffer, 0, read)
            read = read(buffer)
        }
        return out.toByteArray()
    }

    /**
     * A relative path with no traversal, no absolute root and no drive letter.
     * Restore resolves these under `filesDir`, so anything that could climb out
     * of it is dropped.
     */
    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("/") || path.startsWith("\\") || path.contains(':')) return false
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return false
        return true
    }
}

/** A backup this build cannot read, with the reason to show the user. */
class UnsupportedBackupException(message: String) : Exception(message)

/** Everything one backup file holds. */
data class BackupSnapshot(
    val manifest: BackupManifest,
    /** Device-wide preference files: file name to its typed contents. */
    val preferences: Map<String, Map<String, PreferenceValue>> = emptyMap(),
    val profiles: List<BackupProfile> = emptyList(),
    /** Which profile the backed-up install was on. */
    val activeProfileId: String? = null,
    /** Profile-scoped stores, keyed by the *backup's* profile id. */
    val profileData: Map<String, BackupProfileData> = emptyMap(),
    /** Verbatim files, at paths relative to `filesDir`. */
    val files: List<BackupFile> = emptyList()
)

/**
 * What the file says about itself.
 *
 * [contents] is a plain name-to-count map rather than typed fields, so the
 * restore preview can describe a backup written by a build that knew about a
 * store this one does not.
 */
data class BackupManifest(
    val formatVersion: Int,
    val appVersionName: String?,
    val appVersionCode: Int,
    val createdAt: Long,
    val device: String?,
    val contents: Map<String, Int> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", BackupTransfer.FORMAT)
        put("formatVersion", formatVersion)
        put("appVersionName", appVersionName ?: JSONObject.NULL)
        put("appVersionCode", appVersionCode)
        put("createdAt", createdAt)
        put("device", device ?: JSONObject.NULL)
        put("contents", JSONObject().also { counts ->
            contents.forEach { (key, value) -> counts.put(key, value) }
        })
    }
}

/**
 * One identity, without its session.
 *
 * [kind] is the raw enum name rather than [ProfileKind] so an unknown kind
 * from a newer build degrades to local instead of throwing.
 */
data class BackupProfile(
    val id: String,
    val kind: String,
    val name: String,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val datasyncId: String? = null,
    val addedAt: Long = 0L
)

/**
 * The stores that belong to one identity, held as the JSON strings the
 * repositories themselves write.
 *
 * Opaque on purpose: re-encoding [LocalSubscription] and [BlockedChannel]
 * here would be a second copy of two parsers that already exist, and the one
 * thing a backup must never do is lose a field because a mirror went stale.
 */
data class BackupProfileData(
    val subscriptions: String? = null,
    val subscriptionGroups: String? = null,
    val hiddenVideos: String? = null,
    val blockedChannels: String? = null,
    /** This profile's watch history, which is per-profile like the two above. */
    val watchHistory: String? = null,
    /** Ids this profile removed from its history, so a restore keeps them out. */
    val removedFromHistory: Set<String>? = null
) {
    val isEmpty: Boolean
        get() = subscriptions == null && subscriptionGroups == null &&
            hiddenVideos == null && blockedChannels == null &&
            watchHistory == null && removedFromHistory == null
}

/** A file copied verbatim, at a path relative to `filesDir`. */
data class BackupFile(val path: String, val bytes: ByteArray) {
    // Data classes over ByteArray compare by identity, which would make two
    // identical files unequal. Nothing relies on it today; overriding is
    // cheaper than the surprise later.
    override fun equals(other: Any?): Boolean =
        this === other || (other is BackupFile && path == other.path && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * path.hashCode() + bytes.contentHashCode()
}

/** A preference value that remembers what type it was stored as. */
sealed class PreferenceValue {
    abstract val tag: String
    abstract val raw: Any

    data class Text(val value: String) : PreferenceValue() {
        override val tag = TAG_STRING
        override val raw: Any get() = value
    }

    data class Integer(val value: Int) : PreferenceValue() {
        override val tag = TAG_INT
        override val raw: Any get() = value
    }

    data class LongNumber(val value: Long) : PreferenceValue() {
        override val tag = TAG_LONG
        override val raw: Any get() = value
    }

    data class FloatNumber(val value: Float) : PreferenceValue() {
        override val tag = TAG_FLOAT
        override val raw: Any get() = value
    }

    data class Flag(val value: Boolean) : PreferenceValue() {
        override val tag = TAG_BOOLEAN
        override val raw: Any get() = value
    }

    data class StringSet(val value: Set<String>) : PreferenceValue() {
        override val tag = TAG_STRING_SET
        override val raw: Any get() = value
    }

    companion object {
        const val TAG_STRING = "s"
        const val TAG_INT = "i"
        const val TAG_LONG = "l"
        const val TAG_FLOAT = "f"
        const val TAG_BOOLEAN = "b"
        const val TAG_STRING_SET = "ss"

        /** Wraps whatever `SharedPreferences.getAll()` handed back, or null. */
        fun of(value: Any?): PreferenceValue? = when (value) {
            is String -> Text(value)
            is Int -> Integer(value)
            is Long -> LongNumber(value)
            is Float -> FloatNumber(value)
            is Boolean -> Flag(value)
            is Set<*> -> StringSet(value.filterIsInstance<String>().toSet())
            else -> null
        }
    }
}
