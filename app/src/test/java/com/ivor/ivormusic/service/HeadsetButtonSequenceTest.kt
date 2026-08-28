package com.ivor.ivormusic.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetButtonSequenceTest {
    private val sequence = HeadsetButtonSequence(timeoutMs = 300L)

    @Test
    fun singlePressTogglesWhenWindowExpires() {
        val tap = sequence.onTap(eventTimeMs = 1_000L)

        assertNull(tap.completedAction)
        assertTrue(tap.awaitingMore)
        assertEquals(HeadsetButtonAction.TOGGLE_PLAY_PAUSE, sequence.consumePending())
        assertNull(sequence.consumePending())
    }

    @Test
    fun doublePressAdvancesWhenWindowExpires() {
        sequence.onTap(eventTimeMs = 1_000L)
        val secondTap = sequence.onTap(eventTimeMs = 1_200L)

        assertNull(secondTap.completedAction)
        assertTrue(secondTap.awaitingMore)
        assertEquals(HeadsetButtonAction.NEXT, sequence.consumePending())
    }

    @Test
    fun triplePressImmediatelyReturnsPrevious() {
        sequence.onTap(eventTimeMs = 1_000L)
        sequence.onTap(eventTimeMs = 1_180L)
        val thirdTap = sequence.onTap(eventTimeMs = 1_360L)

        assertEquals(HeadsetButtonAction.PREVIOUS, thirdTap.completedAction)
        assertFalse(thirdTap.awaitingMore)
        assertNull(sequence.consumePending())
    }

    @Test
    fun latePressCompletesOldSequenceAndStartsAnother() {
        sequence.onTap(eventTimeMs = 1_000L)
        val lateTap = sequence.onTap(eventTimeMs = 1_301L)

        assertEquals(HeadsetButtonAction.TOGGLE_PLAY_PAUSE, lateTap.completedAction)
        assertTrue(lateTap.awaitingMore)
        assertEquals(HeadsetButtonAction.TOGGLE_PLAY_PAUSE, sequence.consumePending())
    }

    @Test
    fun pressAfterTripleStartsANewSequence() {
        sequence.onTap(eventTimeMs = 1_000L)
        sequence.onTap(eventTimeMs = 1_100L)
        sequence.onTap(eventTimeMs = 1_200L)

        val fourthTap = sequence.onTap(eventTimeMs = 1_300L)

        assertNull(fourthTap.completedAction)
        assertTrue(fourthTap.awaitingMore)
        assertEquals(HeadsetButtonAction.TOGGLE_PLAY_PAUSE, sequence.consumePending())
    }
}
