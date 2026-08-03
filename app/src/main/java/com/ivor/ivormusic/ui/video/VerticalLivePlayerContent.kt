package com.ivor.ivormusic.ui.video

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ivor.ivormusic.data.LiveChatMessage
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VttCue

/**
 * How much of the frame may be cropped away to fill the screen before the
 * layout gives up and letterboxes instead. A 9:16 stream on a 9:20 phone loses
 * about a fifth of its width, which is the bargain Shorts already makes; a 4:5
 * stream would lose closer to half, which is not.
 */
private const val MAX_ACCEPTABLE_CROP = 0.25f

/**
 * The full-bleed player for a vertical live stream.
 *
 * A vertical live stream is not a Short and not a separate kind of video - it
 * is an ordinary broadcast someone encoded at 9:16 (`/shorts/<id>` on one
 * resolves straight back to `/watch?v=`, verified August 2026). Only the shape
 * of the frame differs, and that shape is exactly what the standard watch
 * layout handles worst: a 9:16 source inside the 16:9 box uses about a third
 * of the width and pillarboxes the rest.
 *
 * So this trades the page for the picture. The video is the whole screen, chat
 * rides the bottom on a gradient rather than in a panel, and everything the
 * watch page offers - comments, related, description - is one tap away behind
 * [onExitToPage] rather than permanently occupying the lower half.
 *
 * Controls hide themselves; chat does not. Chat is the reason people sit on a
 * live stream, and making them tap to see it would be the wrong default.
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VerticalLivePlayerContent(
    exoPlayer: ExoPlayer,
    video: VideoItem,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    isPlaying: Boolean,
    isLoading: Boolean,
    isBuffering: Boolean,
    hasError: Boolean,
    errorMessage: String,
    progress: Float,
    bufferedProgress: Float,
    duration: Long,
    liveViewerCount: String?,
    chatMessages: List<LiveChatMessage>,
    isChatAvailable: Boolean?,
    canSendChat: Boolean,
    captionsActive: Boolean,
    captionCues: List<VttCue>,
    /** Null until the first frame decodes; drives the fill-vs-fit decision. */
    videoAspectRatio: Float?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToLive: () -> Unit,
    onBack: () -> Unit,
    onExitToPage: () -> Unit,
    onOpenFullChat: () -> Unit,
    onCaptionsClick: () -> Unit,
    onSettings: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onMinimizeDragDelta: (Float) -> Unit = {},
    onMinimizeDragRelease: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val stableShapes = IconButtonDefaults.shapes()
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }

    // A 9:16 source on a taller phone cannot both fill the screen and keep its
    // edges. Cropping the sides is the Shorts overlay's existing bargain and
    // the one that reads as "full screen", so it is preferred here - but only
    // while the crop stays mild. "Vertical" covers 4:5 and 1:1 streams too, and
    // zooming one of those to fill a 9:20 phone would cut the top and bottom
    // off the frame; past the threshold, letterboxing is the lesser evil.
    val configuration = LocalConfiguration.current
    val screenAspect = configuration.screenWidthDp.toFloat() /
        configuration.screenHeightDp.toFloat().coerceAtLeast(1f)
    val resizeMode = remember(screenAspect, videoAspectRatio) {
        val videoAspect = videoAspectRatio?.takeIf { it > 0f }
        if (videoAspect == null) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            // Filling the screen scales by the larger factor, so the fraction
            // lost off the long edge is what the two aspects differ by.
            val croppedFraction = 1f - (minOf(screenAspect, videoAspect) /
                maxOf(screenAspect, videoAspect))
            if (croppedFraction <= MAX_ACCEPTABLE_CROP) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        PlayerGestureSurface(
            onToggleControls = onToggleControls,
            onSeekBackward = onSeekBackward,
            onSeekForward = onSeekForward,
            onSpeedBoostStart = {
                speedBeforeBoost = exoPlayer.playbackParameters.speed
                exoPlayer.setPlaybackSpeed(2f)
            },
            onSpeedBoostEnd = { exoPlayer.setPlaybackSpeed(speedBeforeBoost) },
            // Swipe down still drops the whole thing into the mini player, the
            // same as the standard portrait layout.
            minimizeDragEnabled = true,
            onMinimizeDragDelta = onMinimizeDragDelta,
            onMinimizeDragRelease = onMinimizeDragRelease,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.resizeMode = resizeMode
                },
                // Hand the surface back before this view is destroyed - the same
                // ExoPlayer is also rendered by the mini and PiP PlayerViews.
                onRelease = { playerView -> playerView.player = null },
                modifier = Modifier.fillMaxSize()
            )

            // Lifted clear of the chat ticker rather than sitting behind it -
            // but only as far as the ticker actually reaches. A stream with
            // chat turned off draws none of it, and captions stranded in the
            // middle of the frame would be the only trace left of it.
            CaptionOverlay(
                cues = captionCues,
                player = exoPlayer,
                bottomPadding = if (isChatAvailable == false) 96.dp else 300.dp,
                compact = true
            )

            if (hasError) ErrorOverlay(errorMessage, onRetry)

            if (isLoading || (isBuffering && !showControls)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.ContainedLoadingIndicator()
                }
            }

            // Top chrome: always up. The back and collapse affordances are the
            // only way out of a layout that has no visible page behind it, so
            // hiding them on the controls timer would strand the user.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(0.4f),
                        contentColor = Color.White
                    ),
                    shapes = stableShapes
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }

                LiveBadge(
                    viewerCount = liveViewerCount,
                    onVideo = true,
                    modifier = Modifier.weight(1f)
                )

                FilledTonalIconButton(
                    onClick = onCaptionsClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (captionsActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Black.copy(0.4f)
                        },
                        contentColor = if (captionsActive) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            Color.White
                        }
                    ),
                    shapes = stableShapes
                ) {
                    Icon(
                        if (captionsActive) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                        contentDescription = "Captions"
                    )
                }

                FilledIconButton(
                    onClick = onSettings,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(0.4f),
                        contentColor = Color.White
                    ),
                    shapes = stableShapes
                ) {
                    Icon(Icons.Rounded.Settings, "Quality")
                }

                // The way back to the watch page: comments, related, the
                // description. The counterpart button on that page returns here.
                FilledIconButton(
                    onClick = onExitToPage,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(0.4f),
                        contentColor = Color.White
                    ),
                    shapes = stableShapes
                ) {
                    Icon(Icons.Rounded.CloseFullscreen, "Show video details")
                }
            }

            // Play/pause and the seek bar are the parts that earn their keep by
            // disappearing - they are only wanted when the user reaches for them.
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        ExpressivePlayPauseButton(
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onClick = onPlayPause
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (video.channelName.isNotBlank()) {
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                LiveChatOverlay(
                    messages = chatMessages,
                    isAvailable = isChatAvailable,
                    canSend = canSendChat,
                    onOpenFullChat = onOpenFullChat,
                )

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PlayerSeekBar(
                            progress = progress,
                            bufferedProgress = bufferedProgress,
                            onSeek = onSeek,
                            modifier = Modifier.weight(1f),
                            durationMs = duration
                        )
                        LiveEdgeChip(
                            atLiveEdge = duration <= 0L || progress >= LIVE_EDGE_THRESHOLD,
                            onClick = onSeekToLive
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

