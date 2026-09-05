package com.ivor.ivormusic.data

/** Only consecutive, successfully measured silence at the end counts. */
internal fun measuredTailSilenceMs(envelope: FloatArray, windowMs: Long = 20L): Long {
    var silentWindows = 0L
    for (i in envelope.indices.reversed()) {
        if (!envelope[i].isFinite() || envelope[i] > 0.002f) break
        silentWindows++
    }
    return silentWindows * windowMs
}

/**
 * Extend an already-silent tail using sparse samples. An unreadable sample is
 * unknown, never evidence of silence: abandon the skip if any probe fails.
 * [isSilentAt] reads a 400ms window at the supplied source position in ms.
 */
internal fun probeTrailingSilenceMs(
    durationMs: Long,
    isSilentAt: (Long) -> Boolean?,
): Long {
    if (durationMs <= 0L) return 0L
    var distanceFromEndMs = 20_000L
    var confirmedMs = minOf(durationMs, distanceFromEndMs)
    while (distanceFromEndMs < 65_000L) {
        distanceFromEndMs += 5_000L
        val sampleStartMs = (durationMs - distanceFromEndMs).coerceAtLeast(0L)
        when (isSilentAt(sampleStartMs)) {
            null -> return 0L
            // Keep the unmeasured gap next to an audible sample. Returning
            // that sample's end as the boundary cuts up to five seconds of
            // music that may continue into the gap between probes.
            false -> return confirmedMs.coerceAtMost(60_000L)
            true -> {
                confirmedMs = minOf(durationMs, distanceFromEndMs)
                if (sampleStartMs == 0L) break
            }
        }
    }
    return minOf(durationMs, 60_000L)
}
