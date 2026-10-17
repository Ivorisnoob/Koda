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
 * How many video cards fit across, in grid layout.
 *
 * Was a hardcoded pair, which is right on a phone and wasteful on a tablet or a
 * desktop window that could hold three or four; the count is now derived from
 * the window. Read at the call site (the list builder is not composable) and
 * passed down. Non-grid layouts are single-column and answer 1.
 */
@Composable
fun rememberVideoGridColumns(): Int {
    if (LocalVideoListLayout.current != ThemePreferences.VIDEO_LAYOUT_GRID) return 1
    // ~320dp is the width below which a 16:9 card's two-line title starts to
    // clip; the cap keeps a very wide window from turning the feed into
    // thumbnail confetti.
    return currentWindowLayout().columns(minCell = 320.dp, max = 5)
}

/**
 * Video rows for a LazyColumn in the user's layout: one card per row, or
 * [columns] per row in grid mode. [card] receives the modifier sizing it for
 * its cell.
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
    if (layout == ThemePreferences.VIDEO_LAYOUT_GRID) {
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
