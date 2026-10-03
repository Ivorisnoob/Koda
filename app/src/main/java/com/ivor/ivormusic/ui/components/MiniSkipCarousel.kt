package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.util.KodaHaptics
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * The pure rules of a mini bar's sideways swipe, free of Compose so the JVM
 * suite can hold them.
 *
 * Offsets are the content's: negative is content moving left, which brings the
 * *next* item in from the right; positive brings the *previous* one in from
 * the left, the way a carousel reads.
 */
internal object MiniSkip {

    enum class Direction { NEXT, PREVIOUS }

    /**
     * Where the content draws for a finger [raw] px from where it went down.
     * Toward a neighbour it follows 1:1 up to one full width; toward an edge
     * with nothing there it gives a little and stiffens, so the bar answers
     * the swipe without pretending something is coming.
     */
    fun displayed(raw: Float, width: Float, hasNext: Boolean, hasPrevious: Boolean, resistPx: Float): Float {
        val allowed = if (raw < 0f) hasNext else hasPrevious
        if (allowed) return raw.coerceIn(-width.coerceAtLeast(0f), width.coerceAtLeast(0f))
        val magnitude = abs(raw)
        // Asymptotic toward resistPx: half of it at one resistPx of travel.
        return sign(raw) * resistPx * magnitude / (magnitude + resistPx)
    }

    /**
     * What a release commits to. Distance or a fling in the direction of the
     * drag, and only toward a neighbour that exists.
     */
    fun decide(
        raw: Float,
        velocity: Float,
        thresholdPx: Float,
        flingVelocityPx: Float,
        hasNext: Boolean,
        hasPrevious: Boolean,
    ): Direction? {
        if (raw == 0f) return null
        val far = abs(raw) > thresholdPx
        val flung = abs(velocity) > flingVelocityPx && sign(velocity) == sign(raw)
        if (!far && !flung) return null
        return if (raw < 0f) Direction.NEXT.takeIf { hasNext } else Direction.PREVIOUS.takeIf { hasPrevious }
    }
}

/**
 * One mini bar's sideways swipe: the bar stays put and the item inside it
 * moves, with the neighbour it would move to peeking in from the edge.
 *
 * A committed swipe slides the neighbour all the way in and then asks for the
 * skip. It holds there until the bar is drawing *that* item - its content key
 * equals the key the neighbour had - and only then snaps back to rest, where
 * the new current item sits exactly where the neighbour was, so the hand-off
 * has no visible seam. While it holds, the carousel keeps drawing the
 * neighbour it was given at the moment of the commit: the moment the skip
 * lands, "next" is recomputed as the item *after* the new one, and drawing
 * that in the middle of the bar showed a third song for a frame or two. Any
 * other change of content (autoplay moving on) also ends the hold, and if
 * nothing changes at all (the skip was refused) it springs back after
 * [COMMIT_TIMEOUT_MS] rather than leaving the wrong item on show.
 */
