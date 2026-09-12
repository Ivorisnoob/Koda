package com.ivor.ivormusic.ui.player

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.isUnknownAlbum
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.ui.theme.appColorScheme
import com.ivor.ivormusic.util.MediaVolumeState
import com.ivor.ivormusic.util.rememberMediaVolume
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
 * **It is a control panel, not a list.** [judgement, September 2026] The shape
 * is three bands, in the order a thumb reaches them: four **tiles** across the
 * top for the things this menu is opened to press, a wrapping row of **pills**
 * for the places it can leave to, and the two **sliders** last. A list of seven
 * 56dp rows spent the whole sheet on labels for actions whose icons already say
 * what they are, put the two destinations - an artist and an album - on lines
 * identical to everything else, and pushed the one thing here that is dragged
 * rather than tapped to wherever it fell in the stack. The long-press
 * [SongOptionsSheet] stays a list on purpose: it acts on an arbitrary row in a
 * screen, where a name and a subtitle are the point. Both still draw from
 * `PlayerOptionRows.kt`, so the vocabulary is shared even where the shape is not.
 *
 * **Speed and volume sit at the foot, together.** They are the only two
 * controls here and the only two things that are dragged; as one deck at the
 * bottom they are under the thumb that opened the sheet, rather than above a
 * row of buttons the hand has to reach across. Volume is the device's own media
 * level - see [VolumeRow] for why it cannot be an app-level gain.
 *
 * **Actions appear only where they lead somewhere.** Go to album is
 * device-library only: [Song.album] is a display string, so it resolves to a
 * real album page for a file on this device and to nothing at all for a YouTube
 * song, and a control that opens a guess is worse than none. Share is the
 * mirror image - a device file has no link to send. Both are hidden rather than
 * disabled, because a greyed control still asks the user to work out why.
 *
 * **Only Like stays open.** It is the one action someone plausibly takes and
 * then follows with another; everything else here either finishes the job or
 * leaves for a screen this sheet would sit on top of.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NowPlayingOptionsSheet(
    song: Song,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    /** Offered only where there is somewhere to go; null hides the row. */
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String) -> Unit)? = null,
    /**
     * Open a YouTube album (MPRE id) in the album detail. The device-library
     * [onAlbumClick] path is separate: a file's album name is a library key,
     * a stream's is a browse id.
     */
    onOpenAlbum: (PlaylistDisplayItem) -> Unit = {},
) {
    var showPlaylists by remember { mutableStateOf(false) }
    val addToPlaylistItems by viewModel.addToPlaylistItems.collectAsState()

    // The account's playlists are a network read, and seven of the eight styles
    // never asked for them - their picker listed local playlists only. Asking
    // once when the menu opens means the list is ready by the time anyone taps
    // through to it.
    LaunchedEffect(Unit) { viewModel.loadYouTubePlaylistsForSheet() }

    if (showPlaylists) {
        // The app's palette here too, for the reason the sheet below states: the
        // picker is the same flow one tap on, and a menu that changed palette
        // halfway through it would be the worst of both.
        MaterialTheme(colorScheme = appColorScheme()) {
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
        }
        return
    }

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    // Follows the hardware keys and the system panel while the sheet is open;
    // hides itself on a device whose output volume cannot be moved.
    val volume = rememberMediaVolume()
    val likedIds by viewModel.likedSongIds.collectAsState()
    val isLiked = song.id in likedIds
    // Keyed on the store's own flows so the row follows a download that
    // finishes while the menu is open, rather than reading once at open time.
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val isDownloaded = remember(downloadedSongs, song.id) { viewModel.isDownloaded(song.id) }
    val isDownloading = remember(downloadingIds, song.id) { viewModel.isDownloading(song.id) }
    val isLocalOriginal = remember(song.id) { viewModel.isLocalOriginal(song) }

    val artist = song.artist.takeIf { !isUnknownArtist(it) }
    // A device file's album name is the key the Library groups by, so it opens
    // a real page. A YouTube song's is free text with no browse id behind it.
    val album = song.album
        .takeIf { !isUnknownAlbum(it) }
        ?.takeIf { song.source == SongSource.LOCAL }
    val shareUrl = "https://music.youtube.com/watch?v=${song.id}"
        .takeIf { song.source == SongSource.YOUTUBE }

    // A stream's album arrives as a link on some rows and not others. When
    // the song itself carries none, one music /next call names it; device
    // files never take this path.
    var resolvedAlbumId by remember(song.id, song.albumId) { mutableStateOf(song.albumId) }
    var resolvedAlbumTitle by remember(song.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(song.id) {
        if (song.source == SongSource.YOUTUBE && resolvedAlbumId == null) {
            val ref = viewModel.getSongAlbumRef(song.id)
            resolvedAlbumId = ref?.albumId
            resolvedAlbumTitle = ref?.albumTitle
        }
    }

    // Where this song came from, worked out before anything is drawn so the
    // pill row knows whether it has anything in it. Streams resolve to a browse
    // id rather than a library key, so they leave through onOpenAlbum.
    val goToArtist = onArtistClick?.takeIf { artist != null }
    val goToAlbum = onAlbumClick?.takeIf { album != null }
    val streamingAlbumTitle = resolvedAlbumTitle
        ?: song.album.takeIf { !isUnknownAlbum(it) }
    val goToStreamingAlbum = if (song.source == SongSource.YOUTUBE) {
        val albumId = resolvedAlbumId
        if (albumId != null && streamingAlbumTitle != null) albumId to streamingAlbumTitle else null
    } else null
    val canBlockArtist = artist != null && song.source == SongSource.YOUTUBE

    // **This sheet is the app's colours, never the album's.** [judgement
    // September 2026] It is composed inside the expanded player, which
    // optionally re-themes its accents from the cover, and a bottom sheet is a
    // subcomposition - so without this every icon, tile, slider and pill in here
    // picked up artwork accents. That looked wrong for a reason worth keeping:
    // `rememberArtworkColorScheme` replaces the *accent* roles and keeps the
    // app's surfaces, so a menu built from those roles is half one palette and
    // half the other - album-tinted controls on app-grey surfaces - and no
    // amount of tinting the surfaces fixed it, because the sheet then matched
    // neither the player behind it nor the app it belongs to. A menu of actions
    // is app furniture, not part of the artwork: it is the same menu whatever is
    // playing, so it takes one palette and that palette is the app's. Choose one
    // or the other here; do not mix them.
    MaterialTheme(colorScheme = appColorScheme()) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SongOptionsHeader(song)

            // The four things this menu is opened to press, as tiles across one
            // row. Like and Download carry their state in the tile rather than
            // in a trailing tick, so what is already done is legible without
            // reading a label.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OptionTile(
                    icon = Icons.Rounded.Radio,
                    label = stringResource(R.string.np_tile_radio),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.playSongRadio(song)
                        onDismiss()
                    }
                )
                OptionTile(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = stringResource(R.string.np_tile_playlist),
                    modifier = Modifier.weight(1f),
                    onClick = { showPlaylists = true }
                )
                OptionTile(
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = if (isLiked) {
                        stringResource(R.string.np_tile_liked)
                    } else {
                        stringResource(R.string.np_tile_like)
                    },
                    selected = isLiked,
                    modifier = Modifier.weight(1f),
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
                    OptionTile(
                        icon = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        label = when {
                            isDownloaded -> stringResource(R.string.np_tile_downloaded)
                            isDownloading -> stringResource(R.string.np_tile_downloading)
                            else -> stringResource(R.string.np_tile_download)
                        },
                        selected = isDownloaded,
                        // Already done or already working: the tile still reads
                        // as the state it is in rather than going grey, which a
                        // selected container says on its own.
                        enabled = !isDownloaded && !isDownloading,
                        loading = isDownloading,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.toggleDownload(song)
                            onDismiss()
                        }
                    )
                }
            }

            // Where this song came from, and what else can be done with it.
            // Every one of these leaves the player or the app, so each closes
            // the sheet ahead of whatever replaces it. Pills rather than rows
            // because an artist's name and an album title are the label.
            if (goToArtist != null || goToAlbum != null || goToStreamingAlbum != null ||
                shareUrl != null || canBlockArtist
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (goToArtist != null && artist != null) {
                        OptionPill(
                            icon = Icons.Rounded.AccountCircle,
                            label = artist,
                            contentDescription = stringResource(
                                R.string.song_options_go_to_artist, artist
                            ),
                            onClick = {
                                onDismiss()
                                goToArtist(artist)
                            }
                        )
                    }
                    if (goToAlbum != null && album != null) {
                        OptionPill(
                            icon = Icons.Rounded.Album,
                            label = album,
                            contentDescription = stringResource(
                                R.string.song_options_go_to_artist, album
                            ),
                            onClick = {
                                onDismiss()
                                goToAlbum(album)
                            }
                        )
                    }
                    if (goToStreamingAlbum != null) {
                        val (streamingAlbumId, streamingTitle) = goToStreamingAlbum
                        OptionPill(
                            icon = Icons.Rounded.Album,
                            label = streamingTitle,
                            contentDescription = stringResource(
                                R.string.song_options_go_to_artist, streamingTitle
                            ),
                            onClick = {
                                onDismiss()
                                onOpenAlbum(
                                    PlaylistDisplayItem(
                                        name = streamingTitle,
                                        url = "https://music.youtube.com/browse/$streamingAlbumId",
                                        uploaderName = artist ?: song.artist,
                                        thumbnailUrl = song.highResThumbnailUrl ?: song.thumbnailUrl,
                                    )
                                )
                            }
                        )
                    }
                    if (shareUrl != null) {
                        OptionPill(
                            icon = Icons.Rounded.Share,
                            label = stringResource(R.string.action_share),
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
                    // Stop recommending this artist. Last and quiet, because it
                    // is the one action here that takes something away rather
                    // than adding it - and only for a song a feed could have
                    // served, since nothing recommended the files on this
                    // device.
                    artist?.takeIf { canBlockArtist }?.let { blockedArtist ->
                        OptionPill(
                            icon = Icons.Rounded.RemoveCircleOutline,
                            label = stringResource(R.string.np_pill_block),
                            contentDescription = stringResource(
                                R.string.song_options_block_artist, blockedArtist
                            ),
                            quiet = true,
                            onClick = {
                                // The undo snackbar sits at the root of the app,
                                // under this sheet; the sheet has to leave for
                                // it to be reachable.
                                onDismiss()
                                viewModel.blockArtist(song)
                            }
                        )
                    }
                }
            }

            // The two controls, last and together - see the deck's own note.
            OptionGroup {
                PlaybackSpeedRow(viewModel)
                if (volume.isAvailable) {
                    OptionRowDivider()
                    VolumeRow(volume)
                }
            }
        }
    }
    }
}

