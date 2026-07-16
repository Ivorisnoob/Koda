package com.ivor.ivormusic.ui.shorts

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _shorts = MutableStateFlow<List<ShortsItem>>(emptyList())
    val shorts: StateFlow<List<ShortsItem>> = _shorts.asStateFlow()

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

    /** Metadata of the current Short, enriched by watch-next (title, channel, avatar). */
    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo.asStateFlow()

    // Sequence continuation for the endless feed; null until known/exhausted
    private var nextSequenceParams: String? = null
    private var isLoadingMore = false
    private var playJob: Job? = null
    private var historyReportJob: Job? = null

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

    private fun cachedQualities(videoId: String): List<VideoQuality>? =
        synchronized(qualitiesCache) { qualitiesCache[videoId] }

    private fun cacheQualities(videoId: String, qualities: List<VideoQuality>) {
        if (qualities.isEmpty()) return
        synchronized(qualitiesCache) { qualitiesCache[videoId] = qualities }
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
        val list = _shorts.value
        val streamTargets =
            ((index + 1)..(index + STREAM_PREFETCH_AHEAD)).mapNotNull { list.getOrNull(it) } +
                listOfNotNull(list.getOrNull(index - 1))
        for (item in streamTargets) {
            val id = item.videoId
            if (cachedQualities(id) != null || !prefetchingIds.add("s:$id")) continue
            viewModelScope.launch {
                try {
                    prefetchSemaphore.acquire()
                    try {
                        // Skip if a later prefetch/playback already filled it
                        if (cachedQualities(id) == null) {
                            cacheQualities(id, youtubeRepository.getVideoStreamQualities(id))
                        }
                    } finally {
                        prefetchSemaphore.release()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ShortsPlayerVM", "stream prefetch failed for $id", e)
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
                    android.util.Log.w("ShortsPlayerVM", "watch-next prefetch failed for $id", e)
                } finally {
                    prefetchingIds.remove("w:$id")
                }
            }
        }
    }

    // ---------------- Engagement ----------------

    private val _engagement = MutableStateFlow<VideoEngagement?>(null)
    val engagement: StateFlow<VideoEngagement?> = _engagement.asStateFlow()

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

    init {
        // First frame after ~1s buffered, like the video player. Shorts are
        // under a minute, so the 60s max buffer already covers the whole clip
        // — no need for the long-form 5-minute read-ahead. Audio focus +
        // becoming-noisy pause music/video playback instead of playing over
        // them (and vice versa).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 60_000, 1_000, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        _exoPlayer = ExoPlayer.Builder(context)
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
                })
            }
    }

    /**
     * Open the Shorts player on a shelf: [items] become the initial pager
     * feed, [startIndex] is the tapped Short. The tapped item's
     * sequenceParams seed the endless feed once the user swipes near the end.
     */
    fun open(items: List<ShortsItem>, startIndex: Int) {
        if (items.isEmpty()) return
        val index = startIndex.coerceIn(0, items.size - 1)
        _shorts.value = items
        _currentIndex.value = index
        nextSequenceParams = items[index].sequenceParams
            ?: items.firstNotNullOfOrNull { it.sequenceParams }
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

    fun close() {
        _isActive.value = false
        playJob?.cancel()
        historyReportJob?.cancel()
        _exoPlayer?.stop()
        _exoPlayer?.clearMediaItems()
        _currentVideo.value = null
        _playbackError.value = null
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
        playIndex(_currentIndex.value)
    }

    private fun playIndex(index: Int) {
        val item = _shorts.value.getOrNull(index) ?: return
        playJob?.cancel()
        historyReportJob?.cancel()

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
                        ?: youtubeRepository.getVideoStreamQualities(item.videoId)
                            .also { cacheQualities(item.videoId, it) }
                    if (_currentIndex.value != index) return@withTimeout
                    if (qualities.isNotEmpty()) {
                        loadQuality(pickDefaultQuality(qualities))
                        _exoPlayer?.play()
                    } else {
                        val streamUrl = youtubeRepository.getVideoStreamUrl(item.videoId)
                        if (streamUrl != null) {
                            val source = ProgressiveMediaSource.Factory(dataSourceFactoryFor(streamUrl))
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
                android.util.Log.w("ShortsPlayerVM", "watch-next failed for ${item.videoId}", e)
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
                    _shorts.value = _shorts.value + fresh
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
     * Data source factory whose User-Agent matches the client that issued the
     * stream URL — googlevideo answers 403 on a UA mismatch.
     */
    private fun dataSourceFactoryFor(url: String): DefaultDataSource.Factory {
        val userAgent = YouTubeRepository.uaForPlaybackUri(android.net.Uri.parse(url))
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
        return DefaultDataSource.Factory(context, httpFactory)
    }

    /**
     * Starting quality from the Default Video Quality setting (fresh pref
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

    private fun loadQuality(quality: VideoQuality) {
        val dataSourceFactory = dataSourceFactoryFor(quality.url)
        val audioUrl = quality.audioUrl
        if (audioUrl != null) {
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(quality.url))
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioUrl))
            _exoPlayer?.setMediaSource(MergingMediaSource(true, videoSource, audioSource))
        } else {
            val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(quality.url))
            _exoPlayer?.setMediaSource(source)
        }
        _exoPlayer?.prepare()
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

    fun toggleSubscribe() {
        val current = _engagement.value ?: return
        val channelId = current.channelId ?: return
        val subscribe = !current.isSubscribed
        _engagement.value = current.copy(isSubscribed = subscribe)
        viewModelScope.launch {
            val ok = youtubeRepository.setSubscribed(channelId, subscribe)
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
        /** Stream URLs resolved ahead of the current Short (plus one behind). */
        private const val STREAM_PREFETCH_AHEAD = 5

        /** Watch-next payloads (metadata + engagement) warmed ahead. */
        private const val WATCH_NEXT_PREFETCH_AHEAD = 2
    }

    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