@Stable
class MiniSkipState internal constructor(
    private val scope: CoroutineScope,
    private val haptics: KodaHaptics,
    private val thresholdPx: Float,
    private val flingVelocityPx: Float,
    private val resistPx: Float,
) {
    internal val offset = Animatable(0f)
    internal var widthPx by mutableFloatStateOf(0f)

    /** Keys of the items either side, null where there is none. */
    internal var nextKey: Any? = null
    internal var previousKey: Any? = null
    private val hasNext get() = nextKey != null
    private val hasPrevious get() = previousKey != null
    /** The item on show, kept current from composition. */
    internal var contentKey: Any? = null
    internal var onNext: () -> Unit = {}
    internal var onPrevious: () -> Unit = {}

    /** A commit is in flight; the carousel freezes its neighbours while true. */
    internal var committing by mutableStateOf(false)
        private set

    private var raw = 0f
    private var crossed = false
    private var startKey: Any? = null
    private var expectedKey: Any? = null
    private val awaiting get() = committing

    internal fun start() {
        raw = offset.value
        crossed = false
        scope.launch { offset.stop() }
    }

    internal fun drag(amount: Float) {
        if (awaiting) return
        raw += amount
        val shown = MiniSkip.displayed(raw, widthPx, hasNext, hasPrevious, resistPx)
        val canCommit = if (raw < 0f) hasNext else hasPrevious
        val nowCrossed = canCommit && abs(raw) > thresholdPx
        if (nowCrossed && !crossed) haptics.threshold()
        crossed = nowCrossed
        scope.launch { offset.snapTo(shown) }
    }

    internal fun release(velocity: Float) {
        if (awaiting) return
        val direction = MiniSkip.decide(raw, velocity, thresholdPx, flingVelocityPx, hasNext, hasPrevious)
        val attemptedEdge = direction == null && abs(raw) > thresholdPx
        raw = 0f
        if (direction == null) {
            if (attemptedEdge) haptics.reject()
            scope.launch { offset.animateTo(0f, SPRING_HOME) }
            return
        }
        haptics.confirm()
        committing = true
        startKey = contentKey
        expectedKey = if (direction == MiniSkip.Direction.NEXT) nextKey else previousKey
        val target = if (direction == MiniSkip.Direction.NEXT) -widthPx else widthPx
        scope.launch {
            offset.animateTo(target, SLIDE_IN)
            if (direction == MiniSkip.Direction.NEXT) onNext() else onPrevious()
            delay(COMMIT_TIMEOUT_MS)
            // Still waiting: the skip did not land. Put the real item back.
            if (committing) {
                committing = false
                offset.animateTo(0f, SPRING_HOME)
            }
        }
    }

    internal fun contentChanged(key: Any?) {
        if (!committing) return
        // Landed on the item that slid in, or moved on to something else
        // entirely: either way the bar is now drawing its own content.
        if (key != expectedKey && key == startKey) return
        scope.launch {
            offset.snapTo(0f)
            committing = false
        }
    }

    private companion object {
        val SPRING_HOME = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
        val SLIDE_IN = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
        const val COMMIT_TIMEOUT_MS = 2_500L
    }
}

/**
 * Remembers an unbound [MiniSkipState]. Where the gesture and the content that
 * knows the neighbours are composed in different places - the video bar's
 * overlay owns the drag, its content collects the queue - create the state
 * with this and bind it with [BindMiniSkip] beside the content, so the
 * overlay never recomposes on a related list arriving.
 */
@Composable
fun rememberMiniSkipState(): MiniSkipState {
    val scope = rememberCoroutineScope()
    val haptics = rememberKodaHaptics()
    val density = LocalDensity.current
    return remember(scope, haptics, density) {
        with(density) {
            MiniSkipState(
                scope = scope,
                haptics = haptics,
                thresholdPx = MINI_SKIP_THRESHOLD.toPx(),
                flingVelocityPx = MINI_SKIP_FLING.toPx(),
                resistPx = MINI_SKIP_RESIST.toPx(),
            )
        }
    }
}

/**
 * Remembers a [MiniSkipState] and binds it in one go, for a host that knows
 * both the gesture and the neighbours. See [BindMiniSkip] for the arguments.
 */
@Composable
fun rememberMiniSkipState(
    contentKey: Any?,
    nextKey: Any?,
    previousKey: Any?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): MiniSkipState {
    val state = rememberMiniSkipState()
    BindMiniSkip(state, contentKey, nextKey, previousKey, onNext, onPrevious)
    return state
}

/**
 * Tells [state] what is on show and what is either side of it, as keys of
 * the same kind: [contentKey] for the item drawn now, [nextKey] and
 * [previousKey] for the neighbours (null where there is none). A commit holds
 * until [contentKey] equals the key the neighbour had, so a key must change
 * only when what the bar *draws* changes - build it from the drawn item, not
 * from an id that can move ahead of it. The lambdas are read live, so a state
 * that outlives a recomposition never skips on a stale queue.
 */
