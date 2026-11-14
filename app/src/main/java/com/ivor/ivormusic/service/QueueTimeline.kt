package com.ivor.ivormusic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * One key per timeline row, in timeline order: the row's queue occurrence, or
 * for a row that carries none, its media id numbered by how many times it has
 * come up so far, so two copies of one song never share a key.
 */
internal fun Player.occurrenceKeys(): List<String> {
    val seen = HashMap<String, Int>()
    return List(mediaItemCount) { index ->
        val item = getMediaItemAt(index)
        item.queueItemId ?: run {
            val nth = seen.merge(item.mediaId, 1, Int::plus)
            "media:${item.mediaId}#$nth"
        }
    }
}

/**
 * Lay the timeline out as [target], an order of [occurrenceKeys], without
 * touching the playing row: everything around it is put back in the new order
 * and the old rows taken out. Four calls whatever the queue's length, where a
 * move per row would send a timeline per row to every connected controller.
 *
 * Rows not named in [target] are kept, after it, in the order they had.
 *
 * **The new rows go in before the old ones come out, and that order is the
 * point** [scar November 2026]. This runs inside a listener callback, so
 * ExoPlayer holds each step's timeline event until the callback returns, and
 * the media session then pairs every one of those timelines with the player's
 * position as it is *now*, after the last step, and throws if that index is
 * not a row of the timeline (`PlayerInfo.Builder.build`). Emptying the queue
 * around the playing row first left a one-row timeline in that batch, so any
 * arrangement that ends with the playing row somewhere other than first -
 * turning shuffle off a few songs in - took the process down. Built this way
 * no step is ever shorter than the playing row's final position.
 */
internal fun Player.arrangeAs(target: List<String>) {
    val keys = occurrenceKeys()
    if (keys == target) return
    val current = currentMediaItemIndex
    if (current !in keys.indices) return
    val rowsByKey = HashMap<String, MediaItem>(keys.size)
    keys.forEachIndexed { index, key -> rowsByKey[key] = getMediaItemAt(index) }
    val ordered = target.filter { it in rowsByKey }.toMutableList()
    val named = ordered.toHashSet()
    keys.filterTo(ordered) { it !in named }

    val playing = keys[current]
    val at = ordered.indexOf(playing)
    val before = ordered.subList(0, at).map(rowsByKey::getValue)
    val after = ordered.subList(at + 1, ordered.size).map(rowsByKey::getValue)

    // before + [old rows before] + playing + [old rows after]
    if (before.isNotEmpty()) addMediaItems(0, before)
    val lead = before.size
    // before + playing + [old rows after]
    if (current > 0) removeMediaItems(lead, lead + current)
    // before + playing
    if (current + 1 < keys.size) removeMediaItems(lead + 1, lead + keys.size - current)
    // before + playing + after
    if (after.isNotEmpty()) addMediaItems(lead + 1, after)
}
