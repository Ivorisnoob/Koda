package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
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
    fun `deduplication preserves SDR and HDR at the same resolution`() {
        val sdr = splitQuality("1080p60")
        val hdr = splitQuality(
            "1080p60",
            format = "webm",
            codec = "vp9.2",
            dynamicRange = VideoDynamicRange.HDR10,
        )

        assertEquals(
            listOf(hdr, sdr),
            deduplicateVideoQualityVariants(listOf(sdr, hdr))
        )
        assertEquals("1080p60 HDR", hdr.displayLabel)
    }

    private fun splitQuality(
        resolution: String,
        format: String = "mp4",
        codec: String = "avc1.4d401f",
        dynamicRange: VideoDynamicRange = VideoDynamicRange.SDR,
    ) = VideoQuality(
        resolution = resolution,
        url = "video-$resolution-$format",
        format = format,
        audioUrl = "audio-$resolution",
        codec = codec,
        dynamicRange = dynamicRange,
    )

    private fun muxedQuality(resolution: String) = VideoQuality(
        resolution = resolution,
        url = "muxed-$resolution",
        format = "mp4",
        codec = "avc1.4d401e",
    )
}
