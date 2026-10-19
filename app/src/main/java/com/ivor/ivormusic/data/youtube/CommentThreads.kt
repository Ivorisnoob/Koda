package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.ChannelPost
import com.ivor.ivormusic.data.CommentItem
import com.ivor.ivormusic.data.CommentsPage
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Comments under a video or a community post: reading a page, posting,
 * replying, and liking or deleting one.
 */
internal class CommentThreads(
    private val webApi: WebApi,
    private val sessionManager: SessionManager,
) {
    /**
     * Fetch one page of comments (top-level or replies) from a continuation token.
     * Parses the modern commentEntityPayload format (frameworkUpdates mutations).
     *
     * [viaBrowse] is for a community post's thread: its first page, next pages
     * and reply threads all answer on `/browse` in the same entity shape and
     * come back empty from `/next` [verified September 2026]. A video's thread
     * is the other way round.
     */
    suspend fun getCommentsPage(
        token: String,
        viaBrowse: Boolean = false
    ): CommentsPage? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("context", webApi.webContext())
                .put("continuation", token)
            val raw = webApi.postWatchApi(if (viaBrowse) "browse" else "next", body)
                ?: return@withContext null
            val root = JSONObject(raw)

            // 1. Collect entity payloads: commentId -> payload, toolbar states and
            // toolbar surfaces (like/reply actions) by their entity keys
            val entities = mutableMapOf<String, JSONObject>()
            val toolbarStates = mutableMapOf<String, JSONObject>()
            val toolbarSurfaces = mutableMapOf<String, JSONObject>()
            val replyParamsList = mutableListOf<String>()
            val mutations = root.optJSONObject("frameworkUpdates")
                ?.optJSONObject("entityBatchUpdate")
                ?.optJSONArray("mutations")
            if (mutations != null) {
                for (i in 0 until mutations.length()) {
                    val payload = mutations.optJSONObject(i)?.optJSONObject("payload") ?: continue
                    payload.optJSONObject("commentEntityPayload")?.let { entity ->
                        val id = entity.optJSONObject("properties")?.optString("commentId")
                        if (!id.isNullOrBlank()) entities[id] = entity
                    }
                    payload.optJSONObject("engagementToolbarStateEntityPayload")?.let { state ->
                        val key = state.optString("key")
                        if (key.isNotBlank()) toolbarStates[key] = state
                    }
                    // Reply-box params and like/unlike actions live in the toolbar
                    // surface entity, one per comment (matched via toolbarSurfaceKey)
                    payload.optJSONObject("engagementToolbarSurfaceEntityPayload")?.let { surface ->
                        val key = surface.optString("key")
                        if (key.isNotBlank()) toolbarSurfaces[key] = surface
                        val replyEndpoints = mutableListOf<JSONObject>()
                        findObjectsByKey(surface, "createCommentReplyEndpoint", replyEndpoints)
                        replyEndpoints.firstOrNull()?.optString("createReplyParams")
                            ?.takeIf { it.isNotBlank() }?.let { replyParamsList.add(it) }
                    }
                }
            }
            // Match reply params to their comment: the decoded protobuf embeds the commentId
            val replyParamsByCommentId = mutableMapOf<String, String>()
            for (params in replyParamsList) {
                val decoded = decodeInnerTubeParams(params) ?: continue
                entities.keys.firstOrNull { decoded.contains(it) }?.let { id ->
                    replyParamsByCommentId[id] = params
                }
            }

            // Params for posting a new top-level comment (present on first pages only)
            val createEndpoints = mutableListOf<JSONObject>()
            findObjectsByKey(root, "createCommentEndpoint", createEndpoints)
            val createCommentParams = createEndpoints.firstOrNull()
                ?.optString("createCommentParams")?.takeIf { it.isNotBlank() }

            // 2. Walk continuationItems in order to keep YouTube's comment ordering
            val comments = mutableListOf<CommentItem>()
            var nextToken: String? = null
            val endpoints = root.optJSONArray("onResponseReceivedEndpoints") ?: JSONArray()
            for (i in 0 until endpoints.length()) {
                val ep = endpoints.optJSONObject(i) ?: continue
                val items = (ep.optJSONObject("reloadContinuationItemsCommand")
                    ?: ep.optJSONObject("appendContinuationItemsAction"))
                    ?.optJSONArray("continuationItems") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val thread = item.optJSONObject("commentThreadRenderer")
                    // Top-level pages wrap comments in commentThreadRenderer;
                    // reply pages carry bare commentViewModel items.
                    val viewModel = (thread ?: item).optJSONObject("commentViewModel")
                        ?.let { vm -> vm.optJSONObject("commentViewModel") ?: vm }
                    if (viewModel != null) {
                        val id = viewModel.optString("commentId")
                        val entity = entities[id] ?: continue
                        var repliesToken: String? = null
                        thread?.optJSONObject("replies")?.let { replies ->
                            val tokens = mutableListOf<String>()
                            findContinuationTokens(replies, tokens)
                            repliesToken = tokens.firstOrNull()
                        }
                        comments.add(
                            parseCommentEntity(
                                id, entity, viewModel, toolbarStates, repliesToken,
                                replyParamsByCommentId[id], toolbarSurfaces
                            )
                        )
                    } else if (item.has("continuationItemRenderer")) {
                        val tokens = mutableListOf<String>()
                        findContinuationTokens(item.getJSONObject("continuationItemRenderer"), tokens)
                        if (nextToken == null) nextToken = tokens.firstOrNull()
                    }
                }
            }

            CommentsPage(comments, nextToken, createCommentParams)
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getCommentsPage failed", e)
            null
        }
    }

    /**
     * Post a new top-level comment. createCommentParams comes from the first
     * comments page (CommentsPage.createCommentParams). Returns the created
     * comment parsed from the response, or null on failure. Requires login.
     */
    suspend fun createComment(createCommentParams: String, text: String): CommentItem? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext null
            val body = JSONObject()
                .put("context", webApi.webContext())
                .put("commentText", text)
                .put("createCommentParams", createCommentParams)
            parseCreatedComment(webApi.postWatchApi("comment/create_comment", body))
        }

    /**
     * Post a reply to a comment. createReplyParams comes from the parent
     * comment (CommentItem.replyParams). Requires login.
     */
    suspend fun createCommentReply(createReplyParams: String, text: String): CommentItem? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext null
            val body = JSONObject()
                .put("context", webApi.webContext())
                .put("commentText", text)
                .put("createReplyParams", createReplyParams)
            parseCreatedComment(webApi.postWatchApi("comment/create_comment_reply", body))
        }

    /**
     * Execute a comment toolbar action (like/unlike). The action param comes
     * from the comment's toolbar surface (CommentItem.likeParams /
     * unlikeParams, present only on signed-in fetches). Requires login.
     */
    suspend fun performCommentAction(action: String): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val body = JSONObject()
            .put("context", webApi.webContext())
            .put("actions", JSONArray().put(action))
        val raw = webApi.postWatchApi("comment/perform_comment_action", body)
            ?: return@withContext false
        try {
            val results = mutableListOf<JSONObject>()
            findObjectsByKey(JSONObject(raw), "actionResult", results)
            results.isEmpty() || results.any { it.optString("status") == "STATUS_SUCCEEDED" }
        } catch (e: Exception) {
            true
        }
    }

    /**
     * One community post, read from its own `FEpost_detail` page: what a
     * notification about a post points at.
     *
     * [verified October 2026, signed in] The page's `backstage-item-section`
     * holds exactly one `backstagePostRenderer`, the same renderer a channel's
     * Posts tab lists, so [parseBackstagePost] reads it unchanged. The params
     * asked for are kept as the post's [ChannelPost.detailParams] rather than
     * re-derived from the renderer: they are known to open this page, which is
     * what the comment thread is loaded from next.
     */
    suspend fun getPostDetail(detailParams: String): ChannelPost? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", POST_DETAIL_BROWSE_ID)
                    .put("params", detailParams)
            ) ?: return@withContext null
            val renderers = mutableListOf<JSONObject>()
            findObjectsByKey(JSONObject(raw), "backstagePostRenderer", renderers)
            val renderer = renderers.firstOrNull() ?: return@withContext null
            parseBackstagePost(renderer)?.copy(
                detailParams = detailParams,
                channelId = renderer.optJSONObject("authorEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                    ?.takeIf { it.startsWith("UC") }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getPostDetail failed", e)
            null
        }
    }

    /**
     * The continuation token for a community post's comments, from the post's
     * own `FEpost_detail` page ([ChannelPost.detailParams]). Null when the post
     * has comments turned off - the page then carries no comment section - or
     * when the request fails.
     *
     * Verified September 2026: the page holds a `backstage-item-section` (the
     * post) and a `comment-item-section` whose continuation opens the thread.
     */
    suspend fun getPostCommentsToken(detailParams: String): String? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", POST_DETAIL_BROWSE_ID)
                    .put("params", detailParams)
            ) ?: return@withContext null
            val sections = mutableListOf<JSONObject>()
            findObjectsByKey(JSONObject(raw), "itemSectionRenderer", sections)
            val comments = sections.firstOrNull {
                it.optString("sectionIdentifier") == "comment-item-section"
            } ?: return@withContext null
            val tokens = mutableListOf<String>()
            findContinuationTokens(comments, tokens)
            tokens.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getPostCommentsToken failed", e)
            null
        }
    }
}
