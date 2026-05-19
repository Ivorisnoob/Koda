package com.ivor.ivormusic.domain

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

sealed class LyricsResult {
    data class Success(val lines: List<LrcLine>) : LyricsResult()
    data class Error(val message: String) : LyricsResult()
    object NotFound : LyricsResult()
    object Loading : LyricsResult()
}
