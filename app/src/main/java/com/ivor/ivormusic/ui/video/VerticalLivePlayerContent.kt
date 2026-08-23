package com.ivor.ivormusic.ui.video

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.CaptionBackground
import com.ivor.ivormusic.data.CaptionTextColor
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
 *
 * **Chrome is tonal, content is white.** Every control here - the chrome
 * buttons, the grouped cluster, the floating scrubber, the chat entry - is a
 * ColorScheme surface, the same bargain [com.ivor.ivormusic.ui.shorts]'s
 * overlay makes, so this screen actually changes with the user's palette,
 * AMOLED and dynamic color. It previously painted itself in black scrims and
 * white icons throughout, which rendered identically under all 29 palettes and
 * was the whole reason it did not look like the rest of the app. What stays
 * white is only what sits directly on the frame with no surface under it: the
 * title, the channel name, the chat ticker text and the captions. Those are
 * legibility over arbitrary video, and the same exception the caption overlay
 * and [LiveChatOverlay] already document.
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
    captionTextSize: Float,
    captionTextColor: CaptionTextColor,
    captionBackground: CaptionBackground,
    /** Null until the first frame decodes; drives the fill-vs-fit decision. */
    videoAspectRatio: Float?,
    isSubscribed: Boolean,
    likeStatus: LikeStatus,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit = {},
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToLive: () -> Unit,
    onBack: () -> Unit,
    onExitToPage: () -> Unit,
    onOpenFullChat: () -> Unit,
    onCaptionsClick: () -> Unit,
    onSettings: () -> Unit,
    onSubscribeClick: () -> Unit,
    onLikeClick: () -> Unit,
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

    // A stream with chat off draws no ticker, so the bottom stack shrinks to
    // the title and channel row and the scrim has to shrink with it - a
    // 420dp gradient under 100dp of content reads as a dirty screen.
    //
    // isChatAvailable is null until the first poll answers, so this settles a
    // second or two into the stream rather than at composition; animated,
    // because a scrim and the captions above it jumping by 180dp mid-watch is
    // the kind of snap the rest of the app does not do. The default Dp spring
    // is non-bouncy, which matters here - an overshoot would drive a height
    // negative.
    val chatShowing = isChatAvailable != false
    val bottomScrimHeight by animateDpAsState(
        targetValue = if (chatShowing) 420.dp else 240.dp,
        label = "liveBottomScrim"
    )
    val captionBottomPadding by animateDpAsState(
        targetValue = if (chatShowing) 310.dp else 130.dp,
        label = "liveCaptionInset"
    )

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

            // Scrims. Tonal chrome still needs something behind it: a daylight
            // IRL stream or a white-background broadcast washes out a 0.9-alpha
            // surface, and the top row had no scrim at all before this.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomScrimHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )

            // Lifted clear of the chat ticker rather than sitting behind it -
            // but only as far as the ticker actually reaches. A stream with
            // chat turned off draws none of it, and captions stranded in the
            // middle of the frame would be the only trace left of it.
            CaptionOverlay(
                cues = captionCues,
                player = exoPlayer,
                bottomPadding = captionBottomPadding,
                compact = true,
                textSize = captionTextSize,
                textColor = captionTextColor,
                background = captionBackground
            )

            if (hasError) ErrorOverlay(errorMessage, onRetry)

            // Only while the controls are down. With them up the play/pause
            // button draws its own shape-morphing loader, and two spinners on
            // one frame is just noise.
            AnimatedVisibility(
                visible = (isLoading || isBuffering) && !showControls && !hasError,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                ContainedLoadingIndicator()
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
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            .copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface
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

                // Three loose circles read as a browser toolbar. One container
                // holding all three reads as a control cluster, costs one scrim
                // instead of three, and keeps the row from spanning the frame.
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        // Shape carries the state as well as color: a filled
                        // circle that only changes hue is the least legible
                        // toggle available on top of moving video.
                        ChromeToggleButton(
                            icon = if (captionsActive) {
                                Icons.Rounded.ClosedCaption
                            } else {
                                Icons.Rounded.ClosedCaptionOff
                            },
                            contentDescription = "Captions",
                            active = captionsActive,
                            onClick = onCaptionsClick,
                        )
                        ChromeGroupButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = "Quality",
                            onClick = onSettings,
                        )
                        ChromeGroupButton(
                            // The way back to the watch page: comments, related,
                            // the description. The counterpart button on that
                            // page returns here.
                            icon = Icons.Rounded.CloseFullscreen,
                            contentDescription = "Show video details",
                            onClick = onExitToPage,
                        )
                    }
                }
            }

            // Play/pause is the part that earns its keep by disappearing - it is
            // only wanted when the user reaches for it.
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                )
            }
            AnimatedVisibility(
                visible = showControls,
                enter = scaleIn(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialScale = 0.8f
                ) + fadeIn(),
                exit = scaleOut(targetScale = 0.8f) + fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                ExpressivePlayPauseButton(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onClick = onPlayPause
                )
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

                // Subscribe and like are the two things people actually reach
                // for on a live stream, and before this both meant leaving the
                // layout for the watch page first.
                if (video.channelName.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(MaterialShapes.Cookie9Sided.toShape())
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                        .copy(alpha = 0.85f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!video.channelIconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = video.channelIconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = onSubscribeClick,
                            colors = if (isSubscribed) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        .copy(alpha = 0.92f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text(
                                text = if (isSubscribed) "Subscribed" else "Subscribe",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        ChromeToggleButton(
                            icon = if (likeStatus == LikeStatus.LIKE) {
                                Icons.Rounded.ThumbUp
                            } else {
                                Icons.Outlined.ThumbUp
                            },
                            contentDescription = "Like",
                            active = likeStatus == LikeStatus.LIKE,
                            onClick = onLikeClick,
                            size = 36.dp,
                            inactiveContainer = MaterialTheme.colorScheme.surfaceContainerHigh
                                .copy(alpha = 0.9f),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                LiveChatOverlay(
                    messages = chatMessages,
                    isAvailable = isChatAvailable,
                    canSend = canSendChat,
                    onOpenFullChat = onOpenFullChat,
                )

                AnimatedVisibility(
                    visible = showControls,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialOffsetY = { it }
                    ) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    // Floated and inset rather than a full-bleed black band.
                    // The band was the last piece of chrome still reading as a
                    // letterbox bar rather than as a control surface.
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 10.dp,
                                top = 4.dp,
                                bottom = 4.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PlayerSeekBar(
                                mediaId = video.videoId,
                                progress = progress,
                                bufferedProgress = bufferedProgress,
                                onSeek = onSeek,
                                modifier = Modifier.weight(1f),
                                durationMs = duration,
                                showSeekPreview = false,
                                onScrubbingChanged = onScrubbingChanged,
                                onTonalSurface = true
                            )
                            LiveEdgeChip(
                                atLiveEdge = duration <= 0L || progress >= LIVE_EDGE_THRESHOLD,
                                onClick = onSeekToLive,
                                contentTint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/**
 * One button inside the grouped top-right cluster. Transparent, because the
 * group's own container is the surface - a container per button is what made
 * the row read as three separate widgets.
 */
@Composable
private fun ChromeGroupButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * A toggle that says so with its shape: a circle when off, a
 * [MaterialShapes.Cookie9Sided] filled with secondaryContainer when on, with
 * the icon popping on a bouncy spring as it flips. Same treatment the Shorts
 * action rail uses, and the reason is the same - a container that only changes
 * hue is hard to read at a glance on top of moving video.
 *
 * [inactiveContainer] defaults to transparent for use inside the grouped
 * cluster, which supplies its own surface; a standalone one passes a container.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChromeToggleButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    inactiveContainer: Color = Color.Transparent,
) {
    val iconScale = remember { Animatable(1f) }
    var isInitial by remember { mutableStateOf(true) }

    LaunchedEffect(active) {
        // The composable enters with whatever state the video arrived in;
        // popping on that would flash every control on every video change.
        if (isInitial) {
            isInitial = false
            return@LaunchedEffect
        }
        if (active) {
            iconScale.snapTo(0.55f)
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            inactiveContainer
        },
        label = "chromeToggleContainer"
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(if (active) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .size(size * 0.55f)
                .graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                },
        )
    }
}
