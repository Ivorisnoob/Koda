package com.ivor.ivormusic.ui.shorts

import com.ivor.ivormusic.util.KLog

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ivor.ivormusic.data.CommentItem
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.ShortsItem
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoEngagement
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.bestSdrFallback
import com.ivor.ivormusic.ui.video.hasHdrDisplay
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Player for the vertical Shorts feed. Owns its own ExoPlayer (like
 * VideoPlayerViewModel) so Shorts, regular video and music playback stay
 * independent — audio focus keeps them from playing over each other.
 *
 * Streams resolve through the same ANDROID_VR /player pipeline as regular
 * videos (Shorts are ordinary videos server-side), and engagement, metadata
 * and comments come from the same single watch-next call per Short.
 */
@UnstableApi
class ShortsPlayerViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val youtubeRepository = YouTubeRepository(context)
    private val themePreferences = ThemePreferences(context)
    private val videoHistoryRepository = com.ivor.ivormusic.data.VideoHistoryRepository(context)

    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    // ---------------- Feed / pager state ----------------

    private val notInterestedRepository =
        com.ivor.ivormusic.data.NotInterestedRepository(context)

    /** Local hide plus best-effort account propagation - see NotInterestedActions. */
    private val notInterestedActions =
        com.ivor.ivormusic.data.NotInterestedActions(notInterestedRepository, youtubeRepository)

    /**
     * Re-read the playing Short's account state when the profile changes.
     *
     * Engagement and the prefetched watch-next data are the account's view, so
     * after a switch they describe somebody else and the like button would be
     * lit for the wrong person.
     *
     * The Shorts sequence itself is deliberately left in place. It is filtered
     * on ingestion and addressed positionally by both the pager and playIndex,
     * so swapping the list underneath someone mid-watch would move them to a
     * different Short than the one on screen. The feed is personalised and does
     * go stale, but it is refreshed the next time Shorts is opened rather than
     * yanked away from someone actively watching.
     */
    private fun observeProfileSwitches() {
        viewModelScope.launch {
            com.ivor.ivormusic.data.ProfileManager(context)
                .activeProfileId
                .drop(1)
                .distinctUntilChanged()
                .collect {
                    youtubeRepository.clearSessionScopedInstanceCaches()
                    synchronized(watchNextCache) { watchNextCache.clear() }
                    _engagement.value = null
                    val playing = _currentVideo.value ?: return@collect
                    val refreshed = runCatching {
                        youtubeRepository.getVideoEngagement(playing.videoId)
                    }.getOrNull() ?: return@collect
                    if (_currentVideo.value?.videoId == playing.videoId) {
                        _engagement.value = refreshed
                    }
                }
        }
    }

    /**
     * The swipe sequence.
     *
     * Unlike the grid feeds, this is filtered on the way *in* rather than by a
     * derived flow. The pager index and [playIndex] both address this list
     * positionally, so a filtered view layered on top would put the pager on
     * item N of one list and playback on item N of another the moment anything
     * was hidden. Dropping items as they arrive keeps one list and one index.
     */
    private val _shorts = MutableStateFlow<List<ShortsItem>>(emptyList())
    val shorts: StateFlow<List<ShortsItem>> = _shorts.asStateFlow()

    /**
     * Only ids can be filtered here: a sequence entry carries no channel at
     * all (see ShortsItem), so a channel block cannot pre-empt one. It still
     * applies the moment a Short is opened and its channel is known.
     */
    private fun withoutHidden(items: List<ShortsItem>): List<ShortsItem> =
        items.filterNot { notInterestedRepository.isVideoHidden(it.videoId) }

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackError = MutableStateFlow<Throwable?>(null)
    val playbackError: StateFlow<Throwable?> = _playbackError.asStateFlow()

    /**
     * A live broadcast that arrived through the Shorts feed, to be reopened in
     * the main video player.
     *
     * Nothing in a reel entry says whether it is live - the feed hands back an
     * id and a thumbnail, and the answer only shows up once the streams
     * resolve. This player is the wrong home for one when it does: its seek bar
     * describes a duration a live stream does not have, there is no chat, and
     * swiping away mid-broadcast is not what the gesture means here. So it is
     * handed off rather than approximated.
     *
     * No replay, one buffered slot: the collector is installed with the overlay
     * host long before any Short is opened, and a replayed handoff would
     * reopen the stream every time that host recomposed from scratch. The
     * buffer is what keeps the emit from suspending.
     */
    private val _liveHandoff = kotlinx.coroutines.flow.MutableSharedFlow<VideoItem>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val liveHandoff: kotlinx.coroutines.flow.SharedFlow<VideoItem> = _liveHandoff

    /** Metadata of the current Short, enriched by watch-next (title, channel, avatar). */
    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo.asStateFlow()

    // Sequence continuation for the endless feed; null until known/exhausted
    private var nextSequenceParams: String? = null
    private var isLoadingMore = false
    private var playJob: Job? = null
    private var historyReportJob: Job? = null
    private var recoveryJob: Job? = null

    // Retry budgets for the current Short, reset by playIndex - see handlePlayerError.
    private var rendererRetryCount = 0
    private var sourceRetryCount = 0
    private var currentQuality: VideoQuality? = null
    private var hdrFallbackUsed = false

    // ---------------- Background prefetch ----------------
    // Swiping must not show a loading spinner, so stream URLs for the next
    // few Shorts (and one behind, for swipe-back) resolve in the background
    // the moment a Short is opened or settled on. Watch-next payloads for
    // the immediate next Shorts are warmed too, so the title/channel/like
    // rail appears instantly. Both caches are LRU-capped; googlevideo URLs
    // stay valid for hours, far beyond a browsing session's needs.

    private val qualitiesCache = object : LinkedHashMap<String, List<VideoQuality>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<VideoQuality>>) =
            size > 30
    }
    private val watchNextCache = object : LinkedHashMap<String, com.ivor.ivormusic.data.WatchNextData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.ivor.ivormusic.data.WatchNextData>) =
            size > 20
    }
    private val prefetchingIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val prefetchSemaphore = kotlinx.coroutines.sync.Semaphore(2)

    /**
     * Bumped whenever the cache is purged because the token that minted its
     * URLs was refused. A prefetch that was already in flight at that moment
     * resolved under the dead token, so its result must be dropped rather than
     * written back over the purge - otherwise recovery fixes the Short on
     * screen and the next five swipes fail exactly as before.
     */
    private var qualitiesEpoch = 0

    private fun cachedQualities(videoId: String): List<VideoQuality>? =
        synchronized(qualitiesCache) { qualitiesCache[videoId] }

    private fun cacheQualities(videoId: String, qualities: List<VideoQuality>, epoch: Int = qualitiesEpoch) {
        if (qualities.isEmpty()) return
        synchronized(qualitiesCache) {
            if (epoch != qualitiesEpoch) return
            qualitiesCache[videoId] = qualities
        }
    }

    private fun cachedWatchNext(videoId: String): com.ivor.ivormusic.data.WatchNextData? =
        synchronized(watchNextCache) { watchNextCache[videoId] }

    private fun cacheWatchNext(videoId: String, data: com.ivor.ivormusic.data.WatchNextData) {
        synchronized(watchNextCache) { watchNextCache[videoId] = data }
    }

    /**
     * Warm the caches around [index]: stream URLs for the next
     * [STREAM_PREFETCH_AHEAD] Shorts plus the previous one, watch-next for
     * the next [WATCH_NEXT_PREFETCH_AHEAD]. Bounded to two concurrent
     * fetches so prefetch never starves the playing Short's buffer.
     */
    private fun prefetchAround(index: Int) {
        if (!ThemePreferences.isPlaybackPreloadEnabled(context)) return
        val list = _shorts.value
        val nextId = list.getOrNull(index + 1)?.videoId
        val streamTargets =
            ((index + 1)..(index + STREAM_PREFETCH_AHEAD)).mapNotNull { list.getOrNull(it) } +
                listOfNotNull(list.getOrNull(index - 1))
        for (item in streamTargets) {
            val id = item.videoId
            if (cachedQualities(id) != null || !prefetchingIds.add("s:$id")) continue
            val epoch = qualitiesEpoch
            viewModelScope.launch {
                try {
                    prefetchSemaphore.acquire()
                    try {
                        // Skip if a later prefetch/playback already filled it
                        if (cachedQualities(id) == null) {
                            val qualities = resolvePlayableQualities(id)
                            cacheQualities(id, qualities, epoch)
                            if (id == nextId) warmPlayableHead(qualities)
                        }
                    } finally {
                        prefetchSemaphore.release()
                    }
                } catch (e: Exception) {
                    KLog.w("ShortsPlayerVM", "stream prefetch failed for $id", e)
                } finally {
                    prefetchingIds.remove("s:$id")
                }
            }
        }

        val watchNextTargets =
            ((index + 1)..(index + WATCH_NEXT_PREFETCH_AHEAD)).mapNotNull { list.getOrNull(it) }
        for (item in watchNextTargets) {
            val id = item.videoId
            if (cachedWatchNext(id) != null || !prefetchingIds.add("w:$id")) continue
            viewModelScope.launch {
                try {
                    prefetchSemaphore.acquire()
                    try {
                        if (cachedWatchNext(id) == null) {
                            cacheWatchNext(id, youtubeRepository.getWatchNextData(id, item.toVideoItem()))
                        }
                    } finally {
                        prefetchSemaphore.release()
                    }
                } catch (e: Exception) {
                    KLog.w("ShortsPlayerVM", "watch-next prefetch failed for $id", e)
                } finally {
                    prefetchingIds.remove("w:$id")
                }
            }
        }
    }

    /**
     * Put actual playable bytes behind the next swipe, not just an expiring
     * URL. Only the immediate next Short is warmed; farther speculative media
     * costs battery and data without improving the next gesture. Adaptive and
     * live manifests are left to Media3 because their segments are not
     * byte-addressable progressive files.
     */
    private suspend fun warmPlayableHead(qualities: List<VideoQuality>) {
        if (!ThemePreferences.isPlaybackPreloadEnabled(context)) return
        if (qualities.isEmpty()) return
        val quality = pickDefaultQuality(qualities)
        if (quality.isDASH || quality.isLive) return
        val videoBytes = if (ThemePreferences.isNetworkMetered(context)) {
            SHORTS_METERED_WARM_BYTES
        } else {
            SHORTS_UNMETERED_WARM_BYTES
        }
        withContext(Dispatchers.IO) {
            fun warm(uri: String, bytes: Long) {
                val spec = DataSpec.Builder()
                    .setUri(uri)
                    .setPosition(0)
                    .setLength(bytes)
                    .build()
                com.ivor.ivormusic.data.CacheManager.cacheVideoRange(context, spec)
            }
            warm(quality.url, videoBytes)
            quality.audioUrl?.let { warm(it, SHORTS_AUDIO_WARM_BYTES) }
        }
    }

    // ---------------- Engagement ----------------

    private val _engagement = MutableStateFlow<VideoEngagement?>(null)
    val engagement: StateFlow<VideoEngagement?> = _engagement.asStateFlow()

    private val subscriptionActions =
        com.ivor.ivormusic.data.SubscriptionActions(context, youtubeRepository)

    /**
     * Whether the current Short's channel is followed by the account or on
     * this device - see VideoPlayerViewModel.isSubscribedToChannel.
     */
    val isSubscribedToChannel: StateFlow<Boolean> =
        combine(_engagement, subscriptionActions.subscriptions) { engagement, localSubs ->
            val channelId = engagement?.channelId
            engagement?.isSubscribed == true ||
                (channelId != null && localSubs.any { it.channelId == channelId })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** True when the subscribe button has to send the user to sign in first. */
    fun subscribeNeedsLogin(): Boolean = subscriptionActions.subscribeNeedsLogin()

    private val _isLoggedIn = MutableStateFlow(youtubeRepository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // ---------------- Comments (same shape VideoPlayerViewModel exposes,
    // so CommentsSheet is reused as-is) ----------------

    private val _comments = MutableStateFlow<List<CommentItem>>(emptyList())
    val comments: StateFlow<List<CommentItem>> = _comments.asStateFlow()

    private val _isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoading: StateFlow<Boolean> = _isCommentsLoading.asStateFlow()

    private val _isLoadingMoreComments = MutableStateFlow(false)
    val isLoadingMoreComments: StateFlow<Boolean> = _isLoadingMoreComments.asStateFlow()

    private val _replies = MutableStateFlow<Map<String, List<CommentItem>>>(emptyMap())
    val replies: StateFlow<Map<String, List<CommentItem>>> = _replies.asStateFlow()

    private val _loadingReplyIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingReplyIds: StateFlow<Set<String>> = _loadingReplyIds.asStateFlow()

    private var commentsNextToken: String? = null
    private var commentsLoadedForVideoId: String? = null
    private val _createCommentParams = MutableStateFlow<String?>(null)

    val canComment: StateFlow<Boolean> = combine(
        _isLoggedIn, _createCommentParams
    ) { loggedIn, params -> loggedIn && params != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isPostingComment = MutableStateFlow(false)
    val isPostingComment: StateFlow<Boolean> = _isPostingComment.asStateFlow()

    /**
     * Stream data source factory: ChunkedStreamDataSource fetches googlevideo
     * media in bounded ranged chunks (open-ended requests are server-paced to
     * the media bitrate) and picks the per-request User-Agent matching the
     * URL's issuing client — googlevideo answers 403 on a UA mismatch.
     *
     * Declared before [init] on purpose: the ExoPlayer built there installs it
     * as the player-wide MediaSource factory, so a lazy declared further down
     * the class would still be an uninitialised delegate at that point.
     */
    private val streamDataSourceFactory =
        com.ivor.ivormusic.data.CacheManager.createVideoPlaybackDataSourceFactory(context, shorts = true)

    init {
        observeProfileSwitches()
        viewModelScope.launch {
            themePreferences.preferHdr.drop(1).distinctUntilChanged().collect {
                synchronized(qualitiesCache) {
                    qualitiesEpoch++
                    qualitiesCache.clear()
                }
            }
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        _exoPlayer?.let { return it }

        // First frame after ~1s buffered, like the video player. Shorts are
        // under a minute, so the 60s max buffer already covers the whole clip
        // — no need for the long-form 5-minute read-ahead. Audio focus +
        // becoming-noisy pause music/video playback instead of playing over
        // them (and vice versa).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 60_000, 1_000, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Every media source this player builds must fetch through
        // ChunkedStreamDataSource. loadQuality() passes it explicitly for the
        // progressive/merged paths, but the adaptive branch hands the player a
        // bare MediaItem, which would otherwise be served by Media3's stock
        // DefaultDataSource - no per-URL User-Agent, so googlevideo answers 403.
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(streamDataSourceFactory))
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true
                // Shorts loop, YouTube-style
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        handlePlayerError(error)
                    }
                })
            }.also { _exoPlayer = it }
    }

    /**
     * Open the Shorts player on a shelf: [items] become the initial pager
     * feed, [startIndex] is the tapped Short. The tapped item's
     * sequenceParams seed the endless feed once the user swipes near the end.
     */
    fun open(items: List<ShortsItem>, startIndex: Int) {
        if (items.isEmpty()) return
        com.ivor.ivormusic.data.CacheManager.setVideoPlaybackActive(SHORTS_CACHE_OWNER, true)
        ensurePlayer()
        // Keep the tapped Short even if it is hidden - the user asked for this
        // one explicitly, and opening onto a different video would be baffling.
        val tapped = items.getOrNull(startIndex.coerceIn(0, items.size - 1))
        val visible = withoutHidden(items).ifEmpty { listOfNotNull(tapped) }
        val ordered = if (tapped != null && tapped !in visible) listOf(tapped) + visible else visible
        if (ordered.isEmpty()) return
        val index = ordered.indexOf(tapped).coerceAtLeast(0)
        _shorts.value = ordered
        _currentIndex.value = index
        nextSequenceParams = ordered[index].sequenceParams
            ?: ordered.firstNotNullOfOrNull { it.sequenceParams }
        _isActive.value = true
        _isLoggedIn.value = youtubeRepository.isLoggedIn()
        playIndex(index)
        prefetchAround(index)
    }

    /** Called by the pager when the user settles on a page. */
    fun onPageSelected(index: Int) {
        if (!_isActive.value) return
        if (index != _currentIndex.value) {
            _currentIndex.value = index
            playIndex(index)
        }
        prefetchAround(index)
        maybeLoadMore(index)
    }

    /**
     * Hide the Short on screen and move on.
     *
     * A grid can just drop an item, but a Shorts feed shows exactly one thing,
     * so dismissing has to say where the user lands. The item is pulled out of
     * the list and the pager holds its index, which now addresses the next
     * Short - or the previous one when the dismissed Short was last.
     */
    fun markCurrentNotInterested() {
        val video = _currentVideo.value ?: return
        notInterestedActions.hideVideo(video, viewModelScope)
        dropCurrentAndAdvance(video.videoId)
    }

    /** Stop recommending this Short's channel, and move on. */
    fun blockChannelForCurrent() {
        val video = _currentVideo.value ?: return
        notInterestedActions.blockChannel(video, viewModelScope)
        dropCurrentAndAdvance(video.videoId)
    }

    private fun dropCurrentAndAdvance(videoId: String) {
        val remaining = _shorts.value.filterNot { it.videoId == videoId }
        if (remaining.isEmpty()) {
            close()
            return
        }
        _shorts.value = remaining
        val target = _currentIndex.value.coerceIn(0, remaining.lastIndex)
        _currentIndex.value = target
        playIndex(target)
        prefetchAround(target)
    }

    fun close() {
        _isActive.value = false
        playJob?.cancel()
        historyReportJob?.cancel()
        recoveryJob?.cancel()
        _exoPlayer?.stop()
        _exoPlayer?.clearMediaItems()
        _currentVideo.value = null
        currentQuality = null
        _playbackError.value = null
        com.ivor.ivormusic.data.CacheManager.setVideoPlaybackActive(SHORTS_CACHE_OWNER, false)
    }

    /**
     * Seek the current Short, for a timestamp link in its description.
     *
     * Clamped at zero only: a timestamp past the end is YouTube's data being
     * wrong about its own video, and ExoPlayer already clamps to the duration.
     */
    fun seekTo(positionMs: Long) {
        _exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun togglePlayPause() {
        if (_isPlaying.value) _exoPlayer?.pause() else _exoPlayer?.play()
    }

    /** Pause without closing (e.g. app backgrounded while a Short is open). */
    fun pause() {
        _exoPlayer?.pause()
    }

    /** Re-attempt playback of the current Short after an error. */
    fun retryCurrent() {
        rendererRetryCount = 0
        sourceRetryCount = 0
        // The cached URLs for this Short are what just failed, and a manual
        // retry is the user telling us the automatic recovery did not work.
        // playIndex would otherwise replay them straight out of the cache.
        currentItem()?.let { synchronized(qualitiesCache) { qualitiesCache.remove(it.videoId) } }
        playIndex(_currentIndex.value)
    }

    private fun currentItem(): ShortsItem? = _shorts.value.getOrNull(_currentIndex.value)

    /**
     * Recover from a player-level failure, or surface it.
     *
     * Without this the Shorts player registered no error listener at all: a
     * fatal error drives the player to STATE_IDLE, whose handler clears
     * [_isBuffering], so a refused Short sat on a frozen frame with no spinner,
     * no error and no way back. That is survivable in the other two surfaces
     * only because they re-mint and `visitorData` is process-wide - a session
     * that opens straight into Shorts never gets that repair.
     *
     * Mirrors VideoPlayerViewModel: a renderer failure is the codec, not the
     * stream, so it re-prepares in place; a source failure means the URL is
     * dead and only re-resolving can help.
     */
    private fun handlePlayerError(error: PlaybackException) {
        if (fallbackFromHdr(error)) return

        if (isTransientRendererError(error) && rendererRetryCount < MAX_RENDERER_RETRIES) {
            rendererRetryCount++
            KLog.w(
                "ShortsPlayerVM",
                "Transient renderer error (attempt $rendererRetryCount/$MAX_RENDERER_RETRIES); re-preparing",
                error
            )
            _exoPlayer?.prepare()
            return
        }

        if (isRecoverableSourceError(error) && sourceRetryCount < MAX_SOURCE_RETRIES) {
            sourceRetryCount++
            KLog.w(
                "ShortsPlayerVM",
                "Source error (attempt $sourceRetryCount/$MAX_SOURCE_RETRIES); re-resolving stream",
                error
            )
            recoverFromSourceError(error)
            return
        }

        _playbackError.value = error
        _isBuffering.value = false
    }

    private suspend fun resolvePlayableQualities(videoId: String): List<VideoQuality> {
        val includeHdr = themePreferences.isPreferHdrEnabled() && hasHdrDisplay(context)
        val qualities = youtubeRepository.getVideoStreamQualities(videoId, includeHdr)
        return if (includeHdr) {
            qualities
        } else {
            qualities.filterNot(VideoQuality::isHdr)
        }
    }

    private fun fallbackFromHdr(error: PlaybackException): Boolean {
        val failed = currentQuality ?: return false
        if (!failed.isHdr || hdrFallbackUsed) return false
        val item = currentItem() ?: return false
        val qualities = cachedQualities(item.videoId).orEmpty()
        val fallback = bestSdrFallback(qualities, failed) ?: return false

        hdrFallbackUsed = true
        val position = _exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        KLog.w(
            "ShortsPlayerVM",
            "HDR ${failed.displayLabel} failed; falling back to ${fallback.displayLabel}",
            error,
        )
        if (httpResponseCode(error) == 403) {
            synchronized(qualitiesCache) {
                qualitiesEpoch++
                qualitiesCache.clear()
            }
            viewModelScope.launch {
                youtubeRepository.refreshVisitorDataAfterPlaybackFailure()
            }
        }
        loadQuality(fallback, startAtMs = position)
        _exoPlayer?.play()
        return true
    }

    /**
     * Mint a fresh visitorData when googlevideo refused us outright, then
     * re-resolve the Short on screen and reload at the position it died on.
     *
     * The cache purge is the part that is specific to this surface. Stream URLs
     * for the next five Shorts are already resolved and sitting in
     * [qualitiesCache], every one of them minted under the token that just got
     * refused, so keeping them would hand the same dead URLs to the next five
     * swipes and make the re-mint look like it did nothing.
     */
    private fun recoverFromSourceError(original: PlaybackException) {
        val item = currentItem()
        if (item == null) {
            _playbackError.value = original
            _isBuffering.value = false
            return
        }
        val index = _currentIndex.value
        val resumeAt = _exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        _isBuffering.value = true
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            try {
                if (httpResponseCode(original) == 403) {
                    youtubeRepository.refreshVisitorDataAfterPlaybackFailure()
                    synchronized(qualitiesCache) {
                        qualitiesEpoch++
                        qualitiesCache.clear()
                    }
                } else {
                    synchronized(qualitiesCache) { qualitiesCache.remove(item.videoId) }
                }
                youtubeRepository.invalidateVideoStreamResult(item.videoId)
                val qualities = kotlinx.coroutines.withTimeout(15_000L) {
                    resolvePlayableQualities(item.videoId)
                }
                // The user swiped on while we were resolving; that Short owns
                // the player now and this recovery has nothing left to fix.
                if (_currentIndex.value != index) return@launch
                if (qualities.isEmpty()) {
                    _playbackError.value = original
                    _isBuffering.value = false
                    return@launch
                }
                cacheQualities(item.videoId, qualities)
                loadQuality(pickDefaultQuality(qualities), startAtMs = resumeAt)
                _exoPlayer?.play()
                _playbackError.value = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.w("ShortsPlayerVM", "Recovery failed for ${item.videoId}", e)
                if (_currentIndex.value == index) {
                    _playbackError.value = original
                    _isBuffering.value = false
                }
            }
        }
    }

    /**
     * Source-level failures - dead URL, 403, malformed container - are worth
     * re-resolving for. Renderer failures are not: [isTransientRendererError]
     * already re-prepares those in place, and this runs after it.
     */
    private fun isRecoverableSourceError(error: PlaybackException): Boolean {
        if (error is ExoPlaybackException && error.type == ExoPlaybackException.TYPE_SOURCE) {
            return true
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    }

    /**
     * Renderer-level failures (surface torn down, codec reclaimed by another
     * app, transient decode error) are worth re-preparing for.
     */
    private fun isTransientRendererError(error: PlaybackException): Boolean {
        if (error is ExoPlaybackException && error.type == ExoPlaybackException.TYPE_RENDERER) {
            return true
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> true
            else -> false
        }
    }

    /** HTTP status behind a source error, or null when it was not an HTTP failure. */
    private fun httpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    private fun playIndex(index: Int) {
        val item = _shorts.value.getOrNull(index) ?: return
        playJob?.cancel()
        historyReportJob?.cancel()
        recoveryJob?.cancel()
        // Per Short, so a run of unrelated failures across the feed does not
        // exhaust the budget for the one the user is actually watching.
        rendererRetryCount = 0
        sourceRetryCount = 0
        hdrFallbackUsed = false
        currentQuality = null

        _playbackError.value = null
        _currentVideo.value = item.toVideoItem()
        resetEngagementState()

        // Phase 1: streams only, playback ASAP (same two-phase pattern and
        // 15s stuck-buffering guard as VideoPlayerViewModel.playVideo).
        // Prefetched Shorts skip the network entirely and start immediately.
        playJob = viewModelScope.launch {
            try {
                _exoPlayer?.stop()
                _exoPlayer?.clearMediaItems()
                kotlinx.coroutines.withTimeout(15_000L) {
                    val qualities = cachedQualities(item.videoId)
                        ?: resolvePlayableQualities(item.videoId)
                            .also { cacheQualities(item.videoId, it) }
                    if (_currentIndex.value != index) return@withTimeout
                    // Live arrived in the reel feed: hand it to the video
                    // player, which has the vertical live layout and chat, and
                    // stand down before touching the surface.
                    if (qualities.any { it.isLive }) {
                        val handoff = _currentVideo.value?.takeIf { it.videoId == item.videoId }
                            ?: item.toVideoItem()
                        // Emit before close(): close() cancels this very job, so
                        // a suspending emit after it would be cancelled instead
                        // of delivered.
                        _liveHandoff.emit(handoff)
                        close()
                        return@withTimeout
                    }
                    if (qualities.isNotEmpty()) {
                        loadQuality(pickDefaultQuality(qualities))
                        _exoPlayer?.play()
                    } else {
                        val streamUrl = youtubeRepository.getVideoStreamUrl(item.videoId)
                        if (streamUrl != null) {
                            val source = ProgressiveMediaSource.Factory(streamDataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(streamUrl))
                            _exoPlayer?.setMediaSource(source)
                            _exoPlayer?.prepare()
                            _exoPlayer?.play()
                        } else {
                            _playbackError.value = Exception("Unable to load this Short")
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _playbackError.value = Exception("Connection timed out. Please check your internet.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _playbackError.value = e
            }
        }

        // Phase 2: one watch-next call fills engagement + real metadata
        // (title, channel, avatar) — sequence entries arrive with id only.
        // Prefetched payloads make the metadata and like rail appear at once.
        viewModelScope.launch {
            try {
                val watchNext = cachedWatchNext(item.videoId)
                    ?: youtubeRepository.getWatchNextData(item.videoId, item.toVideoItem())
                        .also { cacheWatchNext(item.videoId, it) }
                if (_currentIndex.value != index) return@launch
                _engagement.value = watchNext.engagement
                if (watchNext.updatedVideoItem != null) {
                    _currentVideo.value = watchNext.updatedVideoItem
                }
            } catch (e: Exception) {
                KLog.w("ShortsPlayerVM", "watch-next failed for ${item.videoId}", e)
            }
        }

        // History: Shorts are short, report after 5s of actual playback
        historyReportJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5_000)
            var waitedMs = 0
            while (!_isPlaying.value && waitedMs < 30_000) {
                kotlinx.coroutines.delay(1_000)
                waitedMs += 1_000
            }
            // Fresh pref read: Settings toggles through its own instance
            if (_isPlaying.value && themePreferences.isSaveVideoHistoryEnabled()) {
                val watched = _currentVideo.value?.takeIf { it.videoId == item.videoId }
                    ?: item.toVideoItem()
                videoHistoryRepository.addVideo(watched)
                youtubeRepository.reportVideoPlayback(item.videoId)
            }
        }
    }

    /** Extend the feed when the pager nears its end. Dedupes repeated ids. */
    private fun maybeLoadMore(index: Int) {
        val params = nextSequenceParams ?: return
        if (isLoadingMore || index < _shorts.value.size - 4) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val page = youtubeRepository.getShortsSequence(params)
                val known = _shorts.value.mapTo(HashSet()) { it.videoId }
                val fresh = page.items.filter { it.videoId !in known }
                if (fresh.isNotEmpty()) {
                    _shorts.value = _shorts.value + withoutHidden(fresh)
                    // Newly appended entries may fall inside the prefetch
                    // window of the Short being watched right now
                    prefetchAround(_currentIndex.value)
                }
                // A page of only duplicates still advances the continuation,
                // so the next trigger asks for genuinely new entries
                nextSequenceParams = page.continuation
            } finally {
                isLoadingMore = false
            }
        }
    }

    // ---------------- Playback helpers (same conventions as the video player) ----------------

    /**
     * Starting quality from the per-network video quality setting (fresh pref
     * read), like VideoPlayerViewModel.pickDefaultQuality. The list is sorted
     * highest-first, so the first label at or below the target height wins.
     */
    private fun pickDefaultQuality(qualities: List<VideoQuality>): VideoQuality {
        fun height(label: String): Int = label.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        val preferred = themePreferences.getDefaultVideoQuality()
        if (preferred == ThemePreferences.VIDEO_QUALITY_AUTO) {
            return qualities.firstOrNull { height(it.resolution) > 0 } ?: qualities.first()
        }
        val targetHeight = height(preferred)
        return qualities.firstOrNull { height(it.resolution) in 1..targetHeight }
            ?: qualities.lastOrNull { height(it.resolution) > 0 }
            ?: qualities.first()
    }

    /**
     * @param startAtMs where to resume. Non-zero only on the recovery path,
     * which reloads a Short that died partway through and should not restart it
     * from the top.
     */
    private fun loadQuality(quality: VideoQuality, startAtMs: Long = 0L) {
        currentQuality = quality
        // Adaptive manifests carry no progressive URL to wrap: hand the
        // MediaItem to the player and let its MediaSource factory build the
        // DASH/HLS source. Feeding a manifest to ProgressiveMediaSource (what
        // this used to do for every quality) fails extraction outright.
        if (quality.isDASH) {
            _exoPlayer?.setMediaItem(
                MediaItem.Builder()
                    .setUri(quality.url)
                    .setMimeType(adaptiveMimeType(quality))
                    .build(),
                startAtMs
            )
            _exoPlayer?.prepare()
            return
        }

        val dataSourceFactory = streamDataSourceFactory
        val audioUrl = quality.audioUrl
        if (audioUrl != null) {
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(quality.url))
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioUrl))
            _exoPlayer?.setMediaSource(MergingMediaSource(true, videoSource, audioSource), startAtMs)
        } else {
            val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(quality.url))
            _exoPlayer?.setMediaSource(source, startAtMs)
        }
        _exoPlayer?.prepare()
    }

    /**
     * MIME for an adaptive quality. Both DASH and HLS entries arrive with
     * isDASH set and are told apart only by [VideoQuality.format], so pinning
     * MPD unconditionally would make the factory build a DashMediaSource for an
     * m3u8 playlist and fail the load.
     */
    private fun adaptiveMimeType(quality: VideoQuality): String =
        if (quality.format.equals("HLS", ignoreCase = true)) {
            androidx.media3.common.MimeTypes.APPLICATION_M3U8
        } else {
            androidx.media3.common.MimeTypes.APPLICATION_MPD
        }

    // ---------------- Engagement actions (optimistic with rollback) ----------------

    /** Re-check login state and refresh engagement (call after a sign-in). */
    fun onLoginStateChanged() {
        _isLoggedIn.value = youtubeRepository.isLoggedIn()
        val video = _currentVideo.value ?: return
        viewModelScope.launch {
            _engagement.value = youtubeRepository.getVideoEngagement(video.videoId)
        }
    }

    fun toggleLike() = rate { current ->
        if (current == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE
    }

    fun toggleDislike() = rate { current ->
        if (current == LikeStatus.DISLIKE) LikeStatus.INDIFFERENT else LikeStatus.DISLIKE
    }

    private fun rate(target: (LikeStatus) -> LikeStatus) {
        val current = _engagement.value ?: return
        val newStatus = target(current.likeStatus)
        _engagement.value = current.copy(likeStatus = newStatus)
        viewModelScope.launch {
            val ok = youtubeRepository.rateVideo(current.videoId, newStatus)
            if (!ok && _engagement.value?.videoId == current.videoId) {
                _engagement.value = _engagement.value?.copy(likeStatus = current.likeStatus)
            }
        }
    }

    /**
     * Subscribe/unsubscribe to the current Short's channel, routed by the
     * subscribe-target setting. Same contract as the video player's.
     */
    fun toggleSubscribe() {
        val current = _engagement.value ?: return
        val channelId = current.channelId ?: return
        val video = _currentVideo.value
        val subscribe = !isSubscribedToChannel.value

        val writesRemote =
            subscriptionActions.resolveTarget() != com.ivor.ivormusic.data.SubscriptionStore.LOCAL
        _engagement.value = current.copy(
            isSubscribed = when {
                !subscribe -> false
                writesRemote -> true
                else -> current.isSubscribed
            }
        )
        viewModelScope.launch {
            val ok = subscriptionActions.setSubscribed(
                channel = com.ivor.ivormusic.data.LocalSubscription(
                    channelId = channelId,
                    name = video?.channelName?.takeIf { it.isNotBlank() } ?: channelId,
                    avatarUrl = video?.channelIconUrl
                ),
                subscribe = subscribe,
                remotelySubscribed = current.isSubscribed
            )
            if (!ok && _engagement.value?.videoId == current.videoId) {
                _engagement.value = _engagement.value?.copy(isSubscribed = current.isSubscribed)
            }
        }
    }

    // ---------------- Comments ----------------

    private fun resetEngagementState() {
        _engagement.value = null
        _comments.value = emptyList()
        _replies.value = emptyMap()
        _loadingReplyIds.value = emptySet()
        commentsNextToken = null
        commentsLoadedForVideoId = null
        _createCommentParams.value = null
        _isLoggedIn.value = youtubeRepository.isLoggedIn()
    }

    fun ensureCommentsLoaded() {
        val video = _currentVideo.value ?: return
        if (commentsLoadedForVideoId == video.videoId || _isCommentsLoading.value) return
        val token = _engagement.value?.commentsToken ?: return
        _isCommentsLoading.value = true
        viewModelScope.launch {
            try {
                val page = youtubeRepository.getCommentsPage(token)
                if (_currentVideo.value?.videoId == video.videoId && page != null) {
                    _comments.value = page.comments
                    commentsNextToken = page.nextPageToken
                    commentsLoadedForVideoId = video.videoId
                    _createCommentParams.value = page.createCommentParams
                }
            } finally {
                _isCommentsLoading.value = false
            }
        }
    }

    fun loadMoreComments() {
        val video = _currentVideo.value ?: return
        val token = commentsNextToken ?: return
        if (_isLoadingMoreComments.value || _isCommentsLoading.value) return
        _isLoadingMoreComments.value = true
        viewModelScope.launch {
            try {
                val page = youtubeRepository.getCommentsPage(token)
                if (_currentVideo.value?.videoId == video.videoId && page != null) {
                    val known = _comments.value.mapTo(HashSet()) { it.commentId }
                    _comments.value = _comments.value + page.comments.filter { it.commentId !in known }
                    commentsNextToken = page.nextPageToken
                }
            } finally {
                _isLoadingMoreComments.value = false
            }
        }
    }

    fun loadReplies(comment: CommentItem) {
        val video = _currentVideo.value ?: return
        val token = comment.repliesToken ?: return
        if (_replies.value.containsKey(comment.commentId) ||
            comment.commentId in _loadingReplyIds.value
        ) return
        _loadingReplyIds.value = _loadingReplyIds.value + comment.commentId
        viewModelScope.launch {
            try {
                val page = youtubeRepository.getCommentsPage(token)
                if (_currentVideo.value?.videoId == video.videoId && page != null) {
                    _replies.value = _replies.value + (comment.commentId to page.comments)
                }
            } finally {
                _loadingReplyIds.value = _loadingReplyIds.value - comment.commentId
            }
        }
    }

    fun postComment(text: String) {
        val video = _currentVideo.value ?: return
        val params = _createCommentParams.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isPostingComment.value) return
        _isPostingComment.value = true
        viewModelScope.launch {
            try {
                val created = youtubeRepository.createComment(params, trimmed)
                if (created != null && _currentVideo.value?.videoId == video.videoId) {
                    _comments.value = listOf(created) + _comments.value
                }
            } finally {
                _isPostingComment.value = false
            }
        }
    }

    fun postReply(target: CommentItem, threadParent: CommentItem, text: String) {
        val video = _currentVideo.value ?: return
        val params = target.replyParams ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isPostingComment.value) return
        _isPostingComment.value = true
        viewModelScope.launch {
            try {
                val created = youtubeRepository.createCommentReply(params, trimmed)
                if (created != null && _currentVideo.value?.videoId == video.videoId) {
                    val existing = _replies.value[threadParent.commentId]
                    if (existing != null || threadParent.repliesToken == null) {
                        _replies.value = _replies.value +
                            (threadParent.commentId to (existing ?: emptyList()) + created)
                    } else {
                        loadReplies(threadParent)
                    }
                }
            } finally {
                _isPostingComment.value = false
            }
        }
    }

    fun toggleCommentLike(comment: CommentItem) {
        val action = (if (comment.isLiked) comment.unlikeParams else comment.likeParams) ?: return
        val toggled = comment.copy(isLiked = !comment.isLiked)
        replaceComment(toggled)
        viewModelScope.launch {
            if (!youtubeRepository.performCommentAction(action)) {
                replaceComment(comment)
            }
        }
    }

    fun deleteComment(comment: CommentItem) {
        val action = comment.deleteParams ?: return
        val previousComments = _comments.value
        val previousReplies = _replies.value
        _comments.value = _comments.value.filterNot { it.commentId == comment.commentId }
        _replies.value = _replies.value
            .mapValues { (_, list) -> list.filterNot { it.commentId == comment.commentId } }
            .filterKeys { it != comment.commentId }
        viewModelScope.launch {
            if (!youtubeRepository.performCommentAction(action)) {
                _comments.value = previousComments
                _replies.value = previousReplies
            }
        }
    }

    private fun replaceComment(updated: CommentItem) {
        _comments.value = _comments.value.map {
            if (it.commentId == updated.commentId) updated else it
        }
        _replies.value = _replies.value.mapValues { (_, list) ->
            list.map { if (it.commentId == updated.commentId) updated else it }
        }
    }

    companion object {
        private const val SHORTS_CACHE_OWNER = "shorts"
        /** Stream URLs resolved ahead of the current Short (plus one behind). */
        private const val STREAM_PREFETCH_AHEAD = 2

        /** Watch-next payloads (metadata + engagement) warmed ahead. */
        private const val WATCH_NEXT_PREFETCH_AHEAD = 1

        private const val SHORTS_METERED_WARM_BYTES = 512L * 1024
        private const val SHORTS_UNMETERED_WARM_BYTES = 2L * 1024 * 1024
        private const val SHORTS_AUDIO_WARM_BYTES = 256L * 1024

        /** Silent re-prepare attempts before a renderer error reaches the UI. */
        private const val MAX_RENDERER_RETRIES = 2

        /**
         * Silent re-resolve attempts before a source error reaches the UI. One
         * is enough: the first pass already remints a rejected visitorData and
         * fetches brand-new URLs, so a second failure means the Short really is
         * unplayable and the user should get the retry button, not a spinner.
         */
        private const val MAX_SOURCE_RETRIES = 1
    }

    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
        com.ivor.ivormusic.data.CacheManager.setVideoPlaybackActive(SHORTS_CACHE_OWNER, false)
    }
}
