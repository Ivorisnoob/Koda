package com.ivor.ivormusic.data.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacementBudgetTest {

    private val window = 10 * 60 * 1000L

    @Test fun `replacements up to the limit are allowed and the next is not`() {
        val budget = ReplacementBudget(max = 4, windowMs = window)
        repeat(4) {
            assertTrue(budget.allows(it * 1_000L))
            budget.record(it * 1_000L)
        }
        assertFalse(budget.allows(5_000L))
    }

    @Test fun `the window slides, freeing one replacement at a time`() {
        val budget = ReplacementBudget(max = 2, windowMs = window)
        budget.record(0L)
        budget.record(60_000L)
        assertFalse(budget.allows(window - 1))
        assertTrue(budget.allows(window))
        budget.record(window)
        assertFalse(budget.allows(window + 1))
        assertTrue(budget.allows(60_000L + window))
    }

    @Test fun `a new network starts with a full budget`() {
        val budget = ReplacementBudget(max = 1, windowMs = window)
        budget.record(0L)
        budget.reset()
        assertTrue(budget.allows(1L))
    }

    @Test fun `a clock moved backwards cannot pin the budget shut`() {
        val budget = ReplacementBudget(max = 1, windowMs = window)
        budget.record(1_000_000L)
        assertTrue(budget.allows(0L))
    }
}
