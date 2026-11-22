package com.ivor.ivormusic.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A setting picked from the Settings search, waiting to be shown on its page.
 *
 * Search results open a page (or a picker) through their own action, and the
 * pages are hand-written lists of rows that do not know their search ids, so
 * the target is named by the row's title - the index uses the same string
 * resources the rows do. A row whose title matches publishes where it is; the
 * page's [SettingsDetailScaffold] scrolls it to a third of the way down (paging
 * down to it first when it sits in an item that has not been composed yet), and
 * the row then pulses twice in the accent's container colour.
 *
 * A pick that opens a dialog rather than a page, or names a page rather than a
 * row, simply finds nothing and is dropped once the page has been searched.
 */
internal class SettingsJumpState {
    /** Normalised title of the row being looked for; null when nothing is pending. */
    var target by mutableStateOf<String?>(null)
        private set

    /** Bumped on every request, so picking the same result twice jumps twice. */
    var token by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    /** Set once the page has scrolled the row into place and it should pulse. */
    var pulseRequested by mutableStateOf(false)
        private set

    internal var targetCoordinates: LayoutCoordinates? = null

    fun request(title: String) {
        targetCoordinates = null
        pulseRequested = false
        target = normalise(title)
        token++
    }

    fun matches(title: String): Boolean = target != null && target == normalise(title)

    fun pulse() {
        pulseRequested = true
    }

    fun clear() {
        pulseRequested = false
        target = null
        targetCoordinates = null
    }

    private fun normalise(title: String) = title.trim().lowercase()
}

internal val LocalSettingsJump = staticCompositionLocalOf<SettingsJumpState?> { null }

/**
 * Applied by the shared rows, inside their clip: publishes the row's position
 * while it is the search target, and draws the pulse when the page asks for it.
 */
@Composable
internal fun Modifier.settingsJumpTarget(title: String): Modifier {
    val jump = LocalSettingsJump.current ?: return this
    val isTarget = jump.matches(title)
    val pulse = remember { Animatable(0f) }
    if (isTarget && jump.pulseRequested) {
        LaunchedEffect(jump.token) {
            try {
                repeat(2) {
                    pulse.animateTo(1f, tween(durationMillis = 260))
                    pulse.animateTo(0f, tween(durationMillis = 520))
                }
                if (jump.matches(title)) jump.clear()
            } finally {
                // Scrolled away or left mid-pulse: never leave the row tinted.
                withContext(NonCancellable) { pulse.snapTo(0f) }
            }
        }
    }
    val fill = lerp(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
        MaterialTheme.colorScheme.primaryContainer,
        pulse.value
    )
    return this
        .then(if (isTarget) Modifier.onGloballyPositioned { jump.targetCoordinates = it } else Modifier)
        .then(if (pulse.value > 0f) Modifier.background(fill) else Modifier)
}

/**
 * The page's half of a jump: wait for the page to finish arriving, page down
 * until the target row has been composed, settle it a third of the way down the
 * list and ask it to pulse. Every step yields to a finger on the list, since a
 * scroll the user starts cancels the animated one.
 */
@Composable
internal fun SettingsJumpEffect(
    listState: LazyListState,
    listCoordinates: () -> LayoutCoordinates?
) {
    val jump = LocalSettingsJump.current ?: return
    LaunchedEffect(jump.token) {
        if (jump.target == null) return@LaunchedEffect
        // Let the page slide in (and an outgoing page leave, cancelling its own
        // copy of this effect) before moving anything.
        delay(350)
        if (jump.target == null) return@LaunchedEffect
        var steps = 0
        while (jump.targetCoordinates?.isAttached != true && steps < 24) {
            if (!listState.canScrollForward) break
            val viewport = listState.layoutInfo.viewportSize.height
            if (viewport <= 0) break
            listState.animateScrollBy(viewport * 0.8f, tween(durationMillis = 240))
            withFrameNanos { }
            steps++
        }
        val row = jump.targetCoordinates
        val list = listCoordinates()
        if (row == null || !row.isAttached || list == null || !list.isAttached) {
            jump.clear()
            return@LaunchedEffect
        }
        val rowTop = list.localPositionOf(row, Offset.Zero).y
        val desiredTop = list.size.height / 3f - row.size.height / 2f
        val delta = rowTop - desiredTop.coerceAtLeast(0f)
        if (kotlin.math.abs(delta) > 1f) {
            listState.animateScrollBy(
                delta,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
        jump.pulse()
    }
}
