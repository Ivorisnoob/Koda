package com.ivor.ivormusic.data

/**
 * Represents a single line of synced lyrics with its timestamp.
 */
data class LrcLine(
    val timeMs: Long,
    val text: String,
    val contentSpans: List<LrcContentSpan> = emptyList(),
    /** Who sings it, where the provider says; see [LyricVoice]. */
    val voice: LyricVoice = LyricVoice.LEAD
)

/**
 * Which singer a line belongs to, for a duet drawn as a conversation: the
 * first singer's lines at the start edge, the second's at the end, as Apple
 * Music draws them.
 *
 * Read from the provider's own per-line singer: Apple's TTML `ttm:agent` (a
 * `person` or `group` declared in the head) and YouLyPlus's `element.singer`
 * with `metadata.agents` [verified October 2026, `.probe/lyrics_duet_probe.py`].
 * A song with fewer than two singers is all [LEAD], so solo songs look as they
 * always did.
 */
enum class LyricVoice {
    /** The first singer, or the only one. */
    LEAD,

    /** The second singer, drawn on the opposite side. */
    DUET,

    /** Sung together (a `group` agent), drawn on the lead's side. */
    TOGETHER,
}

/**
 * The voice of each line from the provider's singer ids ([lineAgents], one per
 * line, null where it named none) and which ids are groups ([groupAgents]).
 *
 * People are numbered in order of first appearance and alternate sides, so a
 * third singer goes back to the start edge rather than inventing a third
 * column. Fewer than two people anywhere in the song means no duet at all.
 */
internal fun assignLyricVoices(lineAgents: List<String?>, groupAgents: Set<String>): List<LyricVoice> {
    val people = lineAgents.filterNotNull().filter { it !in groupAgents }.distinct()
    if (people.size < 2) return lineAgents.map { LyricVoice.LEAD }
    return lineAgents.map { agent ->
        when {
            agent == null -> LyricVoice.LEAD
            agent in groupAgents -> LyricVoice.TOGETHER
            people.indexOf(agent) % 2 == 1 -> LyricVoice.DUET
            else -> LyricVoice.LEAD
        }
    }
}

data class LrcContentSpan(
    val timeMs: Long,
    val text: String,
    val durationMs: Long = 0L
)

enum class LyricsSyncType(val quality: Int) {
    PLAIN(0),
    LINE(1),
    WORD(2)
}

/**
 * Result wrapper for lyrics fetch operation.
 */
sealed class LyricsResult {
    data class Success(
        val lines: List<LrcLine>,
        val provider: String,
        val syncType: LyricsSyncType
    ) : LyricsResult()

    data class Error(val message: String) : LyricsResult()
    object NotFound : LyricsResult()
    object Loading : LyricsResult()
}

internal data class ParsedLyrics(
    val lines: List<LrcLine>,
    val syncType: LyricsSyncType
)
