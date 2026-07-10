package com.ivor.ivormusic.data

/**
 * One YouTube Short in a feed or swipe sequence.
 *
 * Shelf items (shortsLockupViewModel) carry title/view count; entries from
 * reel_watch_sequence carry only the id and thumbnail — the player enriches
 * them from the watch-next response once the Short is actually opened.
 */
data class ShortsItem(
    val videoId: String,
    val title: String = "",
    val viewCount: String = "",
    val thumbnailUrl: String? = null,
    /**
     * Params seeding the endless reel_watch_sequence feed starting at this
     * Short. Present on shelf items, absent on sequence entries.
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
