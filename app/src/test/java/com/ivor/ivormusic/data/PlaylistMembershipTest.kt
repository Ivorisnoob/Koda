package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistMembershipTest {

    @Test
    fun `only ALL and SOME options count as containing`() {
        // Reduced from a September 2026 www response.
        val raw = """
            {"contents":[{"addToPlaylistRenderer":{"playlists":[
              {"playlistAddToOptionRenderer":{"playlistId":"WL","containsSelectedVideos":"NONE"}},
              {"playlistAddToOptionRenderer":{"playlistId":"PLa","containsSelectedVideos":"ALL"}},
              {"playlistAddToOptionRenderer":{"playlistId":"PLb","containsSelectedVideos":"SOME"}},
              {"playlistAddToOptionRenderer":{"playlistId":"PLc"}}
            ]}}]}
        """.trimIndent()
        assertEquals(setOf("PLa", "PLb"), parsePlaylistsContaining(raw))
    }

    @Test
    fun `own writes override a lagging server until they expire`() {
        var clock = 0L
        val overlay = PlaylistMembershipOverlay(now = { clock }, ttlMs = 60_000L)
        overlay.record("PLadd", "v", contains = true)
        overlay.record("PLgone", "v", contains = false)
        overlay.record("PLother", "w", contains = true)

        assertEquals(setOf("PLadd", "PLkeep"), overlay.apply("v", setOf("PLgone", "PLkeep")))

        clock = 61_000L
        assertEquals(setOf("PLgone", "PLkeep"), overlay.apply("v", setOf("PLgone", "PLkeep")))
    }

    @Test
    fun `a forgotten write falls back to the server`() {
        val overlay = PlaylistMembershipOverlay(now = { 0L })
        overlay.record("PLa", "v", contains = true)
        overlay.forget("PLa", "v")
        assertEquals(emptySet<String>(), overlay.apply("v", emptySet()))
    }
}
