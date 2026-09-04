package com.ivor.ivormusic.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleThresholdTest {

    @Test
    fun shortTracksAreNotEligible() {
        assertFalse(ScrobbleController.isScrobbleEligible(1L))
        assertFalse(ScrobbleController.isScrobbleEligible(15L))
        assertFalse(ScrobbleController.isScrobbleEligible(29L))
    }

    @Test
    fun tracksThirtySecondsOrMoreAreEligible() {
        assertTrue(ScrobbleController.isScrobbleEligible(30L))
        assertTrue(ScrobbleController.isScrobbleEligible(120L))
        assertTrue(ScrobbleController.isScrobbleEligible(600L))
    }

    @Test
    fun unknownDurationDefaultsToEligible() {
        // Unknown duration (<= 0) is treated as eligible until proven otherwise
        assertTrue(ScrobbleController.isScrobbleEligible(0L))
        assertTrue(ScrobbleController.isScrobbleEligible(-1L))
    }

    @Test
    fun thresholdIsHalfOfDurationForTracksUnderEightMinutes() {
        // 40s track -> threshold is 20s (20,000ms)
        assertEquals(20_000L, ScrobbleController.calculateThresholdMs(40L))

        // 60s track -> threshold is 30s (30,000ms)
        assertEquals(30_000L, ScrobbleController.calculateThresholdMs(60L))

        // 200s track -> threshold is 100s (100,000ms)
        assertEquals(100_000L, ScrobbleController.calculateThresholdMs(200L))

        // 400s track -> threshold is 200s (200,000ms)
        assertEquals(200_000L, ScrobbleController.calculateThresholdMs(400L))
    }

    @Test
    fun thresholdIsCappedAtFourMinutesForLongTracks() {
        // 480s (8 min) track -> threshold is 240s (240,000ms)
        assertEquals(240_000L, ScrobbleController.calculateThresholdMs(480L))

        // 600s (10 min) track -> threshold is capped at 240s (240,000ms)
        assertEquals(240_000L, ScrobbleController.calculateThresholdMs(600L))

        // 1800s (30 min) track -> capped at 240s (240,000ms)
        assertEquals(240_000L, ScrobbleController.calculateThresholdMs(1800L))
    }

    @Test
    fun unknownDurationUsesMaxThreshold() {
        assertEquals(ScrobbleController.MAX_THRESHOLD_MS, ScrobbleController.calculateThresholdMs(0L))
        assertEquals(ScrobbleController.MAX_THRESHOLD_MS, ScrobbleController.calculateThresholdMs(-5L))
    }
}
