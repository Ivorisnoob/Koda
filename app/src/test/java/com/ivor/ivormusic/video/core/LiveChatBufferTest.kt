package com.ivor.ivormusic.video.core

import com.ivor.ivormusic.data.LiveChatAuthor
import com.ivor.ivormusic.data.LiveChatMessage
import com.ivor.ivormusic.data.LiveChatPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveChatBufferTest {
    private fun message(id: String, channel: String = "channel-$id") = LiveChatMessage.Text(
        id = id,
        timestampUsec = 0L,
        author = LiveChatAuthor(name = "name", channelId = channel),
        runs = emptyList(),
    )

    private fun LiveChatBuffer.shownIds() = messages.value.map { it.id }

    @Test
    fun `a backlog shows at once and a later page waits to be released`() {
        val buffer = LiveChatBuffer()
        buffer.showBacklog(listOf(message("a"), message("b")))
        buffer.enqueue(listOf(message("c"), message("d")))
        assertEquals(listOf("a", "b"), buffer.shownIds())

        assertEquals(1, buffer.releaseNext())
        assertEquals(listOf("a", "b", "c"), buffer.shownIds())
        assertEquals(0, buffer.releaseNext())
        assertNull(buffer.releaseNext())
        assertEquals(listOf("a", "b", "c", "d"), buffer.shownIds())
    }

    @Test
    fun `a re-delivered message is dropped, queued or shown`() {
        val buffer = LiveChatBuffer()
        buffer.showBacklog(listOf(message("a")))
        buffer.enqueue(listOf(message("a"), message("b")))
        buffer.enqueue(listOf(message("b")))
        assertEquals(0, buffer.releaseNext())
        assertNull(buffer.releaseNext())
        assertEquals(listOf("a", "b"), buffer.shownIds())
    }

    @Test
    fun `a sent message's echo stops its poll delivering it twice`() {
        val buffer = LiveChatBuffer()
        buffer.showEcho(message("mine"))
        buffer.enqueue(listOf(message("mine")))
        assertNull(buffer.releaseNext())
        assertEquals(listOf("mine"), buffer.shownIds())
    }

    @Test
    fun `resuming keeps the screen and drops the overlap of the fresh backlog`() {
        val buffer = LiveChatBuffer()
        buffer.showBacklog(listOf(message("a"), message("b")))
        buffer.enqueue(listOf(message("unshown")))

        buffer.resume()
        assertNull(buffer.releaseNext())
        buffer.showBacklog(listOf(message("b"), message("c")))
        assertEquals(listOf("a", "b", "c"), buffer.shownIds())
    }

    @Test
    fun `a message deleted while queued never reaches the screen`() {
        val buffer = LiveChatBuffer()
        buffer.showBacklog(listOf(message("a")))
        buffer.enqueue(listOf(message("b"), message("c")))

        buffer.moderate(LiveChatPage(removedIds = setOf("a", "b")))
        buffer.flush()
        assertEquals(listOf("c"), buffer.shownIds())
    }

    @Test
    fun `a ban removes every message from that channel`() {
        val buffer = LiveChatBuffer()
        buffer.showBacklog(listOf(message("a", "banned"), message("b", "fine")))
        buffer.enqueue(listOf(message("c", "banned")))

        buffer.moderate(LiveChatPage(removedAuthorIds = setOf("banned")))
        buffer.flush()
        assertEquals(listOf("b"), buffer.shownIds())
    }

    @Test
    fun `an edit replaces the message in place`() {
        val buffer = LiveChatBuffer()
        buffer.showBacklog(listOf(message("a"), message("b")))
        val edited = message("a-edited")

        buffer.moderate(LiveChatPage(replacements = mapOf("a" to edited)))
        assertEquals(listOf("a-edited", "b"), buffer.shownIds())
    }

    @Test
    fun `the visible list keeps only the newest messages`() {
        val buffer = LiveChatBuffer(maxMessages = 2)
        buffer.showBacklog(listOf(message("a"), message("b"), message("c")))
        assertEquals(listOf("b", "c"), buffer.shownIds())
    }

    @Test
    fun `clearing forgets ids so the next video may repeat one`() {
        val buffer = LiveChatBuffer()
        buffer.showBacklog(listOf(message("a")))
        buffer.enqueue(listOf(message("b")))

        buffer.clear()
        assertEquals(emptyList<String>(), buffer.shownIds())
        assertNull(buffer.releaseNext())
        buffer.showBacklog(listOf(message("a")))
        assertEquals(listOf("a"), buffer.shownIds())
    }

    @Test
    fun `the release gap spreads the budget across what is left`() {
        assertEquals(300L, liveChatReleaseGapMs(budgetMs = 900L, remaining = 2))
    }

    @Test
    fun `the release gap stays inside its bounds`() {
        assertEquals(45L, liveChatReleaseGapMs(budgetMs = 100L, remaining = 500))
        assertEquals(45L, liveChatReleaseGapMs(budgetMs = -2_000L, remaining = 3))
        assertEquals(700L, liveChatReleaseGapMs(budgetMs = 10_000L, remaining = 1))
    }
}
