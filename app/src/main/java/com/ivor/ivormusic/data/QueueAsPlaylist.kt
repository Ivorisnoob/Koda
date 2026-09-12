package com.ivor.ivormusic.data

import java.text.DateFormat
import java.util.Date

/**
 * Save-the-queue-as-a-playlist helpers (issue #229).
 *
 * The queue is a device-side list of occurrences: the same track may appear
 * more than once, and the order on screen is the order that was playing. A
 * saved copy must carry both across exactly, so this maps occurrences to
 * songs one-to-one and never deduplicates. [copyPlaylistToLocal] in
 * HomeViewModel drops duplicate ids on purpose - a local playlist cannot
 * survive per-id removal with repeats - but that trade belongs to copying
 * someone else's playlist, not to keeping your own queue.
 */
fun queueTracksForPlaylist(queue: List<MusicQueueItem>): List<Song> =
    queue.map { it.song }

/**
 * Locale-aware date text for the pre-filled playlist name, e.g. "12 Sept 2026"
 * in en-GB or "Sep 12, 2026" in en-US. java.text on purpose: it exists on
 * every API level this app runs, unlike java.time formatting.
 */
fun queuePlaylistDateText(timeMs: Long = System.currentTimeMillis()): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timeMs))
