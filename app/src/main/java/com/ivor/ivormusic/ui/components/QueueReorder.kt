package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/**
 * A music queue opens around its current row once, then leaves scrolling under
 * the listener's control. [leadingItemCount] accounts for queue designs that
 * place a now-playing card before their track rows.
 */
@Composable
fun rememberFocusedQueueListState(
    currentQueueIndex: Int,
    leadingItemCount: Int = 0,
): LazyListState {
    val listState = rememberLazyListState()
    var hasFocusedCurrentItem by remember { mutableStateOf(false) }
    val targetIndex = if (currentQueueIndex >= 0) {
        currentQueueIndex + leadingItemCount
    } else {
        -1
    }

    LaunchedEffect(targetIndex) {
        if (hasFocusedCurrentItem || targetIndex < 0) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { totalItems -> totalItems > targetIndex }
        hasFocusedCurrentItem = true

        val initialLayout = listState.layoutInfo
        val initiallyVisible = initialLayout.visibleItemsInfo.any { it.index == targetIndex }
        if (!initiallyVisible) {
            val initialViewportCenter =
                (initialLayout.viewportStartOffset + initialLayout.viewportEndOffset) / 2
            // Put the row's leading edge near center in the main animation;
            // the measured correction below then moves only about half a row.
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = -initialViewportCenter,
            )
        }
        val layout = listState.layoutInfo
        val target = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            ?: return@LaunchedEffect
        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        val itemCenter = target.offset + target.size / 2
        listState.animateScrollBy((itemCenter - viewportCenter).toFloat())
    }

    return listState
}

/**
 * Stable per-row keys for a queue.
 *
 * `LazyColumn` keys have to be unique, and a queue can legitimately hold the
 * same track twice - a playlist that lists it twice, or the same track added to
 * the queue again. The obvious fix, qualifying the key with the row's index,
 * is what every queue view here used to do and it quietly breaks reordering:
 * moving one row changes the index, and therefore the key, of every row it
 * passed, so Compose sees a screenful of items being destroyed and created
 * rather than one item moving. `animateItem` has nothing to animate and the
 * list jumps.
 *
 * Qualifying by *occurrence* instead gives a key that survives a move. The one
 * case it does not is dragging the second copy of a track above the first, which
 * swaps their occurrence numbers; the keys stay unique, so that reorders
 * without animating rather than misbehaving.
 */
fun queueRowKeys(ids: List<String>, prefix: String): List<String> {
    val seen = HashMap<String, Int>(ids.size)
    return ids.map { id ->
        val occurrence = seen.merge(id, 1, Int::plus)!!
        "${prefix}_${id}_$occurrence"
    }
}

/**
 * Drag-to-reorder for a queue rendered in a `LazyColumn`.
 *
 * Modelled directly on the playlist reorder in `LibraryScreen`, which is the
 * one in this app that has always worked, and it is worth saying why rather
 * than leaving it to be re-derived a third time.
 *
 * **The swap is resolved once per frame, never from a pointer callback.**
 * `LazyListState.layoutInfo` only refreshes after relayout, and several pointer
 * events arrive between frames; resolving a swap per event means the second one
 * measures against a snapshot that already has the first one's move applied but
 * not its layout, so the row jumps or the drag walks away from the finger. The
 * first version of this file made exactly that mistake.
 *
 * Running the swap in the frame loop has a second benefit the playlist version
 * calls out: a finger parked at the edge of the list keeps reordering while the
 * list auto-scrolls underneath it, instead of stalling until it moves again.
 *
 * All geometry is read from the live layout, so rows of any height are handled
 * - which matters here because three queue views with three different row
 * designs share this one implementation.
 */
