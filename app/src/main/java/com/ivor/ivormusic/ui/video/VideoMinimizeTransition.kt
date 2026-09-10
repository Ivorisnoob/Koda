package com.ivor.ivormusic.ui.video

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.ivor.ivormusic.data.VideoQuality

/**
 * Bind [player] to this view, or let go of it, as one of the two views the
 * minimize transition moves the picture between: the watch page's box and the
 * mini bar's frame.
 *
 * Only one view may hold the player's surface. The one letting go keeps its
 * last frame (`keepContentOnPlayerReset`) instead of closing the black shutter
 * `PlayerView` draws over an unbound player, so a hand-off in the middle of the
 * animation shows nothing at all; the one taking over shows its own old frame
 * until the first new one lands. The order inside each branch matters: the flag
 * goes up before the player is dropped and comes down only after it is bound,
 * or the shutter closes in between.
 *
 * The two views need no coordination between them. ExoPlayer clears a
 * TextureView only if it is the one being rendered into [verified September
 * 2026 against the Media3 1.11.0 bytecode, `ExoPlayerImpl.clearVideoTextureView`],
 * so whichever view's update runs first, the one taking over ends up bound.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerView.bindVideoSurface(player: Player?, holds: Boolean) {
    if (holds) {
        if (this.player !== player) this.player = player
        setKeepContentOnPlayerReset(false)
    } else {
        setKeepContentOnPlayerReset(true)
        this.player = null
    }
}

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
