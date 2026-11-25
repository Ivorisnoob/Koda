package com.ivor.ivormusic.data.lossless

import java.util.concurrent.ConcurrentHashMap

/** Where a lossless song being played was resolved from. */
enum class LosslessOrigin { SERVER, KEPT }

/**
 * The last lossless resolution of each song, for the quality sheet's Source
 * line: the service resolves, the sheet reads.
 *
 * Transport in the sense invariant 6 draws, like `VisualizerBus`: bounded,
 * never persisted, nothing a listener entered, and wrong only in the
 * harmless direction (an answer one resolution old).
 */
object LosslessOrigins {
    private const val MAX = 256
    private val origins = ConcurrentHashMap<String, LosslessOrigin>()

    fun record(songId: String, origin: LosslessOrigin) {
        if (origins.size > MAX) origins.clear()
        origins[songId] = origin
    }

    fun of(songId: String): LosslessOrigin? = origins[songId]
}
