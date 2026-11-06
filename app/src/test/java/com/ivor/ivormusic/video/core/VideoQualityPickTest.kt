package com.ivor.ivormusic.video.core

import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoDynamicRange
import com.ivor.ivormusic.data.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoQualityPickTest {
    private val auto = ThemePreferences.VIDEO_QUALITY_AUTO

    private fun quality(
        label: String,
        isLive: Boolean = false,
        range: VideoDynamicRange = VideoDynamicRange.SDR,
    ) = VideoQuality(resolution = label, url = label, isLive = isLive, dynamicRange = range)

    private val ladder = listOf(quality("1080p"), quality("720p"), quality("480p"))

    @Test
    fun `auto takes the top of the ladder`() {
        assertEquals("1080p", pickStartingVideoQuality(ladder, auto, isExpanded = true).resolution)
    }

    @Test
    fun `a preference takes the best rung at or below it`() {
        assertEquals("720p", pickStartingVideoQuality(ladder, "720p", isExpanded = true).resolution)
        assertEquals("720p", pickStartingVideoQuality(ladder, "900p", isExpanded = true).resolution)
    }

    @Test
    fun `a preference below the ladder takes the lowest available`() {
        assertEquals("480p", pickStartingVideoQuality(ladder, "240p", isExpanded = true).resolution)
    }

    @Test
    fun `the mini player stays out of HDR when it can`() {
        val hdr = listOf(quality("2160p", range = VideoDynamicRange.HDR10), quality("1080p"))
        assertEquals("2160p", pickStartingVideoQuality(hdr, auto, isExpanded = true).resolution)
        assertEquals("1080p", pickStartingVideoQuality(hdr, auto, isExpanded = false).resolution)
    }

    @Test
    fun `an HDR-only ladder still plays in the mini player`() {
        val hdr = listOf(quality("2160p", range = VideoDynamicRange.HDR10))
        assertEquals("2160p", pickStartingVideoQuality(hdr, auto, isExpanded = false).resolution)
    }

    @Test
    fun `a live ladder keeps its Auto entry`() {
        val live = listOf(quality("Auto", isLive = true), quality("720p", isLive = true))
        assertEquals("Auto", pickStartingVideoQuality(live, auto, isExpanded = true).resolution)
        assertEquals("720p", pickStartingVideoQuality(live, "1080p", isExpanded = true).resolution)
    }

    @Test
    fun `a live ladder falls back to Auto rather than the lowest rung`() {
        val live = listOf(quality("Auto", isLive = true), quality("720p", isLive = true))
        assertEquals("Auto", pickStartingVideoQuality(live, "480p", isExpanded = true).resolution)
    }
}
