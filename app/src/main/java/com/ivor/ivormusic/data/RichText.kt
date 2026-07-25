package com.ivor.ivormusic.data

import android.net.Uri
import org.json.JSONObject

/**
 * Where a clickable span inside YouTube-authored text leads.
 */
sealed interface RichLinkTarget {
    /** An external link, already unwrapped from YouTube's redirect wrapper. */
    data class Url(val url: String) : RichLinkTarget

    /** A position in the current video, from a `watchEndpoint`. */
    data class Timestamp(val seconds: Long, val videoId: String?) : RichLinkTarget

    /** A hashtag, channel or other in-app destination. */
    data class Browse(val browseId: String) : RichLinkTarget
}

/**
 * One clickable span. [start] and [endExclusive] index into [RichText.text]
 * as Kotlin String offsets, which is what YouTube's own offsets already are
 * (see [parseRichText]).
 */
data class RichLink(
    val start: Int,
    val endExclusive: Int,
    val target: RichLinkTarget
)

/** Text plus the spans YouTube marked as clickable. */
data class RichText(
    val text: String,
    val links: List<RichLink> = emptyList()
) {
    val isBlank: Boolean get() = text.isBlank()

    companion object {
        val EMPTY = RichText("")
    }
}

/**
 * Parse an InnerTube attributed-text object (`{content, commandRuns, styleRuns}`)
 * into [RichText].
 *
 * Used for both video descriptions (`attributedDescription`) and comment bodies
 * (`commentEntityPayload.properties.content`) - verified July 2026 that both
 * carry the same shape, and that comment timestamps arrive as `watchEndpoint`
 * runs with `startTimeSeconds` rather than needing to be pattern-matched out of
 * the text.
 *
 * Offsets in `commandRuns` are **UTF-16 code units**, which is exactly how
 * Kotlin indexes a String, so they are used as-is. Anything that indexes by
 * codepoint instead (Python, Swift) drifts by one per astral character, and
 * descriptions are full of emoji - hence this note.
 *
 * Runs frequently include a trailing space or newline in their length; that is
 * trimmed off so the clickable region matches the visible link.
 */
fun parseRichText(node: JSONObject?): RichText {
    if (node == null) return RichText.EMPTY
    val content = node.optString("content").takeIf { it.isNotBlank() }
        ?: return RichText.EMPTY

    val runs = node.optJSONArray("commandRuns") ?: return RichText(content)
    val links = mutableListOf<RichLink>()

    for (i in 0 until runs.length()) {
        val run = runs.optJSONObject(i) ?: continue
        val start = run.optInt("startIndex", -1)
        val length = run.optInt("length", 0)
        if (start < 0 || length <= 0 || start >= content.length) continue

        // Clamp before trimming: a malformed length must not throw here.
        var end = minOf(start + length, content.length)
        while (end > start && content[end - 1].isWhitespace()) end--
        if (end <= start) continue

        val command = run.optJSONObject("onTap")?.optJSONObject("innertubeCommand") ?: continue
        val target = parseLinkTarget(command) ?: continue
        links.add(RichLink(start, end, target))
    }

    // Overlapping spans would make AnnotatedString throw; keep the first of any
    // pair that overlaps, since runs arrive in document order.
    links.sortBy { it.start }
    val disjoint = mutableListOf<RichLink>()
    for (link in links) {
        if (disjoint.isEmpty() || link.start >= disjoint.last().endExclusive) disjoint.add(link)
    }

    return RichText(content, disjoint)
}

private fun parseLinkTarget(command: JSONObject): RichLinkTarget? {
    command.optJSONObject("watchEndpoint")?.let { watch ->
        // startTimeSeconds is absent for a plain video link, which is not a
        // timestamp; fall through to treating it as a URL in that case.
        if (watch.has("startTimeSeconds")) {
            return RichLinkTarget.Timestamp(
                seconds = watch.optLong("startTimeSeconds"),
                videoId = watch.optString("videoId").takeIf { it.isNotBlank() }
            )
        }
        watch.optString("videoId").takeIf { it.isNotBlank() }?.let {
            return RichLinkTarget.Url("https://www.youtube.com/watch?v=$it")
        }
    }

    command.optJSONObject("urlEndpoint")?.optString("url")
        ?.takeIf { it.isNotBlank() }
        ?.let { return RichLinkTarget.Url(unwrapRedirect(it)) }

    command.optJSONObject("browseEndpoint")?.optString("browseId")
        ?.takeIf { it.isNotBlank() }
        ?.let { return RichLinkTarget.Browse(it) }

    return null
}

/**
 * Descriptions hand back links wrapped as
 * `youtube.com/redirect?...&q=<encoded target>`. Unwrap to the real URL so the
 * user's browser does not bounce through YouTube (and so the link still works
 * once the redirect token expires).
 */
private fun unwrapRedirect(url: String): String {
    if (!url.contains("/redirect?")) return url
    return try {
        Uri.parse(url).getQueryParameter("q")?.takeIf { it.isNotBlank() } ?: url
    } catch (e: Exception) {
        url
    }
}