/**
 * The shape both of this sheet's controls take: a labelled line with the live
 * value at its end and one button, over a slider the width of the group.
 *
 * One scaffold rather than two rows that look alike, so speed and volume cannot
 * drift apart - the label column, the value's reserved width and the slider's
 * inset are the same measurements in both, which is the whole reason they read
 * as one deck instead of two widgets that happen to be adjacent.
 */
@Composable
private fun ControlRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    valueText: String,
    /** The value is off its resting point, so it is worth the accent. */
    valueAccented: Boolean,
    trailing: @Composable () -> Unit,
    slider: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (valueAccented) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
                maxLines = 1,
                // A minimum width, so the row does not shuffle sideways as the
                // number goes from two digits to three during a drag.
                modifier = Modifier.widthIn(min = 48.dp)
            )
            trailing()
        }
        slider()
    }
}

/**
 * The playback rate, as a slider over the whole group's width.
 *
 * **A control rather than an action, and one of two.** Both controls now sit at
 * the foot of the sheet, under the tiles and the pills: the actions above are
 * pressed once and finish the job, while these two are dragged and listened to,
 * and a control deck belongs where the thumb rests rather than above a row of
 * buttons it would be reached across. It led the sheet while it was the only
 * control in a stack of list rows, which is a different sheet from this one.
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

    ControlRow(
        icon = Icons.Rounded.Speed,
        iconTint = MaterialTheme.colorScheme.primary,
        label = stringResource(R.string.song_options_speed),
        valueText = stringResource(R.string.song_options_speed_value, percent),
        valueAccented = !isNormal,
        trailing = {
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
    ) {
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
 * The device's media volume, beside the speed it is judged against.
 *
 * **It is the system stream level, not an app-level gain**, for the reason
 * [com.ivor.ivormusic.util.MediaVolumeState] spells out: `player.volume`
 * already carries the loudness correction and the crossfade/duck curve, and a
 * third factor on that field would be the first thing to break a fade. This is
 * the same level the hardware keys move and the same one the video player's
 * vertical drag moves, so the three controls can never disagree - and it
 * follows the keys live while the sheet is open.
 *
 * **It snaps to the stream's own steps**, the rule the speed slider follows: a
 * device has 15 positions (sometimes 25, sometimes 100), so a continuous thumb
 * would sit between two of them and name a level that is not audible. The tick
 * lands on each crossing, which is what a volume drag feels like on the keys.
 *
 * **The leading icon reports, the trailing button acts.** Mute lives in the
 * trailing slot rather than on the icon so the geometry matches the speed row
 * exactly, and it is always offered: it either silences or restores the level it
 * silenced, so unlike speed's reset there is no state where it does nothing.
 */
