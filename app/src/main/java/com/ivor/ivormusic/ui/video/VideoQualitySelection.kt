package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.VideoStreamDelivery

/**
 * Playback-quality projection shared by the initial load, the quality sheet
 * and every source recovery. Keeping it outside Compose prevents the UI from
 * advertising a source that the playback boundary will later reject.
 *
 * One entry per visible label, preferring the split source: a video-only rung
 * merged with its audio track is higher fidelity than the muxed file carrying
 * the same label, and local playback can merge where a single-URL consumer
 * cannot.
 */
/** One video rendition a live HLS master playlist declares, as the player parsed it. */
internal data class LiveRendition(val width: Int, val height: Int, val frameRate: Float)

/**
 * The live quality ladder, built from the renditions the loaded HLS master
 * playlist declares rather than from the resolver's response.
 *
 * [scar] NewPipe resolves first, and for a live stream it exposes the manifest
 * URL and nothing else, so the menu showed a lone "Auto (HLS)" even though the
 * master playlist lists every rung from 144p up [verified September 2026]. The
 * player has already parsed that playlist by the time it publishes tracks, so
 * the ladder costs no request and describes exactly what can be selected.
 *
 * Labels follow YouTube's convention of naming the short edge, so a 720x1280
 * vertical broadcast reads "720p" rather than "1280p". Each rung keeps its real
 * dimensions and frame rate, which is what the track cap pins against. Returns
 * just [auto] when the playlist declares fewer than two usable rungs - one
 * rendition is not a choice.
 */
internal fun liveVideoQualityLadder(
    auto: VideoQuality,
    renditions: List<LiveRendition>,
): List<VideoQuality> {
    val autoEntry = auto.copy(resolution = "Auto", width = 0, height = 0, frameRate = 0)
    val rungs = renditions
        .filter { it.width > 0 && it.height > 0 }
        .map { rendition ->
            val shortEdge = minOf(rendition.width, rendition.height)
            val fps = if (rendition.frameRate > 0f) Math.round(rendition.frameRate) else 0
            autoEntry.copy(
                resolution = if (fps >= 50) "${shortEdge}p$fps" else "${shortEdge}p",
                width = rendition.width,
                height = rendition.height,
                frameRate = fps,
            )
        }
        // A label can repeat across codecs or dynamic ranges; the largest
        // frame of it is the one the cap would land on anyway.
        .groupBy { it.resolution }
        .map { (_, same) -> same.maxBy { it.width * it.height } }
        .sortedWith(
            compareByDescending<VideoQuality> { minOf(it.width, it.height) }
                .thenByDescending { it.frameRate }
        )
    return if (rungs.size < 2) listOf(autoEntry) else listOf(autoEntry) + rungs
}

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
