package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import com.ivor.ivormusic.data.lossless.LosslessDownloadInfo
import com.ivor.ivormusic.ui.components.LosslessPill

/**
 * What the player is decoding, when it is lossless.
 *
 * Read off the selected audio track rather than off where the song came from,
 * so it is true for every way lossless audio reaches the player: a stream from
 * the listener's lossless server, a lossless download, and a FLAC, ALAC or WAV
 * in the device library. A YouTube stream is never lossless, so it never
 * shows.
 */
data class PlaybackAudioQuality(
    val hiRes: Boolean,
    /** "24-bit · 96 kHz", "96 kHz", or null when the track says neither. */
    val detail: String?,
) {
    companion object {
        private val LOSSLESS_MIME_TYPES = setOf(
            MimeTypes.AUDIO_FLAC,
            MimeTypes.AUDIO_ALAC,
            MimeTypes.AUDIO_WAV,
            MimeTypes.AUDIO_RAW,
            "audio/x-flac",
        )

        /** The quality of the selected audio track, or null when it is lossy or unknown. */
        fun from(tracks: Tracks): PlaybackAudioQuality? {
            val format = tracks.groups
                .asSequence()
                .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                .flatMap { group ->
                    (0 until group.length).asSequence()
                        .filter(group::isTrackSelected)
                        .map(group::getTrackFormat)
                }
                .firstOrNull() ?: return null
            return from(format)
        }

        fun from(format: Format): PlaybackAudioQuality? {
            val mime = format.sampleMimeType ?: return null
            if (mime !in LOSSLESS_MIME_TYPES) return null
            val bits = when (format.pcmEncoding) {
                C.ENCODING_PCM_8BIT -> 8
                C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
                C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
                else -> null
            }
            val rate = format.sampleRate.takeIf { it != Format.NO_VALUE && it > 0 }
            val detail = when {
                bits != null && rate != null -> "$bits-bit · ${LosslessDownloadInfo.formatKhz(rate)}"
                rate != null -> LosslessDownloadInfo.formatKhz(rate)
                else -> null
            }
            return PlaybackAudioQuality(
                hiRes = (bits ?: 16) > 16 || (rate ?: 44_100) > 48_000,
                detail = detail,
            )
        }
    }
}

/**
 * Everything the selected audio track says about itself, for the quality
 * sheet. Any track, lossy or not: the sheet is also where a YouTube stream is
 * shown for what it is.
 */
data class AudioTrackInfo(
    val codec: String,
    val lossless: Boolean,
    val sampleRate: Int?,
    val bitsPerSample: Int?,
    val channels: Int?,
    /** Bits per second, when the container or manifest declares it. */
    val bitrate: Int?,
) {
    companion object {
        fun from(tracks: Tracks): AudioTrackInfo? {
            val format = tracks.groups
                .asSequence()
                .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                .flatMap { group ->
                    (0 until group.length).asSequence()
                        .filter(group::isTrackSelected)
                        .map(group::getTrackFormat)
                }
                .firstOrNull() ?: return null
            val mime = format.sampleMimeType ?: return null
            val quality = PlaybackAudioQuality.from(format)
            return AudioTrackInfo(
                codec = codecName(mime),
                lossless = quality != null,
                sampleRate = format.sampleRate.takeIf { it != Format.NO_VALUE && it > 0 },
                bitsPerSample = bitsOf(format.pcmEncoding),
                channels = format.channelCount.takeIf { it != Format.NO_VALUE && it > 0 },
                bitrate = listOf(format.averageBitrate, format.bitrate, format.peakBitrate)
                    .firstOrNull { it != Format.NO_VALUE && it > 0 },
            )
        }

        private fun codecName(mime: String): String = when (mime) {
            MimeTypes.AUDIO_FLAC, "audio/x-flac" -> "FLAC"
            MimeTypes.AUDIO_ALAC -> "ALAC"
            MimeTypes.AUDIO_WAV -> "WAV"
            MimeTypes.AUDIO_RAW -> "PCM"
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_VORBIS -> "Vorbis"
            MimeTypes.AUDIO_MPEG -> "MP3"
            MimeTypes.AUDIO_AC3 -> "Dolby Digital"
            MimeTypes.AUDIO_E_AC3 -> "Dolby Digital Plus"
            else -> mime.substringAfter('/').uppercase()
        }

        internal fun bitsOf(encoding: Int): Int? = when (encoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
            else -> null
        }
    }
}

/**
 * The playing song's quality, provided once by [ExpandablePlayer] around
 * every style - including the landscape layout - so a style places
 * [PlayerAudioQualityBadge] and has nothing to thread.
 */
internal val LocalPlaybackAudioQuality = staticCompositionLocalOf<PlaybackAudioQuality?> { null }

/** Opens the Audio quality sheet; provided beside [LocalPlaybackAudioQuality]. */
internal val LocalOpenAudioQuality = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * The lossless pill as every player draws it: there while a lossless track is
 * playing, springing in and fading out, and taking no room otherwise. The last
 * quality shown is held through the exit, so the pill does not empty itself
 * as it leaves.
 */
@Composable
internal fun PlayerAudioQualityBadge(
    modifier: Modifier = Modifier,
    showDetail: Boolean = true,
) {
    val quality = LocalPlaybackAudioQuality.current
    // A plain holder, not state: it only remembers what to draw on the way out.
    val last = remember { arrayOfNulls<PlaybackAudioQuality>(1) }
    if (quality != null) last[0] = quality
    AnimatedVisibility(
        visible = quality != null,
        modifier = modifier,
        // The room opens with it, so the title block grows rather than jumps.
        enter = expandIn(spring(stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Center) +
            scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) +
            fadeIn(),
        exit = shrinkOut(shrinkTowards = Alignment.Center) + scaleOut(targetScale = 0.8f) + fadeOut(),
    ) {
        (quality ?: last[0])?.let { current ->
            val open = LocalOpenAudioQuality.current
            val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
            LosslessPill(
                hiRes = current.hiRes,
                detail = current.detail.takeIf { showDetail },
                // A tap asks the obvious question: is it really?
                modifier = if (open == null) Modifier else Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClickLabel = androidx.compose.ui.res.stringResource(com.ivor.ivormusic.R.string.aq_open)) {
                        haptics.tick()
                        open()
                    },
            )
        }
    }
}
