package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentAlbumsTest {

    private var clock = 1_000L

    private fun play(
        songId: String,
        album: String,
        artist: String = "Artist",
        albumId: String? = null,
        source: SongSource = SongSource.YOUTUBE,
    ) = PlayHistoryEntry(
        songId = songId, title = "Song $songId", artist = artist, album = album,
        timestamp = clock--, duration = 180_000L, thumbnailUrl = "art-$songId",
        source = source, albumId = albumId,
    )

    @Test
    fun albumsComeOutInLastPlayedOrderOneCardEach() {
        val history = listOf(play("a1", "Alpha"), play("b1", "Beta"), play("a2", "Alpha"), play("a1", "Alpha"))
        val albums = recentAlbumsFrom(history)
        assertEquals(listOf("Alpha", "Beta"), albums.map { it.title })
        assertEquals(listOf("a1", "a2"), albums[0].tracks.map { it.songId })
    }

    @Test
    fun oldPlaysWithoutAnIdFoldIntoTheCardANewerPlayIdentified() {
        val history = listOf(play("a2", "Alpha", albumId = "MPREb_alpha"), play("a1", "Alpha"))
        val albums = recentAlbumsFrom(history)
        assertEquals(1, albums.size)
        assertEquals("MPREb_alpha", albums[0].albumId)
        assertEquals(listOf("a2", "a1"), albums[0].tracks.map { it.songId })
    }

    @Test
    fun playsWithNoRealAlbumAreSkipped() {
        val history = listOf(play("u1", ""), play("u2", "<unknown>"), play("a1", "Alpha"))
        assertEquals(listOf("Alpha"), recentAlbumsFrom(history).map { it.title })
    }

    @Test
    fun sameNameByDifferentArtistsOrSourcesStaysSeparate() {
        val history = listOf(
            play("a1", "Greatest Hits", artist = "One"),
            play("b1", "Greatest Hits", artist = "Two"),
            play("c1", "Greatest Hits", artist = "One", source = SongSource.LOCAL),
        )
        assertEquals(3, recentAlbumsFrom(history).size)
        assertNull(recentAlbumsFrom(history)[2].albumId)
    }

    @Test
    fun limitCapsCardsButKeepsGatheringTracksForShownOnes() {
        val history = listOf(play("a1", "Alpha"), play("b1", "Beta"), play("a2", "Alpha"))
        val albums = recentAlbumsFrom(history, limit = 1)
        assertEquals(listOf("Alpha"), albums.map { it.title })
        assertEquals(2, albums[0].tracks.size)
    }
}
