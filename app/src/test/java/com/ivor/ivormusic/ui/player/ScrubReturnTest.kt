package com.ivor.ivormusic.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubReturnTest {

    private val enter = 0.03f
    private val exit = 0.05f

    private fun step(fraction: Float, armed: Boolean, snapped: Boolean) =
        ScrubReturn.step(fraction, origin = 0.5f, armed = armed, snapped = snapped, enter = enter, exit = exit)

    @Test
    fun `a drag starting on the origin is not held there`() {
        val s = step(0.51f, armed = false, snapped = false)
        assertEquals(0.51f, s.fraction, 0f)
        assertFalse(s.armed)
        assertFalse(s.snapped)
    }

    @Test
    fun `leaving past the exit arms it without snapping`() {
        val s = step(0.6f, armed = false, snapped = false)
        assertTrue(s.armed)
        assertFalse(s.snapped)
        assertEquals(0.6f, s.fraction, 0f)
    }

    @Test
    fun `coming back inside the enter zone lands exactly on the origin`() {
        val s = step(0.52f, armed = true, snapped = false)
        assertTrue(s.snapped)
        assertEquals(0.5f, s.fraction, 0f)
    }

    @Test
    fun `between enter and exit keeps whatever state it was in`() {
        // 0.04 from the origin: outside enter, inside exit.
        assertFalse(step(0.54f, armed = true, snapped = false).snapped)
        val held = step(0.54f, armed = true, snapped = true)
        assertTrue(held.snapped)
        assertEquals(0.5f, held.fraction, 0f)
    }

    @Test
    fun `past the exit lets go and follows the finger`() {
        val s = step(0.56f, armed = true, snapped = true)
        assertFalse(s.snapped)
        assertEquals(0.56f, s.fraction, 0f)
    }

    @Test
    fun `no geometry means no magnet`() {
        val s = ScrubReturn.step(0.5f, origin = 0.5f, armed = true, snapped = false, enter = 0f, exit = 0f)
        assertFalse(s.snapped)
    }

    @Test
    fun `release reports a drag that ended on the marker, and resets`() {
        val point = ScrubReturnPoint()
        point.updateGeometry(trackWidthPx = 1000, enterPx = 30f, exitPx = 50f)
        point.follow(value = 800f, rangeEnd = 1000f) { 0.5f }   // away: arms
        val back = point.follow(value = 510f, rangeEnd = 1000f) { 0.9f }
        assertEquals("snaps to the origin read at the start, not later", 500f, back, 0.01f)
        assertTrue(point.release())
        assertEquals(ScrubReturnPoint.NO_ORIGIN, point.origin, 0f)
        assertFalse(point.isSnapped)
    }

    @Test
    fun `release after a plain seek is not a cancel`() {
        val point = ScrubReturnPoint()
        point.updateGeometry(trackWidthPx = 1000, enterPx = 30f, exitPx = 50f)
        point.follow(value = 900f, rangeEnd = 1000f) { 0.2f }
        assertFalse(point.release())
    }
}
