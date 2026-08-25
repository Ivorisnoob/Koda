package com.ivor.ivormusic.ui.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.graphics.asImageBitmap
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.CaptionBackground
import com.ivor.ivormusic.data.CaptionTextColor
import com.ivor.ivormusic.data.CAPTION_TEXT_SCALE_DEFAULT
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoChapter
import com.ivor.ivormusic.data.VideoEngagement
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoSeekPreview
import com.ivor.ivormusic.data.VttCue
import com.ivor.ivormusic.data.WebVttParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * Caption colors are intentionally video-safe rather than ColorScheme roles:
 * they sit directly on arbitrary frames. The user's size, foreground and plate
 * choices apply consistently in the watch page, fullscreen, vertical live and
 * PiP, while this layout continues to own collision-free positioning.
 */
@Composable
internal fun CaptionOverlay(
    cues: List<VttCue>,
    player: ExoPlayer,
    bottomPadding: Dp,
    compact: Boolean,
    textSize: Float,
    textColor: CaptionTextColor,
    background: CaptionBackground,
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
                val baseStyle = if (compact) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.titleMedium
                }
                val foreground = when (textColor) {
                    CaptionTextColor.WHITE -> Color.White
                    CaptionTextColor.YELLOW -> Color.Yellow
                }
                val plateAlpha = when (background) {
                    CaptionBackground.NONE -> 0f
                    CaptionBackground.TRANSLUCENT -> 0.75f
                    CaptionBackground.SOLID -> 0.95f
                }
                Box(
                    modifier = Modifier.padding(bottom = bottomPadding),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = cue,
                        color = foreground,
                        style = baseStyle.copy(
                            fontSize = baseStyle.fontSize * textSize,
                            lineHeight = baseStyle.lineHeight * textSize,
                            shadow = if (background == CaptionBackground.NONE) {
                                androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    offset = Offset(1f, 1f),
                                    blurRadius = 4f
                                )
                            } else {
                                null
                            }
                        ),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = plateAlpha),
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
    videoId: String,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    hasError: Boolean,
    errorMessage: String,
    isLoading: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    bufferedProgress: Float = 0f,
    seekPreview: VideoSeekPreview? = null,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit = {},
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    chapters: List<VideoChapter> = emptyList(),
    onOpenChapters: () -> Unit = {},
    captionsActive: Boolean = false,
    onCaptionsClick: () -> Unit = {},
    captionCues: List<VttCue> = emptyList(),
    captionTextSize: Float = CAPTION_TEXT_SCALE_DEFAULT,
    captionTextColor: CaptionTextColor = CaptionTextColor.WHITE,
    captionBackground: CaptionBackground = CaptionBackground.TRANSLUCENT,
    /**
     * Playlist transport and the way into the queue. Fullscreen is where a
     * playlist is most likely to be watched end to end, and leaving fullscreen
     * to reach the list would put the video back in a box to do it.
     */
    showQueueControls: Boolean = false,
    hasPreviousInQueue: Boolean = false,
    hasNextInQueue: Boolean = false,
    onPreviousInQueue: () -> Unit = {},
    onNextInQueue: () -> Unit = {},
    isLive: Boolean = false,
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
    // on a line of their own. Only immediate viewing controls live over the
    // moving frame; everything secondary is in Playback settings.
    val topBarActions: @Composable RowScope.() -> Unit = {
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
            Icon(Icons.Rounded.Settings, "Playback settings")
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
            compact = false,
            textSize = captionTextSize,
            textColor = captionTextColor,
            background = captionBackground
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
                    .background(Color.Black.copy(alpha = 0.22f))
            ) {
                // Top Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(0.7f), Color.Transparent)))
                        .padding(
                            horizontal = if (compactChrome) 16.dp else 24.dp,
                            vertical = 12.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledIconButton(
                                onClick = onBack,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.46f),
                                    contentColor = Color.White
                                ),
                                shapes = stableShapes
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                            }
                            Text(
                                text = videoTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            )
                        }

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
                
                // Familiar transport stays at the visual centre of the video.
                // Expressiveness belongs to the primary control's motion, not
                // to a large permanent container covering the frame.
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    if (showQueueControls) {
                        QueueSkipButton(
                            icon = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous in playlist",
                            enabled = hasPreviousInQueue,
                            onClick = onPreviousInQueue,
                            size = 54.dp
                        )
                    } else {
                        PlayerIconButton(
                            icon = Icons.Rounded.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            onClick = onSeekBackward
                        )
                    }
                    ExpressivePlayPauseButton(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onClick = onPlayPause,
                        size = 74.dp,
                    )
                    if (showQueueControls) {
                        QueueSkipButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = "Next in playlist",
                            enabled = hasNextInQueue,
                            onClick = onNextInQueue,
                            size = 54.dp
                        )
                    } else {
                        PlayerIconButton(
                            icon = Icons.Rounded.Forward10,
                            contentDescription = "Forward 10 seconds",
                            onClick = onSeekForward
                        )
                    }
                }

                // Low-weight utilities and a full-width timeline hug the edge.
                // They remain discoverable without becoming the visual subject.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chapters.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (chapters.isNotEmpty()) {
                                ChapterTitleChip(
                                    chapters = chapters,
                                    currentPositionMs = currentPosition,
                                    onClick = onOpenChapters
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
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
                            mediaId = videoId,
                            progress = progress,
                            bufferedProgress = bufferedProgress,
                            onSeek = onSeek,
                            modifier = Modifier.weight(1f),
                            chapters = chapters,
                            durationMs = duration,
                            seekPreview = seekPreview,
                            showSeekPreview = !isLive,
                            onScrubbingChanged = onScrubbingChanged,
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
    videoId: String,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    hasError: Boolean,
    errorMessage: String,
    isLoading: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    bufferedProgress: Float = 0f,
    seekPreview: VideoSeekPreview? = null,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit = {},
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    chapters: List<VideoChapter> = emptyList(),
    onOpenChapters: () -> Unit = {},
    captionsActive: Boolean = false,
    onCaptionsClick: () -> Unit = {},
    captionCues: List<VttCue> = emptyList(),
    captionTextSize: Float = CAPTION_TEXT_SCALE_DEFAULT,
    captionTextColor: CaptionTextColor = CaptionTextColor.WHITE,
    captionBackground: CaptionBackground = CaptionBackground.TRANSLUCENT,
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
    /**
     * Playlist transport. Composed only while a queue is running - see
     * [QueueSkipButton].
     */
    showQueueControls: Boolean = false,
    hasPreviousInQueue: Boolean = false,
    hasNextInQueue: Boolean = false,
    onPreviousInQueue: () -> Unit = {},
    onNextInQueue: () -> Unit = {},
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
            compact = true,
            textSize = captionTextSize,
            textColor = captionTextColor,
            background = captionBackground
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
                    .background(Color.Black.copy(alpha = 0.32f))
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
                            Icon(Icons.Rounded.Settings, "Playback settings")
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
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (showQueueControls) {
                        QueueSkipButton(
                            icon = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous in playlist",
                            enabled = hasPreviousInQueue,
                            onClick = onPreviousInQueue
                        )
                    }
                    ExpressivePlayPauseButton(isPlaying = isPlaying, isBuffering = isBuffering, onClick = onPlayPause)
                    if (showQueueControls) {
                        QueueSkipButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = "Next in playlist",
                            enabled = hasNextInQueue,
                            onClick = onNextInQueue
                        )
                    }
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
                            mediaId = videoId,
                            progress = progress,
                            bufferedProgress = bufferedProgress,
                            onSeek = onSeek,
                            modifier = Modifier.weight(1f),
                            chapters = chapters,
                            durationMs = duration,
                            seekPreview = seekPreview,
                            showSeekPreview = !isLive,
                            onScrubbingChanged = onScrubbingChanged,
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

