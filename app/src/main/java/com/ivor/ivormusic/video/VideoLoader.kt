package com.ivor.ivormusic.video

import android.icu.text.CompactDecimalFormat
import com.ivor.ivormusic.data.ReturnDislikeRepository
import com.ivor.ivormusic.data.SponsorBlockRepository
import com.ivor.ivormusic.data.SponsorSegment
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.VideoStreamResult
import com.ivor.ivormusic.data.WatchNextData
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.activeCategories
import com.ivor.ivormusic.util.KLog
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where [VideoLoader]'s results land. Every call but the two reads is on the main thread. */
interface VideoLoadHost {
    val currentVideoId: String?
    val isLive: Boolean

    /** HDR is wanted, this display shows it, and nothing in the player withholds it. */
    fun allowsHdr(): Boolean

    /** The previous video's media must go before the new load begins. */
    fun clearMedia()
    fun onStreamsResolved(video: VideoItem, result: VideoStreamResult, resume: VideoResume)
    fun onStreamsFailed(error: Exception)
    fun onSponsorSegments(segments: List<SponsorSegment>)
    fun onWatchNext(data: WatchNextData)
    fun onDislikeCount(text: String)
}

/** Where a load lands: seek here, when set, and stay paused or play. */
data class VideoResume(val positionMs: Long?, val paused: Boolean)

/**
 * The network half of opening a video. [load] starts three independent jobs:
 * stream URLs, the only one that delays the first frame; SponsorBlock; and one
 * `/next` for everything else. Add no extractions here.
 *
 * Results belong to one specific load. Cancellation handles the normal case;
 * the generation check is still required because NewPipe's blocking fetch can
 * finish after cancellation and because a forced retry can reuse the same video
 * id.
 */
class VideoLoader(
    private val repository: YouTubeRepository,
    private val themePreferences: ThemePreferences,
    private val scope: CoroutineScope,
    private val host: VideoLoadHost,
) {
    private val sponsorBlock = SponsorBlockRepository()
    private val returnDislike = ReturnDislikeRepository()

    private var streamJob: Job? = null
    private var watchNextJob: Job? = null
    private var sponsorBlockJob: Job? = null
    private var generation = 0L

    /** A new video is opening: whatever was loading no longer counts. */
    fun begin() {
        generation++
        streamJob?.cancel()
        watchNextJob?.cancel()
    }

    fun cancelSponsorBlock() {
        sponsorBlockJob?.cancel()
    }

    fun close() {
        streamJob?.cancel()
        streamJob = null
        watchNextJob?.cancel()
        watchNextJob = null
    }

    /** Stream URLs for [videoId], without HDR when [VideoLoadHost.allowsHdr] says so. */
    suspend fun resolveStreams(videoId: String): VideoStreamResult {
        // The preference is intent; the display capability decides whether
        // HDR can produce a visible benefit. This also handles an enabled
        // value restored onto an SDR-only phone.
        val includeHdr = host.allowsHdr()
        val result = repository.getVideoStreamResult(videoId, includeHdr)
        val qualities = if (includeHdr) {
            result.qualities
        } else {
            result.qualities.filterNot(VideoQuality::isHdr)
        }
        return result.copy(qualities = qualities)
    }

    /** Fetch everything [video] needs. Call [begin] first. */
    fun load(video: VideoItem, resume: VideoResume) {
        val loaded = generation
        fun isCurrent() = generation == loaded && host.currentVideoId == video.videoId

        loadStreams(video, resume, ::isCurrent)
        loadSponsorSegments(video, ::isCurrent)
        loadWatchNext(video, ::isCurrent)
    }

    private fun loadStreams(video: VideoItem, resume: VideoResume, isCurrent: () -> Boolean) {
        streamJob = scope.launch {
            try {
                if (!isCurrent()) return@launch
                host.clearMedia()
                // NewPipe's fetchPage() is blocking. A coroutine withTimeout
                // cannot interrupt it; when extraction took 17-21 seconds it
                // waited for valid qualities and then discarded them as timed
                // out. The downloader's connect/read timeouts are the actual
                // network bounds, while the generation check still prevents a
                // cancelled load from touching a newer video.
                val result = resolveStreams(video.videoId)
                if (!isCurrent()) return@launch
                host.onStreamsResolved(video, result, resume)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrent()) {
                    KLog.e(TAG, "Initial stream load failed for ${video.videoId}", e)
                    host.onStreamsFailed(e)
                }
            }
        }
    }

    /**
     * Its own job rather than a step inside the `/next` load: the lookup is
     * keyed on the video id alone and needs nothing watch-next returns, so
     * making it wait behind that response would delay skipping for no reason.
     * It must never join the stream load either - a third-party service being
     * slow must not delay the first frame.
     */
    private fun loadSponsorSegments(video: VideoItem, isCurrent: () -> Boolean) {
        sponsorBlockJob = scope.launch {
            // Fresh reads: the settings screen writes through its own
            // ThemePreferences instance, and the user may have changed either
            // of these since this was built.
            if (!themePreferences.isSponsorBlockEnabled()) return@launch
            val categories = activeCategories(themePreferences.sponsorBlockActionsNow())
            if (categories.isEmpty()) return@launch

            val segments = sponsorBlock.getSegments(video.videoId, categories)
            if (!isCurrent()) return@launch
            // A live broadcast has no fixed timeline to key segments against,
            // and the stream load decides that, so by here it is known.
            if (host.isLive) return@launch
            host.onSponsorSegments(segments)
        }
    }

    /**
     * One watch-next call answers engagement, metadata and related videos. The
     * old code paid for an engagement `/next` call and a full NewPipe extraction
     * here, competing with the first buffering for bandwidth.
     */
    private fun loadWatchNext(video: VideoItem, isCurrent: () -> Boolean) {
        watchNextJob = scope.launch {
            try {
                val watchNext = repository.getWatchNextData(video.videoId, video)
                if (!isCurrent()) return@launch
                host.onWatchNext(watchNext)
                if (themePreferences.isReturnDislikeEnabled() && video.videoId.length == 11) {
                    launch {
                        val count = returnDislike.getDislikes(video.videoId) ?: return@launch
                        if (isCurrent()) {
                            host.onDislikeCount(
                                CompactDecimalFormat
                                    .getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT)
                                    .format(count)
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Non-critical: playback already started.
                if (isCurrent()) KLog.w(TAG, "Failed to load watch-next data", e)
            }
        }
    }

    private companion object {
        const val TAG = "VideoLoader"
    }
}
