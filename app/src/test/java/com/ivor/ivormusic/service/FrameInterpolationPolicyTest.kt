package com.ivor.ivormusic.service

import android.os.PowerManager
import org.junit.Assert.*
import org.junit.Test

class FrameInterpolationPolicyTest {

    @Test fun `24 25 and 30 fps are interpolated`() {
        assertTrue(FrameInterpolationPolicy.shouldInterpolate(41_708L))
        assertTrue(FrameInterpolationPolicy.shouldInterpolate(40_000L))
        assertTrue(FrameInterpolationPolicy.shouldInterpolate(33_366L))
    }

    @Test fun `50 and 60 fps pass through`() {
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(20_000L))
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(16_683L))
    }

    @Test fun `gaps and reordered timestamps pass through`() {
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(100_000L))
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(0L))
        assertFalse(FrameInterpolationPolicy.shouldInterpolate(-33_366L))
    }

    @Test fun `midpoint sits between the two frames`() {
        assertEquals(1_016_683L, FrameInterpolationPolicy.midpointUs(1_000_000L, 1_033_366L))
    }

    @Test fun `gate opens only when nothing argues against it`() {
        fun gate(
            enabled: Boolean = true,
            live: Boolean = false,
            hdr: Boolean = false,
            speed: Float = 1f,
            thermal: Int = PowerManager.THERMAL_STATUS_NONE,
            powerSave: Boolean = false,
            drops: Boolean = false
        ) = FrameInterpolationPolicy.isGateOpen(enabled, live, hdr, speed, thermal, powerSave, drops)

        assertTrue(gate())
        assertTrue(gate(thermal = PowerManager.THERMAL_STATUS_LIGHT))
        assertTrue(gate(speed = 1.2f))
        assertFalse(gate(enabled = false))
        assertFalse(gate(live = true))
        assertFalse(gate(hdr = true))
        assertFalse(gate(speed = 1.5f))
        assertFalse(gate(thermal = PowerManager.THERMAL_STATUS_MODERATE))
        assertFalse(gate(powerSave = true))
        assertFalse(gate(drops = true))
    }

    /** Polls a watch at 500ms like the player does; returns the first poll that judged overload. */
    private class Playback(val watch: FrameDropWatch = FrameDropWatch()) {
        var now = 10_000L
        var position = 0L
        var dropped = 0
        var rendered = 0
        var midpoints = 0L

        /** [seconds] of playback at 30 fps, [dropsPerSecond] of them late, interpolating or not. */
        fun play(seconds: Int, dropsPerSecond: Int, interpolating: Boolean = true, playing: Boolean = true): Boolean {
            var overloaded = false
            repeat(seconds * 2) {
                now += 500L
                if (playing) {
                    position += 500L
                    rendered += 15 - dropsPerSecond / 2
                    dropped += dropsPerSecond / 2
                    if (interpolating) midpoints += 15
                }
                if (watch.onPoll(now, playing, position, 1f, dropped, rendered, midpoints)) overloaded = true
            }
            return overloaded
        }
    }

    @Test fun `steady interpolation within budget is kept`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 30, dropsPerSecond = 0))
    }

    @Test fun `sustained drops while interpolating are judged overload`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        // 4 of 30 frames a second late is 13%, well past 5%.
        assertTrue(playback.play(seconds = 12, dropsPerSecond = 4))
    }

    @Test fun `drops while only passing frames through are never counted`() {
        // The 1080p60 case: nothing interpolated, so nothing to blame.
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 8, interpolating = false))
        // And they do not carry over into interpolation afterwards.
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 0))
    }

    @Test fun `startup drops inside the grace period are forgiven`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 3, dropsPerSecond = 10))
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 0))
    }

    @Test fun `a resume after pause starts a new grace period`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 10, dropsPerSecond = 0))
        assertFalse(playback.play(seconds = 5, dropsPerSecond = 0, playing = false))
        assertFalse(playback.play(seconds = 2, dropsPerSecond = 10))
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 0))
    }

    @Test fun `a seek starts a new grace period`() {
        val playback = Playback()
        playback.watch.restart(playback.now)
        assertFalse(playback.play(seconds = 10, dropsPerSecond = 0))
        playback.position += 60_000L
        assertFalse(playback.play(seconds = 2, dropsPerSecond = 10))
        assertFalse(playback.play(seconds = 20, dropsPerSecond = 0))
    }

    @Test fun `levels halve from a 320 texel long side`() {
        val sizes = FrameInterpolationPolicy.levelSizes(1920, 1080)
        assertEquals(listOf(320 to 180, 160 to 90, 80 to 45), sizes)
    }

    @Test fun `portrait costs what landscape does`() {
        val sizes = FrameInterpolationPolicy.levelSizes(1080, 1920)
        assertEquals(listOf(180 to 320, 90 to 160, 45 to 80), sizes)
    }

    @Test fun `small and degenerate frames never produce an empty level`() {
        val small = FrameInterpolationPolicy.levelSizes(256, 144)
        assertEquals(128 to 72, small.first())
        FrameInterpolationPolicy.levelSizes(1, 1).forEach { (w, h) ->
            assertTrue(w >= 4 && h >= 4)
        }
        FrameInterpolationPolicy.levelSizes(4000, 2).forEach { (w, h) ->
            assertTrue(w >= 4 && h >= 4)
        }
    }
}
