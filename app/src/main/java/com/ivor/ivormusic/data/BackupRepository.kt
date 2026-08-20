package com.ivor.ivormusic.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.ivor.ivormusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects this install into a backup, and puts one back.
 *
 * The device half of [BackupTransfer]. Everything about *what* is in a backup
 * lives here as two allowlists - preference files and stored files - because
 * the alternative, a hand-written serializer per store, is the same failure
 * `buildSettingsSearchIndex` has: a new setting is added, nobody remembers the
 * second place, and the omission is silent until someone restores a backup and
 * finds it missing. A preference file is copied key by key, so **every setting
 * added from now on rides along for free**, including ones nobody thought
 * about here.
 *
 * The cost of that choice is that the exclusions have to be deliberate, and
 * each one below is a thing that would be actively wrong to carry rather than
 * merely useless.
 *
 * ## Restore replaces; it does not merge
 *
 * A backup exists to move an install or recover one, so applying one makes
 * this install *become* that one: every store in the allowlist is cleared
 * before the backup's copy is written. Merging would leave the result
 * depending on what happened to be here already, and there is no honest merge
 * rule for a play history or a blocklist anyway.
 *
 * **The profile roster is the one exception, and it is merged.** A profile
 * already on this device keeps its cookies, because destroying a live signed-in
 * session as a side effect of a restore is a much worse outcome than a
 * duplicate row - and there is nothing in the file to replace it with, since
 * cookies are never backed up.
 *
 * ## Applying a backup ends with a process restart
 *
 * There is no DI here, so a restore cannot reach the state it has just
 * invalidated: every ViewModel holds its own [ThemePreferences] whose
 * StateFlows do not cross instances, five stores hold process-wide companion
 * caches seeded once, `MusicService` holds a repository and a media session of
 * its own, and `SharedPreferences` itself serves reads from an in-memory map
 * that a raw file write would not disturb. Hot-reloading all of that is a
 * long list with no compiler behind it and one omission means a restored
 * install that is half the old one. Restarting is one line and correct by
 * construction. Writes therefore use `commit()`, not `apply()`, because the
 * process is about to be killed.
 */
class BackupRepository(context: Context) {

    private val appContext = context.applicationContext

    // ---------------- What is in a backup ----------------

