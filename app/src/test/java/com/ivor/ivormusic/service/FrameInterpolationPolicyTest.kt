package com.ivor.ivormusic.service

import android.os.PowerManager
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToLong

class FrameInterpolationPolicyTest {

    private val step120 = 1_000_000.0 / 120
    private val step60 = 1_000_000.0 / 60

    @Test fun `24 25 30 and 60 fps are interpolated towards 120`() {
        assertTrue(FrameInterpolationPolicy.shouldInterpolate(41_708L, step120))
        assertTrue(FrameInterpolationPolicy.shouldInterpolate(40_000L, step120))
        assertTrue(FrameInterpolationPolicy.shouldInterpolate(33_366L, step120))
        assertTrue(FrameInterpolationPolicy.shouldInterpolate(16_683L, step120))
    }

    @Test fun `a source already at the output rate passes through`() {
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(16_683L, step60))
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(8_333L, step120))
        // 50 fps on a 60 Hz screen is lifted to 60.
        assertTrue(FrameInterpolationPolicy.shouldInterpolate(20_000L, step60))
    }

    @Test fun `gaps and reordered timestamps pass through`() {
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(100_000L, step60))
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(0L, step60))
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(-33_366L, step60))
    }

    @Test fun `target is the lower of the user cap and the screen`() {
        assertEquals(120, FrameInterpolationPolicy.targetFps(120, 120f))
        assertEquals(90, FrameInterpolationPolicy.targetFps(120, 90f))
        assertEquals(60, FrameInterpolationPolicy.targetFps(60, 120f))
        assertEquals(120, FrameInterpolationPolicy.targetFps(120, 144f))
        assertEquals(60, FrameInterpolationPolicy.targetFps(120, 59.94f))
        assertEquals(60, FrameInterpolationPolicy.targetFps(120, 30f))
    }

    @Test fun `large frames step down to 90 then 60 but never below`() {
        assertEquals(120, FrameInterpolationPolicy.affordableFps(120, 1920, 1080))
        assertEquals(120, FrameInterpolationPolicy.affordableFps(120, 1920, 1088))
        assertEquals(120, FrameInterpolationPolicy.affordableFps(120, 1080, 1920))
        assertEquals(90, FrameInterpolationPolicy.affordableFps(120, 2560, 1080))
        assertEquals(60, FrameInterpolationPolicy.affordableFps(120, 2560, 1440))
        assertEquals(60, FrameInterpolationPolicy.affordableFps(120, 3840, 2160))
        assertEquals(60, FrameInterpolationPolicy.affordableFps(60, 854, 480))
    }

    @Test fun `the step snaps to whole divisions of the source`() {
        // 30 into 120: every fourth output is a decoded frame.
        assertEquals(33_366.0 / 4, FrameInterpolationPolicy.outputStepUs(120, 1f, 33_366.0), 0.01)
        // 23.976 into 120: five.
        assertEquals(41_708.0 / 5, FrameInterpolationPolicy.outputStepUs(120, 1f, 41_708.0), 0.01)
        // 25 into 120 does not divide; the clock keeps the screen's rate.
        assertEquals(step120, FrameInterpolationPolicy.outputStepUs(120, 1f, 40_000.0), 0.01)
        // Unknown source: the screen's rate.
        assertEquals(step60, FrameInterpolationPolicy.outputStepUs(60, 1f, 0.0), 0.01)
    }

    @Test fun `the clock runs in real time at other speeds`() {
        assertEquals(1.5 * step120, FrameInterpolationPolicy.outputStepUs(120, 1.5f, 0.0), 0.01)
        // Half speed of 30 fps at 120 is eight outputs a decoded frame: allowed.
        assertEquals(33_333.0 / 8, FrameInterpolationPolicy.outputStepUs(120, 0.5f, 33_333.0), 0.01)
        // Quarter speed would be sixteen, so the rate halves to 60.
        assertEquals(33_333.0 / 8, FrameInterpolationPolicy.outputStepUs(120, 0.25f, 33_333.0), 0.01)
        // Unmeasured, the floor assumes the slowest source worth interpolating.
        assertEquals(0.5 * step60, FrameInterpolationPolicy.outputStepUs(120, 0.5f, 0.0), 0.01)
    }

    @Test fun `pool fits one input with room and keeps a lead within budget`() {
        assertEquals(6, FrameInterpolationPolicy.poolCapacity(1, 1920, 1080, 4))
        assertEquals(15, FrameInterpolationPolicy.poolCapacity(5, 854, 480, 4))
        assertEquals(12, FrameInterpolationPolicy.poolCapacity(5, 1920, 1080, 4))
        assertEquals(4, FrameInterpolationPolicy.poolCapacity(2, 3840, 2160, 4))
        assertTrue(FrameInterpolationPolicy.poolCapacity(9, 3840, 2160, 4) >= 11)
    }

    @Test fun `outputs per input count the ticks in one source interval`() {
        assertEquals(4, FrameInterpolationPolicy.outputsPerInput(33_366.0, 33_366.0 / 4))
        assertEquals(2, FrameInterpolationPolicy.outputsPerInput(33_333.0, step60))
        assertEquals(5, FrameInterpolationPolicy.outputsPerInput(40_000.0, step120))
        assertEquals(1, FrameInterpolationPolicy.outputsPerInput(0.0, step120))
    }

    @Test fun `gate opens only when nothing argues against it`() {
        fun gate(
            enabled: Boolean = true,
            live: Boolean = false,
            hdr: Boolean = false,
            thermal: Int = PowerManager.THERMAL_STATUS_NONE,
            powerSave: Boolean = false,
            drops: Boolean = false
        ) = FrameInterpolationPolicy.isGateOpen(enabled, live, hdr, thermal, powerSave, drops)

        assertTrue(gate())
        assertTrue(gate(thermal = PowerManager.THERMAL_STATUS_LIGHT))
        assertFalse(gate(enabled = false))
        assertFalse(gate(live = true))
        assertFalse(gate(hdr = true))
        assertFalse(gate(thermal = PowerManager.THERMAL_STATUS_MODERATE))
        assertFalse(gate(powerSave = true))
        assertFalse(gate(drops = true))
    }

    /**
     * Runs frames at [sourceUs] intervals through an [OutputClock] the way
     * the shader program does, and returns every emitted timestamp with
     * whether it was drawn.
     */
    private fun clockOutputs(sourceUs: Long, stepUs: Double, frames: Int): List<Pair<Long, Boolean>> {
        val clock = OutputClock()
        val out = ArrayList<Pair<Long, Boolean>>()
        var previous = 0L
        out += 0L to false
        clock.start(0L, stepUs)
        val tolerance = minOf(1_000L, (stepUs / 4).toLong())
        for (i in 1..frames) {
            val t = i * sourceUs
            while (clock.nextTickUs() < t - tolerance) {
                val tick = clock.nextTickUs()
                assertTrue(tick > previous)
                out += tick to true
                clock.advance()
            }
            if (clock.nextTickUs() <= t + tolerance) {
                out += clock.nextTickUs() to false
                clock.advance()
            }
            previous = t
        }
        return out
    }

    @Test fun `30 into 120 is four evenly spaced outputs per frame, one of them real`() {
        val out = clockOutputs(33_333L, FrameInterpolationPolicy.outputStepUs(120, 1f, 33_333.0), 30)
        assertEquals(30 * 4 + 1, out.size)
        assertEquals(31, out.count { !it.second })
        out.zipWithNext().forEach { (a, b) -> assertTrue(abs((b.first - a.first) - 8_333) <= 1) }
    }

    @Test fun `25 into 120 keeps an even 120 fps cadence`() {
        val out = clockOutputs(40_000L, FrameInterpolationPolicy.outputStepUs(120, 1f, 40_000.0), 25)
        // One second of source is 120 outputs, give or take the tick on the last frame.
        assertTrue(out.size in 120..122)
        out.zipWithNext().forEach { (a, b) -> assertTrue(abs((b.first - a.first) - 8_333) <= 1) }
    }

    @Test fun `24 into 60 alternates without drift`() {
        val step = FrameInterpolationPolicy.outputStepUs(60, 1f, 41_667.0)
        val out = clockOutputs(41_667L, step, 240)
        out.zipWithNext().forEach { (a, b) -> assertTrue(abs((b.first - a.first) - step.roundToLong()) <= 1) }
        // Ten seconds later the clock still lands on decoded frames.
        assertFalse(out.last().second)
    }

    @Test fun `a snapped step never runs faster than the screen`() {
        // An estimate pulled to ~49 ms divides into six steps of 8.2 ms,
        // 1.5% inside the tolerance but 122 fps on a 120 Hz screen.
        val skewed = FrameInterpolationPolicy.outputStep(120, 1f, 49_250.0)
        assertFalse(skewed.snapped)
        assertEquals(step120, skewed.stepUs, 0.01)
        // 23.976 divides a hair slower than 120: snapped.
        val film = FrameInterpolationPolicy.outputStep(120, 1f, 41_708.0)
        assertTrue(film.snapped)
        assertEquals(41_708.0 / 5, film.stepUs, 0.01)
        assertEquals(step120, film.paceStepUs, 0.01)
        // Exactly 24 fps divides into exactly the screen's step.
        assertTrue(FrameInterpolationPolicy.outputStep(120, 1f, 41_666.67).snapped)
    }

    @Test fun `a locked pair ends exactly on its decoded frame`() {
        val step = 41_708.0 / 5
        assertEquals(41_708.0 / 5, FrameInterpolationPolicy.lockedStepUs(41_708L, step, step120), 0.001)
        assertEquals(41_709.0 / 5, FrameInterpolationPolicy.lockedStepUs(41_709L, step, step120), 0.001)
        // A long pair in a variable-rate video: six steps still fit at the screen's pace...
        assertEquals(50_000.0 / 6, FrameInterpolationPolicy.lockedStepUs(50_000L, step, step120), 0.001)
        // ...but six would be faster than it here, so five longer ones.
        assertEquals(49_000.0 / 5, FrameInterpolationPolicy.lockedStepUs(49_000L, step, step120), 0.001)
        // A pair no longer than a step gets no ticks of its own.
        assertEquals(8_000.0, FrameInterpolationPolicy.lockedStepUs(8_000L, step, step120), 0.001)
    }

    /** 23.976 fps timestamps as a demuxer rounds them to whole microseconds. */
    private fun filmTimestamps(frames: Int): List<Long> =
        (0 until frames).map { (it * 1_000_000.0 * 1001 / 24_000).roundToLong() }

    @Test fun `a dropped frame does not move the measured rate`() {
        val meter = SourceRateMeter()
        val pts = filmTimestamps(60)
        pts.take(40).forEach(meter::onFrame)
        val settled = meter.intervalUs
        assertEquals(41_708.3, settled, 1.0)
        // Frame 40 never reaches the effect: an 83 ms gap.
        pts.drop(41).forEach(meter::onFrame)
        assertEquals(settled, meter.intervalUs, 1.0)
        // So the clock that restarts on that gap still snaps to five a frame.
        val step = FrameInterpolationPolicy.outputStep(120, 1f, meter.intervalUs)
        assertTrue(step.snapped)
        assertEquals(settled / 5, step.stepUs, 1.0)
    }

    @Test fun `a sustained new rate is taken up after a few frames`() {
        val meter = SourceRateMeter()
        var t = 0L
        repeat(30) { meter.onFrame(t); t += 33_333 }
        assertEquals(33_333.0, meter.intervalUs, 1.0)
        // Two long intervals are gaps...
        repeat(2) { t += 66_667 - 33_333; meter.onFrame(t); t += 33_333 }
        assertEquals(33_333.0, meter.intervalUs, 1.0)
        // ...three in a row are the video dropping to 15 fps.
        val meter2 = SourceRateMeter()
        t = 0L
        repeat(30) { meter2.onFrame(t); t += 33_333 }
        repeat(SourceRateMeter.RATE_CHANGE_FRAMES) { t += 33_334; meter2.onFrame(t); t += 33_333 }
        assertEquals(66_667.0, meter2.intervalUs, 1.0)
    }

    @Test fun `a seek keeps the rate and a new stream measures afresh`() {
        val meter = SourceRateMeter()
        filmTimestamps(20).forEach(meter::onFrame)
        val rate = meter.intervalUs
        meter.forgetLastFrame()
        // The first frame after a seek is far from the last one; it is not an interval.
        meter.onFrame(90_000_000L)
        meter.onFrame(90_000_000L + 41_708)
        assertEquals(rate, meter.intervalUs, 5.0)
        meter.reset()
        assertEquals(0.0, meter.intervalUs, 0.0)
        meter.onFrame(0L)
        meter.onFrame(16_683L)
        assertEquals(16_683.0, meter.intervalUs, 0.0)
    }

    /** Polls a watch at 500ms like the player does; returns whether any poll judged overload. */
    private class Playback(val watch: FrameDropWatch = FrameDropWatch()) {
        var now = 10_000L
        var position = 0L
        var dropped = 0
        var emitted = 0L
        var synthesized = 0L

        /** [seconds] at [outputFps], [dropsPerSecond] of the outputs late, interpolating or not. */
        fun play(
            seconds: Int,
            dropsPerSecond: Int,
            interpolating: Boolean = true,
            playing: Boolean = true,
            outputFps: Int = 120,
        ): Boolean {
            var overloaded = false
            repeat(seconds * 2) {
                now += 500L
                if (playing) {
                    position += 500L
                    emitted += outputFps / 2
                    dropped += dropsPerSecond / 2
                    if (interpolating) synthesized += outputFps / 2 * 3 / 4
                }
                if (watch.onPoll(now, playing, position, 1f, dropped, emitted, synthesized)) overloaded = true
            }
            return overloaded
        }
    }

    @Test fun `steady interpolation within budget is kept`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 30, dropsPerSecond = 0))
    }

    @Test fun `a few late frames at 120 fps stay inside the budget`() {
        // 4 a second is 3% of 120. Against the 30 decoded frames it would
        // have read as 13% and switched the feature off.
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 30, dropsPerSecond = 4))
    }

    @Test fun `sustained drops while interpolating are judged overload`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        // 24 of 120 frames a second late is 20%, well past 10%.
        assertTrue(playback.play(seconds = 20, dropsPerSecond = 24))
    }

    @Test fun `a phone slightly behind keeps trying`() {
        // 10 of 120 a second is 8%: over the old 5% line, inside the 10% one.
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 60, dropsPerSecond = 10))
    }

    @Test fun `overload is judged only after ten seconds past the grace period`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        // Six seconds of grace, then under ten seconds of heavy drops: not yet.
        assertFalse(playback.play(seconds = 15, dropsPerSecond = 60))
        assertTrue(playback.play(seconds = 2, dropsPerSecond = 60))
    }

    @Test fun `drops while only passing frames through are never counted`() {
        // The 1080p60 on a 60 Hz screen case: nothing drawn, nothing to blame.
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 8, interpolating = false, outputFps = 60))
        // And they do not carry over into interpolation afterwards.
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 0))
    }

    @Test fun `startup drops inside the grace period are forgiven`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 6, dropsPerSecond = 40))
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 0))
    }

    @Test fun `a resume after pause starts a new grace period`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 10, dropsPerSecond = 0))
        assertFalse(playback.play(seconds = 5, dropsPerSecond = 0, playing = false))
        assertFalse(playback.play(seconds = 2, dropsPerSecond = 40))
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 0))
    }

    @Test fun `a seek starts a new grace period`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 10, dropsPerSecond = 0))
        playback.position += 60_000L
        assertFalse(playback.play(seconds = 2, dropsPerSecond = 40))
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 0))
    }

    @Test fun `a screen that stays at 60 limits the output after a while`() {
        val watch = ScreenRateWatch(patienceMs = 5_000L)
        var now = 0L
        repeat(8) {
            now += 500L
            watch.onPoll(now, emitting = true, outputFps = 120, displayHz = 60f)
        }
        assertEquals(0, watch.limitFps)
        repeat(4) {
            now += 500L
            watch.onPoll(now, emitting = true, outputFps = 120, displayHz = 60f)
        }
        assertEquals(60, watch.limitFps)
    }

    @Test fun `a screen that follows the output is never limited`() {
        val watch = ScreenRateWatch(patienceMs = 5_000L)
        var now = 0L
        repeat(40) {
            now += 500L
            // Starts at 60 while Media3's estimator syncs, then switches.
            watch.onPoll(now, emitting = true, outputFps = 120, displayHz = if (it < 6) 60f else 120f)
        }
        assertEquals(0, watch.limitFps)
    }

    @Test fun `a paused or backgrounded video says nothing about the screen`() {
        val watch = ScreenRateWatch(patienceMs = 5_000L)
        var now = 0L
        repeat(40) {
            now += 500L
            watch.onPoll(now, emitting = false, outputFps = 120, displayHz = 60f)
        }
        assertEquals(0, watch.limitFps)
    }
}
