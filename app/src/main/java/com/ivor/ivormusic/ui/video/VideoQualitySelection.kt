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
internal fun localVideoQualityOptions(qualities: List<VideoQuality>): List<VideoQuality> =
    qualities
        .groupBy(VideoQuality::resolution)
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
