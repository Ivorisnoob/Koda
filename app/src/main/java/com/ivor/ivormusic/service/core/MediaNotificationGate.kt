package com.ivor.ivormusic.service.core

/**
 * Everything the media notification draws, and nothing it does not.
 *
 * Position is left out on purpose: the system draws the progress bar from the
 * session's playback state, which reaches it without a notification post.
 * The player is identified because a crossfade hands the session a different
 * engine, and the notification's actions have to be rebuilt against it.
 */
data class MediaNotificationState(
    val playerIdentity: Int,
    val mediaId: String?,
    val queueItemId: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val playWhenReady: Boolean,
    val playbackState: Int,
    val suppressionReason: Int,
    val isPlaying: Boolean,
    val speed: Float,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
    val canPlayPause: Boolean,
    val repeatMode: Int,
    val liked: Boolean,
    val promoted: Boolean,
)

/**
 * Lets a media notification be posted only when what it draws has changed.
 *
 * Media3 rebuilds the notification on every timeline change, and a timeline
 * change is any queue edit: each prefetched stream swapped into an upcoming
 * row, a shuffle order applied, a song added. Every rebuild is a fresh
 * `notify()` carrying the cover bitmap again, and apps that watch
 * notifications read each one as a new song or new artwork [verified October
 * 2026 against media3-session 1.11.0's MediaNotificationManager, which posts
 * without comparing]. The cover arriving after its bitmap loads is a separate
 * callback in Media3 and is never held back by this.
 */
class MediaNotificationGate {
    private var last: MediaNotificationState? = null

    /** Whether [state] differs from what was last posted. Records it when it does. */
    fun shouldPost(state: MediaNotificationState): Boolean {
        if (state == last) return false
        last = state
        return true
    }

    /** Forget what was posted, so the next request goes out whatever it holds. */
    fun reset() {
        last = null
    }
}
