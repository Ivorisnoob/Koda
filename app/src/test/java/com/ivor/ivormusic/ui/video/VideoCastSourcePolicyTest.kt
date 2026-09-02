package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.data.VideoDynamicRange
import com.ivor.ivormusic.data.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class VideoCastSourcePolicyTest {

    @Test
    fun `local menu keeps split quality and cast menu keeps muxed quality`() {
        val split = splitQuality("360p")
        val muxed = muxedQuality("360p")
        val qualities = listOf(split, muxed)

        assertEquals(listOf(split), selectableVideoQualities(qualities, isCasting = false))
        assertEquals(listOf(muxed), selectableVideoQualities(qualities, isCasting = true))
    }

    @Test
    fun `local menu keeps HDR beside SDR while cast excludes HDR`() {
        val sdr = splitQuality("1080p60")
        val hdr = VideoQuality(
            resolution = "1080p60",
            url = "video-1080p60-hdr",
            format = "webm",
            audioUrl = "audio-1080p60-hdr",
            codec = "vp09.02.51.10",
            dynamicRange = VideoDynamicRange.HDR10,
        )

        assertEquals(
            listOf(sdr, hdr),
            selectableVideoQualities(listOf(sdr, hdr), isCasting = false),
        )
        assertEquals(
            emptyList<VideoQuality>(),
            selectableVideoQualities(listOf(hdr), isCasting = true),
        )
    }

    @Test
    fun `initial cast choice prefers broadly compatible mp4 over higher webm`() {
        val webm720 = muxedQuality("720p", format = "webm", codec = "vp9")
        val mp4360 = muxedQuality("360p")

        assertSame(
            mp4360,
            pickDefaultCastReceiverQuality(listOf(webm720, mp4360))
        )
    }

    @Test
    fun `initial cast choice prefers avc over higher hevc mp4`() {
        val hevc720 = muxedQuality("720p", codec = "hvc1.1.6.L93")
        val avc360 = muxedQuality("360p")

        assertSame(
            avc360,
            pickDefaultCastReceiverQuality(listOf(hevc720, avc360))
        )
    }

    @Test
    fun `cast recovery preserves preferred resolution only when it is safe`() {
        val split1080 = splitQuality("1080p")
        val muxed360 = muxedQuality("360p")

        assertSame(
            muxed360,
            pickDefaultCastReceiverQuality(
                listOf(split1080, muxed360),
                preferredResolution = "1080p",
            )
        )
    }

    @Test
    fun `vod dash is never used as silent fallback`() {
        val dash = VideoQuality("Auto (Best)", "dash", "DASH", isDASH = true)
        val split = splitQuality("1080p")

        assertNull(pickDefaultCastReceiverQuality(listOf(dash, split)))
        assertEquals(
            emptyList<VideoQuality>(),
            selectableVideoQualities(listOf(dash, split), isCasting = true)
        )
    }

    @Test
    fun `live cast exposes one hls auto option and excludes dash`() {
        val hlsAuto = liveQuality("Auto", "HLS")
        val hls720 = liveQuality("720p", "HLS")
        val dashAuto = liveQuality("Auto", "DASH")

        assertEquals(
            listOf(hlsAuto),
            selectableVideoQualities(
                listOf(hlsAuto, hls720, dashAuto),
                isCasting = true,
            )
        )
    }

    private fun splitQuality(resolution: String) = VideoQuality(
        resolution = resolution,
        url = "video-$resolution",
        format = "mp4",
        audioUrl = "audio-$resolution",
        codec = "avc1.640028",
    )

    private fun muxedQuality(
        resolution: String,
        format: String = "mp4",
        codec: String = "avc1.4d401e",
    ) = VideoQuality(
        resolution = resolution,
        url = "muxed-$resolution-$format",
        format = format,
        codec = codec,
    )

    private fun liveQuality(resolution: String, format: String) = VideoQuality(
        resolution = resolution,
        url = "live-$format",
        format = format,
        isDASH = true,
        isLive = true,
    )
}