@Composable
private fun VolumeRow(volume: MediaVolumeState) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    var dragging by remember { mutableStateOf(false) }
    var sliderVolume by remember { mutableFloatStateOf(volume.fraction) }
    // Follow the device while the control is at rest - the keys, a headset or
    // the system panel can all move it under an open sheet - but never under
    // the finger, where it would fight the drag.
    LaunchedEffect(volume.fraction, dragging) { if (!dragging) sliderVolume = volume.fraction }

    val percent = (sliderVolume * 100f).roundToInt()
    val muted = sliderVolume <= 0f

    ControlRow(
        icon = when {
            muted -> Icons.AutoMirrored.Rounded.VolumeOff
            sliderVolume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
            else -> Icons.AutoMirrored.Rounded.VolumeUp
        },
        iconTint = if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = stringResource(R.string.np_volume),
        valueText = stringResource(R.string.np_volume_value, percent),
        valueAccented = false,
        trailing = {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(
                        if (muted) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                    )
                    dragging = false
                    volume.toggleMute()
                    sliderVolume = volume.fraction
                }
            ) {
                Icon(
                    imageVector = if (muted) {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    },
                    contentDescription = if (muted) {
                        stringResource(R.string.cd_volume_unmute)
                    } else {
                        stringResource(R.string.cd_volume_mute)
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        Slider(
            value = sliderVolume,
            onValueChange = { raw ->
                dragging = true
                val before = (sliderVolume * volume.steps).roundToInt()
                val step = volume.set(raw)
                // One tick per step the drag crosses, not per frame: that is
                // the cadence the hardware keys have, and what the finger is
                // listening for.
                if (step != before) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                sliderVolume = volume.fraction
            },
            onValueChangeFinished = {
                dragging = false
                // Nothing to persist: the stream level is the store.
                sliderVolume = volume.fraction
            },
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
