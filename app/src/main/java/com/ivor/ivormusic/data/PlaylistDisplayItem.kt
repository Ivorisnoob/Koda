package com.ivor.ivormusic.data

data class PlaylistDisplayItem(
    val name: String,
    val url: String,
    val uploaderName: String,
    val itemCount: Int = -1,
    val thumbnailUrl: String? = null,
    val description: String? = null
) {
    val id: String
        get() = when {
            url.contains("list=") -> url.substringAfter("list=").substringBefore("&")
            // Album pages use browse ids (MPREb…) instead of playlist ids
            url.contains("/browse/") -> url.substringAfter("/browse/").substringBefore("?")
            else -> url
        }
}

/**
 * What a playlist says it is - title, author, cover, length - independent of
 * its contents.
 *
 * This is the one thing a playlist *link* does not carry. A shared URL is an
 * id and nothing else, so opening the page it names means asking YouTube what
 * the playlist actually is; everywhere else in the app a playlist arrives from
 * a list that already described it (a search result, a lockup, the account's
 * own playlists).
 *
 * Mode-neutral for the same reason [SavedPlaylist] is: one playlist id opens
 * as songs in music mode and as videos in video mode, and a shared link cannot
 * know which one the reader is in.
 */
data class PlaylistPageInfo(
    val playlistId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String? = null,
    /** -1 is "the page did not say", never "empty". */
    val itemCount: Int = -1
) {
    fun toDisplayItem(): PlaylistDisplayItem = PlaylistDisplayItem(
        name = title,
        // The canonical form, because [PlaylistDisplayItem] derives the id back
        // out of the url and everything downstream keys off that id.
        url = "https://www.youtube.com/playlist?list=$playlistId",
        uploaderName = author,
        itemCount = itemCount,
        thumbnailUrl = thumbnailUrl
    )

    fun toVideoPlaylist(): VideoPlaylist = VideoPlaylist(
        playlistId = playlistId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        videoCountText = when {
            itemCount == 1 -> "1 video"
            itemCount > 1 -> "$itemCount videos"
            else -> null
        },
        subtitle = author.takeIf { it.isNotBlank() }
    )
}
