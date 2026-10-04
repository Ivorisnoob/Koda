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

    @Test
    fun `a refresh leads with songs that have not led`() {
        val songs = (1..6).map { song(it.toString(), "Song $it") }
        assertEquals(
            listOf("3", "4", "5", "6", "1", "2"),
            rotateHomeRecommendations(songs, listOf("1", "2")).map { it.id }
        )
    }

    @Test
    fun `once every song has led the longest ago leads again`() {
        val songs = (1..4).map { song(it.toString(), "Song $it") }
        assertEquals(
            listOf("3", "4", "1", "2"),
            rotateHomeRecommendations(songs, listOf("3", "4", "1", "2")).map { it.id }
        )
    }

    @Test
    fun `nothing has led yet keeps the fetched order`() {
        val songs = (1..3).map { song(it.toString(), "Song $it") }
        assertEquals(songs, rotateHomeRecommendations(songs, emptyList()))
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
