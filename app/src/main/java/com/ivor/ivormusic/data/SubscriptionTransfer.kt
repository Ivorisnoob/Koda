package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Everything one picked file yielded.
 *
 * The foreign-service count rides along rather than being derived by a second
 * pass, because a database export is opened once and closed again - the count
 * is only knowable while that read is happening.
 */
data class ImportedFile(
    val channels: List<ImportedChannel> = emptyList(),
    val groups: List<SubscriptionGroup> = emptyList(),
    val foreignServiceEntries: Int = 0
)

/**
 * Reads and writes the subscription-list file formats users actually have on
 * hand, so migrating into Koda never means re-subscribing by hand:
 *
 * - **NewPipe / PipePipe / Tubular `subscriptions.json`** - the de-facto
 *   interchange format across the whole NewPipe fork family.
 * - **A NewPipe-family backup archive** - `NewPipeData-*.zip` /
 *   `PipePipeData-*.zip`, holding the app's whole Room database.
 * - **Google Takeout `subscriptions.csv`** - what YouTube itself hands you.
 * - **OPML** - what most RSS readers and the old `subscription_manager`
 *   export produce.
 *
 * The format is sniffed rather than asked for, because a user picking a file
 * out of Downloads should not have to know which of them they grabbed - and in
 * PipePipe's case the two exports live in different menus, so people routinely
 * arrive with the backup when they wanted the subscription list.
 *
 * Text parsing here is pure; the database path needs a real file (see [read]).
 * Resolving handles to canonical UC ids needs the network and belongs to
 * [YouTubeRepository].
 */
object SubscriptionTransfer {

    /** NewPipe's service id for YouTube. Other services cannot be played here. */
    private const val SERVICE_ID_YOUTUBE = 0

    /**
     * Reads whichever export [bytes] turned out to be.
     *
     * [scratchFile] is where a database is unpacked to: SQLite can only open a
     * real path, so a backup archive cannot be parsed from the bytes alone.
     * It is written and deleted within this call - the caller only has to
     * name somewhere private, normally in `cacheDir`.
     */
    fun read(bytes: ByteArray, scratchFile: File): ImportedFile {
        if (looksLikeZip(bytes)) {
            return if (unpackDatabase(bytes, scratchFile)) {
                try {
                    parseDatabase(scratchFile)
                } finally {
                    scratchFile.delete()
                }
            } else {
                ImportedFile()
            }
        }
        // Someone who unzipped the backup by hand and picked the database out
        // of it lands here, and means exactly the same thing.
        if (looksLikeSqlite(bytes)) {
            return try {
                scratchFile.writeBytes(bytes)
                parseDatabase(scratchFile)
            } catch (e: Exception) {
                KLog.w(TAG, "Could not read that database", e)
                ImportedFile()
            } finally {
                scratchFile.delete()
            }
        }
        val text = bytes.toString(Charsets.UTF_8)
        return ImportedFile(
            channels = parse(text),
            groups = parseGroups(text),
            foreignServiceEntries = countForeignServiceEntries(text)
        )
    }

