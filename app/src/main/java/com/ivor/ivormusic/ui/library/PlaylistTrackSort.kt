package com.ivor.ivormusic.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import java.util.Locale

/**
 * How a playlist's tracks are shown. A view, not an edit: the playlist keeps
 * its own order, and the choice is remembered per playlist (see
 * `ThemePreferences.getPlaylistTrackSort`), as other players remember it.
 *
 * The constant names are stored, so they are frozen.
 */
enum class PlaylistTrackSort(@StringRes val label: Int) {
    /** The playlist's own order; reversed, its last song first. */
    Custom(R.string.pl_sort_custom),
    Title(R.string.pl_sort_title),
    Artist(R.string.pl_sort_artist),
    Album(R.string.pl_sort_album),
    Duration(R.string.pl_sort_duration),

    /**
     * Newest first. Offered only where the order of adding is known: a device
     * playlist appends, and Liked songs carry the time of each like. A YouTube
     * playlist's order is its owner's, so it says nothing about when.
     */
    RecentlyAdded(R.string.pl_sort_recent);
}

/** A sort and its direction, as stored: "Title:asc". */
data class PlaylistTrackSortChoice(val sort: PlaylistTrackSort, val descending: Boolean = false) {
    val isDefault: Boolean get() = sort == PlaylistTrackSort.Custom && !descending

    fun encode(): String? = if (isDefault) null else "${sort.name}:${if (descending) "desc" else "asc"}"

    /** The same sort the other way, or [next] in its natural direction. */
    fun tapped(next: PlaylistTrackSort): PlaylistTrackSortChoice =
        if (next == sort) copy(descending = !descending)
        else PlaylistTrackSortChoice(next, descending = next == PlaylistTrackSort.RecentlyAdded)

    companion object {
        val Default = PlaylistTrackSortChoice(PlaylistTrackSort.Custom)

        fun decode(stored: String?): PlaylistTrackSortChoice {
            val name = stored?.substringBefore(':') ?: return Default
            val sort = PlaylistTrackSort.entries.firstOrNull { it.name == name } ?: return Default
            return PlaylistTrackSortChoice(sort, descending = stored.substringAfter(':', "asc") == "desc")
        }
    }
}

/**
 * [rows] in [choice]'s order. Every comparison falls back to the playlist's
 * own position, so equal keys keep the order the playlist gave them and a
 * re-sort never shuffles ties.
 */
internal fun <T> List<T>.sortedForPlaylist(
    choice: PlaylistTrackSortChoice,
    song: (T) -> com.ivor.ivormusic.data.Song,
): List<T> {
    if (choice.isDefault) return this
    val indexed = withIndex().toList()
    fun text(value: String) = value.trim().lowercase(Locale.ROOT)
    val byKey: Comparator<IndexedValue<T>> = when (choice.sort) {
        PlaylistTrackSort.Custom -> compareBy { it.index }
        PlaylistTrackSort.Title -> compareBy { text(song(it.value).title) }
        PlaylistTrackSort.Artist -> compareBy<IndexedValue<T>>(
            { text(song(it.value).artist) },
            { text(song(it.value).album) },
            { song(it.value).discNumber ?: 1 },
            { song(it.value).trackNumber ?: Int.MAX_VALUE },
        )
        PlaylistTrackSort.Album -> compareBy<IndexedValue<T>>(
            { text(song(it.value).album) },
            { song(it.value).discNumber ?: 1 },
            { song(it.value).trackNumber ?: Int.MAX_VALUE },
        )
        PlaylistTrackSort.Duration -> compareBy { song(it.value).duration }
        // Ascending here is oldest first; the natural direction is descending.
        // Where every song carries its time (Liked songs) that time decides,
        // otherwise the position does, which for an appending playlist is the
        // same thing.
        PlaylistTrackSort.RecentlyAdded -> {
            val timed = all { song(it).dateAdded != null }
            if (timed) compareBy<IndexedValue<T>> { song(it.value).dateAdded } else compareBy { it.index }
        }
    }
    val directed = if (choice.descending) byKey.reversed() else byKey
    return indexed.sortedWith(directed.thenBy { it.index }).map { it.value }
}

/**
 * The sort button beside the track count, and its menu. Tapping the current
 * choice again flips its direction, shown by the arrow beside it.
 */
@Composable
internal fun PlaylistTrackSortButton(
    choice: PlaylistTrackSortChoice,
    offerRecentlyAdded: Boolean,
    onChoice: (PlaylistTrackSortChoice) -> Unit,
    tint: androidx.compose.ui.graphics.Color,
) {
    var open by remember { mutableStateOf(false) }
    val currentLabel = stringResource(choice.sort.label)
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.AutoMirrored.Rounded.Sort,
                contentDescription = stringResource(R.string.pl_sort_cd, currentLabel),
                tint = if (choice.isDefault) tint else MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PlaylistTrackSort.entries
                .filter { it != PlaylistTrackSort.RecentlyAdded || offerRecentlyAdded }
                .forEach { option ->
                    val selected = option == choice.sort
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(option.label),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        trailingIcon = if (selected) {
                            {
                                Icon(
                                    if (choice.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else null,
                        onClick = {
                            // Stays open on a direction flip, so the arrow can
                            // be seen to turn; a new sort closes it.
                            onChoice(choice.tapped(option))
                            if (!selected) open = false
                        },
                    )
                }
        }
    }
}
