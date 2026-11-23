package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.isUnknownTitle

/**
 * The songs for music Home's speed dial (#373): what this profile plays most,
 * as YouTube Music's speed dial is, but read from Koda's own listening history
 * so it works signed out and with device files.
 *
 * Plays from the last [windowMs] count first, so the grid follows what someone
 * is into now; when that is not enough to fill it, older favourites fill the
 * rest. Ties go to the song played most recently. [history] is newest first,
 * as `StatsRepository.loadHistory` returns it; the entry kept for each song is
 * its newest, which carries its latest title and artwork.
 */
internal fun speedDialFrom(
    history: List<PlayHistoryEntry>,
    nowMs: Long,
    limit: Int = SPEED_DIAL_SIZE,
    windowMs: Long = SPEED_DIAL_WINDOW_MS,
): List<PlayHistoryEntry> {
    if (history.isEmpty() || limit <= 0) return emptyList()
    fun ranked(entries: List<PlayHistoryEntry>): List<PlayHistoryEntry> {
        val newest = LinkedHashMap<String, PlayHistoryEntry>()
        val counts = HashMap<String, Int>()
        entries.forEach { entry ->
            newest.putIfAbsent(entry.songId, entry)
            counts[entry.songId] = (counts[entry.songId] ?: 0) + 1
        }
        // Stable sort over newest-first order: equal counts keep recency.
        return newest.values.sortedByDescending { counts.getValue(it.songId) }
    }
    val recent = ranked(history.filter { nowMs - it.timestamp <= windowMs })
    if (recent.size >= limit) return recent.take(limit)
    val chosen = recent.mapTo(HashSet()) { it.songId }
    return (recent + ranked(history).filter { it.songId !in chosen }).take(limit)
}

/** Three rows of three, as YouTube Music lays it out. */
internal const val SPEED_DIAL_SIZE = 9

/** Fewer than this and the section is left out rather than drawn half empty. */
internal const val SPEED_DIAL_MIN = 3

private const val SPEED_DIAL_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

/**
 * Music Home's speed dial: up to nine covers, three to a row, each one tap to
 * play. Drawn only with [SPEED_DIAL_MIN] songs or more. Rows rather than a
 * nested lazy grid: it sits inside Home's list and never holds more than nine.
 */
@Composable
internal fun SpeedDialSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)?,
) {
    if (songs.size < SPEED_DIAL_MIN) return
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(title = stringResource(R.string.home_section_speed_dial))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            songs.chunked(SPEED_DIAL_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { song ->
                        SpeedDialTile(song, onSongClick, onSongLongPress, Modifier.weight(1f))
                    }
                    repeat(SPEED_DIAL_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SpeedDialTile(
    song: Song,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.homeCardClickable(
            SPEED_DIAL_SHAPE,
            onClick = { onSongClick(song) },
            onLongClick = onSongLongPress?.let { press -> { press(song) } },
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(SPEED_DIAL_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val art = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            com.ivor.ivormusic.ui.components.NowPlayingMark(song.id, Modifier.fillMaxSize(), barSize = 28.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = song.title.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled_song),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private const val SPEED_DIAL_COLUMNS = 3
private val SPEED_DIAL_SHAPE = RoundedCornerShape(20.dp)
