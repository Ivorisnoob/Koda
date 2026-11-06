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
 * touching the playing row: everything around it is taken out and put back in
 * the new order. Four calls whatever the queue's length, where a move per row
 * would send a timeline per row to every connected controller.
 *
 * Rows not named in [target] are kept, after it, in the order they had.
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

    if (current + 1 < keys.size) removeMediaItems(current + 1, keys.size)
    if (current > 0) removeMediaItems(0, current)
    if (after.isNotEmpty()) addMediaItems(1, after)
    if (before.isNotEmpty()) addMediaItems(0, before)
}
