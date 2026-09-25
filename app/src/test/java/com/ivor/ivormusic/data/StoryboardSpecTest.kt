package com.ivor.ivormusic.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The spec below is a real visionOS `/player` storyboard [verified September
 * 2026, `.probe/visionos_keys.py`], with only the `sqp` token shortened. Its
 * level 0 is the zero-duration placeholder YouTube never serves.
 */
class StoryboardSpecTest {

    private val vodSpec =
        "https://i.ytimg.com/sb/ZIvNNe2FwHo/storyboard3_L\$L/\$N.jpg?sqp=-oaymw" +
            "|48#27#100#10#10#0#default#rs\$AOn4CLDb" +
            "|80#45#117#10#10#2000#M\$M#rs\$AOn4CLBH" +
            "|160#90#117#5#5#2000#M\$M#rs\$AOn4CLB7"

    private fun response(renderer: String, spec: String) = JSONObject().put(
        "storyboards",
        JSONObject().put(renderer, JSONObject().put("spec", spec)),
    )

    @Test
    fun picksTheSharpestServedLevelAndExpandsItsPages() {
        val preview = parseStoryboardSeekPreview(response("playerStoryboardSpecRenderer", vodSpec))!!

        assertEquals(160, preview.frameWidthPx)
        assertEquals(90, preview.frameHeightPx)
        assertEquals(117, preview.totalFrameCount)
        assertEquals(5, preview.framesPerPageX)
        assertEquals(5, preview.framesPerPageY)
        assertEquals(2000, preview.durationPerFrameMs)
        // 117 frames at 25 a page is five pages, M0..M4, at level index 2.
        assertEquals(5, preview.pageUrls.size)
        assertEquals(
            "https://i.ytimg.com/sb/ZIvNNe2FwHo/storyboard3_L2/M0.jpg?sqp=-oaymw&sigh=rs\$AOn4CLB7",
            preview.pageUrls.first(),
        )
        assertEquals(
            "https://i.ytimg.com/sb/ZIvNNe2FwHo/storyboard3_L2/M4.jpg?sqp=-oaymw&sigh=rs\$AOn4CLB7",
            preview.pageUrls.last(),
        )
    }

    @Test
    fun liveSpecsYieldNothingAsTheyDidThroughNewPipe() {
        val live = "https://i.ytimg.com/sb/Io-G_aiF8HA/storyboard_live_90_3x3_b15/M\$M.jpg?rs=AOn4#159#90#3#3"
        assertNull(parseStoryboardSeekPreview(response("playerLiveStoryboardSpecRenderer", live)))
    }

    @Test
    fun missingOrMalformedSpecsYieldNothing() {
        assertNull(parseStoryboardSeekPreview(JSONObject()))
        assertNull(parseStoryboardSeekPreview(response("playerStoryboardSpecRenderer", "")))
        assertNull(
            parseStoryboardSeekPreview(
                response("playerStoryboardSpecRenderer", "https://x/\$L|a#b#c#d#e#f#g#h")
            )
        )
    }
}
