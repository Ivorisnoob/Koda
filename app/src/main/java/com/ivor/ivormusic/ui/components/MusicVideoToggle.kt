package com.ivor.ivormusic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Thumb animation for [MusicVideoToggle], hoisted out of the toggle itself.
 *
 * Flipping the mode swaps the whole home content (music top bar vs video top bar),
 * which destroys and recreates the toggle mid-transition. Both top bars render a
 * toggle bound to the same instance of this state, so the thumb keeps sliding
 * across the content swap instead of snapping to its final position.
 */
@Stable
class MusicVideoToggleState(initialVideoMode: Boolean) {
    internal val fastEdge = Animatable(if (initialVideoMode) 1f else 0f)
    internal val slowEdge = Animatable(if (initialVideoMode) 1f else 0f)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberMusicVideoToggleState(videoMode: Boolean): MusicVideoToggleState {
    val state = remember { MusicVideoToggleState(videoMode) }
    val fastSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val defaultSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(videoMode) {
        val target = if (videoMode) 1f else 0f
        // The leading edge races ahead on a fast spring while the trailing edge
        // follows on the default one, stretching the thumb like liquid mid-flight.
        launch { state.fastEdge.animateTo(target, fastSpec) }
        launch { state.slowEdge.animateTo(target, defaultSpec) }
    }
    return state
}

/**
 * Pill-shaped music/video mode switch matching the 44dp top bar icon buttons.
 * A primary-container thumb slides (and stretches) between the two segments
 * with expressive spring motion; the active icon pops in with a bounce.
 */
@Composable
fun MusicVideoToggle(
    videoMode: Boolean,
    onVideoModeChange: (Boolean) -> Unit,
    state: MusicVideoToggleState,
    modifier: Modifier = Modifier
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val segmentWidth = 38.dp
    val segmentHeight = 36.dp

    Surface(
        modifier = modifier.height(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            // Overshoot is clamped so the stretched thumb squashes against the
            // track ends instead of poking outside them.
            val start = minOf(state.fastEdge.value, state.slowEdge.value).coerceAtLeast(0f)
            val end = maxOf(state.fastEdge.value, state.slowEdge.value).coerceAtMost(1f)
            Box(
                modifier = Modifier
                    .offset(x = segmentWidth * start)
                    .width(segmentWidth * (1f + end - start))
                    .height(segmentHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Row {
                ToggleSegment(
                    icon = Icons.Rounded.MusicNote,
                    contentDescription = "Switch to music mode",
                    selected = !videoMode,
                    segmentWidth = segmentWidth,
                    segmentHeight = segmentHeight,
                    onClick = {
                        if (videoMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onVideoModeChange(false)
                        }
                    }
                )
                ToggleSegment(
                    icon = Icons.Rounded.VideoLibrary,
                    contentDescription = "Switch to video mode",
                    selected = videoMode,
                    segmentWidth = segmentWidth,
                    segmentHeight = segmentHeight,
                    onClick = {
                        if (!videoMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onVideoModeChange(true)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ToggleSegment(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    segmentWidth: Dp,
    segmentHeight: Dp,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "segmentTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "segmentScale"
    )

    Box(
        modifier = Modifier
            .size(width = segmentWidth, height = segmentHeight)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .scale(scale)
        )
    }
}
