package com.ivor.ivormusic.video

import android.os.SystemClock
import com.ivor.ivormusic.data.LiveChatBanner
import com.ivor.ivormusic.data.LiveChatMessage
import com.ivor.ivormusic.data.RequestPriority
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.util.KLog
import com.ivor.ivormusic.video.core.LiveChatBuffer
import com.ivor.ivormusic.video.core.liveChatReleaseGapMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What a live broadcast has beyond its picture: the viewer count and the chat.
 * The player screens read the flows below directly.
 *
 * The host says when each part starts and [reset]s between videos.
 * [currentVideoId] is checked after every network wait, so a response for a
 * video the user has left cannot write into the next. Everything here runs on
 * [scope]'s main dispatcher.
 */
class LiveSession(
    private val repository: YouTubeRepository,
    private val scope: CoroutineScope,
    signedIn: StateFlow<Boolean>,
    private val isLive: StateFlow<Boolean>,
    private val currentVideoId: () -> String?,
) {
    /** Concurrent viewers, e.g. "14,618 watching now". */
    private val _viewerCount = MutableStateFlow<String?>(null)
    val viewerCount: StateFlow<String?> = _viewerCount.asStateFlow()

    private val chat = LiveChatBuffer()
    val chatMessages: StateFlow<List<LiveChatMessage>> = chat.messages

    /** Null until the first chat poll answers; false when chat is unavailable. */
    private val _isChatAvailable = MutableStateFlow<Boolean?>(null)
    val isChatAvailable: StateFlow<Boolean?> = _isChatAvailable.asStateFlow()

    private val _chatBanner = MutableStateFlow<LiveChatBanner?>(null)
    val chatBanner: StateFlow<LiveChatBanner?> = _chatBanner.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _isSendingChat = MutableStateFlow(false)
    val isSendingChat: StateFlow<Boolean> = _isSendingChat.asStateFlow()

    private val chatSendParams = MutableStateFlow<String?>(null)

    /**
     * Posting needs both an account and a chat that accepts messages. The send
     * token is present in the poll response even when signed out, so it is not
     * on its own permission to post.
     */
    val canSendChat: StateFlow<Boolean> = combine(signedIn, chatSendParams) { loggedIn, params ->
        loggedIn && params != null
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _chatMaxLength = MutableStateFlow(200)
    val chatMaxLength: StateFlow<Int> = _chatMaxLength.asStateFlow()

    /**
     * Why the composer is closed - subscribers-only, members-only, slow mode, a
     * ban. Null when the viewer may post. Distinct from [canSendChat], which is
     * about having an account at all.
     */
    private val _chatRestriction = MutableStateFlow<String?>(null)
    val chatRestriction: StateFlow<String?> = _chatRestriction.asStateFlow()

    private var chatJob: Job? = null
    private var viewerCountJob: Job? = null
    private var chatStartedForVideoId: String? = null

    /**
     * Chat start token lifted from the watch-next response, so opening the
     * panel does not re-fetch and re-parse that multi-megabyte tree.
     */
    private var chatContinuation: String? = null

    /** Elapsed-realtime mark when the next batch is expected, for pacing. */
    private var chatBatchDueAt = 0L

    /** Hand over the chat token from a watch-next response. */
    fun setChatContinuation(continuation: String?) {
        chatContinuation = continuation
    }

    /**
     * Keep the viewer count fresh. The endpoint asks for a 5s tick, which is far
     * more often than a phone needs, so this runs on [VIEWER_COUNT_POLL_MS].
     * Nobody asked for each tick, so it is automatic work and stays home during
     * a rate-limit hold; the chat a person opened is not.
     */
    fun startViewerCount(videoId: String) {
        viewerCountJob?.cancel()
        viewerCountJob = scope.launch {
            while (true) {
                val metadata = withContext(RequestPriority.Automatic) {
                    repository.getLiveMetadata(videoId)
                }
                if (currentVideoId() != videoId) return@launch
                metadata?.viewerCountText?.let { _viewerCount.value = it }
                delay(VIEWER_COUNT_POLL_MS)
            }
        }
    }

    /**
     * Start the chat stream. Called when the chat panel opens rather than when
     * the video loads: a live stream watched without the panel open should not
     * pay for a poll every 10 seconds.
     */
    fun ensureChatStarted() {
        val videoId = currentVideoId() ?: return
        if (!isLive.value) return
        if (chatStartedForVideoId == videoId && chatJob?.isActive == true) return
        chatStartedForVideoId = videoId
        startChatPolling(videoId)
    }

    /**
     * Stop polling when the panel closes. Messages are kept so reopening shows
     * the conversation immediately while the fresh backlog loads behind it -
     * the poll dedupes by id, so the overlap costs nothing.
     */
    fun stopChat() {
        chatJob?.cancel()
        chatJob = null
        chatStartedForVideoId = null
        _isChatLoading.value = false
    }

    /** Stop both polls. Safe to call when nothing is live. */
    fun stop() {
        stopChat()
        viewerCountJob?.cancel()
        viewerCountJob = null
    }

    /**
     * Stop and forget everything. Live state belongs to one video: the polls
     * must stop before their next tick can write into the next video's chat.
     */
    fun reset() {
        stop()
        _viewerCount.value = null
        _isChatAvailable.value = null
        _chatBanner.value = null
        chatSendParams.value = null
        _chatRestriction.value = null
        chatContinuation = null
        chat.clear()
    }

    /**
     * Post a message to the live chat.
     *
     * The response echoes the accepted message back, so it goes on screen at
     * once instead of waiting up to a poll interval to come round - it is marked
     * seen, so the poll that eventually redelivers it is a no-op.
     *
     * [onFailure] reports a rejection (slow mode, a word filter, a ban) so the
     * composer can put the text back rather than silently swallowing it.
     */
    fun sendChatMessage(text: String, onFailure: (String) -> Unit = {}) {
        val body = text.trim()
        if (body.isEmpty() || _isSendingChat.value) return
        val params = chatSendParams.value ?: return
        scope.launch {
            _isSendingChat.value = true
            try {
                val result = repository.sendLiveChatMessage(params, body)
                if (result.success) {
                    result.echo?.let(chat::showEcho)
                } else {
                    onFailure(result.error ?: "Message not sent")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.w(TAG, "Failed to send live chat message", e)
                onFailure("Message not sent")
            } finally {
                _isSendingChat.value = false
            }
        }
    }

    /**
     * Run the chat: one coroutine polling the server, one releasing what it
     * fetched onto the screen.
     *
     * They are separate because their rhythms are: the server hands over ten
     * seconds of chat in a single response, and rendering that in one frame is
     * what made the panel feel broken. The drain loop spreads a batch across the
     * interval until the next one is due, so messages arrive at roughly the rate
     * people actually typed them.
     */
    private fun startChatPolling(videoId: String) {
        chatJob?.cancel()
        chat.resume()

        chatJob = scope.launch {
            _isChatLoading.value = true

            // Watch-next usually has the token already; only a chat opened
            // before it lands pays for a fetch.
            val start = chatContinuation
                ?: repository.getLiveChatSession(videoId)?.continuation
            if (currentVideoId() != videoId) return@launch
            if (start == null) {
                // No conversationBar in the watch-next response: chat is
                // disabled or unavailable for this broadcast, which is a state
                // to render, not an error.
                _isChatAvailable.value = false
                _isChatLoading.value = false
                return@launch
            }
            chatContinuation = start

            coroutineScope {
                val drain = launch { drainChatQueue() }
                try {
                    pollChatLoop(videoId, start)
                } finally {
                    drain.cancel()
                    // Anything still queued was already fetched; drop it on
                    // screen rather than losing it on the way out.
                    chat.flush()
                    _isChatLoading.value = false
                }
            }
        }
    }

    /**
     * Fetch pages on the cadence the server dictates.
     *
     * Every response hands back the next token and the delay before it should be
     * used (10s on every stream sampled). The wait subtracts the time the request
     * itself took, so the cycle stays a true 10s instead of drifting further
     * behind live by one round trip per poll.
     *
     * The first response carries the visible backlog - that is history, not new
     * chat, so it goes straight to the screen; only later pages are paced.
     */
    private suspend fun pollChatLoop(videoId: String, firstContinuation: String) {
        var continuation: String? = firstContinuation
        var consecutiveFailures = 0
        var isFirstPage = true

        while (true) {
            val token = continuation ?: break
            val startedAt = SystemClock.elapsedRealtime()
            val page = repository.pollLiveChat(token)
            if (currentVideoId() != videoId) return

            if (page == null) {
                // A failed poll is usually transient (the stream is still
                // there), so back off and retry the same token a few times
                // before declaring the chat gone.
                consecutiveFailures++
                if (consecutiveFailures > MAX_CHAT_POLL_FAILURES) {
                    if (_isChatAvailable.value == null) _isChatAvailable.value = false
                    return
                }
                delay(CHAT_RETRY_DELAY_MS)
                continue
            }
            consecutiveFailures = 0

            _isChatAvailable.value = true
            _isChatLoading.value = false
            page.sendParams?.let { chatSendParams.value = it }
            _chatMaxLength.value = page.maxMessageLength
            _chatRestriction.value = page.restrictionMessage
            if (page.bannerCleared) _chatBanner.value = null
            page.banner?.let { _chatBanner.value = it }

            chat.moderate(page)

            if (isFirstPage) {
                chat.showBacklog(page.messages)
                isFirstPage = false
            } else {
                chat.enqueue(page.messages)
            }

            continuation = page.nextContinuation
            val window = page.timeoutMs.coerceIn(CHAT_MIN_WINDOW_MS, CHAT_MAX_WINDOW_MS)
            val wait = (window - (SystemClock.elapsedRealtime() - startedAt))
                .coerceAtLeast(CHAT_MIN_WINDOW_MS)
            chatBatchDueAt = SystemClock.elapsedRealtime() + wait
            delay(wait)
        }
    }

    /**
     * Release queued messages one at a time, spread over the time remaining
     * until the next batch lands.
     *
     * The first message of a batch shows immediately - the pacing only applies
     * to the ones behind it - so a quiet chat has no added latency while a busy
     * one flows instead of arriving in slabs.
     */
    private suspend fun drainChatQueue() {
        while (true) {
            val remaining = chat.releaseNext()
            if (remaining == null || remaining == 0) {
                delay(CHAT_DRAIN_IDLE_MS)
                continue
            }
            delay(liveChatReleaseGapMs(chatBatchDueAt - SystemClock.elapsedRealtime(), remaining))
        }
    }

    private companion object {
        const val TAG = "LiveSession"

        /** Consecutive failed polls tolerated before the chat is given up on. */
        const val MAX_CHAT_POLL_FAILURES = 3
        const val CHAT_RETRY_DELAY_MS = 5_000L

        /**
         * Bounds on the server's requested poll interval. Live streams ask for
         * 10s; a replay can ask for much less, and a misparse must not turn into
         * a hot loop.
         */
        const val CHAT_MIN_WINDOW_MS = 2_000L
        const val CHAT_MAX_WINDOW_MS = 30_000L

        /** Poll interval of the drain loop while the queue is empty. */
        const val CHAT_DRAIN_IDLE_MS = 120L

        /**
         * Viewer-count refresh. The endpoint asks for 5s; a phone showing one
         * number does not need it that often.
         */
        const val VIEWER_COUNT_POLL_MS = 25_000L
    }
}
