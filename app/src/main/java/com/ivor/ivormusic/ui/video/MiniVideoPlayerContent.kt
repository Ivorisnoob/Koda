package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource

import android.view.LayoutInflater
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
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ivor.ivormusic.R
import com.ivor.ivormusic.ui.player.rememberPlayerHaptics

/** Height of the collapsed bar. Shared with the overlay that sizes it. */
val MINI_VIDEO_HEIGHT = 88.dp

/**
 * Gap between the collapsed bar and whatever is beneath it - the system
 * navigation inset, or the host's own bottom chrome when it has any.
 */
val MINI_VIDEO_MARGIN = 16.dp

/**
 * Width of the video preview. Sized so a 16:9 frame is 52dp tall inside an
 * 88dp bar - enough that the picture reads as picture - while leaving the
 * title room to say something on a narrow screen.
 */
internal val MINI_VIDEO_THUMB_WIDTH = 92.dp

/** Inset of the bar's content from the card edge. */
internal val MINI_VIDEO_BAR_PADDING = 12.dp

/** Rounding of the video frame inside the bar. */
internal val MINI_VIDEO_THUMB_CORNER = 12.dp

/** Rounding of the bar itself. */
internal val MINI_VIDEO_BAR_CORNER = 28.dp

/**
 * The video player's collapsed bar: the video still playing, what it is, and
 * the two controls worth reaching for without opening the player.
 *
 * Play/pause and Close are the durable actions here. The downward dismiss
 * gesture remains as a shortcut, but is no longer the only way to stop and
 * remove a persistent player. Queue transport remains in the expanded player,
 * where previous and next can stay together and keep their positions.
 *
 * Nothing here polls. Position comes off [VideoPlayerViewModel.progress], which
 * the expanded player's scrubber already drives, replacing a one-second loop
 * that stepped the bar in visible jumps and stopped updating the moment
 * playback paused - so a seek while paused left the hairline behind.
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniVideoPlayerContent(
    viewModel: VideoPlayerViewModel,
    /**
     * False while the minimize transition is still carrying the picture into
     * this frame. Only one view may hold the player's surface, so during the
     * hand-off the bar draws everything except the video and the travelling
     * watch page supplies the picture, landing exactly on the empty frame.
     */
    showSurface: Boolean = true,
) {
    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isLive by viewModel.isLive.collectAsState()
    val isPortrait by viewModel.isPortraitVideo.collectAsState()

    val video = currentVideo ?: return
    val haptics = rememberPlayerHaptics()
    val liveLabel = stringResource(R.string.badge_live)
    val playLabel = stringResource(R.string.cd_play)
    val pauseLabel = stringResource(R.string.cd_pause)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MINI_VIDEO_BAR_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniVideoSurface(
            viewModel = viewModel,
            isBuffering = isBuffering,
            isPortrait = isPortrait,
            isLive = isLive,
            progress = progress,
            showSurface = showSurface
        )

        Spacer(modifier = Modifier.width(12.dp))

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
                text = when {
                    isLive ->
                        listOf(liveLabel, video.channelName)
                            .filter { it.isNotBlank() }
                            .joinToString("  •  ")
                    else -> video.channelName
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
                contentDescription = if (isPlaying) pauseLabel else playLabel,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))
        FilledIconButton(
            onClick = { viewModel.closePlayer() },
            modifier = Modifier.size(44.dp),
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.mv_close_player),
                modifier = Modifier.size(22.dp)
            )
        }
    }

}

/**
 * The playing video itself, at bar size, with its position along the bottom.
 *
 * **A TextureView, not the SurfaceView `PlayerView` builds by default.** A
 * SurfaceView is composited in its own layer behind the app window, so it
 * cannot be clipped by the card around it: its opaque background slab bled out
 * below the mini bar as a hard dark rectangle, and what looked like a rounded
 * video was the card showing through a square hole. `surface_type` is only
 * settable in the constructor, which is the whole reason `mini_video_surface`
 * is a layout file. The portrait watch page now does the same, because the
 * minimize transition scales and rounds the picture on its way into this frame;
 * fullscreen and HDR keep their SurfaceView.
 *
 * **Fit, not zoom, for a portrait source.** The frame is 16:9 and a vertical
 * video cropped to it loses its top and bottom, which is exactly where a
 * vertical upload puts faces and captions - the same reason the watch page
 * letterboxes past its crop limit rather than filling. A landscape video still
 * fills, since cropping a 16:9 source into a 16:9 frame removes nothing.
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniVideoSurface(
    viewModel: VideoPlayerViewModel,
    isBuffering: Boolean,
    isPortrait: Boolean,
    isLive: Boolean,
    progress: Float,
    showSurface: Boolean
) {
    val resizeMode = remember(isPortrait) {
        if (isPortrait) AspectRatioFrameLayout.RESIZE_MODE_FIT
        else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    Box(
        modifier = Modifier
            .width(MINI_VIDEO_THUMB_WIDTH)
            .aspectRatio(16f / 9f)
            // PiP entry from the collapsed player must animate from the video
            // thumbnail, never from a stale expanded-player rectangle or the
            // whole activity. Window coordinates are what Android expects.
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                viewModel.setMiniVideoSurfaceBounds(
                    android.graphics.Rect(
                        bounds.left.toInt(),
                        bounds.top.toInt(),
                        bounds.right.toInt(),
                        bounds.bottom.toInt(),
                    )
                )
            }
            .clip(RoundedCornerShape(MINI_VIDEO_THUMB_CORNER))
            // Letterbox bars are the absence of picture, not a themed surface,
            // so they stay black in either theme. The documented exception to
            // the palette rule, same as the watch page's own video box.
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (showSurface) AndroidView(
            factory = { ctx ->
                LayoutInflater.from(ctx)
                    .inflate(R.layout.mini_video_surface, null) as PlayerView
            },
            update = { pv ->
                // Re-attached rather than only bound at construction: error
                // recovery can hand the ViewModel a new ExoPlayer, and a view
                // still holding the old one shows a frozen last frame.
                if (pv.player !== viewModel.exoPlayer) pv.player = viewModel.exoPlayer
                pv.resizeMode = resizeMode
            },
            // Release the shared player before this view's surface goes away,
            // so expanding and collapsing hand it over cleanly instead of
            // racing the full player's own view for it.
            onRelease = { pv -> pv.player = null },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            // The watch page's own choice over video: it draws its own
            // container, so it reads over any frame without a colour picked
            // against the picture.
            ContainedLoadingIndicator(modifier = Modifier.size(28.dp))
        }

        // Position rides the bottom edge of the picture, the way it does on a
        // thumbnail everywhere else in the app, rather than floating as a
        // hairline across the card. On a 28dp-rounded card a full-bleed line
        // gets visibly cut at both ends, and an inset one reads as unattached
        // to anything. Live has no end to be a fraction of, so it shows none.
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
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shownProgress)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
