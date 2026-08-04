package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.ivor.ivormusic.ui.theme.contrastInk
import kotlin.math.roundToInt

/** Which level a vertical drag on the video surface is adjusting. */
internal enum class LevelAdjustment { Brightness, Volume }

/**
 * Overall height of the slat stack, gaps included.
 *
 * Sized against the shortest landscape phone rather than the roomiest: the pill
 * is vertically centred and the whole stack (readout, ladder, icon) has to clear
 * a ~320dp-tall window without touching the edges.
 */
private val LADDER_LENGTH = 138.dp

/** Space between slats. Small on purpose - the slats should read as a stack. */
private val LADDER_GAP = 3.dp

/**
 * Slat widths at the bottom and top of the ladder.
 *
 * The taper is what makes the level legible from the corner of the eye: a lit
 * stack is not just taller at 90% than at 30%, it is visibly *wider*, so the
 * silhouette carries the value on its own without anyone reading the number.
 */
private val SLAT_MIN_WIDTH = 13.dp
private val SLAT_MAX_WIDTH = 30.dp

/** How visible an unlit slat is. Present enough to show the range, quiet enough to ignore. */
private const val SLAT_UNLIT_ALPHA = 0.20f

/**
 * Feedback for a brightness or volume drag on the video surface: a segmented
 * ladder of slats that light from the bottom, a live percentage, and the lane's
 * icon.
 *
 * The point of segmenting it is that volume genuinely *is* stepped - a
 * continuous bar quietly lies about a control the system exposes in about
 * fifteen notches. [segments] comes from the device's own media volume steps,
 * so a haptic tick and a slat lighting are the same event rather than two
 * things that happen near each other. Brightness has no steps of its own and
 * borrows the same count, which is what keeps the two lanes feeling like one
 * control.
 *
 * Three things carry the value, deliberately redundantly:
 * - **how many** slats are lit, which is the precise reading;
 * - **how wide** the lit stack is, because the slats taper from narrow at the
 *   bottom to wide at the top and unlit ones all sit at the narrow width - so
 *   the wedge is something the level reveals rather than a shape that is always
 *   there. This is the glanceable reading;
 * - **the frontier slat**, which fills fractionally rather than snapping, so
 *   fine adjustment inside a step is still visible.
 *
 * Each slat springs on its own, so sweeping the finger fast lights them in a
 * cascade rather than all at once - the stagger falls out of the drag itself
 * and costs no extra machinery.
 *
 * Kept as two instances - one per side, mirroring [SeekFeedbackBadge] - so each
 * can animate out on its own edge instead of one pill sliding across the video
 * when the user switches lanes.
 */
@Composable
internal fun PlayerLevelIndicator(
    kind: LevelAdjustment,
    level: Float,
    segments: Int,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    // This overlay always sits on a dark scrim over the video frame, whatever
    // the app's light/dark mode is, so `primary` alone will not do: in a light
    // scheme it is a tone-40 that disappears against black. `inversePrimary` is
    // the tone-80 of the same seed, so the accent reads identically either way
    // and still comes from the user's palette.
    val accent = if (scheme.surface.luminance() < 0.5f) scheme.primary else scheme.inversePrimary
    val ink = contrastInk(scheme.scrim)

    val clamped = level.coerceIn(0f, 1f)
    val percent = (clamped * 100).roundToInt()

    val slatCount = segments.coerceAtLeast(2)
    val slatHeight = (LADDER_LENGTH - LADDER_GAP * (slatCount - 1)) / slatCount

    val label = when (kind) {
        LevelAdjustment.Brightness -> "Brightness"
        LevelAdjustment.Volume -> "Volume"
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) + scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialScale = 0.85f,
            // Grows out of the edge it belongs to rather than appearing from
            // nowhere in the middle of the frame.
            transformOrigin = TransformOrigin(
                pivotFractionX = if (kind == LevelAdjustment.Brightness) 0f else 1f,
                pivotFractionY = 0.5f
            )
        ),
        // Leaving is not a moment: it happens while the user has already moved
        // on, so it goes out on a short tween rather than a spring that would
        // still be settling.
        exit = fadeOut(tween(durationMillis = 180)) +
            scaleOut(animationSpec = tween(durationMillis = 180), targetScale = 0.92f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .background(
                    color = scheme.scrim.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$label $percent percent"
                }
        ) {
            Text(
                text = "$percent%",
                color = ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LADDER_GAP)
            ) {
                // Drawn top down, so index 0 - the first slat to light - is the
                // one at the bottom of the stack.
                for (index in slatCount - 1 downTo 0) {
                    LevelSlat(
                        fill = (clamped * slatCount - index).coerceIn(0f, 1f),
                        width = lerp(
                            start = SLAT_MIN_WIDTH,
                            stop = SLAT_MAX_WIDTH,
                            fraction = index / (slatCount - 1f)
                        ),
                        height = slatHeight,
                        color = accent
                    )
                }
            }

            AnimatedContent(
                targetState = levelIcon(kind, clamped),
                transitionSpec = {
                    (fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.7f)) togetherWith
                        (fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.7f))
                },
                label = "levelIcon"
            ) { vector ->
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * One slat of the ladder, lit by [fill] between 0 and 1.
 *
 * [width] is the slat's width once lit; unlit it renders at [SLAT_MIN_WIDTH]
 * whatever its place in the taper. That narrowing is done with a draw-time
 * scale rather than an animated width on purpose - the spring here is bouncy so
 * a newly lit slat overshoots its width slightly and snaps back, and a bouncy
 * spring driving a real layout size is exactly the thing that dips below zero
 * for one frame and throws.
 */
@Composable
private fun LevelSlat(
    fill: Float,
    width: Dp,
    height: Dp,
    color: Color
) {
    val lit by animateFloatAsState(
        targetValue = fill,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "levelSlat"
    )

    val narrow = (SLAT_MIN_WIDTH / width).coerceAtMost(1f)

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            // Read inside the layer block so lighting a slat costs a draw, not
            // a recomposition - there are up to sixteen of these on screen.
            .graphicsLayer {
                scaleX = narrow + (1f - narrow) * lit
                alpha = (SLAT_UNLIT_ALPHA + (1f - SLAT_UNLIT_ALPHA) * lit).coerceIn(0f, 1f)
            }
            .background(color = color, shape = CircleShape)
    )
}

/**
 * Three stages per level so the icon keeps up with the drag instead of sitting
 * on one glyph for most of the range.
 */
private fun levelIcon(kind: LevelAdjustment, level: Float): ImageVector = when (kind) {
    LevelAdjustment.Volume -> when {
        level <= 0.001f -> Icons.AutoMirrored.Rounded.VolumeOff
        level < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }
    LevelAdjustment.Brightness -> when {
        level < 0.33f -> Icons.Rounded.BrightnessLow
        level < 0.66f -> Icons.Rounded.BrightnessMedium
        else -> Icons.Rounded.BrightnessHigh
    }
}
