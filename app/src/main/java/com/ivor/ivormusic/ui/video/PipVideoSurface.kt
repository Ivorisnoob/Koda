package com.ivor.ivormusic.ui.video

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Everything the app draws while it is in system Picture-in-Picture: the video
 * and nothing else.
 *
 * This is rendered *instead of* the NavHost and the player overlays, not on top
 * of them. Previously the whole app carried on composing behind a full-screen
 * PlayerView, so home screens and animations kept running at PiP frame rate for
 * no visible benefit, and any gap around the video showed app chrome rather
 * than black.
 *
 * The window is already sized to the video's aspect ratio by
 * PictureInPictureParams, so RESIZE_MODE_FIT lands flush and the surrounding
 * black is only ever seen while the window is being resized.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PipVideoSurface(
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val exoPlayer = viewModel.exoPlayer ?: return
    val captionCues by viewModel.captionCues.collectAsState()
    val embeddedCueText by viewModel.embeddedCueText.collectAsState()
    val captionTextSize by viewModel.captionTextSize.collectAsState()
    val captionTextColor by viewModel.captionTextColor.collectAsState()
    val captionBackground by viewModel.captionBackground.collectAsState()

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    disableBuiltInSubtitles()
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // The PiP window is the video; a shutter over it during the
                    // hand-off from the full player's surface just reads as a
                    // black flash on entry.
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView -> playerView.player = exoPlayer },
            // Hand the surface back before this view is destroyed - the same
            // ExoPlayer is rendered by the full and mini PlayerViews too.
            onRelease = { playerView -> playerView.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // Captions used to come free with PlayerView's SubtitleView. They are
        // drawn by the app now, so PiP has to ask for them explicitly.
        CaptionOverlay(
            cues = captionCues,
            embeddedCueText = embeddedCueText,
            player = exoPlayer,
            bottomPadding = 8.dp,
            compact = true,
            textSize = captionTextSize,
            textColor = captionTextColor,
            background = captionBackground
        )
    }
}
