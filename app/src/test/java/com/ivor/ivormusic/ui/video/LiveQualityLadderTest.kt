package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.data.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveQualityLadderTest {

    private val auto = VideoQuality(
        resolution = "Auto (HLS)",
        url = "https://manifest.googlevideo.com/hls",
        format = "HLS",
        isDASH = true,
        isLive = true,
    )

    @Test
    fun `master playlist renditions become a highest-first ladder under Auto`() {
        // The variant set a live master playlist declared in September 2026.
        val ladder = liveVideoQualityLadder(
            auto,
            listOf(
                LiveRendition(256, 144, 15f),
                LiveRendition(426, 240, 30f),
                LiveRendition(1920, 1080, 30f),
                LiveRendition(1280, 720, 30f),
                LiveRendition(1920, 1080, 60f),
            ),
        )
        assertEquals(
            listOf("Auto", "1080p60", "1080p", "720p", "240p", "144p"),
            ladder.map { it.resolution },
        )
        assertEquals(true, ladder.all { it.isLive && it.url == auto.url })
        assertEquals(1920 to 1080, ladder[1].width to ladder[1].height)
    }

    @Test
    fun `a vertical broadcast is labelled by its short edge`() {
        val ladder = liveVideoQualityLadder(
            auto,
            listOf(LiveRendition(720, 1280, 30f), LiveRendition(360, 640, 30f)),
        )
        assertEquals(listOf("Auto", "720p", "360p"), ladder.map { it.resolution })
        assertEquals(720 to 1280, ladder[1].width to ladder[1].height)
    }

    @Test
    fun `fewer than two usable renditions is no choice`() {
        val ladder = liveVideoQualityLadder(
            auto,
            listOf(LiveRendition(1280, 720, 30f), LiveRendition(0, 0, 0f)),
        )
        assertEquals(listOf("Auto"), ladder.map { it.resolution })
    }
}
