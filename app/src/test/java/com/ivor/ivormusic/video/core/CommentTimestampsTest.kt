package com.ivor.ivormusic.video.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentTimestampsTest {

    @Test fun `minutes and seconds`() {
        assertEquals(83_000L, firstTimestampMs("the drop at 1:23 is unreal"))
    }

    @Test fun `hours minutes and seconds`() {
        assertEquals(3_753_000L, firstTimestampMs("1:02:33 best part"))
    }

    @Test fun `the first time mentioned wins`() {
        assertEquals(725_000L, firstTimestampMs("12:05 and again at 14:00"))
    }

    @Test fun `seconds past 59 are not a time`() {
        assertNull(firstTimestampMs("final score 3:99"))
    }

    @Test fun `minutes past 59 are not a time once hours are given`() {
        assertNull(firstTimestampMs("1:75:00"))
    }

    @Test fun `digits glued to the time are not a time`() {
        assertNull(firstTimestampMs("call 123:456"))
    }

    @Test fun `no time at all`() {
        assertNull(firstTimestampMs("great video"))
    }
}
