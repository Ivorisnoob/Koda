package com.ivor.ivormusic.data

internal data class LockupVideoStats(
    val viewCount: String = "",
    val uploadedDate: String = ""
)

/**
 * Classifies the statistics row from a modern video lockup.
 *
 * YouTube currently sends compact sibling parts such as `263K` and `1h ago`,
 * but older and accessibility-rich variants still say `263K views` and
 * `1 hour ago`. Explicit labels win; otherwise the stable row order is views
 * followed by upload date. Verified against live WEB /next responses August
 * 2026.
 */
internal fun parseLockupVideoStats(parts: List<Pair<String, String?>>): LockupVideoStats {
    var viewCount = ""
    var uploadedDate = ""
    val unclassified = mutableListOf<String>()

    parts.forEach { (content, accessibilityLabel) ->
        content.split('•')
            .map { it.trim() }
            .filter { value -> value.any { it.isLetterOrDigit() } }
            .forEach { value ->
                val searchable = "$value ${accessibilityLabel.orEmpty()}"
                when {
                    searchable.contains("view", ignoreCase = true) ||
                        searchable.contains("watching", ignoreCase = true) -> {
                        if (viewCount.isBlank()) viewCount = value
                    }
                    value.isUploadDateText() -> {
                        if (uploadedDate.isBlank()) uploadedDate = value
                    }
                    else -> unclassified += value
                }
            }
    }

    unclassified.forEach { value ->
        when {
            viewCount.isBlank() -> viewCount = value
            uploadedDate.isBlank() -> uploadedDate = value
        }
    }
    return LockupVideoStats(viewCount, uploadedDate)
}

private fun String.isUploadDateText(): Boolean =
    contains("ago", ignoreCase = true) ||
        startsWith("streamed ", ignoreCase = true) ||
        startsWith("premiered ", ignoreCase = true) ||
        matches(Regex("""\d+\s*(?:s|m|h|d|w|mo|y)\s+ago""", RegexOption.IGNORE_CASE))
