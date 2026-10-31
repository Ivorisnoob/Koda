package com.ivor.ivormusic.video.core

import com.ivor.ivormusic.data.CommentItem
import com.ivor.ivormusic.data.TimedComment

/**
 * The comments that mention a playback time, earliest first, each with the
 * first time it mentions. They drive the timed comments over the video.
 */
fun timedCommentsOf(comments: List<CommentItem>): List<TimedComment> =
    comments.mapNotNull { comment ->
        firstTimestampMs(comment.text)?.let { TimedComment(comment, it) }
    }.sortedBy { it.timeMs }

/**
 * The first playback time [text] mentions ("1:23", "12:05" or "1:02:33"), in
 * milliseconds, or null when it mentions none. A time is read the way a viewer
 * writes one: seconds below 60, and minutes below 60 once hours are given, so
 * a score or a ratio ("3:99") is not taken for one.
 */
fun firstTimestampMs(text: String): Long? {
    val match = TIMESTAMP.find(text) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: return null
    val seconds = match.groupValues[3].toLongOrNull() ?: return null
    if (seconds >= 60) return null
    if (match.groupValues[1].isNotEmpty() && minutes >= 60) return null
    return ((hours * 60 + minutes) * 60 + seconds) * 1000L
}

private val TIMESTAMP = Regex("""(?<!\d)(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?!\d)""")
