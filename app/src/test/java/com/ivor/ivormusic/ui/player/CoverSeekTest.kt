package com.ivor.ivormusic.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverSeekTest {
    @Test
    fun `left half goes back and right half forward`() {
        assertEquals(-1, coverSeekDirection(10f, 400))
        assertEquals(-1, coverSeekDirection(199f, 400))
        assertEquals(1, coverSeekDirection(200f, 400))
        assertEquals(1, coverSeekDirection(390f, 400))
    }

    @Test
    fun `a cover with no width seeks forward`() {
        assertEquals(1, coverSeekDirection(0f, 0))
    }
}
