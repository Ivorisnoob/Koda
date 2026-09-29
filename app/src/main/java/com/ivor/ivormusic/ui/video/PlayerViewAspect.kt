package com.ivor.ivormusic.ui.video

import android.view.SurfaceView
import android.view.View
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ivor.ivormusic.R
import kotlin.math.roundToInt

/**
 * Give this view's content frame the video's shape, [aspectRatio], whenever the
 * player itself reports no video size, and hold a SurfaceView's buffer at the
 * video's fitted size.
 *
 * With Smooth motion's effect graph installed the player never reports a size.
 * The renderer hands the graph's size to its video-sink listener, and in
 * Media3 1.11.0 that listener's `onVideoSizeChanged` is an empty method
 * (`MediaCodecVideoRenderer$1`), with nothing else listening [verified
 * September 2026 against the 1.11.0 bytecode]. `Player.videoSize` stays 0x0,
 * so `PlayerView` gives its [AspectRatioFrameLayout] no ratio, the frame fills
 * the whole view and the graph's last stage letterboxes the picture inside the
 * surface. Fit still looks right; zoom changes nothing, which is how "Zoomed to
 * fill" came to show while the picture stayed put.
 *
 * The page already knows the shape from the stream or the file
 * ([VideoPlayerViewModel.videoAspectRatio]), so it is supplied here. A size the
 * player does report always wins. `PlayerView` sets the frame back to 0 in
 * `setPlayer` and on every unknown size the renderer reports (it sends one when
 * it resets), so call this after binding the player; the listener it keeps
 * puts the shape back after the reset.
 *
 * The buffer: the graph draws each frame at the output surface's size, so a
 * surface that resizes - which is all a zoom is - makes it re-configure, and
 * the frames drawn meanwhile come out at the old size, anchored bottom-left
 * (GL's origin) [judgement September 2026, from the device report: a flicker
 * on every zoom]. Fixed at the fitted size, the buffer does not change on a
 * zoom; the compositor scales it into the larger frame instead. Without the
 * graph this changes nothing visible: the decoder scales into the buffer as it
 * scaled into the view.
 *
 * Pair with [releaseKnownAspectRatio] in the view's `onRelease`, or the
 * listener keeps the view reachable from the player.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerView.keepKnownAspectRatio(aspectRatio: Float?) {
    val keeper = getTag(R.id.known_video_aspect_ratio) as? KnownAspectRatio
        ?: KnownAspectRatio(this).also { setTag(R.id.known_video_aspect_ratio, it) }
    keeper.ratio = aspectRatio?.takeIf { it.isFinite() && it > 0f }
    keeper.follow(player)
    keeper.apply()
    keeper.fixSurfaceBuffer()
}

/** Stop keeping the shape; the listener leaves the player. */
internal fun PlayerView.releaseKnownAspectRatio() {
    (getTag(R.id.known_video_aspect_ratio) as? KnownAspectRatio)?.detach()
    setTag(R.id.known_video_aspect_ratio, null)
}

@androidx.annotation.OptIn(UnstableApi::class)
private class KnownAspectRatio(private val view: PlayerView) : Player.Listener {
    var ratio: Float? = null
    private var player: Player? = null
    private var fixedBuffer: Pair<Int, Int>? = null

    // The fitted size follows the view (rotation, a docked chat column), not
    // the content frame, which is the part a zoom resizes.
    private val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
        if (r - l != oldR - oldL || b - t != oldB - oldT) fixSurfaceBuffer()
    }

    init {
        view.addOnLayoutChangeListener(layoutListener)
    }

    fun follow(next: Player?) {
        if (player === next) return
        player?.removeListener(this)
        next?.addListener(this)
        player = next
    }

    fun detach() {
        follow(null)
        view.removeOnLayoutChangeListener(layoutListener)
    }

    fun apply() {
        val reported = view.player?.videoSize
        if (reported != null && reported.width > 0 && reported.height > 0) return
        val known = ratio ?: return
        view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            ?.setAspectRatio(known)
    }

    fun fixSurfaceBuffer() {
        val surface = view.videoSurfaceView as? SurfaceView ?: return
        val known = ratio
        val width = view.width
        val height = view.height
        if (known == null || width <= 0 || height <= 0) {
            if (fixedBuffer != null) {
                fixedBuffer = null
                surface.holder.setSizeFromLayout()
            }
            return
        }
        val fitted = if (width.toFloat() / height > known) {
            (height * known).roundToInt() to height
        } else {
            width to (width / known).roundToInt()
        }
        if (fitted == fixedBuffer) return
        fixedBuffer = fitted
        surface.holder.setFixedSize(fitted.first.coerceAtLeast(1), fitted.second.coerceAtLeast(1))
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        // PlayerView's own listener resets the frame for this same event, and
        // the order the two run in is not ours to choose - so after it.
        view.post { apply() }
    }
}
