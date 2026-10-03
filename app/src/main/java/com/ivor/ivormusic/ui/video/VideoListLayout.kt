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

/** App-wide video list layout (cards, compact or grid), provided once at the root. */
val LocalVideoListLayout = staticCompositionLocalOf { ThemePreferences.VIDEO_LAYOUT_CARDS }

/**
 * Video rows for a LazyColumn in the user's layout: one card per row, or two
 * per row in grid mode. [card] receives the modifier sizing it for its cell.
 */
fun LazyListScope.videoListItems(
    videos: List<VideoItem>,
    layout: String,
    keyPrefix: String = "",
    card: @Composable (VideoItem, Modifier) -> Unit,
) {
    // Keys must be unique, and feeds do repeat videos.
    val unique = videos.distinctBy { it.videoId }
    if (layout == ThemePreferences.VIDEO_LAYOUT_GRID) {
        items(unique.chunked(2), key = { row -> keyPrefix + row.first().videoId }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { card(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    } else {
        items(unique, key = { keyPrefix + it.videoId }) { card(it, Modifier.padding(horizontal = 16.dp)) }
    }
}
