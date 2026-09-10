package com.ivor.ivormusic.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VideoWatchProgressTest {
    private fun lockup(percent: String) = JSONObject("""
        {"contentImage":{"thumbnailViewModel":{"overlays":[
          {"thumbnailBottomOverlayViewModel":{"progressBar":{
            "thumbnailOverlayProgressBarViewModel":{"startPercent":$percent}
          }}}
        ]}}}
    """)

    @Test fun `probed history overlay reads a partial watch`() {
        assertEquals(0.39f, parseVideoWatchProgress(lockup("39"))!!, 0.0001f)
    }

    @Test fun `completed watches remain visible`() {
        assertEquals(1f, parseVideoWatchProgress(lockup("100"))!!, 0f)
    }

    @Test fun `missing or invalid progress is unknown rather than completed`() {
        assertNull(parseVideoWatchProgress(JSONObject()))
        assertNull(parseVideoWatchProgress(lockup("null")))
        assertNull(parseVideoWatchProgress(lockup("\"unknown\"")))
    }

    @Test fun `progress is bounded`() {
        assertEquals(0f, parseVideoWatchProgress(lockup("-10"))!!, 0f)
        assertEquals(1f, parseVideoWatchProgress(lockup("110"))!!, 0f)
    }

    @Test fun `buffering and paused time do not qualify a watch`() {
        val clock = VideoWatchClock(10_000L)
        for (time in 0L..60_000L step 1_000L) assertFalse(clock.sample(time, false, true))
        assertEquals(0L, clock.playedMs)
        assertFalse(clock.sample(61_000L, true, true))
        for (time in 62_000L..70_000L step 1_000L) assertFalse(clock.sample(time, true, true))
        assertTrue(clock.sample(71_000L, true, true))
    }

    @Test fun `pause preserves actual watched time without adding idle time`() {
        val clock = VideoWatchClock(5_000L)
        clock.sample(0L, true, true)
        clock.sample(1_000L, true, true)
        clock.sample(2_000L, false, true)
        clock.sample(30_000L, false, true)
        assertEquals(2_000L, clock.playedMs)
        clock.sample(31_000L, true, true)
        clock.sample(32_000L, true, true)
        clock.sample(33_000L, true, true)
        assertTrue(clock.sample(34_000L, true, true))
    }

    @Test fun `privacy gate discards qualification and disabled viewing`() {
        val clock = VideoWatchClock(5_000L)
        for (time in 0L..5_000L step 1_000L) clock.sample(time, true, true)
        assertFalse(clock.sample(6_000L, true, false))
        assertFalse(clock.sample(7_000L, true, true))
        assertEquals(0L, clock.playedMs)
    }

    @Test fun `a blocked main thread cannot manufacture a whole watch`() {
        val clock = VideoWatchClock(10_000L)
        clock.sample(0L, true, true)
        assertFalse(clock.sample(60_000L, true, true))
        assertEquals(2_000L, clock.playedMs)
    }
}
