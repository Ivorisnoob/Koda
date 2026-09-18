package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrMintedToken
import com.ivor.ivormusic.data.youtube.sabr.model.parseSabrResolution
import com.ivor.ivormusic.data.youtube.sabr.model.validateSabrStreamingUrl
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SabrResolutionTest {
    private val token = SabrMintedToken(
        visitorData = "Cgt0ZXN0dj09",
        clientVersion = "2.20260917.01.00",
        poTokenBase64Url = "QUJD",
    )

    @Test fun `live envelope resolves url formats and lifetime`() {
        val resolution = parseSabrResolution(response(), "WKZO-CWeOVA", "cpn1", token, 1000)
        assertEquals("Cgt0ZXN0dj09", resolution.visitorData)
        assertEquals(1000 + 21540 * 1000, resolution.expiresAtMs)
        assertNull(resolution.ustreamerConfig)
        assertEquals(2, resolution.formats.size)
        val audio = resolution.formats.first { it.isAudio }
        assertEquals(140, audio.id.itag)
        assertEquals(1787395337470794, audio.id.lastModified)
        assertNull(audio.id.xtags)
        assertNull(audio.id.audioTrackId)
        assertEquals("audio/mp4", audio.mimeType)
        assertEquals(0L..758L, audio.initializationRange)
        assertEquals(759L..2000L, audio.indexRange)
        val video = resolution.formats.first { it.isVideo }
        assertEquals(137, video.id.itag)
        assertEquals(1920, video.width)
        assertEquals(1080, video.height)
        // Ciphered URLs stay unresolved until the signature decoder lands.
        assertNull(video.initializationUrl)
        assertFalse(resolution.toString().contains("Cgt0ZXN0dj09"))
    }

    @Test fun `ustreamer path resolves when the server sends it`() {
        val root = JSONObject(response().toString())
        root.getJSONObject("playerConfig").getJSONObject("mediaCommonConfig")
            .put("mediaUstreamerRequestConfig", JSONObject()
                .put("videoPlaybackUstreamerConfig", "ustreamer"))
        val resolution = parseSabrResolution(root, "WKZO-CWeOVA", "cpn1", token, 0)
        assertEquals("ustreamer", resolution.ustreamerConfig)
    }

    @Test fun `malformed formats are skipped but an empty ladder throws`() {
        val root = JSONObject(response().toString())
        val adaptive = root.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
        // Unknown itag-less entry, text track and negative range are skipped.
        adaptive.getJSONObject(0).remove("itag")
        adaptive.put(JSONObject().put("itag", 999).put("mimeType", "text/vtt"))
        adaptive.getJSONObject(1).getJSONObject("initRange").put("start", "9")
        val resolution = parseSabrResolution(root, "WKZO-CWeOVA", "cpn1", token, 0)
        assertEquals(1, resolution.formats.size)
        assertEquals(137, resolution.formats.single().id.itag)
        val empty = JSONObject(response().toString())
        empty.getJSONObject("streamingData").put("adaptiveFormats", org.json.JSONArray())
        assertThrows(SabrProtocolException::class.java) {
            parseSabrResolution(empty, "WKZO-CWeOVA", "cpn1", token, 0)
        }
    }

    @Test fun `envelope gaps throw without guessing`() {
        assertThrows(SabrProtocolException::class.java) {
            parseSabrResolution(JSONObject(), "v", "c", token, 0)
        }
        val noUrl = JSONObject(response().toString())
        noUrl.getJSONObject("streamingData").remove("serverAbrStreamingUrl")
        assertThrows(SabrProtocolException::class.java) {
            parseSabrResolution(noUrl, "v", "c", token, 0)
        }
        val noExpiry = JSONObject(response().toString())
        noExpiry.getJSONObject("streamingData").remove("expiresInSeconds")
        assertThrows(SabrProtocolException::class.java) {
            parseSabrResolution(noExpiry, "v", "c", token, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseSabrResolution(response(), "", "c", token, 0)
        }
    }

    @Test fun `streaming url validation matches the live minting`() {
        validateSabrStreamingUrl(
            "https://rr1---sn-example.googlevideo.com/videoplayback?c=MWEB&sparams=expire",
        )
        for (bad in listOf(
            "https://rr1---sn-example.googlevideo.com/videoplayback?c=ANDROID_VR",
            "http://rr1---sn-example.googlevideo.com/videoplayback?c=MWEB",
            "https://example.com/videoplayback?c=MWEB",
            "not a url",
        )) {
            assertThrows(SabrProtocolException::class.java) { validateSabrStreamingUrl(bad) }
        }
    }

    /** Shape of the 2026-09-18 anonymous MWEB /player: ciphered, ranged, token-less. */
    private fun response(): JSONObject = JSONObject(
        """{"responseContext":{"visitorData":"Cgt0ZXN0dj09"},"playerConfig":{"mediaCommonConfig":{}},""" +
            """"streamingData":{"expiresInSeconds":21540,""" +
            """"serverAbrStreamingUrl":"https://rr1---sn-example.googlevideo.com/videoplayback?c=MWEB",""" +
            """"adaptiveFormats":[""" +
            """{"itag":140,"mimeType":"audio/mp4","bitrate":131007,"approxDurationMs":"210000",""" +
            """"lastModified":"1787395337470794","initRange":{"start":"0","end":"758"},""" +
            """"indexRange":{"start":"759","end":"2000"},"signatureCipher":"s=ABC"},""" +
            """{"itag":137,"mimeType":"video/mp4","bitrate":4000000,"width":1920,"height":1080,""" +
            """"lastModified":"1787395413776269","initRange":{"start":"0","end":"742"},""" +
            """"indexRange":{"start":"743","end":"1134"},"signatureCipher":"s=DEF"}]}}""",
    )
}
