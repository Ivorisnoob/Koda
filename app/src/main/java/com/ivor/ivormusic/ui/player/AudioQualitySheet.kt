package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.lossless.LosslessCache
import com.ivor.ivormusic.data.lossless.LosslessDownloadInfo
import com.ivor.ivormusic.data.lossless.LosslessOrigin
import com.ivor.ivormusic.data.lossless.LosslessOrigins
import com.ivor.ivormusic.data.lossless.LosslessQuality
import com.ivor.ivormusic.service.SpectrumProbe
import com.ivor.ivormusic.service.SpectrumReading
import com.ivor.ivormusic.service.core.QualityVerdict
import com.ivor.ivormusic.service.core.SpectrumAnalysis
import com.ivor.ivormusic.ui.components.AdaptiveModalSheet
import com.ivor.ivormusic.ui.components.LosslessPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** Where the playing song's audio is coming from, as the sheet names it. */
private enum class AudioOrigin { LOSSLESS_SERVER, LOSSLESS_KEPT, LOSSLESS_DOWNLOAD, DOWNLOAD, DEVICE_FILE, YOUTUBE }

/**
 * Audio quality: what the playing song really is, and the evidence.
 *
 * Opened from the Lossless pill on any player. The top half is what the file
 * says about itself - codec, sample rate, bit depth, channels, bitrate, and
 * where it came from. The bottom half checks the claim against the sound: a
 * live spectrum of the decoded audio, averaged over the seconds it has been
 * open, with the ceiling it finds marked against the lines that give fakes
 * away (a lossy encoder's 16-20 kHz, a CD's 22.05 kHz) and a plain verdict
 * ([SpectrumAnalysis]). It costs nothing while closed: the probe only runs
 * while this sheet holds it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun AudioQualitySheet(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val song by viewModel.currentSong.collectAsState()
    val track by viewModel.audioTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val reading by SpectrumProbe.reading.collectAsState()
    val downloads = remember(context) { DownloadRepository.getInstance(context) }
    val downloadedSongs by downloads.downloadedSongs.collectAsState()
    val losslessDownloads by downloads.losslessDownloads.collectAsState()

    DisposableEffect(Unit) {
        SpectrumProbe.acquire()
        onDispose { SpectrumProbe.release() }
    }
    // A new song is a new measurement.
    LaunchedEffect(song?.id) { SpectrumProbe.reset() }

    val songId = song?.id
    // Whether the song is whole in the lossless cache yet; it fills as it plays.
    val keptWhole by produceState(false, songId) {
        while (songId != null) {
            value = withContext(Dispatchers.IO) {
                LosslessQuality.entries.any { q -> LosslessCache.kept(context, LosslessCache.prefix(q, songId)) != null }
            }
            if (value) break
            delay(4_000)
        }
    }

    val downloadedLossless = songId?.let { losslessDownloads[it] }
    val origin = when {
        songId == null -> AudioOrigin.YOUTUBE
        downloadedLossless != null && downloadedSongs.any { it.id == songId } -> AudioOrigin.LOSSLESS_DOWNLOAD
        downloadedSongs.any { it.id == songId } -> AudioOrigin.DOWNLOAD
        song?.source == SongSource.LOCAL -> AudioOrigin.DEVICE_FILE
        track?.lossless == true && LosslessOrigins.of(songId) == LosslessOrigin.KEPT -> AudioOrigin.LOSSLESS_KEPT
        track?.lossless == true -> AudioOrigin.LOSSLESS_SERVER
        else -> AudioOrigin.YOUTUBE
    }

    val current = reading
    val lossless = track?.lossless == true
    val sampleRate = track?.sampleRate ?: current?.sampleRate ?: 0
    val verdict = SpectrumAnalysis.verdict(lossless, sampleRate, current?.cutoffHz, current?.seconds ?: 0f)

    AdaptiveModalSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Two cards, six tiles and a chart: scrolls on any landscape window.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(stringResource(R.string.aq_title), style = MaterialTheme.typography.headlineSmall)
                song?.let {
                    Text(
                        text = listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            QualityHero(track = track, origin = origin, keptWhole = keptWhole)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 2
            ) {
                val unknown = stringResource(R.string.aq_unknown)
                FactTile(stringResource(R.string.aq_codec), track?.codec ?: unknown, Modifier.weight(1f))
                FactTile(
                    stringResource(R.string.aq_sample_rate),
                    track?.sampleRate?.let(LosslessDownloadInfo::formatKhz) ?: unknown,
                    Modifier.weight(1f)
                )
                FactTile(
                    stringResource(R.string.aq_bit_depth),
                    (track?.bitsPerSample ?: downloadedLossless?.bitsPerSample?.takeIf { it > 0 })
                        ?.let { stringResource(R.string.aq_bits, it) }
                        ?: if (track?.lossless == false) stringResource(R.string.aq_not_applicable) else unknown,
                    Modifier.weight(1f)
                )
                FactTile(
                    stringResource(R.string.aq_channels),
                    when (track?.channels) {
                        null -> unknown
                        1 -> stringResource(R.string.aq_mono)
                        2 -> stringResource(R.string.aq_stereo)
                        else -> stringResource(R.string.aq_channels_n, track?.channels ?: 0)
                    },
                    Modifier.weight(1f)
                )
                FactTile(
                    stringResource(R.string.aq_bitrate),
                    track?.bitrate?.let { stringResource(R.string.aq_kbps, String.format(Locale.US, "%,d", (it + 500) / 1000)) }
                        ?: stringResource(R.string.aq_variable),
                    Modifier.weight(1f)
                )
                FactTile(stringResource(R.string.aq_source), originLabel(origin), Modifier.weight(1f))
            }

            FrequencyCheckCard(
                reading = current,
                sampleRate = sampleRate,
                verdict = verdict,
                isPlaying = isPlaying,
                lossless = lossless
            )

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp).size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.aq_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The headline: the tier, the format in large type, and where it comes from. */
@Composable
private fun QualityHero(track: AudioTrackInfo?, origin: AudioOrigin, keptWhole: Boolean) {
    val lossless = track?.lossless == true
    val hiRes = lossless && ((track?.bitsPerSample ?: 16) > 16 || (track?.sampleRate ?: 44_100) > 48_000)
    val container by animateColorAsState(
        if (lossless) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "aqHeroContainer"
    )
    val content = if (lossless) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lossless) {
                    LosslessPill(hiRes = hiRes)
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.aq_lossy).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (keptWhole && (origin == AudioOrigin.LOSSLESS_SERVER || origin == AudioOrigin.LOSSLESS_KEPT)) {
                    Icon(
                        Icons.Rounded.OfflinePin,
                        contentDescription = stringResource(R.string.aq_kept),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = heroFormat(track),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(originIcon(origin), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (keptWhole && origin == AudioOrigin.LOSSLESS_SERVER) {
                        stringResource(R.string.aq_origin_server_kept)
                    } else originLabel(origin),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun heroFormat(track: AudioTrackInfo?): String {
    track ?: return stringResource(R.string.aq_nothing)
    val rate = track.sampleRate?.let(LosslessDownloadInfo::formatKhz)
    return when {
        track.lossless && track.bitsPerSample != null && rate != null -> "${track.bitsPerSample}-bit / $rate"
        track.lossless && rate != null -> "${track.codec} / $rate"
        track.bitrate != null -> "${track.codec} · ${(track.bitrate + 500) / 1000} kbps"
        else -> track.codec
    }
}

@Composable
private fun FactTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The frequency check: spectrum, ceiling, reference lines and the verdict. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FrequencyCheckCard(
    reading: SpectrumReading?,
    sampleRate: Int,
    verdict: QualityVerdict,
    isPlaying: Boolean,
    lossless: Boolean,
) {
    val good = verdict == QualityVerdict.GENUINE_HI_RES || verdict == QualityVerdict.LOSSLESS_CD
    val bad = verdict == QualityVerdict.LOSSY_SOURCE || verdict == QualityVerdict.UPSAMPLED_CD
    val tone by animateColorAsState(
        when {
            good -> MaterialTheme.colorScheme.tertiary
            bad -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        label = "aqTone"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = tone, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.aq_check_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                reading?.cutoffHz?.let { cutoff ->
                    Text(
                        text = stringResource(R.string.aq_sound_to, LosslessDownloadInfo.formatKhz(cutoff)),
                        style = MaterialTheme.typography.labelLarge,
                        color = tone
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            SpectrumChart(
                reading = reading,
                sampleRate = sampleRate,
                tone = tone,
                shadeAboveCutoff = bad,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )

            Spacer(Modifier.height(14.dp))

            if (verdict == QualityVerdict.LISTENING && lossless) {
                val progress by animateFloatAsState(
                    ((reading?.seconds ?: 0f) / SpectrumAnalysis.SECONDS_FOR_VERDICT).coerceIn(0f, 1f),
                    label = "aqProgress"
                )
                LinearWavyProgressIndicator(
                    progress = { progress },
                    color = tone,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            AnimatedContent(
                targetState = verdict to (reading == null && !isPlaying),
                transitionSpec = {
                    (fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.92f))
                        .togetherWith(fadeOut())
                },
                label = "aqVerdict"
            ) { (shown, waitingForPlay) ->
                VerdictRow(verdict = shown, waitingForPlay = waitingForPlay, tone = tone)
            }
        }
    }
}

@Composable
private fun VerdictRow(verdict: QualityVerdict, waitingForPlay: Boolean, tone: Color) {
    val (icon, title, body) = when {
        waitingForPlay -> Triple(Icons.Rounded.PlayCircle, R.string.aq_v_play_title, R.string.aq_v_play_body)
        else -> when (verdict) {
            QualityVerdict.LISTENING -> Triple(Icons.Rounded.HourglassTop, R.string.aq_v_listening_title, R.string.aq_v_listening_body)
            QualityVerdict.GENUINE_HI_RES -> Triple(Icons.Rounded.Verified, R.string.aq_v_hires_title, R.string.aq_v_hires_body)
            QualityVerdict.LOSSLESS_CD -> Triple(Icons.Rounded.Verified, R.string.aq_v_cd_title, R.string.aq_v_cd_body)
            QualityVerdict.UPSAMPLED_CD -> Triple(Icons.Rounded.WarningAmber, R.string.aq_v_upsampled_title, R.string.aq_v_upsampled_body)
            QualityVerdict.LOSSY_SOURCE -> Triple(Icons.Rounded.WarningAmber, R.string.aq_v_lossy_source_title, R.string.aq_v_lossy_source_body)
            QualityVerdict.LOSSY_STREAM -> Triple(Icons.Rounded.Info, R.string.aq_v_lossy_title, R.string.aq_v_lossy_body)
            QualityVerdict.INCONCLUSIVE -> Triple(Icons.Rounded.HelpOutline, R.string.aq_v_unsure_title, R.string.aq_v_unsure_body)
        }
    }
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tone.copy(alpha = 0.14f),
            contentColor = tone,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The averaged spectrum, 0 Hz to Nyquist, eased towards each new reading
 * every frame so the shape settles rather than jumps. Reference lines mark
 * 16 and 20 kHz (where lossy encoders stop), 22.05 kHz (the CD limit) and,
 * on a hi-res file, 24 kHz; the ceiling found is a solid line in the verdict's
 * colour, with the empty band above it shaded when it gives the file away.
 */
@Composable
private fun SpectrumChart(
    reading: SpectrumReading?,
    sampleRate: Int,
    tone: Color,
    shadeAboveCutoff: Boolean,
    modifier: Modifier = Modifier,
) {
    val bandCount = 120
    val target = remember(reading) {
        reading?.let { SpectrumAnalysis.bands(it.levelsDb, bandCount) }
    }
    var shown by remember { mutableStateOf(FloatArray(bandCount) { 0f }) }
    // Normalised 0..1 heights, eased each frame.
    LaunchedEffect(target) {
        val goal = target?.let { bands ->
            val top = bands.filter { it.isFinite() }.maxOrNull() ?: return@let null
            val range = 90f
            FloatArray(bandCount) { i -> ((bands[i] - (top - range)) / range).coerceIn(0f, 1f) }
        } ?: FloatArray(bandCount)
        while (true) {
            var settled = true
            withFrameNanos {
                val next = shown.copyOf()
                for (i in next.indices) {
                    val delta = goal[i] - next[i]
                    if (kotlin.math.abs(delta) > 0.002f) settled = false
                    next[i] += delta * 0.18f
                }
                shown = next
            }
            if (settled) break
        }
    }

    val nyquist = (reading?.sampleRate ?: sampleRate).takeIf { it > 0 }?.div(2) ?: 22_050
    val cutoff = reading?.cutoffHz
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val shade = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    val well = MaterialTheme.colorScheme.surfaceContainerHighest
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = label)
    val description = stringResource(R.string.aq_chart_description)

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        drawRoundRect(well, cornerRadius = CornerRadius(20.dp.toPx()))
        val left = 10.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val width = right - left
        fun x(hz: Int) = left + width * (hz.toFloat() / nyquist).coerceIn(0f, 1f)

        if (cutoff != null && shadeAboveCutoff) {
            drawRect(shade, topLeft = Offset(x(cutoff), top), size = Size(right - x(cutoff), bottom - top))
        }

        // Reference lines a fake gives itself away against.
        val references = buildList {
            add(16_000); add(20_000); add(22_050)
            if (nyquist > 24_000) add(24_000)
            if (nyquist > 40_000) add(40_000)
        }.filter { it < nyquist }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (hz in references) {
            drawLine(grid, Offset(x(hz), top), Offset(x(hz), bottom), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            val text = LosslessDownloadInfo.formatKhz(hz).removeSuffix(" kHz")
            val laid = measurer.measure(text, labelStyle)
            drawText(laid, topLeft = Offset(x(hz) - laid.size.width / 2f, 3.dp.toPx()))
        }

        // The spectrum as a filled shape.
        val bands = shown
        val path = Path().apply {
            moveTo(left, bottom)
            for (i in bands.indices) {
                val bx = left + width * (i + 0.5f) / bands.size
                lineTo(bx, bottom - (bottom - top) * bands[i])
            }
            lineTo(right, bottom)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(tone.copy(alpha = 0.85f), tone.copy(alpha = 0.12f)), top, bottom))
        drawPath(path, tone, style = Stroke(width = 1.5.dp.toPx()))

        if (cutoff != null) {
            drawLine(tone, Offset(x(cutoff), top - 4.dp.toPx()), Offset(x(cutoff), bottom), strokeWidth = 2.dp.toPx())
        }

        // Axis: 0, half way, Nyquist.
        listOf(0, nyquist / 2, nyquist).forEach { hz ->
            val text = LosslessDownloadInfo.formatKhz(hz)
            val laid = measurer.measure(text, labelStyle)
            val lx = (x(hz) - laid.size.width / 2f).coerceIn(left, right - laid.size.width)
            drawText(laid, topLeft = Offset(lx, bottom + 3.dp.toPx()))
        }
    }
}

@Composable
private fun originLabel(origin: AudioOrigin): String = stringResource(
    when (origin) {
        AudioOrigin.LOSSLESS_SERVER -> R.string.aq_origin_server
        AudioOrigin.LOSSLESS_KEPT -> R.string.aq_origin_kept
        AudioOrigin.LOSSLESS_DOWNLOAD -> R.string.aq_origin_lossless_download
        AudioOrigin.DOWNLOAD -> R.string.aq_origin_download
        AudioOrigin.DEVICE_FILE -> R.string.aq_origin_device
        AudioOrigin.YOUTUBE -> R.string.aq_origin_youtube
    }
)

private fun originIcon(origin: AudioOrigin): ImageVector = when (origin) {
    AudioOrigin.LOSSLESS_SERVER -> Icons.Rounded.Dns
    AudioOrigin.LOSSLESS_KEPT -> Icons.Rounded.OfflinePin
    AudioOrigin.LOSSLESS_DOWNLOAD, AudioOrigin.DOWNLOAD -> Icons.Rounded.DownloadDone
    AudioOrigin.DEVICE_FILE -> Icons.Rounded.Smartphone
    AudioOrigin.YOUTUBE -> Icons.Rounded.CloudDownload
}
