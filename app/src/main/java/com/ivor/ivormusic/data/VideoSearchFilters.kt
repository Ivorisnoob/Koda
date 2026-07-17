package com.ivor.ivormusic.data

import java.time.LocalDate

/**
 * Upload-date filter for video mode search.
 *
 * Implemented with YouTube's `after:YYYY-MM-DD` search operator appended to
 * the query text, so it rides the normal NewPipe search path with no extra
 * API calls or InnerTube params. Verified July 2026 against the live
 * /search endpoint (results all fall inside the requested window).
 */
enum class VideoSearchDateFilter(val label: String, private val days: Long?) {
    ANY("Any time", null),
    WEEK("This week", 7),
    MONTH("This month", 31),
    SIX_MONTHS("6 months", 183),
    YEAR("This year", 366);

    /** The query with the matching `after:` operator appended, or unchanged for [ANY]. */
    fun applyTo(query: String): String {
        val d = days ?: return query
        return "$query after:${LocalDate.now().minusDays(d)}"
    }
}

/**
 * Result ordering for video mode search, mirroring YouTube's own "Sort by"
 * filter. [code] is the sort value inside the InnerTube `sp` search params
 * (the same protobuf the youtube.com filter sheet sends); non-relevance
 * orders route the search through a direct /search call because NewPipe's
 * YouTube search has no sort support. Verified July 2026 against the live
 * /search endpoint.
 */
enum class VideoSearchSort(val label: String, val code: Int) {
    RELEVANCE("Relevance", 0),
    UPLOAD_DATE("Newest", 2),
    VIEW_COUNT("Most viewed", 3),
    RATING("Top rated", 1)
}
