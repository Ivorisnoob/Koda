package com.ivor.ivormusic.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A parent screen with a child on top of it, where back previews the child
 * leaving rather than performing it at the end of the gesture.
 *
 * **The reason this needs a component at all is what predictive back requires
 * of the layout.** A preview has to show the destination, so the destination
 * has to be composed while the child is open. Every stack in this app was
 * written as one `AnimatedContent` over a route enum, which composes exactly
 * one state at a time, so there was nothing behind the child to reveal. The
 * fix is the same every time: lift the parent out and layer the child over it,
 * which is what [background] and [foreground] are.
 *
 * Lifting it has a second effect worth having on its own. The parent stops
 * being torn down every time a child opens, so its scroll position survives.
 *
 * [foreground] is handed whether the child is leaving because of a completed
 * gesture. When it is, its own exit animation must be suppressed: the finger
 * already performed that move, and running it again snaps the child back to
 * full size to replay it.
 */
@Composable
fun PredictiveBackStack(
    childOpen: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether back is handled here at all. Widen it when the screen owns steps beyond closing the child. */
    enabled: Boolean = childOpen,
    /**
     * Whether the *next* back actually closes the child. Steps that change the
     * parent in place (clearing a filter, leaving a mode) must not be
     * previewed: nothing leaves, so a peel would animate a departure that is
     * not happening, and the child would be left peeled with nothing to
     * restore it.
     */
    previewable: Boolean = childOpen,
    background: @Composable () -> Unit,
    foreground: @Composable (committedByGesture: Boolean) -> Unit
) {
    // One continuous value across both halves of the gesture. 0..1 is the
    // drag; 1..2 carries the child the rest of the way off after release, so
    // letting go continues the movement instead of restarting it.
    val peel = remember { Animatable(0f) }
    var isPeeling by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    // +1 when the finger came from the left edge, so the child leaves the way
    // the hand is already moving. Physical, so it needs no RTL adjustment.
    var peelSign by remember { mutableFloatStateOf(1f) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    PredictiveBackHandler(enabled = enabled) { events ->
        var dragged = false
        try {
            events.collect { event ->
                if (!previewable) return@collect
                dragged = true
                isPeeling = true
                peelSign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                peel.snapTo(event.progress.coerceIn(0f, 1f))
            }
            if (dragged) {
                committed = true
                peel.animateTo(
                    targetValue = 2f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            onBack()
        } catch (cancelled: CancellationException) {
            // The case the feature lives or dies on. The spring has to run in
            // a scope that outlives the gesture: this coroutine is the one
            // being cancelled, and an animation started here never moves,
            // leaving the child stranded mid-peel.
            //
            // Cleared here as well as after the child closes, because a second
            // gesture can start while the first is still settling and a commit
            // flag left set would make the next ordinary back skip its
            // animation entirely.
            committed = false
            if (dragged) {
                scope.launch {
                    peel.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    isPeeling = false
                }
            } else {
                isPeeling = false
            }
        }
    }

    // Flags down before the value: snapTo suspends, so clearing isPeeling
    // after it leaves a frame where the peel reads 0 while the parent is still
    // wearing its peel layer, and the parent jumps for exactly one frame.
    LaunchedEffect(childOpen) {
        if (!childOpen && committed) {
            committed = false
            isPeeling = false
            peel.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width.toFloat() }
    ) {
        val dragFraction = peel.value.coerceIn(0f, 1f)
        val leavingFraction = (peel.value - 1f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPeeling) {
                        Modifier.graphicsLayer {
                            // Comes forward as the child leaves, so the two
                            // read as one stack rather than two slides.
                            val scale = lerp(0.94f, 1f, dragFraction)
                            scaleX = scale
                            scaleY = scale
                            translationX =
                                -peelSign * containerWidth * 0.08f * (1f - dragFraction)
                        }
                    } else {
                        Modifier
                    }
                )
                .coveredBy(childOpen)
        ) {
            background()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPeeling) {
                        Modifier.graphicsLayer {
                            val scale = lerp(1f, 0.90f, dragFraction)
                            scaleX = scale
                            scaleY = scale
                            translationX = peelSign * containerWidth *
                                (0.10f * dragFraction + 0.95f * leavingFraction)
                            alpha = 1f - leavingFraction
                            // Driven by a value the system clamps to 0..1, so
                            // it cannot undershoot into a negative corner.
                            shape = RoundedCornerShape(lerp(0f, 28f, dragFraction).dp)
                            clip = true
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            foreground(committed)
        }
    }
}

/**
 * Makes a layer inert while something opaque sits on top of it.
 *
 * A parent stays composed behind an open child so predictive back has
 * something to reveal, and a composed layer is a live one: its rows still take
 * taps that fall through gaps in the child above, and TalkBack still reads
 * them out as if they were on screen. Both are invisible in normal use and
 * both are wrong.
 */
internal fun Modifier.coveredBy(covered: Boolean): Modifier =
    if (!covered) {
        this
    } else {
        this
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes
                            .forEach { it.consume() }
                    }
                }
            }
    }
