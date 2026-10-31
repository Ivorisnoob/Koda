package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryBackoffTest {

    @Test fun `a fresh backoff allows at once`() {
        assertTrue(RetryBackoff(initialDelayMs = 1_000L).allows(0L))
    }

    @Test fun `a failure waits the first delay`() {
        val backoff = RetryBackoff(initialDelayMs = 1_000L, maxDelayMs = 60_000L)
        backoff.failed(10_000L)
        assertFalse(backoff.allows(10_999L))
        assertTrue(backoff.allows(11_000L))
    }

    @Test fun `each failure in a row doubles the wait up to the cap`() {
        val backoff = RetryBackoff(initialDelayMs = 1_000L, maxDelayMs = 3_000L)
        backoff.failed(0L)
        assertTrue(backoff.allows(1_000L))
        backoff.failed(1_000L)
        assertFalse(backoff.allows(2_999L))
        assertTrue(backoff.allows(3_000L))
        backoff.failed(3_000L)
        // 4s would be next; the cap holds it at 3s.
        assertTrue(backoff.allows(6_000L))
    }

    @Test fun `it stops after the last allowed failure`() {
        val backoff = RetryBackoff(initialDelayMs = 1_000L, maxFailures = 2)
        backoff.failed(0L)
        backoff.failed(1_000L)
        assertFalse(backoff.allows(Long.MAX_VALUE / 2))
        assertEquals(2, backoff.failures)
    }

    @Test fun `success starts over from the first delay`() {
        val backoff = RetryBackoff(initialDelayMs = 1_000L, maxDelayMs = 60_000L, maxFailures = 2)
        backoff.failed(0L)
        backoff.failed(1_000L)
        backoff.succeeded()
        assertTrue(backoff.allows(1_000L))
        backoff.failed(1_000L)
        assertTrue(backoff.allows(2_000L))
    }

    @Test fun `a long run of failures does not overflow the delay`() {
        val backoff = RetryBackoff(initialDelayMs = 5 * 60_000L, maxDelayMs = 30 * 60_000L)
        repeat(100) { backoff.failed(0L) }
        assertTrue(backoff.allows(30 * 60_000L))
    }

    @Test fun `keyed backoffs hold one key without holding another`() {
        val backoffs = RetryBackoffs(capacity = 4) { RetryBackoff(initialDelayMs = 1_000L) }
        backoffs.failed("a", 0L)
        assertFalse(backoffs.allows("a", 500L))
        assertTrue(backoffs.allows("b", 500L))
        backoffs.succeeded("a")
        assertTrue(backoffs.allows("a", 500L))
    }

    @Test fun `keyed backoffs forget the least recently touched key past capacity`() {
        val backoffs = RetryBackoffs(capacity = 2) { RetryBackoff(initialDelayMs = 1_000L) }
        backoffs.failed("a", 0L)
        backoffs.failed("b", 0L)
        backoffs.failed("c", 0L)
        // Forgotten means it may be tried again, never that it is stuck.
        assertTrue(backoffs.allows("a", 0L))
        assertFalse(backoffs.allows("c", 0L))
    }
}
