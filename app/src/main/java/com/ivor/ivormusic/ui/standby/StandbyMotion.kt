package com.ivor.ivormusic.ui.standby

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Standby's motion vocabulary, after Apple's *Designing Fluid Interfaces*
 * (WWDC 2018): a spring is described by how quickly it gets there ([response],
 * seconds) and how much it overshoots ([damping], 1 = none), not by a duration.
 *
 * Everything that can be touched in Standby moves on one of these, so any of it
 * can be grabbed mid-flight and carries the finger's velocity when let go.
 * Critically damped by default; a little bounce only after a flick, where the
 * hand gave the motion its momentum.
 */
internal fun <T> appleSpring(
    response: Float,
    damping: Float = 1f,
    visibilityThreshold: T? = null,
): SpringSpec<T> = spring(
    dampingRatio = damping,
    // A unit-mass spring's natural period is 2*pi/sqrt(k), and Apple's
    // "response" is that period.
    stiffness = (2f * PI.toFloat() / response).let { it * it },
    visibilityThreshold = visibilityThreshold,
)

/**
 * Where a release at [velocity] (px/s) would come to rest, as UIScrollView's
 * deceleration does it. Apple's own projection, not v^2/2a: a flick is
 * judged by where it is going, not by where the finger let go.
 */
internal fun projectedDistance(velocity: Float, decelerationRate: Float = 0.998f): Float =
    (velocity / 1000f) * decelerationRate / (1f - decelerationRate)

/**
 * Resistance past an edge: the further the drag goes, the less the surface
 * follows, so the edge reads as soft rather than frozen.
 */
internal fun rubberBand(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float =
    (overshoot * dimension * constant) / (dimension + constant * abs(overshoot))

/**
 * Lay the content out at [scale] of its measured size, drawn from its top-left
 * corner. Read in the layout phase, so a spring driving it re-lays the column
 * every frame (its neighbours flow with it) without recomposing anything.
 *
 * Text is measured at its largest and only ever scaled down, which keeps the
 * glyphs as sharp at the small end as at the large one.
 */
internal fun Modifier.layoutScale(scale: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val s = scale().coerceAtLeast(0f)
    layout((placeable.width * s).roundToInt(), (placeable.height * s).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = s
            scaleY = s
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

internal fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
