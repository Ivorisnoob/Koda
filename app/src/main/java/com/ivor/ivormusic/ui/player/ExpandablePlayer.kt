package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.animation.animateColor
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.ui.components.MiniPlayerContent

/**
 * A container that expands from a MiniPlayer (floating pill) to a Full Screen Player.
 * Animates bounds, corners, and padding.
 */
@androidx.compose.animation.ExperimentalAnimationApi
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

    val transition = updateTransition(isExpanded, label = "ExpandParams")
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    // Calculate bottom padding based on NavBar height (~90dp) + system insets
    val density = LocalDensity.current
    val bottomWindowInsets = WindowInsets.navigationBars
    val bottomInset = with(density) { bottomWindowInsets.getBottom(this).toDp() }
    
    // Animate container properties
    val height by transition.animateDp(
        transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
        label = "height"
    ) { expanded ->
        if (expanded) screenHeight else 80.dp
    }
    
    val widthPadding by transition.animateDp(
        transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
        label = "widthPadding"
    ) { expanded ->
        if (expanded) 0.dp else 16.dp
    }

    val bottomPadding by transition.animateDp(
        transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
        label = "bottomPadding"
    ) { expanded ->
        // FloatingPillNavBar is ~88dp tall + padding. We place MiniPlayer just above it.
        if (expanded) 0.dp else (100.dp + bottomInset)
    }
    
    val cornerRadius by transition.animateDp(
        transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
        label = "cornerRadius"
    ) { expanded ->
        if (expanded) 0.dp else 50.dp
    }

    // Animate container color: SurfaceHigh when collapsed, Transparent when expanded 
    val containerColor by transition.animateColor(label = "containerColor") { expanded ->
        if (expanded) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh
    }

    // Swipe Logic
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = -50f

    // Container
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Enforce padding via Box content alignment and absolute offset or padding
        // Using padding on the box itself might clip content if we aren't careful? and this caused some crashes lmao here and there as expected
        // Let's stick to the previous structure but ensure correct Z-index/HitTest
        
        Surface(
            modifier = Modifier
                .padding(bottom = bottomPadding.coerceAtLeast(0.dp)) // Clamp to prevent negative
                .padding(horizontal = widthPadding.coerceAtLeast(0.dp)) // Clamp to prevent negative
                .fillMaxWidth()
                .height(height.coerceAtLeast(0.dp)) 
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragOffset = 0f },
                        onDragEnd = {
                            if (dragOffset < swipeThreshold && !isExpanded) {
                                onExpandChange(true)
                            } else if (dragOffset > -swipeThreshold && isExpanded) {
                                onExpandChange(false)
                            }
                            dragOffset = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                        }
                    )
                }
                .clickable(enabled = !isExpanded) { onExpandChange(true) },
            shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp)),
            color = containerColor,
            shadowElevation = if (isExpanded) 0.dp else 8.dp,
            tonalElevation = if (isExpanded) 0.dp else 4.dp
        ) {
            // Content Crossfade with better transition
            transition.AnimatedContent(
                transitionSpec = {
                    if (targetState) {
                        // Expanding: Fade In + Scale Up, while MiniPlayer Fades Out
                        (fadeIn(animationSpec = spring(stiffness = 300f)) + 
                         scaleIn(initialScale = 0.9f, animationSpec = spring(stiffness = 300f))) with
                        (fadeOut(animationSpec = spring(stiffness = 300f)))
                    } else {
                        // Collapsing: MiniPlayer Fades In, FullPlayer Fades Out + Scales Down
                        (fadeIn(animationSpec = spring(stiffness = 300f))) with
                        (fadeOut(animationSpec = spring(stiffness = 300f)) + 
                         scaleOut(targetScale = 0.9f, animationSpec = spring(stiffness = 300f)))
                    }
                },
                contentKey = { it }
            ) { targetExpanded ->
                if (targetExpanded) {
                    // Guard: Skip rendering complex player content when height is too small
                    // This prevents IllegalArgumentException: maxWidth >= minWidth constraint failures
                    // during the collapse/expand animation when container dimensions are invalid
                    // Use relative threshold (25% of screen) to work on all DPI settings
                    val minSafeHeight = screenHeight * 0.25f
                    if (height < minSafeHeight) {
                        // Show simple placeholder during animation transition
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


