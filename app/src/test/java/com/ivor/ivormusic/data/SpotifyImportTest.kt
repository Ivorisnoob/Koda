package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyImportTest {

    @Test
    fun `links are read from a shared message in every shape`() {
        val id = "37i9dQZF1DXcBWIGoYBM5M"
        assertEquals(
            SpotifyRef("playlist", id),
            SpotifyLinks.parse("Listen to this https://open.spotify.com/playlist/$id?si=abc")
        )
        assertEquals(
            SpotifyRef("playlist", id),
            SpotifyLinks.parse("https://open.spotify.com/intl-de/playlist/$id")
        )
        assertEquals(SpotifyRef("album", id), SpotifyLinks.parse("spotify:album:$id"))
        assertNull(SpotifyLinks.parse("https://open.spotify.com/track/$id"))
        assertNull(SpotifyLinks.parse("https://music.youtube.com/playlist?list=PL123"))
    }

    @Test
    fun `the embed page is read into tracks`() {
        val html = """<html><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{
              "type":"playlist","name":"Road trip","subtitle":"Sam",
              "trackList":[
                {"title":"First","subtitle":"A, B","duration":200000,"entityType":"track"},
                {"title":"A podcast","subtitle":"Host","duration":900000,"entityType":"episode"},
                {"title":"Second","subtitle":"C","duration":181270,"entityType":"track"}
              ]}}}}}}
            </script></html>"""
        val collection = SpotifyEmbedParser.parse(html)!!
        assertEquals("Road trip", collection.name)
        assertEquals("Sam", collection.owner)
        assertEquals(listOf("First", "Second"), collection.tracks.map { it.title })
        assertEquals(181270L, collection.tracks[1].durationMs)
        assertFalse(collection.mayBeTruncated)
    }

    @Test
    fun `a page with no playlist in it reads as nothing`() {
        assertNull(SpotifyEmbedParser.parse("<html>not found</html>"))
    }

    @Test
    fun `the same recording matches across punctuation accents and guest credits`() {
        val track = SpotifyTrack("Déjà Vu (feat. JAY-Z)", "Beyoncé, JAY-Z", 240_000)
        assertTrue(SpotifyMatcher.isExact(track, song("Deja Vu", "Beyoncé & JAY-Z", 241_000)))
        assertTrue(
            SpotifyMatcher.isExact(
                SpotifyTrack("Song - Live", "Band", 200_000),
                song("Song (Live)", "Band", 200_000)
            )
        )
    }

    @Test
    fun `a different version a different artist or a different length is not a match`() {
        val track = SpotifyTrack("Song", "Band", 200_000)
        assertFalse(SpotifyMatcher.isExact(track, song("Song (Live)", "Band", 200_000)))
        assertFalse(SpotifyMatcher.isExact(track, song("Song", "Tribute Band Lowell", 200_000)))
        assertFalse(SpotifyMatcher.isExact(track, song("Song", "Band", 215_000)))
        assertFalse(SpotifyMatcher.isExact(track, song("Song", "Band", 0)))
        assertFalse(SpotifyMatcher.isExact(SpotifyTrack("Song", "Low", 200_000), song("Song", "Lowell", 200_000)))
    }

    @Test
    fun `the first exact match in search order is taken`() {
        val track = SpotifyTrack("Song", "Band", 200_000)
        val picked = SpotifyMatcher.pick(
            track,
            listOf(
                song("Song (Karaoke)", "Band", 200_000, "karaoke"),
                song("Song", "Band", 200_500, "right"),
                song("Song", "Band", 200_000, "later")
            )
        )
        assertEquals("right", picked?.id)
    }

    private fun song(title: String, artist: String, duration: Long, id: String = "id") =
        Song.fromYouTube(
            videoId = id,
            title = title,
            artist = artist,
            album = "",
            duration = duration,
            thumbnailUrl = null
        )
}
