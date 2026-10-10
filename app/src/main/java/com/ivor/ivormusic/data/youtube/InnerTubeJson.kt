package com.ivor.ivormusic.data.youtube

import android.net.Uri
import android.util.Base64
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject

// Reading InnerTube JSON by hand: the recursive searches, text runs and
// thumbnail pickers every parser in this package shares. All pure.

/**
 * Recursively collect every JSONObject stored under [key] anywhere in the tree.
 * Kept structure-agnostic on purpose — InnerTube nests these renderers
 * differently across response variants (queue vs. wrapper renderers).
 */
internal fun findObjectsByKey(node: Any, key: String, results: MutableList<JSONObject>) {
    when (node) {
        is JSONObject -> {
            node.optJSONObject(key)?.let { results.add(it) }
            val keys = node.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k != key) findObjectsByKey(node.get(k), key, results)
            }
        }
        is JSONArray -> {
            for (i in 0 until node.length()) {
                findObjectsByKey(node.get(i), key, results)
            }
        }
    }
}

/**
 * The first object stored under [key] inside [scope], or failing that anywhere
 * in [root].
 *
 * For a value that has one home in a response. Searching the whole response
 * and taking the first hit picks a different object as soon as the key turns
 * up somewhere else - a related card, a featured channel, an engagement panel
 * - and nothing logs it. Naming the container makes the right one win by
 * construction; the whole response is still searched when the container has
 * moved, which is exactly what happened before.
 */
internal fun firstObjectByKey(scope: JSONObject?, root: JSONObject, key: String): JSONObject? {
    val found = mutableListOf<JSONObject>()
    if (scope != null) findObjectsByKey(scope, key, found)
    if (found.isEmpty() && scope !== root) findObjectsByKey(root, key, found)
    return found.firstOrNull()
}

/**
 * Whether a framework entity's [key] belongs to [id].
 *
 * An entity key is a URL-encoded base64 blob with the id of what it describes
 * in plain ASCII inside it: a `subscriptionStateEntity` carries its channel
 * id, a `likeStatusEntity` and a `viewCountEntity` their video id. [verified
 * October 2026, signed out, WEB `/next`] It is how one of several entities of
 * a kind is told from the others, since they all sit in one list.
 */
internal fun entityKeyNames(key: String?, id: String): Boolean {
    if (key.isNullOrBlank() || id.isBlank()) return false
    return try {
        val bytes = java.util.Base64.getUrlDecoder().decode(URLDecoder.decode(key, "UTF-8"))
        String(bytes, Charsets.ISO_8859_1).contains(id)
    } catch (e: IllegalArgumentException) {
        false
    }
}

/**
 * Extract continuation token from API response for pagination.
 */
internal fun extractContinuationToken(json: String): String? {
    try {
        val root = JSONObject(json)
        val continuations = mutableListOf<String>()

        // Find all nextContinuationData or continuationEndpoint objects
        findContinuationTokens(root, continuations)

        return continuations.firstOrNull()
    } catch (e: Exception) {
        // Ignore
    }
    return null
}

internal fun findContinuationTokens(node: Any, results: MutableList<String>) {
    if (node is JSONObject) {
        // Check for nextContinuationData
        if (node.has("nextContinuationData")) {
            val token = node.optJSONObject("nextContinuationData")?.optString("continuation")
            if (!token.isNullOrEmpty()) {
                results.add(token)
                return
            }
        }
        // Check for continuationEndpoint
        if (node.has("continuationEndpoint")) {
            val token = node.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")
                ?.optString("token")
            if (!token.isNullOrEmpty()) {
                results.add(token)
                return
            }
        }
        // Check for direct continuationCommand
        if (node.has("continuationCommand")) {
            val token = node.optJSONObject("continuationCommand")?.optString("token")
            if (!token.isNullOrEmpty()) {
                results.add(token)
                return
            }
        }
        // Recurse
        val keys = node.keys()
        while (keys.hasNext()) {
            val nextKey = keys.next()
            findContinuationTokens(node.get(nextKey), results)
        }
    } else if (node is JSONArray) {
        for (i in 0 until node.length()) {
            findContinuationTokens(node.get(i), results)
        }
    }
}

internal fun getRunText(formattedString: JSONObject?): String? {
    if (formattedString == null) return null
    if (formattedString.has("simpleText")) {
        return formattedString.optString("simpleText")
    }
    val runs = formattedString.optJSONArray("runs") ?: return null
    val sb = StringBuilder()
    for (i in 0 until runs.length()) {
        sb.append(runs.optJSONObject(i)?.optString("text") ?: "")
    }
    return sb.toString()
}

