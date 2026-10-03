package com.ivor.ivormusic.ui.player

import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.ui.theme.WindowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFrameTest {

    private fun frame(width: Int, height: Int) = playerFrameFor(WindowLayout(width.dp, height.dp))

    @Test
    fun `phones held upright keep the style full screen`() {
        assertEquals(PlayerFrameKind.FULL, frame(412, 915).kind)
        assertEquals(PlayerFrameKind.FULL, frame(360, 640).kind)
    }

    @Test
    fun `a phone on its side gets the landscape layout`() {
        assertEquals(PlayerFrameKind.LANDSCAPE, frame(915, 412).kind)
        assertEquals(PlayerFrameKind.LANDSCAPE, frame(640, 360).kind)
    }

    @Test
    fun `a short split-screen half on a tablet is landscape too`() {
        assertEquals(PlayerFrameKind.LANDSCAPE, frame(1280, 400).kind)
    }

    @Test
    fun `a tablet sideways puts the style on a stage beside the companion`() {
        val tablet = frame(1280, 800)
        assertEquals(PlayerFrameKind.STAGE, tablet.kind)
        // Phone proportions at this height, and the rest is the panel.
        assertEquals(448f, tablet.stageWidth.value, 0.5f)
        assertTrue(1280.dp - tablet.stageWidth >= COMPANION_MIN_WIDTH)
    }

    @Test
    fun `an unfolded foldable held sideways is a stage`() {
        assertEquals(PlayerFrameKind.STAGE, frame(882, 690).kind)
    }

    @Test
    fun `tablets and foldables held upright keep the style full screen`() {
        // The companion would be narrower than a queue row needs.
        assertEquals(PlayerFrameKind.FULL, frame(800, 1280).kind)
        assertEquals(PlayerFrameKind.FULL, frame(690, 882).kind)
    }

    @Test
    fun `the stage never grows past what a style is drawn at`() {
        assertEquals(600f, frame(2560, 1600).stageWidth.value, 0.01f)
    }
}