/** Time distance close enough to confirm that the position poll observed a seek. */
private const val SEEK_CONFIRM_TOLERANCE_MS = 1_000f

/** Backstop for failed or heavily rounded seeks; prevents a stale optimistic thumb. */
private const val SEEK_COMMIT_TIMEOUT_MS = 1_500L

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
    mediaId: String,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    chapters: List<VideoChapter> = emptyList(),
    durationMs: Long = 0L,
    seekPreview: VideoSeekPreview? = null,
    showSeekPreview: Boolean = true,
    onScrubbingChanged: (Boolean) -> Unit = {},
    onTonalSurface: Boolean = false
) {
    var isScrubbing by remember(mediaId) { mutableStateOf(false) }
    var scrubValue by remember(mediaId) { mutableFloatStateOf(0f) }
    var committedSeekValue by remember(mediaId) { mutableStateOf<Float?>(null) }
    val currentScrubbingChanged by rememberUpdatedState(onScrubbingChanged)
    val externalProgress = progress.coerceIn(0f, 1f)
    val displayedProgress = when {
        isScrubbing -> scrubValue
        committedSeekValue != null -> committedSeekValue ?: externalProgress
        else -> externalProgress
    }

    // A seek changes ExoPlayer immediately, but the StateFlow feeding this
    // composable is sampled twice a second. Hold the committed value until the
    // poll catches up so the thumb never flashes back to the pre-seek position.
    LaunchedEffect(externalProgress, mediaId) {
        val target = committedSeekValue ?: return@LaunchedEffect
        val distanceMs = abs(externalProgress - target) * durationMs.toFloat()
        if (distanceMs <= SEEK_CONFIRM_TOLERANCE_MS) {
            committedSeekValue = null
        }
    }
    LaunchedEffect(committedSeekValue, mediaId) {
        val target = committedSeekValue ?: return@LaunchedEffect
        delay(SEEK_COMMIT_TIMEOUT_MS)
        if (committedSeekValue == target) committedSeekValue = null
    }
    DisposableEffect(mediaId) {
        onDispose { currentScrubbingChanged(false) }
    }

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
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = inactiveTrackColor
    )

    // Keep the control's measured height identical before and during a drag.
    // The preview is an overlay: its negative offset changes where it draws,
    // not the space the bottom controls reserve for this seek bar.
    BoxWithConstraints(modifier = modifier.height(48.dp)) {
        Slider(
            value = displayedProgress,
            onValueChange = {
                if (!isScrubbing) {
                    isScrubbing = true
                    committedSeekValue = null
                    onScrubbingChanged(true)
                }
                scrubValue = it
            },
            onValueChangeFinished = {
                if (isScrubbing) {
                    val target = scrubValue.coerceIn(0f, 1f)
                    committedSeekValue = target
                    onSeek(target)
                }
                isScrubbing = false
                onScrubbingChanged(false)
            },
            enabled = durationMs > 0L,
            colors = sliderColors,
            track = { sliderState ->
                // The track slot is measured to the slider's actual travel
                // width and placed half a thumb in from either edge. Drawing
                // buffer and chapter marks here keeps them on the same geometry
                // as the gesture, instead of using the wider 48dp touch target.
                Box {
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        enabled = durationMs > 0L,
                        colors = sliderColors,
                    )
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val centerY = size.height / 2f
                        val buffered = bufferedProgress.coerceIn(0f, 1f)
                        if (buffered > displayedProgress) {
                            // A round cap extends half the stroke past each
                            // endpoint. At 100% that used to draw beyond the
                            // seek bar's right edge (most visible once the
                            // whole video was cached). Map the buffer onto an
                            // inset centerline so both caps remain inside it.
                            val strokeWidth = 4.dp.toPx()
                            val capRadius = strokeWidth / 2f
                            val drawableWidth = (size.width - strokeWidth).coerceAtLeast(0f)
                            val startX = capRadius + displayedProgress.coerceIn(0f, 1f) * drawableWidth
                            val endX = capRadius + buffered * drawableWidth
                            drawLine(
                                color = bufferedColor,
                                start = Offset(startX, centerY),
                                end = Offset(endX, centerY),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round,
                            )
                        }

                        if (chapters.size > 1 && durationMs > 0L) {
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
                                        strokeWidth = stroke,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (showSeekPreview && isScrubbing && durationMs > 0L) {
            val previewWidth = if (seekPreview?.isUsable == true) 144.dp else 72.dp
            val previewX = (maxWidth * scrubValue - previewWidth / 2)
                .coerceIn(0.dp, (maxWidth - previewWidth).coerceAtLeast(0.dp))
            SeekPreviewCard(
                preview = seekPreview,
                positionMs = (scrubValue * durationMs).toLong(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = previewX, y = if (seekPreview?.isUsable == true) (-126).dp else (-46).dp)
                    // Measure the card at its natural size, but report only the
                    // seek bar's fixed bounds to the parent. Without this, the
                    // 108dp storyboard card makes the whole bottom row taller
                    // and visibly pushes the slider upward as dragging begins.
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
            )
        }

    }
}

/** Storyboard frame (when available) plus the exact target time while dragging. */
@Composable
private fun SeekPreviewCard(
    preview: VideoSeekPreview?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val frame = preview?.frameAt(positionMs)
    val localVideoUri = preview?.localVideoUri
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.9f),
        contentColor = Color.White,
        shadowElevation = 4.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (localVideoUri != null) {
                LocalVideoSeekFrame(
                    uriString = localVideoUri,
                    positionMs = positionMs,
                )
            } else if (preview != null && frame != null) {
                val frameWidth = 144.dp
                val frameHeight = frameWidth *
                    (preview.frameHeightPx.toFloat() / preview.frameWidthPx.toFloat())
                val pageRequest = remember(
                    frame.pageUrl,
                    preview.frameWidthPx,
                    preview.frameHeightPx,
                    preview.framesPerPageX,
                    preview.framesPerPageY,
                ) {
                    ImageRequest.Builder(context)
                        .data(frame.pageUrl)
                        // Decode at the storyboard's real dimensions rather
                        // than the density-scaled size of the Compose grid.
                        .size(
                            preview.frameWidthPx * preview.framesPerPageX,
                            preview.frameHeightPx * preview.framesPerPageY,
                        )
                        // A long video can have many multi-megabyte pages. The
                        // visible painter already owns the current one; keeping
                        // every page in Coil's memory cache exhausts the 128MB
                        // heap after a scrub across the timeline. Compressed
                        // pages remain on disk, so revisiting one avoids network.
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .allowRgb565(true)
                        .crossfade(false)
                        .build()
                }
                Box(
                    modifier = Modifier
                        .size(frameWidth, frameHeight)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                ) {
                    AsyncImage(
                        model = pageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        // `size` is capped by the one-frame parent constraints,
                        // which shrinks the whole storyboard into 144dp and
                        // makes every translated cell except the first blank.
                        // The sprite must retain its full grid dimensions; the
                        // clipped parent is the viewport onto the selected cell.
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(
                                frameWidth * preview.framesPerPageX,
                                frameHeight * preview.framesPerPageY,
                            )
                            .offset(
                                x = -(frameWidth * frame.column),
                                y = -(frameHeight * frame.row),
                            )
                    )
                }
            }
            Text(
                text = formatDuration(positionMs),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * Decode downloaded-video previews locally, one request at a time.
 *
 * Positions are bucketed and briefly debounced because a slider can emit
 * hundreds of values in one drag. The mutex prevents cancelled native decoder
 * calls from piling up; the last good frame remains visible while the next one
 * is extracted. Frames are deliberately small and recycled when replaced.
 */
@Composable
private fun LocalVideoSeekFrame(
    uriString: String,
    positionMs: Long,
) {
    val context = LocalContext.current
    val extractionMutex = remember(uriString) { Mutex() }
    val frameTimeMs = (positionMs.coerceAtLeast(0L) / LOCAL_PREVIEW_BUCKET_MS) *
        LOCAL_PREVIEW_BUCKET_MS
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uriString, frameTimeMs) {
        delay(LOCAL_PREVIEW_DEBOUNCE_MS)
        val next = extractionMutex.withLock {
            extractLocalVideoFrame(context, uriString, frameTimeMs)
        }
        if (next != null) bitmap = next
    }

    val displayedBitmap = bitmap
    DisposableEffect(displayedBitmap) {
        onDispose { displayedBitmap?.recycle() }
    }

    Box(
        modifier = Modifier
            .size(LOCAL_PREVIEW_WIDTH, LOCAL_PREVIEW_HEIGHT)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (displayedBitmap != null) {
            Image(
                bitmap = displayedBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ContainedLoadingIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

private suspend fun extractLocalVideoFrame(
    context: Context,
    uriString: String,
    positionMs: Long,
): Bitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        val uri = Uri.parse(uriString)
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return@withContext null
            retriever.setDataSource(path)
        } else {
            retriever.setDataSource(context, uri)
        }
        val encodedWidth = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?: LOCAL_PREVIEW_WIDTH_PX
        val encodedHeight = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?: LOCAL_PREVIEW_HEIGHT_PX
        val rotation = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
        val (targetWidth, targetHeight) = localPreviewPixelSize(
            encodedWidth = encodedWidth,
            encodedHeight = encodedHeight,
            rotationDegrees = rotation,
        )
        retriever.getScaledFrameAtTime(
            positionMs * 1_000L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            targetWidth,
            targetHeight,
        )
    } catch (_: Exception) {
        null
    } finally {
        retriever.close()
    }
}

private fun localPreviewPixelSize(
    encodedWidth: Int,
    encodedHeight: Int,
    rotationDegrees: Int,
): Pair<Int, Int> {
    if (encodedWidth <= 0 || encodedHeight <= 0) {
        return LOCAL_PREVIEW_WIDTH_PX to LOCAL_PREVIEW_HEIGHT_PX
    }
    val quarterTurn = rotationDegrees.mod(180) != 0
    val displayWidth = if (quarterTurn) encodedHeight else encodedWidth
    val displayHeight = if (quarterTurn) encodedWidth else encodedHeight
    val scale = min(
        LOCAL_PREVIEW_WIDTH_PX.toFloat() / displayWidth.toFloat(),
        LOCAL_PREVIEW_HEIGHT_PX.toFloat() / displayHeight.toFloat(),
    )
    return (displayWidth * scale).roundToInt().coerceAtLeast(1) to
        (displayHeight * scale).roundToInt().coerceAtLeast(1)
}

private const val LOCAL_PREVIEW_BUCKET_MS = 1_000L
private const val LOCAL_PREVIEW_DEBOUNCE_MS = 60L
private const val LOCAL_PREVIEW_WIDTH_PX = 288
private const val LOCAL_PREVIEW_HEIGHT_PX = 162
private val LOCAL_PREVIEW_WIDTH = 144.dp
private val LOCAL_PREVIEW_HEIGHT = 81.dp

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
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()

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
    // of the app must not stay stuck at the video's brightness). The
    // remember setting turns the carry-over off; a fresh pref read rather than
    // the flow, because this effect only runs on fullscreen entry anyway.
    if (fullscreenGesturesEnabled) {
        DisposableEffect(activity) {
            activity?.let { act ->
                val saved = themePreferences.getVideoBrightness()
                if (themePreferences.getRememberVideoBrightness() &&
                    saved != ThemePreferences.VIDEO_BRIGHTNESS_UNSET
                ) {
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
                            // over on its own. The remember setting decides
                            // whether the level survives to the next video.
                            if (mode == 1 && leftSide &&
                                themePreferences.getRememberVideoBrightness()
                            ) {
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
    onDownloadClick: () -> Unit = {},
    onChannelClick: () -> Unit = {},
    onRelatedLongPress: ((VideoItem) -> Unit)? = null,
    /**
     * The playlist this video is being watched through, when there is one. Null
     * for a one-off video, which is most of them.
     */
    queue: com.ivor.ivormusic.data.VideoQueue? = null,
    onOpenQueue: () -> Unit = {},
    /** Seek the player, in seconds. Enables timestamp links in the description. */
    onSeekTo: ((seconds: Long) -> Unit)? = null,
    isOffline: Boolean = false,
    isLive: Boolean = false,
    /** Concurrent viewers, refreshed while the stream is open. */
    liveViewerCount: String? = null,
    onLiveChatClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
            // Applied inside verticalScroll, so this is scrolling clearance
            // rather than a viewport inset: the list passes under the
            // navigation bar and only its last item has to clear it. The
            // parent no longer takes the bottom inset for the same reason.
            .padding(
                bottom = 80.dp +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
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
            } else if (isOffline) {
                Text(
                    text = "Available offline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
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

        // The playlist this is being watched through. Directly under the title
        // because it is context for what is on screen rather than a section of
        // its own, and it is the only thing on the page that says where the
        // next video is coming from.
        if (queue != null) {
            PlayingFromPlaylistCard(queue = queue, onClick = onOpenQueue)
        }

        // Like / Dislike + Save + Share Actions (scrolls like YouTube's chip
        // row so the pills never squash on narrow screens)
        if (!isOffline) Row(
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
            DownloadVideoButton(video = video, onDownloadClick = onDownloadClick)
        }

        // Channel Info Surface (tap navigates to the channel)
        if (!isOffline) Surface(
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
        if (!isLive && !isOffline) {
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
                    // "Up Next" is a promise about what plays when this ends,
                    // and inside a playlist that promise belongs to the queue.
                    // These are then just recommendations, and saying so is the
                    // difference between the header being true and being wrong.
                    text = if (queue != null) "Related videos" else "Up Next",
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
                                .width(144.dp)
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
 * Previous / next within the playlist, flanking play/pause.
 *
 * Only composed while a queue is running: on a one-off video both buttons would
 * be permanently dead, which is the same reason the media session withdraws the
 * transport commands (see VideoPlaybackService). Disabled rather than hidden at
 * the ends of the playlist, so the pair does not shuffle sideways under a finger
 * that is already reaching for it.
 */
@Composable
private fun QueueSkipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    onTonalSurface: Boolean = false
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (onTonalSurface) MaterialTheme.colorScheme.secondaryContainer
                else Color.Black.copy(0.5f),
            contentColor = if (onTonalSurface) MaterialTheme.colorScheme.onSecondaryContainer
                else Color.White,
            disabledContainerColor = if (onTonalSurface) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else Color.Black.copy(0.3f),
            disabledContentColor = if (onTonalSurface) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            } else Color.White.copy(0.35f)
        ),
        // Same "square flash" guard the rest of the control chrome uses.
        shapes = IconButtonDefaults.shapes()
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/**
 * "Playing from <playlist>", with what comes next and the way into the queue.
 *
 * The one thing on the watch page that admits a playlist is running. Tapping it
 * opens [VideoQueueSheet]; the next title is spelled out rather than left to the
 * sheet because the question this card answers most often is "what am I about to
 * get", and answering it costs a line rather than a tap.
 */
@Composable
private fun PlayingFromPlaylistCard(
    queue: com.ivor.ivormusic.data.VideoQueue,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Playing from ${queue.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = queue.videos.getOrNull(queue.index + 1)
                        ?.let { "${queue.positionLabel} • Next: ${it.title}" }
                        ?: "${queue.positionLabel} • Last video",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = "Open the playlist queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
 * Download pill. A new download opens the quality-and-size sheet; an active or
 * completed one retains its cancel/delete behavior. This keeps the convenient
 * status control without bypassing the user's quality choice.
 */
@Composable
private fun DownloadVideoButton(
    video: VideoItem,
    onDownloadClick: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) {
        com.ivor.ivormusic.data.DownloadRepository.getInstance(context)
    }
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
                else -> onDownloadClick()
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
    size: androidx.compose.ui.unit.Dp = 72.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Expressive Spring Animation for Scale
    val scale by animateDpAsState(
        targetValue = if (isPressed) size * 0.95f else size,
        animationSpec = spring(
            dampingRatio = 0.72f,
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
