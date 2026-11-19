package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether a [FloatingTopBarHost]'s bar is out of the way.
 *
 * Driven by what the list underneath actually scrolled (`consumed` in
 * [NestedScrollConnection.onPostScroll]), not by the finger: a drag on a page
 * too short to move, or a pull-to-refresh at the top, moves nothing and so
 * leaves the bar where it is. Hiding waits for [HIDE_AFTER] of travel, so a
 * small wobble while reading does not flick it away; coming back takes any
 * upward scroll at all, because a bar the user reaches for has to be there.
 */
@Stable
class FloatingTopBarState {
    var hidden by mutableStateOf(false)
        internal set

    // Plain fields: read only inside the scroll callback, never drawn.
    private var downTravelPx = 0f
    internal var hideAfterPx = 0f

    internal val connection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val dy = consumed.y
            when {
                // Content moved up: the user is reading further down.
                dy < 0f -> {
                    downTravelPx -= dy
                    if (downTravelPx >= hideAfterPx) hidden = true
                }
                dy > 0f -> {
                    downTravelPx = 0f
                    hidden = false
                }
            }
            return Offset.Zero
        }
    }

    /** Bring the bar back, as at the top of the page. */
    fun show() {
        downTravelPx = 0f
        hidden = false
    }
}

/**
 * A top bar floating over a scrolling page: it slides away while the page
 * scrolls down and comes back on the first scroll up.
 *
 * The bar is drawn over [content], not inside it, so the page passes under it.
 * [content] receives the bar's measured height and must leave that much room
 * at the top of its list (a spacer item where the bar used to be), so the
 * page at rest looks exactly as it did with the bar inside the list.
 *
 * Over content - anything but the top of the page - the bar stands on the
 * page's own background colour, status bar included, so the profile picture,
 * greeting and buttons read over artwork in light and dark themes alike. At
 * the top the backdrop fades out and the bar sits on the page as before.
 *
 * The slide is a graphics-layer translation read in the draw phase, so it
 * neither recomposes nor relayouts the page; pointer input follows the layer,
 * so a hidden bar takes no taps.
 *
 * @param scrolledPastTop whether anything of the page is under the bar
 *   (typically `listState.canScrollBackward`). At the top the bar is always
 *   shown.
 * @param contentPadding the padding of the list underneath. Its top (normally
 *   the status bar, which the backdrop covers too) goes above the bar, and its
 *   sides around it, so the bar keeps the list's width: a page capped and
 *   centred on a tablet, or clear of a camera cutout in landscape.
 */
@Composable
fun FloatingTopBarHost(
    scrolledPastTop: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: FloatingTopBarState = remember { FloatingTopBarState() },
    bar: @Composable () -> Unit,
    content: @Composable BoxScope.(barHeight: Dp) -> Unit,
) {
    val density = LocalDensity.current
    state.hideAfterPx = with(density) { HIDE_AFTER.toPx() }

    // Reaching the top by any route (a fling, a tap on the tab that scrolls
    // up, a refresh) shows the bar, so it is never missing at the top.
    LaunchedEffect(scrolledPastTop) {
        if (!scrolledPastTop) state.show()
    }

    var barHeightPx by remember { mutableIntStateOf(0) }
    // Until the bar is measured, assume the usual height rather than zero, so
    // the first frame does not lay the page out under it and then jump.
    val barHeight = if (barHeightPx > 0) with(density) { barHeightPx.toDp() } else DEFAULT_BAR_HEIGHT

    val hideProgress = animateFloatAsState(
        targetValue = if (state.hidden && scrolledPastTop) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "FloatingTopBarHide"
    )
    val backdropAlpha = animateFloatAsState(
        targetValue = if (scrolledPastTop) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "FloatingTopBarBackdrop"
    )
    val backdrop = MaterialTheme.colorScheme.background
    val layoutDirection = LocalLayoutDirection.current

    Box(modifier = modifier.nestedScroll(state.connection)) {
        content(barHeight)

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = -hideProgress.value * size.height
                }
                .drawBehind {
                    val alpha = backdropAlpha.value
                    if (alpha > 0f) drawRect(backdrop.copy(alpha = alpha))
                }
                .padding(
                    top = contentPadding.calculateTopPadding(),
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection)
                )
        ) {
            Box(modifier = Modifier.onSizeChanged { barHeightPx = it.height }) {
                bar()
            }
        }
    }
}

/** Travel down the page before the bar slides away. */
private val HIDE_AFTER = 24.dp

/** The Home bars' height: a 44dp row with 16dp above and below. */
private val DEFAULT_BAR_HEIGHT = 76.dp
