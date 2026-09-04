package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoResumePositionTest {

    @Test
    fun `positions before ten seconds are not retained`() {
        assertNull(retainedVideoResumePosition(9_999L, 600_000L))
    }

    @Test
    fun `middle position is retained`() {
        assertEquals(240_000L, retainedVideoResumePosition(240_000L, 600_000L))
    }

    @Test
    fun `last thirty seconds restart from beginning`() {
        assertNull(retainedVideoResumePosition(570_000L, 600_000L))
    }

    @Test
    fun `last five percent restart from beginning`() {
        assertNull(retainedVideoResumePosition(5_700_000L, 6_000_000L))
    }

    @Test
    fun `unknown duration is not retained`() {
        assertNull(retainedVideoResumePosition(60_000L, 0L))
    }
}
