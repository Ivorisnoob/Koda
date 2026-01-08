package com.ivor.ivormusic.data

/**
 * Represents a single line of synced lyrics with its timestamp.
 */
data class LrcLine(
    val timeMs: Long,  // Timestamp in milliseconds
    val text: String   // Lyrics text for this line
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
