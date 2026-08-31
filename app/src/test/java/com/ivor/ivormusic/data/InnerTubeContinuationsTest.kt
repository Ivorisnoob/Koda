package com.ivor.ivormusic.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shapes here are hand-written minimal structures, not captured responses.
 * They record the container each form uses, which is the only part that matters
 * and the only part that drifts.
 */
class InnerTubeContinuationsTest {

    private fun rowsAndToken(rows: Int) = buildString {
        append("""{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[""")
        repeat(rows) {
            if (it > 0) append(",")
            append("""{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"v$it"}}}""")
        }
        append(""",{"continuationItemRenderer":{"continuationEndpoint":""")
        append("""{"continuationCommand":{"token":"NEXT"}}}}]}}]}""")
    }

    @Test
    fun `reads rows from appendContinuationItemsAction`() {
        val items = continuationItemsOrNull(JSONObject(rowsAndToken(100)))
        // 100 rows plus the trailing continuationItemRenderer.
        assertEquals(101, items?.length())
    }

    @Test
    fun `reads rows from reloadContinuationItemsCommand`() {
        val json = """
            {"onResponseReceivedActions":[{"reloadContinuationItemsCommand":
            {"continuationItems":[{"musicResponsiveListItemRenderer":{}}]}}]}
        """.trimIndent()
        assertEquals(1, continuationItemsOrNull(JSONObject(json))?.length())
    }

    /**
     * A playlist page carries a sectionList continuation whose token returns a
     * carousel of related playlists rather than more tracks, so the legacy
     * container must not be mistaken for this one.
     */
    @Test
    fun `legacy continuationContents is not claimed by the modern reader`() {
        val json = """
            {"continuationContents":{"musicPlaylistShelfContinuation":
            {"contents":[{"musicResponsiveListItemRenderer":{}}]}}}
        """.trimIndent()
        assertNull(continuationItemsOrNull(JSONObject(json)))
    }

    @Test
    fun `a page one browse yields nothing`() {
        val json = """
            {"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":
            {"sectionListRenderer":{"contents":[{"musicPlaylistShelfRenderer":{"contents":[]}}]}}}}}
        """.trimIndent()
        assertNull(continuationItemsOrNull(JSONObject(json)))
    }

    @Test
    fun `an empty or absent action list yields nothing rather than an empty array`() {
        assertNull(continuationItemsOrNull(JSONObject("""{"onResponseReceivedActions":[]}""")))
        assertNull(
            continuationItemsOrNull(
                JSONObject("""{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[]}}]}""")
            )
        )
        assertNull(continuationItemsOrNull(JSONObject("""{"responseContext":{}}""")))
    }
}
