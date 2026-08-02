package com.ivor.ivormusic.ui.video

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ivor.ivormusic.data.CaptionTrack
import com.ivor.ivormusic.data.CommentItem
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.TimedComment
import com.ivor.ivormusic.data.VideoEngagement
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.VttCue
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.ThemePreferences
import kotlinx.coroutines.Job
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

    // Chapter markers for the current video (empty when the video has none)
    private val _chapters = MutableStateFlow<List<com.ivor.ivormusic.data.VideoChapter>>(emptyList())
    val chapters: StateFlow<List<com.ivor.ivormusic.data.VideoChapter>> = _chapters.asStateFlow()

    // Caption/subtitle tracks (loaded lazily when the user opens the CC menu)
    private val _captionTracks = MutableStateFlow<List<CaptionTrack>>(emptyList())
    val captionTracks: StateFlow<List<CaptionTrack>> = _captionTracks.asStateFlow()

    // Currently displayed caption track, or null when captions are off
    private val _selectedCaption = MutableStateFlow<CaptionTrack?>(null)
    val selectedCaption: StateFlow<CaptionTrack?> = _selectedCaption.asStateFlow()

    private val _isCaptionsLoading = MutableStateFlow(false)
    val isCaptionsLoading: StateFlow<Boolean> = _isCaptionsLoading.asStateFlow()

    private var captionsLoadedForVideoId: String? = null

    // Cues of the selected track, rendered by the player overlay. Captions are
    // deliberately kept out of the media source - see [setCaptionTrack].
    private val _captionCues = MutableStateFlow<List<VttCue>>(emptyList())
    val captionCues: StateFlow<List<VttCue>> = _captionCues.asStateFlow()

    private var captionCuesJob: Job? = null

    // Repeat sticks across videos and app restarts, so seed it from prefs
    // rather than defaulting to off on every player creation.
    private val _isLooping = MutableStateFlow(themePreferences.isVideoRepeatEnabled())
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var playbackReportJob: kotlinx.coroutines.Job? = null

    // Track quality change listener to prevent leaks
    private var qualityChangeListener: Player.Listener? = null

    // Error state
    private val _playbackError = MutableStateFlow<Throwable?>(null)
    val playbackError: StateFlow<Throwable?> = _playbackError.asStateFlow()

    // Video rendering is suspended while the app is not visible - see onEnterBackground()
    private var isVideoSuspended = false

    // Renderer/decoder failures are usually transient (surface torn down, codec
    // reclaimed by another app). Retry in place a couple of times before the
    // error reaches the UI; reset on every successful playback start.
    private var rendererRetryCount = 0

    // Source failures (googlevideo 403 on a flagged token, an expired URL, a
    // network blip) are recovered by re-resolving the stream rather than by
    // replaying the dead URL. Bounded so a genuinely unplayable video still
    // reaches the error overlay; reset on every successful playback start.
    private var sourceRetryCount = 0
    private var sourceRecoveryJob: kotlinx.coroutines.Job? = null

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

    // ---------------- Save to playlist (Watch Later) ----------------

    private val _videoPlaylists = MutableStateFlow<List<com.ivor.ivormusic.data.VideoPlaylist>>(emptyList())
    val videoPlaylists: StateFlow<List<com.ivor.ivormusic.data.VideoPlaylist>> = _videoPlaylists.asStateFlow()

    private val _isVideoPlaylistsLoading = MutableStateFlow(false)
    val isVideoPlaylistsLoading: StateFlow<Boolean> = _isVideoPlaylistsLoading.asStateFlow()

    // ---------------- Channel page (latest uploads) ----------------

    private val _channelVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val channelVideos: StateFlow<List<VideoItem>> = _channelVideos.asStateFlow()

    private val _isChannelVideosLoading = MutableStateFlow(false)
    val isChannelVideosLoading: StateFlow<Boolean> = _isChannelVideosLoading.asStateFlow()

    private var channelVideosLoadedForChannelId: String? = null

    /**
     * Stream data source factory: ChunkedStreamDataSource fetches googlevideo
     * media in bounded ranged chunks (open-ended requests are server-paced to
     * the media bitrate) and picks the per-request User-Agent matching the
     * URL's issuing client (`?c=` param; a UA mismatch means a 403).
     *
     * Declared before [init] on purpose: the ExoPlayer built there installs it
     * as the player-wide MediaSource factory, so a lazy declared further down
     * the class would still be an uninitialised delegate at that point.
     */
    private val streamDataSourceFactory =
        DefaultDataSource.Factory(context, com.ivor.ivormusic.data.ChunkedStreamDataSource.Factory())

    init {
        // Near-instant first frame (~1s buffered) plus an aggressive
        // read-ahead: up to 5 minutes (min == max: continuous top-up),
        // hard-capped at 200MB of sample RAM so high-bitrate 4K streams
        // can't exhaust memory — loading stops at whichever limit is hit
        // first. Video has no disk cache, so a 30s keyframe-aligned back
        // buffer keeps short rewinds instant too.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                300_000, // min buffer (== max: continuous top-up)
                300_000, // max buffer: up to 5 minutes ahead
                1_000,   // buffer before first frame
                2_500    // buffer after a rebuffer
            )
            .setTargetBufferBytes(200 * 1024 * 1024)
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        // Initialize ExoPlayer.
        // WAKE_MODE_NETWORK holds CPU + WiFi locks while playing: without it,
        // locking the screen lets the device sleep, the network stalls, the
        // buffer drains and playback dies in a stuck-buffering state.
        // Audio focus + becoming-noisy mirror MusicService, so video playback
        // pauses the music player (and vice versa) instead of playing over it.
        // Every media source this player builds must fetch through
        // ChunkedStreamDataSource. loadQuality() passes it explicitly for the
        // progressive/merged paths, but the DASH branch hands the player a bare
        // MediaItem, which would otherwise be served by Media3's stock
        // DefaultDataSource - no per-URL User-Agent, so googlevideo answers 403
        // and the video dead-ends on "Source error". Setting it here covers the
        // manifest, its segments, and any future setMediaItem call.
        _exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(streamDataSourceFactory))
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            playWhenReady = true
            // Repeat is persisted, so a fresh player must adopt the stored
            // mode: otherwise the toolbar shows the repeat icon while the
            // player silently auto-plays the next video instead of looping.
            repeatMode = if (_isLooping.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        // Playback recovered (or started cleanly): allow the
                        // retry budget to be spent again on a later failure.
                        rendererRetryCount = 0
                        sourceRetryCount = 0
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        // Repeat and auto-play are mutually exclusive: looping
                        // repeats the current video, otherwise auto-play moves
                        // to the next related one.
                        if (!_isLooping.value) {
                            val nextVideo = _relatedVideos.value.firstOrNull()
                            // Guard: ensure ViewModel/player is still valid before launching
                            if (nextVideo != null && _exoPlayer != null) {
                                viewModelScope.launch { playVideo(nextVideo) }
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // A renderer/decoder failure is not a broken stream: the
                    // codec lost its surface or was reclaimed. Re-prepare in
                    // place (position is kept) instead of dead-ending the
                    // player on an error overlay the user cannot dismiss.
                    if (isTransientRendererError(error) && rendererRetryCount < MAX_RENDERER_RETRIES) {
                        rendererRetryCount++
                        android.util.Log.w(
                            "VideoPlayerVM",
                            "Transient renderer error (attempt $rendererRetryCount/$MAX_RENDERER_RETRIES); re-preparing",
                            error
                        )
                        _exoPlayer?.prepare()
                        return
                    }

                    // A source failure means the URL is dead, not the video:
                    // re-resolving is the only thing that can help, and
                    // re-preparing the same URL never will.
                    if (isRecoverableSourceError(error) && sourceRetryCount < MAX_SOURCE_RETRIES) {
                        sourceRetryCount++
                        android.util.Log.w(
                            "VideoPlayerVM",
                            "Source error (attempt $sourceRetryCount/$MAX_SOURCE_RETRIES); re-resolving stream",
                            error
                        )
                        recoverFromSourceError(error)
                        return
                    }

                    _playbackError.value = error
                    _isBuffering.value = false
                }
            })
        }

        // Warm the visitorData cache so the first playback doesn't pay for
        // the youtube.com bootstrap download on its critical path.
        viewModelScope.launch { youtubeRepository.prefetchVisitorData() }
    }

    /**
     * The app is no longer visible (home, recents, screen off, another app on
     * top) and is not in Picture-in-Picture.
     *
     * The window - and with it the PlayerView's Surface - is destroyed shortly
     * after this point, while the player keeps decoding: WAKE_MODE_NETWORK
     * deliberately keeps playback alive so screen-off listening works. A video
     * decoder writing into a surface that is being torn down throws inside
     * MediaCodecVideoRenderer, which used to surface as a permanent error
     * overlay on return even though the format was fully supported.
     *
     * Disabling the video track releases the codec cleanly and leaves audio
     * playing. [onEnterForeground] re-enables it and rendering resumes at the
     * live position once the new surface is attached.
     */
    fun onEnterBackground() {
        val player = _exoPlayer ?: return
        if (isVideoSuspended || _currentVideo.value == null) return
        isVideoSuspended = true
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
    }

    /** The app is visible again: restore the video track suspended above. */
    fun onEnterForeground() {
        val player = _exoPlayer ?: return
        if (!isVideoSuspended) return
        isVideoSuspended = false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .build()
    }

    /**
     * Retry after playback failed, from the error overlay. Re-resolves the
     * stream and rebuilds the media source at the current position; falls back
     * to re-running the whole load when there is no player to reload into.
     */
    fun retryPlayback() {
        rendererRetryCount = 0
        sourceRetryCount = 0
        _playbackError.value = null
        val video = _currentVideo.value ?: return
        if (_exoPlayer == null) {
            playVideo(video, forceRestart = true)
            return
        }
        // Always re-resolve rather than replaying _currentQuality: its URL is
        // what just failed, and googlevideo URLs are short-lived, so handing
        // the same one back to the player fails identically every time.
        _isLoading.value = true
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = viewModelScope.launch {
            if (!reresolveAndReload(video)) {
                _playbackError.value = Exception("Unable to load video stream")
                _isLoading.value = false
            }
        }
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
        val video = _currentVideo.value
        if (video == null) {
            _playbackError.value = original
            _isBuffering.value = false
            return
        }
        // _isLoading, not _isBuffering: a fatal error drives the player to
        // STATE_IDLE, whose onPlaybackStateChanged sets _isBuffering back to
        // false and would leave the re-resolve showing a frozen frame with no
        // spinner. Nothing else writes _isLoading, and "resolving a stream" is
        // exactly what it already means during the initial load.
        _isLoading.value = true
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = viewModelScope.launch {
            if (httpResponseCode(original) == 403) {
                youtubeRepository.refreshVisitorDataAfterPlaybackFailure()
            }
            if (!reresolveAndReload(video)) {
                _playbackError.value = original
                _isLoading.value = false
            }
        }
    }

    /**
     * Re-resolve [video]'s stream URLs and rebuild the media source at the
     * current position, keeping the quality the user was watching when it is
     * still on offer. Returns false when resolution yielded nothing usable, so
     * the caller can surface an error; true also covers "the user moved on
     * mid-flight", where there is nothing left to recover.
     */
    private suspend fun reresolveAndReload(video: VideoItem): Boolean {
        if (_exoPlayer == null) return false
        val qualities = try {
            kotlinx.coroutines.withTimeout(15000L) {
                youtubeRepository.getVideoStreamQualities(video.videoId)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Caught before CancellationException below, which it subclasses:
            // a timed-out resolution is a real failure the caller must surface,
            // not a cancellation to propagate.
            android.util.Log.w("VideoPlayerVM", "Re-resolve timed out for ${video.videoId}", e)
            emptyList()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // playVideo() cancels this job when the user moves on. Swallowing
            // that would let a dead recovery keep writing loading/error state
            // over the video that replaced it.
            throw e
        } catch (e: Exception) {
            android.util.Log.w("VideoPlayerVM", "Re-resolve failed for ${video.videoId}", e)
            emptyList()
        }
        if (_currentVideo.value?.videoId != video.videoId) {
            _isLoading.value = false
            return true
        }
        if (qualities.isEmpty()) return false

        _availableQualities.value = qualities
        val previousLabel = _currentQuality.value?.resolution
        val quality = qualities.firstOrNull { it.resolution == previousLabel }
            ?: pickDefaultQuality(qualities)
        reloadPreservingPosition(quality)
        _exoPlayer?.play()
        _playbackError.value = null
        _isLoading.value = false
        return true
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

    /** HTTP status behind a source error, or null when it was not an HTTP failure. */
    private fun httpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            cause = cause.cause
        }
        return null
    }

    /**
     * Renderer-level failures (surface torn down, codec reclaimed by another
     * app, transient decode error) are worth re-preparing for. Source-level
     * failures - dead URL, unsupported format - are not, and must reach the UI.
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

    fun toggleLooping() {
        _isLooping.value = !_isLooping.value
        _exoPlayer?.repeatMode = if (_isLooping.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        themePreferences.setVideoRepeatEnabled(_isLooping.value)
    }

    /** Set the playback speed for the current video. Resets to 1x on video change. */
    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        _exoPlayer?.setPlaybackSpeed(speed)
    }

    /** Jump to a chapter's start position. */
    fun seekToChapter(chapter: com.ivor.ivormusic.data.VideoChapter) {
        _exoPlayer?.seekTo(chapter.startMs)
    }

    /**
     * @param forceRestart reload even when this video is already current, for
     * the retry path - tapping the same video otherwise only re-expands.
     */
    fun playVideo(video: VideoItem, forceRestart: Boolean = false) {
        if (!forceRestart && _currentVideo.value?.videoId == video.videoId) {
            // Already playing this video, just expand
            _isExpanded.value = true
            return
        }

        _currentVideo.value = video
        _isExpanded.value = true
        _isLoading.value = true
        _relatedVideos.value = emptyList() // Clear previous related
        _chapters.value = emptyList() // Clear previous chapters
        _captionTracks.value = emptyList() // Clear previous caption tracks
        _selectedCaption.value = null // Captions default off per video
        _isCaptionsLoading.value = false
        captionsLoadedForVideoId = null
        captionCuesJob?.cancel()
        _captionCues.value = emptyList()
        _playbackError.value = null // Clear previous error
        rendererRetryCount = 0
        sourceRetryCount = 0
        // A recovery still in flight belongs to the previous video
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = null

        // Reset engagement + comments state for the new video
        _engagement.value = null
        _comments.value = emptyList()
        _replies.value = emptyMap()
        _loadingReplyIds.value = emptySet()
        commentsNextToken = null
        commentsLoadedForVideoId = null
        _createCommentParams.value = null
        _isLoggedIn.value = youtubeRepository.isLoggedIn()

        // Channel sheet content is per-channel; the next video may belong to
        // a different one, so force a re-fetch on the next open
        _channelVideos.value = emptyList()
        _isChannelVideosLoading.value = false
        channelVideosLoadedForChannelId = null

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
                            val source = ProgressiveMediaSource.Factory(streamDataSourceFactory)
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
                _chapters.value = watchNext.chapters
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
     * Pick the starting quality based on the Settings preference for the
     * current network (Wi-Fi vs mobile data). Fresh pref read because Settings
     * toggles through its own ThemePreferences instance.
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

    /**
     * Build the media source for [quality].
     *
     * Captions are not part of it. They used to be sideloaded here as a text
     * track, which made every CC toggle a media-source rebuild: the video and
     * audio were torn down and refetched from the network just to add or drop a
     * subtitle. Koda renders cues itself instead - see [setCaptionTrack] - so
     * this only ever deals with video and audio.
     */
    private fun loadQuality(quality: VideoQuality) {
        _currentQuality.value = quality

        if (quality.isDASH) {
            // Adaptive manifest - hand it to the player and let its MediaSource
            // factory build the DASH/HLS source, no merging needed.
            _exoPlayer?.setMediaItem(
                MediaItem.Builder()
                    .setUri(quality.url)
                    .setMimeType(adaptiveMimeType(quality))
                    .build()
            )
        } else {
            val dataSourceFactory = streamDataSourceFactory
            val audioUrl = quality.audioUrl
            val primarySource = if (audioUrl != null) {
                // Non-DASH with separate audio - use MergingMediaSource.
                // adjustPeriodTimeOffsets aligns the two tracks' start offsets,
                // preventing A/V desync when they don't begin at the same time.
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(quality.url))
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl))
                MergingMediaSource(true, videoSource, audioSource)
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(quality.url))
            }

            _exoPlayer?.setMediaSource(primarySource)
        }
        _exoPlayer?.prepare()
    }

    /**
     * MIME for an adaptive quality. Both DASH and HLS entries arrive with
     * isDASH set and are told apart only by [VideoQuality.format], so pinning
     * MPD unconditionally would make the factory build a DashMediaSource for an
     * m3u8 playlist and fail the load - which is what every live stream gets.
     */
    private fun adaptiveMimeType(quality: VideoQuality): String =
        if (quality.format.equals("HLS", ignoreCase = true)) MimeTypes.APPLICATION_M3U8
        else MimeTypes.APPLICATION_MPD

    fun setQuality(quality: VideoQuality) {
        reloadPreservingPosition(quality)
    }

    /**
     * Rebuild the media source for a new quality while keeping the current
     * playback position. Only quality switches need this - caption changes no
     * longer touch the media source at all.
     */
    private fun reloadPreservingPosition(quality: VideoQuality) {
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

    /** Load the caption track list for the current video, once, on demand. */
    fun ensureCaptionsLoaded() {
        val video = _currentVideo.value ?: return
        if (captionsLoadedForVideoId == video.videoId) return
        captionsLoadedForVideoId = video.videoId
        _isCaptionsLoading.value = true
        viewModelScope.launch {
            try {
                val tracks = youtubeRepository.getCaptionTracks(video.videoId)
                // Ignore stale results if the user switched videos meanwhile
                if (_currentVideo.value?.videoId == video.videoId) {
                    _captionTracks.value = tracks
                }
            } finally {
                if (_currentVideo.value?.videoId == video.videoId) {
                    _isCaptionsLoading.value = false
                }
            }
        }
    }

    /**
     * Select a caption track, or null to turn captions off.
     *
     * The playback pipeline is deliberately untouched here. Captions used to be
     * a text track merged into the media source, so switching them rebuilt that
     * source: playback re-prepared, the buffer was discarded, and - because
     * video has no disk cache - everything already downloaded was fetched
     * again. Cues are fetched and parsed on their own instead, and the overlay
     * draws them over the video surface, which makes the CC toggle instant and
     * free no matter how many times it is pressed.
     */
    fun setCaptionTrack(track: CaptionTrack?) {
        if (_selectedCaption.value == track) return
        _selectedCaption.value = track

        captionCuesJob?.cancel()
        if (track == null) {
            _captionCues.value = emptyList()
            return
        }
        captionCuesJob = viewModelScope.launch {
            val cues = youtubeRepository.getCaptionCues(track)
            // Ignore a load the user has already switched away from
            if (_selectedCaption.value == track) {
                _captionCues.value = cues
                if (cues.isEmpty()) {
                    android.util.Log.w(
                        "VideoPlayerVM",
                        "Caption track ${track.languageCode} produced no cues"
                    )
                }
            }
        }
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    fun closePlayer() {
        // Remove quality change listener to prevent leaks if player closed before STATE_READY
        qualityChangeListener?.let { _exoPlayer?.removeListener(it) }
        qualityChangeListener = null
        _exoPlayer?.stop()
        // Track selection outlives media items: a player closed while the video
        // track is suspended would come back audio-only on the next video.
        onEnterForeground()
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

    /** Pause without closing the player (music or Shorts playback started). */
    fun pause() {
        _exoPlayer?.pause()
    }

    // ---------------- Save to playlist / Watch Later ----------------

    /** Load the user's YouTube playlists for the save sheet. Requires login. */
    fun loadVideoPlaylists(force: Boolean = false) {
        if (_isVideoPlaylistsLoading.value) return
        if (_videoPlaylists.value.isNotEmpty() && !force) return
        viewModelScope.launch {
            _isVideoPlaylistsLoading.value = true
            try {
                _videoPlaylists.value = youtubeRepository.getVideoPlaylists()
            } finally {
                _isVideoPlaylistsLoading.value = false
            }
        }
    }

    /**
     * Add a video to a YouTube playlist ("WL" = Watch Later). Reports the
     * outcome on the main thread so the save sheet can show inline feedback.
     * Requires login.
     */
    fun addVideoToPlaylist(playlistId: String, video: VideoItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(youtubeRepository.addToYouTubePlaylist(playlistId, video.videoId, music = false))
        }
    }

    // ---------------- Channel page ----------------

    /**
     * Load the current video's channel uploads for the channel sheet, once
     * per channel. Uses the canonical UC... id from engagement (falls back to
     * the id parsed with the video item).
     */
    fun loadChannelVideos() {
        val video = _currentVideo.value ?: return
        val channelId = _engagement.value?.channelId ?: video.channelId ?: return
        if (channelVideosLoadedForChannelId == channelId) return
        channelVideosLoadedForChannelId = channelId
        _channelVideos.value = emptyList()
        _isChannelVideosLoading.value = true
        viewModelScope.launch {
            try {
                val channel = com.ivor.ivormusic.data.SubscribedChannel(
                    channelId = channelId,
                    name = video.channelName,
                    avatarUrl = video.channelIconUrl,
                    subscriberCountText = _engagement.value?.subscriberCountText
                )
                _channelVideos.value = youtubeRepository.getChannelVideos(channel)
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayerVM", "Failed to load channel videos", e)
            } finally {
                _isChannelVideosLoading.value = false
            }
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

        /** Silent re-prepare attempts before a renderer error reaches the UI. */
        private const val MAX_RENDERER_RETRIES = 2

        /**
         * Silent re-resolve attempts before a source error reaches the UI. One
         * is enough: the first pass already remints a rejected visitorData and
         * fetches brand-new URLs, so a second failure means the video really is
         * unplayable and the user should get the overlay instead of a spinner.
         */
        private const val MAX_SOURCE_RETRIES = 1
    }

    override fun onCleared() {
        super.onCleared()
        // Remove quality change listener to prevent leaks
        qualityChangeListener?.let { _exoPlayer?.removeListener(it) }
        qualityChangeListener = null
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = null
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
