package com.ivor.ivormusic.ui.downloads
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.DownloadAudioFormat
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.MusicDownloadOptions
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.components.SongArtwork
import java.util.Locale

/**
 * The sheet every individual music download goes through: which quality, with
 * the exact size of each on its card, whether to keep the lyrics, and whether
 * those choices become the default.
 *
 * It opens on the stored defaults and changes them only when asked to and only
 * once the download is confirmed, so trying a card never rewrites a setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDownloadSheet(
    song: Song,
    onConfirm: (MusicDownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { DownloadRepository.getInstance(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var quality by remember(song.id) {
        mutableStateOf(ThemePreferences.currentDownloadMusicQuality(context))
    }
    var saveLyrics by remember(song.id) {
        mutableStateOf(ThemePreferences.saveLyricsWithDownloads(context))
    }
    var rememberChoices by remember(song.id) { mutableStateOf(false) }

    // Null while the one request that answers both cards is in flight; empty
    // when it did not answer, and the size is then asked for the chosen
    // quality alone through the download path's own fallbacks.
    var formats by remember(song.id) { mutableStateOf<List<DownloadAudioFormat>?>(null) }
    var fallbackBytes by remember(song.id) { mutableStateOf<Map<String, Long?>>(emptyMap()) }

    LaunchedEffect(song.id) {
        val resolved = repository.songDownloadFormats(song)
        formats = resolved
        // A song with one stream only: do not leave the missing one selected.
        if (resolved.isNotEmpty() && resolved.none { it.quality == quality }) {
            quality = resolved.first().quality
        }
    }
    LaunchedEffect(song.id, formats, quality) {
        if (formats?.isEmpty() == true && quality !in fallbackBytes) {
            val bytes = repository.estimateSongDownloadBytes(song, quality)
            fallbackBytes = fallbackBytes + (quality to bytes)
        }
    }

    fun bytesFor(option: String): Long? =
        formats?.firstOrNull { it.quality == option }?.contentLength ?: fallbackBytes[option]

    val selectedBytes = bytesFor(quality)
    val sizePending = formats == null ||
        (formats?.isEmpty() == true && quality !in fallbackBytes)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Two cards, two tiles and a button run past a landscape or
                // large-font window; unscrolled, the button is what is lost.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.sd_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SongArtwork(
                    song = song,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = stringResource(R.string.sd_quality),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            val knownFormats = formats
            val highDetail = qualityDetail(knownFormats, ThemePreferences.DOWNLOAD_MUSIC_QUALITY_HIGH)
            val saverDetail = qualityDetail(knownFormats, ThemePreferences.DOWNLOAD_MUSIC_QUALITY_SAVER)
            MusicQualityPicker(
                selected = quality,
                onSelect = { quality = it },
                info = { option ->
                    MusicQualityCardInfo(
                        detail = if (option == ThemePreferences.DOWNLOAD_MUSIC_QUALITY_SAVER) saverDetail
                            else highDetail,
                        size = bytesFor(option)?.let(::formatDownloadSize),
                        sizeLoading = knownFormats == null ||
                            (knownFormats.isEmpty() && option == quality && option !in fallbackBytes),
                        available = knownFormats.isNullOrEmpty() ||
                            knownFormats.any { it.quality == option }
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            DownloadOptionSwitch(
                icon = Icons.Rounded.Lyrics,
                title = stringResource(R.string.sd_lyrics),
                subtitle = stringResource(R.string.sd_lyrics_sub),
                checked = saveLyrics,
                onCheckedChange = { saveLyrics = it }
            )

            Spacer(Modifier.height(10.dp))

            DownloadOptionSwitch(
                icon = Icons.Rounded.Bookmark,
                title = stringResource(R.string.sd_remember),
                subtitle = stringResource(R.string.sd_remember_sub),
                checked = rememberChoices,
                onCheckedChange = { rememberChoices = it }
            )

            Text(
                text = stringResource(R.string.sd_artwork_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (rememberChoices) {
                        ThemePreferences.setDownloadMusicQuality(context, quality)
                        ThemePreferences.setSaveLyricsWithDownloads(context, saveLyrics)
                    }
                    onConfirm(MusicDownloadOptions(quality = quality, saveLyrics = saveLyrics))
                },
                enabled = !sizePending,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedBytes?.let {
                        stringResource(R.string.sd_download_size, formatDownloadSize(it))
                    } ?: stringResource(R.string.song_options_download),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** The codec line under a quality's name: the real bitrate once known, the nominal one before. */
@Composable
private fun qualityDetail(formats: List<DownloadAudioFormat>?, quality: String): String =
    musicBitrateLabel(
        formats?.firstOrNull { it.quality == quality }?.bitrate?.takeIf { it > 0 }
            ?: nominalMusicBitrate(quality)
    )

internal fun formatDownloadSize(bytes: Long): String {
    val mib = bytes / (1024.0 * 1024.0)
    return if (mib >= 1024.0) {
        String.format(Locale.US, "%.1f GB", mib / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mib)
    }
}
