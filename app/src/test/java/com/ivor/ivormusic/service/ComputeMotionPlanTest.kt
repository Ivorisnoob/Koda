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

    @Test fun `720p is matched at full size over five levels`() {
        assertEquals(listOf(1280, 640, 320, 160, 80), ComputeMotionPlan.levels(1280, 720).map { it.w })
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

    @Test fun `only the coarsest level searches wide, the two under it re-search and the rest refine`() {
        // 720p and 1080p both have five levels, so both search alike.
        assertEquals(listOf(2, 2, 4, 4, 8), (0 until 5).map { ComputeMotionPlan.radius(it, 5) })
        assertEquals(listOf(2, 4, 4, 8), (0 until 4).map { ComputeMotionPlan.radius(it, 4) })
        // A frame already at the coarsest size is its own coarsest level.
        assertEquals(8, ComputeMotionPlan.radius(0, 1))
    }

    @Test fun `a finer level never searches wider than the one above it`() {
        for (count in 1..6) {
            for (level in 0 until count - 1) {
                assertTrue(
                    "level $level of $count",
                    ComputeMotionPlan.radius(level, count) <= ComputeMotionPlan.radius(level + 1, count)
                )
            }
        }
    }

    @Test fun `no level searches past the window the search shader loads`() {
        for (count in 1..6) {
            for (level in 0 until count) {
                val radius = ComputeMotionPlan.radius(level, count)
                // The SEARCH shader's MAX_RADIUS, which sizes its shared window and cost table.
                assertTrue("level $level of $count: $radius", radius in 1..8)
            }
        }
        assertEquals(8, ComputeMotionPlan.COARSE_RADIUS)
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
