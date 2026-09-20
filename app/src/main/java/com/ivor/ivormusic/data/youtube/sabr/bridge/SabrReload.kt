package com.ivor.ivormusic.data.youtube.sabr.bridge

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrReloadException
import kotlinx.coroutines.CancellationException

/**
 * Bounded reload recovery (stage 8a): runs [attempt] once, and when the server
 * answers reload-player-response runs [onReload] - invalidate the attestation
 * and drop cached snapshots - then tries exactly once more with a fresh
 * resolution. Anything after that, including a second reload, returns null so
 * the caller falls back to the direct path at the preserved position.
 * Other first-attempt failures propagate untouched - the caller owns mapping
 * those to its direct fallback. Cancellation always propagates; it is never
 * a fallback.
 */
internal suspend fun <T> reloadOnce(
    onReload: suspend () -> Unit,
    attempt: suspend () -> T?,
): T? {
    try {
        return attempt()
    } catch (e: SabrReloadException) {
        // Single bounded re-resolution below; a second reload still falls back.
    }
    onReload()
    return try {
        attempt()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
