package com.ivor.ivormusic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Replacing a stream URI replaces its Media3 source and can reset its position.
 * Read the current position and playback intent immediately before replacement:
 * resolution may have taken seconds, during which the user can seek or pause.
 * An upcoming item can also have become current while its prefetch was waiting.
 * Must run on the player's application thread, like the surrounding queue edits.
 */
internal fun Player.replaceMusicSource(index: Int, replacement: MediaItem) {
    val replacesCurrent = index == currentMediaItemIndex
    val resumePositionMs = if (replacesCurrent) currentPosition.coerceAtLeast(0L) else 0L
    val resumePlayback = playWhenReady

    replaceMediaItem(index, replacement)

    if (replacesCurrent) {
        // Restore the index as well: removing the old source can select a
        // different successor under shuffle. Seek before preparing so the new
        // source starts loading at the retained position, including zero.
        seekTo(index, resumePositionMs)
        prepare()
        playWhenReady = resumePlayback
    }
}
