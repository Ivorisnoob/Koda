package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class QueueAsPlaylistTest {

    private fun song(id: String) = Song(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        duration = 180_000L
    )

    @Test
    fun orderIsPreservedExactly() {
        val queue = listOf(
            MusicQueueItem(song = song("c")),
            MusicQueueItem(song = song("a")),
            MusicQueueItem(song = song("b"))
        )

        assertEquals(listOf("c", "a", "b"), queueTracksForPlaylist(queue).map { it.id })
    }

    @Test
    fun duplicatesAreKept() {
        // A queue legitimately holds the same track twice - saving must
        // produce what was playing, not a deduplicated near-copy. Deliberately
        // the opposite of copyPlaylistToLocal, which drops repeats.
        val queue = listOf(
            MusicQueueItem(song = song("a")),
            MusicQueueItem(song = song("b")),
            MusicQueueItem(song = song("a"))
        )

        assertEquals(listOf("a", "b", "a"), queueTracksForPlaylist(queue).map { it.id })
    }

    @Test
    fun emptyQueueSavesNothing() {
        assertTrue(queueTracksForPlaylist(emptyList()).isEmpty())
    }

    @Test
    fun dateTextCarriesTheYear() {
        val noon = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.SEPTEMBER, 12, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val text = queuePlaylistDateText(noon)

        assertTrue("Expected a dated name, got '$text'", text.contains("2026"))
    }
}
