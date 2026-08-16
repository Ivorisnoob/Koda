package com.ivor.ivormusic.ui.player

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.Song
import kotlinx.coroutines.delay

/**
 * Stable per-row keys for a queue.
 *
 * `LazyColumn` keys have to be unique, and a queue can legitimately hold the
 * same song twice - a playlist that lists it twice, or the same track added to
 * the queue again. The obvious fix, qualifying the key with the row's index,
 * is what every queue view here used to do and it quietly breaks reordering:
 * moving one row changes the index, and therefore the key, of every row it
 * passed, so Compose sees a screenful of items being destroyed and created
 * rather than one item moving. `animateItem` has nothing to animate and the
 * list jumps.
 *
 * Qualifying by *occurrence* instead gives a key that survives a move. The one
 * case it does not is dragging the second copy of a song above the first, which
 * swaps their occurrence numbers; the keys stay unique, so that reorders
 * without animating rather than misbehaving.
 */
fun queueRowKeys(queue: List<Song>, prefix: String): List<String> {
    val seen = HashMap<String, Int>(queue.size)
    return queue.map { song ->
        val occurrence = seen.merge(song.id, 1, Int::plus)!!
        "${prefix}_${song.id}_$occurrence"
    }
}

/**
 * Hold-to-drag reordering for a queue rendered in a `LazyColumn`.
 *
 * Written once and shared, because there are three queue views across the eight
 * player styles and only one of them had reordering at all. That one measured
 * the drag against a hardcoded 80dp row height while its rows were a different
 * height, so the list drifted out from under the finger on any long drag, and
 * it committed every crossing straight to the player and to disk.
 *
 * The geometry here is read from the list itself rather than assumed, so it is
 * correct for rows of any height, including views whose rows differ from each
 * other. Each crossing moves the item in the queue immediately - that is what
 * makes the neighbouring rows slide out of the way through `animateItem` - but
 * the session is written once, when the finger lifts.
 */
class QueueReorderState internal constructor(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    /** Edge zone and per-frame step, scaled from dp by the caller. */
    private val edgeZonePx: Float,
    private val maxScrollStepPx: Float
) {
    /** Key of the row under the finger, or null when nothing is being dragged. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    private var initialOffset by mutableIntStateOf(0)
    private var draggedDelta by mutableFloatStateOf(0f)

    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
    internal var onSettle: () -> Unit = {}

    /**
     * Row key to queue position. A map rather than the list it is built from
     * because both lookups below run for every visible row on every drag frame,
     * and a queue is routinely hundreds of songs long.
     */
    internal var keyIndex: Map<Any, Int> = emptyMap()

    val isDragging: Boolean get() = draggingKey != null

    private val draggingItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    /**
     * How far the dragged row should be drawn from where the list has settled
     * it. Recomputed from live layout, so after a move lands the row stays
     * exactly under the finger instead of snapping.
     */
    val draggingOffset: Float
        get() = draggingItem?.let { initialOffset + draggedDelta - it.offset } ?: 0f

    fun offsetFor(key: Any): Float = if (key == draggingKey) draggingOffset else 0f

    internal fun onDragStart(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        initialOffset = item.offset
        draggedDelta = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    internal fun onDrag(delta: Float) {
        if (draggingKey == null) return
        draggedDelta += delta

        val dragged = draggingItem ?: return
        val start = dragged.offset + draggingOffset
        val middle = start + dragged.size / 2f

        // Only rows that belong to the queue are drop targets; a view may also
        // have a header or a "load more" footer in the same list.
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.key != draggingKey &&
                candidate.key in keyIndex &&
                middle.toInt() in candidate.offset..(candidate.offset + candidate.size)
        } ?: return

        val from = keyIndex[dragged.key] ?: return
        val to = keyIndex[target.key] ?: return
        if (from == to) return

        onMove(from, to)
        // Keep the row under the finger: it is about to settle where the target
        // used to be, so the baseline moves by the same amount.
        initialOffset += target.offset - dragged.offset
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    internal fun onDragStop() {
        if (draggingKey == null) return
        draggingKey = null
        draggedDelta = 0f
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onSettle()
    }

    /**
     * Distance to scroll this frame so a row dragged to the edge keeps going.
     * Zero away from the edges, which is also what stops the effect below from
     * scrolling a list that is already where it should be.
     */
    internal fun autoScrollDelta(): Float {
        val dragged = draggingItem ?: return 0f
        val info = listState.layoutInfo
        val start = dragged.offset + draggingOffset
        val end = start + dragged.size
        val top = info.viewportStartOffset + edgeZonePx
        val bottom = info.viewportEndOffset - edgeZonePx
        return when {
            end > bottom -> (end - bottom).coerceAtMost(maxScrollStepPx)
            start < top -> (start - top).coerceAtLeast(-maxScrollStepPx)
            else -> 0f
        }
    }
}

/** How close to an edge a dragged row gets before the list follows it. */
private val EDGE_ZONE = 72.dp

/** Ceiling on one frame's auto-scroll, so the list never bolts. */
private val MAX_SCROLL_STEP = 12.dp

/**
 * @param keys the queue's row keys, in queue order, as returned by
 * [queueRowKeys]. Positions in this list are the indices handed to [onMove],
 * which is why the view must not pass the `LazyColumn`'s own item indices: a
 * header shifts them by one and the wrong song moves.
 * @param onMove called on every crossing, so the list can animate. Should not
 * persist anything.
 * @param onSettle called once when the finger lifts.
 */
@Composable
fun rememberQueueReorderState(
    listState: LazyListState,
    keys: List<Any>,
    onMove: (from: Int, to: Int) -> Unit,
    onSettle: () -> Unit
): QueueReorderState {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val state = remember(listState, density) {
        with(density) {
            QueueReorderState(listState, haptics, EDGE_ZONE.toPx(), MAX_SCROLL_STEP.toPx())
        }
    }

    // Read through on every recomposition rather than captured once: the queue
    // changes under this state constantly, both from the drag itself and from
    // the auto-queue topping it up mid-gesture.
    val currentMove by rememberUpdatedState(onMove)
    val currentSettle by rememberUpdatedState(onSettle)
    state.keyIndex = remember(keys) { keys.withIndex().associate { it.value to it.index } }
    state.onMove = { from, to -> currentMove(from, to) }
    state.onSettle = { currentSettle() }

    LaunchedEffect(state.isDragging) {
        if (!state.isDragging) return@LaunchedEffect
        while (true) {
            val delta = state.autoScrollDelta()
            if (delta != 0f) listState.scrollBy(delta)
            delay(AUTO_SCROLL_FRAME_MS)
        }
    }

    return state
}

private const val AUTO_SCROLL_FRAME_MS = 16L

/**
 * Attach hold-to-drag to a queue row. [key] must be the row's `LazyColumn` key.
 *
 * `pointerInput` is keyed on the row key rather than left unkeyed, so a row
 * recycled onto a different song does not keep the previous gesture in flight.
 */
fun Modifier.queueDragHandle(state: QueueReorderState, key: Any): Modifier =
    this.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key) },
            onDragEnd = { state.onDragStop() },
            onDragCancel = { state.onDragStop() },
            onDrag = { change, amount ->
                change.consume()
                state.onDrag(amount.y)
            }
        )
    }
