package com.ivor.ivormusic.ui.components

import androidx.compose.material3.FloatingToolbarState

/**
 * How far the floating navigation toolbar has scrolled out of the way, 0 (fully
 * shown) to 1 (fully hidden).
 *
 * Anything parked above that toolbar - the music pill, the video mini bar - has
 * to follow it down, or scrolling a feed leaves the mini player floating over a
 * strip of nothing where the toolbar used to be.
 *
 * Read this inside a deferred lambda (`Modifier.offset {}`, `graphicsLayer {}`),
 * never in composition: it changes on every scroll frame, and reading it during
 * composition recomposes whatever read it just as often.
 *
 * The ratio is taken rather than the raw offset so the sign convention of
 * [FloatingToolbarState.offsetLimit] does not matter, and an unmeasured
 * toolbar (limit still zero) reports nothing hidden rather than dividing by it.
 */
internal fun FloatingToolbarState.hiddenFraction(): Float {
    val limit = offsetLimit
    if (limit == 0f) return 0f
    return (offset / limit).coerceIn(0f, 1f)
}
