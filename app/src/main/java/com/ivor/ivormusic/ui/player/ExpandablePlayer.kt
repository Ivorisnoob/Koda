package com.ivor.ivormusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.ui.components.MiniPlayerContent
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * A container that expands from a MiniPlayer (floating pill) to a Full Screen Player.
 * Uses a single animated progress value to drive all property interpolations for optimal performance.
 * Leverages Material Physics motion scheme for smooth, interruptible animations.
 * 
 * Swipe gestures:
 * - Swipe UP on mini player: Expand to full player
 * - Swipe DOWN on full player: Collapse to mini player
 * - Swipe LEFT/RIGHT on mini player: Dismiss/clear player
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandablePlayer(
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    progress: Float,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    artworkColors: Boolean = false,
    playerStyle: PlayerStyle = PlayerStyle.CLASSIC,
    onPlayerStyleChange: (PlayerStyle) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return

    // Long-pressing the artwork in any style summons the style wheel; it
    // lives here, above whichever style is active, so the player can morph
    // live underneath it. The controller streams the hold-drag-release
    // gesture from the artwork into the wheel.
    val styleWheel = rememberPlayerStyleWheelController()
    LaunchedEffect(isExpanded) {
        if (!isExpanded) styleWheel.dismiss()
    }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    val bottomWindowInsets = WindowInsets.navigationBars
    val bottomInset = with(density) { bottomWindowInsets.getBottom(this).toDp() }
    
    // Single animated progress (0f = collapsed, 1f = expanded)
    // Using Material Physics slowSpatialSpec for full-screen animations
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "expandProgress"
    )
    
    // Derive all properties from the single progress value
    val collapsedHeight = 80.dp
    val collapsedWidthPadding = 16.dp
    val collapsedBottomPadding = 100.dp + bottomInset
    // Exactly half the collapsed height: a radius larger than that (the old
    // 50.dp) is illegal for the shape and rendered visibly distorted corners.
    // It also now matches MiniPlayerContent's inner 50% pill, so the ripple
    // and the container clip along the same outline.
    val collapsedCornerRadius = collapsedHeight / 2

    val expandedHeight = screenHeight
    val expandedWidthPadding = 0.dp
    val expandedBottomPadding = 0.dp
    val expandedCornerRadius = 0.dp

    // Interpolated values based on progress
    val height = lerp(collapsedHeight, expandedHeight, expandProgress)
    val widthPadding = lerp(collapsedWidthPadding, expandedWidthPadding, expandProgress)
    val bottomPadding = lerp(collapsedBottomPadding, expandedBottomPadding, expandProgress)
    val cornerRadius = lerp(collapsedCornerRadius, expandedCornerRadius, expandProgress)
        .coerceAtMost(height / 2)
    // Soft floating-pill depth while collapsed, gone once fullscreen
    val pillShadowElevation = lerp(8.dp, 0.dp, expandProgress)

    // Color interpolation - collapsed shows surface, expanded shows transparent
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
        alpha = 1f - expandProgress
    )

    // Swipe Logic for expand/collapse (vertical)
    var verticalDragOffset by remember { mutableFloatStateOf(0f) }
    val verticalSwipeThreshold = -50f
    
    // Swipe Logic for dismiss (horizontal) - only when collapsed
    var horizontalDragOffset by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val horizontalDismissThreshold = with(density) { 100.dp.toPx() }
    
    // Dismiss animation - slides out and fades
    val dismissOffsetTarget = if (isDismissing) {
        // Slide out in the direction of the swipe
        if (horizontalDragOffset > 0) with(density) { screenWidth.toPx() } else with(density) { -screenWidth.toPx() }
    } else {
        horizontalDragOffset
    }
    
    val animatedHorizontalOffset by animateFloatAsState(
        targetValue = if (!isExpanded) dismissOffsetTarget else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        finishedListener = { 
            if (isDismissing) {
                viewModel.clearPlayer()
                isDismissing = false
                horizontalDragOffset = 0f
            }
        },
        label = "horizontalOffset"
    )
    
    // Alpha based on swipe distance
    val dismissAlpha = if (isDismissing) 0f else 1f - (animatedHorizontalOffset.absoluteValue / (horizontalDismissThreshold * 2)).coerceIn(0f, 0.5f)

    // Container
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .padding(bottom = bottomPadding.coerceAtLeast(0.dp))
                .padding(horizontal = widthPadding.coerceAtLeast(0.dp))
                .offset { IntOffset(animatedHorizontalOffset.roundToInt(), 0) }
                .graphicsLayer { alpha = dismissAlpha }
                .fillMaxWidth()
                .height(height.coerceAtLeast(0.dp))
                .pointerInput(isExpanded) {
                    if (isExpanded) {
                        // Expanded: Only handle vertical drag for collapse
                        detectVerticalDragGestures(
                            onDragStart = { verticalDragOffset = 0f },
                            onDragEnd = {
                                if (verticalDragOffset > -verticalSwipeThreshold) {
                                    onExpandChange(false)
                                }
                                verticalDragOffset = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                verticalDragOffset += dragAmount
                            }
                        )
                    } else {
                        // Collapsed: Handle horizontal drag for dismiss
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDragOffset = 0f },
                            onDragEnd = {
                                if (horizontalDragOffset.absoluteValue > horizontalDismissThreshold) {
                                    // Trigger dismiss animation - don't reset offset yet
                                    isDismissing = true
                                } else {
                                    // Snap back
                                    horizontalDragOffset = 0f
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                horizontalDragOffset += dragAmount
                            }
                        )
                    }
                }
                .pointerInput(isExpanded) {
                    if (!isExpanded) {
                        // Collapsed: Also handle vertical drag for expand
                        detectVerticalDragGestures(
                            onDragStart = { verticalDragOffset = 0f },
                            onDragEnd = {
                                if (verticalDragOffset < verticalSwipeThreshold) {
                                    onExpandChange(true)
                                }
                                verticalDragOffset = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                verticalDragOffset += dragAmount
                            }
                        )
                    }
                }
                .clickable(enabled = !isExpanded) { onExpandChange(true) },
            shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp)),
            color = containerColor,
            shadowElevation = pillShadowElevation.coerceAtLeast(0.dp)
        ) {
            // Both layers are positioned in a Box sized to the current (animating)
            // Surface height, which the Surface shape clips. The expanded content
            // is given a FIXED full-screen height so it is measured exactly once —
            // the growing Surface merely reveals/clips it instead of forcing the
            // whole now-playing screen (and its ambient shader) to re-lay-out every
            // frame. A single expandProgress value drives the mini/full crossfade.
            Box(modifier = Modifier.fillMaxSize()) {

                // --- Mini layer: fades out over the first part of the expansion ---
                if (expandProgress < 0.999f) {
                    val miniAlpha = (1f - expandProgress / 0.4f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(collapsedHeight)
                            .graphicsLayer { alpha = miniAlpha }
                    ) {
                        MiniPlayerContent(
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            playWhenReady = playWhenReady,
                            progress = progress,
                            onPlayPauseClick = onPlayPauseClick,
                            onNextClick = onNextClick,
                            onClick = { onExpandChange(true) }
                        )
                    }
                }

                // --- Full layer: fixed height, fades in over the latter part ---
                if (isExpanded || expandProgress > 0.001f) {
                    val fullAlpha = ((expandProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    // Optionally re-theme the expanded player's accent roles
                    // from the album cover; every style's buttons read
                    // MaterialTheme.colorScheme, so one wrapper covers them
                    // all. The mini player stays on the app theme.
                    // Local songs carry albumArtUri, YouTube songs only a
                    // thumbnailUrl (Palette samples at 128px, so the normal
                    // -res thumbnail is plenty and never 404s like maxres)
                    val playerColorScheme = rememberArtworkColorScheme(
                        enabled = artworkColors,
                        albumArtUri = currentSong.albumArtUri?.toString()
                            ?: currentSong.thumbnailUrl,
                        base = MaterialTheme.colorScheme
                    )
                    MaterialTheme(colorScheme = playerColorScheme) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(expandedHeight)
                            .graphicsLayer { alpha = fullAlpha }
                    ) {
                        // The live player blurs beneath the style wheel.
                        val wheelBlur by animateDpAsState(
                            targetValue = if (styleWheel.isOpen) 24.dp else 0.dp,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "StyleWheelBlur"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (wheelBlur > 0.5.dp) Modifier.blur(wheelBlur) else Modifier)
                        ) {
                        // Any artwork can host the wheel's hold gesture
                        // through this local, without per-style plumbing.
                        CompositionLocalProvider(
                            LocalPlayerStyleWheelController provides styleWheel
                        ) {
                        // Crossfade makes a live style swap (from the style
                        // wheel or Settings) a soft morph instead of a cut.
                        Crossfade(
                            targetState = playerStyle,
                            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                            label = "PlayerStyleSwap"
                        ) { activeStyle ->
                        when (activeStyle) {
                            PlayerStyle.CLASSIC -> {
                                PlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick
                                )
                            }
                            PlayerStyle.GESTURE -> {
                                GesturePlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick
                                )
                            }
                            PlayerStyle.EDITORIAL -> {
                                EditorialPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick
                                )
                            }
                            PlayerStyle.POSTER -> {
                                PosterPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick
                                )
                            }
                            PlayerStyle.BENTO -> {
                                BentoPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick
                                )
                            }
                            PlayerStyle.STICKER -> {
                                StickerPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick
                                )
                            }
                            PlayerStyle.MORPH -> {
                                MorphPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick
                                )
                            }
                            PlayerStyle.DIAL -> {
                                DialPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick
                                )
                            }
                        }
                        }
                        }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = styleWheel.isOpen,
                            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                        ) {
                            PlayerStyleWheel(
                                currentStyle = playerStyle,
                                controller = styleWheel,
                                onStyleSelected = onPlayerStyleChange,
                                onDismiss = { styleWheel.dismiss() }
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}
