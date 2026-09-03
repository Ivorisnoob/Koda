package com.ivor.ivormusic.data

enum class VideoStreamDelivery {
    ADAPTIVE_MANIFEST,
    SPLIT_VIDEO_AUDIO,
    MUXED_PROGRESSIVE,
}

/**
 * Collapse codec/container alternatives without collapsing delivery types.
 * A muxed 360p file and a video-only 360p file are not interchangeable: local
 * playback merges the latter with [VideoQuality.audioUrl], while a download
 * wants the self-contained file. Keeping one of each is what lets playback and
 * download selection each choose safely.
 */
internal fun deduplicateVideoQualityVariants(
    qualities: List<VideoQuality>
): List<VideoQuality> {
    fun height(label: String): Int = label.takeWhile(Char::isDigit).toIntOrNull() ?: 0
    fun fps(label: String): Int =
        label.substringAfter('p', "").takeWhile(Char::isDigit).toIntOrNull() ?: 30

    return qualities
        .groupBy { Triple(it.resolution, it.delivery, it.dynamicRange) }
        .mapNotNull { (_, variants) ->
            variants.maxWithOrNull(
                compareBy<VideoQuality>(
                    { if (it.isMp4DownloadCompatible) 2 else if (it.isMp4Container) 1 else 0 },
                    { if (it.codec?.contains("avc1", ignoreCase = true) == true) 1 else 0 },
                )
            )
        }
        .sortedWith(
            compareByDescending<VideoQuality> { height(it.resolution) }
                .thenByDescending { fps(it.resolution) }
                .thenByDescending { if (it.isHdr) 1 else 0 }
                // Keep the higher-fidelity split entry first for local playback
                // when two delivery types share the same visible label.
                .thenByDescending {
                    when (it.delivery) {
                        VideoStreamDelivery.SPLIT_VIDEO_AUDIO -> 2
                        VideoStreamDelivery.MUXED_PROGRESSIVE -> 1
                        VideoStreamDelivery.ADAPTIVE_MANIFEST -> 0
                    }
                }
        )
}
