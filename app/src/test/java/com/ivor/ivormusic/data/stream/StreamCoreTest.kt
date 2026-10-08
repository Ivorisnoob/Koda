package com.ivor.ivormusic.data.stream

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reduced live October 2026 visionOS `/player` responses, captured by
 * `.probe/stream_fixtures.py`: a song, the same song asked for with no
 * visitorData, and a video with dubbed audio. Every URL in them is made up
 * and keeps only `itag`, `clen`, `dur`, `mime` and `c`.
 */
class StreamCoreTest {
    private fun text(name: String): String =
        javaClass.getResourceAsStream("/stream/$name.json")!!.bufferedReader().use { it.readText() }

    private fun streamingData(name: String): JSONObject =
        JSONObject(text(name)).getJSONObject("streamingData")

    private fun JSONObject.itagOfUrl(url: String?): Int? {
        val formats = getJSONArray("adaptiveFormats")
        return (0 until formats.length()).map(formats::getJSONObject)
            .firstOrNull { it.optString("url") == url }?.optInt("itag")
    }

    // --- readPlayerAnswer ---------------------------------------------------

    @Test fun `an OK response with streams is playable`() {
        val answer = readPlayerAnswer(200, text("player_ok"))
        assertTrue(answer is PlayerAnswer.Playable)
        assertEquals(6, (answer as PlayerAnswer.Playable).streamingData.getJSONArray("adaptiveFormats").length())
    }

    @Test fun `a request without visitorData is the bot check, not an unplayable video`() {
        val answer = readPlayerAnswer(200, text("player_bot_check"))
        assertTrue(answer is PlayerAnswer.IdentityRefused)
        answer as PlayerAnswer.IdentityRefused
        assertTrue(answer.reason!!.contains("not a bot"))
        assertNull(answer.root)
    }

    @Test fun `OK without streams refuses the identity and keeps the response`() {
        val body = JSONObject(text("player_ok")).apply { remove("streamingData") }.toString()
        val answer = readPlayerAnswer(200, body)
        assertTrue(answer is PlayerAnswer.IdentityRefused)
        assertNotNull((answer as PlayerAnswer.IdentityRefused).root)
        assertNotNull(answer.okRoot())
    }

    @Test fun `any other playability status is a verdict on the video`() {
        val body = JSONObject(text("player_bot_check")).apply {
            getJSONObject("playabilityStatus").put("status", "UNPLAYABLE").put("reason", "Video unavailable")
        }.toString()
        val answer = readPlayerAnswer(200, body)
        assertTrue(answer is PlayerAnswer.Unplayable)
        assertEquals("UNPLAYABLE", (answer as PlayerAnswer.Unplayable).status)
        assertNull(answer.okRoot())
    }

    @Test fun `an HTTP error, an empty body and a malformed body are failures`() {
        assertEquals(403, (readPlayerAnswer(403, text("player_ok")) as PlayerAnswer.Failed).httpCode)
        assertTrue(readPlayerAnswer(200, "") is PlayerAnswer.Failed)
        assertTrue(readPlayerAnswer(200, "<html>") is PlayerAnswer.Failed)
    }

    // --- pickAudio ----------------------------------------------------------

    @Test fun `each preference takes its own format`() {
        val data = streamingData("player_ok")
        fun itagFor(preference: AudioPreference) = data.itagOfUrl(pickAudio(data, preference)?.url)
        assertEquals(251, itagFor(AudioPreference.HIGHEST))
        assertEquals(140, itagFor(AudioPreference.BALANCED))
        assertEquals(139, itagFor(AudioPreference.LOWEST))
        assertFalse(pickAudio(data, AudioPreference.HIGHEST)!!.muxed)
    }

    @Test fun `a video-only adaptive format is never played as audio`() {
        val data = streamingData("player_ok")
        val formats = data.getJSONArray("adaptiveFormats")
        val videoOnly = (0 until formats.length()).map(formats::getJSONObject)
            .filter { it.getString("mimeType").startsWith("video") }
        data.put("adaptiveFormats", org.json.JSONArray(videoOnly))
        assertNull(pickAudio(data, AudioPreference.HIGHEST))
    }

    @Test fun `a muxed format stands in when there is no audio-only one`() {
        val data = streamingData("player_ok")
        val formats = data.getJSONArray("adaptiveFormats")
        val video = (0 until formats.length()).map(formats::getJSONObject)
            .first { it.getString("mimeType").startsWith("video") }
        data.put("adaptiveFormats", org.json.JSONArray())
        data.put("formats", org.json.JSONArray(listOf(video)))
        val pick = pickAudio(data, AudioPreference.HIGHEST)!!
        assertTrue(pick.muxed)
        assertEquals(video.getString("url"), pick.url)
    }

