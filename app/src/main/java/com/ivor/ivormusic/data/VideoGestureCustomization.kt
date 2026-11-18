package com.ivor.ivormusic.data

/**
 * What flanks play and pause on the video.
 *
 * Stored ids are frozen. [SKIP] is the player as it shipped: ten-second skips
 * in fullscreen, and previous/next only while a playlist is running.
 */
enum class VideoCentreButtons(val id: String) {
    SKIP("skip"),
    PREVIOUS_NEXT("previous_next"),
    BOTH("both");

    companion object {
        fun fromId(raw: String?): VideoCentreButtons =
            entries.firstOrNull { it.id == raw } ?: SKIP
    }
}

/**
 * The timestamps under the seek bar. Stored ids are frozen.
 * [ELAPSED_TOTAL] is the player as it shipped.
 */
enum class VideoTimeDisplay(val id: String) {
    ELAPSED_TOTAL("elapsed_total"),
    ELAPSED_REMAINING("elapsed_remaining");

    companion object {
        fun fromId(raw: String?): VideoTimeDisplay =
            entries.firstOrNull { it.id == raw } ?: ELAPSED_TOTAL
    }
}

/**
 * Which of the video player's optional gestures are on, and which controls
 * the chrome shows. Gestures default to on, and the controls default to the
 * player as it shipped before these were settings.
 *
 * Pinch to fill and the swipes in and out of fullscreen stay on: they are how
 * the player is operated. Double-tap to skip stays on too, and moves by
 * [skipSeconds].
 */
data class VideoGestureCustomization(
    /** Touch and hold to play faster until the finger lifts. */
    val holdToSpeedUp: Boolean = true,
    /** A vertical swipe on the left of a fullscreen video. */
    val brightnessSwipe: Boolean = true,
    /** A vertical swipe on the right of a fullscreen video. */
    val volumeSwipe: Boolean = true,
    /**
     * The rate a hold starts at, one of [HOLD_SPEEDS]. Sliding the held finger
     * still reaches every other rate, so this is where it begins, not a cap.
     */
    val holdSpeed: Float = 2f,
    /** Seconds the controls stay up over a playing video, one of [CONTROLS_HIDE_SECONDS]. */
    val controlsHideSeconds: Int = 4,
    /** What flanks play and pause. One of [VideoCentreButtons]. */
    val centreButtons: VideoCentreButtons = VideoCentreButtons.SKIP,
    /** Seconds per skip, one of [SKIP_SECONDS]. Shared by the buttons and double-tap. */
    val skipSeconds: Int = 10,
    /** Elapsed over the length, or elapsed over the time left. */
    val timeDisplay: VideoTimeDisplay = VideoTimeDisplay.ELAPSED_TOTAL,
    /** The CC button. Captions themselves stay in playback settings. */
    val showCaptions: Boolean = true,
    /** The fullscreen button. Swiping the video still enters and leaves it. */
    val showFullscreen: Boolean = true,
    /** The lock on the fullscreen player. */
    val showScreenLock: Boolean = true,
    /**
     * A picture-in-picture button on the chrome. Off, because the player did
     * not have one: the playback-settings tile and auto-enter still open the window.
     */
    val showPipButton: Boolean = false,
    /** A button that opens the playback-speed page. Off, so the bar does not grow. */
    val showSpeedShortcut: Boolean = false,
) {
    /** How many of the three gesture toggles are switched off. */
    val offCount: Int
        get() = listOf(holdToSpeedUp, brightnessSwipe, volumeSwipe).count { !it }

    companion object {
        val HOLD_SPEEDS = listOf(2f, 3f, 4f)
        val CONTROLS_HIDE_SECONDS = listOf(2, 4, 8)
        val SKIP_SECONDS = listOf(5, 10, 15, 30)

        /**
         * A previous press inside this window leaves the video for the previous
         * playlist item. Further in, the same press restarts the video.
         */
        const val PREVIOUS_RESTART_WINDOW_MS = 3_000L
    }
}
