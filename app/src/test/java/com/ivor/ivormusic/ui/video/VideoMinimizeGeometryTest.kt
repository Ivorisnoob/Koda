package com.ivor.ivormusic.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The minimize transition hands the playing picture from the watch page to the
 * mini bar's thumbnail. Whether it lands is arithmetic, and this is where that
 * arithmetic is checked: everything else about the animation needs a screen.
 */
class VideoMinimizeGeometryTest {

    // A 1080x2400 phone at 3x: 360x800dp, a 24dp status bar, a 16:9 video box.
    private val windowWidth = 1080f
    private val windowHeight = 2400f
    private val videoTop = 72f
    private val videoHeight = windowWidth * 9f / 16f
    private val thumbWidth = 276f // 92dp
    private val thumbHeight = thumbWidth * 9f / 16f
    private val thumbLeft = 84f // 16dp bar inset plus 12dp card padding
    private val thumbTop = 2100f
    private val thumbCorner = 36f

    private fun geometry(progress: Float, portrait: Boolean = false) = videoMinimizeGeometry(
        progress = progress,
        windowWidth = windowWidth,
        windowHeight = windowHeight,
        videoTop = videoTop,
        videoHeight = if (portrait) windowWidth * 16f / 9f else videoHeight,
        thumbLeft = thumbLeft,
        thumbTop = thumbTop,
        thumbWidth = thumbWidth,
        thumbHeight = thumbHeight,
        thumbCornerRadius = thumbCorner,
        isPortraitVideo = portrait,
    )

    @Test
    fun `fully expanded is the identity layout`() {
        val g = geometry(1f)

        assertEquals(0f, g.clipLeft, 0.01f)
        assertEquals(0f, g.clipTop, 0.01f)
        assertEquals(windowWidth, g.clipWidth, 0.01f)
        assertEquals(windowHeight, g.clipHeight, 0.01f)
        // The page is drawn exactly where it would be with no transition at all.
        assertEquals(0f, g.pageLeft, 0.01f)
        assertEquals(0f, g.pageTop, 0.01f)
        assertEquals(1f, g.scale, 0.001f)
        assertEquals(0f, g.cornerRadius, 0.01f)
    }

    @Test
    fun `fully collapsed puts the video exactly on the thumbnail`() {
        val g = geometry(0f)

        assertEquals(thumbLeft, g.clipLeft, 0.01f)
        assertEquals(thumbTop, g.clipTop, 0.01f)
        assertEquals(thumbWidth, g.clipWidth, 0.01f)
        assertEquals(thumbHeight, g.clipHeight, 0.01f)
        assertEquals(thumbCorner, g.cornerRadius, 0.01f)

        // The video box, which starts videoTop down the page, lands on the top
        // left of the thumbnail once the page is scaled and placed.
        val videoLeft = g.clipLeft + g.pageLeft
        val videoTopDrawn = g.clipTop + g.pageTop + g.scale * videoTop
        assertEquals(thumbLeft, videoLeft, 0.01f)
        assertEquals(thumbTop, videoTopDrawn, 0.01f)

        // And it fills it, because a 16:9 source in a 16:9 frame has nothing
        // left over in either direction.
        assertEquals(thumbWidth, g.scale * windowWidth, 0.01f)
        assertEquals(thumbHeight, g.scale * videoHeight, 0.01f)
    }

    @Test
    fun `a portrait source fits the frame rather than being cropped by it`() {
        val g = geometry(0f, portrait = true)
        val sourceHeight = windowWidth * 16f / 9f

        // Height fills the frame, width is inside it - the letterbox the mini
        // bar draws for the same video, rather than a crop that would jump at
        // the moment the bar takes the picture over.
        assertEquals(thumbHeight, g.scale * sourceHeight, 0.01f)
        assertTrue(g.scale * windowWidth <= thumbWidth + 0.01f)

        // Centred in the bars it leaves.
        val videoLeft = g.clipLeft + g.pageLeft
        val leftBar = videoLeft - thumbLeft
        val rightBar = (thumbLeft + thumbWidth) - (videoLeft + g.scale * windowWidth)
        assertEquals(leftBar, rightBar, 0.01f)
    }

    @Test
    fun `the clip never cuts into the video on the way down`() {
        var p = 0f
        while (p <= 1f) {
            val g = geometry(p)
            val videoLeft = g.clipLeft + g.pageLeft
            val videoTopDrawn = g.clipTop + g.pageTop + g.scale * videoTop
            val videoRight = videoLeft + g.scale * windowWidth
            val videoBottom = videoTopDrawn + g.scale * videoHeight

            assertTrue("left at $p", videoLeft >= g.clipLeft - 0.01f)
            assertTrue("top at $p", videoTopDrawn >= g.clipTop - 0.01f)
            assertTrue("right at $p", videoRight <= g.clipLeft + g.clipWidth + 0.01f)
            assertTrue("bottom at $p", videoBottom <= g.clipTop + g.clipHeight + 0.01f)
            p += 0.05f
        }
    }

    @Test
    fun `progress outside the unit range cannot invert the layout`() {
        // A spring may overshoot either end; the geometry has to stay a layout.
        listOf(-0.4f, 1.6f).forEach { p ->
            val g = geometry(p)
            assertTrue("width at $p", g.clipWidth >= 0f)
            assertTrue("height at $p", g.clipHeight >= 0f)
            assertTrue("scale at $p", g.scale > 0f)
        }
        assertEquals(geometry(0f), geometry(-0.4f))
        assertEquals(geometry(1f), geometry(1.6f))
    }

    @Test
    fun `a zero sized window does not divide by zero`() {
        val g = videoMinimizeGeometry(
            progress = 0f,
            windowWidth = 0f,
            windowHeight = 0f,
            videoTop = 0f,
            videoHeight = 0f,
            thumbLeft = 0f,
            thumbTop = 0f,
            thumbWidth = 0f,
            thumbHeight = 0f,
            thumbCornerRadius = 0f,
            isPortraitVideo = false,
        )
        assertTrue(g.scale.isFinite())
        assertTrue(g.pageTop.isFinite())
    }
}
