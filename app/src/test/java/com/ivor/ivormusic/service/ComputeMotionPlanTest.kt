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

    @Test fun `the rounded-up 8 px grid already covers the frame, so the motion grid is the block grid`() {
        for (level in listOf(Size(854, 480), Size(1920, 1080), Size(1080, 1920), Size(853, 355))) {
            val grid = ComputeMotionPlan.fieldSize(level)
            assertEquals("$level", grid, ComputeMotionPlan.motionGrid(level, grid))
        }
    }

    @Test fun `a grid short of the frame gains SVP's extra column and row`() {
        assertEquals(Size(11, 6), ComputeMotionPlan.motionGrid(Size(84, 44), Size(10, 5)))
    }
}
