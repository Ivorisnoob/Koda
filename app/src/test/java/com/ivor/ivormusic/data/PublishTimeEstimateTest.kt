package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishTimeEstimateTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private val week = 7 * day

    @Test
    fun aRunOfOnePhraseKeepsTheServedOrderInsideItsSpan() {
        val times = VideoItem.estimatePublishTimes(
            listOf("1 week ago", "1 week ago", "1 week ago"),
            now
        ).map { it!! }
        assertTrue(times[0] > times[1] && times[1] > times[2])
        times.forEach { assertTrue(it <= now - week && it > now - 2 * week) }
    }

    @Test
    fun differentPhrasesAreNotDraggedByTheirNeighbours() {
        // A pinned old entry first must not push the recent ones down.
        val times = VideoItem.estimatePublishTimes(listOf("2 years ago", "1 day ago", "3 days ago"), now)
        assertTrue(times[1]!! > times[0]!!)
        assertTrue(times[1]!! > times[2]!!)
        assertTrue(times[1]!! <= now - day && times[1]!! > now - 2 * day)
    }

    @Test
    fun anEntryWithNoDateHasNoTimeAndBreaksTheRun() {
        val times = VideoItem.estimatePublishTimes(listOf("Streamed 2 days ago", null, "2 days ago"), now)
        assertNull(times[1])
        assertEquals(times[0], times[2])
    }

    @Test
    fun anExactTimeIsKept() {
        val exact = VideoItem(
            videoId = "a", title = "a", channelName = "c", thumbnailUrl = null,
            duration = 0, viewCount = "", uploadedDate = "1 week ago", publishedAtMs = 42L
        )
        val prose = exact.copy(videoId = "b", publishedAtMs = null)
        val stamped = withEstimatedPublishTimes(listOf(exact, prose), now)
        assertEquals(42L, stamped[0].publishedAtMs)
        assertTrue(stamped[1].publishedAtMs!! <= now - week)
    }
}
