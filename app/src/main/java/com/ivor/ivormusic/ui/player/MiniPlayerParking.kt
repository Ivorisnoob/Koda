package com.ivor.ivormusic.ui.player

/**
 * Whether the music mini player steps aside for the video one.
 *
 * A mode switch pauses the other player rather than closing it (see
 * `changePlaybackMode` in `MainActivity`), so both mini players used to sit on
 * Home together: in video mode a paused music pill, or its bubble, beside the
 * video bar, which read as clutter. In video mode the video bar is now the
 * only one shown, and the music player stays where it is - queue, position
 * and all - and comes back on the switch to music mode or when the video
 * player closes.
 *
 * Music that means to play (`playWhenReady`, so a song still loading counts)
 * is never hidden: those are the controls for what is coming out of the
 * speaker. Music mode keeps both, as before.
 *
 * One rule for both places that need it: `HomeScreen`, which draws the pill
 * and reserves room for it, and `MainActivity`, which stacks the video bar
 * above it. If the two disagreed the video bar would float over a gap.
 */
fun isMusicMiniParked(
    videoMode: Boolean,
    hasVideoMiniPlayer: Boolean,
    musicPlayWhenReady: Boolean,
): Boolean = videoMode && hasVideoMiniPlayer && !musicPlayWhenReady
