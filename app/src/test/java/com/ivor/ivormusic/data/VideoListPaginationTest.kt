package com.ivor.ivormusic.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoListPaginationTest {
    private fun fixture(name: String): JSONObject = JSONObject(
        checkNotNull(javaClass.getResource("/pagination/$name.json")).readText()
    )

    @Test fun historyInitialAndContinuationUseTheSameListToken() {
        assertEquals("next-page", videoListContinuationToken(fixture("history")))
        assertEquals("next-page", videoListContinuationToken(fixture("history_next")))
    }

    @Test fun sortedSearchIgnoresHeaderChipTokensOnBothPages() {
        assertEquals("next-page", videoListContinuationToken(fixture("sorted"), search = true))
        assertEquals("next-page", videoListContinuationToken(fixture("sorted_next"), search = true))
    }

    @Test fun exhaustedSortedSearchDoesNotFollowTheRemainingHeaderChips() {
        val root = fixture("sorted_next")
        root.remove("onResponseReceivedCommands")
        assertNull(videoListContinuationToken(root, search = true))
    }

    @Test fun emptyOrErrorResponseHasNoListToken() {
        assertNull(videoListContinuationToken(JSONObject()))
        assertNull(videoListContinuationToken(JSONObject("""{"error":{"code":400}}""")))
    }
}