internal fun extractVideoId(url: String): String {
    // Extract video ID from various YouTube URL formats
    val patterns = listOf(
        Regex("watch\\?v=([a-zA-Z0-9_-]+)"),
        Regex("youtu\\.be/([a-zA-Z0-9_-]+)"),
        Regex("youtube\\.com/embed/([a-zA-Z0-9_-]+)"),
        Regex("music\\.youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+)")
    )

    for (pattern in patterns) {
        pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }
    }

    return url // Fallback: return the URL as-is
}

/**
 * Resurrected helper for deep recursive search.
 * Used sparingly for fallback scenarios where structure is unknown.
 */
internal fun findAllObjects(json: JSONObject, key: String, results: MutableList<JSONObject>, depth: Int = 0) {
    if (depth > 20) return // Reduced depth limit from 50

    if (json.has(key)) {
        val value = json.opt(key)
        if (value is JSONObject) {
            results.add(value)
        } else if (value is JSONArray) {
            for (i in 0 until value.length()) {
                val item = value.optJSONObject(i)
                if (item != null) results.add(item)
            }
        }
    }

    json.keys().forEach { keyName ->
        val value = json.opt(keyName)
        when (value) {
            is JSONObject -> findAllObjects(value, key, results, depth + 1)
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val item = value.optJSONObject(i)
                    if (item != null) findAllObjects(item, key, results, depth + 1)
                }
            }
        }
    }
}

/** "1,234 videos" to a number; -1 for anything with no digits in it. */
internal fun countFromText(text: String?): Int {
    val digits = text?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: return -1
    return digits.toIntOrNull() ?: -1
}

/**
 * Parse duration string like "3:45" or "1:23:45" to seconds.
 */
internal fun parseDurationToSeconds(duration: String): Long {
    if (duration.isBlank() || duration == "0:00") return 0L
    val parts = duration.split(":").mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0L
    }
}

/** Widest entry of a thumbnail/source array, which is the highest quality. */
internal fun widestThumbnailUrl(array: JSONArray?, urlKey: String): String? {
    if (array == null) return null
    var best: String? = null
    var bestWidth = -1
    for (i in 0 until array.length()) {
        val entry = array.optJSONObject(i) ?: continue
        val url = entry.optString(urlKey).takeIf { it.isNotBlank() } ?: continue
        val width = entry.optInt("width", 0)
        if (width >= bestWidth) {
            bestWidth = width
            best = url
        }
    }
    return best
}

/**
 * Decode a URL-encoded, URL-safe base64 InnerTube params blob into a
 * string for substring matching (embedded ids are plain ASCII).
 */
internal fun decodeInnerTubeParams(params: String): String? = try {
    val unescaped = URLDecoder.decode(params, "UTF-8")
    val bytes = Base64.decode(unescaped, Base64.URL_SAFE)
    String(bytes, Charsets.ISO_8859_1)
} catch (e: Exception) {
    null
}

internal inline fun <T, R : Any> List<T>.lastNotNullOfOrNull(transform: (T) -> R?): R? {
    for (i in indices.reversed()) {
        transform(this[i])?.let { return it }
    }
    return null
}

/** Widest entry of a modern `image.sources` array. */
internal fun bestImageSource(sources: JSONArray?): String? {
    if (sources == null) return null
    var best: String? = null
    var maxWidth = -1
    for (i in 0 until sources.length()) {
        val source = sources.optJSONObject(i) ?: continue
        val width = source.optInt("width", 0)
        val url = source.optString("url").takeIf { it.isNotBlank() } ?: continue
        if (width >= maxWidth) {
            maxWidth = width
            best = url
        }
    }
    return best?.let { if (it.startsWith("//")) "https:$it" else it }
}

/** Widest entry of a legacy `thumbnails` array. */
internal fun bestThumbnail(thumbnails: JSONArray?): String? {
    if (thumbnails == null) return null
    var best: String? = null
    var maxWidth = -1
    for (i in 0 until thumbnails.length()) {
        val thumb = thumbnails.optJSONObject(i) ?: continue
        val width = thumb.optInt("width", 0)
        val url = thumb.optString("url").takeIf { it.isNotBlank() } ?: continue
        if (width >= maxWidth) {
            maxWidth = width
            best = url
        }
    }
    return best?.let { if (it.startsWith("//")) "https:$it" else it }
}

/**
 * Unwraps `youtube.com/redirect?...&q=<target>` to the real destination, so
 * a link does not bounce through YouTube and still works once the redirect
 * token expires. Same rule [RichText] applies to descriptions.
 */
internal fun unwrapYouTubeRedirect(url: String): String {
    if (!url.contains("/redirect?")) return url
    return try {
        Uri.parse(url).getQueryParameter("q")?.takeIf { it.isNotBlank() } ?: url
    } catch (e: Exception) {
        url
    }
}
