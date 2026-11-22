package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.VideoAccess
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reduced from anonymous WEB responses, November 2026. */
class VideoAccessParserTest {

    private fun lockup(badgeStyle: String?) = JSONObject(
        """
        {"contentId":"abcdefghijk","metadata":{"lockupMetadataViewModel":{
          "title":{"content":"A video"},
          "metadata":{"contentMetadataViewModel":{"metadataRows":[
            {"metadataParts":[{"text":{"content":"12K views"}},{"text":{"content":"2 days ago"}}]}
            ${if (badgeStyle != null) """,{"badges":[{"badgeViewModel":{"badgeText":"Members only","badgeStyle":"$badgeStyle"}}]}""" else ""}
          ]}}}}}
        """.trimIndent()
    )

    @Test
    fun `a members-only lockup is marked`() {
        assertEquals(VideoAccess.MEMBERS_ONLY, parseLockupViewModel(lockup("BADGE_MEMBERS_ONLY"))?.access)
        assertNull(parseLockupViewModel(lockup(null))?.access)
        assertNull(parseLockupViewModel(lockup("BADGE_OVERLAY_WHITE"))?.access)
    }

    private fun movie(offer: String?) = JSONObject(
        """
        {"videoId":"vO2N0LGvhWc","title":{"runs":[{"text":"Oppenheimer"}]},
         "longBylineText":{"runs":[{"text":"YouTube Movies & TV"}]},
         "lengthText":{"simpleText":"3:02:47"}
         ${if (offer != null) ""","offerButtons":[{"buttonRenderer":{"text":{"runs":[{"text":"$offer"}]}}}]""" else ""}}
        """.trimIndent()
    )

    @Test
    fun `a film for sale is paid, a free one is not`() {
        assertEquals(VideoAccess.PAID, parseMovieRenderer(movie("Buy or rent"))?.access)
        assertNull(parseMovieRenderer(movie("Watch free with ads"))?.access)
        assertNull(parseMovieRenderer(movie(null))?.access)
        assertEquals("Oppenheimer", parseMovieRenderer(movie("Buy or rent"))?.title)
    }
}
