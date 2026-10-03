package com.ivor.ivormusic.service

import com.ivor.ivormusic.service.ComputeMotionEngine.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeMotionPlanTest {

    @Test fun `every size is searched at 320 px down to about 40 px`() {
        for ((w, h) in listOf(854 to 480, 1280 to 720, 1920 to 1080, 3840 to 2160)) {
            val levels = ComputeMotionPlan.levels(w, h)
            assertEquals("${w}x$h", Size(320, 180), levels.first())
            assertEquals("${w}x$h", listOf(320, 160, 80, 40), levels.map { it.w })
        }
    }

    @Test fun `a portrait video is searched at 320 px on its long side`() {
        assertEquals(Size(180, 320), ComputeMotionPlan.levels(1080, 1920).first())
    }

    @Test fun `a video smaller than the search size keeps its own size`() {
        val levels = ComputeMotionPlan.levels(256, 144)
        assertEquals(Size(256, 144), levels.first())
        assertTrue(maxOf(levels.last().w, levels.last().h) <= ComputeMotionPlan.COARSEST_LONG_SIDE)
    }

    @Test fun `the first luma step covers its footprint, and a halving is a 2x2 box`() {
        assertEquals(2, ComputeMotionPlan.lumaTaps(2f))
        assertEquals(2, ComputeMotionPlan.lumaTaps(0.5f))
        assertEquals(2, ComputeMotionPlan.lumaTaps(3.5f))
        assertEquals(3, ComputeMotionPlan.lumaTaps(6f)) // 1080p to 320
        assertEquals(6, ComputeMotionPlan.lumaTaps(12f)) // 4K to 320
        assertEquals(ComputeMotionPlan.MAX_LUMA_TAPS, ComputeMotionPlan.lumaTaps(100f))
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

    @Test fun `only the coarsest level searches wide, the one under it re-searches and the rest refine`() {
        // Every video from 480p up has these four levels.
        assertEquals(listOf(1, 1, 2, 8), (0 until 4).map { ComputeMotionPlan.radius(it, 4) })
        assertEquals(listOf(1, 1, 1, 2, 8), (0 until 5).map { ComputeMotionPlan.radius(it, 5) })
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