    @Test fun `a format without a plain url is not chosen`() {
        val data = streamingData("player_ok")
        val formats = data.getJSONArray("adaptiveFormats")
        (0 until formats.length()).map(formats::getJSONObject)
            .first { it.getInt("itag") == 251 }.remove("url")
        assertEquals(140, data.itagOfUrl(pickAudio(data, AudioPreference.HIGHEST)?.url))
    }

    // --- original soundtrack ------------------------------------------------

    private fun dubbedFormats(): List<JSONObject> {
        val formats = streamingData("player_dubbed").getJSONArray("adaptiveFormats")
        return (0 until formats.length()).map(formats::getJSONObject)
    }

    @Test fun `only the original soundtrack survives among dubs`() {
        val originals = originalTrackOnly(dubbedFormats())
        assertEquals(listOf(140, 251), originals.map { it.getInt("itag") })
        assertTrue(originals.all { it.getJSONObject("audioTrack").getBoolean("audioIsDefault") })
    }

    @Test fun `a response that is all dubs offers nothing rather than a dub`() {
        val dubs = dubbedFormats().filterNot { it.getJSONObject("audioTrack").getBoolean("audioIsDefault") }
        assertEquals(4, dubs.size)
        assertTrue(originalTrackOnly(dubs).isEmpty())
    }

    @Test fun `unlabelled formats are the single-track fallback`() {
        val formats = streamingData("player_ok").getJSONArray("adaptiveFormats")
        val audio = (0 until formats.length()).map(formats::getJSONObject)
            .filter { it.getString("mimeType").startsWith("audio") }
        assertEquals(5, audio.size)
        assertEquals(audio, originalTrackOnly(audio))
    }

    // --- downloads ----------------------------------------------------------

    @Test fun `downloads take only m4a, best or smallest`() {
        val data = streamingData("player_ok")
        assertEquals(listOf(139, 140), m4aAudioFormats(data).map { it.getInt("itag") })
        assertEquals(140, data.itagOfUrl(pickM4aUrl(data, smallest = false)))
        assertEquals(139, data.itagOfUrl(pickM4aUrl(data, smallest = true)))
    }

    @Test fun `a dubbed video downloads its original m4a`() {
        val formats = m4aAudioFormats(streamingData("player_dubbed"))
        assertEquals(1, formats.size)
        assertTrue(formats.single().getJSONObject("audioTrack").getString("displayName").contains("original"))
    }

    // --- lastByteOffset -----------------------------------------------------

    @Test fun `the probe asks for the last byte of a stream that states its length`() {
        val data = streamingData("player_ok")
        val url = pickAudio(data, AudioPreference.HIGHEST)!!.url
        assertEquals(3528471L - 1, lastByteOffset(url))
    }

    @Test fun `a short clip, an unsized url and a non-url prove nothing`() {
        val base = "https://rr1---sn-fixture.googlevideo.com/videoplayback?itag=251&c=VISIONOS"
        assertNull(lastByteOffset("$base&clen=500000&dur=45.000"))
        assertNull(lastByteOffset("$base&dur=221.181"))
        assertNull(lastByteOffset("$base&clen=3528471"))
        assertNull(lastByteOffset("$base&clen=0&dur=221.181"))
        assertNull(lastByteOffset("error://resolution_failed/abc"))
        assertEquals(99L, lastByteOffset("$base&clen=100&dur=120.000"))
    }

    // --- user agents --------------------------------------------------------

    @Test fun `a stream is fetched as the client that resolved it`() {
        val browser = "Browser/1.0"
        val url = pickAudio(streamingData("player_ok"), AudioPreference.HIGHEST)!!.url
        assertEquals(PlayerClients.VISION_OS.userAgent, userAgentForStreamUrl(url, browser))
        assertEquals(PlayerClients.ANDROID_VR.userAgent, userAgentForStreamClient("android_vr", browser))
        assertEquals(PlayerClients.IOS.userAgent, userAgentForStreamClient("IOS", browser))
        assertEquals(browser, userAgentForStreamClient("WEB_REMIX", browser))
        assertEquals(browser, userAgentForStreamClient(null, browser))
        assertEquals(browser, userAgentForStreamUrl("not a url", browser))
    }
}
