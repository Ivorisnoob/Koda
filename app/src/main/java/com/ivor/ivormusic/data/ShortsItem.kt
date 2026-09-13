package com.ivor.ivormusic.data

/**
 * One YouTube Short in a feed or swipe sequence.
 *
 * Search/channel shelf items carry title/view count. The signed-in Home shelf
 * uses a seedless Shorts sequence; most entries carry only id and thumbnail,
 * with metadata on prefetched entries. Watch-next enriches them on playback.
 */
data class ShortsItem(
    val videoId: String,
    val title: String = "",
    val viewCount: String = "",
    val thumbnailUrl: String? = null,
    /**
     * Search/channel seed params, or the continuation after the loaded Home
     * shelf. Raw sequence entries have none until added to a Home shelf.
     */
    val sequenceParams: String? = null
) {
    /** Portrait first-frame thumbnail YouTube serves for every Short. */
    val portraitThumbnailUrl: String
        get() = thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/frame0.jpg"

    fun toVideoItem(): VideoItem = VideoItem(
        videoId = videoId,
        title = title.ifBlank { "Short" },
        channelName = "",
        thumbnailUrl = portraitThumbnailUrl,
        duration = 0L,
        viewCount = viewCount
    )
}

/** One page of the endless Shorts feed plus the token for the next page. */
data class ShortsFeedPage(
    val items: List<ShortsItem>,
    val continuation: String?
)
