package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.bridge.selectAudio
import com.ivor.ivormusic.data.youtube.sabr.bridge.selectVideo
import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId
import com.ivor.ivormusic.data.youtube.sabr.model.SabrIdentity
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SabrSelectionTest {
    private fun audio(bitrate: Int, id: Int = 140) =
        SabrFormat(SabrFormatId(id, 1), "audio/mp4", bitrate, 200000)
    private fun video(height: Int, bitrate: Int) =
        SabrFormat(SabrFormatId(1000 + height, 2), "video/mp4", bitrate, 200000, 0, height)

    private fun descriptor(vararg formats: SabrFormat) = SabrDescriptor(
        "video", "cpn-1", "2.20260901", "visitor",
        "https://rr1.googlevideo.com/videoplayback?c=MWEB&id=1",
        Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(1)),
        SabrIdentity("p", 1, 1), 0, Long.MAX_VALUE, formats.toList(), byteArrayOf(9))

    @Test fun `audio picks the highest bitrate`() {
        val descriptor = descriptor(audio(64000, 139), audio(128000, 140), video(720, 1000000))
        assertEquals(140, descriptor.selectAudio()?.id?.itag)
    }

    @Test fun `video caps at the device then falls back to the lowest`() {
        val descriptor = descriptor(
            audio(128000), video(1080, 4000000), video(720, 2000000), video(480, 1000000))
        assertEquals(720, descriptor.selectVideo(720)?.height)
        assertEquals(1080, descriptor.selectVideo(4320)?.height)
        assertEquals(480, descriptor.selectVideo(100)?.height)
    }

    @Test fun `unknown heights never win a capped pick`() {
        val descriptor = descriptor(audio(128000), video(0, 9000000), video(480, 1000000))
        assertEquals(480, descriptor.selectVideo(1080)?.height)
    }

    @Test fun `empty sides select nothing`() {
        val audioOnly = descriptor(audio(128000))
        assertNull(audioOnly.selectVideo(1080))
        val videoOnly = descriptor(video(720, 1000000))
        assertNull(videoOnly.selectAudio())
    }
}
