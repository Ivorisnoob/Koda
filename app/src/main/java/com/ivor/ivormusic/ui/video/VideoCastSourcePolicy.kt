package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.VideoStreamDelivery

/**
 * Playback-quality projection shared by the initial load, the quality sheet
 * and every source recovery. Keeping it outside Compose prevents the UI from
 * advertising a source that the playback boundary will later reject.
 */
internal fun selectableVideoQualities(
    qualities: List<VideoQuality>,
    isCasting: Boolean,
): List<VideoQuality> {
    if (qualities.isEmpty()) return emptyList()
    if (!isCasting) return localVideoQualityOptions(qualities)

    val compatible = defaultCastReceiverQualityOptions(qualities)
    if (compatible.firstOrNull()?.isLive != true) return compatible

    // Every live rung is the same HLS master playlist. The receiver owns ABR
    // and the sender has no remote track-selector cap, so only Auto is honest.
    return compatible.filter {
        it.resolution.startsWith("Auto", ignoreCase = true) ||
            it.resolution.takeWhile(Char::isDigit).isEmpty()
    }.take(1)
}

/** One local-playback entry per label and dynamic range, preferring split. */
internal fun localVideoQualityOptions(qualities: List<VideoQuality>): List<VideoQuality> =
    qualities
        .groupBy { it.resolution to it.dynamicRange }
        .mapNotNull { (_, variants) ->
            variants.maxWithOrNull(
                compareBy<VideoQuality> {
                    when (it.delivery) {
                        VideoStreamDelivery.SPLIT_VIDEO_AUDIO -> 2
                        VideoStreamDelivery.MUXED_PROGRESSIVE -> 1
                        VideoStreamDelivery.ADAPTIVE_MANIFEST -> 0
                    }
                }.thenBy { if (it.isMp4Container) 1 else 0 }
            )
        }

/** One Default Receiver-safe entry per visible label. */
internal fun defaultCastReceiverQualityOptions(
    qualities: List<VideoQuality>
): List<VideoQuality> = qualities
    .asSequence()
    .filter(VideoQuality::isDefaultCastReceiverCompatible)
    .groupBy(VideoQuality::resolution)
    .mapNotNull { (_, variants) ->
        // Prefer MP4 for the broadest Cast hardware coverage. A non-MP4 muxed
        // stream remains a valid last resort when it is all the extractor has.
        variants.maxWithOrNull(
            compareBy<VideoQuality> { if (it.isMp4Container) 1 else 0 }
                .thenBy { if (it.codec?.contains("avc", ignoreCase = true) == true) 1 else 0 }
        )
    }

/**
 * Pick a receiver-safe source, preserving a requested label when possible.
 * MP4 beats a higher WebM rung for the initial choice because older Cast
 * hardware has a narrower codec matrix than the phone.
 */
internal fun pickDefaultCastReceiverQuality(
    qualities: List<VideoQuality>,
    preferredResolution: String? = null,
): VideoQuality? {
    val options = defaultCastReceiverQualityOptions(qualities)
    preferredResolution?.let { preferred ->
        options.firstOrNull { it.resolution == preferred }?.let { return it }
    }
    return options.firstOrNull { it.isLive }
        ?: options.firstOrNull {
            it.isMp4Container && it.codec.orEmpty().let { codec ->
                codec.isBlank() || codec.contains("avc", ignoreCase = true) ||
                    codec.contains("h264", ignoreCase = true)
            }
        }
        ?: options.firstOrNull { it.isMp4Container }
        ?: options.firstOrNull()
}
