package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
    playerStyle: PlayerStyle = PlayerStyle.CLASSIC,
    onArtistClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return

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
    val collapsedCornerRadius = 50.dp
    
    val expandedHeight = screenHeight
    val expandedWidthPadding = 0.dp
    val expandedBottomPadding = 0.dp
    val expandedCornerRadius = 0.dp
    
    // Interpolated values based on progress
    val height = lerp(collapsedHeight, expandedHeight, expandProgress)
    val widthPadding = lerp(collapsedWidthPadding, expandedWidthPadding, expandProgress)
    val bottomPadding = lerp(collapsedBottomPadding, expandedBottomPadding, expandProgress)
    val cornerRadius = lerp(collapsedCornerRadius, expandedCornerRadius, expandProgress)
    
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
            color = containerColor
            // No shadow/elevation for cleaner look
        ) {
            // Get animation specs from material motion scheme (must be called in composable scope)
            val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            
            // Content with simple crossfade - no scale transforms for performance
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    // Simple fade for better performance
                    fadeIn(animationSpec = fadeSpec) togetherWith fadeOut(animationSpec = fadeSpec)
                },
                contentKey = { it },
                label = "playerContent"
            ) { targetExpanded ->
                if (targetExpanded) {
                    // Guard: Skip rendering when height is too small during animation
                    val minSafeHeight = screenHeight * 0.25f
                    if (height < minSafeHeight) {
                        Box(modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (playerStyle) {
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
                            }
                        }
                    }
                } else {
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
        }
    }
}
