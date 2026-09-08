package com.ivor.ivormusic.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedPlaylistTest {
    private fun song(id: String) = Song(id, "Track $id", "Artist", "Album", 1000,
        source = SongSource.YOUTUBE)

    @Test fun offlineQueuePreservesOrderAndRepeatedEntries() {
        val a = song("a")
        val b = song("b")
        val playlist = DownloadedPlaylist("playlist", "Title", songs = listOf(b, a, b))
        val localA = a.copy(source = SongSource.LOCAL, title = "Downloaded A")
        val localB = b.copy(source = SongSource.LOCAL, title = "Downloaded B")
        assertEquals(listOf(localB, localA, localB), playlist.offlineSongs(listOf(localA, localB)))
    }

    @Test fun missingOrDeletedDownloadsNeverFallBackToRemoteTracks() {
        val playlist = DownloadedPlaylist("playlist", "Title", songs = listOf(song("a"), song("b")))
        assertEquals(listOf(song("b")), playlist.offlineSongs(listOf(song("b"))))
        assertTrue(playlist.offlineSongs(emptyList()).isEmpty())
    }

    @Test fun snapshotRoundTripRetainsIdentityAndOccurrences() {
        val playlist = DownloadedPlaylist("playlist", "A title", "https://example.com/cover",
            listOf(song("b"), song("a"), song("b")))
        val restored = Json.decodeFromString<DownloadedPlaylist>(Json.encodeToString(playlist))
        assertEquals(playlist, restored)
    }
}
