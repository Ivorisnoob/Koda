package com.ivor.ivormusic.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricTimingLayoutTest {

    @Test
    fun `missing span punctuation keeps the displayed ending`() {
        assertEquals(
            listOf(0, 0, 1, 2, 3, 4),
            (0 until 6).map { index ->
                lyricTimingIndex(displayIndex = index, displayCount = 6, timingCount = 5)
            }
        )
    }

    @Test
    fun `provider timing and displayed text share both endpoints`() {
        assertEquals(0, lyricTimingIndex(displayIndex = 0, displayCount = 11, timingCount = 4))
        assertEquals(3, lyricTimingIndex(displayIndex = 10, displayCount = 11, timingCount = 4))
    }
}
