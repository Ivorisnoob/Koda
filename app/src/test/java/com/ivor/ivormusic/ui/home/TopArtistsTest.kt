package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.data.PlayHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class TopArtistsTest {

    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun play(artist: String, daysAgo: Long, id: String = artist) = PlayHistoryEntry(
        songId = id, title = "t", artist = artist, album = "a",
        timestamp = now - daysAgo * day, duration = 1L, thumbnailUrl = "art-$id",
    )

    @Test
    fun ranksByPlaysWithinTheWindowAndBreaksTiesByRecency() {
        val history = listOf(
            play("B", 1), play("A", 2), play("C", 3), play("A", 4), play("B", 5), play("C", 6), play("C", 7),
        )
        assertEquals(listOf("C", "B", "A"), topArtistsFrom(history, now).map { it.name })
        assertEquals(3, topArtistsFrom(history, now).first().plays)
    }

    @Test
    fun oldPlaysDoNotCountWhileTheWindowHasEnough() {
        val history = listOf(play("A", 1), play("B", 2), play("C", 3)) +
            List(10) { play("Old", 200, "old$it") }
        assertEquals(listOf("A", "B", "C"), topArtistsFrom(history, now).map { it.name })
    }

    @Test
    fun aQuietWindowWidensToAllOfHistory() {
        val history = listOf(play("A", 1)) + List(3) { play("Old", 200, "old$it") }
        assertEquals(listOf("Old", "A"), topArtistsFrom(history, now).map { it.name })
    }

    @Test
    fun featuredCreditsCountForTheLeadAndCaseIsFolded() {
        val history = listOf(play("Lead feat. Guest", 1), play("lead", 2), play("Lead ft Guest", 3))
        val top = topArtistsFrom(history, now, minimum = 1)
        assertEquals(1, top.size)
        assertEquals("Lead", top[0].name)
        assertEquals(3, top[0].plays)
    }

    @Test
    fun unknownArtistsAreSkippedAndJoinedActsStayWhole() {
        val history = listOf(play("", 1), play("<unknown>", 1), play("Simon & Garfunkel", 1))
        assertEquals(listOf("Simon & Garfunkel"), topArtistsFrom(history, now, minimum = 1).map { it.name })
    }
}
