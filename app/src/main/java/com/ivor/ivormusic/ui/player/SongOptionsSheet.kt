package com.ivor.ivormusic.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.SongArtwork

/**
 * What you get for long-pressing a song anywhere in music mode.
 *
 * Music mode had no such sheet. Songs could be tapped to play and nothing else,
 * which is why "add to queue" existed on `PlayerViewModel` and could not be
 * reached from anywhere in the app - the queue was something you could only
 * build by starting playback over from a new list, and adding one track to what
 * was already playing was impossible.
 *
 * **Play next and Add to queue lead**, because they are the two this sheet
 * exists for and they are the two with no other route. Adding to a playlist
 * hands off to [AddToPlaylistSheet] rather than reimplementing it; the two
 * sheets swap in place so it stays one gesture rather than a sheet on top of a
 * sheet.
 *
 * **The body scrolls, and that is not decoration.** A bottom sheet whose
 * content is a plain `Column` has a silent hard ceiling: rows past the sheet's
 * height are clipped with nothing logged, identically at every data size, which
 * is exactly how `VideoOptionsSheet` shipped with its last three rows
 * unreachable. This sheet fits a Pixel-class phone upright today, but it does
 * not fit one at a large interface scale or system font size, and it does not
 * fit landscape at all - both of which grow the rows without growing the sheet.
 *
 * **The rows are grouped, not stacked.** They used to be standalone cards with
 * their own icon plate, press spring and 8dp gap, which is roughly 76dp per
 * action for a menu of six. Grouping the related ones into shared containers -
 * queue, library, creator - is both what this actually is and what keeps it a
 * screenful. A chevron means the row navigates somewhere; a row with no
 * trailing glyph acts and closes.
 *
 * **Like toggles in place.** Every other row here is terminal, but liking a
 * song is the one thing someone plausibly does *and then* queues it, and
 * closing the sheet under them made the second action a second long press.
 * [onArtistClick] is nullable because it is only honest where there is an
 * artist page to reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    /** Offered only where there is somewhere to go; null hides the row. */
    onArtistClick: ((String) -> Unit)? = null
) {
    var showPlaylists by remember { mutableStateOf(false) }
    val addToPlaylistItems by viewModel.addToPlaylistItems.collectAsState()

    if (showPlaylists) {
        AddToPlaylistSheet(
            playlists = addToPlaylistItems,
            onPlaylistClick = { playlist ->
                viewModel.addToPlaylist(playlist.id, song)
                onDismiss()
            },
            onCreateNewClick = { name, desc ->
                viewModel.createPlaylistWithSong(name, desc, song)
                onDismiss()
            },
            onDismissRequest = onDismiss
        )
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val likedIds by viewModel.likedSongIds.collectAsState()
    val isLiked = song.id in likedIds
    val isDownloaded = remember(song.id) { viewModel.isDownloaded(song.id) }
    val artist = song.artist.takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Before the insets and the padding, so the last row can be
                // scrolled clear of the gesture bar rather than sitting under
                // it on a short window.
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SongOptionsHeader(song)

            // Queue. The two actions this sheet was built for, first and
            // together, because they are a pair: the only difference is where
            // in the queue the song lands.
            OptionGroup {
                OptionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = stringResource(R.string.song_options_play_next),
                    subtitle = stringResource(R.string.song_options_play_next_subtitle),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.playNext(song)
                        onDismiss()
                    }
                )
                OptionRowDivider()
                OptionRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = stringResource(R.string.song_options_add_to_queue),
                    subtitle = stringResource(R.string.song_options_add_to_queue_subtitle),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.addToQueue(song)
                        onDismiss()
                    }
                )
            }

            // Library. What keeping this song looks like: a playlist, the liked
            // list, or the device.
            OptionGroup {
                OptionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    title = stringResource(R.string.song_options_add_to_playlist),
                    trailing = OptionRowTrailing.CHEVRON,
                    onClick = { showPlaylists = true }
                )
                OptionRowDivider()
                OptionRow(
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    title = if (isLiked) {
                        stringResource(R.string.song_options_remove_from_liked)
                    } else {
                        stringResource(R.string.song_options_like)
                    },
                    iconTint = if (isLiked) MaterialTheme.colorScheme.primary else null,
                    onClick = {
                        haptics.performHapticFeedback(
                            if (isLiked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                        )
                        viewModel.toggleLike(song)
                    }
                )

                // A song already on the device has nothing to download, and a
                // row that would undo the download belongs on the downloads
                // screen rather than one tap from a list of everything.
                if (isDownloaded) {
                    OptionRowDivider()
                    OptionRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.song_options_downloaded),
                        trailing = OptionRowTrailing.CHECK,
                        enabled = false,
                        onClick = {}
                    )
                } else if (!viewModel.isLocalOriginal(song)) {
                    OptionRowDivider()
                    OptionRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.song_options_download),
                        subtitle = stringResource(R.string.song_options_download_subtitle),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.toggleDownload(song)
                            onDismiss()
                        }
                    )
                }
            }

            // The creator, where the caller has a page to send them to.
            if (onArtistClick != null && artist != null) {
                OptionGroup {
                    OptionRow(
                        icon = Icons.Rounded.AccountCircle,
                        title = stringResource(R.string.song_options_go_to_artist, artist),
                        trailing = OptionRowTrailing.CHEVRON,
                        onClick = {
                            // Terminal: the sheet is over a screen the artist
                            // page is about to replace, and leaving it open
                            // would put it on top of the destination.
                            onDismiss()
                            onArtistClick(artist)
                        }
                    )
                }
            }
        }
    }
}

/** Artwork, title and artist, so the sheet says which row was pressed. */
@Composable
private fun SongOptionsHeader(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The 8dp below joins the column's own 8dp gap, so the header
            // stands further off the first group than the groups do off each
            // other - it is a caption, not another action.
            .padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            SongArtwork(
                song = song,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                // Two lines: a long track name truncated to one is the same
                // ellipsis for every song in an album, which tells the user
                // nothing about which one they pressed.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One rounded container holding a run of [OptionRow]s. */
@Composable
private fun OptionGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(content = content)
    }
}

@Composable
private fun OptionRowDivider() {
    HorizontalDivider(
        // Indented past the icon column, so the divider separates the labels
        // rather than cutting the row in half.
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

/** Whether the row goes somewhere, has already happened, or simply acts. */
private enum class OptionRowTrailing { NONE, CHEVRON, CHECK }

/**
 * One action inside a group: 24dp icon, title, optional subtitle, and a
 * trailing glyph.
 *
 * Shaped like `VideoOptionsSheet`'s rows, so the same gesture produces a
 * recognisably similar sheet in both modes - deliberately lighter than the
 * standalone cards this replaced, with no icon plate and no per-row spring
 * scale, because six of those is what stopped fitting. The ripple is the press
 * feedback, which is what a list row is supposed to use.
 */
@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: OptionRowTrailing = OptionRowTrailing.NONE,
    enabled: Boolean = true,
    /** Overrides the accent, for a row whose state is the icon (liked). */
    iconTint: Color? = null
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val resolvedIconTint = iconTint ?: if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // A minimum rather than a fixed height: the labels grow with the
            // user's font scale instead of being cut off by a literal.
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedIconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when (trailing) {
            OptionRowTrailing.NONE -> Unit
            OptionRowTrailing.CHEVRON -> {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            OptionRowTrailing.CHECK -> {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