    companion object {
        private const val TAG = "BackupRepository"

        /** Where the last-backup timestamp lives, for the Settings hub row. */
        private const val STATE_PREFS = "koda_backup_state"
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"

        /**
         * Device-wide preference files carried verbatim, key by key.
         *
         * Deliberately absent, each for its own reason:
         *  - `yt_music_session` holds the session cookies and is a credential;
         *    the roster inside it is carried separately, without them.
         *  - `ivor_visitor_data` is the anti-bot identity. [AccountSwitcher]
         *    already documents that a stale or shared one gets flagged
         *    `LOGIN_REQUIRED`, and replaying one device's onto another is
         *    exactly that. It re-mints on first use.
         *  - `koda_download_migration` is a one-time "already migrated" flag.
         *    Restoring a true onto an install that never ran the migration
         *    would skip it permanently.
         *  - `local_subscriptions` and `not_interested` are profile-scoped and
         *    travel structurally instead (see [collectProfileData]).
         */
        private val PREFERENCE_FILES = listOf(
            "ivor_music_theme_prefs",   // every setting, palette and player style
            "ivor_music_liked_songs",   // liked song ids
            "saved_playlists",          // playlists kept as references, both modes
            "video_history",            // local watch history
            "search_history",
            "ivor_track_loudness"       // measured per-track gain
        )

        /**
         * Keys skipped inside an otherwise-copied preference file.
         *
         * The last-played snapshot describes a playback session rather than a
         * preference. Restoring one points the resume UI at a song this
         * install has no queue for, on a device that was never playing it.
         */
        private val EXCLUDED_PREFERENCE_KEYS: Map<String, Set<String>> = mapOf(
            "ivor_music_theme_prefs" to setOf(
                "last_song_id",
                "last_song_title",
                "last_song_artist",
                "last_song_album",
                "last_song_artwork",
                "last_song_duration"
            )
        )

        /**
         * Directories under `filesDir` copied whole.
         *
         * `playlist_covers` is the reason the file is a zip: a chosen cover is
         * a photo the user cropped, and unlike a generated one there is
         * nothing to regenerate it from.
         */
        private val BACKED_UP_DIRECTORIES = listOf(
            "playlists",        // local music playlists, songs embedded
            "playlist_covers",  // generated and chosen artwork
            "video_playlists"   // local video playlists, videos embedded
        )

        /**
         * Individual files under `filesDir`.
         *
         * `audio_profiles.json` and the loudness preferences are measurements
         * of the audio itself, not of this device: they cost a download and an
         * analysis pass to reacquire, and they are correct anywhere.
         *
         * Deliberately absent: `playback_session.json` and
         * `video_playback_session.json`, which are where you were in a queue
         * and belong to a session; and `downloaded_songs_metadata.json` /
         * `downloaded_videos_metadata.json`, because the media they point at
         * is not in the file and a restored index of downloads that are not
         * there is worse than no index.
         */
        private val BACKED_UP_FILES = listOf(
            "liked_songs_meta.json",  // liked song metadata, so likes survive signed out
            "play_history.json",      // listening stats
            "audio_profiles.json"     // measured envelopes, for AutoMix
        )

        // The three allowlists above restate names that are private constants
        // in the stores that own them, and no compiler checks that. Renaming a
        // preference file or a stored file therefore silently drops it from
        // every backup taken afterwards, with no error anywhere - the same
        // failure mode buildSettingsSearchIndex has. If you rename one, rename
        // it here. Worth the trade: opening nine private constants up so this
        // file could reference them would put the coupling in nine places
        // instead of one.

        /** Names used by the restore preview and the manifest. */
        const val COUNT_PLAYLISTS = "playlists"
        const val COUNT_VIDEO_PLAYLISTS = "videoPlaylists"
        const val COUNT_SAVED_PLAYLISTS = "savedPlaylists"
        const val COUNT_LIKED_SONGS = "likedSongs"
        const val COUNT_PLAY_HISTORY = "playHistory"
        const val COUNT_WATCH_HISTORY = "watchHistory"
        const val COUNT_SUBSCRIPTIONS = "localSubscriptions"
        const val COUNT_BLOCKED = "blockedChannels"
        const val COUNT_HIDDEN = "hiddenVideos"
        const val COUNT_PROFILES = "profiles"
        const val COUNT_SETTINGS = "settings"

        /**
         * A name that sorts and reads well in a file manager a year from now,
         * which is where these are actually found again.
         */
        fun suggestedFileName(): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
            return "koda-backup-$stamp.zip"
        }

