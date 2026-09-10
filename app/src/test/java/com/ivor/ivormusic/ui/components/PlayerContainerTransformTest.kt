package com.ivor.ivormusic.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The container transform both players share. Whether the container lands on
 * the collapsed bar and on the window, and whether it ever shows Home through
 * itself, is arithmetic - everything else about the animation needs a screen.
 */
class PlayerContainerTransformTest {

    // A 1080x2400 phone at 3x: 360x800dp, with the video bar 88dp tall, 16dp
    // in from the sides, 16dp plus a 48dp navigation inset up from the bottom,
    // and 28dp corners.
    private val windowWidth = 1080f
    private val windowHeight = 2400f
    private val barHeight = 264f
    private val barSide = 48f
    private val barBottom = 192f
    private val barCorner = 84f

    private fun frame(progress: Float, corner: Float = barCorner) = playerContainerFrame(
        progress = progress,
        windowWidth = windowWidth,
        windowHeight = windowHeight,
        collapsedHeight = barHeight,
        collapsedSideInset = barSide,
        collapsedBottomInset = barBottom,
        collapsedCornerRadius = corner,
    )

    private fun progressSteps(): List<Float> = (0..20).map { it / 20f }

    @Test
    fun `fully expanded is the whole window with square corners`() {
        val f = frame(1f)

        assertEquals(0f, f.left, 0.01f)
        assertEquals(0f, f.top, 0.01f)
        assertEquals(windowWidth, f.width, 0.01f)
        assertEquals(windowHeight, f.height, 0.01f)
        assertEquals(0f, f.cornerRadius, 0.01f)
    }

    @Test
    fun `fully collapsed is the resting bar to the pixel`() {
        val f = frame(0f)

        // The resting bar is laid out by padding: inset from both sides and
        // from the bottom. The swap to it at rest must not move an edge.
        assertEquals(barSide, f.left, 0.01f)
        assertEquals(windowWidth - 2 * barSide, f.width, 0.01f)
        assertEquals(windowHeight - barBottom - barHeight, f.top, 0.01f)
        assertEquals(barHeight, f.height, 0.01f)
        assertEquals(barCorner, f.cornerRadius, 0.01f)
    }

    @Test
    fun `the container only ever grows as the player opens`() {
        progressSteps().zipWithNext().forEach { (a, b) ->
            val before = frame(a)
            val after = frame(b)
            assertTrue("height at $b", after.height >= before.height)
            assertTrue("width at $b", after.width >= before.width)
            assertTrue("top at $b", after.top <= before.top)
            assertTrue("left at $b", after.left <= before.left)
            // The bottom edge travels from the bar's down to the window's and
            // never leaves the window on the way.
            val bottom = after.top + after.height
            assertTrue("bottom at $b", bottom <= windowHeight + 0.01f)
            assertTrue("bottom at $b", bottom >= windowHeight - barBottom - 0.01f)
        }
    }

    @Test
    fun `the corner never exceeds half the shorter side`() {
        // An oversized collapsed radius is clamped rather than drawn distorted.
        progressSteps().forEach { p ->
            val f = frame(p, corner = 10_000f)
            assertTrue("corner at $p", f.cornerRadius <= minOf(f.width, f.height) / 2f + 0.01f)
            assertTrue("corner at $p", f.cornerRadius >= 0f)
        }
    }

    @Test
    fun `progress outside the unit range cannot invert the layout`() {
        // A spring may overshoot either end; the frame has to stay a layout.
        assertEquals(frame(0f), frame(-0.4f))
        assertEquals(frame(1f), frame(1.6f))
    }

    @Test
    fun `a degenerate window does not produce a negative frame`() {
        val f = playerContainerFrame(
            progress = 0f,
            windowWidth = 20f,
            windowHeight = 0f,
            collapsedHeight = 0f,
            collapsedSideInset = 48f,
            collapsedBottomInset = 0f,
            collapsedCornerRadius = 84f,
        )
        assertTrue(f.width >= 0f)
        assertTrue(f.height >= 0f)
        assertTrue(f.cornerRadius >= 0f)
    }

    @Test
    fun `each layer is solid at its own end`() {
        assertEquals(1f, containerMiniAlpha(0f), 0.001f)
        assertEquals(0f, containerMiniAlpha(1f), 0.001f)
        assertEquals(0f, containerFullAlpha(0f), 0.001f)
        assertEquals(1f, containerFullAlpha(1f), 0.001f)
        assertEquals(1f, containerBackdropAlpha(0f), 0.001f)
        assertEquals(0f, containerBackdropAlpha(1f), 0.001f)
    }

    @Test
    fun `the expanded content is solid by the halfway point`() {
        assertEquals(1f, containerFullAlpha(0.5f), 0.001f)
    }

    @Test
    fun `the container never turns see-through before the content is solid`() {
        // Fading both together showed Home through the middle of the expansion.
        progressSteps().forEach { p ->
            if (containerBackdropAlpha(p) < 1f) {
                assertEquals("content at $p", 1f, containerFullAlpha(p), 0.001f)
            }
        }
    }

    @Test
    fun `the two layers overlap rather than leaving an empty card between them`() {
        progressSteps().forEach { p ->
            assertTrue("empty at $p", containerMiniAlpha(p) > 0f || containerFullAlpha(p) > 0f)
        }
    }
}
