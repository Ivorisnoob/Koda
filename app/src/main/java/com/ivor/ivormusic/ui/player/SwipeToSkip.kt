package com.ivor.ivormusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The one swipe-to-skip contract shared by every player style.
 *
 * Drag left for the next song, right for the previous one. The surface
 * under the finger follows it, springs home if the drag is abandoned, and
 * commits the skip past [SwipeToSkipDefaults.Threshold]. This is the house
 * pattern the artwork gestures were each hand-rolling; it lives here so the
 * artwork and the song title cannot drift into two different feels, which is
 * the whole point of issue #180.
 *
 * Both the artwork and the title/artist block of a style share one state, so
 * whichever one the finger lands on moves the same distance and commits at the
 * same threshold. A drag that starts on the artwork and travels across the
 * title cannot skip twice: Compose delivers the whole gesture to the node that
 * received the initial press, so the second detector never sees a down event.
 *
 * Taps are untouched. `detectHorizontalDragGestures` only claims the pointer
 * once it has passed touch slop horizontally, so a clickable artist name
 * underneath still fires on a tap, and a vertical drag still reaches the
 * player's collapse gesture.
 */
@Stable
class SwipeToSkipState internal constructor(
    private val scope: CoroutineScope,
    private val thresholdPx: Float,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit
) {
    private val dragX: Animatable<Float, AnimationVector1D> = Animatable(0f)

    /** Live horizontal drag distance in px. Read this in a draw-phase lambda. */
    val offset: Float get() = dragX.value

    internal fun drag(amount: Float) {
        scope.launch { dragX.snapTo(dragX.value + amount) }
    }

    internal fun settle() {
        val dx = dragX.value
        scope.launch {
            if (abs(dx) > thresholdPx) {
                if (dx < 0) onNext() else onPrevious()
            }
            dragX.animateTo(0f, SwipeToSkipDefaults.SpringBack)
        }
    }

    internal fun cancel() {
        scope.launch { dragX.animateTo(0f, spring()) }
    }
}

object SwipeToSkipDefaults {
    /** Distance the finger must travel before a release commits a skip. */
    val Threshold: Dp = 90.dp

    /** Underdamped spring home, matching the artwork gestures. */
    val SpringBack = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** Artwork tracks the finger at roughly half speed. */
    const val ArtFollow = 0.5f

    /**
     * Text tracks it at a third. A headline moving as far as the cover reads
     * as the layout coming apart rather than as a card being pushed.
     */
    const val TextFollow = 0.33f
}

/**
 * Remembers a [SwipeToSkipState]. Pass the style's existing skip lambdas so
 * the swipe and the transport buttons commit through the same path, haptics
 * included.
 */
@Composable
fun rememberSwipeToSkip(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    threshold: Dp = SwipeToSkipDefaults.Threshold
): SwipeToSkipState {
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    // The lambdas are captured once inside the state, so they have to be read
    // live or a state that outlives a recomposition would skip on a stale
    // ViewModel reference.
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    return remember(scope, thresholdPx) {
        SwipeToSkipState(
            scope = scope,
            thresholdPx = thresholdPx,
            onNext = { currentOnNext() },
            onPrevious = { currentOnPrevious() }
        )
    }
}

/**
 * Attaches the gesture. [enabled] is how a style switches it off while lyrics
 * or another overlay own the same area.
 */
fun Modifier.swipeToSkip(state: SwipeToSkipState, enabled: Boolean = true): Modifier =
    this.pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                state.drag(dragAmount)
            },
            onDragEnd = { state.settle() },
            onDragCancel = { state.cancel() }
        )
    }

/**
 * Makes the content follow the finger. Separate from [swipeToSkip] because the
 * gesture is usually attached to a container and the movement applied to the
 * thing inside it that should visibly slide.
 */
fun Modifier.swipeToSkipFollow(
    state: SwipeToSkipState,
    factor: Float = SwipeToSkipDefaults.TextFollow
): Modifier = this.graphicsLayer { translationX = state.offset * factor }
