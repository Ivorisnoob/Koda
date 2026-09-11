package com.ivor.ivormusic.ui.video

import org.junit.Assert.*
import org.junit.Test

class LiveWindowOffsetTest {
    @Test fun unknownWindowHasNoInventedOffset() {
        assertNull(liveWindowOffsetMs(0L, 42L))
        assertNull(liveWindowOffsetMs(Long.MIN_VALUE + 1, 42L))
    }

    @Test fun longWindowStillReportsMinutesBehind() {
        val offset = liveWindowOffsetMs(43_200_000L, 43_020_000L)!!
        assertEquals(180_000L, offset)
        assertTrue(offset > LIVE_EDGE_TOLERANCE_MS)
    }

    @Test fun movingWindowAdvancesWhilePaused() {
        assertEquals(30_000L, liveWindowOffsetMs(100_000L, 70_000L))
        assertEquals(40_000L, liveWindowOffsetMs(110_000L, 70_000L))
    }

    @Test fun transientOutOfWindowPositionsAreClamped() {
        assertEquals(0L, liveWindowOffsetMs(100L, 110L))
        assertEquals(100L, liveWindowOffsetMs(100L, -1L))
    }

    /**
     * YouTube sends no EXT-X-START and no HOLD-BACK, so Media3 targets three
     * target-durations back from the playlist end. A stream sitting exactly
     * there is at the live edge, not 15s behind it.
     */
    @Test fun theManifestsOwnLiveEdgeReadsAsLive() {
        val target = 15_000L
        val offset = liveWindowOffsetMs(600_000L, 585_000L, target)!!
        assertEquals(0L, offset)
        assertTrue(offset <= LIVE_EDGE_TOLERANCE_MS)
    }

    /** A window that has grown past the player by less than one segment. */
    @Test fun aSegmentOfWindowGrowthStaysWithinTolerance() {
        val offset = liveWindowOffsetMs(605_000L, 585_000L, 15_000L)!!
        assertEquals(5_000L, offset)
        assertTrue(offset <= LIVE_EDGE_TOLERANCE_MS)
    }

    /** Scrubbing back reports the distance the viewer actually moved. */
    @Test fun scrubbingBackReportsDistanceFromTheEdgeNotTheWindowEnd() {
        assertEquals(120_000L, liveWindowOffsetMs(600_000L, 465_000L, 15_000L))
    }

    /** Never negative: the position can briefly sit past the computed edge. */
    @Test fun aPositionPastTheEdgeIsNotNegative() {
        assertEquals(0L, liveWindowOffsetMs(600_000L, 599_000L, 15_000L))
    }

    /** A source declaring no target falls back to the window-end measurement. */
    @Test fun noDeclaredTargetKeepsTheWindowEndMeasurement() {
        assertEquals(
            liveWindowOffsetMs(600_000L, 585_000L),
            liveWindowOffsetMs(600_000L, 585_000L, 0L)
        )
        assertEquals(15_000L, liveWindowOffsetMs(600_000L, 585_000L))
    }

    /** A nonsense target cannot push the edge outside the window. */
    @Test fun anOversizedTargetIsClampedToTheWindow() {
        assertEquals(0L, liveWindowOffsetMs(60_000L, 10_000L, 600_000L))
    }
}
