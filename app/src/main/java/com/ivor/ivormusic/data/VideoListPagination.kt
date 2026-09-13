package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The next page of history or video search, scoped to the list itself.
 * Verified September 2026: FEhistory uses sectionListRenderer and Actions;
 * sorted search uses primaryContents and Commands. Search header chips carry
 * unrelated continuationCommands, so never take a token from the whole root.
 */
internal fun videoListContinuationToken(root: JSONObject, search: Boolean = false): String? {
    val contents = root.optJSONObject("contents")
    val section = if (search) {
        contents?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")?.optJSONObject("sectionListRenderer")
    } else {
        val tabs = (contents?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?: contents?.optJSONObject("singleColumnBrowseResultsRenderer"))?.optJSONArray("tabs")
        tabs?.optJSONObject(0)?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")?.optJSONObject("sectionListRenderer")
    }
    fun token(items: JSONArray?): String? {
        if (items == null) return null
        for (i in 0 until items.length()) {
            val value = items.optJSONObject(i)?.optJSONObject("continuationItemRenderer")
                ?.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")
                ?.optString("token")?.takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }
    token(section?.optJSONArray("contents"))?.let { return it }
    for (key in listOf("onResponseReceivedActions", "onResponseReceivedCommands")) {
        val actions = root.optJSONArray(key) ?: continue
        for (i in 0 until actions.length()) {
            val action = actions.optJSONObject(i) ?: continue
            val items = (action.optJSONObject("appendContinuationItemsAction")
                ?: action.optJSONObject("reloadContinuationItemsCommand"))?.optJSONArray("continuationItems")
            token(items)?.let { return it }
        }
    }
    return null
}
