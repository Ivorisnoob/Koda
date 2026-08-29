package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LockupVideoMetadataTest {
    @Test
    fun `compact watch-next parts preserve views and upload date`() {
        val stats = parseLockupVideoStats(
            listOf("263K" to null, "1h ago" to null)
        )

        assertEquals("263K", stats.viewCount)
        assertEquals("1h ago", stats.uploadedDate)
    }

    @Test
    fun `legacy combined part remains supported`() {
        val stats = parseLockupVideoStats(
            listOf("376K views • 2 days ago" to null)
        )

        assertEquals("376K views", stats.viewCount)
        assertEquals("2 days ago", stats.uploadedDate)
    }

    @Test
    fun `accessibility label identifies compact view count`() {
        val stats = parseLockupVideoStats(
            listOf("575K" to "575 thousand views", "2h ago" to "2 hours ago")
        )

        assertEquals("575K", stats.viewCount)
        assertEquals("2h ago", stats.uploadedDate)
    }

    @Test
    fun `live watching count is not treated as an upload date`() {
        val stats = parseLockupVideoStats(
            listOf("1.2K watching" to null)
        )

        assertEquals("1.2K watching", stats.viewCount)
        assertEquals("", stats.uploadedDate)
    }
}
