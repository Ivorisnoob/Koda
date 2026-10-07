package com.ivor.ivormusic.data

/**
 * Which of the video player's optional gestures are on. All of them by
 * default, which is the player as it shipped before these were settings.
 *
 * Only the gestures people trip by accident are here. Double-tap to skip,
 * pinch to fill and the swipes in and out of fullscreen are how the player is
 * operated and stay on.
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
) {
    /** How many of the three are switched off. */
    val offCount: Int
        get() = listOf(holdToSpeedUp, brightnessSwipe, volumeSwipe).count { !it }

    companion object {
        val HOLD_SPEEDS = listOf(2f, 3f, 4f)
        val CONTROLS_HIDE_SECONDS = listOf(2, 4, 8)
    }
}
