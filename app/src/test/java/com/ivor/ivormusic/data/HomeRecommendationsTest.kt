package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRecommendationsTest {

    @Test
    fun `placeholder rows are removed and fallback fills in order`() {
        val primary = listOf(song("bad", "None"), song("one", "First"))
        val fallback = listOf(song("one", "Duplicate"), song("two", "Second"), song("three", "Third"))

        assertEquals(
            listOf("one", "two", "three"),
            usableHomeRecommendations(listOf(primary, fallback)).map { it.id }
        )
    }

    @Test
    fun `recommendation pool stays bounded`() {
        val songs = (1..40).map { song(it.toString(), "Song $it") }
        assertEquals(30, usableHomeRecommendations(listOf(songs)).size)
    }

    private fun song(id: String, title: String) = Song.fromYouTube(
        videoId = id,
        title = title,
        artist = "Artist",
        album = "Album",
        duration = 0,
        thumbnailUrl = null
    )
}
