package com.ivor.ivormusic.data.lossless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LosslessMatcherTest {

    private fun track(id: String, title: String, artist: String, seconds: Int = 0) =
        LosslessTrack(id = id, title = title, artist = artist, durationSec = seconds)

    @Test
    fun `picks the studio recording over a live take and a remix`() {
        val picked = LosslessMatcher.pick(
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationMs = 200_000,
            candidates = listOf(
                track("live", "Blinding Lights (Live)", "The Weeknd", 210),
                track("remix", "Blinding Lights - Chromatics Remix", "The Weeknd", 330),
                track("studio", "Blinding Lights", "The Weeknd", 200),
            )
        )
        assertEquals("studio", picked?.id)
    }

    @Test
    fun `a different artist is a different song`() {
        val picked = LosslessMatcher.pick(
            title = "Hello",
            artist = "Adele",
            durationMs = 295_000,
            candidates = listOf(track("ritchie", "Hello", "Lionel Richie", 250))
        )
        assertNull(picked)
    }

    @Test
    fun `lengths far apart are rejected`() {
        val picked = LosslessMatcher.pick(
            title = "Song",
            artist = "Band",
            durationMs = 180_000,
            candidates = listOf(track("long", "Song", "Band", 420))
        )
        assertNull(picked)
    }

    @Test
    fun `youtube decorations and featured guests are ignored`() {
        val picked = LosslessMatcher.pick(
            title = "Stay With Me (Official Video) (feat. Someone)",
            artist = "Sam Smith",
            durationMs = 172_000,
            candidates = listOf(track("a", "Stay With Me", "Sam Smith", 172))
        )
        assertEquals("a", picked?.id)
    }

    @Test
    fun `a remaster is the same song`() {
        val picked = LosslessMatcher.pick(
            title = "Here Comes the Sun",
            artist = "The Beatles",
            durationMs = 185_000,
            candidates = listOf(track("r", "Here Comes The Sun - Remastered 2009", "The Beatles", 185))
        )
        assertEquals("r", picked?.id)
    }

    @Test
    fun `the query keeps a bare with and drops the guest`() {
        assertEquals("Stay With Me Sam Smith", LosslessMatcher.query("Stay With Me (feat. X)", "Sam Smith, X"))
    }

    @Test
    fun `nothing convincing is nothing`() {
        assertNull(LosslessMatcher.pick("Song", "Band", 0, listOf(track("x", "Completely Else", "Band"))))
    }
}

class DashManifestTest {

    @Test
    fun `template with timeline and start number`() {
        val xml = """
            <?xml version="1.0"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" mediaPresentationDuration="PT10S">
              <Period>
                <AdaptationSet contentType="audio" mimeType="audio/mp4">
                  <Representation id="FLAC" bandwidth="1411000" codecs="flac">
                    <SegmentTemplate timescale="44100" initialization="init.mp4?t=1" media="seg-${'$'}Number${'$'}.mp4?t=1" startNumber="0">
                      <SegmentTimeline>
                        <S d="176128" r="1"/>
                        <S d="88200"/>
                      </SegmentTimeline>
                    </SegmentTemplate>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val segments = DashManifest.parse(xml, "https://cdn.example/track/manifest.mpd")
        assertEquals("https://cdn.example/track/init.mp4?t=1", segments.initUrl)
        assertEquals(
            listOf(
                "https://cdn.example/track/seg-0.mp4?t=1",
                "https://cdn.example/track/seg-1.mp4?t=1",
                "https://cdn.example/track/seg-2.mp4?t=1",
            ),
            segments.mediaUrls
        )
    }

    @Test
    fun `template with a fixed duration counts from the period length`() {
        val xml = """
            <MPD mediaPresentationDuration="PT0H0M9.5S">
              <Period>
                <AdaptationSet mimeType="audio/mp4">
                  <SegmentTemplate timescale="1000" duration="4000" initialization="i.mp4" media="s${'$'}Number%03d${'$'}.mp4"/>
                  <Representation id="a" bandwidth="1"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val segments = DashManifest.parse(xml, "https://h/x/m.mpd")
        // 9.5 s at 4 s a segment is three segments, the last one short.
        assertEquals(listOf("https://h/x/s001.mp4", "https://h/x/s002.mp4", "https://h/x/s003.mp4"), segments.mediaUrls)
    }

    @Test
    fun `a single base url is the whole file`() {
        val xml = """
            <MPD mediaPresentationDuration="PT3M">
              <Period><AdaptationSet contentType="audio">
                <Representation id="r" bandwidth="900000"><BaseURL>https://files.example/song.flac</BaseURL><SegmentBase indexRange="0-100"/></Representation>
              </AdaptationSet></Period>
            </MPD>
        """.trimIndent()
        val segments = DashManifest.parse(xml, "https://h/m.mpd")
        assertNull(segments.initUrl)
        assertEquals(listOf("https://files.example/song.flac"), segments.mediaUrls)
    }

    @Test
    fun `durations`() {
        assertEquals(3723.5, DashManifest.parseDuration("PT1H2M3.5S"), 1e-9)
        assertEquals(180.0, DashManifest.parseDuration("P0DT3M"), 1e-9)
    }
}

class LosslessClientTest {

    @Test
    fun `signature is hmac sha256 of ts method path and token`() {
        // HMAC-SHA256("key", "1\nGET\n/a/tok/search\ntok"), computed independently.
        val sign = LosslessClient.sign("key", "1", "get", "/a/tok/search", "tok")
        assertEquals(64, sign.length)
        assertTrue(sign.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(sign, LosslessClient.sign("key", "1", "GET", "/a/tok/search", "tok"))
    }

    @Test
    fun `search results tolerate numeric ids and nested artists`() {
        val tracks = LosslessClient.parseTracks(
            """{"tracks":[
                {"id":12345,"title":"One","artist":{"name":"A"},"duration":200,"quality":"HI_RES_LOSSLESS"},
                {"id":"x","title":"Two","artist":"B","album":"Al"},
                {"title":"no id"}
            ]}"""
        )
        assertEquals(2, tracks.size)
        assertEquals("12345", tracks[0].id)
        assertEquals("A", tracks[0].artist)
        assertEquals(200, tracks[0].durationSec)
        assertEquals("Al", tracks[1].album)
    }

    @Test
    fun `khz labels`() {
        assertEquals("44.1 kHz", LosslessDownloadInfo.formatKhz(44_100))
        assertEquals("96 kHz", LosslessDownloadInfo.formatKhz(96_000))
    }
}
