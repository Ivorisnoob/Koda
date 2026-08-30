package com.ivor.ivormusic.ui.components
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

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
import androidx.compose.material.icons.rounded.Movie
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.AppMode
import kotlinx.coroutines.launch

/**
 * Thumb animation for [AppModeToggle], hoisted out of the toggle itself.
 *
 * Flipping the mode swaps the whole home content (music top bar vs video top
 * bar vs TV top bar), which destroys and recreates the toggle mid-transition.
 * Every top bar renders a toggle bound to the same instance of this state, so
 * the thumb keeps sliding across the content swap instead of snapping to its
 * final position.
 */
@Stable
class AppModeToggleState(initialMode: AppMode) {
    internal val fastEdge = Animatable(initialMode.ordinal.toFloat())
    internal val slowEdge = Animatable(initialMode.ordinal.toFloat())
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberAppModeToggleState(mode: AppMode): AppModeToggleState {
    val state = remember { AppModeToggleState(mode) }
    val fastSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val defaultSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(mode) {
        val target = mode.ordinal.toFloat()
        // The leading edge races ahead on a fast spring while the trailing edge
        // follows on the default one, stretching the thumb like liquid mid-flight.
        launch { state.fastEdge.animateTo(target, fastSpec) }
        launch { state.slowEdge.animateTo(target, defaultSpec) }
    }
    return state
}

/**
 * Pill-shaped music / video / TV mode switch matching the 44dp top bar icon
 * buttons. A primary-container thumb slides (and stretches) between the
 * segments with expressive spring motion; the active icon pops in with a bounce.
 *
 * **Segments are 32dp rather than the 38dp the two-mode version used**, and the
 * reason is arithmetic rather than taste. The top bar carries an avatar, up to
 * two icon buttons and this pill; at 38dp a third segment lands at roughly the
 * full usable width of a 320dp phone once `ui_scale` is at its 1.15 ceiling,
 * which clips. 32dp buys back enough headroom to survive the smallest supported
 * screen at the largest supported scale. Do not widen these without redoing
 * that measurement.
 */
@Composable
fun AppModeToggle(
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    state: AppModeToggleState,
    modifier: Modifier = Modifier
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val segmentWidth = 32.dp
    val segmentHeight = 36.dp

    Surface(
        modifier = modifier.height(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            // Overshoot is clamped so the stretched thumb squashes against the
            // track ends instead of poking outside them. The clamp is against
            // the last segment index rather than 1f, which is the only thing
            // the two-mode version needed changing to carry a third mode.
            val lastIndex = (AppMode.entries.size - 1).toFloat()
            val start = minOf(state.fastEdge.value, state.slowEdge.value).coerceAtLeast(0f)
            val end = maxOf(state.fastEdge.value, state.slowEdge.value).coerceAtMost(lastIndex)
            Box(
                modifier = Modifier
                    .offset(x = segmentWidth * start)
                    .width(segmentWidth * (1f + end - start))
                    .height(segmentHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Row {
                AppMode.entries.forEach { entry ->
                    ToggleSegment(
                        icon = entry.toggleIcon,
                        contentDescription = stringResource(entry.switchToContentDescription),
                        selected = mode == entry,
                        segmentWidth = segmentWidth,
                        segmentHeight = segmentHeight,
                        onClick = {
                            if (mode != entry) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onModeChange(entry)
                            }
                        }
                    )
                }
            }
        }
    }
}

private val AppMode.toggleIcon: ImageVector
    get() = when (this) {
        AppMode.MUSIC -> Icons.Rounded.MusicNote
        AppMode.VIDEO -> Icons.Rounded.VideoLibrary
        AppMode.TV -> Icons.Rounded.Movie
    }

private val AppMode.switchToContentDescription: Int
    get() = when (this) {
        AppMode.MUSIC -> R.string.cd_switch_to_music
        AppMode.VIDEO -> R.string.cd_switch_to_video
        AppMode.TV -> R.string.cd_switch_to_tv
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
