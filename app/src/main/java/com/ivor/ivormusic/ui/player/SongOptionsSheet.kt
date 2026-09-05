package com.ivor.ivormusic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song

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
    onArtistClick: ((String) -> Unit)? = null,
    /**
     * The two "don't recommend this" taps. Null on a surface with no
     * recommendation feed behind it - the same gate `VideoOptionsSheet` uses -
     * and both are null for a device file, which no feed recommended in the
     * first place.
     */
    onNotInterested: (() -> Unit)? = null,
    onBlockArtist: (() -> Unit)? = null
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

            // Dismissals last and on their own, the way the video sheet has
            // them: they are the destructive-shaped actions here, and a row
            // that removes something from a feed should not sit next to the
            // rows that add it to one.
            if (onNotInterested != null || onBlockArtist != null) {
                OptionGroup {
                    onNotInterested?.let { dismiss ->
                        OptionRow(
                            icon = Icons.Rounded.NotInterested,
                            title = stringResource(R.string.song_options_not_interested),
                            subtitle = stringResource(
                                R.string.song_options_not_interested_subtitle
                            ),
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                // The undo snackbar lives at the root of the
                                // app, behind this sheet, so the sheet has to
                                // get out of the way for it to be reachable.
                                onDismiss()
                                dismiss()
                            }
                        )
                    }
                    if (onNotInterested != null && onBlockArtist != null && artist != null) {
                        OptionRowDivider()
                    }
                    if (onBlockArtist != null && artist != null) {
                        OptionRow(
                            icon = Icons.Rounded.RemoveCircleOutline,
                            title = stringResource(R.string.song_options_block_artist, artist),
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                onDismiss()
                                onBlockArtist()
                            }
                        )
                    }
                }
            }
        }
    }
}
