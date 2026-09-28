package com.ivor.ivormusic.service

import com.ivor.ivormusic.service.ComputeMotionEngine.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeMotionPlanTest {

    @Test fun `1080p is matched at full size down to about 120 px`() {
        val levels = ComputeMotionPlan.levels(1920, 1080)
        assertEquals(Size(1920, 1080), levels.first())
        assertEquals(listOf(1920, 960, 480, 240, 120), levels.map { it.w })
    }

    @Test fun `4K is matched at half size`() {
        assertEquals(Size(1920, 1080), ComputeMotionPlan.levels(3840, 2160).first())
    }

    @Test fun `480p keeps its own size`() {
        val levels = ComputeMotionPlan.levels(854, 480)
        assertEquals(Size(854, 480), levels.first())
        assertTrue(maxOf(levels.last().w, levels.last().h) <= ComputeMotionPlan.COARSEST_LONG_SIDE)
    }

    @Test fun `tiny and degenerate frames still give a usable level`() {
        val levels = ComputeMotionPlan.levels(2, 2)
        assertEquals(1, levels.size)
        assertTrue(levels[0].w >= 16 && levels[0].h >= 16)
    }

    @Test fun `one vector per 8x8 block, rounding up`() {
        assertEquals(Size(107, 60), ComputeMotionPlan.fieldSize(Size(854, 480)))
        assertEquals(Size(240, 135), ComputeMotionPlan.fieldSize(Size(1920, 1080)))
    }

    @Test fun `full resolution search is wide up to 720p and narrow above`() {
        assertEquals(8, ComputeMotionPlan.radius(0, 4, Size(854, 480)))
        assertEquals(8, ComputeMotionPlan.radius(0, 5, Size(1280, 720)))
        assertEquals(4, ComputeMotionPlan.radius(0, 5, Size(1920, 1080)))
        assertEquals(8, ComputeMotionPlan.radius(3, 5, Size(1920, 1080)))
    }

    @Test fun `splat texels always fit an 18-bit index`() {
        for (size in listOf(Size(1920, 1080), Size(1080, 1920), Size(1920, 1920), Size(854, 480))) {
            val step = ComputeMotionPlan.splatStep(size)
            val splat = ComputeMotionPlan.splatSize(size, step)
            assertTrue("$size", splat.w * splat.h <= ComputeMotionPlan.MAX_SPLAT_TEXELS)
        }
        // Level 0 is capped at a 1920 long side, so even a square frame fits
        // at the finest step: 480 x 480 texels.
        assertEquals(4, ComputeMotionPlan.splatStep(Size(1920, 1080)))
        assertEquals(4, ComputeMotionPlan.splatStep(Size(1920, 1920)))
        assertEquals(8, ComputeMotionPlan.splatStep(Size(4000, 4000)))
    }
}
