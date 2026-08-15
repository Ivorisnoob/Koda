package com.ivor.ivormusic.data

/**
 * An ordered run of videos the user is watching *through*, and where they are
 * in it.
 *
 * The video pipeline has never had one. `VideoPlayerViewModel` holds a single
 * `currentVideo` and its ExoPlayer holds a single media item, so opening a
 * playlist and tapping the third video played that video and nothing else:
 * when it ended, auto-play took the first *related* video, which is a
 * recommendation and almost never the fourth entry in the playlist. There was
 * also nothing on screen that knew the playlist existed, so there was no way
 * back to it without leaving the player.
 *
 * This is that missing context, and it is deliberately a plain snapshot rather
 * than a live view of whatever list it came from: the source list is owned by
 * `HomeViewModel` and is reused by the next playlist the user opens, so a
 * queue reading through to it would silently re-point mid-playback.
 *
 * **[index] addresses the queue, not [VideoItem.videoId].** A YouTube playlist
 * may legitimately list the same video twice, so the id is not a key here -
 * which is why every jump goes through the index and why the player is told to
 * restart rather than being left to compare ids.
 */
data class VideoQueue(
    val videos: List<VideoItem>,
    val index: Int,
    /** Playlist name, shown on the "Playing from" card and the queue sheet. */
    val title: String,
    /** Null for a queue that is not a real YouTube playlist. */
    val playlistId: String? = null
) {
    val current: VideoItem? get() = videos.getOrNull(index)

    val hasNext: Boolean get() = index >= 0 && index < videos.lastIndex

    val hasPrevious: Boolean get() = index > 0

    /** "3 / 24", for the card and the sheet header. */
    val positionLabel: String get() = "${index + 1} / ${videos.size}"

    /** The same queue at another position, clamped to the list. */
    fun at(newIndex: Int): VideoQueue = copy(index = newIndex.coerceIn(0, videos.lastIndex))
}
