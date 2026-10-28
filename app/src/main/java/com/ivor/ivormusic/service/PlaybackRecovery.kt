package com.ivor.ivormusic.service

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.httpFailureUrl
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * What happens when the playing song fails: retry it under a fresh stream,
 * recover the whole queue from a refused visitorData, or move past it and say
 * so - within a bounded number of attempts and a bounded amount of time.
 *
 * Every retry is anchored on the queue occurrence that failed. A tap can
 * replace the queue while YouTube answers, with the same track id, and that
 * new choice must not be reset, replaced or skipped by a stale recovery.
 *
 * Main-thread owned, like the player it drives.
 *
 * @param resolver read on each use, so a replaced resolver is the one asked.
 * @param forgetWarmed lets a remint warm the queue again under the new token.
 * @param announce tells controllers a song was skipped, stopped on, or refused.
 */
@UnstableApi
internal class PlaybackRecovery(
    private val scope: CoroutineScope,
    private val player: () -> Player,
    private val resolver: () -> MusicStreamResolver,
    private val youtube: () -> YouTubeRepository,
    private val forgetWarmed: () -> Unit,
    private val announce: (failed: MediaItem, outcome: String) -> Unit,
) {
    // Per-song retries. Reset on successful playback so a song can't
    // permanently exhaust its budget over the lifetime of the service.
    private val retryCounts = ConcurrentHashMap<String, Int>()

    /**
     * When each queue occurrence first failed, for [MAX_RECOVERY_MS]. Keyed by
     * occurrence rather than song, so the same song tapped again in a new
     * queue starts with a fresh allowance.
     */
    private val recoveryStartedAt = ConcurrentHashMap<String, Long>()

    /** Give [item] its full allowance again: it played, or it was arrived at afresh. */
    fun resetAllowance(item: MediaItem) {
        retryCounts.remove(item.mediaId)
        recoveryStartedAt.remove(recoveryKey(item))
    }

    fun clear() {
        retryCounts.clear()
        recoveryStartedAt.clear()
    }

    fun onError(error: PlaybackException) {
        val player = player()
        val currentItem = player.currentMediaItem ?: return
        val videoId = currentItem.mediaId
        val uri = currentItem.localConfiguration?.uri

        // Stream URLs carry short-lived signatures and local URIs may expose
        // device paths. The media id is sufficient for a user-shared report.
        KLog.w(TAG, "Handling playback error for $videoId")

        // Local songs (content:// or file://) — errors are typically unrecoverable
        // (file deleted, permission revoked, corrupt file). Don't try YouTube resolution.
        if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
            KLog.e(TAG, "Error: Local song $videoId failed. Skipping (not retryable via YouTube).")
            skipAfterFailure(currentItem)
            return
        }

        // 1. If we are already resolving this item, just wait.
        // The validation logic or update logic will handle it when ready.
        if (resolver().isResolving(videoId)) {
            KLog.d(TAG, "Error: Already resolving $videoId. Ignoring error.")
            return
        }

        // 2. A resolution the bot check just refused with a fresh identity is
        // not retried, and the queue is not skipped through. Each retry was a
        // full NewPipe extraction plus the direct chain, and each skip resolved
        // the next song into the same refusal, so a queue became a loop of
        // 20-40 requests per song against a network YouTube had already
        // refused. The player stays on this song: skipping or tapping another
        // one still resolves it, and after the verdict expires this retries
        // normally.
        if (uri?.scheme == "error" && YouTubeRepository.isBotCheckVerdictActive()) {
            KLog.w(TAG, "Error: $videoId refused by YouTube's bot check; not retrying or skipping")
            announce(currentItem, MusicService.FAILURE_REFUSED)
            return
        }

        // 3. The resolver spent its whole budget, every client included, and
        // got nothing. Asking again is another twenty seconds of spinner for
        // the same answer - with two retries, a broken song used to hold the
        // player for most of a minute.
        if (uri?.scheme == "error" && uri.host == MusicStreamResolver.RESOLUTION_TIMEOUT_HOST) {
            KLog.e(TAG, "Error: resolution for $videoId used its whole budget. Skipping.")
            skipAfterFailure(currentItem)
            return
        }

        // 4. However the retries below go, one song gets a bounded amount of
        // recovery time, counted from its first failure.
        val now = SystemClock.elapsedRealtime()
        val recoveryStarted = recoveryStartedAt.getOrPut(recoveryKey(currentItem)) { now }
        if (now - recoveryStarted > MAX_RECOVERY_MS) {
            KLog.e(TAG, "Error: recovering $videoId took over ${MAX_RECOVERY_MS}ms. Skipping.")
            skipAfterFailure(currentItem)
            return
        }

        // 5. Retry Logic (YouTube songs only)
        val retryCount = retryCounts[videoId] ?: 0
        // Any googlevideo 403, whichever client issued the URL, as the video
        // player and downloads already treat it. This used to be limited to
        // ANDROID_VR and IOS, from when NewPipe resolved music under its own
        // identities. Music now resolves through visionOS under Koda's
        // visitorData, and the verdict reaches it too [verified October 2026,
        // `.probe/visionos_token_verdict_probe.py`: one fresh token in eight
        // got /player OK and the opening bytes, then 403 on every range past
        // them, for every song]. Left out of this check, such a token was
        // re-resolved twice into the same refusal and the song skipped, and so
        // was each song after it for the token's whole TTL.
        val isVisitorDataForbidden = httpResponseCode(error) == 403
        val forbiddenUrl = if (isVisitorDataForbidden) httpFailureUrl(error) else null
        val maxRetries = when {
            isVisitorDataForbidden -> MAX_FORBIDDEN_RETRIES
            uri?.scheme == "error" -> MAX_RESOLUTION_RETRIES
            else -> MAX_RETRIES
        }

        if (retryCount >= maxRetries) {
            KLog.e(TAG, "Error: Max retries exhausted for $videoId. Skipping.")
            skipAfterFailure(currentItem)
            return
        }

        KLog.w(TAG, "Error: Retrying ($retryCount/$maxRetries) for $videoId...")
        retryCounts[videoId] = retryCount + 1
        resolver().forgetUrl(videoId)

        scope.launch {
            delay(RETRY_DELAY_MS)
            if (player().currentMediaItem?.isSameQueueItemAs(currentItem) != true) {
                return@launch
            }
            // Mint a fresh visitorData before re-resolving. /player answered
            // 200 and never sees this refusal, so without it the flagged
            // token stays in prefs and is replayed for its whole 6h TTL -
            // every uncached song failing until the user clears app data.
            // Mirrors VideoPlayerViewModel.recoverFromSourceError. A URL signed
            // under a token another surface already replaced keeps the current
            // one; the queue below is dropped either way, since it may hold
            // URLs from before that replacement.
            if (isVisitorDataForbidden) {
                youtube().refreshVisitorDataAfterPlaybackFailure(forbiddenUrl)
                currentCoroutineContext().ensureActive()
                if (player().currentMediaItem?.isSameQueueItemAs(currentItem) != true) {
                    return@launch
                }
                // Everything the prefetch resolved was signed with the token
                // just discarded, so the rest of the queue is already dead.
                // Dropping it here turns one recovery into a recovery for the
                // whole queue, instead of the same stall repeating on every
                // following song.
                resolver().forgetAllUrls()
                retryCounts.clear()
                retryCounts[videoId] = retryCount + 1
                val key = recoveryKey(currentItem)
                recoveryStartedAt.keys.removeAll { it != key }
                resetUpcomingItemsToPlaceholders()
            }
            // FORCE new resolution
            val resolved = reResolve(currentItem)
            val current = player()
            if (current.currentMediaItem?.isSameQueueItemAs(currentItem) != true) return@launch
            if (resolved == null) {
                KLog.e(TAG, "Recovery: re-resolving $videoId did not finish in time. Skipping.")
                skipAfterFailure(currentItem)
                return@launch
            }
            KLog.i(TAG, "Recovery: Restoring $videoId at ${current.currentPosition}ms")
            current.replaceMusicSource(current.currentMediaItemIndex, resolved)
        }
    }

    /**
     * The now-playing sheet's "Refresh stream": the playing song only.
     *
     * The same moves as the automatic recovery - drop the cached URL, force a
     * fresh resolution, swap it in with [replaceMusicSource] so the position
     * and play intent stand - without its preconditions, because the user is
     * reporting something the player may not have noticed (a stream that
     * stalls, sounds wrong, or failed and was retried out). visitorData is
     * deliberately left alone: reminting it would throw away every other
     * song's resolved stream, and this is a request about one song.
     *
     * If the user moves on while YouTube answers, the new stream is dropped
     * rather than put under a different song.
     */
    fun refreshCurrent(): ListenableFuture<SessionResult> {
        val currentItem = player().currentMediaItem
            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
        val uri = currentItem.localConfiguration?.uri
        if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
        val videoId = currentItem.mediaId
        KLog.i(TAG, "Refresh requested for $videoId")
        return scope.future {
            resolver().forgetUrl(videoId)
            resetAllowance(currentItem)
            val resolved = reResolve(currentItem)
            val current = player()
            if (current.currentMediaItem?.isSameQueueItemAs(currentItem) != true) {
                // Moved on; nothing went wrong, there is just nothing to apply.
                return@future SessionResult(SessionResult.RESULT_INFO_SKIPPED)
            }
            if (resolved == null) return@future SessionResult(SessionResult.RESULT_ERROR_IO)
            current.replaceMusicSource(current.currentMediaItemIndex, resolved)
            SessionResult(SessionResult.RESULT_SUCCESS)
        }
    }

    /** A fresh resolution for [item], bound to it, or null when none arrived in time. */
    private suspend fun reResolve(item: MediaItem): MediaItem? {
        val resolver = resolver()
        resolver.forgetInFlight(item.mediaId)
        return try {
            withTimeoutOrNull(RETRY_RESOLVE_BUDGET_MS) { resolver.resolve(item).await() }
                ?.let { resolver.bind(item, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(TAG, "Recovery: resolving ${item.mediaId} failed", e)
            null
        }
    }

    /** Recovery bookkeeping is per queue occurrence, falling back to the song id. */
    private fun recoveryKey(item: MediaItem): String = item.queueItemId ?: item.mediaId

    /**
     * Move past a song that cannot be played, and tell the app, which used to
     * learn nothing: the queue just jumped, or stopped, with no word why.
     */
    private fun skipAfterFailure(failed: MediaItem) {
        val player = player()
        if (player.hasNextMediaItem()) {
            player.seekToNext()
            player.play()
            announce(failed, MusicService.FAILURE_SKIPPED)
        } else {
            player.stop()
            announce(failed, MusicService.FAILURE_STOPPED)
        }
    }

    /**
     * Put every already-resolved YouTube item in the queue back to its
     * placeholder URI, so the normal prefetch path resolves it again.
     *
     * The prefetch only acts on placeholders, so an item it has already
     * replaced would keep its stream URL forever. After a visitorData remint
     * those URLs are all signed with the discarded token, and leaving them in
     * place means the 403, the retry and the stall repeat once per song for
     * the rest of the queue.
     *
     * The playing item is left alone: its own re-resolution is already in
     * flight, and replacing it here would fight that. Local songs are left
     * alone because their content:// / file:// URIs never came from YouTube.
     */
    private fun resetUpcomingItemsToPlaceholders() {
        val player = player()
        val playingIndex = player.currentMediaItemIndex
        var reset = 0
        for (index in 0 until player.mediaItemCount) {
            if (index == playingIndex) continue
            val item = player.getMediaItemAt(index)
            val uri = item.localConfiguration?.uri ?: continue
            if (uri.scheme != "http" && uri.scheme != "https") continue
            val url = uri.toString()
            if (url.startsWith(MusicStreamResolver.PLACEHOLDER_PREFIX)) continue
            if (url.startsWith(MusicStreamResolver.CACHED_PREFIX)) continue
            player.replaceMediaItem(
                index,
                item.buildUpon().setUri("${MusicStreamResolver.PLACEHOLDER_PREFIX}${item.mediaId}").build(),
            )
            reset++
        }
        // Warming is one-shot per id, so an id warmed under the old token would
        // never be re-warmed. Forget them and let the fresh prefetch warm again.
        forgetWarmed()
        if (reset > 0) KLog.d(TAG, "Recovery: reset $reset queued item(s) for re-resolution")
    }

    /**
     * The HTTP status behind a playback failure, or null when the error did not
     * come from an HTTP response. googlevideo's refusals surface as an
     * [HttpDataSource.InvalidResponseCodeException] nested some way down the
     * cause chain, never as the top-level exception.
     */
    private fun httpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            cause = cause.cause
        }
        return null
    }

    private companion object {
        const val TAG = "MusicService"

        // Re-resolution attempts before a song is skipped. A dead or expired URL
        // is fixed by a fresh extraction or not at all, so the general ceiling
        // stays low. A googlevideo 403 is different: it is a verdict on the
        // visitorData the stream was resolved under, so each retry re-rolls
        // that identity. Four attempts recover the large majority (measured
        // August 2026 on ANDROID_VR, where about half of fresh tokens were
        // refused; one in eight on visionOS in October 2026).
        const val MAX_RETRIES = 2
        const val MAX_FORBIDDEN_RETRIES = 4

        /**
         * A resolution that failed outright (not one that timed out, which is
         * skipped at once) gets one more try: enough for a network blip, and
         * no second full client chain for a song that is simply unplayable.
         */
        const val MAX_RESOLUTION_RETRIES = 1

        /**
         * How long a retry waits for its re-resolution. A retry, not a first
         * attempt: that already had [MusicStreamResolver.RESOLVE_TIMEOUT_MS],
         * and the usual cause here - an expired URL - resolves in a second or two.
         */
        const val RETRY_RESOLVE_BUDGET_MS = 15_000L

        /**
         * The most time one song may spend being recovered, counted from its
         * first error. Together with skipping a timed-out resolution at once,
         * this is what keeps a broken song from holding the player on a
         * loading spinner for most of a minute before anything moves.
         */
        const val MAX_RECOVERY_MS = 30_000L

        const val RETRY_DELAY_MS = 1000L
    }
}
