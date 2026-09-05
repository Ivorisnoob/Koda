package com.ivor.ivormusic.ui.player

import android.content.Intent
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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource

/**
 * Everything you can do to the song that is playing, from any of the eight
 * player styles.
 *
 * **This is what the overflow button in each player opens, and it replaced the
 * Add-to-playlist button that used to sit there.** One action had a permanent
 * place in the top bar of every style while start-a-radio, go-to-artist,
 * go-to-album and share had no place at all - and each style separately hosted
 * its own `AddToPlaylistSheet`, so the one thing that *was* reachable was
 * implemented eight times. The styles now own the button and nothing else: the
 * menu behind it is this file, once.
 *
 * **The button is per style, the sheet is not.** Each player draws the dots
 * with its own primitive - Editorial's die-cut circle, Bento's tile, Morph's
 * utility button, Classic's filled icon button - because the top bar is part of
 * that style's identity and a shared button would be the one foreign element in
 * it. What opens is the same menu everywhere, because the actions are not a
 * style choice.
 *
 * **Rows appear only where they lead somewhere.** Go to album is device-library
 * only: [Song.album] is a display string, so it resolves to a real album page
 * for a file on this device and to nothing at all for a YouTube song, and a row
 * that opens a guess is worse than no row. Share is the mirror image - a device
 * file has no link to send. Both are hidden rather than disabled, because a
 * greyed row still asks the user to work out why.
 *
 * **Only Like stays open.** It is the one action someone plausibly takes and
 * then follows with another; everything else here either finishes the job or
 * leaves for a screen this sheet would sit on top of.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingOptionsSheet(
    song: Song,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    /** Offered only where there is somewhere to go; null hides the row. */
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String) -> Unit)? = null
) {
    var showPlaylists by remember { mutableStateOf(false) }
    val addToPlaylistItems by viewModel.addToPlaylistItems.collectAsState()

    // The account's playlists are a network read, and seven of the eight styles
    // never asked for them - their picker listed local playlists only. Asking
    // once when the menu opens means the list is ready by the time anyone taps
    // through to it.
    LaunchedEffect(Unit) { viewModel.loadYouTubePlaylistsForSheet() }

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

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val likedIds by viewModel.likedSongIds.collectAsState()
    val isLiked = song.id in likedIds
    // Keyed on the store's own flows so the row follows a download that
    // finishes while the menu is open, rather than reading once at open time.
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val isDownloaded = remember(downloadedSongs, song.id) { viewModel.isDownloaded(song.id) }
    val isDownloading = remember(downloadingIds, song.id) { viewModel.isDownloading(song.id) }
    val isLocalOriginal = remember(song.id) { viewModel.isLocalOriginal(song) }

    val artist = song.artist.takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) }
    // A device file's album name is the key the Library groups by, so it opens
    // a real page. A YouTube song's is free text with no browse id behind it.
    val album = song.album
        .takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) }
        ?.takeIf { song.source == SongSource.LOCAL }
    val shareUrl = "https://music.youtube.com/watch?v=${song.id}"
        .takeIf { song.source == SongSource.YOUTUBE }

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
                // scrolled clear of the gesture bar. This menu is opened from
                // a full-screen player, which is exactly where a sheet has the
                // least room and where silent clipping would go unnoticed.
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SongOptionsHeader(song)

            // What to do with this song.
            OptionGroup {
                OptionRow(
                    icon = Icons.Rounded.Radio,
                    title = stringResource(R.string.ar_start_radio),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.playSongRadio(song)
                        onDismiss()
                    }
                )
                OptionRowDivider()
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

                // A file already on this device has nothing to download, and
                // undoing a download belongs on the downloads screen rather
                // than one tap from the player.
                if (!isLocalOriginal) {
                    OptionRowDivider()
                    when {
                        isDownloaded -> OptionRow(
                            icon = Icons.Rounded.Download,
                            title = stringResource(R.string.song_options_downloaded),
                            trailing = OptionRowTrailing.CHECK,
                            enabled = false,
                            onClick = {}
                        )

                        isDownloading -> OptionRow(
                            icon = Icons.Rounded.Download,
                            title = stringResource(R.string.song_options_download),
                            trailing = OptionRowTrailing.LOADING,
                            enabled = false,
                            onClick = {}
                        )

                        else -> OptionRow(
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
            }

            // Where this song came from. Both rows leave the player, so both
            // close the sheet ahead of the screen that replaces it.
            val goToArtist = onArtistClick?.takeIf { artist != null }
            val goToAlbum = onAlbumClick?.takeIf { album != null }
            if (goToArtist != null || goToAlbum != null) {
                OptionGroup {
                    if (goToArtist != null && artist != null) {
                        OptionRow(
                            icon = Icons.Rounded.AccountCircle,
                            title = stringResource(R.string.song_options_go_to_artist, artist),
                            trailing = OptionRowTrailing.CHEVRON,
                            onClick = {
                                onDismiss()
                                goToArtist(artist)
                            }
                        )
                    }
                    if (goToArtist != null && goToAlbum != null) OptionRowDivider()
                    if (goToAlbum != null && album != null) {
                        OptionRow(
                            icon = Icons.Rounded.Album,
                            title = stringResource(R.string.song_options_go_to_artist, album),
                            trailing = OptionRowTrailing.CHEVRON,
                            onClick = {
                                onDismiss()
                                goToAlbum(album)
                            }
                        )
                    }
                }
            }

            // Stop recommending this artist. Last and on its own, because it
            // is the one row here that takes something away rather than
            // adding it - and only for a song a feed could have served, since
            // nothing recommended the files on this device.
            if (artist != null && song.source == SongSource.YOUTUBE) {
                OptionGroup {
                    OptionRow(
                        icon = Icons.Rounded.RemoveCircleOutline,
                        title = stringResource(R.string.song_options_block_artist, artist),
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            // The undo snackbar sits at the root of the app,
                            // under this sheet; the sheet has to leave for it
                            // to be reachable.
                            onDismiss()
                            viewModel.blockArtist(song)
                        }
                    )
                }
            }

            if (shareUrl != null) {
                OptionGroup {
                    OptionRow(
                        icon = Icons.Rounded.Share,
                        title = stringResource(R.string.action_share),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareUrl)
                            }
                            context.startActivity(
                                Intent.createChooser(send, context.getString(R.string.action_share))
                            )
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
