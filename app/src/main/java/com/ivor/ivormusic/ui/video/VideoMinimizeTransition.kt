package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.data.VideoQuality

/**
 * Whether the playing rendition can take the animated minimize: the music
 * player's container transform, the collapsed bar growing into the watch page
 * (`PlayerContainerTransform.kt`).
 *
 * The transform clips the page to a rounded, growing rectangle and fades it in,
 * and a SurfaceView follows neither - it is composited in its own layer behind
 * the app window. HDR needs a SurfaceView for output, so those sessions keep
 * the curtain. Read by both the page that chooses the surface type and the
 * overlay that chooses the transition, because the two disagreeing would
 * animate a frame the video is not in.
 */
internal fun supportsAnimatedMinimize(quality: VideoQuality?): Boolean =
    quality?.isHdr != true
