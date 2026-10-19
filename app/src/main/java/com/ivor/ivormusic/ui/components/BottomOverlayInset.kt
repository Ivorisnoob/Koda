package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clearance, measured from the top of the navigation bar inset, that
 * bottom-anchored UI (FABs, split buttons) needs to stay clear of the
 * floating overlays HomeScreen stacks above every tab: the pill nav bar,
 * plus the music and/or video mini players when something is loaded.
 *
 * Provided (animated) by HomeScreen so deep screens like the playlist detail
 * page don't need the player state threaded through every call site. Screens
 * hosted outside HomeScreen fall back to 0.
 */
val LocalBottomOverlayInset = compositionLocalOf { 0.dp }

/**
 * Where a floating bar should rest while those overlays move: the navigation
 * pill leaves on scroll and the music pill closes into a bubble in the corner,
 * and a bar still holding [LocalBottomOverlayInset] is left standing over the
 * gap they vacated.
 *
 * Both values change on every scroll frame, so they are lambdas to be read in
 * layout or placement, never in composition.
 */
@Stable
class BottomOverlayMotion(
    /** Clearance above the navigation bar inset right now, in pixels. */
    val bottomPx: () -> Float,
    /** Room to leave at the end edge for the music bubble, in pixels. */
    val endInsetPx: () -> Float
)

/** Null outside HomeScreen, where nothing floats above the content. */
val LocalBottomOverlayMotion = compositionLocalOf<BottomOverlayMotion?> { null }

/**
 * Stands a full-width floating bar on whatever is beneath it right now: the
 * overlays as they move ([LocalBottomOverlayMotion]), narrowed to sit beside
 * the music bubble, or the keyboard when that is taller. For a bar aligned to
 * the bottom of a box that reaches the screen edge.
 *
 * A docked bar holding [LocalBottomOverlayInset] as padding is what this
 * replaces: the navigation pill and the mini player leave on scroll, and the
 * padding stayed, as a tall empty panel under the button.
 *
 * Read in layout, so following a scroll moves the bar without recomposing it.
 */
@Composable
fun Modifier.floatAboveBottomOverlays(): Modifier {
    val motion = LocalBottomOverlayMotion.current
    val restingInset = LocalBottomOverlayInset.current
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    return layout { measurable, constraints ->
        val keyboard = imeInsets.getBottom(this)
        val overlays = navInsets.getBottom(this) +
            (motion?.bottomPx?.invoke() ?: restingInset.toPx()).roundToInt()
        val overKeyboard = keyboard > overlays
        val bottom = if (overKeyboard) keyboard else overlays
        // The bubble is behind the keyboard, so there is nothing to stand beside.
        val end = if (overKeyboard) 0 else (motion?.endInsetPx?.invoke() ?: 0f).roundToInt()
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = (constraints.maxWidth - end).coerceAtLeast(0)
            )
        )
        layout(constraints.maxWidth, placeable.height + bottom) {
            placeable.placeRelative(0, 0)
        }
    }
}
