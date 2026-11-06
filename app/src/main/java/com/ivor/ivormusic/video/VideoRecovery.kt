package com.ivor.ivormusic.video

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.media3.common.PlaybackException
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ConnectionAdvice
import com.ivor.ivormusic.data.NetworkChangeWatcher
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.VideoQueue
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.connectionAdviceFor
import com.ivor.ivormusic.data.httpFailureUrl
import com.ivor.ivormusic.util.KLog
import com.ivor.ivormusic.video.core.VideoFailure
import com.ivor.ivormusic.video.core.VideoRecoveryPolicy
import com.ivor.ivormusic.video.core.VideoRecoveryStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What [VideoRecovery] needs from the player that owns the video. */
interface VideoRecoveryHost {
    val currentVideo: VideoItem?
    val queue: VideoQueue?
    val isLocalPlayback: Boolean
    val hasPlayer: Boolean

    /** Whether any of this video has played or buffered. */
    val hasPlayableBytes: Boolean

    /** The SDR rendition to fall back to, when an HDR one is playing. */
    fun sdrFallback(): VideoQuality?
    fun fallBackToSdr(quality: VideoQuality)
    fun rePrepare()
    fun pause()
    fun setLoading(loading: Boolean)
    fun clearBuffering()
    fun playQueueEntry(index: Int)

    /** Run the whole load again, for when there is no player to reload into. */
    fun restart(video: VideoItem)

    /** New URLs at the current position; false when resolution found nothing. */
    suspend fun reresolveAndReload(video: VideoItem): Boolean
}

/**
 * What happens when a video breaks: the player's error goes through
 * [VideoRecoveryPolicy], and each step it picks is carried out here. Also the
 * overlay's Retry, and the connection advice with its retry on a network
 * change.
 *
 * Runs on [scope]'s main dispatcher. [playbackError] stays the host's, since
 * opening a video writes it too; this watches it for advice.
 */
