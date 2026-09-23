package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsQueryTest {

    private fun request(title: String, artist: String) =
        LyricsRequest(songId = "id", title = title, artist = artist, album = "", durationMs = 200_000L)

    private fun cleaned(title: String, artist: String) =
        request(title, artist).cleanedForSearch()?.let { it.title to it.artist }

    @Test
    fun `clean metadata needs no second pass`() {
        assertNull(cleaned("Yellow", "Coldplay"))
    }

    @Test
    fun `upload decoration is stripped`() {
        assertEquals("Yellow" to "Coldplay", cleaned("Coldplay - Yellow (Official Video)", "ColdplayVEVO"))
        assertEquals("Blinding Lights" to "The Weeknd", cleaned("Blinding Lights [Official Audio] | 4K", "The Weeknd - Topic"))
    }

    @Test
    fun `featuring credits leave title and artist`() {
        assertEquals("Stay" to "The Kid LAROI", cleaned("Stay (feat. Justin Bieber)", "The Kid LAROI, Justin Bieber"))
    }

    @Test
    fun `version markers that change the lyric are kept`() {
        assertEquals("Yellow (Live)" to "Coldplay", cleaned("Yellow (Live) (Official Video)", "Coldplay"))
    }

    @Test
    fun `a dash title under an unrelated channel is left alone`() {
        assertEquals(
            "Part One - Part Two" to "Some Band",
            cleaned("Part One - Part Two (Lyrics)", "Some Band"),
        )
    }
}
