package com.ivor.ivormusic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The entrance has to end, and it has to end at exactly the song's own levels.
 *
 * A sweep that settles at 0.98 leaves every bar permanently 2% short, which nobody would spot
 * by looking and which would make the waveform quietly wrong about the track forever after.
 */
class WaveformRevealTest {

    private val bars = 96

    @Test fun everyBarIsFullyRisenOnceTheSweepIsDone() {
        for (index in 0 until bars) {
            assertEquals(1f, WaveformReveal.factorAt(index, bars, 1f), 0f)
            assertEquals(1f, WaveformReveal.factorAt(index, bars, 1.5f), 0f)
        }
    }

    @Test fun everyBarStartsFlat() {
        for (index in 0 until bars) {
            assertEquals(0f, WaveformReveal.factorAt(index, bars, 0f), 0f)
            assertEquals(0f, WaveformReveal.factorAt(index, bars, -0.2f), 0f)
        }
    }

    /**
     * The point of the stagger: mid-sweep the left of the track is ahead of the right.
     *
     * Sampled before the leading bar has settled. Past that point a trailing bar can legitimately
     * read higher than a leading one, because the leader has come back to 1 while the follower is
     * still at the top of its overshoot - which is the bounce, not a fault.
     */
    @Test fun theSweepRunsLeftToRight() {
        for (reveal in listOf(0.15f, 0.35f, 0.6f)) {
            val first = WaveformReveal.factorAt(0, bars, reveal)
            val last = WaveformReveal.factorAt(bars - 1, bars, reveal)
            assertTrue("reveal=$reveal first=$first last=$last", first >= last)
        }
        // And it is a real spread rather than a rounding difference.
        assertTrue(
            WaveformReveal.factorAt(0, bars, 0.3f) - WaveformReveal.factorAt(bars - 1, bars, 0.3f) > 0.3f
        )
    }

    /**
     * A bar rises, tops out once, and settles: one direction change and no oscillation.
     *
     * A curve that wobbles would read as the bar shivering rather than springing, and it is the
     * kind of thing only arithmetic catches - at 620ms across a hundred bars nobody could see
     * which of two shapes was drawn.
     */
    @Test fun eachBarRisesToOnePeakAndSettles() {
        for (index in listOf(0, bars / 3, bars - 1)) {
            var previous = WaveformReveal.factorAt(index, bars, 0f)
            var falling = false
            var turns = 0
            for (step in 1..400) {
                val value = WaveformReveal.factorAt(index, bars, step / 400f)
                val delta = value - previous
                if (delta < -1e-5f && !falling) {
                    falling = true
                    turns++
                } else if (delta > 1e-5f && falling) {
                    falling = false
                    turns++
                }
                previous = value
            }
            assertTrue("bar=$index turned $turns times", turns <= 1)
            assertEquals(1f, WaveformReveal.factorAt(index, bars, 1f), 0f)
        }
    }

    /** It overshoots on the way, which is where the spring feel comes from. */
    @Test fun aBarPassesItsLevelBeforeSettling() {
        val peak = (0..100).maxOf { WaveformReveal.factorAt(0, bars, it / 100f) }
        assertTrue("peak=$peak", peak > 1f)
        assertTrue("peak=$peak", peak < 1.2f)
    }

    /** Degenerate widths must not divide by zero or index off the end. */
    @Test fun oneBarAndNoBarsAreBothSafe() {
        assertEquals(0f, WaveformReveal.factorAt(0, 0, 0.5f), 0f)
        assertTrue(WaveformReveal.factorAt(0, 1, 0.5f) > 0f)
        assertEquals(1f, WaveformReveal.factorAt(0, 1, 1f), 0f)
        // An index past the end is clamped rather than throwing.
        assertEquals(1f, WaveformReveal.factorAt(500, 8, 1f), 0f)
    }
}
