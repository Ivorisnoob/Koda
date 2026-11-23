package com.ivor.ivormusic.ui.channel

import com.ivor.ivormusic.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelSearchSortTest {

    private val now = 1_800_000_000_000L

    private fun video(id: String, uploaded: String?, views: String) = VideoItem(
        videoId = id,
        title = id,
        channelName = "c",
        thumbnailUrl = null,
        duration = 60,
        viewCount = views,
        uploadedDate = uploaded,
    )

    private val results = listOf(
        video("a", "3 weeks ago", "5.4K views"),
        video("b", "2 days ago", "1.2M views"),
        video("c", null, "No views"),
        video("d", "1 year ago", "1,234 views"),
        video("e", "2 days ago", "800 views"),
    )

    private fun ids(sort: ChannelSearchSort) = sortChannelSearch(results, sort, now).map { it.videoId }

    @Test
    fun `relevance keeps YouTube's order`() {
        assertEquals(listOf("a", "b", "c", "d", "e"), ids(ChannelSearchSort.RELEVANCE))
    }

    @Test
    fun `newest and oldest put undated videos last and keep ties in relevance order`() {
        assertEquals(listOf("b", "e", "a", "d", "c"), ids(ChannelSearchSort.NEWEST))
        assertEquals(listOf("d", "a", "b", "e", "c"), ids(ChannelSearchSort.OLDEST))
    }

    @Test
    fun `most viewed reads compact counts`() {
        assertEquals(listOf("b", "a", "d", "e", "c"), ids(ChannelSearchSort.MOST_VIEWED))
    }

    @Test
    fun `view counts as YouTube writes them`() {
        assertEquals(5_400L, parseViewCount("5.4K views"))
        assertEquals(1_200_000L, parseViewCount("1.2M views"))
        assertEquals(1_234L, parseViewCount("1,234 views"))
        assertEquals(2_000_000_000L, parseViewCount("2B views"))
        assertEquals(0L, parseViewCount("No views"))
        assertNull(parseViewCount(""))
        assertNull(parseViewCount("views"))
    }
}
