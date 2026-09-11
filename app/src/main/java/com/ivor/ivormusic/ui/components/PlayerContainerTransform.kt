package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/*
 * The mini-to-full container transform both players share.
 *
 * The music player grows its floating pill into the full screen as one rounded
 * container: the pill's own content fades out early, the full player - measured
 * once at window size - rides the container's top edge as it fades in, and the
 * container holds opaque until the content inside it is solid. The video
 * player's portrait watch page opens out of its collapsed bar the same way, so
 * the two players open and close alike. The numbers live here rather than as
 * two copies, so they cannot drift apart.
 */

/**
 * Settle for the transform, and for the release of a drag or a back gesture.
 *
 * Near-critically damped so the container never bounces against its bounds, but
 * stiff enough to arrive: at 180 this settled over roughly twice Material's own
 * slow spatial spec and read as mush rather than as calm. 0.95 leaves overshoot
 * far below a pixel.
 */
internal val PLAYER_CONTAINER_SPRING: SpringSpec<Float> =
    spring(dampingRatio = 0.95f, stiffness = 350f)

/** Progress by which the collapsed content has faded out completely. */
private const val MINI_FADE_END = 0.28f

/** Progress at which the expanded content starts to fade in. */
private const val FULL_FADE_START = 0.12f

/**
 * Progress by which the expanded content is solid: the halfway point rather
 * than the very end, so the second half of the motion is one opaque screen
 * growing instead of a washed-out one.
 */
private const val FULL_SOLID_AT = 0.5f

/** Progress after which the container hands over to the content's own background. */
private const val BACKDROP_FADE_START = 0.7f

/** Opacity of the collapsed content at [progress]: gone over the first stretch of an expansion. */
internal fun containerMiniAlpha(progress: Float): Float =
    (1f - progress / MINI_FADE_END).coerceIn(0f, 1f)

/** Opacity of the expanded content at [progress]. */
internal fun containerFullAlpha(progress: Float): Float =
    ((progress - FULL_FADE_START) / (FULL_SOLID_AT - FULL_FADE_START)).coerceIn(0f, 1f)

/**
 * Opacity of the container's own fill at [progress], for content that paints
 * its own background once open.
 *
 * Not a straight ramp: fading the container in step with the progress made the
 * growing pill half transparent exactly while the content inside it was too,
 * so mid-expansion showed Home through both. It holds opaque until the content
 * is solid and only then hands over.
 */
internal fun containerBackdropAlpha(progress: Float): Float =
    1f - ((progress - BACKDROP_FADE_START) / (1f - BACKDROP_FADE_START)).coerceIn(0f, 1f)

/**
 * The container's rectangle at one point of the transform, in pixels, in the
 * window's coordinate space.
 */
internal data class PlayerContainerFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cornerRadius: Float,
)

/**
 * Resolve the container for one frame.
 *
 * The same geometry `ExpandablePlayer` lays out through padding and height: the
 * collapsed rest is inset from the sides and the bottom of the window, and grows
 * upward and outward into it. The resting states are the endpoints - at 1 this
 * is the whole window with square corners, at 0 it is the collapsed bar to the
 * pixel - so neither needs a layout of its own to be kept in agreement.
 *
 * @param progress 1 fully expanded, 0 fully collapsed. Values outside are
 *   clamped, so a spring that overshoots cannot invert the layout.
 */
internal fun playerContainerFrame(
    progress: Float,
    windowWidth: Float,
    windowHeight: Float,
    collapsedHeight: Float,
    collapsedSideInset: Float,
    collapsedBottomInset: Float,
    collapsedCornerRadius: Float,
): PlayerContainerFrame {
    val p = progress.coerceIn(0f, 1f)
    val side = lerp(collapsedSideInset, 0f, p)
    val bottom = lerp(collapsedBottomInset, 0f, p)
    val width = (windowWidth - 2f * side).coerceAtLeast(0f)
    val height = lerp(collapsedHeight, windowHeight, p).coerceAtLeast(0f)
    // Never past half the shorter side: a larger radius is illegal for the
    // shape and draws visibly distorted corners.
    val corner = lerp(collapsedCornerRadius, 0f, p)
        .coerceAtMost(minOf(width, height) / 2f)
        .coerceAtLeast(0f)
    return PlayerContainerFrame(
        left = side,
        top = windowHeight - bottom - height,
        width = width,
        height = height,
        cornerRadius = corner,
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
