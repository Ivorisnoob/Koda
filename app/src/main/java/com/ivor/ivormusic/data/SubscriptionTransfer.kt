package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and writes the subscription-list file formats users actually have on
 * hand, so migrating into Koda never means re-subscribing by hand:
 *
 * - **NewPipe / PipePipe / Tubular `subscriptions.json`** - the de-facto
 *   interchange format across the whole NewPipe fork family.
 * - **Google Takeout `subscriptions.csv`** - what YouTube itself hands you.
 * - **OPML** - what most RSS readers and the old `subscription_manager`
 *   export produce.
 *
 * The format is sniffed rather than asked for, because a user picking a file
 * out of Downloads should not have to know which of the three they grabbed.
 *
 * Everything here is pure parsing over a String; resolving handles to
 * canonical UC ids needs the network and belongs to [YouTubeRepository].
 */
object SubscriptionTransfer {

    /** NewPipe's service id for YouTube. Other services cannot be played here. */
    private const val SERVICE_ID_YOUTUBE = 0

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
            android.util.Log.w(TAG, "Not a NewPipe subscriptions file", e)
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

    private val UC_ID = Regex("""^UC[\w-]{20,}$""")
    private val CHANNEL_ID_IN_URL =
        Regex("""(?:channel/|channel_id=|/c(?:hannel)?/)(UC[\w-]{20,})""")
    private val HANDLE_IN_URL = Regex("""youtube\.com/@([\w.\-]+)""", RegexOption.IGNORE_CASE)
    private val VANITY_IN_URL = Regex("""youtube\.com/(c|user)/([\w.\-]+)""", RegexOption.IGNORE_CASE)
    private val OUTLINE = Regex("""<outline\s+([^>]*?)/?>""", RegexOption.IGNORE_CASE)
}
