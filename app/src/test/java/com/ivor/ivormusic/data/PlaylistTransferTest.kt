package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistTransferTest {

    @Test
    fun parsesExtendedM3uWithUrlsAndPaths() {
        val tracks = PlaylistTransfer.parseM3u(
            """
            #EXTM3U
            #EXTINF:215,Daft Punk - One More Time
            https://music.youtube.com/watch?v=FGBhQbmPwH8&list=RDAMVM
            #EXTINF:-1,Local Song
            /storage/emulated/0/Music/local.mp3
            https://youtu.be/dQw4w9WgXcQ
            """.trimIndent()
        )
        assertEquals(3, tracks.size)
        assertEquals("FGBhQbmPwH8", tracks[0].videoId)
        assertEquals("Daft Punk", tracks[0].artist)
        assertEquals("One More Time", tracks[0].title)
        assertEquals(215_000L, tracks[0].durationMs)
        assertNull(tracks[1].videoId)
        assertEquals("/storage/emulated/0/Music/local.mp3", tracks[1].path)
        assertEquals(0L, tracks[1].durationMs)
        assertEquals("dQw4w9WgXcQ", tracks[2].videoId)
        assertEquals("dQw4w9WgXcQ", tracks[2].title)
    }

    @Test
    fun exportRoundTrips() {
        val songs = listOf(
            Song.fromYouTube("FGBhQbmPwH8", "One More Time", "Daft Punk", "", 215_000, null),
            Song.fromYouTube("dQw4w9WgXcQ", "Never", UNKNOWN_ARTIST, "", 0, null),
        )
        val back = PlaylistTransfer.parseM3u(PlaylistTransfer.buildM3u(songs))
        assertEquals(listOf("FGBhQbmPwH8", "dQw4w9WgXcQ"), back.map { it.videoId })
        assertEquals("Daft Punk", back[0].artist)
        assertEquals("Never", back[1].title)
    }

    @Test
    fun rejectsNonVideoUrls() {
        assertNull(PlaylistTransfer.videoIdFrom("https://www.youtube.com/playlist?list=PL123"))
        assertNull(PlaylistTransfer.videoIdFrom("song.mp3"))
        assertEquals("PL123", PlaylistTransfer.playlistIdFrom("https://www.youtube.com/playlist?list=PL123"))
    }
}
