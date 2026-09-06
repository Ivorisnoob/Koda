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
}
