package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrVideoSupportTest {
    @Test
    fun `youtube PQ spellings map to HDR10`() {
        assertEquals(
            VideoDynamicRange.HDR10,
            youtubeVideoDynamicRange(
                "1080p60 HDR",
                "COLOR_TRANSFER_CHARACTERISTICS_SMPTEST2084",
            )
        )
        assertEquals(
            VideoDynamicRange.HDR10,
            youtubeVideoDynamicRange(
                "1080p60",
                "COLOR_TRANSFER_CHARACTERISTICS_SMPTE2084",
            )
        )
    }

    @Test
    fun `youtube HLG transfer maps to HLG`() {
        assertEquals(
            VideoDynamicRange.HLG,
            youtubeVideoDynamicRange(
                "2160p60 HDR",
                "COLOR_TRANSFER_CHARACTERISTICS_ARIB_STD_B67",
            )
        )
    }

    @Test
    fun `HDR suffix is presentation rather than resolution identity`() {
        assertEquals("1440p60", normalizedVideoQualityLabel("1440p60 HDR"))
        assertEquals("720p", normalizedVideoQualityLabel("720p"))
    }

    @Test
    fun `HDR failure falls back to SDR at the same quality`() {
        val failed = quality("1080p60", VideoDynamicRange.HDR10)
        val sameHeight = quality("1080p60")
        val lower = quality("720p")

        assertEquals(sameHeight, bestSdrFallback(listOf(lower, sameHeight, failed), failed))
    }

    @Test
    fun `direct parser admits visionOS HDR itags without a hardcoded itag table`() {
        val streamingData = JSONObject()
            .put("formats", JSONArray())
            .put(
                "adaptiveFormats",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("itag", 140)
                            .put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                            .put("bitrate", 129_000)
                            .put("url", "https://audio.example/140")
                    )
                    .put(
                        JSONObject()
                            .put("itag", 299)
                            .put("qualityLabel", "1080p60")
                            .put("mimeType", "video/mp4; codecs=\"avc1.64002a\"")
                            .put("bitrate", 5_000_000)
                            .put("url", "https://video.example/299")
                            .put("width", 1920)
                            .put("height", 1080)
                            .put("fps", 60)
                    )
                    .put(
                        JSONObject()
                            .put("itag", 337)
                            .put("qualityLabel", "2160p60 HDR")
                            .put("mimeType", "video/webm; codecs=\"vp09.02.51.10.01.09.16.09.01\"")
                            .put("bitrate", 20_000_000)
                            .put("url", "https://video.example/337")
                            .put("width", 3840)
                            .put("height", 2160)
                            .put("fps", 60)
                            .put(
                                "colorInfo",
                                JSONObject().put(
                                    "transferCharacteristics",
                                    "COLOR_TRANSFER_CHARACTERISTICS_SMPTEST2084",
                                )
                            )
                    )
            )

        val qualities = parseDirectVideoQualities(streamingData, includeHdr = true)
        val hdr = qualities.single { it.dynamicRange == VideoDynamicRange.HDR10 }

        assertEquals("2160p60", hdr.resolution)
        assertEquals("2160p60 HDR", hdr.displayLabel)
        assertEquals("vp09.02.51.10.01.09.16.09.01", hdr.codec)
        assertEquals(3840, hdr.width)
        assertEquals(2160, hdr.height)
        assertEquals(60, hdr.frameRate)
        assertEquals("https://audio.example/140", hdr.audioUrl)
        assertTrue(qualities.any { !it.isHdr })

        val sdrOnly = parseDirectVideoQualities(streamingData, includeHdr = false)
        assertTrue(sdrOnly.isNotEmpty())
        assertTrue(sdrOnly.none(VideoQuality::isHdr))
    }

    private fun quality(
        resolution: String,
        dynamicRange: VideoDynamicRange = VideoDynamicRange.SDR,
    ) = VideoQuality(
        resolution = resolution,
        url = "$resolution-${dynamicRange.name}",
        format = "webm",
        audioUrl = "audio",
        codec = if (dynamicRange == VideoDynamicRange.SDR) "vp9" else "vp9.2",
        dynamicRange = dynamicRange,
    )
}
