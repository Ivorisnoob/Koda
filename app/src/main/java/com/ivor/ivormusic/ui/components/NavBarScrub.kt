package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs

/**
 * Drag along a navigation bar to pick a destination, the way a thumb slides
 * across a segmented control. The highlight follows the finger ([hoveredTab])
 * and the destination only changes on release, so crossing three tabs on the
 * way to a fourth composes one screen rather than four.
 *
 * Items register their bounds with [navBarScrubItem]; the bar that hosts them
 * takes [navBarScrub]. A tap is untouched: the drag detector only claims the
 * pointer past touch slop, which is also what cancels the item's own press.
 */
class NavBarScrubState {
    private var bar: LayoutCoordinates? = null
    private val items = mutableMapOf<Int, LayoutCoordinates>()

    /** The tab ids currently drawn, so a destination that was hidden is never landed on. */
    internal var tabIds: List<Int> = emptyList()

    // Refreshed on every composition rather than used as pointerInput keys: a
    // new lambda each recomposition would restart the detector, and hovering
    // recomposes the bar, so the scrub would cancel itself on the first tick.
    internal var onHoverChange: () -> Unit = {}
    internal var onCommit: (Int) -> Unit = {}

    /** The tab under the finger while a scrub is in progress, else null. */
    var hoveredTab by mutableStateOf<Int?>(null)
        private set

    internal fun registerBar(coordinates: LayoutCoordinates) {
        bar = coordinates
    }

    internal fun registerItem(tabId: Int, coordinates: LayoutCoordinates) {
        items[tabId] = coordinates
    }

    /**
     * The item whose centre is nearest [position] along the bar's axis, in the
     * bar's coordinates. Nearest rather than contains, so the gaps between
     * pills and the bar's own end padding still resolve to something.
     */
    internal fun tabAt(position: Float, vertical: Boolean = false): Int? {
        val bar = bar?.takeIf { it.isAttached } ?: return null
        return tabIds.mapNotNull { id ->
            val item = items[id]?.takeIf { it.isAttached } ?: return@mapNotNull null
            val centre = bar.localBoundingBoxOf(item).center
            id to abs((if (vertical) centre.y else centre.x) - position)
        }.minByOrNull { it.second }?.first
    }

    internal fun hover(tabId: Int?) {
        hoveredTab = tabId
    }
}

/**
 * [onHoverChange] fires each time the finger crosses onto another item (a
 * haptic tick belongs there); [onCommit] fires once on release with the item
 * under the finger. A cancelled drag commits nothing.
 */
@Composable
fun rememberNavBarScrubState(
    tabIds: List<Int>,
    onHoverChange: () -> Unit,
    onCommit: (Int) -> Unit,
): NavBarScrubState = remember { NavBarScrubState() }.also {
    it.tabIds = tabIds
    it.onHoverChange = onHoverChange
    it.onCommit = onCommit
}

fun Modifier.navBarScrubItem(state: NavBarScrubState, tabId: Int): Modifier =
    onGloballyPositioned { state.registerItem(tabId, it) }

/**
 * [vertical] is for a navigation rail: the same scrub, along the other axis.
 */
fun Modifier.navBarScrub(state: NavBarScrubState, vertical: Boolean = false): Modifier = this
    .onGloballyPositioned { state.registerBar(it) }
    .pointerInput(state, vertical) {
        if (vertical) {
            detectVerticalDragGestures(
                onDragStart = { offset -> state.hover(state.tabAt(offset.y, vertical = true)) },
                onDragEnd = {
                    val target = state.hoveredTab
                    state.hover(null)
                    if (target != null) state.onCommit(target)
                },
                onDragCancel = { state.hover(null) },
                onVerticalDrag = { change, _ ->
                    change.consume()
                    val target = state.tabAt(change.position.y, vertical = true)
                    if (target != null && target != state.hoveredTab) {
                        state.hover(target)
                        state.onHoverChange()
                    }
                },
            )
            return@pointerInput
        }
        detectHorizontalDragGestures(
            onDragStart = { offset -> state.hover(state.tabAt(offset.x)) },
            onDragEnd = {
                val target = state.hoveredTab
                state.hover(null)
                if (target != null) state.onCommit(target)
            },
            onDragCancel = { state.hover(null) },
            onHorizontalDrag = { change, _ ->
                change.consume()
                val target = state.tabAt(change.position.x)
                if (target != null && target != state.hoveredTab) {
                    state.hover(target)
                    state.onHoverChange()
                }
            },
        )
    }
