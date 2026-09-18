package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.bridge.buildSabrManifest
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class SabrManifestTest {
    private fun audio(id: Int = 140, track: String? = null, name: String? = null) = SabrFormat(
        SabrFormatId(id, 1, audioTrackId = track),
        "audio/mp4; codecs=\"mp4a.40.2\"", 128000, 210000,
        audioTrackDisplayName = name)
    private fun video() = SabrFormat(
        SabrFormatId(137, 2), "video/mp4; codecs=\"avc1.640020\"",
        4000000, 210000, width = 1920, height = 1080)

    private fun timeline(vararg durations: Int) =
        SabrFormatTimeline.parse(audio(), sidx(durations = durations))

    @Test fun `audio and video share one static manifest`() {
        val mpd = buildSabrManifest(audio(), timeline(1000, 2000), video(), timeline(1000, 2000), 210000)
        assertTrue(mpd.contains("profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\""))
        assertTrue(mpd.contains("mediaPresentationDuration=\"PT210.000S\""))
        assertTrue(mpd.contains("<BaseURL>sabrseg://a0/</BaseURL>"))
        assertTrue(mpd.contains("<BaseURL>sabrseg://v0/</BaseURL>"))
        assertTrue(mpd.contains("initialization=\"init\" media=\"\$Number\$\""))
        assertTrue(mpd.contains("codecs=\"mp4a.40.2\""))
        assertTrue(mpd.contains("codecs=\"avc1.640020\""))
        assertTrue(mpd.contains("width=\"1920\" height=\"1080\""))
        assertTrue(mpd.contains("<Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\"/>"))
        // Two segments per timeline: 0-1000 and 1000-3000.
        assertEquals(4, "<S t=\"".toRegex().findAll(mpd).count())
        assertTrue(mpd.contains("<S t=\"1000\" d=\"2000\"/>"))
    }

    @Test fun `audio only manifest carries no video`() {
        val mpd = buildSabrManifest(audio(), timeline(1000), durationMs = 3000)
        assertTrue(mpd.contains("contentType=\"audio\""))
        assertFalse(mpd.contains("contentType=\"video\""))
        assertFalse(mpd.contains("sabrseg://v0/"))
        assertTrue(mpd.contains("mediaPresentationDuration=\"PT3.000S\""))
    }

    @Test fun `labels and languages are escaped`() {
        val mpd = buildSabrManifest(
            audio(track = "en.dialog", name = "English & \"quoted\" <s>"),
            timeline(1000), durationMs = 1000)
        assertTrue(mpd.contains("lang=\"en\""))
        assertTrue(mpd.contains("<Label>English &amp; &quot;quoted&quot; &lt;s&gt;</Label>"))
    }

    @Test fun `mismatched tracks throw without guessing`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildSabrManifest(video(), timeline(1000), durationMs = 1000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildSabrManifest(audio(), timeline(1000), video(), null, 1000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildSabrManifest(audio(), timeline(1000), null, timeline(1000), 1000)
        }
    }

    private fun sidx(durations: IntArray): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).apply {
            writeInt(0); writeInt(1); writeInt(1000); writeInt(0); writeInt(0)
            writeShort(0); writeShort(durations.size)
            durations.forEach { writeInt(100); writeInt(it); writeInt(0) }
        }
        val body = payload.toByteArray()
        return ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(body.size + 8); writeBytes("sidx"); write(body)
            }
        }.toByteArray()
    }
}
