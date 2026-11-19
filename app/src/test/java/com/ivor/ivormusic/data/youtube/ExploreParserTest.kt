package com.ivor.ivormusic.data.youtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reduced from anonymous WEB browses of the Explore pages, November 2026. */
class ExploreParserTest {

    private fun runs(text: String) = """{"runs":[{"text":"$text"}]}"""

    @Test
    fun `reads every card shape in page order, once each, without shorts`() {
        val root = JSONObject(
            """
            {"contents":{"sectionListRenderer":{"contents":[
              {"shelfRenderer":{"title":{"simpleText":"Top 100"},"content":{"gridRenderer":{"items":[
                {"gridVideoRenderer":{"videoId":"hype1","title":${runs("Hyped")},"shortBylineText":${runs("Creator A")}}}
              ]}}}},
              {"videoCardRenderer":{"videoId":"game1","title":${runs("GTA VI Trailer")},
                 "bylineText":${runs("Rockstar Games")},"lengthText":{"simpleText":"1:31"}}},
              {"richItemRenderer":{"content":{"videoRenderer":{"videoId":"news1","title":${runs("Headline")},
                 "ownerText":${runs("Newsroom")},"lengthText":{"simpleText":"4:05"}}}}},
              {"reelShelfRenderer":{"items":[{"reelItemRenderer":{"videoId":"short1"}}]}},
              {"gridVideoRenderer":{"videoId":"hype1","title":${runs("Hyped again")}}}
            ]}}}
            """.trimIndent()
        )
        val videos = parseExploreVideos(root)
        assertEquals(listOf("hype1", "game1", "news1"), videos.map { it.videoId })
        assertEquals("Rockstar Games", videos[1].channelName)
        assertEquals(91L, videos[1].duration)
        assertEquals("Newsroom", videos[2].channelName)
    }
}
