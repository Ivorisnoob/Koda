package com.ivor.ivormusic.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The date windows are query text, so a wrong boundary is a silently wrong
 * result set rather than an error. Today in particular must be `after:`
 * yesterday: `after:` today leaked days-old videos when probed live.
 */
class VideoSearchFiltersTest {

    @Test
    fun `any time leaves the query alone`() {
        assertEquals("lofi", VideoSearchDateFilter.ANY.applyTo("lofi"))
    }

    @Test
    fun `today is a one-day window ending now`() {
        val yesterday = LocalDate.now().minusDays(1)
        assertEquals("lofi after:$yesterday", VideoSearchDateFilter.TODAY.applyTo("lofi"))
    }

    @Test
    fun `this week still reaches back seven days`() {
        val weekAgo = LocalDate.now().minusDays(7)
        assertEquals("lofi after:$weekAgo", VideoSearchDateFilter.WEEK.applyTo("lofi"))
    }

    @Test
    fun `today sits between any time and this week in the chip row`() {
        val order = VideoSearchDateFilter.entries.map { it.name }
        assertEquals(listOf("ANY", "TODAY", "WEEK"), order.take(3))
    }
}
