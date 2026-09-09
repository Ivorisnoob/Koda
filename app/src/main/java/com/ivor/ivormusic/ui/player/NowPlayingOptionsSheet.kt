package com.ivor.ivormusic.ui.player

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.ThemePreferences
import kotlin.math.roundToInt

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

            // How it plays, before what to do with it - see the row's own note.
            OptionGroup { PlaybackSpeedRow(viewModel) }

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

/**
 * The playback rate, as a slider over the whole group's width.
 *
 * **A control rather than an action, and the only one in this sheet.** It sits
 * directly under the header for that reason: everything below is a row you tap
 * once and are done with, while this is the thing someone opens the menu to
 * adjust, and burying it under four action rows would put it under the fold on
 * a small screen at the moment it is being looked for.
 *
 * **The rate applies as it moves and persists once, when the finger lifts.**
 * Speed is judged by ear, so a slider that only took effect on release is one
 * the user has to guess at; but writing preferences on every frame of a drag
 * is forty writes for one decision. [PlayerViewModel.setPlaybackSpeed] carries
 * that distinction and the service honours it.
 *
 * **Values snap to five percent.** The slider is continuous, but the thumb is
 * drawn at the rounded value, so the label, the thumb and what is audible all
 * agree - and a drag past the recorded speed detents onto it with a haptic,
 * because 100% is the one value people want to land on exactly. Material's own
 * tick marks were the alternative and 38 of them read as clutter.
 */
@Composable
private fun PlaybackSpeedRow(viewModel: PlayerViewModel) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val speed by viewModel.playbackSpeed.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var sliderSpeed by remember { mutableFloatStateOf(speed) }
    // Follow the service while the control is at rest - another surface, or a
    // restored session, can change the rate under an open sheet - but never
    // under the finger, where it would fight the drag.
    LaunchedEffect(speed, dragging) { if (!dragging) sliderSpeed = speed }

    val percent = (sliderSpeed * 100f).roundToInt()
    val isNormal = percent == 100

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.song_options_speed),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.song_options_speed_value, percent),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isNormal) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.End,
                maxLines = 1,
                // A minimum width, so the row does not shuffle sideways as the
                // number goes from two digits to three during a drag.
                modifier = Modifier.widthIn(min = 48.dp)
            )
            // Only where it leads somewhere: at the recorded speed there is
            // nothing to reset to, and a permanently dead button beside a
            // control is worse than none.
            AnimatedVisibility(visible = !isNormal) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        dragging = false
                        sliderSpeed = ThemePreferences.DEFAULT_PLAYBACK_SPEED
                        viewModel.setPlaybackSpeed(ThemePreferences.DEFAULT_PLAYBACK_SPEED)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_speed_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Slider(
            value = sliderSpeed,
            onValueChange = { raw ->
                dragging = true
                val snapped = snapPlaybackSpeed(raw)
                if (snapped != sliderSpeed) {
                    // One tick as the thumb crosses the recorded speed, so the
                    // detent can be felt without looking at the number.
                    if (snapped == ThemePreferences.DEFAULT_PLAYBACK_SPEED) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    sliderSpeed = snapped
                    // persist = false: this is one frame of a gesture, not a
                    // decision. It still applies, because the choice is made
                    // by ear.
                    viewModel.setPlaybackSpeed(snapped, persist = false)
                }
            },
            onValueChangeFinished = {
                dragging = false
                viewModel.setPlaybackSpeed(sliderSpeed)
            },
            valueRange = ThemePreferences.MIN_PLAYBACK_SPEED..ThemePreferences.MAX_PLAYBACK_SPEED,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Round a raw slider position to the nearest five percent, with a wider catch
 * around the recorded speed so a drag settles on exactly 100% rather than on
 * the 95% or 105% either side of it.
 */
private fun snapPlaybackSpeed(raw: Float): Float {
    val bounded = raw.coerceIn(
        ThemePreferences.MIN_PLAYBACK_SPEED,
        ThemePreferences.MAX_PLAYBACK_SPEED,
    )
    if (kotlin.math.abs(bounded - ThemePreferences.DEFAULT_PLAYBACK_SPEED) < SPEED_DETENT) {
        return ThemePreferences.DEFAULT_PLAYBACK_SPEED
    }
    return (bounded * 20f).roundToInt() / 20f
}

/** Half a step either side of the recorded speed, so 100% is easy to hit. */
private const val SPEED_DETENT = 0.03f
