package com.ivor.ivormusic.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniSkipTest {

    private val width = 300f
    private val resist = 30f

    @Test
    fun `toward a neighbour the item follows the finger, up to one width`() {
        assertEquals(-120f, MiniSkip.displayed(-120f, width, hasNext = true, hasPrevious = false, resistPx = resist), 0f)
        assertEquals(-300f, MiniSkip.displayed(-500f, width, hasNext = true, hasPrevious = false, resistPx = resist), 0f)
        assertEquals(80f, MiniSkip.displayed(80f, width, hasNext = false, hasPrevious = true, resistPx = resist), 0f)
    }

    @Test
    fun `toward an empty edge it gives a little and never reaches the resist distance`() {
        val short = MiniSkip.displayed(30f, width, hasNext = true, hasPrevious = false, resistPx = resist)
        assertEquals("half the resist distance at one resist of travel", 15f, short, 0.001f)
        val far = MiniSkip.displayed(10_000f, width, hasNext = true, hasPrevious = false, resistPx = resist)
        assertTrue(far < resist)
        val left = MiniSkip.displayed(-30f, width, hasNext = false, hasPrevious = true, resistPx = resist)
        assertEquals(-15f, left, 0.001f)
    }

    @Test
    fun `left commits next and right commits previous, past the threshold`() {
        assertEquals(
            MiniSkip.Direction.NEXT,
            MiniSkip.decide(-100f, 0f, thresholdPx = 72f, flingVelocityPx = 700f, hasNext = true, hasPrevious = true)
        )
        assertEquals(
            MiniSkip.Direction.PREVIOUS,
            MiniSkip.decide(100f, 0f, thresholdPx = 72f, flingVelocityPx = 700f, hasNext = true, hasPrevious = true)
        )
    }

    @Test
    fun `a short drag commits only when flung the same way`() {
        assertNull(MiniSkip.decide(-30f, 0f, 72f, 700f, hasNext = true, hasPrevious = true))
        assertEquals(
            MiniSkip.Direction.NEXT,
            MiniSkip.decide(-30f, -900f, 72f, 700f, hasNext = true, hasPrevious = true)
        )
        // A fling back toward the start is an abandoned drag, not a skip.
        assertNull(MiniSkip.decide(-30f, 900f, 72f, 700f, hasNext = true, hasPrevious = true))
    }

    @Test
    fun `no neighbour means no commit however far`() {
        assertNull(MiniSkip.decide(-500f, -2000f, 72f, 700f, hasNext = false, hasPrevious = true))
        assertNull(MiniSkip.decide(500f, 2000f, 72f, 700f, hasNext = true, hasPrevious = false))
    }

    @Test
    fun `no travel is nothing`() {
        assertNull(MiniSkip.decide(0f, 5000f, 72f, 700f, hasNext = true, hasPrevious = true))
    }
}
