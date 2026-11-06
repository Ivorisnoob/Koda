package com.ivor.ivormusic.video.core

import com.ivor.ivormusic.data.LiveChatMessage
import com.ivor.ivormusic.data.LiveChatPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the chat has received: the messages on screen, the ones waiting to be
 * released, and the ids already seen. No timing and no network, so every rule
 * about what reaches the screen is testable on its own.
 *
 * A poll returns ten seconds of chat in one lump; releasing it in one frame is
 * what made the panel feel like a stuttering wall rather than a live
 * conversation, so later pages queue here and go out one at a time. The poll
 * and the drain both run on the main dispatcher, so this needs no
 * synchronization.
 */
class LiveChatBuffer(private val maxMessages: Int = DEFAULT_MAX_MESSAGES) {
    private val _messages = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    val messages: StateFlow<List<LiveChatMessage>> = _messages.asStateFlow()

    private val pending = ArrayDeque<LiveChatMessage>()

    /** Ids already queued or displayed, so a re-delivered message is dropped. */
    private val seenIds = LinkedHashSet<String>()

    /** Forget everything: the next video's chat starts empty. */
    fun clear() {
        _messages.value = emptyList()
        pending.clear()
        seenIds.clear()
    }

    /**
     * Start receiving again, keeping what is on screen. Reopening the panel
     * shows the conversation at once while the backlog loads behind it, and
     * the ids taken here are what drop the overlap.
     */
    fun resume() {
        pending.clear()
        seenIds.clear()
        _messages.value.forEach { seenIds.add(it.id) }
    }

    /** A first page is history, not new chat, so it goes straight on screen. */
    fun showBacklog(messages: List<LiveChatMessage>) {
        append(messages.filter { seenIds.add(it.id) })
        trimSeenIds()
    }

    /** A later page waits to be released at the rate people typed it. */
    fun enqueue(messages: List<LiveChatMessage>) {
        pending.addAll(messages.filter { seenIds.add(it.id) })
        trimSeenIds()
    }

    /**
     * Put the next queued message on screen.
     *
     * @return how many are still queued, or null when nothing was.
     */
    fun releaseNext(): Int? {
        val next = pending.removeFirstOrNull() ?: return null
        append(listOf(next))
        return pending.size
    }

    /** Put everything still queued on screen at once; it was already fetched. */
    fun flush() {
        if (pending.isEmpty()) return
        val rest = pending.toList()
        pending.clear()
        append(rest)
    }

    /** The accepted message from a send, on screen ahead of its poll. */
    fun showEcho(message: LiveChatMessage) {
        if (seenIds.add(message.id)) append(listOf(message))
    }

    /**
     * Apply deletions and edits to both the visible list and the queue - a
     * message deleted while still waiting to be shown must never reach the
     * screen.
     */
    fun moderate(page: LiveChatPage) {
        if (page.removedIds.isEmpty() &&
            page.removedAuthorIds.isEmpty() &&
            page.replacements.isEmpty()
        ) return

        fun survives(message: LiveChatMessage): Boolean =
            message.id !in page.removedIds &&
                message.author?.channelId?.let { it !in page.removedAuthorIds } != false

        pending.retainAll { survives(it) }
        _messages.update { current ->
            current.mapNotNull { message ->
                if (!survives(message)) null else page.replacements[message.id] ?: message
            }
        }
    }

    /**
     * Append to the visible list, capped at [maxMessages] so a stream left open
     * overnight cannot grow the list without bound.
     */
    private fun append(new: List<LiveChatMessage>) {
        if (new.isEmpty()) return
        _messages.update { current -> (current + new).takeLast(maxMessages) }
    }

    /**
     * Keep the dedupe set from growing for the lifetime of a long stream. Only
     * ids that are still visible or queued can be re-delivered in a way that
     * matters.
     */
    private fun trimSeenIds() {
        if (seenIds.size <= maxMessages * 3) return
        val live = HashSet<String>(_messages.value.size + pending.size)
        _messages.value.forEach { live.add(it.id) }
        pending.forEach { live.add(it.id) }
        seenIds.retainAll(live)
    }

    companion object {
        /**
         * Matches the cap YouTube declares in the response itself
         * (itemList.liveChatItemListRenderer.maxItemsToDisplay).
         */
        const val DEFAULT_MAX_MESSAGES = 250
    }
}

/**
 * The gap before the next queued message, spreading what is left over
 * [budgetMs], the time until the next batch lands, so the queue empties just as
 * it arrives. Falling behind (a burst larger than the window can pace, or a
 * budget already spent) collapses the gap to the floor and catches up.
 */
fun liveChatReleaseGapMs(budgetMs: Long, remaining: Int): Long =
    (budgetMs / (remaining + 1)).coerceIn(LIVE_CHAT_MIN_GAP_MS, LIVE_CHAT_MAX_GAP_MS)

/**
 * Pacing bounds for releasing a batch. The floor keeps a burst from animating
 * one message per frame; the ceiling stops a sparse batch from sitting in the
 * queue for seconds when there is nothing behind it.
 */
private const val LIVE_CHAT_MIN_GAP_MS = 45L
private const val LIVE_CHAT_MAX_GAP_MS = 700L
