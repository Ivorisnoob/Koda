package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.data.PlayHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedDialTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000L * day

    private fun play(id: String, daysAgo: Long, title: String = id) =
        PlayHistoryEntry(id, title, "artist", "album", now - daysAgo * day, 1000)

    @Test
    fun `recent favourites lead and older ones fill the rest`() {
        // Newest first, as the history file is read.
        val history = listOf(
            play("a", 1), play("b", 2), play("a", 3), play("c", 4),
            play("old", 60), play("old", 61), play("old", 62),
        )
        assertEquals(listOf("a", "b", "c", "old"), speedDialFrom(history, now, limit = 4).map { it.songId })
    }

    @Test
    fun `ties go to the most recent and each song keeps its newest entry`() {
        val history = listOf(play("x", 1, "X new"), play("y", 2), play("x", 40, "X old"))
        val dial = speedDialFrom(history, now, limit = 2)
        assertEquals(listOf("x", "y"), dial.map { it.songId })
        assertEquals("X new", dial.first().title)
    }

    @Test
    fun `nothing played, nothing shown`() {
        assertEquals(emptyList<PlayHistoryEntry>(), speedDialFrom(emptyList(), now))
    }
}
