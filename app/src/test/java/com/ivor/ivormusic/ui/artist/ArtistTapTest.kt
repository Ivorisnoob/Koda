package com.ivor.ivormusic.ui.artist

import com.ivor.ivormusic.data.ArtistCredit
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistTapTest {

    private val calvin = ArtistCredit("Calvin Harris", "UCZ0Aezmtk-S2l8A9Ln-2lKw")
    private val dua = ArtistCredit("Dua Lipa", "UCzVb0SIXp9q9PeKCcFjsBtA")

    @Test
    fun `the joined line of a collaboration asks which artist`() {
        assertEquals(
            ArtistTap.Choose(listOf(calvin, dua)),
            resolveArtistTap("Calvin Harris, Dua Lipa", listOf(calvin, dua)),
        )
    }

    @Test
    fun `one credited name opens that channel`() {
        assertEquals(ArtistTap.Open("Dua Lipa", dua.id), resolveArtistTap("dua lipa ", listOf(calvin, dua)))
    }

    @Test
    fun `a single credit opens its channel even under another spelling`() {
        assertEquals(ArtistTap.Open("Calvin Harris", calvin.id), resolveArtistTap("CALVIN HARRIS", listOf(calvin)))
        assertEquals(ArtistTap.Open("Calvin Harris", calvin.id), resolveArtistTap("Calvin Harris - Topic", listOf(calvin)))
    }

    @Test
    fun `a song without credits opens by name`() {
        assertEquals(ArtistTap.Open("Tyler, The Creator", null), resolveArtistTap("Tyler, The Creator", emptyList()))
    }

    @Test
    fun `menus list each artist of a collaboration`() {
        assertEquals(listOf("Calvin Harris", "Dua Lipa"), artistMenuNames("Calvin Harris, Dua Lipa", listOf(calvin, dua)))
        assertEquals(listOf("Calvin Harris"), artistMenuNames("Calvin Harris", listOf(calvin)))
        assertEquals(emptyList<String>(), artistMenuNames(null, emptyList()))
    }
}
