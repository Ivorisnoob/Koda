package com.ivor.ivormusic.data

/**
 * Represents a single line of synced lyrics with its timestamp.
 */
data class LrcLine(
    val timeMs: Long,
    val text: String,
    val contentSpans: List<LrcContentSpan> = emptyList() // Optional word-level spans
)

data class LrcContentSpan(
    val timeMs: Long,
    val text: String,
    val durationMs: Long = 0L // Duration of this span/word
)

/**
 * Result wrapper for lyrics fetch operation.
 */
sealed class LyricsResult {
    data class Success(val lines: List<LrcLine>) : LyricsResult()
    data class Error(val message: String) : LyricsResult()
    object NotFound : LyricsResult()
    object Loading : LyricsResult()
}
