package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.LiveChatBanner
import com.ivor.ivormusic.data.LiveChatMessage
import com.ivor.ivormusic.data.LiveChatPage
import com.ivor.ivormusic.data.LiveChatSendResult
import com.ivor.ivormusic.data.LiveChatSession
import com.ivor.ivormusic.data.LiveMetadata
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live chat on www.youtube.com: finding a stream's chat, polling it, sending
 * to it, and the live viewer count beside it.
 */
internal class LiveChat(
    private val webApi: WebApi,
    private val watchPage: WatchPage,
    private val sessionManager: SessionManager,
) {
    /**
     * Open a live chat stream for [videoId], returning the first continuation
     * token and the send token.
     *
     * The entry point rides the same /next response the player already parses:
     * contents.twoColumnWatchNextResults.conversationBar.liveChatRenderer, with
     * the start token at continuations[0].reloadContinuationData.continuation.
     * conversationBar is absent entirely when the video is not live or the
     * creator disabled chat, which is the "no chat" signal - not an error.
     *
     * Reading chat needs no account: a signed-out poll returns the full
     * backlog. Sending does, so [LiveChatSession.sendParams] is only meaningful
     * alongside [isLoggedIn].
     *
     * Verified against the live /next API August 2026.
     */
    suspend fun getLiveChatSession(videoId: String): LiveChatSession? = withContext(Dispatchers.IO) {
        try {
            val root = watchPage.fetchWatchNextRoot(videoId) ?: return@withContext null
            parseLiveChatContinuation(root)?.let { LiveChatSession(continuation = it) }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getLiveChatSession failed for $videoId", e)
            null
        }
    }

    /**
     * Poll one page of live chat.
     *
     * continuationContents.liveChatContinuation carries the new actions plus
     * the next token in continuations[0].invalidationContinuationData, whose
     * timeoutMs (10s on every stream sampled) is the interval the server wants
     * between polls. The first poll returns the whole visible backlog, roughly
     * 70 messages; subsequent polls return only what arrived since.
     *
     * Action shapes handled, in order of how often they actually appear:
     * addChatItemAction (text/paid/membership/gift/system items),
     * addBannerToLiveChatCommand (pinned message or chat summary) and
     * removeChatItemAction (a message deleted after it was already rendered).
     * These were verified against the live live_chat/get_live_chat API
     * August 2026.
     *
     * NOT yet probed against a live response, and so to be treated as
     * best-effort until they are: markChatItemAsDeletedAction,
     * markChatItemsByAuthorAsDeletedAction, replaceChatItemAction,
     * removeBannerForLiveChatCommand, liveChatPaidStickerRenderer and
     * liveChatRestrictedParticipationRenderer. Each one is an additive branch
     * that no-ops when the key is absent, so a wrong guess costs the feature
     * rather than the chat - but confirm the shapes before relying on them.
     */
    suspend fun pollLiveChat(continuation: String): LiveChatPage? = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("continuation", continuation)
            val raw = webApi.postWatchApi("live_chat/get_live_chat", body) ?: return@withContext null
            val chat = org.json.JSONObject(raw)
                .optJSONObject("continuationContents")
                ?.optJSONObject("liveChatContinuation")
                ?: return@withContext null

            // Either invalidationContinuationData (the usual live tick) or
            // timedContinuationData / reloadContinuationData; all three hold the
            // next token and a timeout under different keys.
            val nextData = chat.optJSONArray("continuations")?.optJSONObject(0)?.let { c ->
                c.optJSONObject("invalidationContinuationData")
                    ?: c.optJSONObject("timedContinuationData")
                    ?: c.optJSONObject("reloadContinuationData")
            }

            val actions = chat.optJSONArray("actions")
            val messages = mutableListOf<LiveChatMessage>()
            val removed = mutableSetOf<String>()
            val removedAuthors = mutableSetOf<String>()
            val replacements = mutableMapOf<String, LiveChatMessage>()
            var banner: LiveChatBanner? = null
            var bannerCleared = false

            for (i in 0 until (actions?.length() ?: 0)) {
                val action = actions?.optJSONObject(i) ?: continue
                action.optJSONObject("addChatItemAction")?.optJSONObject("item")?.let { item ->
                    parseLiveChatItem(item, fallbackOrder = i)?.let(messages::add)
                }
                action.optJSONObject("removeChatItemAction")
                    ?.optString("targetItemId")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(removed::add)
                // What a moderator delete actually emits. The renderer carries a
                // "deleted by" placeholder, but YouTube's own client collapses
                // the row away, so the message is simply dropped.
                action.optJSONObject("markChatItemAsDeletedAction")
                    ?.optString("targetItemId")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(removed::add)
                // A ban: every message from that channel disappears at once.
                action.optJSONObject("markChatItemsByAuthorAsDeletedAction")
                    ?.optString("externalChannelId")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(removedAuthors::add)
                action.optJSONObject("replaceChatItemAction")?.let { replace ->
                    val target = replace.optString("targetItemId").takeIf { it.isNotBlank() }
                    val item = replace.optJSONObject("replacementItem")
                    if (target != null && item != null) {
                        parseLiveChatItem(item, fallbackOrder = i)?.let { replacements[target] = it }
                    }
                }
                action.optJSONObject("addBannerToLiveChatCommand")
                    ?.optJSONObject("bannerRenderer")
                    ?.optJSONObject("liveChatBannerRenderer")
                    ?.let { parseLiveChatBanner(it) }
                    ?.let { banner = it }
                if (action.has("removeBannerForLiveChatCommand")) bannerCleared = true
            }

            val actionPanel = chat.optJSONObject("actionPanel")
            val inputRenderer = actionPanel?.optJSONObject("liveChatMessageInputRenderer")
            // When chat is subscribers-only, members-only, in slow mode, or the
            // viewer is banned, the input renderer is replaced wholesale by this
            // one carrying the reason.
            val restriction = actionPanel
                ?.optJSONObject("liveChatRestrictedParticipationRenderer")
                ?.let { getRunText(it.optJSONObject("message")) }
                ?.takeIf { it.isNotBlank() }

            LiveChatPage(
                messages = messages,
                removedIds = removed,
                removedAuthorIds = removedAuthors,
                replacements = replacements,
                banner = banner,
                bannerCleared = bannerCleared,
                restrictionMessage = restriction,
                nextContinuation = nextData?.optString("continuation")?.takeIf { it.isNotBlank() },
                timeoutMs = nextData?.optLong("timeoutMs")?.takeIf { it > 0L } ?: 10_000L,
                sendParams = inputRenderer
                    ?.optJSONObject("sendButton")
                    ?.optJSONObject("buttonRenderer")
                    ?.optJSONObject("serviceEndpoint")
                    ?.optJSONObject("sendLiveChatMessageEndpoint")
                    ?.optString("params")
                    ?.takeIf { it.isNotBlank() },
                maxMessageLength = inputRenderer
                    ?.optJSONObject("inputField")
                    ?.optJSONObject("liveChatTextInputFieldRenderer")
                    ?.optInt("maxCharacterLimit")
                    ?.takeIf { it > 0 }
                    ?: 200,
            )
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "pollLiveChat failed", e)
            null
        }
    }

    /**
     * Post a message to a live chat. Requires login - the input renderer is
     * present in the response even signed out, so its presence is not an auth
     * signal.
     *
     * [params] is the opaque token from the poll response's
     * actionPanel.liveChatMessageInputRenderer.sendButton, echoed back verbatim.
     *
     * An accepted message comes straight back as an addChatItemAction, so the
     * result carries the parsed item: showing it at once is what makes sending
     * feel instant instead of costing up to a full poll interval. Its id is the
     * one the poll will hand back later, so the normal dedupe absorbs it.
     */
    suspend fun sendLiveChatMessage(params: String, text: String): LiveChatSendResult =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) {
                return@withContext LiveChatSendResult(false, error = "Sign in to chat")
            }
            try {
                val body = org.json.JSONObject()
                    .put("context", webApi.webContext())
                    .put("params", params)
                    .put(
                        "richMessage",
                        org.json.JSONObject().put(
                            "textSegments",
                            org.json.JSONArray().put(org.json.JSONObject().put("text", text))
                        )
                    )
                    .put("clientMessageId", java.util.UUID.randomUUID().toString())
                val raw = webApi.postWatchApi("live_chat/send_message", body)
                    ?: return@withContext LiveChatSendResult(false, error = "Message not sent")
                val root = org.json.JSONObject(raw)

                val results = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(root, "addChatItemAction", results)
                val echo = results.firstNotNullOfOrNull { action ->
                    action.optJSONObject("item")?.let { parseLiveChatItem(it, fallbackOrder = 0) }
                }
                if (results.isNotEmpty()) {
                    return@withContext LiveChatSendResult(true, echo = echo)
                }

                // A rejected message (slow mode, a word filter, a ban) answers
                // 200 with an error string in place of the item.
                val errors = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(root, "errorMessage", errors)
                val reason = errors.firstNotNullOfOrNull { getRunText(it) }
                    ?.takeIf { it.isNotBlank() }
                LiveChatSendResult(false, error = reason ?: "Message not sent")
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "sendLiveChatMessage failed", e)
                LiveChatSendResult(false, error = "Message not sent")
            }
        }

    /**
     * Concurrent viewers and the "Started streaming ..." line for a live video.
     *
     * The updated_metadata endpoint is what the web player polls to keep those
     * counters fresh without re-running /next. It asks for a 5s tick, which is
     * far more often than a phone needs - call it on the chat's 10s cadence or
     * slower.
     *
     * Verified against the live updated_metadata API August 2026.
     */
    suspend fun getLiveMetadata(videoId: String): LiveMetadata? = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("videoId", videoId)
            val raw = webApi.postWatchApi("updated_metadata", body) ?: return@withContext null
            val root = org.json.JSONObject(raw)

            val viewCounts = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "videoViewCountRenderer", viewCounts)
            val viewCount = viewCounts.firstOrNull { it.optBoolean("isLive") } ?: viewCounts.firstOrNull()

            val dateTexts = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "updateDateTextAction", dateTexts)

            LiveMetadata(
                viewerCountText = getRunText(viewCount?.optJSONObject("viewCount"))
                    ?.takeIf { it.isNotBlank() },
                shortViewerCount = getRunText(viewCount?.optJSONObject("extraShortViewCount"))
                    ?.takeIf { it.isNotBlank() },
                dateText = getRunText(dateTexts.firstOrNull()?.optJSONObject("dateText"))
                    ?.takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getLiveMetadata failed for $videoId", e)
            null
        }
    }
}
