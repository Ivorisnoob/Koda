package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualityVariantsTest {

    @Test
    fun `deduplication preserves split and muxed variants at the same resolution`() {
        val split = splitQuality("360p")
        val muxed = muxedQuality("360p")

        val result = deduplicateVideoQualityVariants(listOf(split, muxed))

        assertEquals(listOf(split, muxed), result)
    }

    @Test
    fun `deduplication still collapses codec alternatives within one delivery type`() {
        val webm = splitQuality("1080p", format = "webm", codec = "vp9")
        val mp4 = splitQuality("1080p", format = "mp4", codec = "avc1.640028")

        assertEquals(
            listOf(mp4),
            deduplicateVideoQualityVariants(listOf(webm, mp4))
        )
    }

    @Test
    fun `only muxed vod and live hls are Default Receiver compatible`() {
        assertTrue(muxedQuality("360p").isDefaultCastReceiverCompatible)
        assertFalse(splitQuality("1080p").isDefaultCastReceiverCompatible)
        assertFalse(
            VideoQuality("Auto", "dash", "DASH", isDASH = true)
                .isDefaultCastReceiverCompatible
        )
        assertTrue(
            VideoQuality("Auto", "hls", "HLS", isDASH = true, isLive = true)
                .isDefaultCastReceiverCompatible
        )
        assertFalse(
            VideoQuality("Auto", "dash", "DASH", isDASH = true, isLive = true)
                .isDefaultCastReceiverCompatible
        )
    }

    private fun splitQuality(
        resolution: String,
        format: String = "mp4",
        codec: String = "avc1.4d401f",
    ) = VideoQuality(
        resolution = resolution,
        url = "video-$resolution-$format",
        format = format,
        audioUrl = "audio-$resolution",
        codec = codec,
    )

    private fun muxedQuality(resolution: String) = VideoQuality(
        resolution = resolution,
        url = "muxed-$resolution",
        format = "mp4",
        codec = "avc1.4d401e",
    )
}
