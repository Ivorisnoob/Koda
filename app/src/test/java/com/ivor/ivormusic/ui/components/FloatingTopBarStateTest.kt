package com.ivor.ivormusic.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingTopBarStateTest {

    private fun state() = FloatingTopBarState().apply { hideAfterPx = 60f }

    /** The list moved by [dy]: negative is the page scrolling down. */
    private fun FloatingTopBarState.scrolled(dy: Float, available: Float = 0f) {
        connection.onPostScroll(
            consumed = Offset(0f, dy),
            available = Offset(0f, available),
            source = NestedScrollSource.UserInput
        )
    }

    @Test
    fun `stays up until the page has moved down past the threshold`() {
        val bar = state()
        bar.scrolled(-30f)
        assertFalse(bar.hidden)
        bar.scrolled(-30f)
        assertTrue(bar.hidden)
    }

    @Test
    fun `any upward scroll brings it back and restarts the count`() {
        val bar = state()
        bar.scrolled(-100f)
        assertTrue(bar.hidden)
        bar.scrolled(1f)
        assertFalse(bar.hidden)
        bar.scrolled(-59f)
        assertFalse(bar.hidden)
    }

    @Test
    fun `a drag the list does not follow leaves it where it is`() {
        val bar = state()
        // A short page or a pull at the top: the finger moved, the list did not.
        bar.scrolled(0f, available = -200f)
        assertFalse(bar.hidden)
        bar.scrolled(-100f)
        assertTrue(bar.hidden)
        bar.scrolled(0f, available = 200f)
        assertTrue(bar.hidden)
    }

    @Test
    fun `show resets it as at the top of the page`() {
        val bar = state()
        bar.scrolled(-100f)
        bar.show()
        assertFalse(bar.hidden)
        bar.scrolled(-30f)
        assertFalse(bar.hidden)
    }
}
