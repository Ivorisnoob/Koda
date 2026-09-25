package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.isUnknownArtist

/** An artist the user keeps coming back to, and how often lately. */
data class TopArtist(
    val name: String,
    val plays: Int,
    /** Newest play's artwork: a stand-in until the artist's own photo is known. */
    val fallbackArtwork: String?,
)

/** How far back "top" looks before it falls back to all of history. */
internal const val TOP_ARTISTS_WINDOW_MS = 90L * 24 * 60 * 60 * 1000

/**
 * The most-played artists in [history] (newest first).
 *
 * Counted over the last [TOP_ARTISTS_WINDOW_MS] so the rail follows what the
 * user is into now rather than what they wore out two years ago; when that
 * window holds fewer than [minimum] artists - a quiet spell, a fresh install
 * with an imported history - it widens to everything. Ties go to whoever was
 * played more recently.
 *
 * Only a trailing featured credit is cut ("A feat. B" counts for A). Joined
 * names are left whole on purpose: "Simon & Garfunkel" is one act, and there
 * is no way to tell that apart from a collaboration by the string alone.
 */
fun topArtistsFrom(
    history: List<PlayHistoryEntry>,
    now: Long,
    limit: Int = 10,
    minimum: Int = 3,
): List<TopArtist> {
    val recent = rank(history.filter { now - it.timestamp <= TOP_ARTISTS_WINDOW_MS }, limit)
    return if (recent.size >= minimum) recent else rank(history, limit)
}

private fun rank(history: List<PlayHistoryEntry>, limit: Int): List<TopArtist> {
    data class Tally(val name: String, var plays: Int, val firstSeen: Int, val artwork: String?)
    val tallies = LinkedHashMap<String, Tally>()
    history.forEachIndexed { index, entry ->
        val name = primaryArtist(entry.artist) ?: return@forEachIndexed
        val key = name.lowercase()
        val tally = tallies[key]
        if (tally == null) {
            tallies[key] = Tally(name, 1, index, entry.thumbnailUrl)
        } else {
            tally.plays++
        }
    }
    return tallies.values
        .sortedWith(compareByDescending<Tally> { it.plays }.thenBy { it.firstSeen })
        .take(limit)
        .map { TopArtist(it.name, it.plays, it.artwork) }
}

private val FEATURED_CREDIT = Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)

internal fun primaryArtist(credit: String): String? {
    if (isUnknownArtist(credit)) return null
    return credit.replace(FEATURED_CREDIT, "").trim().takeIf { it.isNotEmpty() }
}
