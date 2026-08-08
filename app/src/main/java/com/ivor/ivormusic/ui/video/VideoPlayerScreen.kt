package com.ivor.ivormusic.ui.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.roundToInt
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoChapter
import com.ivor.ivormusic.data.VideoEngagement
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VttCue
import com.ivor.ivormusic.data.WebVttParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

// VideoPlayerScreen function removed.
// Logic moved to VideoPlayerViewModel and VideoPlayerContent.
// Keeping helper composables for reuse.

// ---------------- Sub-Composables ----------------

/**
 * Draws the active caption cue over the video.
 *
 * Captions are rendered here rather than by PlayerView's built-in SubtitleView
 * because they are no longer part of the media source - sideloading them as a
 * text track meant every CC toggle rebuilt the source and dropped the whole
 * video buffer. Drawing them in Compose also sidesteps two problems the
 * SubtitleView had: cues no longer scale or slide off-screen with
 * RESIZE_MODE_ZOOM, and their distance from the bottom edge is a plain padding
 * value instead of a fight with per-cue positioning.
 *
 * Black-on-white here rather than ColorScheme: captions sit on video frames, so
 * they have to stay legible whatever the app theme is. Same reasoning as the
 * player controls above them.
 */
@Composable
internal fun CaptionOverlay(
    cues: List<VttCue>,
    player: ExoPlayer,
    bottomPadding: Dp,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    if (cues.isEmpty()) return

    // Polled off the player rather than the surrounding 500ms progress ticker:
    // at that rate a cue visibly lands after the words it belongs to.
    var text by remember(cues) { mutableStateOf<String?>(null) }
    LaunchedEffect(cues, player) {
        while (isActive) {
            text = WebVttParser.cueAt(cues, player.currentPosition)?.text
            delay(100)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Crossfade(
            targetState = text,
            animationSpec = tween(150),
            label = "captionCue",
            modifier = Modifier.padding(horizontal = 16.dp)
        ) { cue ->
            if (cue != null) {
                Box(
                    modifier = Modifier.padding(bottom = bottomPadding),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = cue,
                        color = Color.White,
                        style = if (compact) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@kotlin.OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullscreenPlayerContent(
    exoPlayer: ExoPlayer,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    hasError: Boolean,
    errorMessage: String,
    isLoading: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    isLooping: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    bufferedProgress: Float = 0f,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    onLoopToggle: () -> Unit,
    showTimedCommentsButton: Boolean = false,
    timedCommentsActive: Boolean = false,
    onTimedCommentsToggle: () -> Unit = {},
    chapters: List<VideoChapter> = emptyList(),
    onOpenChapters: () -> Unit = {},
    captionsActive: Boolean = false,
    onCaptionsClick: () -> Unit = {},
    captionCues: List<VttCue> = emptyList(),
    isLive: Boolean = false,
    liveChatActive: Boolean = false,
    onLiveChatToggle: () -> Unit = {},
    /** Jump to the live edge of the DVR window. */
    onSeekToLive: () -> Unit = {},
    /**
     * Width to keep clear on the trailing edge so the video sits beside the
     * docked chat column instead of centred underneath it. Only worth doing for
     * a portrait source: a 16:9 video already spans the screen, and shrinking
     * it to dodge an overlay that mostly covers letterbox bars would lose more
     * than it gains.
     */
    videoEndPadding: Dp = 0.dp,
    /**
     * True when fullscreen is being shown upright, for a vertical video.
     *
     * The top bar was laid out for a landscape window and does not fit a
     * portrait one: a back button, a title and up to five actions on a single
     * line need more width than a phone has on its short edge, so the title
     * collapses to nothing and the actions still run off the edge. Set, the
     * actions move to a row of their own underneath the title.
     */
    compactChrome: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    // Stable shapes to prevent "square flash"
    val stableShapes = IconButtonDefaults.shapes()

    // The top-bar actions, defined once and placed either beside the title or
    // on a line of their own. Landscape has width for one row and portrait
    // does not, and the alternative to hoisting them is the same five buttons
    // written twice.
    val topBarActions: @Composable RowScope.() -> Unit = {
        // Combined mode toggle: repeat off = auto-play next, repeat on = loop this video
        FilledTonalIconButton(
            onClick = onLoopToggle,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isLooping) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                contentColor = if (isLooping) MaterialTheme.colorScheme.onPrimary else Color.White
            ),
            shapes = stableShapes
        ) {
            Icon(
                if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Autorenew,
                contentDescription = if (isLooping) "Repeat" else "Auto Play"
            )
        }

        if (showTimedCommentsButton && !isLive) {
            FilledTonalIconButton(
                onClick = onTimedCommentsToggle,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (timedCommentsActive) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                    contentColor = if (timedCommentsActive) MaterialTheme.colorScheme.onPrimary else Color.White
                ),
                shapes = stableShapes
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Comment,
                    contentDescription = "Timed comments"
                )
            }
        }

        // Landscape is where a side-by-side chat actually fits, so
        // the toggle only exists here.
        if (isLive) {
            FilledTonalIconButton(
                onClick = onLiveChatToggle,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (liveChatActive) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                    contentColor = if (liveChatActive) MaterialTheme.colorScheme.onPrimary else Color.White
                ),
                shapes = stableShapes
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Chat,
                    contentDescription = if (liveChatActive) "Hide live chat" else "Show live chat"
                )
            }
        }

        FilledTonalIconButton(
            onClick = onCaptionsClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (captionsActive) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                contentColor = if (captionsActive) MaterialTheme.colorScheme.onPrimary else Color.White
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
                containerColor = Color.Black.copy(0.5f),
                contentColor = Color.White
            ),
            shapes = stableShapes
        ) {
            Icon(Icons.Rounded.Settings, "Quality")
        }

        FilledIconButton(
            onClick = onFullscreenToggle,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Black.copy(0.5f),
                contentColor = Color.White
            ),
            shapes = stableShapes
        ) {
            Icon(Icons.Rounded.FullscreenExit, "Exit Fullscreen")
        }
    }

    // Pinch-to-zoom: fill the screen (crop) vs fit inside it
    var isZoomedToFill by remember { mutableStateOf(false) }

    // Speed captured when a hold-to-2x begins, restored when the finger lifts
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }

    // Hold captions off the gesture bar / bottom bezel, and lift them over the
    // bottom bar while the controls are up.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val captionLift = animateDpAsState(
        targetValue = if (showControls) maxOf(112.dp, navBarInset + 24.dp) else navBarInset + 24.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "captionLift"
    )

    // Animated so the video slides aside as chat docks rather than jumping.
    val chatInsetAnimated by animateDpAsState(
        targetValue = videoEndPadding,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "videoEndPadding"
    )

    PlayerGestureSurface(
        onToggleControls = onToggleControls,
        onSeekBackward = onSeekBackward,
        onSeekForward = onSeekForward,
        fullscreenGesturesEnabled = true,
        onZoomedToFillChange = { isZoomedToFill = it },
        onSpeedBoostStart = {
            speedBeforeBoost = exoPlayer.playbackParameters.speed
            exoPlayer.setPlaybackSpeed(2f)
        },
        onSpeedBoostEnd = { exoPlayer.setPlaybackSpeed(speedBeforeBoost) },
        // Swipe down the middle of the video to come back to portrait, the
        // mirror of the swipe up that got here. Same callback as the toolbar's
        // fullscreen button.
        onExitFullscreen = onFullscreenToggle
    ) {
        // Video View
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
                playerView.resizeMode = if (isZoomedToFill) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            // Hand the surface back before this view is destroyed - the same
            // ExoPlayer is also rendered by the mini and PiP PlayerViews.
            onRelease = { playerView -> playerView.player = null },
            modifier = Modifier
                .fillMaxSize()
                .padding(end = chatInsetAnimated)
        )

        CaptionOverlay(
            cues = captionCues,
            player = exoPlayer,
            bottomPadding = captionLift.value,
            compact = false
        )

        // Overlays
        if (hasError) {
            ErrorOverlay(errorMessage, onRetry)
        } else if (isLoading || (isBuffering && !showControls)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
        }
        
        // Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(0.7f), Color.Transparent)))
                        // Cutout insets are stable, unlike status-bar insets which go
                        // 0 -> bar-height whenever another window (a bottom sheet)
                        // makes the hidden system bars reappear — statusBarsPadding
                        // here made the whole top bar jump down when a sheet opened
                        .displayCutoutPadding()
                        .padding(
                            horizontal = if (compactChrome) 16.dp else 24.dp,
                            vertical = 12.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledIconButton(
                            onClick = onBack,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(0.5f),
                                contentColor = Color.White
                            ),
                            shapes = stableShapes
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }

                        Text(
                            text = videoTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Landscape keeps the single row it always had.
                        if (!compactChrome) topBarActions()
                    }

                    if (compactChrome) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                space = 8.dp,
                                alignment = Alignment.End
                            ),
                            content = topBarActions
                        )
                    }
                }
                
                // Center Play/Pause
                Box(modifier = Modifier.align(Alignment.Center)) {
                    ExpressivePlayPauseButton(
                        isPlaying = isPlaying, 
                        isBuffering = isBuffering, 
                        onClick = onPlayPause,
                        size = 80.dp
                    )
                }
                
                // Bottom Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(0.8f))))
                        .padding(horizontal = 32.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chapters.isNotEmpty()) {
                        ChapterTitleChip(
                            chapters = chapters,
                            currentPositionMs = currentPosition,
                            onClick = onOpenChapters
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // A live stream's position is an offset into the DVR
                        // window, not a place in a video, so the elapsed
                        // readout means nothing to the viewer.
                        if (!isLive) {
                            Text(formatDuration(currentPosition), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }

                        PlayerSeekBar(
                            progress = progress,
                            bufferedProgress = bufferedProgress,
                            onSeek = onSeek,
                            modifier = Modifier.weight(1f),
                            chapters = chapters,
                            durationMs = duration
                        )

                        if (isLive) {
                            LiveEdgeChip(
                                // A stream without a DVR window reports no
                                // duration, so there is nowhere to be behind.
                                atLiveEdge = duration <= 0L || progress >= LIVE_EDGE_THRESHOLD,
                                onClick = onSeekToLive
                            )
                        } else {
                            Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PortraitPlayerContent(
    exoPlayer: ExoPlayer,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    hasError: Boolean,
    errorMessage: String,
    isLoading: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    isLooping: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    bufferedProgress: Float = 0f,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    onLoopToggle: () -> Unit,
    showTimedCommentsButton: Boolean = false,
    timedCommentsActive: Boolean = false,
    onTimedCommentsToggle: () -> Unit = {},
    chapters: List<VideoChapter> = emptyList(),
    onOpenChapters: () -> Unit = {},
    captionsActive: Boolean = false,
    onCaptionsClick: () -> Unit = {},
    captionCues: List<VttCue> = emptyList(),
    isLive: Boolean = false,
    /** Jump to the live edge of the DVR window. */
    onSeekToLive: () -> Unit = {},
    /**
     * Shown only for a portrait live stream. Returns to the full-bleed vertical
     * layout the stream opened in.
     *
     * A portrait VOD gets no such button, deliberately: the watch page's box now
     * takes the shape of the video, so the page is a reasonable home for it, and
     * the full-bleed layout is built around live chat with nothing to say on a
     * finished video.
     */
    showVerticalLiveButton: Boolean = false,
    onVerticalLiveClick: () -> Unit = {},
    showPipButton: Boolean = false,
    onPipClick: () -> Unit = {},
    minimizeDragEnabled: Boolean = false,
    onMinimizeDragDelta: (Float) -> Unit = {},
    onMinimizeDragRelease: (Float) -> Unit = {},
    onRetry: (() -> Unit)? = null
) {
    // Stable shapes
    val stableShapes = IconButtonDefaults.shapes()

    // Speed captured when a hold-to-2x begins, restored when the finger lifts
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }

    // Same caption lift as fullscreen, scaled to the smaller inline video box
    val captionLift = animateDpAsState(
        targetValue = if (showControls) 64.dp else 12.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "captionLift"
    )

    PlayerGestureSurface(
        onToggleControls = onToggleControls,
        onSeekBackward = onSeekBackward,
        onSeekForward = onSeekForward,
        onSpeedBoostStart = {
            speedBeforeBoost = exoPlayer.playbackParameters.speed
            exoPlayer.setPlaybackSpeed(2f)
        },
        onSpeedBoostEnd = { exoPlayer.setPlaybackSpeed(speedBeforeBoost) },
        minimizeDragEnabled = minimizeDragEnabled,
        onMinimizeDragDelta = onMinimizeDragDelta,
        onMinimizeDragRelease = onMinimizeDragRelease,
        // Swipe up on the video to go fullscreen. Down already minimizes, so
        // the surface now answers both directions.
        onEnterFullscreen = onFullscreenToggle
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
            },
            // Hand the surface back before this view is destroyed - the same
            // ExoPlayer is also rendered by the mini and PiP PlayerViews.
            onRelease = { playerView -> playerView.player = null },
            modifier = Modifier.fillMaxSize()
        )

        CaptionOverlay(
            cues = captionCues,
            player = exoPlayer,
            bottomPadding = captionLift.value,
            compact = true
        )

        if (hasError) ErrorOverlay(errorMessage, onRetry)
        if (isLoading || (isBuffering && !showControls)) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ContainedLoadingIndicator()
        }
        
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledIconButton(
                        onClick = onBack,
                         colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(0.5f),
                            contentColor = Color.White
                        ),
                        shapes = stableShapes
                    ) {
                         Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Combined mode toggle: repeat off = auto-play next, repeat on = loop this video
                         FilledTonalIconButton(
                            onClick = onLoopToggle,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isLooping) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                                contentColor = if (isLooping) MaterialTheme.colorScheme.onPrimary else Color.White
                            ),
                            shapes = stableShapes
                        ) {
                             Icon(
                                if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Autorenew,
                                contentDescription = if (isLooping) "Repeat" else "Auto Play"
                            )
                        }
                        if (showTimedCommentsButton && !isLive) {
                            FilledTonalIconButton(
                                onClick = onTimedCommentsToggle,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (timedCommentsActive) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                                    contentColor = if (timedCommentsActive) MaterialTheme.colorScheme.onPrimary else Color.White
                                ),
                                shapes = stableShapes
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Comment,
                                    contentDescription = "Timed comments"
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = onCaptionsClick,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (captionsActive) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                                contentColor = if (captionsActive) MaterialTheme.colorScheme.onPrimary else Color.White
                            ),
                            shapes = stableShapes
                        ) {
                            Icon(
                                if (captionsActive) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                                contentDescription = "Captions"
                            )
                        }
                        // The only discoverable way into PiP. Auto-enter covers
                        // leaving the app on API 31+, but nothing advertised
                        // that PiP existed, and on Android 11 there was no way
                        // in at all.
                        if (showPipButton) {
                            FilledTonalIconButton(
                                onClick = onPipClick,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.Black.copy(0.5f),
                                    contentColor = Color.White
                                ),
                                shapes = stableShapes
                            ) {
                                Icon(
                                    Icons.Rounded.PictureInPictureAlt,
                                    contentDescription = "Picture in picture"
                                )
                            }
                        }
                        FilledIconButton(
                            onClick = onSettings,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(0.5f),
                                contentColor = Color.White
                            ),
                            shapes = stableShapes
                        ) {
                            Icon(Icons.Rounded.Settings, "Quality")
                        }
                        if (showVerticalLiveButton) {
                            FilledIconButton(
                                onClick = onVerticalLiveClick,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.Black.copy(0.5f),
                                    contentColor = Color.White
                                ),
                                shapes = stableShapes
                            ) {
                                Icon(
                                    Icons.Rounded.StayCurrentPortrait,
                                    contentDescription = "Fill the screen vertically"
                                )
                            }
                        }
                        FilledIconButton(
                            onClick = onFullscreenToggle,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(0.5f),
                                contentColor = Color.White
                            ),
                            shapes = stableShapes
                        ) {
                            Icon(Icons.Rounded.Fullscreen, "Fullscreen")
                        }
                    }
                }
                
                // Center
                Box(modifier = Modifier.align(Alignment.Center)) {
                    ExpressivePlayPauseButton(isPlaying = isPlaying, isBuffering = isBuffering, onClick = onPlayPause)
                }
                
                // Bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(0.8f))))
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    if (chapters.isNotEmpty()) {
                        ChapterTitleChip(
                            chapters = chapters,
                            currentPositionMs = currentPosition,
                            onClick = onOpenChapters
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isLive) {
                            Text(formatDuration(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }

                        PlayerSeekBar(
                            progress = progress,
                            bufferedProgress = bufferedProgress,
                            onSeek = onSeek,
                            modifier = Modifier.weight(1f),
                            chapters = chapters,
                            durationMs = duration
                        )

                        if (isLive) {
                            LiveEdgeChip(
                                // A stream without a DVR window reports no
                                // duration, so there is nowhere to be behind.
                                atLiveEdge = duration <= 0L || progress >= LIVE_EDGE_THRESHOLD,
                                onClick = onSeekToLive
                            )
                        } else {
                            Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fraction of the DVR window past which playback counts as "at the live edge".
 * The window is hours long, so anything short of the last fraction of a percent
 * is genuinely behind.
 */
internal const val LIVE_EDGE_THRESHOLD = 0.995f

/**
 * The "LIVE" marker where a normal video shows its duration. Red and tappable
 * while the viewer is behind, so getting back to the edge is one tap; muted
 * once they are already there.
 *
 * [contentTint] is what the chip resolves to when it is *not* at the edge, plus
 * the label color throughout. It defaults to white because the standard player
 * puts this straight on the video; the vertical live player sits it inside a
 * tonal container instead, where white is invisible in a light theme.
 */
@Composable
internal fun LiveEdgeChip(
    atLiveEdge: Boolean,
    onClick: () -> Unit,
    contentTint: Color = Color.White
) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        onClick = onClick,
        enabled = !atLiveEdge
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (atLiveEdge) MaterialTheme.colorScheme.error
                        else contentTint.copy(alpha = 0.6f)
                    )
            )
            Text(
                text = "LIVE",
                color = if (atLiveEdge) contentTint else contentTint.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Scrubbing seek bar. While the user drags, the thumb follows a local value and
 * the player is NOT touched, so we don't kick off a buffer/fetch on every pixel.
 * The actual seek fires once, on release (onValueChangeFinished). The 500ms
 * progress poll from the parent is ignored during the drag to avoid the thumb
 * fighting the finger.
 *
 * [onTonalSurface] switches the track and tick colors from the white-on-video
 * set to ColorScheme roles. The standard player draws this straight onto the
 * frame, where white is the only thing that reads on any video; the vertical
 * live player floats it inside a surfaceContainer, where white would disappear
 * in a light theme.
 */
@Composable
internal fun PlayerSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    chapters: List<VideoChapter> = emptyList(),
    durationMs: Long = 0L,
    onTonalSurface: Boolean = false
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    val bufferedColor = if (onTonalSurface) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.35f)
    }
    val inactiveTrackColor = if (onTonalSurface) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.White.copy(0.3f)
    }
    val tickColor = if (onTonalSurface) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    } else {
        Color.Black.copy(alpha = 0.85f)
    }

    Box(modifier = modifier) {
        // Buffered-ahead indicator: a soft white bar from the start to the
        // buffered position, drawn under the Slider. The inactive track is
        // translucent, so the buffered region reads as a brighter segment
        // ahead of the playhead (YouTube-style) while the opaque active
        // track covers the part already played.
        if (bufferedProgress > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val centerY = size.height / 2f
                drawLine(
                    color = bufferedColor,
                    start = Offset(0f, centerY),
                    end = Offset(bufferedProgress.coerceIn(0f, 1f) * size.width, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Slider(
            value = if (isScrubbing) scrubValue else progress.coerceIn(0f, 1f),
            onValueChange = {
                isScrubbing = true
                scrubValue = it
            },
            onValueChangeFinished = {
                onSeek(scrubValue)
                isScrubbing = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = inactiveTrackColor
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Chapter boundary ticks drawn over the track. Purely decorative; the
        // Canvas has no pointer input so touches still reach the Slider.
        if (chapters.size > 1 && durationMs > 0) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val centerY = size.height / 2f
                val half = 5.dp.toPx()
                val stroke = 2.5.dp.toPx()
                chapters.forEach { chapter ->
                    val fraction = chapter.startMs.toFloat() / durationMs.toFloat()
                    if (fraction > 0.001f && fraction < 0.999f) {
                        val x = fraction * size.width
                        drawLine(
                            color = tickColor,
                            start = Offset(x, centerY - half),
                            end = Offset(x, centerY + half),
                            strokeWidth = stroke
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pill above the seek bar showing the current chapter title; tapping it opens
 * the full chapters list.
 */
@Composable
private fun ChapterTitleChip(
    chapters: List<VideoChapter>,
    currentPositionMs: Long,
    onClick: () -> Unit
) {
    val index = currentChapterIndex(chapters, currentPositionMs)
    val title = chapters.getOrNull(index)?.title ?: return
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = "Chapters",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Seconds jumped per double-tap on either edge of the video surface. */
private const val DOUBLE_TAP_SEEK_SECONDS = 10

/**
 * How long a press has to be held before playback boosts to 2x.
 *
 * Deliberately longer than the platform's long-press timeout (roughly 500ms).
 * A press meant as a tap to bring the controls up was crossing the system
 * threshold and jumping the video to double speed instead, which is startling
 * in a way a mis-tap should not be. The boost is a sustained gesture held for
 * seconds, so asking for a little more commitment up front costs nothing and
 * stops the accident.
 *
 * Applied by overriding [LocalViewConfiguration] around the gesture surface
 * rather than by delaying the boost off `onLongPress`. Delaying would leave a
 * dead zone: `detectTapGestures` still consumes the gesture as a long press at
 * the system timeout, so a press landing between the two thresholds would
 * neither toggle the controls nor boost, and the video would feel unresponsive.
 * Moving the view configuration's threshold moves the tap and long-press
 * boundaries together, so below it is cleanly a tap and above it cleanly a
 * boost.
 */
private const val SPEED_BOOST_HOLD_MS = 650L

/**
 * [ViewConfiguration] that reports a longer long-press timeout and delegates
 * everything else, so raising the boost threshold does not also move touch slop
 * or the double-tap window that the seek gesture depends on.
 */
private class SpeedBoostViewConfiguration(
    private val base: ViewConfiguration,
    private val longPressMs: Long
) : ViewConfiguration {
    override val longPressTimeoutMillis: Long get() = longPressMs
    override val doubleTapTimeoutMillis: Long get() = base.doubleTapTimeoutMillis
    override val doubleTapMinTimeMillis: Long get() = base.doubleTapMinTimeMillis
    override val touchSlop: Float get() = base.touchSlop
}

/**
 * Bounds on the level ladder's segment count.
 *
 * The count itself comes from the device's own media volume steps, so one slat
 * is one real notch of volume and the haptic tick lands on the slat lighting.
 * That number is not universal though - most phones expose 15, a few expose 7,
 * and some expose 100, which would be neither drawable nor tickable - so it is
 * clamped into a range that stays legible at the ladder's height. Brightness is
 * continuous and borrows the same count, which is what keeps the two lanes
 * feeling like one control.
 */
private const val LEVEL_SEGMENT_MIN = 8
private const val LEVEL_SEGMENT_MAX = 16

/** How long the level indicator stays up after the last movement. */
private const val LEVEL_HIDE_MS = 800L

/** Accumulated pinch ratios beyond these thresholds toggle zoom-to-fill. */
private const val PINCH_ZOOM_IN_THRESHOLD = 1.15f
private const val PINCH_ZOOM_OUT_THRESHOLD = 0.87f

/**
 * Upward travel on the inline video that commits to fullscreen.
 *
 * Small, because the inline box is only a 16:9 slice of a portrait screen -
 * there is barely 100dp of room above a finger that started in the middle of
 * it. Comfortably past touch slop, and the gesture commits the moment it is
 * reached rather than waiting for the finger to lift, so the rotation starts
 * while the swipe still feels like it is happening.
 */
private val ENTER_FULLSCREEN_SWIPE_TRAVEL = 56.dp

/**
 * Downward travel in the centre column of a fullscreen video that exits it.
 *
 * Deliberately longer than the way in: leaving fullscreen throws away the
 * orientation the user is holding the phone in, so it should take a gesture
 * they meant.
 */
private val EXIT_FULLSCREEN_SWIPE_TRAVEL = 72.dp

/**
 * Half-width of the centre column reserved for the exit-fullscreen swipe, as a
 * fraction of the surface.
 *
 * Vertical drags in fullscreen are already spoken for - brightness on the left,
 * volume on the right - so the exit gesture needs a lane of its own rather than
 * a direction of its own. A third of the width each still leaves both levels
 * more than enough to grab, since their travel is vertical.
 */
private const val FULLSCREEN_CENTRE_COLUMN_HALF_WIDTH = 0.18f

/**
 * Wraps the video surface with tap gestures: single tap toggles the controls,
 * a double tap on the left rewinds and on the right fast-forwards (YouTube-style),
 * with an animated badge that accumulates when tapped repeatedly.
 *
 * With [fullscreenGesturesEnabled] it also recognizes, like most video players:
 * - vertical drag on the left half = screen brightness (window-level override,
 *   restored when the surface leaves composition);
 * - vertical drag on the right half = media volume;
 * - both only arm when the drag STARTS below the top 30% of the surface, so
 *   pulling down the notification shade never yanks brightness or volume;
 * - pinch open/closed = zoom the video to fill the screen / fit inside it,
 *   reported through [onZoomedToFillChange].
 *
 * With [minimizeDragEnabled] (portrait, non-fullscreen only) a vertical drag
 * on the surface is reported through [onMinimizeDragDelta] /
 * [onMinimizeDragRelease] so the overlay can drag the player down into the
 * mini player. Mutually exclusive with the fullscreen level drags.
 *
 * The two fullscreen swipes are the YouTube pair, and they are directional
 * rather than positional so each one lands where the hand already is:
 * - inline (portrait): swipe **up** anywhere on the video enters fullscreen,
 *   through [onEnterFullscreen]. Down is still the minimize drag, so the video
 *   surface answers both directions with the obvious thing.
 * - fullscreen (landscape): swipe **down** the centre column exits, through
 *   [onExitFullscreen]. Only the centre, because the sides are the brightness
 *   and volume lanes.
 *
 * Both commit mid-gesture on travel alone rather than on release. There is no
 * dragged preview to release into - the layout swap is an orientation change,
 * not something that can follow a finger - so waiting for the lift would just
 * make the gesture feel like it had not registered. Leaving either callback
 * null leaves that swipe out entirely.
 */
@Composable
internal fun PlayerGestureSurface(
    onToggleControls: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    fullscreenGesturesEnabled: Boolean = false,
    onZoomedToFillChange: (Boolean) -> Unit = {},
    onSpeedBoostStart: () -> Unit = {},
    onSpeedBoostEnd: () -> Unit = {},
    minimizeDragEnabled: Boolean = false,
    onMinimizeDragDelta: (Float) -> Unit = {},
    onMinimizeDragRelease: (Float) -> Unit = {},
    onEnterFullscreen: (() -> Unit)? = null,
    onExitFullscreen: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    // side: -1 rewind, +1 forward, 0 hidden. seconds accumulates on rapid taps.
    var side by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    var pulse by remember { mutableIntStateOf(0) }

    // Press-and-hold anywhere temporarily boosts playback to 2x (YouTube-style).
    var isBoosting by remember { mutableStateOf(false) }

    // The player recomposes on every position tick, so these arrive as fresh
    // lambda instances several times a second. Read through a state holder and
    // key the pointerInputs on whether the gesture exists at all, or every tick
    // would restart the gesture detectors and cancel a drag in progress.
    val enterFullscreen by rememberUpdatedState(onEnterFullscreen)
    val exitFullscreen by rememberUpdatedState(onExitFullscreen)
    val enterFullscreenEnabled = onEnterFullscreen != null
    val exitFullscreenEnabled = onExitFullscreen != null

    // Both fullscreen swipes commit while the finger is still down and have no
    // dragged preview behind them, so a tick is the only thing that tells the
    // user the gesture took before the screen turns.
    val haptics = LocalHapticFeedback.current

    // Hide the badge a short while after the last tap. Re-runs (and so resets
    // the timer) every double tap because it is keyed on `pulse`.
    LaunchedEffect(pulse) {
        if (side != 0) {
            delay(650)
            side = 0
            seconds = 0
        }
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // Brightness/volume feedback overlay: which level is being adjusted and
    // its current 0..1 value. adjustPulse restarts the auto-hide timer.
    var adjustment by remember { mutableStateOf<LevelAdjustment?>(null) }
    var adjustmentLevel by remember { mutableFloatStateOf(0f) }
    var adjustPulse by remember { mutableIntStateOf(0) }

    // One slat per real volume notch, shared by both lanes. Read once: the
    // stream's step count does not change while a video is open.
    val levelSegments = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            .coerceIn(LEVEL_SEGMENT_MIN, LEVEL_SEGMENT_MAX)
    }

    // Re-keyed by every drag frame, so a continuous drag never gets past the
    // delay and the indicator stays up for as long as the finger moves.
    LaunchedEffect(adjustPulse) {
        if (adjustment != null) {
            delay(LEVEL_HIDE_MS)
            adjustment = null
        }
    }

    val themePreferences = remember(context) { ThemePreferences(context) }

    // The brightness gesture overrides the window brightness: re-apply the
    // level the user last dialed in so every fullscreen video looks the same,
    // and hand control back to the system when the surface goes away (the rest
    // of the app must not stay stuck at the video's brightness).
    if (fullscreenGesturesEnabled) {
        DisposableEffect(activity) {
            activity?.let { act ->
                val saved = themePreferences.getVideoBrightness()
                if (saved != ThemePreferences.VIDEO_BRIGHTNESS_UNSET) {
                    setWindowBrightness(act, saved.coerceAtLeast(0.01f))
                }
            }
            onDispose {
                activity?.let { setWindowBrightness(it, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
            }
        }
    }

    // Raise the long-press threshold for the whole surface so a tap meant for
    // the controls cannot trip the 2x boost. Scoped here rather than app-wide,
    // and nothing inside this surface uses its own long-press (the content is
    // the PlayerView and the caption overlay; the badges below are not
    // interactive), so the override has nothing to leak into.
    val baseViewConfiguration = LocalViewConfiguration.current
    val boostViewConfiguration = remember(baseViewConfiguration) {
        SpeedBoostViewConfiguration(baseViewConfiguration, SPEED_BOOST_HOLD_MS)
    }
    CompositionLocalProvider(LocalViewConfiguration provides boostViewConfiguration) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggleControls() },
                        onLongPress = {
                            isBoosting = true
                            onSpeedBoostStart()
                        },
                        onPress = {
                            // Suspends until the finger lifts (or the gesture is
                            // cancelled); undo the boost if this press started one.
                            tryAwaitRelease()
                            if (isBoosting) {
                                isBoosting = false
                                onSpeedBoostEnd()
                            }
                        },
                        onDoubleTap = { offset ->
                            val tappedSide = if (offset.x < size.width / 2f) -1 else 1
                            seconds = if (tappedSide == side) seconds + DOUBLE_TAP_SEEK_SECONDS else DOUBLE_TAP_SEEK_SECONDS
                            side = tappedSide
                            pulse++
                            if (tappedSide < 0) onSeekBackward() else onSeekForward()
                        }
                    )
                }
                .then(
                    if (fullscreenGesturesEnabled ||
                        (!minimizeDragEnabled && !enterFullscreenEnabled)
                    ) {
                        Modifier
                    } else Modifier.pointerInput(minimizeDragEnabled, enterFullscreenEnabled) {
                        val velocityTracker = VelocityTracker()
                        val enterTravelPx = ENTER_FULLSCREEN_SWIPE_TRAVEL.toPx()
                        var totalDy = 0f
                        var wentFullscreen = false
                        var minimizing = false
                        detectVerticalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                                totalDy = 0f
                                wentFullscreen = false
                                minimizing = false
                            },
                            onVerticalDrag = { change, dragAmount ->
                                totalDy += dragAmount

                                // Velocity from the accumulated delta, NOT from
                                // change.position. This surface is inside the player
                                // being dragged, and the minimize drag moves it down
                                // one-for-one with the finger - so the pointer's
                                // position *within this node* barely changes, and a
                                // tracker fed those positions reported almost no
                                // velocity. That is why a calm downward swipe never
                                // tripped the velocity test and minimizing felt like
                                // it needed to be yanked. The deltas are measured
                                // against the node's current transform and are
                                // correct; only the absolute positions are not.
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    Offset(0f, totalDy)
                                )

                                // Direction is re-read from the accumulated travel
                                // every event rather than locked in from the first
                                // delta past touch slop. Locking meant a swipe that
                                // began with the smallest upward roll of the finger
                                // - which is most of them - spent the whole gesture
                                // in the fullscreen lane and minimized nothing.
                                if (!wentFullscreen &&
                                    enterFullscreenEnabled &&
                                    totalDy <= -enterTravelPx
                                ) {
                                    wentFullscreen = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    enterFullscreen?.invoke()
                                }

                                if (minimizeDragEnabled && !wentFullscreen) {
                                    // Upward deltas are safe to forward: the player
                                    // clamps at fully expanded, so an early wobble
                                    // costs nothing and the pull still counts.
                                    onMinimizeDragDelta(dragAmount)
                                    if (totalDy > 0f) minimizing = true
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                if (minimizing) {
                                    onMinimizeDragRelease(velocityTracker.calculateVelocity().y)
                                }
                            },
                            onDragCancel = { if (minimizing) onMinimizeDragRelease(0f) }
                        )
                    }
                )
                .then(
                    if (!fullscreenGesturesEnabled) Modifier
                    else Modifier.pointerInput(activity, exitFullscreenEnabled) {
                        val exitTravelPx = EXIT_FULLSCREEN_SWIPE_TRAVEL.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val leftSide = down.position.x < size.width / 2f
                            // Vertical drags only arm in the bottom 70% of the
                            // surface: a swipe from the top area is almost always
                            // the user reaching for the notification shade, and
                            // grabbing it as a brightness/volume drag was a
                            // constant misfire - the same is true of a downward
                            // swipe meant to leave fullscreen. Pinch-to-zoom stays
                            // available everywhere.
                            val inDragZone = down.position.y >= size.height * 0.3f
                            // The exit-fullscreen lane, between the two level lanes.
                            val inCentreColumn = abs(down.position.x - size.width / 2f) <=
                                size.width * FULLSCREEN_CENTRE_COLUMN_HALF_WIDTH
                            // 0 = undecided, 1 = vertical level drag, 2 = pinch,
                            // 3 = downward swipe out of fullscreen
                            var mode = 0
                            var accumulatedZoom = 1f
                            var level = 0f
                            var exitCommitted = false
                            // Whether the level is currently pinned to 0 or 1, so
                            // the rail tick fires once on arrival rather than on
                            // every frame the finger keeps pushing past the end.
                            var atRail = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                // A child (slider, button) claimed the gesture
                                if (mode == 0 && event.changes.any { it.isConsumed }) break

                                if (pressed.size > 1 && mode != 1) {
                                    mode = 2
                                    accumulatedZoom *= event.calculateZoom()
                                    if (accumulatedZoom > PINCH_ZOOM_IN_THRESHOLD) {
                                        onZoomedToFillChange(true)
                                    } else if (accumulatedZoom < PINCH_ZOOM_OUT_THRESHOLD) {
                                        onZoomedToFillChange(false)
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (pressed.size == 1 && mode != 2) {
                                    val change = pressed.first()
                                    if (mode == 0) {
                                        val totalDx = change.position.x - down.position.x
                                        val totalDy = change.position.y - down.position.y
                                        if (inDragZone &&
                                            abs(totalDy) > viewConfiguration.touchSlop &&
                                            abs(totalDy) > abs(totalDx)
                                        ) {
                                            if (inCentreColumn && exitFullscreenEnabled) {
                                                mode = 3
                                            } else {
                                                mode = 1
                                                level = if (leftSide) {
                                                    activity?.let { currentWindowBrightness(it) } ?: 0.5f
                                                } else {
                                                    currentVolumeFraction(audioManager)
                                                }
                                            }
                                        }
                                    }
                                    if (mode == 1) {
                                        val dy = change.position.y - change.previousPosition.y
                                        // Dragging ~70% of the surface height sweeps the full range
                                        val previousLevel = level
                                        level = (level - dy / (size.height * 0.7f)).coerceIn(0f, 1f)
                                        if (leftSide) {
                                            activity?.let { setWindowBrightness(it, level.coerceAtLeast(0.01f)) }
                                            adjustment = LevelAdjustment.Brightness
                                        } else {
                                            setVolumeFraction(audioManager, level)
                                            adjustment = LevelAdjustment.Volume
                                        }
                                        // One tick per slat, on both lanes, because
                                        // the ladder is drawn at exactly this
                                        // granularity - the tick and the slat
                                        // lighting are meant to be one event, not
                                        // two that happen near each other.
                                        val crossed = (level * levelSegments).toInt() !=
                                            (previousLevel * levelSegments).toInt()
                                        if (crossed) {
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.SegmentFrequentTick
                                            )
                                        }
                                        val onRail = level <= 0f || level >= 1f
                                        if (onRail && !atRail) {
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.GestureThresholdActivate
                                            )
                                        }
                                        atRail = onRail
                                        adjustmentLevel = level
                                        adjustPulse++
                                        change.consume()
                                    } else if (mode == 3) {
                                        // Upward in this lane is deliberately
                                        // nothing: the centre is the way out of
                                        // fullscreen, not a third level slider.
                                        if (!exitCommitted &&
                                            change.position.y - down.position.y >= exitTravelPx
                                        ) {
                                            exitCommitted = true
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            exitFullscreen?.invoke()
                                        }
                                        change.consume()
                                    }
                                }
                            }
                            // Persist once the finger lifts, not on every frame of
                            // the drag. Volume is deliberately not stored: it is
                            // the system STREAM_MUSIC level, which already carries
                            // over on its own.
                            if (mode == 1 && leftSide) {
                                themePreferences.setVideoBrightness(level)
                            }
                        }
                    }
                )
        ) {
            content()

            // "2x" pill shown at the top while the hold-to-speed-up is active
            SpeedBoostBadge(
                visible = isBoosting,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )

            SeekFeedbackBadge(
                visible = side < 0,
                seconds = seconds,
                forward = false,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            SeekFeedbackBadge(
                visible = side > 0,
                seconds = seconds,
                forward = true,
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            // One per lane rather than one that moves, so switching sides mid-video
            // fades the old pill out on its own edge instead of flying it across.
            PlayerLevelIndicator(
                kind = LevelAdjustment.Brightness,
                level = adjustmentLevel,
                segments = levelSegments,
                visible = adjustment == LevelAdjustment.Brightness,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 48.dp)
            )
            PlayerLevelIndicator(
                kind = LevelAdjustment.Volume,
                level = adjustmentLevel,
                segments = levelSegments,
                visible = adjustment == LevelAdjustment.Volume,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 48.dp)
            )
        }
    }
}

/** Current window brightness, falling back to the system setting when unset. */
private fun currentWindowBrightness(activity: Activity): Float {
    val fromWindow = activity.window.attributes.screenBrightness
    if (fromWindow >= 0f) return fromWindow
    return try {
        android.provider.Settings.System.getInt(
            activity.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS
        ) / 255f
    } catch (e: Exception) {
        0.5f
    }
}

private fun setWindowBrightness(activity: Activity, value: Float) {
    val window = activity.window
    val attributes = window.attributes
    attributes.screenBrightness = value
    window.attributes = attributes
}

private fun currentVolumeFraction(audioManager: AudioManager): Float {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return 0f
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun setVolumeFraction(audioManager: AudioManager, fraction: Float) {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val target = (fraction * max).roundToInt().coerceIn(0, max)
    if (target != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }
}

/** "2x" indicator shown at the top of the video while hold-to-speed-up is active. */
@Composable
private fun SpeedBoostBadge(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Rounded.Forward10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "2x",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SeekFeedbackBadge(
    visible: Boolean,
    seconds: Int,
    forward: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.5f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Icon(
                    if (forward) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "$seconds seconds",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideoInfoSection(
    video: VideoItem,
    relatedVideos: List<VideoItem>,
    onVideoSelect: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    engagement: VideoEngagement? = null,
    /**
     * Followed by the account *or* on this device. Separate from
     * [engagement], which only ever reports the signed-in account's state.
     */
    isSubscribed: Boolean = false,
    onLikeClick: () -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onSubscribeClick: () -> Unit = {},
    onCommentsClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onChannelClick: () -> Unit = {},
    onRelatedLongPress: ((VideoItem) -> Unit)? = null,
    /** Seek the player, in seconds. Enables timestamp links in the description. */
    onSeekTo: ((seconds: Long) -> Unit)? = null,
    isLive: Boolean = false,
    /** Concurrent viewers, refreshed while the stream is open. */
    liveViewerCount: String? = null,
    onLiveChatClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .padding(bottom = 80.dp), // Bottom padding for navigation bar
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Title & Stats Group
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            // A live stream's "view count" is a concurrent-viewer number that
            // moves, and its upload date is meaningless, so the badge replaces
            // the static line rather than sitting next to it.
            if (isLive) {
                LiveBadge(
                    viewerCount = liveViewerCount ?: video.viewCount.takeIf { it.isNotEmpty() }
                )
            } else {
                Text(
                    text = buildString {
                        if (video.viewCount.isNotEmpty()) append(video.viewCount)
                        if (!video.uploadedDate.isNullOrEmpty()) append(" • ${video.uploadedDate}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Like / Dislike + Save + Share Actions (scrolls like YouTube's chip
        // row so the pills never squash on narrow screens)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            LikeDislikeBar(
                engagement = engagement,
                onLikeClick = onLikeClick,
                onDislikeClick = onDislikeClick
            )
            SaveVideoButton(onClick = onSaveClick)
            ShareVideoButton(video = video)
            DownloadVideoButton(video = video)
        }

        // Channel Info Surface (tap navigates to the channel)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
            onClick = onChannelClick
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                         text = engagement?.subscriberCountText ?: video.subscriberCount ?: "",
                         style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingContent = {
                    if (!video.channelIconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = video.channelIconUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = video.channelName.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                trailingContent = {
                    Button(
                        onClick = onSubscribeClick,
                        enabled = engagement?.channelId != null,
                        colors = if (isSubscribed) {
                            ButtonDefaults.filledTonalButtonColors()
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(if (isSubscribed) "Subscribed" else "Subscribe")
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )
        }

        // Live chat entry. On a live stream this replaces comments outright
        // rather than sitting above them: chat is where the conversation
        // actually is, and the comment section on a running broadcast is
        // usually empty or disabled, so offering both sent people to the dead
        // one.
        if (isLive) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
                onClick = onLiveChatClick
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Live chat",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    LiveDot()
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = "Open live chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Comments Entry
        if (!isLive) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
                onClick = onCommentsClick,
                enabled = engagement?.commentsToken != null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Comment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Comments",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = "Open comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Description Surface
        if (!video.description.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    var isDescriptionExpanded by remember { mutableStateOf(false) }
                    // YouTube's link offsets are measured against the raw
                    // attributedDescription text, so it must not be rewritten
                    // when we have them. Only the NewPipe-sourced descriptions
                    // (which carry no links) still need HTML unescaping.
                    val cleanedDescription = remember(video.description, video.descriptionLinks) {
                        when {
                            video.description == null -> ""
                            video.descriptionLinks.isNotEmpty() -> video.description
                            else -> androidx.core.text.HtmlCompat.fromHtml(
                                video.description,
                                androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
                            ).toString().trim()
                        }
                    }
                    val describedText = com.ivor.ivormusic.ui.components.rememberLinkedText(
                        rich = com.ivor.ivormusic.data.RichText(
                            cleanedDescription,
                            video.descriptionLinks
                        ),
                        onTimestampClick = onSeekTo
                    )

                    if (cleanedDescription.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = describedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (cleanedDescription.length > 100 || cleanedDescription.count { it == '\n' } > 2) {
                                // Only this row toggles expansion. It used to be
                                // the whole block, which would now fight the
                                // links inside the text for the same tap.
                                Text(
                                    text = if (isDescriptionExpanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                        .padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Related Videos Section
        if (relatedVideos.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                relatedVideos.forEach { relatedVideo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            // Long-press saves to Watch Later / a playlist,
                            // same gesture as the home feed cards
                            .combinedClickable(
                                onClick = { onVideoSelect(relatedVideo) },
                                onLongClick = onRelatedLongPress?.let { longPress ->
                                    { longPress(relatedVideo) }
                                }
                            )
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Thumbnail
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .aspectRatio(16f/9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            if (relatedVideo.thumbnailUrl != null) {
                                AsyncImage(
                                    model = relatedVideo.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            // Duration
                            if (relatedVideo.duration > 0) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = relatedVideo.formattedDuration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        
                        // Info
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = relatedVideo.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = relatedVideo.channelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = relatedVideo.viewCount + " • " + (relatedVideo.uploadedDate ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Save pill beside Share: opens the save-to-playlist sheet with Watch Later
 * pinned on top. Matches the pill shape of the like/dislike bar.
 */
@Composable
private fun SaveVideoButton(onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Icon(
                Icons.Rounded.WatchLater,
                contentDescription = "Save to Watch Later",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Save",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Download pill. Queues the video into the shared download repository, which
 * fetches the best MP4 video and audio streams and remuxes them into
 * Downloads/Koda/Video. Reflects queued/downloading/downloaded state so the
 * pill is not a fire-and-forget button.
 */
@Composable
private fun DownloadVideoButton(video: VideoItem) {
    val context = LocalContext.current
    val repository = remember(context) {
        com.ivor.ivormusic.data.DownloadRepository.getInstance(context)
    }
    val scope = rememberCoroutineScope()

    val downloadedVideos by repository.downloadedVideos.collectAsState()
    val progressMap by repository.downloadProgress.collectAsState()

    val downloaded = downloadedVideos.any { it.id == video.videoId }
    val progress = progressMap[video.videoId]
    val inFlight = progress != null &&
        (progress.status == com.ivor.ivormusic.data.DownloadStatus.DOWNLOADING ||
            progress.status == com.ivor.ivormusic.data.DownloadStatus.QUEUED)

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = {
            when {
                downloaded -> repository.deleteVideoDownload(video.videoId)
                inFlight -> repository.cancelDownload(video.videoId)
                else -> scope.launch { repository.downloadVideo(video) }
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Icon(
                imageVector = when {
                    downloaded -> Icons.Rounded.CheckCircle
                    inFlight -> Icons.Rounded.Close
                    else -> Icons.Rounded.Download
                },
                contentDescription = "Download",
                modifier = Modifier.size(20.dp),
                tint = if (downloaded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = when {
                    downloaded -> "Downloaded"
                    inFlight -> "${((progress?.progress ?: 0f) * 100).toInt()}%"
                    else -> "Download"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Share pill that fires the system share sheet with the video's watch URL,
 * matching the pill shape of the like/dislike bar it sits beside.
 */
@Composable
private fun ShareVideoButton(video: VideoItem) {
    val context = LocalContext.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://youtube.com/watch?v=${video.videoId}")
            }
            context.startActivity(Intent.createChooser(send, "Share video"))
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Icon(
                Icons.Rounded.Share,
                contentDescription = "Share",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Share",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * YouTube-style segmented like/dislike pill. Disabled until engagement loads.
 */
@Composable
private fun LikeDislikeBar(
    engagement: VideoEngagement?,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit
) {
    val likeStatus = engagement?.likeStatus ?: LikeStatus.INDIFFERENT
    val enabled = engagement != null

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            // Like segment
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = enabled) { onLikeClick() }
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Icon(
                    imageVector = if (likeStatus == LikeStatus.LIKE) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = if (likeStatus == LikeStatus.LIKE) "Remove like" else "Like",
                    modifier = Modifier.size(20.dp),
                    tint = if (likeStatus == LikeStatus.LIKE) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                )
                val likeCount = engagement?.likeCount
                if (!likeCount.isNullOrBlank()) {
                    Text(
                        text = likeCount,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (likeStatus == LikeStatus.LIKE) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Dislike segment
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = enabled) { onDislikeClick() }
                    .padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Icon(
                    imageVector = if (likeStatus == LikeStatus.DISLIKE) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = if (likeStatus == LikeStatus.DISLIKE) "Remove dislike" else "Dislike",
                    modifier = Modifier.size(20.dp),
                    tint = if (likeStatus == LikeStatus.DISLIKE) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ---------------- Helpers ----------------

@Composable
fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = tint
        )
    ) {
        Icon(icon, contentDescription)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 72.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Expressive Spring Animation for Scale
    val scale by animateDpAsState(
        targetValue = if (isPressed) size * 0.9f else size,
        animationSpec = spring(
            dampingRatio = 0.4f, // Bouncy!
            stiffness = 600f
        ),
        label = "ButtonScale"
    )
    
    // Shape Morphing
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) size / 3 else size / 2, // Morph from circle to squircle
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ButtonShape"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.size(scale),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        interactionSource = interactionSource,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isBuffering && !isPlaying) {
                // Expressive Loading Indicator
                 LoadingIndicator(
                    modifier = Modifier.size(size * 0.5f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    polygons = listOf(
                        MaterialShapes.SoftBurst,
                        MaterialShapes.Cookie9Sided,
                        MaterialShapes.Pill,
                        MaterialShapes.Sunny
                    )
                )
            } else {
                // Animated Icon with Scale/Rotate transition potential (kept simple for now)
                val iconSize = size * 0.45f
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
fun ErrorOverlay(message: String, onRetry: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(Icons.Rounded.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.White, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val seconds = millis / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
