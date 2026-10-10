package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.CommentItem
import com.ivor.ivormusic.data.parseRichText
import com.ivor.ivormusic.util.KLog

// Comments as commentEntityPayload mutations, and the reply to posting one. Pure.

internal fun parseCommentEntity(
    id: String,
    entity: org.json.JSONObject,
    viewModel: org.json.JSONObject,
    toolbarStates: Map<String, org.json.JSONObject>,
    repliesToken: String?,
    replyParams: String? = null,
    toolbarSurfaces: Map<String, org.json.JSONObject> = emptyMap()
): CommentItem {
    val props = entity.optJSONObject("properties")
    val author = entity.optJSONObject("author")
    val toolbar = entity.optJSONObject("toolbar")
    val toolbarStateKey = props?.optString("toolbarStateKey").orEmpty()
    val toolbarState = toolbarStates[toolbarStateKey]
    val heartState = toolbarState?.optString("heartState")
    val likeState = toolbarState?.optString("likeState")

    // Like/unlike actions come from the comment's toolbar surface entity
    // (signed-in responses only; signed out the commands are empty stubs)
    val surface = toolbarSurfaces[viewModel.optString("toolbarSurfaceKey")]
    fun surfaceAction(command: String): String? =
        surface?.optJSONObject(command)?.let {
            val endpoints = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(it, "performCommentActionEndpoint", endpoints)
            endpoints.firstOrNull()?.optString("action")?.takeIf { a -> a.isNotBlank() }
        }

    // Own comments carry a "Delete" item in the surface's three-dot menu
    // (menuCommand -> menuRenderer -> menuNavigationItemRenderer, label is
    // stable because webContext pins hl=en); its confirm-dialog endpoint
    // holds the perform_comment_action delete param. Verified July 2026.
    val deleteParams = surface?.optJSONObject("menuCommand")?.let { menu ->
        val menuItems = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(menu, "menuNavigationItemRenderer", menuItems)
        menuItems.firstOrNull { getRunText(it.optJSONObject("text")) == "Delete" }
            ?.let { item ->
                val endpoints = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(item, "performCommentActionEndpoint", endpoints)
                endpoints.firstOrNull()?.optString("action")?.takeIf { a -> a.isNotBlank() }
            }
    }

    // Comment bodies carry the same attributed-text shape as descriptions:
    // timestamps arrive as watchEndpoint runs with startTimeSeconds, so the
    // clickable ranges never have to be pattern-matched out of the prose.
    val content = parseRichText(props?.optJSONObject("content"))

    return CommentItem(
        commentId = id,
        text = content.text,
        links = content.links,
        author = author?.optString("displayName").orEmpty(),
        authorAvatarUrl = author?.optString("avatarThumbnailUrl")?.takeIf { it.isNotBlank() },
        authorChannelId = author?.optString("channelId")?.takeIf { it.startsWith("UC") },
        publishedTime = props?.optString("publishedTime").orEmpty(),
        likeCount = toolbar?.optString("likeCountNotliked").orEmpty().trim(),
        replyCount = toolbar?.optString("replyCount").orEmpty().trim(),
        isPinned = viewModel.has("pinnedText"),
        isHearted = heartState == "TOOLBAR_HEART_STATE_HEARTED",
        isCreator = author?.optBoolean("isCreator", false) ?: false,
        isVerified = author?.optBoolean("isVerified", false) ?: false,
        repliesToken = repliesToken,
        replyParams = replyParams,
        likeCountLiked = toolbar?.optString("likeCountLiked").orEmpty().trim(),
        isLiked = likeState == "TOOLBAR_LIKE_STATE_LIKED",
        likeParams = surfaceAction("likeCommand"),
        unlikeParams = surfaceAction("unlikeCommand"),
        deleteParams = deleteParams
    )
}

/**
 * Parse the comment entity out of a create_comment / create_comment_reply
 * response. Returns null unless the actionResult reports success.
 */
internal fun parseCreatedComment(raw: String?): CommentItem? {
    if (raw == null) return null
    return try {
        val root = org.json.JSONObject(raw)
        val results = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "actionResult", results)
        if (results.none { it.optString("status") == "STATUS_SUCCEEDED" }) return null

        val entities = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "commentEntityPayload", entities)
        val entity = entities.firstOrNull() ?: return null
        val id = entity.optJSONObject("properties")?.optString("commentId")
            ?.takeIf { it.isNotBlank() } ?: return null

        val replyEndpoints = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "createCommentReplyEndpoint", replyEndpoints)
        val replyParams = replyEndpoints.firstOrNull()
            ?.optString("createReplyParams")?.takeIf { it.isNotBlank() }

        // The create response also carries the comment's viewModel and its
        // toolbar surface — passing them through gives the fresh comment
        // its like/delete params immediately (no page reload needed)
        val viewModels = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "commentViewModel", viewModels)
        val viewModel = viewModels
            .map { it.optJSONObject("commentViewModel") ?: it }
            .firstOrNull { it.optString("commentId") == id }
            ?: org.json.JSONObject()

        val surfaces = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "engagementToolbarSurfaceEntityPayload", surfaces)
        val toolbarSurfaces = surfaces
            .filter { it.optString("key").isNotBlank() }
            .associateBy { it.optString("key") }

        parseCommentEntity(
            id, entity,
            viewModel = viewModel,
            toolbarStates = emptyMap(),
            repliesToken = null,
            replyParams = replyParams,
            toolbarSurfaces = toolbarSurfaces
        )
    } catch (e: Exception) {
        KLog.e("YouTubeRepo", "parseCreatedComment failed", e)
        null
    }
}
