package com.ivor.ivormusic.ui.video

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ivor.ivormusic.ui.player.rememberPlayerHaptics

/** Height of the collapsed bar. Shared with the overlay that sizes it. */
val MINI_VIDEO_HEIGHT = 88.dp

/**
 * Gap between the collapsed bar and whatever is beneath it - the system
 * navigation inset, or the host's own bottom chrome when it has any.
 */
val MINI_VIDEO_MARGIN = 16.dp

/**
 * The video player's collapsed bar: the video still playing, what it is, and
 * the two controls worth reaching for without opening the player.
 *
 * **There is no close button.** A downward drag on the bar dismisses it and an
 * upward one expands it, both handled by [VideoPlayerOverlay] which owns the
 * bar's position; a close button would spend a fifth target on the thing the
 * gesture already does, in a row that has a video, two lines of text and two
 * buttons in 88dp. The gesture is the same one the expanded player already
 * answers, so it is not a new thing to learn.
 *
 * Nothing here polls. Position comes off [VideoPlayerViewModel.progress], which
 * the expanded player's scrubber already drives, replacing a one-second loop
 * that stepped the bar in visible jumps and stopped updating the moment
 * playback paused - so a seek while paused left the hairline behind.
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniVideoPlayerContent(viewModel: VideoPlayerViewModel) {
    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isLive by viewModel.isLive.collectAsState()
    val isPortrait by viewModel.isPortraitVideo.collectAsState()

    val video = currentVideo ?: return
    val haptics = rememberPlayerHaptics()

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniVideoSurface(
                viewModel = viewModel,
                isBuffering = isBuffering,
                isPortrait = isPortrait
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // A live broadcast has no position to report, so the line
                    // that would carry one says what it is instead, in the
                    // accent rather than the red every other app uses: a
                    // hardcoded red is what the palette system exists to
                    // prevent, and the word already reads as live on its own.
                    text = if (isLive) {
                        listOf("LIVE", video.channelName)
                            .filter { it.isNotBlank() }
                            .joinToString("  •  ")
                    } else {
                        video.channelName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Play/pause stays live while buffering rather than being
            // replaced by a spinner the way the music pill's is. Buffering is
            // already reported on the video itself a few dp to the left, and
            // two indicators for one state read as two problems; pausing a
            // video that is still loading is also a thing people do on purpose.
            FilledIconButton(
                onClick = {
                    haptics.playPause(!isPlaying)
                    viewModel.togglePlayPause()
                },
                modifier = Modifier.size(44.dp),
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Next is hidden rather than disabled off the end of a queue, and
            // absent entirely without one: a permanently dead target costs
            // more room here than it is worth. The expanded player disables
            // its pair instead, where there is space for both to stay put.
            if (queue?.hasNext == true) {
                Spacer(modifier = Modifier.width(6.dp))
                FilledIconButton(
                    onClick = {
                        haptics.skip()
                        viewModel.playNextInQueue()
                    },
                    modifier = Modifier.size(44.dp),
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next in playlist",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // A hairline rather than a LinearProgressIndicator: M3's now draws a
        // stop indicator and a gap at the active end, which at 3dp inside a
        // rounded bar reads as a rendering fault rather than as progress.
        // Hidden on live, where there is no end to be a fraction of.
        if (!isLive) {
            val shownProgress by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "miniVideoProgress"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shownProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/**
 * The playing video itself, at bar size.
 *
 * **Fit, not zoom, for a portrait source.** The frame is 16:9 and a vertical
 * video cropped to it loses its top and bottom, which is exactly where a
 * vertical upload puts faces and captions - the same reason the watch page
 * letterboxes past its crop limit rather than filling. A landscape video still
 * fills, since cropping a 16:9 source into a 16:9 frame removes nothing.
 */
@OptIn(UnstableApi::class)
@Composable
private fun MiniVideoSurface(
    viewModel: VideoPlayerViewModel,
    isBuffering: Boolean,
    isPortrait: Boolean
) {
    val resizeMode = remember(isPortrait) {
        if (isPortrait) AspectRatioFrameLayout.RESIZE_MODE_FIT
        else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    Box(
        modifier = Modifier
            .width(80.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            // Letterbox bars are the absence of picture, not a themed surface,
            // so they stay black in either theme. The documented exception to
            // the palette rule, same as the watch page's own video box.
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.exoPlayer
                    useController = false
                }
            },
            update = { pv ->
                // Re-attached rather than only bound at construction: error
                // recovery can hand the ViewModel a new ExoPlayer, and a view
                // still holding the old one shows a frozen last frame.
                if (pv.player !== viewModel.exoPlayer) pv.player = viewModel.exoPlayer
                pv.resizeMode = resizeMode
            },
            // Release the shared player before this view's Surface is
            // destroyed, so expanding/collapsing hands the surface over
            // cleanly instead of racing the full player's PlayerView.
            onRelease = { pv -> pv.player = null },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            // The watch page's own choice over video: it draws its own
            // container, so it reads over any frame without a colour picked
            // against the picture.
            ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
        }
    }
}