    /**
     * Parses [text] as whichever of the supported formats it looks like.
     * Returns an empty list when nothing recognisable is in it - the caller
     * turns that into a "couldn't read this file" message.
     */
    fun parse(text: String): List<ImportedChannel> {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> parseNewPipeJson(trimmed)
            trimmed.startsWith("<") -> parseOpml(trimmed)
            else -> parseCsv(trimmed)
        }
    }

    /**
     * How many entries in [text] belonged to a service Koda cannot play
     * (BiliBili, NicoNico, SoundCloud...). Reported separately so a PipePipe
     * user is told why 40 of their 200 channels did not come across, rather
     * than silently losing them.
     */
    fun countForeignServiceEntries(text: String): Int {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return 0
        return try {
            subscriptionArray(trimmed)?.let { array ->
                (0 until array.length()).count { i ->
                    val obj = array.optJSONObject(i) ?: return@count false
                    obj.has("service_id") && obj.optInt("service_id", SERVICE_ID_YOUTUBE) != SERVICE_ID_YOUTUBE
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    // ---------------- NewPipe-family backup archive ----------------

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    private fun looksLikeSqlite(bytes: ByteArray): Boolean =
        bytes.size >= SQLITE_MAGIC.size &&
            SQLITE_MAGIC.indices.all { bytes[it] == SQLITE_MAGIC[it] }

    /**
     * Pulls the database out of a `NewPipeData-*.zip` / `PipePipeData-*.zip`
     * into [destination]. The archive also carries a `.settings` file, which
     * is of no interest here.
     *
     * Entry names are only used to spot the database - never to build the
     * destination path, so a crafted archive cannot write outside cacheDir -
     * and the copy is capped, since an export is a couple of megabytes and
     * anything wildly past that is not one.
     */
    private fun unpackDatabase(bytes: ByteArray, destination: File): Boolean {
        return try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || !name.endsWith(".db", ignoreCase = true)) {
                        zip.closeEntry()
                        continue
                    }
                    destination.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            written += read
                            if (written > MAX_DATABASE_BYTES) {
                                throw IllegalStateException("Database entry too large")
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                    return true
                }
            }
            KLog.w(TAG, "No database inside that archive")
            false
        } catch (e: Exception) {
            KLog.w(TAG, "Could not unpack that archive", e)
            destination.delete()
            false
        }
    }

    /**
     * Reads the NewPipe-family Room schema directly:
     *
     * ```
     * subscriptions(uid, service_id, url, name, avatar_url, ...)
     * feed_group(uid, name, ...)
     * feed_group_subscription_join(group_id, subscription_id)
     * ```
     *
     * Verified against a PipePipe export, August 2026. Columns are located by
     * name and every one but `url` is optional, because the schema has grown
     * over the years and the forks are not all on the same revision - an
     * import that fails outright on a missing `avatar_url` would be a worse
     * answer than one that simply has no pictures yet.
     *
     * Avatars are carried across, which the JSON export cannot do, so a
     * database import arrives with channel pictures already filled in.
     */
    private fun parseDatabase(file: File): ImportedFile {
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                val channels = mutableListOf<ImportedChannel>()
                // Kept so the group join, which addresses rows by uid, can be
                // turned into channel ids.
                val channelIdByRow = mutableMapOf<Long, String>()
                var foreign = 0

                db.rawQuery(
                    "SELECT uid, service_id, url, name, avatar_url FROM subscriptions", null
                ).use { cursor ->
                    val uidCol = cursor.getColumnIndex("uid")
                    val serviceCol = cursor.getColumnIndex("service_id")
                    val urlCol = cursor.getColumnIndex("url")
                    val nameCol = cursor.getColumnIndex("name")
                    val avatarCol = cursor.getColumnIndex("avatar_url")
                    while (cursor.moveToNext()) {
                        val service =
                            if (serviceCol >= 0) cursor.getInt(serviceCol) else SERVICE_ID_YOUTUBE
                        if (service != SERVICE_ID_YOUTUBE) {
                            foreign++
                            continue
                        }
                        val url = if (urlCol >= 0) cursor.getString(urlCol) else null
                        if (url.isNullOrBlank()) continue
                        val name = if (nameCol >= 0) {
                            cursor.getString(nameCol)?.takeIf { it.isNotBlank() }
                        } else null
                        val avatar = if (avatarCol >= 0) {
                            cursor.getString(avatarCol)?.takeIf { it.isNotBlank() }
                        } else null
                        val channel = fromUrl(url, name)?.copy(avatarUrl = avatar) ?: continue
                        channels.add(channel)
                        val channelId = channel.channelId
                        if (uidCol >= 0 && channelId != null) {
                            channelIdByRow[cursor.getLong(uidCol)] = channelId
                        }
                    }
                }
                ImportedFile(channels, readDatabaseGroups(db, channelIdByRow), foreign)
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Not a NewPipe-family database", e)
            ImportedFile()
        }
    }

    /**
     * Feed groups, mapped onto Koda's own. Older schema revisions have no
     * group tables at all, so a failure here costs the groups and keeps the
     * channels rather than failing the import - the channels are the point.
     *
     * A group member whose row did not resolve to a canonical UC id is
     * dropped: groups are matched by channel id everywhere else, and a
     * handle placed in one would silently never match.
     */
    private fun readDatabaseGroups(
        db: SQLiteDatabase,
        channelIdByRow: Map<Long, String>
    ): List<SubscriptionGroup> {
        if (channelIdByRow.isEmpty()) return emptyList()
        return try {
            val members = linkedMapOf<String, MutableList<String>>()
            db.rawQuery(
                """
                SELECT g.name AS group_name, j.subscription_id AS subscription_id
                FROM feed_group g
                JOIN feed_group_subscription_join j ON j.group_id = g.uid
                """.trimIndent(),
                null
            ).use { cursor ->
                val nameCol = cursor.getColumnIndex("group_name")
                val subCol = cursor.getColumnIndex("subscription_id")
                if (nameCol < 0 || subCol < 0) return emptyList()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: continue
                    val channelId = channelIdByRow[cursor.getLong(subCol)] ?: continue
                    members.getOrPut(name) { mutableListOf() }.add(channelId)
                }
            }
            members.map { (name, ids) ->
                SubscriptionGroup(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    channelIds = ids.distinct()
                )
            }
        } catch (e: Exception) {
            KLog.w(TAG, "No readable feed groups in that database", e)
            emptyList()
        }
    }

    // ---------------- NewPipe / PipePipe JSON ----------------

    private fun subscriptionArray(text: String): JSONArray? {
        if (text.startsWith("[")) return JSONArray(text)
        val root = JSONObject(text)
        return root.optJSONArray("subscriptions")
    }

    /**
     * `{"app_version": "...", "subscriptions": [{"service_id": 0, "url": ..., "name": ...}]}`
     *
     * Entries whose `service_id` is not YouTube are dropped: a BiliBili
     * channel has no meaning in an app that resolves everything through
     * InnerTube. A bare array of the same objects is accepted too, since
     * several third-party converters emit that.
     */
    fun parseNewPipeJson(text: String): List<ImportedChannel> {
        return try {
            val array = subscriptionArray(text) ?: return emptyList()
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                if (obj.optInt("service_id", SERVICE_ID_YOUTUBE) != SERVICE_ID_YOUTUBE) {
                    return@mapNotNull null
                }
                val url = obj.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = obj.optString("name").takeIf { it.isNotBlank() }
                fromUrl(url, name)
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Not a NewPipe subscriptions file", e)
            emptyList()
        }
    }

    /**
     * Groups exported alongside the channels. NewPipe itself has never put
     * groups in its export (a long-standing open request), so this only ever
     * matches Koda's own files - hence the tolerant read and the empty
     * fallback for everyone else's.
     */
    fun parseGroups(text: String): List<SubscriptionGroup> {
        return try {
            if (!text.trimStart().startsWith("{")) return emptyList()
            val array = JSONObject(text).optJSONArray("groups") ?: return emptyList()
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val ids = obj.optJSONArray("channel_ids")
                SubscriptionGroup(
                    id = obj.optString("id").takeIf { it.isNotBlank() }
                        ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    channelIds = (0 until (ids?.length() ?: 0)).mapNotNull { j ->
                        ids?.optString(j)?.takeIf { it.isNotBlank() }
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------------- Google Takeout CSV ----------------

    /**
     * Takeout's `subscriptions.csv`: `Channel Id,Channel Url,Channel Title`.
     * The header casing has changed at least once across Takeout revisions,
     * so columns are located by fuzzy header match and fall back to Takeout's
     * fixed positional order when the header is missing entirely.
     */
    fun parseCsv(text: String): List<ImportedChannel> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()

        val header = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        val looksLikeHeader = header.any { it.contains("channel") }
        var idIdx = header.indexOfFirst { it.contains("id") }
        var urlIdx = header.indexOfFirst { it.contains("url") }
        var nameIdx = header.indexOfFirst { it.contains("title") || it.contains("name") }
        if (!looksLikeHeader) {
            idIdx = 0; urlIdx = 1; nameIdx = 2
        }

        val rows = if (looksLikeHeader) lines.drop(1) else lines
        return rows.mapNotNull { line ->
            val cells = splitCsvLine(line)
            fun cell(index: Int): String? =
                cells.getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }

            val name = cell(nameIdx)
            val id = cell(idIdx)?.takeIf { it.startsWith("UC") && it.length >= 20 }
            if (id != null) return@mapNotNull ImportedChannel(id, name ?: id)
            val url = cell(urlIdx) ?: return@mapNotNull null
            fromUrl(url, name)
        }
    }

    /**
     * Splits one CSV row, honouring double-quoted cells and the `""` escape.
     * Channel titles routinely contain commas, so a naive `split(",")` drops
     * or shifts columns on a meaningful slice of any real export.
     */
    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    cells.add(current.toString()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }

    // ---------------- OPML ----------------

    /**
     * OPML `<outline>` entries. The channel id normally rides in the
     * `xmlUrl` as `feeds/videos.xml?channel_id=UC...`; some readers only
     * keep `htmlUrl`, so both are tried.
     */
    fun parseOpml(text: String): List<ImportedChannel> {
        return OUTLINE.findAll(text).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val name = attribute(attrs, "text") ?: attribute(attrs, "title")
            val url = attribute(attrs, "xmlUrl") ?: attribute(attrs, "xmlurl")
                ?: attribute(attrs, "htmlUrl") ?: attribute(attrs, "htmlurl")
                ?: return@mapNotNull null
            fromUrl(unescapeXml(url), name?.let { unescapeXml(it) })
        }.toList()
    }

    private fun attribute(attrs: String, name: String): String? {
        val quote = '"'
        val pattern = "\\b" + Regex.escape(name) + "\\s*=\\s*$quote([^$quote]*)$quote"
        return Regex(pattern).find(attrs)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun unescapeXml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")

    // ---------------- URL -> channel ----------------

    /**
     * Pulls a canonical UC id out of [url] when it has one, otherwise keeps
     * the handle/vanity path for the caller to resolve over the network.
     * Returns null only for URLs that are not channel references at all.
     */
    fun fromUrl(url: String, name: String?): ImportedChannel? {
        val cleaned = url.trim().removeSuffix("/")
        if (cleaned.isBlank()) return null

        CHANNEL_ID_IN_URL.find(cleaned)?.let { match ->
            val id = match.groupValues[1]
            return ImportedChannel(id, name ?: id)
        }
        // A bare id, which some converters emit in the url column.
        if (UC_ID.matches(cleaned)) return ImportedChannel(cleaned, name ?: cleaned)

        HANDLE_IN_URL.find(cleaned)?.let { match ->
            val handle = "@" + match.groupValues[1]
            return ImportedChannel(null, name ?: handle, unresolvedPath = handle)
        }
        VANITY_IN_URL.find(cleaned)?.let { match ->
            val path = match.groupValues[1] + "/" + match.groupValues[2]
            return ImportedChannel(null, name ?: match.groupValues[2], unresolvedPath = path)
        }
        // A bare "@handle" with no URL around it.
        if (cleaned.startsWith("@") && cleaned.length > 1 && !cleaned.contains('/')) {
            return ImportedChannel(null, name ?: cleaned, unresolvedPath = cleaned)
        }
        return null
    }

    // ---------------- Export ----------------

    /**
     * Writes the NewPipe-compatible shape, so a Koda export imports cleanly
     * into NewPipe, PipePipe and Tubular. Groups ride along under a top-level
     * `groups` key that those apps ignore, which is the only way to keep them
     * without breaking interchange.
     */
    fun buildExportJson(
        subscriptions: List<LocalSubscription>,
        groups: List<SubscriptionGroup>,
        appVersionName: String
    ): String {
        val root = JSONObject()
        root.put("app_version", appVersionName)
        root.put("app_version_int", 0)
        val array = JSONArray()
        subscriptions.forEach { sub ->
            array.put(JSONObject().apply {
                put("service_id", SERVICE_ID_YOUTUBE)
                put("url", "https://www.youtube.com/channel/${sub.channelId}")
                put("name", sub.name)
            })
        }
        root.put("subscriptions", array)
        if (groups.isNotEmpty()) {
            val groupArray = JSONArray()
            groups.forEach { group ->
                groupArray.put(JSONObject().apply {
                    put("id", group.id)
                    put("name", group.name)
                    put("channel_ids", JSONArray().also { ids -> group.channelIds.forEach(ids::put) })
                })
            }
            root.put("groups", groupArray)
        }
        return root.toString(2)
    }

    private const val TAG = "SubscriptionTransfer"

    /**
     * The 16-byte header every SQLite file opens with. The terminating NUL is
     * appended rather than written into the literal, where it would be an
     * invisible character in source and a trap for whoever edits the line next.
     */
    private val SQLITE_MAGIC =
        "SQLite format 3".toByteArray(Charsets.US_ASCII) + 0.toByte()

    /** A real export is a few MB; this is only here to bound a hostile zip. */
    private const val MAX_DATABASE_BYTES = 128L * 1024 * 1024

    private val UC_ID = Regex("""^UC[\w-]{20,}$""")
    private val CHANNEL_ID_IN_URL =
        Regex("""(?:channel/|channel_id=|/c(?:hannel)?/)(UC[\w-]{20,})""")
    private val HANDLE_IN_URL = Regex("""youtube\.com/@([\w.\-]+)""", RegexOption.IGNORE_CASE)
    private val VANITY_IN_URL = Regex("""youtube\.com/(c|user)/([\w.\-]+)""", RegexOption.IGNORE_CASE)
    private val OUTLINE = Regex("""<outline\s+([^>]*?)/?>""", RegexOption.IGNORE_CASE)
}
