package com.ivor.ivormusic.data

/**
 * Where playback starts when a list is played from one tapped song.
 *
 * Its own file so a JVM test can reach it: this is the decision that turns
 * "the user tapped this row" into an index, and getting it wrong is not
 * visible in a diff - it is heard, as a different song.
 *
 * Two lookups, in this order, and both are load-bearing:
 *
 * - **Reference identity first.** A playlist may list the same song twice, and
 *   both occurrences are equal `Song` values with the same id. The caller
 *   passes the object out of the list it drew, so identity picks the
 *   occurrence that was actually tapped rather than the first copy of it.
 * - **Song id second**, for callers whose displayed list is a re-mapped copy
 *   (a sorted view, a deduplicated history) rather than the list handed on.
 *
 * Returns [ABSENT] when the song is in neither. That is a wiring fault rather
 * than a user-visible state - the list a call site plays must be the list it
 * shows - and it is reported instead of clamped, because clamping to zero is
 * how a tap on one song came to play another with nothing logged.
 */
internal const val QUEUE_START_ABSENT = -1

internal fun queueStartIndex(songs: List<Song>, startSong: Song?): Int {
    if (startSong == null) return 0
    songs.indexOfFirst { it === startSong }.let { if (it >= 0) return it }
    return songs.indexOfFirst { it.id == startSong.id }
}
