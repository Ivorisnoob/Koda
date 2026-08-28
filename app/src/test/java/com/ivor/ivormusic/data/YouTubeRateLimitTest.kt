package com.ivor.ivormusic.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The rate-limit hold is the one piece of this work with real decision logic
 * rather than plumbing, and every case below is something that would otherwise
 * only show up on a throttled device, which is exactly where it cannot be
 * debugged.
 */
class YouTubeRateLimitTest {

    @Before
    fun reset() = YouTubeRateLimit.clear()

    @After
    fun tearDown() = YouTubeRateLimit.clear()

    @Test
    fun `a 429 arms the hold and reports it as a rate limit`() {
        assertTrue(YouTubeRateLimit.note(429, "browse FEsubscriptions"))
        assertTrue(YouTubeRateLimit.isHeld())
    }

    @Test
    fun `a 403 does not arm the hold`() {
        // googlevideo 403 is the PO-Token verdict on visitorData and has its
        // own remint recovery; routing it here would replace a working fix with
        // a five minute wait.
        assertFalse(YouTubeRateLimit.note(403, "googlevideo"))
        assertFalse(YouTubeRateLimit.isHeld())
    }

    @Test
    fun `a 404 does not arm the hold`() {
        assertFalse(YouTubeRateLimit.note(404, "channel feed"))
        assertFalse(YouTubeRateLimit.isHeld())
    }

    @Test
    fun `success does not arm the hold`() {
        assertFalse(YouTubeRateLimit.note(200, "browse"))
        assertFalse(YouTubeRateLimit.isHeld())
    }

    @Test
    fun `Retry-After lengthens the hold but is clamped`() {
        // An hour would disable the subscriptions tab with no way for the user
        // to tell why; the ceiling is 30 minutes.
        YouTubeRateLimit.note(429, "browse", retryAfterHeader = "3600")
        val remaining = YouTubeRateLimit.remainingMs()
        assertTrue("expected clamp to 30min, got ${remaining}ms", remaining <= 30 * 60 * 1000L)
        assertTrue(remaining > 25 * 60 * 1000L)
    }

    @Test
    fun `a short Retry-After does not undercut the default hold`() {
        // Honouring "1 second" literally would let a refresh loop walk straight
        // back into the limit.
        YouTubeRateLimit.note(429, "browse", retryAfterHeader = "1")
        assertTrue(YouTubeRateLimit.remainingMs() > 4 * 60 * 1000L)
    }

    @Test
    fun `a garbage Retry-After falls back to the default hold`() {
        YouTubeRateLimit.note(429, "browse", retryAfterHeader = "Wed, 21 Oct 2026 07:28:00 GMT")
        assertTrue(YouTubeRateLimit.isHeld())
        assertTrue(YouTubeRateLimit.remainingMs() > 4 * 60 * 1000L)
    }

    @Test
    fun `a second shorter hold cannot shorten the first`() {
        YouTubeRateLimit.note(429, "browse", retryAfterHeader = "1800")
        val long = YouTubeRateLimit.remainingMs()
        YouTubeRateLimit.note(429, "channel feed")
        assertTrue(
            "a concurrent 429 must not shorten an existing hold",
            YouTubeRateLimit.remainingMs() >= long - 1_000L,
        )
    }

    @Test
    fun `clear releases the hold`() {
        YouTubeRateLimit.note(429, "browse")
        assertTrue(YouTubeRateLimit.isHeld())
        YouTubeRateLimit.clear()
        assertFalse(YouTubeRateLimit.isHeld())
        assertEquals(0L, YouTubeRateLimit.remainingMs())
    }

    @Test
    fun `remainingMs is zero when nothing is held`() {
        assertEquals(0L, YouTubeRateLimit.remainingMs())
    }
}
