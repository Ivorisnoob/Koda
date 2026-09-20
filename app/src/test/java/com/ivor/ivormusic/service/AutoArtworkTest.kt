package com.ivor.ivormusic.service

import org.junit.Assert.*
import org.junit.Test

class AutoArtworkTest {
    @Test fun `every browse category id has a tile`() {
        val ids = listOf(
            "LIBRARY", "RECOMMENDED", "PLAYLISTS",
            "DOWNLOADS", "LIKED", "RECENT", "LOCAL_SONGS"
        )
        val keys = ids.map { AutoArtwork.categoryKey(it) }
        assertTrue(keys.none { it.isNullOrBlank() })
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test fun `unknown and null ids have no tile`() {
        assertNull(AutoArtwork.categoryKey(null))
        assertNull(AutoArtwork.categoryKey("root"))
        assertNull(AutoArtwork.categoryKey("PLAYLIST_OLAK5uy_x"))
        assertNull(AutoArtwork.categoryKey(""))
    }

    @Test fun `tile paths are stable category png paths`() {
        assertEquals("category/downloads.png", AutoArtwork.pathFor("downloads"))
        assertEquals("category/liked.png", AutoArtwork.pathFor("liked"))
    }
}
