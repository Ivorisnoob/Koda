package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.Song

/**
 * The one segmented list row.
 *
 * Every list in the app used to draw its own row, and the eight that existed
 * had drifted onto six different container radii for the same job - flat 0dp in
 * the library and playlist pages, 8dp on Home, 14dp in Spotlight and the video
 * library, 20dp in search, 28dp on the album page. This is the single answer,
 * and it is Material 3 Expressive's own: [SegmentedListItem] gives each row a
 * tonal container, [ListItemDefaults.segmentedShapes] rounds the ends of the
 * group and tightens the corners between, and the component morphs its shape on
 * press without the caller wiring anything.
 *
 * [index] and [count] are the row's position in its own visual group, not in the
 * data set. A list with a header above it still passes 0 for its first row.
 *
 * The gap between rows is applied here rather than by the caller's
 * [androidx.compose.foundation.layout.Arrangement], because a lazy list's
 * arrangement would also space the headers and section titles that share the
 * same LazyColumn, and because a caller that forgets it gets a group of
 * touching containers, which reads as a rendering bug rather than a style.
 */
@Composable
fun KodaListRow(
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.segmentedColors()
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = if (index < count - 1) ListItemDefaults.SegmentedGap else 0.dp),
        // The long press opens the options sheet. Passing null leaves the row
        // tap-only: SegmentedListItem, like combinedClickable, still consumes a
        // long press it was given a handler for, so a no-op lambda would swallow
        // the gesture from anything underneath.
        onLongClick = onLongClick?.let {
            {
                haptics.longPress()
                it()
            }
        },
        content = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = colors
    )
}

/**
 * A song in a segmented list: artwork, title, artist, and whichever of liked,
 * downloaded and duration apply.
 *
 * The trailing metadata is deliberately built here rather than left to the
 * caller. It was the part that differed most between the hand-rolled rows -
 * some showed the like state, some the download state, some neither - and none
 * of those differences were decisions anybody made.
 */
@Composable
fun KodaSongRow(
    song: Song,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    showDuration: Boolean = false,
    /** Short metric shown where the duration normally sits (e.g. "12 plays"). */
    trailingLabel: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.segmentedColors()
) {
    KodaListRow(
        index = index,
        count = count,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        colors = colors,
        headlineContent = {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { KodaRowArtwork(song) },
        trailingContent = trailingContent ?: rowMetadata(
            isLiked = isLiked,
            isDownloaded = isDownloaded,
            durationText = song.duration
                .takeIf { showDuration && it > 0 }
                ?.let { formatRowDuration(it) },
            trailingLabel = trailingLabel
        )
    )
}

/** Leading artwork at the list size, falling back to a note on the tonal container. */
@Composable
fun KodaRowArtwork(song: Song, size: Dp = ROW_ARTWORK_SIZE) {
    Surface(
        shape = RoundedCornerShape(ROW_ARTWORK_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(size)
    ) {
        if (song.albumArtUri != null || song.thumbnailUrl != null) {
            SongArtwork(song = song, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Null when there is nothing to show, so the row does not reserve trailing space
 * for an empty Row - a 0dp trailing slot still shifts the headline's end margin.
 */
@Composable
private fun rowMetadata(
    isLiked: Boolean,
    isDownloaded: Boolean,
    durationText: String?,
    trailingLabel: String?
): (@Composable () -> Unit)? {
    if (!isLiked && !isDownloaded && durationText == null && trailingLabel == null) return null
    return {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            if (isDownloaded) {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (isLiked) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            val label = trailingLabel ?: durationText
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (trailingLabel != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (trailingLabel != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/** The list artwork size and corner, shared so rows cannot drift apart again. */
val ROW_ARTWORK_SIZE = 48.dp
val ROW_ARTWORK_CORNER = 12.dp

internal fun formatRowDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
