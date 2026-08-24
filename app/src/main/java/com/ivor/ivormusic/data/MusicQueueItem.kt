package com.ivor.ivormusic.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One occurrence of a [Song] in the music playback queue.
 *
 * [Song.id] identifies the underlying track and is intentionally shared when
 * the same track appears more than once. [id] identifies this occurrence, so
 * selecting, highlighting, moving, removing, and restoring one copy cannot
 * accidentally target another copy of the same song.
 */
@Serializable
data class MusicQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val song: Song
)