class VideoRecovery(
    private val context: Context,
    private val repository: YouTubeRepository,
    private val scope: CoroutineScope,
    private val playbackError: MutableStateFlow<Throwable?>,
    private val host: VideoRecoveryHost,
) {
    private val policy = VideoRecoveryPolicy()

    /**
     * Network advice for the current error when YouTube refused the connection
     * rather than the video; see [connectionAdviceFor]. While it stands, a
     * network change retries on its own.
     */
    private val _connectionAdvice = MutableStateFlow<ConnectionAdvice?>(null)
    val connectionAdvice: StateFlow<ConnectionAdvice?> = _connectionAdvice.asStateFlow()

    private val connectionWatcher = NetworkChangeWatcher(context) {
        scope.launch { retryAfterNetworkChange() }
    }

    private var job: Job? = null

    init {
        scope.launch {
            playbackError.collect { error ->
                // A device video never asked YouTube for anything.
                val advice = error
                    ?.takeUnless { host.isLocalPlayback }
                    ?.let { connectionAdviceFor(context, it) }
                _connectionAdvice.value = advice
                if (advice != null) connectionWatcher.start() else connectionWatcher.stop()
            }
        }
    }

    fun onPlayerError(error: PlaybackException) {
        val sourceError = isRecoverableSourceError(error)
        val sdrFallback = host.sdrFallback()
        val queue = host.queue
        val failure = VideoFailure(
            isRendererError = isTransientRendererError(error),
            isSourceError = sourceError,
            httpStatus = httpResponseCode(error),
            hasSdrFallback = sdrFallback != null,
            networkAllowed = !sourceError || networkPlaybackAllowed(),
            isLocalPlayback = host.isLocalPlayback,
            isBotCheckActive = YouTubeRepository.isBotCheckVerdictActive(),
            queueHasNext = queue?.hasNext == true,
        )
        when (policy.next(failure, SystemClock.elapsedRealtime())) {
            VideoRecoveryStep.FALL_BACK_TO_SDR -> {
                val sdr = sdrFallback ?: return
                KLog.w(TAG, "HDR failed; falling back to ${sdr.displayLabel}", error)
                host.fallBackToSdr(sdr)
            }
            VideoRecoveryStep.RE_PREPARE -> {
                KLog.w(
                    TAG,
                    "Transient renderer error (attempt ${policy.rendererRetries}/" +
                        "${VideoRecoveryPolicy.MAX_RENDERER_RETRIES}); re-preparing",
                    error,
                )
                host.rePrepare()
            }
            VideoRecoveryStep.HOLD_OFFLINE -> holdOffline()
            VideoRecoveryStep.RE_RESOLVE -> {
                KLog.w(
                    TAG,
                    "Source error (attempt ${policy.sourceRetries}/" +
                        "${VideoRecoveryPolicy.MAX_SOURCE_RETRIES}); re-resolving stream",
                    error,
                )
                recoverFromSourceError(error)
            }
            VideoRecoveryStep.SKIP_IN_QUEUE -> {
                val index = queue?.index ?: return
                KLog.w(
                    TAG,
                    "Unplayable video in the queue at $index (skip ${policy.queueSkips}/" +
                        "${VideoRecoveryPolicy.MAX_QUEUE_SKIPS}); moving on",
                    error,
                )
                scope.launch { host.playQueueEntry(index + 1) }
            }
            VideoRecoveryStep.SHOW_ERROR -> {
                playbackError.value = error
                host.clearBuffering()
            }
        }
    }

    /**
     * Retry after playback failed, from the error overlay. Re-resolves the
     * stream and rebuilds the media source at the current position; falls back
     * to re-running the whole load when there is no player to reload into.
     */
    fun retry() {
        policy.onRetry()
        playbackError.value = null
        val video = host.currentVideo ?: return
        if (host.isLocalPlayback || !host.hasPlayer) {
            // Restarting, not reopening: retrying the video that failed is not
            // leaving the playlist it belongs to.
            host.restart(video)
            return
        }
        // Always re-resolve rather than replaying the current quality: its URL
        // is what just failed, and googlevideo URLs are short-lived, so handing
        // the same one back to the player fails identically every time.
        host.setLoading(true)
        job?.cancel()
        job = scope.launch {
            if (!host.reresolveAndReload(video)) {
                playbackError.value = Exception("Unable to load video stream")
                host.setLoading(false)
            }
        }
    }

    /** Playback reached READY: recovered, or started cleanly. */
    fun onReady() = policy.onReady()

    /** One tick of the progress poll while the player is actually playing. */
    fun onPlaying() = policy.onHealthy(SystemClock.elapsedRealtime())

    /** The user chose where to go in or out of a queue. */
    fun onQueueMoved() = policy.onQueueMoved()

    /** A new video: a recovery still in flight belongs to the previous one. */
    fun onNewVideo() {
        policy.onNewVideo()
        job?.cancel()
        job = null
    }

    fun close() {
        connectionWatcher.stop()
        job?.cancel()
        job = null
    }

    /**
     * The connection is gone. Keep the current item, surface and controls
     * intact; Retry later re-resolves and continues from this exact position.
     */
    private fun holdOffline() {
        val hadPlayableBytes = host.hasPlayableBytes
        host.pause()
        playbackError.value = Exception(
            context.getString(
                if (hadPlayableBytes) {
                    R.string.video_cached_portion_ended
                } else {
                    R.string.video_not_cached_offline
                }
            )
        )
        host.clearBuffering()
        host.setLoading(false)
    }

    /**
     * Rescue a source failure without bothering the user: mint a fresh
     * visitorData when googlevideo refused us outright (403 means the token or
     * the request's client signature was rejected at the media layer - stream
     * resolution itself answered 200 and never sees this), then re-resolve and
     * reload. Falls back to surfacing [original] so the error overlay and its
     * Retry button still appear when recovery cannot help.
     */
    private fun recoverFromSourceError(original: PlaybackException) {
        val video = host.currentVideo
        if (video == null) {
            playbackError.value = original
            host.clearBuffering()
            return
        }
        // Loading, not buffering: a fatal error drives the player to
        // STATE_IDLE, which clears buffering and would leave the re-resolve
        // showing a frozen frame with no spinner. "Resolving a stream" is
        // exactly what loading already means during the initial load.
        host.setLoading(true)
        job?.cancel()
        job = scope.launch {
            if (httpResponseCode(original) == 403) {
                repository.refreshVisitorDataAfterPlaybackFailure(httpFailureUrl(original))
            }
            if (!host.reresolveAndReload(video)) {
                playbackError.value = original
                host.setLoading(false)
            }
        }
    }

    private suspend fun retryAfterNetworkChange() {
        if (_connectionAdvice.value == null) return
        KLog.i(TAG, "Network changed while YouTube was refusing the connection; retrying")
        YouTubeRepository.forgetConnectionVerdicts()
        repository.refreshVisitorDataAfterPlaybackFailure()
        // The user may have moved on or retried while the remint ran.
        if (_connectionAdvice.value != null) retry()
    }

    private fun networkPlaybackAllowed(): Boolean {
        if (ThemePreferences.isLocalOnly(context)) return false
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private companion object {
        const val TAG = "VideoRecovery"
    }
}
