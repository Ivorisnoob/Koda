package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.VideoItem

// A channel's public Atom feed read into videos. Pure.

/**
 * Parses the YouTube channel Atom feed.
 *
 * The parser is built namespace-*un*aware on purpose, so `getName()`
 * returns the literal prefixed tag ("yt:videoId", "media:thumbnail")
 * matched below. `android.util.Xml.newPullParser()` cannot be used here:
 * it turns namespace processing on, which strips the prefixes and would
 * collapse the Atom `<title>` and Media RSS `<media:title>` - two
 * different values on every entry - onto the same local name.
 */
internal fun parseChannelFeedXml(xml: String, avatarUrl: String?): List<VideoItem> {
    val videos = mutableListOf<VideoItem>()
    val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance()
        .apply { isNamespaceAware = false }
        .newPullParser()
    parser.setInput(java.io.StringReader(xml))

    var inEntry = false
    var inAuthor = false
    var videoId: String? = null
    var channelId: String? = null
    var title: String? = null
    var author: String? = null
    var publishedAtMs: Long? = null
    var thumbnailUrl: String? = null
    var description: String? = null
    var viewCount: Long? = null

    fun reset() {
        videoId = null; channelId = null; title = null; author = null
        publishedAtMs = null; thumbnailUrl = null; description = null; viewCount = null
    }

    var event = parser.eventType
    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        when (event) {
            org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                "entry" -> { inEntry = true; reset() }
                "author" -> inAuthor = true
                "yt:videoId" -> if (inEntry) videoId = parser.nextText().trim()
                "yt:channelId" -> if (inEntry) channelId = parser.nextText().trim()
                // The feed-level <title> is the channel name; only the one
                // inside an <entry> is a video title.
                "title" -> if (inEntry && title == null) title = parser.nextText().trim()
                "name" -> if (inEntry && inAuthor) author = parser.nextText().trim()
                "published" -> if (inEntry) {
                    publishedAtMs = runCatching {
                        java.time.OffsetDateTime.parse(parser.nextText().trim())
                            .toInstant().toEpochMilli()
                    }.getOrNull()
                }
                "media:thumbnail" -> if (inEntry && thumbnailUrl == null) {
                    thumbnailUrl = parser.getAttributeValue(null, "url")
                }
                "media:description" -> if (inEntry) {
                    description = runCatching { parser.nextText() }.getOrNull()
                }
                "media:statistics" -> if (inEntry) {
                    viewCount = parser.getAttributeValue(null, "views")?.toLongOrNull()
                }
            }

            org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                "author" -> inAuthor = false
                "entry" -> {
                    inEntry = false
                    val id = videoId
                    if (!id.isNullOrBlank()) {
                        videos.add(
                            VideoItem(
                                videoId = id,
                                title = title.orEmpty(),
                                channelName = author.orEmpty(),
                                channelId = channelId,
                                channelIconUrl = avatarUrl,
                                thumbnailUrl = thumbnailUrl,
                                duration = 0L,
                                viewCount = VideoItem.formatViewCount(viewCount),
                                uploadedDate = publishedAtMs?.let { VideoItem.formatRelativeTime(it) },
                                description = description,
                                publishedAtMs = publishedAtMs
                            )
                        )
                    }
                    reset()
                }
            }
        }
        event = parser.next()
    }
    return videos
}
