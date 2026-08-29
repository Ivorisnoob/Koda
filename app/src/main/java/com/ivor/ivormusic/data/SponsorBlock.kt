package com.ivor.ivormusic.data

import androidx.compose.ui.graphics.Color

/**
 * SponsorBlock model and preference encoding.
 *
 * Deliberately free of Android and Compose runtime dependencies beyond
 * [Color], so the encoding helpers below can be unit-tested without a Context -
 * the same reason `captionTextScaleFromStored` and `uiScaleFromStored` live
 * outside `ThemePreferences`.
 */

/**
 * What the player does when playback reaches a segment.
 *
 * Three states rather than a boolean because the categories are not
 * equivalent: a sponsor read is something nearly everyone wants gone, while an
 * intro or an off-topic music section is something plenty of people want to
 * keep. One global switch would force the same answer onto both and get filed
 * as a bug by whoever wanted their music videos left alone.
 */
enum class SegmentAction {
    /** Seek past it automatically, announcing the skip with an undo. */
    SKIP,

    /** Offer a button while inside it and never move on its own. */
    MANUAL,

    /** Do not fetch, draw, or act on this category at all. */
    IGNORE
}

/**
 * The categories Koda understands.
 *
 * [apiName] is SponsorBlock's own identifier and is the wire format, so it is
 * frozen the way a persisted enum constant is - renaming one silently resets
 * that category to its default for every existing user.
 */
enum class SponsorCategory(
    val apiName: String,
    val defaultAction: SegmentAction,
    /**
     * The canonical SponsorBlock colour for this category. See the note in
     * `SegmentColors` below on why these are literals.
     */
    val color: Color
) {
    SPONSOR("sponsor", SegmentAction.SKIP, Color(0xFF00D400)),
    SELF_PROMO("selfpromo", SegmentAction.SKIP, Color(0xFFFFFF00)),
    INTERACTION("interaction", SegmentAction.SKIP, Color(0xFFCC00FF)),
    INTRO("intro", SegmentAction.IGNORE, Color(0xFF00FFFF)),
    OUTRO("outro", SegmentAction.IGNORE, Color(0xFF0202ED)),
    PREVIEW("preview", SegmentAction.IGNORE, Color(0xFF008FD6)),
    MUSIC_OFFTOPIC("music_offtopic", SegmentAction.IGNORE, Color(0xFFFF9900)),
    FILLER("filler", SegmentAction.IGNORE, Color(0xFF7300FF));

    companion object {
        fun fromApiName(name: String): SponsorCategory? =
            entries.firstOrNull { it.apiName == name }

        /** The default map, used before anything is stored and by Reset. */
        fun defaultActions(): Map<SponsorCategory, SegmentAction> =
            entries.associateWith { it.defaultAction }
    }
}

/**
 * One segment of one video, in milliseconds.
 *
 * [uuid] is SponsorBlock's own id for the submission. It is what identifies a
 * segment the viewer has undone, which must survive a seek back into the same
 * range - an index or a start time would not, because a segment can be
 * re-entered from anywhere.
 */
data class SponsorSegment(
    val uuid: String,
    val category: SponsorCategory,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    fun contains(positionMs: Long): Boolean =
        positionMs >= startMs && positionMs < endMs
}

/* ------------------------------------------------------------------ */
/* Preference encoding                                                 */
/* ------------------------------------------------------------------ */

/**
 * Encodes the per-category actions as one preference value.
 *
 * One key rather than eight: the categories are read and written together, and
 * eight keys would mean eight StateFlows, eight listener branches and eight
 * search-index questions for what is a single decision. The format is
 * `apiName:ACTION` pairs joined by `;`, chosen so it stays readable in a
 * backup and survives a category being added or removed - an unknown name is
 * dropped on read and a missing one falls back to its default, so neither an
 * older nor a newer build can be poisoned by the other.
 */
fun encodeSegmentActions(actions: Map<SponsorCategory, SegmentAction>): String =
    SponsorCategory.entries
        .mapNotNull { category ->
            actions[category]?.let { "${category.apiName}:${it.name}" }
        }
        .joinToString(";")

/** Reads what [encodeSegmentActions] wrote, tolerating anything at all. */
fun decodeSegmentActions(stored: String?): Map<SponsorCategory, SegmentAction> {
    val defaults = SponsorCategory.defaultActions().toMutableMap()
    if (stored.isNullOrBlank()) return defaults
    stored.split(';').forEach { pair ->
        val parts = pair.split(':')
        if (parts.size != 2) return@forEach
        val category = SponsorCategory.fromApiName(parts[0].trim()) ?: return@forEach
        val action = SegmentAction.entries.firstOrNull { it.name == parts[1].trim() }
            ?: return@forEach
        defaults[category] = action
    }
    return defaults
}

/**
 * The categories worth asking the server for: anything not ignored.
 *
 * Requesting only what will be acted on keeps the response small and means an
 * ignored category is never even received, which is a stronger guarantee than
 * filtering it after the fact.
 */
fun activeCategories(actions: Map<SponsorCategory, SegmentAction>): List<SponsorCategory> =
    SponsorCategory.entries.filter { actions[it] != SegmentAction.IGNORE }

/**
 * Picks the segment to act on at [positionMs], ignoring [skipped] ones.
 *
 * Segments can overlap and can be nested - a self-promo inside a sponsor read
 * is common - so "the first one that contains the position" is not good enough
 * when they start at the same place. The one ending latest wins, because
 * skipping to the nearer end would land the viewer back inside the other one
 * and skip twice in a row, which reads as a stutter rather than one skip.
 */
fun segmentAt(
    segments: List<SponsorSegment>,
    positionMs: Long,
    ignored: Set<String> = emptySet()
): SponsorSegment? =
    segments
        .filter { it.uuid !in ignored && it.contains(positionMs) }
        .maxByOrNull { it.endMs }