@Composable
fun BindMiniSkip(
    state: MiniSkipState,
    contentKey: Any?,
    nextKey: Any?,
    previousKey: Any?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    SideEffect {
        state.contentKey = contentKey
        state.nextKey = nextKey
        state.previousKey = previousKey
        state.onNext = { currentOnNext() }
        state.onPrevious = { currentOnPrevious() }
    }
    LaunchedEffect(state, contentKey) { state.contentChanged(contentKey) }
}

/**
 * The sideways drag, attached to the whole bar so a swipe that starts over a
 * button still moves the item. Buttons keep their taps: the detector claims
 * the pointer only past horizontal touch slop, and a vertical drag goes on to
 * the bar's expand and dismiss detector.
 */
fun Modifier.miniSkipGesture(state: MiniSkipState, enabled: Boolean = true): Modifier =
    this.pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        val tracker = VelocityTracker()
        var travel = 0f
        detectHorizontalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                travel = 0f
                state.start()
            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                travel += amount
                tracker.addPosition(change.uptimeMillis, Offset(travel, 0f))
                state.drag(amount)
            },
            onDragEnd = { state.release(tracker.calculateVelocity().x) },
            onDragCancel = { state.release(0f) },
        )
    }

/**
 * The window the item slides in. [current] is the item on show; [previous]
 * and [next] are drawn one width to either side and appear only while a drag
 * leans toward them. Clipped, so the item slides under the bar's own edges and
 * the controls beside this window never move.
 *
 * The offset is read in the layers, so a drag redraws these three boxes and
 * recomposes nothing.
 */
@Composable
fun MiniSkipCarousel(
    state: MiniSkipState,
    modifier: Modifier = Modifier,
    previousLabel: String? = null,
    nextLabel: String? = null,
    previous: (@Composable () -> Unit)? = null,
    next: (@Composable () -> Unit)? = null,
    current: @Composable () -> Unit,
) {
    // While a commit is in flight, keep drawing the neighbours as they were
    // when it began: the one that slid into the middle must stay that item
    // until the bar's own content has become it (see MiniSkipState).
    val committing = state.committing
    val frozenNext = remember(committing) { next }
    val frozenPrevious = remember(committing) { previous }
    val drawnNext = if (committing) frozenNext else next
    val drawnPrevious = if (committing) frozenPrevious else previous

    // A swipe has no accessibility equivalent of its own, so the same two
    // moves are offered as actions on the item.
    val actions = buildList {
        if (previous != null && previousLabel != null) {
            add(CustomAccessibilityAction(previousLabel) { state.onPrevious(); true })
        }
        if (next != null && nextLabel != null) {
            add(CustomAccessibilityAction(nextLabel) { state.onNext(); true })
        }
    }
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { state.widthPx = it.width.toFloat() }
            .semantics { if (actions.isNotEmpty()) customActions = actions }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = state.offset.value }) {
            current()
        }
        if (drawnNext != null) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val offset = state.offset.value
                    translationX = offset + state.widthPx
                    alpha = if (offset < 0f) min(1f, abs(offset) / (state.widthPx * 0.35f).coerceAtLeast(1f)) else 0f
                }
            ) { drawnNext() }
        }
        if (drawnPrevious != null) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val offset = state.offset.value
                    translationX = offset - state.widthPx
                    alpha = if (offset > 0f) min(1f, offset / (state.widthPx * 0.35f).coerceAtLeast(1f)) else 0f
                }
            ) { drawnPrevious() }
        }
    }
}

/**
 * Commit distance. Shorter than the full players' 90dp: the window is a strip
 * of a bar, and a skip that needs half the screen of travel reads as a dismiss.
 */
private val MINI_SKIP_THRESHOLD = 72.dp
private val MINI_SKIP_FLING = 700.dp
private val MINI_SKIP_RESIST = 28.dp
