package com.ivor.ivormusic.ui.video

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
import com.ivor.ivormusic.data.TimedComment
import com.ivor.ivormusic.data.VideoEngagement
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@UnstableApi
class VideoPlayerViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val youtubeRepository = YouTubeRepository(context)
    private val themePreferences = ThemePreferences(context)
    private val videoHistoryRepository = com.ivor.ivormusic.data.VideoHistoryRepository(context)

    // Player Instance
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    // State
    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering
    
    // Qualities and Related
    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities

    private val _currentQuality = MutableStateFlow<VideoQuality?>(null)
    val currentQuality: StateFlow<VideoQuality?> = _currentQuality

    private val _relatedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val relatedVideos: StateFlow<List<VideoItem>> = _relatedVideos

    private val _isAutoPlayEnabled = MutableStateFlow(false)
    val isAutoPlayEnabled: StateFlow<Boolean> = _isAutoPlayEnabled.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var playbackReportJob: kotlinx.coroutines.Job? = null

    // Track quality change listener to prevent leaks
    private var qualityChangeListener: Player.Listener? = null

    // Error state
    private val _playbackError = MutableStateFlow<Throwable?>(null)
    val playbackError: StateFlow<Throwable?> = _playbackError.asStateFlow()

    // ---------------- Engagement (likes / subscribe / comments) ----------------

    private val _engagement = MutableStateFlow<VideoEngagement?>(null)
    val engagement: StateFlow<VideoEngagement?> = _engagement.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(youtubeRepository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _comments = MutableStateFlow<List<CommentItem>>(emptyList())
    val comments: StateFlow<List<CommentItem>> = _comments.asStateFlow()

    // Comments that mention a playback timestamp, sorted by that time.
    // Drives the timed comments overlay in the video player.
    val timedComments: StateFlow<List<TimedComment>> = _comments
        .map { list ->
            list.mapNotNull { comment ->
                parseFirstTimestampMs(comment.text)?.let { TimedComment(comment, it) }
            }.sortedBy { it.timeMs }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoading: StateFlow<Boolean> = _isCommentsLoading.asStateFlow()

    private val _isLoadingMoreComments = MutableStateFlow(false)
    val isLoadingMoreComments: StateFlow<Boolean> = _isLoadingMoreComments.asStateFlow()

    // Replies keyed by parent commentId; presence of key = replies loaded (or loading)
    private val _replies = MutableStateFlow<Map<String, List<CommentItem>>>(emptyMap())
    val replies: StateFlow<Map<String, List<CommentItem>>> = _replies.asStateFlow()

    private val _loadingReplyIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingReplyIds: StateFlow<Set<String>> = _loadingReplyIds.asStateFlow()

    private var commentsNextToken: String? = null
    private var commentsLoadedForVideoId: String? = null

    // Params for posting a new top-level comment; arrives with the first comments page
    private val _createCommentParams = MutableStateFlow<String?>(null)

    /** Whether posting a comment is possible (logged in + params available). */
    val canComment: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        _isLoggedIn, _createCommentParams
    ) { loggedIn, params -> loggedIn && params != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isPostingComment = MutableStateFlow(false)
    val isPostingComment: StateFlow<Boolean> = _isPostingComment.asStateFlow()

    init {
        // Faster first frame than the stock 2.5s start buffer: begin playback
        // after ~1.5s buffered and keep a large max buffer for stability.
        // Mirrors the tuned LoadControl the music service already uses.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // min buffer
                60_000, // max buffer
                1_500,  // buffer before first frame
                3_000   // buffer after a rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Initialize ExoPlayer.
        // WAKE_MODE_NETWORK holds CPU + WiFi locks while playing: without it,
        // locking the screen lets the device sleep, the network stalls, the
        // buffer drains and playback dies in a stuck-buffering state.
        // Audio focus + becoming-noisy mirror MusicService, so video playback
        // pauses the music player (and vice versa) instead of playing over it.
        _exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_ENDED) {
                        if (_isAutoPlayEnabled.value) {
                            val nextVideo = _relatedVideos.value.firstOrNull()
                            // Guard: ensure ViewModel/player is still valid before launching
                            if (nextVideo != null && _exoPlayer != null) {
                                viewModelScope.launch { playVideo(nextVideo) }
                            }
                        }
                    }
                }
            })
        }

        // Warm the visitorData cache so the first playback doesn't pay for
        // the youtube.com bootstrap download on its critical path.
        viewModelScope.launch { youtubeRepository.prefetchVisitorData() }
    }

    fun toggleAutoPlay() {
        _isAutoPlayEnabled.value = !_isAutoPlayEnabled.value
    }

    fun toggleLooping() {
        _isLooping.value = !_isLooping.value
        _exoPlayer?.repeatMode = if (_isLooping.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Set the playback speed for the current video. Resets to 1x on video change. */
    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        _exoPlayer?.setPlaybackSpeed(speed)
    }

    fun playVideo(video: VideoItem) {
        if (_currentVideo.value?.videoId == video.videoId) {
            // Already playing this video, just expand
            _isExpanded.value = true
            return
        }
        
        _currentVideo.value = video
        _isExpanded.value = true
        _isLoading.value = true
        _relatedVideos.value = emptyList() // Clear previous related
        _playbackError.value = null // Clear previous error

        // Reset engagement + comments state for the new video
        _engagement.value = null
        _comments.value = emptyList()
        _replies.value = emptyMap()
        _loadingReplyIds.value = emptySet()
        commentsNextToken = null
        commentsLoadedForVideoId = null
        _createCommentParams.value = null
        _isLoggedIn.value = youtubeRepository.isLoggedIn()

        // Speed is per-video, like YouTube
        _playbackSpeed.value = 1f
        _exoPlayer?.setPlaybackSpeed(1f)

        // ========== PHASE 1: START PLAYBACK ASAP (fast) ==========
        // Uses lightweight getVideoStreamQualities() which ONLY fetches stream URLs
        viewModelScope.launch {
            try {
                _exoPlayer?.stop()
                _exoPlayer?.clearMediaItems()
                
                // Add timeout for stream fetching to prevent "stuck in buffering"
                kotlinx.coroutines.withTimeout(15000L) {
                    // FAST: Get stream URLs only (no metadata, no related, no channel avatar)
                    val qualities = youtubeRepository.getVideoStreamQualities(video.videoId)
                    _availableQualities.value = qualities
                    
                    if (qualities.isNotEmpty()) {
                        loadQuality(pickDefaultQuality(qualities))
                        // FORCE PLAY: Ensure we override any previous paused state
                        _exoPlayer?.play() 
                        _isLoading.value = false // ✅ Playback starting NOW!
                    } else {
                        // Fallback to legacy stream URL
                        val streamUrl = youtubeRepository.getVideoStreamUrl(video.videoId)
                        if (streamUrl != null) {
                            _currentQuality.value = VideoQuality(
                                resolution = "Auto",
                                url = streamUrl,
                                isDASH = false,
                                audioUrl = null
                            )
                            val source = ProgressiveMediaSource.Factory(dataSourceFactoryFor(streamUrl))
                                .createMediaSource(MediaItem.fromUri(streamUrl))
                            _exoPlayer?.setMediaSource(source)
                            _exoPlayer?.prepare()
                            _exoPlayer?.play() // FORCE PLAY
                            _isLoading.value = false
                        } else {
                            _playbackError.value = Exception("Unable to load video stream")
                            _isLoading.value = false
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _playbackError.value = Exception("Connection timed out. Please check your internet.")
                _isLoading.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                _playbackError.value = e
                _isLoading.value = false
            }
        }
        
        // ========== PHASE 2: ENGAGEMENT + METADATA + RELATED ==========
        // One watch-next call answers all three (the old code paid for an
        // engagement /next call AND a full NewPipe extraction here, competing
        // with Phase 1's initial buffering for bandwidth).
        viewModelScope.launch {
            try {
                val watchNext = youtubeRepository.getWatchNextData(video.videoId, video)
                // Guard against a video switch that happened mid-flight
                if (_currentVideo.value?.videoId != video.videoId) return@launch
                _engagement.value = watchNext.engagement
                if (watchNext.updatedVideoItem != null) {
                    _currentVideo.value = watchNext.updatedVideoItem
                }
                if (watchNext.relatedVideos.isNotEmpty()) {
                    _relatedVideos.value = watchNext.relatedVideos
                }
            } catch (e: Exception) {
                // Phase 2 errors are non-critical - playback already started
                android.util.Log.w("VideoPlayerVM", "Failed to load watch-next data", e)
            }
        }
        
        // Report Playback (cancel previous if user switched videos)
        // Only report if saveVideoHistory setting is enabled
        playbackReportJob?.cancel()
        playbackReportJob = viewModelScope.launch {
            kotlinx.coroutines.delay(10000)
            // A stream still buffering (or briefly paused) at the 10s mark must
            // not lose its report — wait up to a minute for playback to run.
            var waitedMs = 0
            while (!_isPlaying.value && waitedMs < 60_000) {
                kotlinx.coroutines.delay(1000)
                waitedMs += 1000
            }
            // Fresh pref read: the settings screen toggles through its own
            // ThemePreferences instance, so this VM's StateFlow copy is stale.
            if (_isPlaying.value && themePreferences.isSaveVideoHistoryEnabled()) {
                // Local history: works without login and feeds recommendations.
                // Use the current video state — Phase 2 may have enriched it.
                val watched = _currentVideo.value?.takeIf { it.videoId == video.videoId } ?: video
                videoHistoryRepository.addVideo(watched)
                // WEB client flow: the music (WEB_REMIX) reporter does not
                // register plain videos in YouTube watch history
                youtubeRepository.reportVideoPlayback(video.videoId)
            }
        }
    }

    /**
     * Data source factory whose User-Agent matches the client that issued the
     * stream URL. YouTube binds googlevideo URLs to the issuing client via the
     * `?c=` param and answers 403 when the playback UA does not match.
     */
    private fun dataSourceFactoryFor(url: String): DefaultDataSource.Factory {
        val userAgent = YouTubeRepository.uaForPlaybackUri(android.net.Uri.parse(url))
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
        return DefaultDataSource.Factory(context, httpFactory)
    }

    /**
     * Pick the starting quality based on the Settings preference. Fresh pref
     * read because Settings toggles through its own ThemePreferences instance.
     * The list from getVideoStreamQualities() is sorted highest-first with
     * 60fps variants before 30fps, so the first label at or below the target
     * height is the best match; if the video has nothing at or below it, take
     * the lowest available.
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
        _currentQuality.value = quality
        val mediaItemBuilder = MediaItem.Builder().setUri(quality.url)

        if (quality.isDASH) {
            // DASH streams are adaptive - use directly without merging
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            _exoPlayer?.setMediaItem(mediaItemBuilder.build())
        } else {
            val dataSourceFactory = dataSourceFactoryFor(quality.url)
            val audioUrl = quality.audioUrl
            if (audioUrl != null) {
                // Non-DASH with separate audio - use MergingMediaSource
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(quality.url))
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl))

                // adjustPeriodTimeOffsets aligns the two tracks' start offsets,
                // preventing A/V desync when they don't begin at the same time.
                val mergingSource = MergingMediaSource(true, videoSource, audioSource)
                _exoPlayer?.setMediaSource(mergingSource)
            } else {
                val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItemBuilder.build())
                _exoPlayer?.setMediaSource(source)
            }
        }
        _exoPlayer?.prepare()
    }
    
    fun setQuality(quality: VideoQuality) {
        val player = _exoPlayer ?: return
        val position = player.currentPosition
        
        // Remove any existing quality change listener to prevent leaks
        qualityChangeListener?.let { player.removeListener(it) }
        
        loadQuality(quality)
        
        // Wait for player to be ready before seeking to preserved position
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    player.seekTo(position)
                    player.removeListener(this)
                    qualityChangeListener = null
                }
            }
        }
        qualityChangeListener = listener
        player.addListener(listener)
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    fun closePlayer() {
        // Remove quality change listener to prevent leaks if player closed before STATE_READY
        qualityChangeListener?.let { _exoPlayer?.removeListener(it) }
        qualityChangeListener = null
        _exoPlayer?.stop()
        _currentVideo.value = null
        _isExpanded.value = false
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            _exoPlayer?.pause()
        } else {
            _exoPlayer?.play()
        }
    }

    // ---------------- Engagement actions ----------------

    /** Re-check login state and refresh engagement (call after a successful sign-in). */
    fun onLoginStateChanged() {
        _isLoggedIn.value = youtubeRepository.isLoggedIn()
        val video = _currentVideo.value ?: return
        viewModelScope.launch {
            _engagement.value = youtubeRepository.getVideoEngagement(video.videoId)
        }
    }

    /** Like the video, or remove the like if already liked. Optimistic with rollback. */
    fun toggleLike() = rate(
        target = { current -> if (current == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE }
    )

    /** Dislike the video, or remove the dislike if already disliked. */
    fun toggleDislike() = rate(
        target = { current -> if (current == LikeStatus.DISLIKE) LikeStatus.INDIFFERENT else LikeStatus.DISLIKE }
    )

    private fun rate(target: (LikeStatus) -> LikeStatus) {
        val current = _engagement.value ?: return
        val newStatus = target(current.likeStatus)
        _engagement.value = current.copy(likeStatus = newStatus)
        viewModelScope.launch {
            val ok = youtubeRepository.rateVideo(current.videoId, newStatus)
            if (!ok) {
                // Roll back on failure (only if still on the same video)
                if (_engagement.value?.videoId == current.videoId) {
                    _engagement.value = _engagement.value?.copy(likeStatus = current.likeStatus)
                }
            }
        }
    }

    /** Subscribe/unsubscribe to the current video's channel. Optimistic with rollback. */
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

    /**
     * Extract the first playback timestamp mentioned in a comment ("1:23",
     * "12:05" or "1:02:33") as milliseconds, or null when none is present.
     */
    private fun parseFirstTimestampMs(text: String): Long? {
        val match = TIMESTAMP_REGEX.find(text) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        if (seconds >= 60) return null
        if (match.groupValues[1].isNotEmpty() && minutes >= 60) return null
        return ((hours * 60 + minutes) * 60 + seconds) * 1000L
    }

    /** Load the first page of comments if not already loaded for this video. */
    fun ensureCommentsLoaded() {
        val video = _currentVideo.value ?: return
        if (commentsLoadedForVideoId == video.videoId || _isCommentsLoading.value) return
        val token = _engagement.value?.commentsToken ?: return
        _isCommentsLoading.value = true
        viewModelScope.launch {
            try {
                val page = youtubeRepository.getCommentsPage(token)
                // Ignore stale results if the user switched videos meanwhile
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

    /** Load the next page of comments (no-op when exhausted or already loading). */
    fun loadMoreComments() {
        val video = _currentVideo.value ?: return
        val token = commentsNextToken ?: return
        if (_isLoadingMoreComments.value || _isCommentsLoading.value) return
        _isLoadingMoreComments.value = true
        viewModelScope.launch {
            try {
                val page = youtubeRepository.getCommentsPage(token)
                if (_currentVideo.value?.videoId == video.videoId && page != null) {
                    // Guard against duplicates if YouTube repeats items across pages
                    val known = _comments.value.mapTo(HashSet()) { it.commentId }
                    _comments.value = _comments.value + page.comments.filter { it.commentId !in known }
                    commentsNextToken = page.nextPageToken
                }
            } finally {
                _isLoadingMoreComments.value = false
            }
        }
    }

    /** Load replies for a comment (first page only; collapses are handled in UI). */
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

    /**
     * Post a new top-level comment on the current video. The created comment
     * is prepended to the list on success. Requires login.
     */
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

    /**
     * Post a reply into a comment thread. `target` is the comment being
     * replied to (a top-level comment or one of its replies — replying to a
     * reply posts into the same thread, like YouTube); `threadParent` is the
     * top-level comment whose replies list shows the result. Requires login.
     */
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
                        // Replies already visible (or none exist yet): append inline
                        _replies.value = _replies.value +
                            (threadParent.commentId to (existing ?: emptyList()) + created)
                    } else {
                        // Replies exist but are collapsed: fetch the thread so the
                        // older replies are not hidden behind the local insert
                        loadReplies(threadParent)
                    }
                }
            } finally {
                _isPostingComment.value = false
            }
        }
    }

    /**
     * Like or unlike a comment (top-level or reply). Optimistically flips the
     * local state, then reverts if the InnerTube action fails. Requires login
     * and the action params that only arrive on signed-in comment fetches.
     */
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

    /**
     * Delete one of the user's own comments (top-level or reply). The delete
     * param only exists on own comments (CommentItem.deleteParams). Optimistic
     * removal with restore on failure.
     */
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

    /** Swap a comment (matched by id) in the top-level list and all reply threads. */
    private fun replaceComment(updated: CommentItem) {
        _comments.value = _comments.value.map {
            if (it.commentId == updated.commentId) updated else it
        }
        _replies.value = _replies.value.mapValues { (_, list) ->
            list.map { if (it.commentId == updated.commentId) updated else it }
        }
    }

    companion object {
        private val TIMESTAMP_REGEX = Regex("""(?<!\d)(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?!\d)""")

        /** Speeds offered in the player's speed menu. */
        val PLAYBACK_SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }

    override fun onCleared() {
        super.onCleared()
        // Remove quality change listener to prevent leaks
        qualityChangeListener?.let { _exoPlayer?.removeListener(it) }
        qualityChangeListener = null
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