class QueueReorderState internal constructor(
    private val listState: LazyListState,
    private val haptics: com.ivor.ivormusic.util.KodaHaptics
) {
    /** Key of the row being dragged, or null when nothing is. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /**
     * How far the dragged row is drawn from its settled slot, in pixels.
     *
     * Held directly rather than derived from `layoutInfo` at composition time:
     * the derived form re-read layout on every recomposition and made the drag
     * position depend on when Compose happened to recompose.
     */
    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    private var dragStartIndex by mutableIntStateOf(-1)

    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
    internal var onSettle: () -> Unit = {}

    /**
     * Row key to queue position. A map rather than the list it is built from,
     * because the lookups below run for every visible row on every frame of a
     * drag and a queue is routinely hundreds of tracks long.
     */
    internal var keyIndex: Map<Any, Int> = emptyMap()

    val isDragging: Boolean get() = draggingKey != null

    fun isDragging(key: Any): Boolean = key == draggingKey

    /** Pixels to translate [key]'s row by. Zero for every row but the dragged one. */
    fun offsetFor(key: Any): Float = if (key == draggingKey) dragOffsetY else 0f

    internal fun begin(key: Any) {
        draggingKey = key
        dragOffsetY = 0f
        dragStartIndex = keyIndex[key] ?: -1
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    internal fun drag(delta: Float) {
        if (draggingKey == null) return
        dragOffsetY += delta
    }

    internal fun end() {
        if (draggingKey == null) return
        draggingKey = null
        dragOffsetY = 0f
        dragStartIndex = -1
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onSettle()
    }

    /**
     * Swap the dragged row with whichever row its centre currently covers.
     * Called once per frame from the loop in [rememberQueueReorderState].
     */
    internal fun settleSwap() {
        val key = draggingKey ?: return
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val centre = current.offset + current.size / 2f + dragOffsetY

        // Only queue rows are drop targets; a view may also carry a now-playing
        // header or a "load more" footer in the same list.
        val target = info.visibleItemsInfo.firstOrNull { candidate ->
            candidate.key != key &&
                candidate.key in keyIndex &&
                centre >= candidate.offset &&
                centre <= candidate.offset + candidate.size
        } ?: return

        val from = keyIndex[key] ?: return
        val to = keyIndex[target.key] ?: return
        if (from == to) return

        onMove(from, to)
        // The row's settled slot has just become the target's, so take that out
        // of the drag offset or the row visibly jumps under the finger.
        dragOffsetY -= (target.offset - current.offset).toFloat()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Auto-scroll step for this frame; zero away from the viewport edges. */
    internal fun autoScrollDelta(edgeZonePx: Float, maxStepPx: Float): Float {
        val key = draggingKey ?: return 0f
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        val top = current.offset + dragOffsetY
        val bottom = top + current.size
        return when {
            bottom > info.viewportEndOffset - edgeZonePx ->
                (bottom - (info.viewportEndOffset - edgeZonePx)).coerceAtMost(maxStepPx)
            top < info.viewportStartOffset + edgeZonePx ->
                (top - (info.viewportStartOffset + edgeZonePx)).coerceAtLeast(-maxStepPx)
            else -> 0f
        }
    }
}

/** How close to an edge a dragged row gets before the list follows it. */
private val EDGE_ZONE = 96.dp

/** Ceiling on one frame's auto-scroll, so the list never bolts. */
private val MAX_SCROLL_STEP = 14.dp

/**
 * @param keys the queue's row keys, in queue order, as returned by
 * [queueRowKeys]. Positions in this list are the indices handed to [onMove],
 * which is why a view must not pass the `LazyColumn`'s own item indices: a
 * header shifts them by one and the wrong track moves.
 * @param onMove called on each swap so the list can animate. Should not persist.
 * @param onSettle called once when the finger lifts.
 */
@Composable
fun rememberQueueReorderState(
    listState: LazyListState,
    keys: List<Any>,
    onMove: (from: Int, to: Int) -> Unit,
    onSettle: () -> Unit
): QueueReorderState {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val density = LocalDensity.current
    val state = remember(listState) { QueueReorderState(listState, haptics) }

    // Read through on every recomposition rather than captured once: the queue
    // changes under this state constantly, both from the drag itself and from
    // the auto-queue topping it up mid-gesture.
    val currentMove by rememberUpdatedState(onMove)
    val currentSettle by rememberUpdatedState(onSettle)
    state.keyIndex = remember(keys) { keys.withIndex().associate { it.value to it.index } }
    state.onMove = { from, to -> currentMove(from, to) }
    state.onSettle = { currentSettle() }

    // Per-frame drag loop: auto-scroll near the edges, then resolve one swap.
    // Both have to happen here rather than in the pointer callback - see the
    // class KDoc.
    LaunchedEffect(state.draggingKey) {
        if (state.draggingKey == null) return@LaunchedEffect
        val edge = with(density) { EDGE_ZONE.toPx() }
        val maxStep = with(density) { MAX_SCROLL_STEP.toPx() }
        while (isActive) {
            withFrameNanos { }
            val delta = state.autoScrollDelta(edge, maxStep)
            // Only scroll when the list can actually take it. A `scrollBy` the
            // list cannot consume is passed on through nested scroll, and the
            // video queue lives in a `ModalBottomSheet` - so dragging a row
            // against the top of an already-scrolled-to-top list would drag the
            // sheet down and close it mid-reorder.
            val canScroll = if (delta > 0f) listState.canScrollForward else listState.canScrollBackward
            if (delta != 0f && canScroll) listState.scrollBy(delta)
            state.settleSwap()
        }
    }

    return state
}

/**
 * Grab a row by its handle and drag immediately, with no long press first.
 *
 * **This is the reliable path and the one to put on a visible handle.** A queue
 * row is tappable - tapping skips to that track - so its `clickable` sits deeper
 * in the modifier chain than any long-press detector on the same row and wins
 * the gesture. The handle has no click of its own, so nothing competes for it.
 * The playlist reorder in `LibraryScreen` splits the same two ways and calls the
 * handle "the discoverable path".
 */
fun Modifier.queueDragHandle(state: QueueReorderState, key: Any): Modifier =
    this.pointerInput(key) {
        // Vertical only, so a horizontal swipe that happens to start on the
        // handle still falls through to swipe-to-remove instead of beginning a
        // reorder that goes nowhere.
        detectVerticalDragGestures(
            onDragStart = { state.begin(key) },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
            onVerticalDrag = { change, amount ->
                change.consume()
                state.drag(amount)
            }
        )
    }

/**
 * Long press anywhere on a row to drag it: the accelerator for people who know
 * it is there, never the only way in.
 *
 * Kept separate from [queueDragHandle] because it is genuinely less reliable -
 * moving before the long-press timeout hands the gesture to the list's own
 * scroll, which reads as the drag simply not working.
 */
fun Modifier.queueDragLongPress(state: QueueReorderState, key: Any): Modifier =
    this.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.begin(key) },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            }
        )
    }
