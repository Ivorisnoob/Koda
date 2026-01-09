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
import com.ivor.ivormusic.ui.components.MiniPlayerContent

/**
 * A container that expands from a MiniPlayer (floating pill) to a Full Screen Player.
 * Animates bounds, corners, and padding.
 */
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
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = if (isExpanded) 0.dp else 8.dp,
            tonalElevation = if (isExpanded) 0.dp else 4.dp
        ) {
            // Content Crossfade
            transition.AnimatedContent(
                contentKey = { it }
            ) { targetExpanded ->
                if (targetExpanded) {
                    Box(modifier = Modifier.fillMaxSize()) {
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


