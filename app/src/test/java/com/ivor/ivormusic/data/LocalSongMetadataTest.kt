package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSongMetadataTest {

    @Test
    fun `mediastore unknown sentinel counts as unknown`() {
        assertTrue(isUnknownArtist("<unknown>"))
        assertTrue(isUnknownArtist("<UNKNOWN>"))
        assertTrue(isUnknownAlbum("<unknown>"))
        assertTrue(isUnknownTitle("<unknown>"))
    }

    @Test
    fun `blank and sentinel spellings count as unknown`() {
        assertTrue(isUnknownArtist(null))
        assertTrue(isUnknownArtist(""))
        assertTrue(isUnknownArtist("   "))
        assertTrue(isUnknownArtist("Unknown Artist"))
        assertTrue(isUnknownArtist("unknown artist"))
        assertTrue(isUnknownAlbum(null))
        assertTrue(isUnknownAlbum("Unknown Album"))
        assertTrue(isUnknownTitle(null))
        assertTrue(isUnknownTitle("Unknown"))
    }

    @Test
    fun `real names starting with unknown are not unknown`() {
        assertFalse(isUnknownArtist("Unknown Mortal Orchestra"))
        assertFalse(isUnknownTitle("Unknown Pleasures"))
        assertFalse(isUnknownAlbum("Unknown Pleasures"))
        assertFalse(isUnknownArtist("Cher"))
    }

    @Test
    fun `normalizers collapse sentinels and trim`() {
        assertEquals(UNKNOWN_ARTIST, normalizeLocalArtist("<unknown>"))
        assertEquals(UNKNOWN_ARTIST, normalizeLocalArtist(null))
        assertEquals(UNKNOWN_ARTIST, normalizeLocalArtist("  "))
        assertEquals("Cher", normalizeLocalArtist("  Cher  "))
        assertEquals(UNKNOWN_ALBUM, normalizeLocalAlbum("<unknown>"))
        assertEquals("file", normalizeLocalTitle(null, "file"))
        assertEquals("file", normalizeLocalTitle("<unknown>", "file"))
        assertEquals("Real Title", normalizeLocalTitle("Real Title", "file"))
    }

    @Test
    fun `album grouping is case insensitive and keeps one display name`() {
        val songs = listOf(
            localSong("1", "Song A", "Artist", "My Album"),
            localSong("2", "Song B", "Artist", "my album"),
            localSong("3", "Song C", "Artist", "Other Album")
        )
        val albums = songs.groupSongsByAlbum()
        assertEquals(listOf("My Album", "Other Album"), albums.map { it.first })
        assertEquals(listOf("1", "2"), albums.first().second.map { it.id })
    }

    @Test
    fun `artist grouping is case insensitive`() {
        val songs = listOf(
            localSong("1", "Song A", "AC/DC", "Album"),
            localSong("2", "Song B", "ac/dc", "Album")
        )
        val artists = songs.groupSongsByArtist()
        assertEquals(1, artists.size)
        assertEquals("AC/DC", artists.first().first)
    }

    @Test
    fun `album label names compilations honestly`() {
        val compilation = listOf(
            localSong("1", "Song A", "Artist A", "Mix"),
            localSong("2", "Song B", "Artist B", "Mix")
        )
        assertEquals("Various Artists", albumArtistLabel(compilation, "Various Artists"))

        val single = listOf(
            localSong("1", "Song A", "Artist A", "Mix"),
            localSong("2", "Song B", "Artist A", "Mix")
        )
        assertEquals("Artist A", albumArtistLabel(single, "Various Artists"))

        val unnamed = listOf(localSong("1", "Song A", UNKNOWN_ARTIST, "Mix"))
        assertEquals(UNKNOWN_ARTIST, albumArtistLabel(unnamed, "Various Artists"))
    }

    private fun localSong(id: String, title: String, artist: String, album: String) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = 0,
        source = SongSource.LOCAL
    )
}
