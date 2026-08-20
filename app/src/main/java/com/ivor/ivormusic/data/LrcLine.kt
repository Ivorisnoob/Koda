package com.ivor.ivormusic.data

/**
 * Represents a single line of synced lyrics with its timestamp.
 */
data class LrcLine(
    val timeMs: Long,
    val text: String,
    val contentSpans: List<LrcContentSpan> = emptyList()
)

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
