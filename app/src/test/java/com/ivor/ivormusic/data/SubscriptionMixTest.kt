package com.ivor.ivormusic.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionMixTest {

    private fun video(id: String, channel: String) = VideoItem(
        videoId = id,
        title = id,
        channelName = channel,
        channelId = channel,
        thumbnailUrl = null,
        duration = 60L,
        viewCount = ""
    )

    private fun pool(channel: String, recent: Int, catalogue: Int) = ChannelMixPool(
        channelId = channel,
        recent = (0 until recent).map { video("$channel-r$it", channel) },
        catalogue = (0 until catalogue).map { video("$channel-c$it", channel) }
    )

    @Test
    fun `each channel gives its share, two from the catalogue and one recent`() {
        val page = SubscriptionMix.buildPage(
            listOf(pool("a", 30, 30), pool("b", 30, 30)),
            shownIds = emptySet(),
            random = Random(1)
        )
        assertEquals(6, page.size)
        for (channel in listOf("a", "b")) {
            val mine = page.filter { it.channelId == channel }
            assertEquals(2, mine.count { "-c" in it.videoId })
            assertEquals(1, mine.count { "-r" in it.videoId })
        }
    }

    @Test
    fun `a channel with no popular order still fills its share from recent`() {
        val page = SubscriptionMix.buildPage(listOf(pool("a", 30, 0)), emptySet(), Random(2))
        assertEquals(SubscriptionMix.VIDEOS_PER_CHANNEL, page.size)
    }

    @Test
    fun `videos already shown are never picked again`() {
        val source = pool("a", 2, 2)
        val shown = setOf("a-r0", "a-c0", "a-c1")
        val page = SubscriptionMix.buildPage(listOf(source), shown, Random(3))
        assertEquals(listOf("a-r1"), page.map { it.videoId })
        assertFalse(SubscriptionMix.hasUnseen(listOf(source), shown + "a-r1"))
    }

    @Test
    fun `a collaboration in two pools appears once`() {
        val shared = video("collab", "a")
        val pools = listOf(
            ChannelMixPool("a", recent = listOf(shared), catalogue = emptyList()),
            ChannelMixPool("b", recent = listOf(shared.copy(channelId = "b")), catalogue = emptyList())
        )
        val page = SubscriptionMix.buildPage(pools, emptySet(), Random(4))
        assertEquals(1, page.count { it.videoId == "collab" })
    }

    @Test
    fun `creators are spread so neighbours differ whenever possible`() {
        val videos = (0 until 6).map { video("a$it", "a") } +
            (0 until 5).map { video("b$it", "b") } +
            (0 until 2).map { video("c$it", "c") }
        repeat(20) { seed ->
            val spread = SubscriptionMix.spreadCreators(videos.shuffled(Random(seed)), Random(seed))
            assertEquals(videos.size, spread.size)
            assertTrue(spread.zipWithNext().none { (x, y) -> x.channelId == y.channelId })
        }
    }
}
