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

    // ---------------- Editing ----------------
    //
    // Every edit below keeps [index] pointing at the *same video*, and that is
    // the whole reason this arithmetic lives on the data class rather than in
    // the ViewModel: [index] is what the player, the next/previous buttons, the
    // "Playing from" card and the queue sheet's highlight all read, so an edit
    // that moves the list without moving the index silently re-points playback
    // at a different video. There is no id to fall back on either - a playlist
    // may list the same video twice, which is why this is index-addressed in
    // the first place.

    /**
     * Move the entry at [from] to [to].
     *
     * Four cases, and the three that are not "the moved row is the current one"
     * are the ones easy to get wrong: dragging a row from above the current one
     * to below it shifts the current up by one, and the reverse shifts it down.
     */
    fun moved(from: Int, to: Int): VideoQueue {
        if (from !in videos.indices || to !in videos.indices || from == to) return this
        val reordered = videos.toMutableList().apply { add(to, removeAt(from)) }
        val newIndex = when {
            index == from -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        return copy(videos = reordered, index = newIndex)
    }

    /**
     * Drop the entry at [at].
     *
     * @return null when the removal is refused, which is the caller's cue to
     * offer no control at all rather than one that fails. Two cases refuse:
     * the last remaining entry, because a queue of nothing has no meaning while
     * a video is still playing; and **the entry that is playing**, because
     * video mode has no playback service to hand off to - the player would go
     * on playing a video the queue no longer contains, and every count and
     * control that reads [index] would be describing something else.
     */
    fun removedAt(at: Int): VideoQueue? {
        if (at !in videos.indices) return null
        if (videos.size <= 1 || at == index) return null
        return copy(
            videos = videos.toMutableList().apply { removeAt(at) },
            index = if (at < index) index - 1 else index
        )
    }

    fun canRemoveAt(at: Int): Boolean = videos.size > 1 && at != index && at in videos.indices

    /** Insert [items] at [at], keeping [index] on the video that is playing. */
    fun withInserted(items: List<VideoItem>, at: Int): VideoQueue {
        if (items.isEmpty()) return this
        val position = at.coerceIn(0, videos.size)
        return copy(
            videos = videos.toMutableList().apply { addAll(position, items) },
            index = if (position <= index) index + items.size else index
        )
    }

    /** Straight after what is playing: "play this next". */
    fun withPlayingNext(items: List<VideoItem>): VideoQueue =
        withInserted(items, index + 1)

    /** At the end: "play this eventually". */
    fun withAppended(items: List<VideoItem>): VideoQueue =
        withInserted(items, videos.size)

    companion object {
        /**
         * What a queue is called when the user built it by adding videos rather
         * than by opening a playlist. [playlistId] stays null, which everything
         * downstream already tolerates - there is no published playlist behind
         * it to share or re-open.
         */
        const val AD_HOC_TITLE = "Your queue"

        /** The first "add to queue" with nothing but a playing video to build on. */
        fun adHoc(current: VideoItem, added: List<VideoItem>): VideoQueue = VideoQueue(
            videos = listOf(current) + added,
            index = 0,
            title = AD_HOC_TITLE
        )
    }
}
