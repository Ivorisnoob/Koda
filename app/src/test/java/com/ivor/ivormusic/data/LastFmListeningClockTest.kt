package com.ivor.ivormusic.data

import org.junit.Assert.*
import org.junit.Test

class LastFmListeningClockTest {
    @Test fun shortTracksNeverQualify() {
        val clock = LastFmListeningClock()
        repeat(100) { assertFalse(clock.sample("a", true, it * 1000L, 1000, 30_000)) }
    }

    @Test fun qualifiesAtHalfAndOnlyOnce() {
        val clock = LastFmListeningClock()
        repeat(30) { assertFalse(clock.sample("a", true, it * 1000L, 1000, 60_000)) }
        assertTrue(clock.sample("a", true, 30_000, 1030, 60_000))
        assertFalse(clock.sample("a", true, 31_000, 1031, 60_000))
        assertEquals(1000, clock.startedAt)
    }

    @Test fun longTracksQualifyAfterFourMinutes() {
        val clock = LastFmListeningClock()
        repeat(240) { assertFalse(clock.sample("a", true, it * 1000L, 1000, 900_000)) }
        assertTrue(clock.sample("a", true, 240_000, 1240, 900_000))
    }

    @Test fun pauseAndBufferingDoNotCount() {
        val clock = LastFmListeningClock()
        clock.sample("a", true, 0, 1000, 60_000)
        repeat(60) { assertFalse(clock.sample("a", false, it * 1000L, 1000, 60_000)) }
        assertFalse(clock.sample("a", true, 60_000, 1060, 60_000))
    }

    @Test fun newOccurrenceAndPrivacyResetDiscardPartialListening() {
        val clock = LastFmListeningClock()
        repeat(29) { clock.sample("first", true, it * 1000L, 1000, 60_000) }
        assertFalse(clock.sample("second", true, 29_000, 1029, 60_000))
        repeat(29) { clock.sample("second", true, 30_000 + it * 1000L, 1030, 60_000) }
        clock.reset()
        assertFalse(clock.sample("second", true, 59_000, 1059, 60_000))
    }

    @Test fun stalledSchedulerCannotInventListeningTime() {
        val clock = LastFmListeningClock()
        clock.sample("a", true, 0, 1000, 60_000)
        assertFalse(clock.sample("a", true, 300_000, 1300, 60_000))
    }

    @Test fun timestampBeginsWhenPlaybackStarts() {
        val clock = LastFmListeningClock()
        clock.sample("a", false, 0, 1000, 60_000)
        clock.sample("a", true, 20_000, 1020, 60_000)
        assertEquals(1020, clock.startedAt)
    }
}
