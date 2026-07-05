package com.ivor.ivormusic.ui.video

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.VideoEngagement
import com.ivor.ivormusic.data.VideoItem
import kotlinx.coroutines.delay
import java.util.Locale

// VideoPlayerScreen function removed.
// Logic moved to VideoPlayerViewModel and VideoPlayerContent.
// Keeping helper composables for reuse.

// ---------------- Sub-Composables ----------------

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
    isAutoPlayEnabled: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    onLoopToggle: () -> Unit,
    onAutoPlayToggle: () -> Unit,
    showTimedCommentsButton: Boolean = false,
    timedCommentsActive: Boolean = false,
    onTimedCommentsToggle: () -> Unit = {}
) {
    // Stable shapes to prevent "square flash"
    val stableShapes = IconButtonDefaults.shapes()

    PlayerGestureSurface(
        onToggleControls = onToggleControls,
        onSeekBackward = onSeekBackward,
        onSeekForward = onSeekForward
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
            modifier = Modifier.fillMaxSize()
        )
        
        // Overlays
        if (hasError) {
            ErrorOverlay(errorMessage)
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
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(0.7f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
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
                    
                    // Auto Play Toggle
                    FilledTonalIconButton(
                        onClick = onAutoPlayToggle,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isAutoPlayEnabled) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                            contentColor = if (isAutoPlayEnabled) MaterialTheme.colorScheme.onPrimary else Color.White
                        ),
                        shapes = stableShapes
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.Autorenew,
                            contentDescription = "Auto Play"
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onLoopToggle,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isLooping) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                            contentColor = if (isLooping) MaterialTheme.colorScheme.onPrimary else Color.White
                        ),
                        shapes = stableShapes
                    ) {
                         Icon(
                            if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Loop"
                        )
                    }

                    if (showTimedCommentsButton) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(formatDuration(currentPosition), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        
                        PlayerSeekBar(
                            progress = progress,
                            onSeek = onSeek,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelLarge)
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
    isAutoPlayEnabled: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    onLoopToggle: () -> Unit,
    onAutoPlayToggle: () -> Unit,
    showTimedCommentsButton: Boolean = false,
    timedCommentsActive: Boolean = false,
    onTimedCommentsToggle: () -> Unit = {}
) {
    // Stable shapes
    val stableShapes = IconButtonDefaults.shapes()

    PlayerGestureSurface(
        onToggleControls = onToggleControls,
        onSeekBackward = onSeekBackward,
        onSeekForward = onSeekForward
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
            modifier = Modifier.fillMaxSize()
        )
        
        if (hasError) ErrorOverlay(errorMessage)
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
                        // Auto Play Toggle
                         FilledTonalIconButton(
                            onClick = onAutoPlayToggle,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isAutoPlayEnabled) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                                contentColor = if (isAutoPlayEnabled) MaterialTheme.colorScheme.onPrimary else Color.White
                            ),
                            shapes = stableShapes
                        ) {
                            Icon(
                                Icons.Rounded.Autorenew,
                                contentDescription = "Auto Play"
                            )
                        }
                        
                         FilledTonalIconButton(
                            onClick = onLoopToggle,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isLooping) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                                contentColor = if (isLooping) MaterialTheme.colorScheme.onPrimary else Color.White
                            ),
                            shapes = stableShapes
                        ) {
                             Icon(
                                if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = "Loop"
                            )
                        }
                        if (showTimedCommentsButton) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(formatDuration(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        
                        PlayerSeekBar(
                            progress = progress,
                            onSeek = onSeek,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Scrubbing seek bar. While the user drags, the thumb follows a local value and
 * the player is NOT touched, so we don't kick off a buffer/fetch on every pixel.
 * The actual seek fires once, on release (onValueChangeFinished). The 500ms
 * progress poll from the parent is ignored during the drag to avoid the thumb
 * fighting the finger.
 */
@Composable
private fun PlayerSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

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
            inactiveTrackColor = Color.White.copy(0.3f)
        ),
        modifier = modifier
    )
}

/** Seconds jumped per double-tap on either edge of the video surface. */
private const val DOUBLE_TAP_SEEK_SECONDS = 10

/**
 * Wraps the video surface with tap gestures: single tap toggles the controls,
 * a double tap on the left rewinds and on the right fast-forwards (YouTube-style),
 * with an animated badge that accumulates when tapped repeatedly.
 */
@Composable
private fun PlayerGestureSurface(
    onToggleControls: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // side: -1 rewind, +1 forward, 0 hidden. seconds accumulates on rapid taps.
    var side by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    var pulse by remember { mutableIntStateOf(0) }

    // Hide the badge a short while after the last tap. Re-runs (and so resets
    // the timer) every double tap because it is keyed on `pulse`.
    LaunchedEffect(pulse) {
        if (side != 0) {
            delay(650)
            side = 0
            seconds = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        val tappedSide = if (offset.x < size.width / 2f) -1 else 1
                        seconds = if (tappedSide == side) seconds + DOUBLE_TAP_SEEK_SECONDS else DOUBLE_TAP_SEEK_SECONDS
                        side = tappedSide
                        pulse++
                        if (tappedSide < 0) onSeekBackward() else onSeekForward()
                    }
                )
            }
    ) {
        content()

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

@Composable
fun VideoInfoSection(
    video: VideoItem,
    relatedVideos: List<VideoItem>,
    onVideoSelect: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    engagement: VideoEngagement? = null,
    onLikeClick: () -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onSubscribeClick: () -> Unit = {},
    onCommentsClick: () -> Unit = {}
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

            Text(
                text = buildString {
                    if (video.viewCount.isNotEmpty()) append(video.viewCount)
                    if (!video.uploadedDate.isNullOrEmpty()) append(" • ${video.uploadedDate}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Like / Dislike Actions
        LikeDislikeBar(
            engagement = engagement,
            onLikeClick = onLikeClick,
            onDislikeClick = onDislikeClick
        )

        // Channel Info Surface
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
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
                    val isSubscribed = engagement?.isSubscribed == true
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

        // Comments Entry
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
                    val cleanedDescription = remember(video.description) {
                        if (video.description != null) {
                            androidx.core.text.HtmlCompat.fromHtml(
                                video.description,
                                androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
                            ).toString().trim()
                        } else ""
                    }
                    
                    if (cleanedDescription.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                        ) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = cleanedDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            if (cleanedDescription.length > 100 || cleanedDescription.count { it == '\n' } > 2) {
                                Text(
                                    text = if (isDescriptionExpanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
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
                            .clickable { onVideoSelect(relatedVideo) }
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
fun ErrorOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.White)
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
