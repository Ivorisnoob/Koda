package com.ivor.ivormusic.data

import android.util.Xml
import com.ivor.ivormusic.util.KLog
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Minimal RSS 2.0 parser for podcast feeds, built on Android's [XmlPullParser]
 * so no new dependency is needed.
 *
 * Handles the channel fields podcasts actually use ([title], [link],
 * [description], [image]/[itunes:image]) and per-item [title], [enclosure],
 * [guid], [pubDate], [description] and [itunes:duration]/[itunes:image].
 * Unknown elements and namespaces are skipped, so Atom-flavoured or
 * FeedBurner-proxied feeds degrade to their RSS core rather than failing.
 */
object PodcastRssParser {

    private const val TAG = "PodcastRssParser"

    private val pubDateFormats = listOf(
        // "Tue, 07 Oct 2025 10:00:00 GMT" / "... +0000"
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        // Without weekday: "07 Oct 2025 10:00:00 GMT"
        SimpleDateFormat("dd MMM yyyy HH:mm:ss z", Locale.US),
        // ISO-8601 some feeds emit: "2025-10-07T10:00:00Z"
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
    )

    fun parse(xml: String): ParsedPodcastFeed {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        var title = ""
        var siteUrl: String? = null
        var description: String? = null
        var artworkUrl: String? = null
        val episodes = mutableListOf<ParsedPodcastEpisode>()

        var eventType = parser.eventType
        // Depth tracking: only <item> children become episodes; channel-level
        // fields are read while not inside an item.
        var inItem = false
        var itemBuilder: EpisodeBuilder? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase(Locale.US)
                    when {
                        name == "item" -> {
                            inItem = true
                            itemBuilder = EpisodeBuilder()
                        }
                        inItem && itemBuilder != null -> readItemTag(parser, itemBuilder)
                        else -> readChannelTag(parser, name)?.let { value ->
                            when (name) {
                                "title" -> if (title.isEmpty()) title = value
                                "link" -> if (siteUrl == null) siteUrl = value
                                "description" -> if (description == null) description = value
                            }
                        } ?: run {
                            // Namespaced channel artwork: <itunes:image href="..."/>
                            if (name == "image") {
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null && artworkUrl == null) artworkUrl = href
                                val nested = readImageUrl(parser)
                                if (nested != null && artworkUrl == null) artworkUrl = nested
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true) && inItem) {
                        itemBuilder?.build()?.let { episodes.add(it) }
                        itemBuilder = null
                        inItem = false
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedPodcastFeed(
            title = title.ifEmpty { "Untitled podcast" },
            siteUrl = siteUrl,
            description = description,
            artworkUrl = artworkUrl,
            episodes = episodes,
        )
    }

    /** Reads a simple text element at the channel level. Null when not one. */
    private fun readChannelTag(parser: XmlPullParser, name: String): String? {
        return when (name) {
            "title", "link", "description" -> readText(parser)
            else -> null
        }
    }

    /** Reads <image><url>…</url></image>; the parser is on the <image> start. */
    private fun readImageUrl(parser: XmlPullParser): String? {
        var url: String? = null
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG &&
                parser.name.equals("url", ignoreCase = true)
            ) {
                url = readText(parser)
            }
            eventType = parser.next()
        }
        return url?.takeIf { it.isNotBlank() }
    }

    private fun readItemTag(parser: XmlPullParser, b: EpisodeBuilder) {
        val name = parser.name.lowercase(Locale.US)
        when (name) {
            "title" -> b.title = readText(parser).ifEmpty { b.title }
            "enclosure" -> {
                val url = parser.getAttributeValue(null, "url")
                if (!url.isNullOrBlank() && b.audioUrl == null) b.audioUrl = url
                skip(parser)
            }
            "guid" -> b.guid = readText(parser).takeIf { it.isNotBlank() }
            "pubdate" -> b.pubDate = parsePubDate(readText(parser))
            "description" -> if (b.description == null) b.description = readText(parser)
            "duration" -> b.durationMs = parseDuration(readText(parser))
            "image" -> {
                // <itunes:image href="..."/> — empty element, attribute only.
                parser.getAttributeValue(null, "href")?.takeIf { it.isNotBlank() }?.let {
                    if (b.artworkUrl == null) b.artworkUrl = it
                }
                skip(parser)
            }
            // <media:content url="..." medium="audio"> fallback for feeds
            // whose enclosure lives in the media namespace.
            "content" -> {
                val url = parser.getAttributeValue(null, "url")
                val medium = parser.getAttributeValue(null, "medium")
                if (!url.isNullOrBlank() && b.audioUrl == null &&
                    (medium == null || medium == "audio")
                ) {
                    b.audioUrl = url
                }
                skip(parser)
            }
            else -> skip(parser)
        }
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text.orEmpty()
            parser.nextTag()
        }
        return result.trim()
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                else -> Unit
            }
        }
    }

    /** Accepts seconds ("3600"), MM:SS and HH:MM:SS. */
    private fun parseDuration(raw: String): Long {
        val t = raw.trim()
        if (t.isEmpty()) return 0L
        t.toLongOrNull()?.let { return it * 1000L }
        val parts = t.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return 0L
        var ms = 0L
        var mult = 1000L
        for (p in parts.reversed()) {
            ms += p * mult
            mult *= 60L
        }
        return ms
    }

    private fun parsePubDate(raw: String): Long {
        val t = raw.trim()
        if (t.isEmpty()) return 0L
        for (fmt in pubDateFormats) {
            try {
                // SimpleDateFormat is not thread-safe; these are parser-local.
                val parsed = synchronized(fmt) { fmt.parse(t) }
                if (parsed != null) return parsed.time
            } catch (_: ParseException) {
            }
        }
        KLog.w(TAG, "Unparseable pubDate: $t")
        return 0L
    }

    private class EpisodeBuilder {
        var guid: String? = null
        var title: String = "Untitled episode"
        var audioUrl: String? = null
        var artworkUrl: String? = null
        var description: String? = null
        var pubDate: Long = 0L
        var durationMs: Long = 0L

        /** Episodes without playable audio are dropped. */
        fun build(): ParsedPodcastEpisode? {
            val url = audioUrl?.takeIf { it.isNotBlank() } ?: return null
            return ParsedPodcastEpisode(
                guid = guid,
                title = title,
                audioUrl = url,
                artworkUrl = artworkUrl,
                description = description?.takeIf { it.isNotBlank() },
                pubDate = pubDate,
                durationMs = durationMs,
            )
        }
    }
}
