package com.ivor.ivormusic.service.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class QueueShuffleTest {
    private val ids = (0 until 8).map { "q$it" }
    private fun random() = Random(42)

    @Test
    fun turningOnKeepsTheCurrentSongFirstAndEverySongOnce() {
        val shuffle = QueueShuffle()
        val order = shuffle.turnOn(ids, currentIndex = 3, isRecent = { false }, random = random())
        assertEquals("q3", order.first())
        assertEquals(ids.toSet(), order.toSet())
        assertEquals(ids.size, order.size)
        assertTrue(shuffle.isOn)
    }

    @Test
    fun songsHeardLatelyComeLast() {
        val recent = setOf("q1", "q2")
        val order = QueueShuffle().turnOn(ids, currentIndex = 0, isRecent = { it in recent }, random = random())
        assertEquals(recent, order.takeLast(2).toSet())
    }

    @Test
    fun turningOffRestoresTheOrderBeforeShuffle() {
        val shuffle = QueueShuffle()
        shuffle.turnOn(ids, 3, { false }, random())
        val restored = shuffle.turnOff(ids.shuffled(random()))
        assertEquals(ids, restored)
        assertFalse(shuffle.isOn)
        assertNull(shuffle.unshuffledOrder())
    }

    @Test
    fun playNextWhileShuffledLandsAfterTheSongBeforeItOnceUnshuffled() {
        val shuffle = QueueShuffle()
        val shuffled = shuffle.turnOn(ids, 5, { false }, random())
        // "Play next" goes straight after the current song, q5, in the timeline.
        val edited = shuffled.toMutableList().apply { add(1, "new") }
        assertFalse(shuffle.follow(edited))
        val order = shuffle.unshuffledOrder()!!
        assertEquals("new", order[order.indexOf("q5") + 1])
    }

    @Test
    fun songsAddedAtTheEndJoinTheEnd() {
        val shuffle = QueueShuffle()
        val shuffled = shuffle.turnOn(ids, 0, { false }, random())
        shuffle.follow(shuffled + listOf("a", "b"))
        assertEquals(ids + listOf("a", "b"), shuffle.unshuffledOrder())
    }

    @Test
    fun removedSongsLeaveTheRememberedOrder() {
        val shuffle = QueueShuffle()
        val shuffled = shuffle.turnOn(ids, 0, { false }, random())
        shuffle.follow(shuffled - "q4")
        assertEquals(ids - "q4", shuffle.unshuffledOrder())
    }

    @Test
    fun aMoveIsNotANewQueueAndLeavesTheRememberedOrder() {
        val shuffle = QueueShuffle()
        val shuffled = shuffle.turnOn(ids, 0, { false }, random())
        val moved = shuffled.toMutableList().apply { add(2, removeAt(6)) }
        assertFalse(shuffle.follow(moved))
        assertEquals(ids, shuffle.unshuffledOrder())
    }

    @Test
    fun aQueueSharingNoSongIsNew() {
        val shuffle = QueueShuffle()
        shuffle.follow(ids)
        assertTrue(shuffle.follow(listOf("x", "y")))
        assertFalse(shuffle.follow(listOf("x", "y", "z")))
    }

    @Test
    fun aQueueLandingInAnEmptyPlayerIsNew() {
        val shuffle = QueueShuffle()
        shuffle.turnOn(emptyList(), -1, { false }, random())
        assertTrue(shuffle.follow(ids))
    }

    @Test
    fun aReshuffledQueueIsRememberedAsGiven() {
        val shuffle = QueueShuffle()
        val order = shuffle.reshuffle(ids, 2, { false }, random())
        assertEquals("q2", order.first())
        assertEquals(ids, shuffle.unshuffledOrder())
    }

    @Test
    fun anAdoptedSessionRestoresItsOwnOrder() {
        val shuffle = QueueShuffle()
        val timeline = listOf("q2", "q0", "q1")
        shuffle.adopt(timeline, order = listOf("q0", "q1", "q2"))
        assertTrue(shuffle.isOn)
        assertFalse(shuffle.follow(timeline))
        assertEquals(listOf("q0", "q1", "q2"), shuffle.turnOff(timeline))
    }
}
