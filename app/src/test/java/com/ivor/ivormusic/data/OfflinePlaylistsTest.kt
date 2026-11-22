package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OfflinePlaylistsTest {
    private fun song(id: String) = Song(id, "Track $id", "Artist", "Album", 1000,
        source = SongSource.YOUTUBE)

    private fun card(id: String, count: Int = 50) =
        PlaylistDisplayItem("Playlist $id", id, "Me", count, "art-$id")

    private val roadTrip = DownloadedPlaylist("PLroad", "Road trip", "road-art",
        songs = listOf(song("a"), song("b"), song("c")))

    private fun offline(
        playlists: List<PlaylistDisplayItem>,
        downloaded: List<DownloadedPlaylist> = listOf(roadTrip),
        downloads: List<Song> = listOf(song("a"), song("b")),
        hidden: Set<String> = emptySet(),
        online: Boolean = false,
    ) = playlistsForOffline(playlists, online, downloaded, downloads, hidden, "Downloaded")

    @Test fun onlineTheListIsUntouched() {
        val list = listOf(card("PLmine"))
        assertSame(list, offline(list, online = true))
    }

    @Test fun offlineADownloadedPlaylistMissingFromTheLibraryIsListedFirst() {
        // The reported bug: the account fetch fails offline, so the grid had
        // only the device playlist and the downloaded one was nowhere.
        val result = offline(listOf(card("local_1")))
        assertEquals(listOf("PLroad", "local_1"), result.map { it.id })
        assertEquals("Road trip", result[0].name)
        assertEquals("Downloaded", result[0].uploaderName)
        assertEquals("road-art", result[0].thumbnailUrl)
    }

    @Test fun itCountsOnlyTheSongsOnTheDevice() {
        assertEquals(2, offline(emptyList())[0].itemCount)
    }

    @Test fun aListedPlaylistKeepsItsCardAndMovesUpOnce() {
        val result = offline(listOf(card("local_1"), card("PLroad", count = 3)))
        assertEquals(listOf("PLroad", "local_1"), result.map { it.id })
        assertEquals("Me", result[0].uploaderName)
        assertEquals(2, result[0].itemCount)
    }

    @Test fun aPlaylistWithNothingOnTheDeviceIsNotAdded() {
        assertEquals(listOf("local_1"), offline(listOf(card("local_1")), downloads = emptyList()).map { it.id })
    }

    @Test fun aHiddenPlaylistStaysHiddenOffline() {
        assertEquals(emptyList<String>(), offline(emptyList(), hidden = setOf("PLroad")).map { it.id })
    }
}