        /**
         * When this install last wrote a backup, or null.
         *
         * A static read rather than a flow: the Settings hub row asks once as
         * it composes, and returning from the backup screen recomposes it.
         */
        fun lastBackupAt(context: Context): Long? =
            context.applicationContext
                .getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_BACKUP_AT, 0L)
                .takeIf { it > 0L }
    }

    private val filesDir: File get() = appContext.filesDir

    // ---------------- Collecting ----------------

    /** Everything this install would put in a backup. */
    suspend fun collect(): BackupSnapshot = withContext(Dispatchers.IO) {
        val preferences = collectPreferences()
        val profileManager = ProfileManager(appContext)
        val profiles = profileManager.profiles.value
        val profileData = collectProfileData(profiles)
        val files = collectFiles()

        BackupSnapshot(
            manifest = BackupManifest(
                formatVersion = BackupTransfer.FORMAT_VERSION,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                createdAt = System.currentTimeMillis(),
                device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                contents = countContents(preferences, profiles, profileData, files)
            ),
            preferences = preferences,
            profiles = profiles.map { profile ->
                BackupProfile(
                    id = profile.id,
                    kind = profile.kind.name,
                    name = profile.name,
                    handle = profile.handle,
                    avatarUrl = profile.avatarUrl,
                    datasyncId = profile.datasyncId,
                    addedAt = profile.addedAt
                )
            },
            activeProfileId = profileManager.activeProfileId.value.takeIf { it.isNotBlank() },
            profileData = profileData,
            files = files
        )
    }

    /**
     * Writes a backup of this install to [target].
     *
     * @return the manifest that was written, or null when the file could not
     * be written at all.
     */
    suspend fun writeTo(target: Uri): BackupManifest? = withContext(Dispatchers.IO) {
        try {
            val snapshot = collect()
            appContext.contentResolver.openOutputStream(target)?.use { out ->
                BackupTransfer.write(snapshot, out)
            } ?: return@withContext null
            appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_BACKUP_AT, snapshot.manifest.createdAt).apply()
            snapshot.manifest
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            null
        }
    }

    private fun collectPreferences(): Map<String, Map<String, PreferenceValue>> =
        PREFERENCE_FILES.associateWith { fileName ->
            val excluded = EXCLUDED_PREFERENCE_KEYS[fileName].orEmpty()
            val prefs = appContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            prefs.all.mapNotNull { (key, value) ->
                if (key in excluded) return@mapNotNull null
                // A profile-scoped key would only land here if a store were
                // added to PREFERENCE_FILES by mistake; carrying it raw is the
                // flattening this whole structure exists to avoid.
                PreferenceValue.of(value)?.let { key to it }
            }.toMap()
        }.filterValues { it.isNotEmpty() }

    /**
     * The profile-scoped stores, read for every profile in the roster rather
     * than only the active one.
     *
     * [ProfileManager.profileScopedKey] is a pure function of the profile id,
     * so the keys for a profile that is not currently active can be computed
     * without switching to it - which is the only reason a backup can carry
     * more than one identity's subscriptions.
     */
    private fun collectProfileData(profiles: List<Profile>): Map<String, BackupProfileData> {
        val legacyId = ProfileManager.legacyProfileId(appContext)
        val subs = appContext.getSharedPreferences("local_subscriptions", Context.MODE_PRIVATE)
        val blocklist = appContext.getSharedPreferences("not_interested", Context.MODE_PRIVATE)

        fun scoped(base: String, profileId: String) =
            ProfileManager.profileScopedKey(base, profileId, legacyId)

        return profiles.associate { profile ->
            profile.id to BackupProfileData(
                subscriptions = subs.getString(scoped("subscriptions", profile.id), null),
                subscriptionGroups = subs.getString(scoped("groups", profile.id), null),
                hiddenVideos = blocklist.getString(scoped("hidden_videos", profile.id), null),
                blockedChannels = blocklist.getString(scoped("blocked_channels", profile.id), null)
            )
        }.filterValues { !it.isEmpty }
    }

    private fun collectFiles(): List<BackupFile> {
        val files = mutableListOf<BackupFile>()
        for (name in BACKED_UP_FILES) {
            val file = File(filesDir, name)
            if (file.isFile) {
                runCatching { files.add(BackupFile(name, file.readBytes())) }
                    .onFailure { Log.w(TAG, "Skipped $name", it) }
            }
        }
        for (dirName in BACKED_UP_DIRECTORIES) {
            val dir = File(filesDir, dirName)
            if (!dir.isDirectory) continue
            dir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                runCatching { files.add(BackupFile("$dirName/${file.name}", file.readBytes())) }
                    .onFailure { Log.w(TAG, "Skipped ${file.name}", it) }
            }
        }
        return files
    }

    /**
     * The counts the restore preview shows.
     *
     * Derived by counting entries in what was collected rather than by asking
     * the repositories, so a backup describes the file rather than the app
     * that happened to be running when it was opened.
     */
    private fun countContents(
        preferences: Map<String, Map<String, PreferenceValue>>,
        profiles: List<Profile>,
        profileData: Map<String, BackupProfileData>,
        files: List<BackupFile>
    ): Map<String, Int> {
        fun arrayLength(raw: String?): Int = raw?.let {
            runCatching { org.json.JSONArray(it).length() }.getOrDefault(0)
        } ?: 0

        val likedIds = preferences["ivor_music_liked_songs"]?.get("liked_song_ids")
        return mapOf(
            COUNT_PLAYLISTS to files.count { it.path.startsWith("playlists/") },
            COUNT_VIDEO_PLAYLISTS to files.count { it.path.startsWith("video_playlists/") },
            COUNT_SAVED_PLAYLISTS to arrayLength(
                (preferences["saved_playlists"]?.get("playlists") as? PreferenceValue.Text)?.value
            ),
            COUNT_LIKED_SONGS to ((likedIds as? PreferenceValue.StringSet)?.value?.size ?: 0),
            COUNT_PLAY_HISTORY to arrayLength(
                files.firstOrNull { it.path == "play_history.json" }
                    ?.bytes?.toString(Charsets.UTF_8)
            ),
            COUNT_WATCH_HISTORY to arrayLength(
                (preferences["video_history"]?.get("history_list") as? PreferenceValue.Text)?.value
            ),
            COUNT_SUBSCRIPTIONS to profileData.values.sumOf { arrayLength(it.subscriptions) },
            COUNT_BLOCKED to profileData.values.sumOf { arrayLength(it.blockedChannels) },
            COUNT_HIDDEN to profileData.values.sumOf { arrayLength(it.hiddenVideos) },
            COUNT_PROFILES to profiles.size,
            COUNT_SETTINGS to (preferences["ivor_music_theme_prefs"]?.size ?: 0)
        )
    }

    // ---------------- Reading a file back ----------------

    /**
     * Reads the backup at [source] without applying any of it, so the user can
     * be shown what is in the file before agreeing to replace what they have.
     *
     * @throws UnsupportedBackupException when the file is a Koda backup this
     * build is too old to read.
     */
    suspend fun peek(source: Uri): BackupSnapshot? = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(source)?.use { input ->
            BackupTransfer.read(input)
        }
    }

    // ---------------- Applying ----------------

    /**
     * Replaces this install's data with [snapshot].
     *
     * Everything in the two allowlists is cleared first, so a restore does not
     * leave a playlist or a blocked channel behind that the backup did not
     * have. The caller is expected to restart the process immediately
     * afterwards; see the class doc for why.
     */
    suspend fun apply(snapshot: BackupSnapshot): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val profileMap = restoreProfiles(snapshot)
            restorePreferences(snapshot)
            restoreProfileData(snapshot, profileMap)
            restoreFiles(snapshot)
            // The restored roster can land the app on a different identity to
            // the one it was on a moment ago, and visitorData is the anti-bot
            // token that identity was minted under. AccountSwitcher already
            // documents what carrying a stale or shared one across costs
            // (`LOGIN_REQUIRED`), and this is the same move: drop it and let it
            // re-mint. Committed, because the process is about to go and the
            // persisted copy would otherwise outlive it.
            YouTubeRepository.invalidateSessionScopedCaches(appContext, commitNow = true)
            RestoreResult(
                success = true,
                restoredProfiles = profileMap.size,
                signInNeeded = snapshot.profiles.count { it.kind == ProfileKind.YOUTUBE.name }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            RestoreResult(success = false, error = "The restore could not be completed.")
        }
    }

    /**
     * Puts the roster back and works out which local profile each backup
     * profile's data belongs to.
     *
     * Merged rather than replaced: a profile already here keeps its cookies,
     * because the file has none to replace them with and signing someone out
     * of an account they are using is not something a restore should do. A
     * backup profile is recognised as one already present first by id (the
     * same install, restored onto itself after a data wipe) and then by
     * `datasyncId` (the same Google account, added by hand on this device),
     * and only otherwise created.
     *
     * @return backup profile id to the local profile id its data should be
     * written under.
     */
    private fun restoreProfiles(snapshot: BackupSnapshot): Map<String, String> {
        if (snapshot.profiles.isEmpty()) return emptyMap()
        val profileManager = ProfileManager(appContext)
        val existing = profileManager.profiles.value
        val mapping = mutableMapOf<String, String>()
        val additions = mutableListOf<Profile>()

        for (backup in snapshot.profiles) {
            val match = existing.firstOrNull { it.id == backup.id }
                ?: backup.datasyncId?.let { sync ->
                    existing.firstOrNull { it.datasyncId == sync }
                }
            if (match != null) {
                mapping[backup.id] = match.id
                continue
            }
            val kind = runCatching { ProfileKind.valueOf(backup.kind) }
                .getOrDefault(ProfileKind.LOCAL)
            additions.add(
                Profile(
                    id = backup.id,
                    kind = kind,
                    name = backup.name,
                    handle = backup.handle,
                    avatarUrl = backup.avatarUrl,
                    datasyncId = backup.datasyncId,
                    addedAt = backup.addedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    // A restored YouTube profile has an identity and no
                    // session, which is exactly what the expired badge already
                    // means: the row shows the account and asks to be signed
                    // into again, rather than pretending to be connected.
                    expired = kind == ProfileKind.YOUTUBE
                )
            )
            mapping[backup.id] = backup.id
        }

        // A fresh install has exactly one profile: the default local one, made
        // for it and never used. Restoring onto that is the common case - it is
        // what a new phone is - and leaving it behind puts an empty row beside
        // the restored one, usually under the identical name, since both are
        // called "No account" by default. So it is dropped, but only when it is
        // genuinely untouched: the sole profile, local, and not something the
        // backup matched onto.
        val soleDefault = existing.singleOrNull()
            ?.takeIf { it.isLocal && it.name == ProfileManager.DEFAULT_LOCAL_NAME }
            ?.takeIf { candidate -> mapping.values.none { it == candidate.id } }
        val dropping = setOfNotNull(soleDefault?.id.takeIf { additions.isNotEmpty() })

        if (additions.isNotEmpty() || dropping.isNotEmpty()) {
            profileManager.restoreProfiles(additions, dropping)
        }

        // Land on the identity the backup was being used under. Without this a
        // restore onto a fresh install leaves the data scoped to a profile
        // nothing is looking at, and the subscriptions and blocklist read as
        // empty even though they restored correctly. Falling back to any
        // restored profile rather than none, because the alternative is coming
        // back active on a profile that was just dropped.
        val landOn = snapshot.activeProfileId?.let { mapping[it] }
            ?: mapping.values.firstOrNull()
        landOn?.let { profileManager.setActive(it, commitNow = true) }

        return mapping
    }

    private fun restorePreferences(snapshot: BackupSnapshot) {
        for (fileName in PREFERENCE_FILES) {
            val prefs = appContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            val editor = prefs.edit().clear()
            snapshot.preferences[fileName]?.forEach { (key, value) ->
                when (value) {
                    is PreferenceValue.Text -> editor.putString(key, value.value)
                    is PreferenceValue.Integer -> editor.putInt(key, value.value)
                    is PreferenceValue.LongNumber -> editor.putLong(key, value.value)
                    is PreferenceValue.FloatNumber -> editor.putFloat(key, value.value)
                    is PreferenceValue.Flag -> editor.putBoolean(key, value.value)
                    is PreferenceValue.StringSet -> editor.putStringSet(key, value.value)
                }
            }
            // commit, not apply: the process is about to be killed, and an
            // apply() still queued when it dies takes the restore with it.
            editor.commit()
        }
    }

    /**
     * Writes each profile's subscriptions and blocklist under the key *this*
     * device computes for the profile they landed on.
     *
     * The suffix cannot be carried across from the backup, because the profile
     * migrated from a pre-profiles install keeps the un-suffixed key and which
     * profile that is differs per device.
     */
    private fun restoreProfileData(snapshot: BackupSnapshot, profileMap: Map<String, String>) {
        val legacyId = ProfileManager.legacyProfileId(appContext)
        val subs = appContext.getSharedPreferences("local_subscriptions", Context.MODE_PRIVATE)
        val blocklist = appContext.getSharedPreferences("not_interested", Context.MODE_PRIVATE)

        val subsEditor = subs.edit().clear()
        val blocklistEditor = blocklist.edit().clear()

        for ((backupId, data) in snapshot.profileData) {
            val localId = profileMap[backupId] ?: continue
            fun key(base: String) = ProfileManager.profileScopedKey(base, localId, legacyId)
            data.subscriptions?.let { subsEditor.putString(key("subscriptions"), it) }
            data.subscriptionGroups?.let { subsEditor.putString(key("groups"), it) }
            data.hiddenVideos?.let { blocklistEditor.putString(key("hidden_videos"), it) }
            data.blockedChannels?.let { blocklistEditor.putString(key("blocked_channels"), it) }
        }

        subsEditor.commit()
        blocklistEditor.commit()
    }

    /**
     * Clears the backed-up directories and writes the file set back.
     *
     * Directories are emptied rather than deleted so a playlist the backup
     * does not contain is gone afterwards, which is what replacing means. Only
     * the directories in the allowlist are touched; nothing else under
     * `filesDir` is, and [BackupTransfer] has already refused any entry whose
     * path could climb out of it.
     */
    private fun restoreFiles(snapshot: BackupSnapshot) {
        for (dirName in BACKED_UP_DIRECTORIES) {
            val dir = File(filesDir, dirName)
            if (dir.isDirectory) dir.listFiles()?.forEach { it.delete() } else dir.mkdirs()
        }
        for (name in BACKED_UP_FILES) {
            File(filesDir, name).delete()
        }
        for (file in snapshot.files) {
            val target = File(filesDir, file.path)
            // Second line of defence behind the path check in the reader: a
            // resolved path outside filesDir is never written, whatever the
            // entry name claimed.
            if (!target.canonicalPath.startsWith(filesDir.canonicalPath + File.separator)) {
                Log.w(TAG, "Refused an entry outside filesDir: ${file.path}")
                continue
            }
            runCatching {
                target.parentFile?.mkdirs()
                if (file.path.startsWith("playlists/")) {
                    target.writeBytes(rehomeCoverPath(file.bytes))
                } else {
                    target.writeBytes(file.bytes)
                }
            }.onFailure { Log.w(TAG, "Could not write ${file.path}", it) }
        }
    }

    /**
     * Point a restored playlist's `coverUri` at *this* install's covers
     * directory.
     *
     * [UserPlaylist.coverUri] is stored as an absolute `file://` path under
     * `filesDir`, and `filesDir` is not the same string everywhere: it
     * carries the Android user id, so `/data/user/0/...` on the phone the
     * backup came from is `/data/user/10/...` under a work profile or a
     * second user. The cover file itself is in the backup and lands
     * correctly; only the pointer to it is wrong, so every playlist would
     * come back with its artwork silently missing - and
     * `PlaylistRepository.deleteCoverFile` checks the parent directory
     * before deleting, so replacing one of those covers later would leave
     * the old file behind rather than removing it.
     *
     * Rewritten as targeted string surgery rather than by decoding the
     * playlist: this must not become the thing that drops a field a future
     * [UserPlaylist] gains. Only the segment *before* `/playlist_covers/`
     * is replaced, and only for a `file://` value, so a `content://` cover
     * from an old build is left alone - it was already broken across
     * devices and inventing a path for it would be worse.
     */
    private fun rehomeCoverPath(bytes: ByteArray): ByteArray {
        val text = bytes.toString(Charsets.UTF_8)
        if (!text.contains(COVERS_SEGMENT)) return bytes
        val home = "file://" + File(filesDir, "playlist_covers").absolutePath + "/"
        val rewritten = COVER_URI_PATTERN.replace(text) { match ->
            home + match.groupValues[1]
        }
        return if (rewritten == text) bytes else rewritten.toByteArray(Charsets.UTF_8)
    }
}

private const val COVERS_SEGMENT = "/playlist_covers/"

/**
 * A `file://` cover path, capturing the file name after the covers
 * directory. Anchored on `file://` so nothing else in the playlist JSON can
 * match, and neither group crosses a double quote, so a match cannot run
 * past the end of the JSON string it sits in.
 */
private val COVER_URI_PATTERN = Regex("file://[^\"]*/playlist_covers/([^\"/]+)")

/** What a restore did, for the message shown before the app restarts. */
data class RestoreResult(
    val success: Boolean,
    val restoredProfiles: Int = 0,
    /** How many restored identities are YouTube accounts needing a fresh sign-in. */
    val signInNeeded: Int = 0,
    val error: String? = null
)
