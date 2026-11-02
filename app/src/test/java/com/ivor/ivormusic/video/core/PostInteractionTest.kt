package com.ivor.ivormusic.video.core

import com.ivor.ivormusic.data.ChannelPollChoice
import com.ivor.ivormusic.data.ChannelPost
import com.ivor.ivormusic.data.linkifyPlainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostInteractionTest {
    private fun choice(name: String, selected: Boolean = false) = ChannelPollChoice(
        text = name,
        isSelected = selected,
        selectAction = "select-$name",
        deselectAction = "deselect-$name",
        ratioIfSelected = 0.6f,
        percentIfSelected = "60%",
        ratioIfNotSelected = 0.4f,
        percentIfNotSelected = "40%",
    )

    private val post = ChannelPost(
        postId = "p",
        authorName = "a",
        voteCountText = "99",
        likedCountText = "100",
        unlikedCountText = "99",
        likeAction = "like",
        unlikeAction = "unlike",
        pollChoices = listOf(choice("a"), choice("b")),
    )

    @Test
    fun `a like uses its token and YouTube's count, and undoes the same way`() {
        assertEquals("like", post.likeToken())
        val liked = post.withLike(true)
        assertTrue(liked.isLiked)
        assertEquals("100", liked.voteCountText)
        assertEquals("unlike", liked.likeToken())
        assertEquals("99", liked.withLike(false).voteCountText)
    }

    @Test
    fun `a vote selects one choice and reveals that the poll was voted on`() {
        assertFalse(post.hasVoted)
        assertEquals("select-b", post.pollToken(1))
        val voted = post.withPollTap(1)
        assertTrue(voted.hasVoted)
        assertEquals(listOf(false, true), voted.pollChoices.map { it.isSelected })
    }

    @Test
    fun `tapping your own vote takes it back`() {
        val voted = post.withPollTap(0)
        assertEquals("deselect-a", voted.pollToken(0))
        assertFalse(voted.withPollTap(0).hasVoted)
    }

    @Test
    fun `changing a vote moves it`() {
        val moved = post.withPollTap(0).withPollTap(1)
        assertEquals(listOf(false, true), moved.pollChoices.map { it.isSelected })
    }

    @Test
    fun `a choice shows the figures for its own case`() {
        val voted = post.withPollTap(0)
        assertEquals(0.6f, voted.pollChoices[0].shareRatio())
        assertEquals("60%", voted.pollChoices[0].shareText())
        assertEquals(0.4f, voted.pollChoices[1].shareRatio())
    }

    @Test
    fun `a feed keeps recent posts and drops old or undated ones`() {
        val day = 24L * 60 * 60 * 1000
        val now = 1_000L * day
        assertTrue(isRecentPost(post.copy(publishedAtMs = now - 3 * day), now))
        assertFalse(isRecentPost(post.copy(publishedAtMs = now - 1_095 * day), now))
        assertFalse(isRecentPost(post.copy(publishedAtMs = null), now))
    }

    @Test
    fun `bare domains link only when asked for`() {
        val text = "Merch at shop.example.com/new and example.io, v2.0 e.g. Node.js"
        assertEquals(2, linkifyPlainText(text, bareDomains = true).links.size)
        assertEquals(0, linkifyPlainText(text).links.size)
    }
}
