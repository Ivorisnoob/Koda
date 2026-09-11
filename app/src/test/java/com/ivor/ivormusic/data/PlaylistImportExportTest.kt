package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImportExportTest {

    @Test
    fun exportToM3uFormatsPlaylistCorrectly() {
        val playlist = UserPlaylist(
            id = "test-playlist-id",
            name = "My Test Playlist",
            description = "A playlist for testing",
            coverUri = null,
            songs = listOf(
                Song(
                    id = "dQw4w9WgXcQ",
                    title = "Never Gonna Give You Up",
                    artist = "Rick Astley",
                    album = "Whenever You Need Somebody",
                    duration = 212000,
                    source = SongSource.YOUTUBE
                ),
                Song(
                    id = "device:/storage/emulated/0/Music/local_song.mp3",
                    title = "Local Track",
                    artist = "Local Artist",
                    album = "Local Album",
                    duration = 180000,
                    source = SongSource.LOCAL
                )
            )
        )

        val m3uContent = PlaylistExport.toM3u(playlist)

        assertTrue(m3uContent.contains("#EXTM3U"))
        assertTrue(m3uContent.contains("#PLAYLIST:My Test Playlist"))
        assertTrue(m3uContent.contains("#EXTINF:212,Rick Astley - Never Gonna Give You Up"))
        assertTrue(m3uContent.contains("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(m3uContent.contains("#EXTINF:180,Local Artist - Local Track"))
        assertTrue(m3uContent.contains("/storage/emulated/0/Music/local_song.mp3"))
    }

    @Test
    fun parseM3uExtractsYouTubeAndLocalSongs() {
        val m3uInput = """
            #EXTM3U
            #PLAYLIST:Imported Playlist
            #EXTINF:212,Rick Astley - Never Gonna Give You Up
            https://www.youtube.com/watch?v=dQw4w9WgXcQ
            #EXTINF:180,Local Artist - Local Track
            /storage/emulated/0/Music/local_song.mp3
            #EXTINF:-1,Bare YouTube ID Song
            jNQXAC9IVRw
        """.trimIndent()

        val songs = PlaylistImport.parseM3u(m3uInput)

        assertEquals(3, songs.size)

        assertEquals("dQw4w9WgXcQ", songs[0].id)
        assertEquals("Never Gonna Give You Up", songs[0].title)
        assertEquals("Rick Astley", songs[0].artist)
        assertEquals(212000L, songs[0].duration)
        assertEquals(SongSource.YOUTUBE, songs[0].source)

        assertEquals("device:/storage/emulated/0/Music/local_song.mp3", songs[1].id)
        assertEquals("Local Track", songs[1].title)
        assertEquals("Local Artist", songs[1].artist)
        assertEquals(180000L, songs[1].duration)
        assertEquals(SongSource.LOCAL, songs[1].source)

        assertEquals("jNQXAC9IVRw", songs[2].id)
        assertEquals("Bare YouTube ID Song", songs[2].title)
        assertEquals(SongSource.YOUTUBE, songs[2].source)
    }
}
