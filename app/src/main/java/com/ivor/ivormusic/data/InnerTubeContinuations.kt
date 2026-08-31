package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The items of a modern InnerTube continuation response.
 *
 * [scar] [verified August 2026 against a 982-track playlist] Continuations used
 * to answer under `continuationContents.<something>Continuation.contents`, and
 * every branch of the repository's own container lookup still reads that legacy
 * form. A music playlist continuation now answers with neither, carrying its
 * rows under:
 *
 * ```
 * onResponseReceivedActions[0].appendContinuationItemsAction.continuationItems[]
 * ```
 *
 * Failing to recognise that shape is what capped every playlist at exactly one
 * page of 100 songs while the load still reported itself complete, so nothing
 * retried it and nothing was logged as degraded.
 *
 * The returned array is flat item wrappers rather than shelves - 100 rows plus
 * the next page's `continuationItemRenderer` - which the shelf parser already
 * handles as its "direct item" case, exactly as the legacy continuation
 * branches rely on.
 *
 * Lives in its own file, and takes a parsed root rather than a response string,
 * so the shape can be covered by a JVM test without a Context. Shapes drift;
 * this one already has.
 */
internal fun continuationItemsOrNull(root: JSONObject): JSONArray? {
    val actions = root.optJSONArray("onResponseReceivedActions") ?: return null
    for (i in 0 until actions.length()) {
        val action = actions.optJSONObject(i) ?: continue
        val items = (action.optJSONObject("appendContinuationItemsAction")
            ?: action.optJSONObject("reloadContinuationItemsCommand"))
            ?.optJSONArray("continuationItems")
        if (items != null && items.length() > 0) return items
    }
    return null
}
