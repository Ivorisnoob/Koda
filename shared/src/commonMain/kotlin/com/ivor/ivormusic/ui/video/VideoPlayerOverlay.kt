package com.ivor.ivormusic.ui.video

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.platform.PlatformBackHandler

/**
 * Overlay component for persistent video playback across the app.
 * Handles both In-App Mini Player and expanded full player.
 */
@Composable
fun VideoPlayerOverlay(
    viewModel: VideoPlayerViewModel
) {
    val isExpanded by viewModel.isExpanded.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()

    if (currentVideo == null) return

    // Handle Back Press to minimize player
    PlatformBackHandler(enabled = isExpanded) {
        viewModel.setExpanded(false)
    }

    val transition = updateTransition(isExpanded, label = "VideoExpand")
    val density = LocalDensity.current
    val bottomWindowInsets = WindowInsets.navigationBars
    val bottomInset = with(density) { bottomWindowInsets.getBottom(this).toDp() }

    // Container
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val screenHeight = maxHeight

        // Animate dimensions
        val height by transition.animateDp(
            transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
            label = "height"
        ) { expanded ->
            if (expanded) screenHeight else 88.dp
        }

        val widthPadding by transition.animateDp(
            transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
            label = "widthPadding"
        ) { expanded ->
            if (expanded) 0.dp else 16.dp
        }

        // Position above nav bar when minimized
        val bottomPadding by transition.animateDp(
            transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
            label = "bottomPadding"
        ) { expanded ->
            if (expanded) 0.dp else (100.dp + bottomInset)
        }

        Surface(
            modifier = Modifier
                .padding(bottom = bottomPadding.coerceAtLeast(0.dp))
                .padding(horizontal = widthPadding.coerceAtLeast(0.dp))
                .fillMaxWidth()
                .height(height.coerceAtLeast(0.dp))
                .clickable(enabled = !isExpanded) { viewModel.setExpanded(true) },
            shape = RoundedCornerShape(if (isExpanded) 0.dp else 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (isExpanded) 0.dp else 4.dp,
            shadowElevation = if (isExpanded) 0.dp else 12.dp
        ) {
             if (isExpanded) {
                 // Full Screen Content
                 VideoPlayerContent(
                     viewModel = viewModel,
                     onBackClick = {
                         viewModel.setExpanded(false)
                     }
                 )
             } else {
                 // Mini Player Content
                 MiniVideoPlayerContent(
                     viewModel = viewModel,
                     onExpand = { viewModel.setExpanded(true) },
                     onClose = { viewModel.closePlayer() }
                 )
             }
        }
    }
}
