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

    @Test fun `a batch of 50 drops is over budget only when it came quickly`() {
        assertTrue(FrameInterpolationPolicy.dropsExceedBudget(50, 10_000L))
        assertFalse(FrameInterpolationPolicy.dropsExceedBudget(50, 60_000L))
        assertFalse(FrameInterpolationPolicy.dropsExceedBudget(0, 0L))
        assertTrue(FrameInterpolationPolicy.dropsExceedBudget(3, 0L))
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
