package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Loading placeholders that stand in for content at its real size and shape.
 *
 * The point is that a screen's chrome - top bar, section titles, navigation -
 * has nothing to wait for and should never be replaced by a spinner. Only the
 * regions actually blocked on data get covered, and they keep their layout, so
 * nothing jumps when the data lands.
 *
 * A slow alpha pulse rather than a travelling gradient: it reads as "not ready"
 * without competing with the staggered entrance animations the real content
 * plays on arrival. Time-driven, so tween rather than spring.
 */
private const val SKELETON_PULSE_MS = 900

/**
 * The shared pulse. Hoist this once per screen and pass it down so every
 * placeholder breathes in step - separate transitions drift apart and the
 * screen starts to twinkle.
 */
@Composable
fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(SKELETON_PULSE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    return alpha
}

/**
 * A single placeholder block. Size it with [modifier] to match whatever it is
 * standing in for; [shape] should match too, so the swap to real content is a
 * fill rather than a reflow.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    alpha: Float = rememberSkeletonAlpha()
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
    )
}

/** Placeholder for a line of text, sized like the type it replaces. */
@Composable
fun SkeletonTextLine(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = rememberSkeletonAlpha()
) {
    SkeletonBox(
        modifier = modifier.size(width = width, height = height),
        shape = RoundedCornerShape(height / 2),
        alpha = alpha
    )
}
