package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryBudgetTest {

    private fun budget() = PlaybackRecoveryBudget(maxAttempts = 1, refundAfterMs = 60_000L)

    @Test fun `one attempt is spent and then refused`() {
        val budget = budget()
        assertTrue(budget.tryConsume(0L))
        assertFalse(budget.tryConsume(1_000L))
        assertEquals(1, budget.attemptsUsed)
    }

    @Test fun `a stream refused after its opening does not earn its attempt back`() {
        // The loop this budget exists to stop: recover, play a few seconds
        // (READY, healthy ticks), fail again.
        val budget = budget()
        assertTrue(budget.tryConsume(0L))
        budget.onHealthy(2_000L)
        budget.onHealthy(4_000L)
        assertFalse(budget.tryConsume(5_000L))
    }

    @Test fun `playback that stays healthy earns the attempt back`() {
        val budget = budget()
        assertTrue(budget.tryConsume(0L))
        budget.onHealthy(60_000L)
        assertEquals(0, budget.attemptsUsed)
        assertTrue(budget.tryConsume(3 * 60 * 60 * 1000L))
    }

    @Test fun `healthy ticks with nothing spent change nothing`() {
        val budget = budget()
        budget.onHealthy(0L)
        assertTrue(budget.tryConsume(1L))
    }

    @Test fun `reset gives a new video or a user retry a full budget`() {
        val budget = budget()
        budget.tryConsume(0L)
        budget.reset()
        assertTrue(budget.tryConsume(1L))
    }
}
