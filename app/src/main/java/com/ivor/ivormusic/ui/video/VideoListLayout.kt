package com.ivor.ivormusic.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.theme.currentWindowLayout

/** App-wide video list layout (cards, compact or grid), provided once at the root. */
val LocalVideoListLayout = staticCompositionLocalOf { ThemePreferences.VIDEO_LAYOUT_CARDS }

/**
 * How many video cards fit across.
 *
 * Was a hardcoded pair, which is right on a phone and wasteful on a tablet or a
 * desktop window that could hold three or four; the count is now derived from
 * the window. Read at the call site (the list builder is not composable) and
 * passed down.
 *
 * **Every layout gains columns on a wide window, not only Grid**
 * [judgement November 2026]. Cards and Compact answered 1 whatever the window,
 * so a phone on its side or a tablet drew one card the width of the screen:
 * a thumbnail taller than the window, one video per screenful. The layout the
 * listener chose still decides what a card looks like; the window decides how
 * many sit side by side. Below 600dp nothing changes.
 */
@Composable
fun rememberVideoGridColumns(): Int {
    val window = currentWindowLayout()
    val wide = window.usesNavigationRail
    // The rail takes its width off the page before the cards are laid out.
    val available = if (wide) window.width - 80.dp else window.width
    return when (LocalVideoListLayout.current) {
        // Grid on a phone has always been two across, and the user chose it
        // for that, so two is the floor. Past that a column is added per
        // ~320dp, the width below which a 16:9 card's two-line title starts
        // to clip on a wide window; the cap keeps a very wide one from
        // turning the feed into thumbnail confetti.
        ThemePreferences.VIDEO_LAYOUT_GRID ->
            window.columns(available, minCell = 320.dp, max = 5).coerceAtLeast(2)
        // A row of thumbnail beside text needs more width than a card does
        // before a second one fits beside it.
        ThemePreferences.VIDEO_LAYOUT_COMPACT ->
            if (wide) window.columns(available, minCell = 400.dp, max = 3) else 1
        // Full cards carry an avatar and metadata under the frame, so they
        // stay a little larger than Grid's cells and stop at four.
        else -> if (wide) window.columns(available, minCell = 340.dp, max = 4).coerceAtLeast(2) else 1
    }
}

/**
 * Video rows for a LazyColumn in the user's layout: [columns] cards per row,
 * which is one on a phone unless the layout is Grid. [card] receives the
 * modifier sizing it for its cell.
 */
fun LazyListScope.videoListItems(
    videos: List<VideoItem>,
    layout: String,
    keyPrefix: String = "",
    columns: Int = 2,
    card: @Composable (VideoItem, Modifier) -> Unit,
) {
    // Keys must be unique, and feeds do repeat videos.
    val unique = videos.distinctBy { it.videoId }
    if (layout == ThemePreferences.VIDEO_LAYOUT_GRID || columns > 1) {
        val perRow = columns.coerceAtLeast(1)
        items(unique.chunked(perRow), key = { row -> keyPrefix + row.first().videoId }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { card(it, Modifier.weight(1f)) }
                // A short final row keeps its cells at the width the full rows
                // use rather than stretching one card across the page.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else {
        items(unique, key = { keyPrefix + it.videoId }) { card(it, Modifier.padding(horizontal = 16.dp)) }
    }
}
