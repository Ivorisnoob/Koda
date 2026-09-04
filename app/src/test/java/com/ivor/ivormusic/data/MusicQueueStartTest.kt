package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicQueueStartTest {

    private fun song(id: String) = Song(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        duration = 180_000L
    )

    @Test
    fun noStartSongPlaysFromTheTop() {
        assertEquals(0, queueStartIndex(listOf(song("a"), song("b")), null))
    }

    @Test
    fun startSongIsFoundByPosition() {
        val list = listOf(song("a"), song("b"), song("c"))

        assertEquals(2, queueStartIndex(list, list[2]))
    }

    @Test
    fun duplicateSongResolvesToTheTappedOccurrence() {
        // The same track listed twice: both entries are equal values with the
        // same id, so only reference identity can tell the second tap from the
        // first. A playlist that repeats a track is the ordinary case here.
        val repeated = song("dup")
        val list = listOf(song("a"), repeated, song("b"), repeated.copy())

        assertEquals(3, queueStartIndex(list, list[3]))
        assertEquals(1, queueStartIndex(list, list[1]))
    }

    @Test
    fun aRemappedListStillResolvesById() {
        // A sorted or deduplicated view hands on equal copies rather than the
        // objects it drew; the id lookup is what keeps those call sites working.
        val list = listOf(song("a"), song("b"), song("c"))

        assertEquals(1, queueStartIndex(list, song("b")))
    }

    @Test
    fun aSongOutsideTheListIsReportedRatherThanClampedToZero() {
        // Clamping to 0 here is what answered a tap on one song by playing a
        // different one. The caller needs to be able to tell the two apart.
        val list = listOf(song("a"), song("b"))

        assertEquals(QUEUE_START_ABSENT, queueStartIndex(list, song("z")))
    }

    @Test
    fun anEmptyListIsAbsentRatherThanTheTop() {
        assertEquals(QUEUE_START_ABSENT, queueStartIndex(emptyList(), song("a")))
    }
}
