package com.ivor.ivormusic.ui.channel

import com.ivor.ivormusic.data.VideoItem

/**
 * How a search inside one channel is ordered.
 *
 * YouTube's channel search (the channel browse with a query) has no sort of
 * its own and answers in relevance order, which on a big channel reads as
 * random when what someone wants is the latest upload about a topic (#371).
 * These order what it returned, on the device; [RELEVANCE] is YouTube's order.
 */
internal enum class ChannelSearchSort { RELEVANCE, NEWEST, OLDEST, MOST_VIEWED }

/**
 * [results] in [sort] order. Stable, so equal keys keep YouTube's relevance
 * order between them. A video with no readable date or view count goes last
 * either way rather than being treated as the oldest or least watched.
 */
internal fun sortChannelSearch(
    results: List<VideoItem>,
    sort: ChannelSearchSort,
    nowMs: Long = System.currentTimeMillis(),
): List<VideoItem> {
    fun published(video: VideoItem): Long? =
        video.publishedAtMs ?: VideoItem.parseRelativeTime(video.uploadedDate, nowMs)

    return when (sort) {
        ChannelSearchSort.RELEVANCE -> results
        ChannelSearchSort.NEWEST -> results.sortedWith(
            compareBy<VideoItem> { published(it) == null }.thenByDescending { published(it) }
        )
        ChannelSearchSort.OLDEST -> results.sortedWith(
            compareBy<VideoItem> { published(it) == null }.thenBy { published(it) }
        )
        ChannelSearchSort.MOST_VIEWED -> results.sortedWith(
            compareBy<VideoItem> { parseViewCount(it.viewCount) == null }
                .thenByDescending { parseViewCount(it.viewCount) }
        )
    }
}

private val VIEW_COUNT = Regex("""(\d[\d,]*(?:\.\d+)?)\s*([KMB])?""", RegexOption.IGNORE_CASE)

/**
 * The number in a view count as YouTube writes it: "1,234 views", "5.4K views",
 * "1.2M views", "No views". Null when there is no number to read (a live
 * "watching" line is still read, which is fine for ordering).
 */
internal fun parseViewCount(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    if (text.trim().startsWith("No ", ignoreCase = true)) return 0L
    val match = VIEW_COUNT.find(text) ?: return null
    val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    val scale = when (match.groupValues[2].uppercase()) {
        "K" -> 1_000.0
        "M" -> 1_000_000.0
        "B" -> 1_000_000_000.0
        else -> 1.0
    }
    return (number * scale).toLong()
}
