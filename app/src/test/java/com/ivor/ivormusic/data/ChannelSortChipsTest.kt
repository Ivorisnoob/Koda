package com.ivor.ivormusic.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hand-written minimal shapes recording where the sort chips live, which is the
 * part that drifted: the September 2026 Videos tab carries the token directly on
 * each chip, where the parser before it only looked inside a sheet.
 */
class ChannelSortChipsTest {

    private fun chip(label: String, token: String, selected: Boolean = false) =
        """{"chipViewModel":{"text":"$label","selected":$selected,
           "tapCommand":{"innertubeCommand":{"continuationCommand":{"token":"$token"}}}}}"""

    private fun videosTab(vararg chips: String) = JSONObject(
        """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
           {"richGridRenderer":{"contents":[],"header":{"chipBarViewModel":{"chips":[
           ${chips.joinToString(",")}
           ]}}}}}}]}}}"""
    )

    @Test
    fun `reads label, selection and token from direct chips`() {
        val options = ChannelSortChips.parse(
            videosTab(chip("Latest", "T_LATEST", selected = true), chip("Popular", "T_POP"), chip("Oldest", "T_OLD"))
        )
        assertEquals(listOf("Latest", "Popular", "Oldest"), options.map { it.label })
        assertTrue(options[0].selected)
        assertEquals("T_POP", options[1].token)
    }

    @Test
    fun `ignores chips outside a grid header`() {
        val scope = JSONObject(
            """{"somewhereElse":{"chipBarViewModel":{"chips":[${chip("All", "T_ALL")}]}}}"""
        )
        assertTrue(ChannelSortChips.parse(scope).isEmpty())
    }

    @Test
    fun `the old sheet shape yields nothing here, leaving it to the fallback`() {
        val sheet = JSONObject(
            """{"richGridRenderer":{"header":{"chipBarViewModel":{"chips":[{"chipViewModel":{"text":"Sort",
               "tapCommand":{"innertubeCommand":{"showSheetCommand":{}}}}}]}}}}"""
        )
        assertTrue(ChannelSortChips.parse(sheet).isEmpty())
    }

    @Test
    fun `popular is found by label`() {
        val options = listOf(
            ChannelSortOption("Latest", token = "A"),
            ChannelSortOption("Popular", token = "B"),
            ChannelSortOption("Oldest", token = "C"),
        )
        assertEquals("B", ChannelSortChips.popularToken(options))
    }

    @Test
    fun `popular falls back to the middle of exactly three orders`() {
        val options = listOf(
            ChannelSortOption("Neueste", token = "A"),
            ChannelSortOption("Beliebt", token = "B"),
            ChannelSortOption("Älteste", token = "C"),
        )
        assertEquals("B", ChannelSortChips.popularToken(options))
    }

    @Test
    fun `no popular order when the tab offers something else`() {
        val options = listOf(ChannelSortOption("Latest", token = "A"), ChannelSortOption("Oldest", token = "C"))
        assertNull(ChannelSortChips.popularToken(options))
    }
}
