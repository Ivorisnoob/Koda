package com.ivor.ivormusic.ui.video

import android.content.Context
import android.os.SystemClock
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
import com.ivor.ivormusic.data.LocalSubscription
import com.ivor.ivormusic.data.SubscriptionActions
import com.ivor.ivormusic.data.SubscriptionStore
import com.ivor.ivormusic.data.LiveChatBanner
import com.ivor.ivormusic.data.LiveChatMessage
import com.ivor.ivormusic.data.LiveChatPage
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private val notInterestedRepository =
        com.ivor.ivormusic.data.NotInterestedRepository(context)

    private val _relatedVideos = MutableStateFlow<List<VideoItem>>(emptyList())

    /**
     * Up Next, minus what the user asked not to see. Derived rather than
     * written through, so a "not interested" tap removes the row on the next
     * frame and Undo restores it in place - see HomeViewModel.trendingVideos.
     */
    val relatedVideos: StateFlow<List<VideoItem>> =
        combine(
            _relatedVideos,
            notInterestedRepository.hiddenVideos,
            notInterestedRepository.blockedChannels
        ) { videos, _, _ -> notInterestedRepository.filter(videos) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Hide one video from every recommendation feed. */
    fun markNotInterested(video: VideoItem) = notInterestedRepository.hideVideo(video)

    /** Stop recommending anything from this video's channel. */
    fun blockChannelFor(video: VideoItem) =
        notInterestedRepository.blockChannel(video.channelId, video.channelName, video.channelIconUrl)

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

    // ---------------- Picture-in-Picture ----------------

    // Width/height of the video being played, so the PiP window takes the
    // shape of the video instead of a hardcoded 16:9 that letterboxes
    // everything else. Null until the first frame is decoded.
    private val _videoAspectRatio = MutableStateFlow<Float?>(null)
    val videoAspectRatio: StateFlow<Float?> = _videoAspectRatio.asStateFlow()

    // Where the video surface sits on screen, in window coordinates. Handed to
    // PictureInPictureParams as the source rect hint so the system animates the
    // PiP window out of the video itself; without it the transition scales down
    // the entire activity window, app chrome and all.
    private val _videoSurfaceBounds = MutableStateFlow<android.graphics.Rect?>(null)
    val videoSurfaceBounds: StateFlow<android.graphics.Rect?> = _videoSurfaceBounds.asStateFlow()

    fun setVideoSurfaceBounds(bounds: android.graphics.Rect?) {
        _videoSurfaceBounds.value = bounds
    }

    // True while the app is in system Picture-in-Picture. Set by the
    // composition so the STATE_ENDED auto-play can stand down: advancing
    // to the next video while in PiP means the user returns to a video
    // they did not put there.
    private var _isInPipMode = false

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode = inPip
    }

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

    private val subscriptionActions =
        SubscriptionActions(context, youtubeRepository)

    /**
     * Whether the current video's channel is followed at all - by the signed-in
     * account or on this device. The subscribe button binds to this rather than
     * to `engagement.isSubscribed`, which only ever knows about the account and
     * so read "Subscribe" for a channel the user had followed locally.
     */
    val isSubscribedToChannel: StateFlow<Boolean> =
        combine(_engagement, subscriptionActions.subscriptions) { engagement, localSubs ->
            val channelId = engagement?.channelId
            engagement?.isSubscribed == true ||
                (channelId != null && localSubs.any { it.channelId == channelId })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** True when the subscribe button has to send the user to sign in first. */
    fun subscribeNeedsLogin(): Boolean = subscriptionActions.subscribeNeedsLogin()

    // ---------------- Live broadcast ----------------

    /**
     * Whether the current video is a live broadcast. Resolved in Phase 1 from
     * the quality list: a live stream only ever produces a single adaptive HLS
     * entry (see YouTubeRepository.parseQualitiesFromStreamingData), so the
     * flag rides along with the streams instead of costing a separate call.
     */
    private val _isLive = MutableStateFlow(false)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    /**
     * Whether the source frame is portrait. Set in Phase 1 off the stream
     * dimensions so the vertical live layout can be composed before the first
     * frame exists, then confirmed by the player's own video size once it does
     * (the NewPipe fallback path can resolve a live manifest without ever
     * listing a dimensioned format).
     *
     * Together with [isLive] this is what selects the full-bleed vertical
     * player: a vertical live stream is an ordinary broadcast with a 9:16
     * encode, so orientation is the only thing distinguishing it.
     */
    private val _isPortraitVideo = MutableStateFlow(false)
    val isPortraitVideo: StateFlow<Boolean> = _isPortraitVideo.asStateFlow()

    /** Concurrent viewers, e.g. "14,618 watching now". Live videos only. */
    private val _liveViewerCount = MutableStateFlow<String?>(null)
    val liveViewerCount: StateFlow<String?> = _liveViewerCount.asStateFlow()

    /** Null until the first chat poll answers; false when chat is unavailable. */
    private val _isLiveChatAvailable = MutableStateFlow<Boolean?>(null)
    val isLiveChatAvailable: StateFlow<Boolean?> = _isLiveChatAvailable.asStateFlow()

    private val _liveChatMessages = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    val liveChatMessages: StateFlow<List<LiveChatMessage>> = _liveChatMessages.asStateFlow()

    private val _liveChatBanner = MutableStateFlow<LiveChatBanner?>(null)
    val liveChatBanner: StateFlow<LiveChatBanner?> = _liveChatBanner.asStateFlow()

    private val _isLiveChatLoading = MutableStateFlow(false)
    val isLiveChatLoading: StateFlow<Boolean> = _isLiveChatLoading.asStateFlow()

    private val _isSendingLiveChat = MutableStateFlow(false)
    val isSendingLiveChat: StateFlow<Boolean> = _isSendingLiveChat.asStateFlow()

    private val _liveChatSendParams = MutableStateFlow<String?>(null)

    /**
     * Posting needs both an account and a chat that accepts messages. The send
     * token is present in the poll response even when signed out, so it is not
     * on its own permission to post.
     */
    val canSendLiveChat: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        _isLoggedIn, _liveChatSendParams
    ) { loggedIn, params -> loggedIn && params != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _liveChatMaxLength = MutableStateFlow(200)
    val liveChatMaxLength: StateFlow<Int> = _liveChatMaxLength.asStateFlow()

    /**
     * Why the composer is closed - subscribers-only, members-only, slow mode, a
     * ban. Null when the viewer may post. Distinct from [canSendLiveChat], which
     * is about having an account at all.
     */
    private val _liveChatRestriction = MutableStateFlow<String?>(null)
    val liveChatRestriction: StateFlow<String?> = _liveChatRestriction.asStateFlow()

    private var liveChatJob: Job? = null
    private var liveMetadataJob: Job? = null
    private var liveChatStartedForVideoId: String? = null

    /**
     * Chat start token lifted from Phase 2's watch-next response, so opening the
     * panel does not re-fetch and re-parse that multi-megabyte tree.
     */
    private var liveChatContinuation: String? = null

    /**
     * Messages that have arrived from the server but are not on screen yet.
     *
     * A poll returns ten seconds of chat in one lump; releasing it in one frame
     * is what made the panel feel like a stuttering wall rather than a live
     * conversation. Both the poll loop and the drain loop run on viewModelScope's
     * main dispatcher, so this needs no synchronization.
     */
    private val pendingLiveChat = ArrayDeque<LiveChatMessage>()

    /** Ids already queued or displayed, so a re-delivered message is dropped. */
    private val seenLiveChatIds = LinkedHashSet<String>()

    /** Elapsed-realtime mark when the next batch is expected, for pacing. */
    private var liveChatBatchDueAt = 0L

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
            // 10s each way, matching the player's own double-tap seek. These
            // are what the media notification's and PiP window's skip buttons
            // move by, so they must agree with the in-app gesture rather than
            // with Media3's 5s/15s defaults.
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
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

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    // Drives the PiP window's shape. Ignore the 0x0 the player
                    // reports between media items, which would otherwise
                    // collapse the aspect ratio mid-switch.
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                        _videoAspectRatio.value = ratio
                        // Backstop for the parse-time read: authoritative, but
                        // only available once a frame has decoded.
                        _isPortraitVideo.value = ratio < 1f
                    }
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
                        // to the next related one. Suppressed during PiP so
                        // the user returns to the video they put there.
                        if (!_isLooping.value && !_isInPipMode) {
                            // The filtered list, not the raw one: auto-playing
                            // a video the user just said "not interested" to is
                            // the single most annoying way to get this wrong.
                            val nextVideo = relatedVideos.value.firstOrNull()
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

        // Live state belongs to the previous video; the polls must stop before
        // their next tick can write into the new video's chat.
        stopLivePolling()
        // Track selection outlives media items, so a height cap picked on a
        // live stream would silently limit the next video too - and on a VOD,
        // where quality is a source swap, that cap is invisible in the UI.
        _exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearVideoSizeConstraints()
                .setForceHighestSupportedBitrate(false)
                .build()
        }
        _isLive.value = false
        _isPortraitVideo.value = false
        _liveViewerCount.value = null
        _isLiveChatAvailable.value = null
        _liveChatMessages.value = emptyList()
        _liveChatBanner.value = null
        _liveChatSendParams.value = null
        _liveChatRestriction.value = null
        _isLiveChatLoading.value = false
        liveChatStartedForVideoId = null
        liveChatContinuation = null
        pendingLiveChat.clear()
        seenLiveChatIds.clear()

        // Speed is per-video, like YouTube
        _playbackSpeed.value = 1f
        _exoPlayer?.setPlaybackSpeed(1f)

        // Publish to the system's media controls. Started here, from a user tap,
        // because a foreground service may not be started from the background;
        // repeat calls once it is up are no-ops.
        _exoPlayer?.let {
            com.ivor.ivormusic.service.VideoPlaybackService.start(context, it)
        }

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
                    _isLive.value = qualities.any { it.isLive }
                    if (qualities.any { it.isPortrait }) _isPortraitVideo.value = true
                    if (_isLive.value) startLiveMetadataPolling(video.videoId)

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
                                .createMediaSource(nowPlayingMediaItem(streamUrl))
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
                    val wasNameless = _currentVideo.value?.title.isNullOrBlank()
                    _currentVideo.value = watchNext.updatedVideoItem
                    // A video opened from a shared link starts out nameless -
                    // give the lock screen and shade the real title now that
                    // there is one. Deliberately only for that case: every other
                    // entry point already carries a title, and touching the
                    // media item is not worth it just to swap a thumbnail size.
                    if (wasNameless) refreshNowPlayingMetadata()
                }
                if (watchNext.relatedVideos.isNotEmpty()) {
                    _relatedVideos.value = watchNext.relatedVideos
                }
                _chapters.value = watchNext.chapters
                // Opening the chat panel now costs one poll instead of a second
                // watch-next round trip plus its parse.
                liveChatContinuation = watchNext.liveChatContinuation
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

        // The live ladder leads with an "Auto" entry (height 0) that the VOD
        // branches below would skip over, so it picks its own entry: Auto when
        // the setting says auto, otherwise the best rendition at or below the
        // target, falling back to Auto rather than to the lowest available.
        if (qualities.firstOrNull()?.isLive == true) {
            if (preferred == ThemePreferences.VIDEO_QUALITY_AUTO) return qualities.first()
            val targetHeight = height(preferred)
            return qualities.firstOrNull { height(it.resolution) in 1..targetHeight }
                ?: qualities.first()
        }

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

        if (quality.isLive) {
            // The whole ladder is one manifest, so the cap has to be applied
            // alongside preparing it - loadQuality is the only entry point that
            // runs for the initial pick.
            applyLiveQualityCap(quality)
        }

        if (quality.isDASH) {
            // Adaptive manifest - hand it to the player and let its MediaSource
            // factory build the DASH/HLS source, no merging needed.
            _exoPlayer?.setMediaItem(
                nowPlayingMediaItem(quality.url)
                    .buildUpon()
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
                // The merged source reports the *video* item's MediaItem, which
                // is why the metadata rides on that one.
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(nowPlayingMediaItem(quality.url))
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl))
                MergingMediaSource(true, videoSource, audioSource)
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(nowPlayingMediaItem(quality.url))
            }

            _exoPlayer?.setMediaSource(primarySource)
        }
        _exoPlayer?.prepare()
    }

    /**
     * A [MediaItem] for [uri] carrying the current video's title, channel and
     * thumbnail.
     *
     * Without this the player has no metadata at all, and the lock screen /
     * shade / Bluetooth display that [com.ivor.ivormusic.service.VideoPlaybackService]
     * publishes would be a blank card with transport buttons on it. The artwork
     * URI is loaded by Media3's own bitmap loader, so no bitmap work happens
     * here.
     */
    private fun nowPlayingMediaItem(uri: String): MediaItem {
        val video = _currentVideo.value
        val builder = MediaItem.Builder().setUri(uri)
        if (video != null) {
            builder.setMediaId(video.videoId)
            builder.setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(video.title.takeIf { it.isNotBlank() })
                    .setArtist(video.channelName.takeIf { it.isNotBlank() })
                    .setArtworkUri(video.thumbnailUrl?.let { android.net.Uri.parse(it) })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
        }
        return builder.build()
    }

    /**
     * Push freshly-learned title/channel into the already-playing media item.
     *
     * Only matters for the shared-link path, which starts playback from a video
     * id alone and so hands the session a nameless card until watch-next lands.
     * [Player.replaceMediaItem] is metadata-only here - the URI is untouched, so
     * every source type in use reports it can update in place and playback is
     * not interrupted. Guarded anyway: a wrong guess would rebuild the source
     * and cost a rebuffer, which is never worth a caption.
     */
    private fun refreshNowPlayingMetadata() {
        val player = _exoPlayer ?: return
        if (player.mediaItemCount != 1) return
        val current = player.currentMediaItem ?: return
        val uri = current.localConfiguration?.uri?.toString() ?: return
        val metadata = nowPlayingMediaItem(uri).mediaMetadata
        if (metadata == current.mediaMetadata) return
        try {
            // buildUpon rather than a fresh item: the URI, MIME type and cache
            // key all have to stay byte-identical for the update to be seamless.
            player.replaceMediaItem(0, current.buildUpon().setMediaMetadata(metadata).build())
        } catch (e: Exception) {
            android.util.Log.w("VideoPlayerVM", "Could not refresh now-playing metadata", e)
        }
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
        // Live renditions all live in the manifest that is already playing, so
        // switching is a track-selector cap rather than a new media source: no
        // re-prepare, no rebuffer, and ABR keeps adapting underneath the cap.
        // Rebuilding the source here would also drop the viewer back to the
        // live edge and throw away the DVR buffer.
        if (quality.isLive) {
            _currentQuality.value = quality
            applyLiveQualityCap(quality)
            return
        }
        reloadPreservingPosition(quality)
    }

    /**
     * Constrain the video track to [quality]'s height, or lift the constraint
     * for the "Auto" entry.
     *
     * The height cap alone is only a ceiling - ABR would still be free to sit
     * two rungs below it, so picking "1080p60" could visibly play 480p. Pairing
     * it with forceHighestSupportedBitrate makes an explicit pick behave like a
     * pin (always the best track that fits the cap) while Auto stays adaptive,
     * which is the split users expect from the menu.
     *
     * buildUpon() so this composes with the video-track suspend/restore in
     * [onEnterBackground] / [onEnterForeground] instead of overwriting it.
     */
    private fun applyLiveQualityCap(quality: VideoQuality) {
        val player = _exoPlayer ?: return
        val height = quality.resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply {
                if (height > 0) setMaxVideoSize(Int.MAX_VALUE, height)
                else clearVideoSizeConstraints()
                setForceHighestSupportedBitrate(height > 0)
            }
            .build()
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
        stopLivePolling()
        liveChatStartedForVideoId = null
        _currentVideo.value = null
        _isExpanded.value = false
        // Nothing is playing any more, so nothing should be on the lock screen.
        com.ivor.ivormusic.service.VideoPlaybackService.stop(context)
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            _exoPlayer?.pause()
        } else {
            _exoPlayer?.play()
        }
    }

    /**
     * Skip [deltaMs] from the current position, clamped to the media.
     *
     * Lives here rather than in the player UI because the same step is driven
     * from outside the composition too - the Picture-in-Picture window's
     * RemoteActions have no other way to reach the player.
     */
    fun seekBy(deltaMs: Long) {
        val player = _exoPlayer ?: return
        val duration = player.duration
        val upperBound = if (duration > 0) duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, upperBound))
    }

    /** Pause without closing the player (music or Shorts playback started). */
    fun pause() {
        _exoPlayer?.pause()
    }

    // ---------------- Live chat ----------------

    /**
     * Start the chat stream. Called when the chat panel opens rather than when
     * the video loads: a live stream watched without the panel open should not
     * pay for a poll every 10 seconds.
     */
    fun ensureLiveChatStarted() {
        val videoId = _currentVideo.value?.videoId ?: return
        if (!_isLive.value) return
        if (liveChatStartedForVideoId == videoId && liveChatJob?.isActive == true) return
        liveChatStartedForVideoId = videoId
        startLiveChatPolling(videoId)
    }

    /**
     * Stop polling when the panel closes. Messages are kept so reopening shows
     * the conversation immediately while the fresh backlog loads behind it -
     * the poll dedupes by id, so the overlap costs nothing.
     */
    fun stopLiveChat() {
        liveChatJob?.cancel()
        liveChatJob = null
        liveChatStartedForVideoId = null
        _isLiveChatLoading.value = false
    }

    /**
     * Run the chat: one coroutine polling the server, one releasing what it
     * fetched onto the screen.
     *
     * They are separate because their rhythms are: the server hands over ten
     * seconds of chat in a single response, and rendering that in one frame is
     * what made the panel feel broken. The drain loop spreads a batch across the
     * interval until the next one is due, so messages arrive at roughly the rate
     * people actually typed them.
     */
    private fun startLiveChatPolling(videoId: String) {
        liveChatJob?.cancel()
        pendingLiveChat.clear()
        seenLiveChatIds.clear()
        _liveChatMessages.value.forEach { seenLiveChatIds.add(it.id) }

        liveChatJob = viewModelScope.launch {
            _isLiveChatLoading.value = true

            // Phase 2 usually has the token already; only a chat opened before
            // watch-next lands pays for a fetch.
            val start = liveChatContinuation
                ?: youtubeRepository.getLiveChatSession(videoId)?.continuation
            if (_currentVideo.value?.videoId != videoId) return@launch
            if (start == null) {
                // No conversationBar in the watch-next response: chat is
                // disabled or unavailable for this broadcast, which is a state
                // to render, not an error.
                _isLiveChatAvailable.value = false
                _isLiveChatLoading.value = false
                return@launch
            }
            liveChatContinuation = start

            coroutineScope {
                val drain = launch { drainLiveChatQueue() }
                try {
                    pollLiveChatLoop(videoId, start)
                } finally {
                    drain.cancel()
                    // Anything still queued was already fetched; drop it on
                    // screen rather than losing it on the way out.
                    flushPendingLiveChat()
                    _isLiveChatLoading.value = false
                }
            }
        }
    }

    /**
     * Fetch pages on the cadence the server dictates.
     *
     * Every response hands back the next token and the delay before it should be
     * used (10s on every stream sampled). The wait subtracts the time the request
     * itself took, so the cycle stays a true 10s instead of drifting further
     * behind live by one round trip per poll.
     *
     * The first response carries the visible backlog - that is history, not new
     * chat, so it goes straight to the screen; only later pages are paced.
     */
    private suspend fun pollLiveChatLoop(videoId: String, firstContinuation: String) {
        var continuation: String? = firstContinuation
        var consecutiveFailures = 0
        var isFirstPage = true

        while (true) {
            val token = continuation ?: break
            val startedAt = SystemClock.elapsedRealtime()
            val page = youtubeRepository.pollLiveChat(token)
            if (_currentVideo.value?.videoId != videoId) return

            if (page == null) {
                // A failed poll is usually transient (the stream is still
                // there), so back off and retry the same token a few times
                // before declaring the chat gone.
                consecutiveFailures++
                if (consecutiveFailures > MAX_LIVE_CHAT_POLL_FAILURES) {
                    if (_isLiveChatAvailable.value == null) _isLiveChatAvailable.value = false
                    return
                }
                kotlinx.coroutines.delay(LIVE_CHAT_RETRY_DELAY_MS)
                continue
            }
            consecutiveFailures = 0

            _isLiveChatAvailable.value = true
            _isLiveChatLoading.value = false
            page.sendParams?.let { _liveChatSendParams.value = it }
            _liveChatMaxLength.value = page.maxMessageLength
            _liveChatRestriction.value = page.restrictionMessage
            if (page.bannerCleared) _liveChatBanner.value = null
            page.banner?.let { _liveChatBanner.value = it }

            applyLiveChatModeration(page)

            val fresh = page.messages.filter { seenLiveChatIds.add(it.id) }
            if (isFirstPage) {
                appendLiveChatMessages(fresh)
                isFirstPage = false
            } else {
                pendingLiveChat.addAll(fresh)
            }
            trimSeenLiveChatIds()

            continuation = page.nextContinuation
            val window = page.timeoutMs.coerceIn(LIVE_CHAT_MIN_WINDOW_MS, LIVE_CHAT_MAX_WINDOW_MS)
            val wait = (window - (SystemClock.elapsedRealtime() - startedAt))
                .coerceAtLeast(LIVE_CHAT_MIN_WINDOW_MS)
            liveChatBatchDueAt = SystemClock.elapsedRealtime() + wait
            kotlinx.coroutines.delay(wait)
        }
    }

    /**
     * Release queued messages one at a time, spread over the time remaining
     * until the next batch lands, so the queue empties just as it arrives.
     *
     * The first message of a batch shows immediately - the pacing only applies
     * to the ones behind it - so a quiet chat has no added latency while a busy
     * one flows instead of arriving in slabs. Falling behind (a burst larger
     * than the window can pace) collapses the gap to the floor and catches up.
     */
    private suspend fun drainLiveChatQueue() {
        while (true) {
            val next = pendingLiveChat.removeFirstOrNull()
            if (next == null) {
                kotlinx.coroutines.delay(LIVE_CHAT_DRAIN_IDLE_MS)
                continue
            }
            appendLiveChatMessages(listOf(next))

            val remaining = pendingLiveChat.size
            if (remaining == 0) {
                kotlinx.coroutines.delay(LIVE_CHAT_DRAIN_IDLE_MS)
                continue
            }
            val budget = liveChatBatchDueAt - SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(
                (budget / (remaining + 1))
                    .coerceIn(LIVE_CHAT_MIN_GAP_MS, LIVE_CHAT_MAX_GAP_MS)
            )
        }
    }

    /** Put everything still queued on screen at once. */
    private fun flushPendingLiveChat() {
        if (pendingLiveChat.isEmpty()) return
        val rest = pendingLiveChat.toList()
        pendingLiveChat.clear()
        appendLiveChatMessages(rest)
    }

    /**
     * Append to the visible list, capped at [MAX_LIVE_CHAT_MESSAGES] - the same
     * cap YouTube declares in the response - so a stream left open overnight
     * cannot grow the list without bound.
     */
    private fun appendLiveChatMessages(new: List<LiveChatMessage>) {
        if (new.isEmpty()) return
        _liveChatMessages.update { current -> (current + new).takeLast(MAX_LIVE_CHAT_MESSAGES) }
    }

    /**
     * Apply deletions and edits to both the visible list and the queue - a
     * message deleted while still waiting to be shown must never reach the
     * screen.
     */
    private fun applyLiveChatModeration(page: LiveChatPage) {
        if (page.removedIds.isEmpty() &&
            page.removedAuthorIds.isEmpty() &&
            page.replacements.isEmpty()
        ) return

        fun survives(message: LiveChatMessage): Boolean =
            message.id !in page.removedIds &&
                message.author?.channelId?.let { it !in page.removedAuthorIds } != false

        pendingLiveChat.retainAll { survives(it) }
        _liveChatMessages.update { current ->
            current.mapNotNull { message ->
                if (!survives(message)) null else page.replacements[message.id] ?: message
            }
        }
    }

    /**
     * Keep the dedupe set from growing for the lifetime of a long stream. Only
     * ids that are still visible or queued can be re-delivered in a way that
     * matters.
     */
    private fun trimSeenLiveChatIds() {
        if (seenLiveChatIds.size <= MAX_LIVE_CHAT_MESSAGES * 3) return
        val live = HashSet<String>(_liveChatMessages.value.size + pendingLiveChat.size)
        _liveChatMessages.value.forEach { live.add(it.id) }
        pendingLiveChat.forEach { live.add(it.id) }
        seenLiveChatIds.retainAll(live)
    }

    /**
     * Keep the concurrent viewer count fresh. The endpoint asks for a 5s tick,
     * which is far more often than a phone needs, so this runs on
     * [LIVE_METADATA_POLL_MS] instead.
     */
    private fun startLiveMetadataPolling(videoId: String) {
        liveMetadataJob?.cancel()
        liveMetadataJob = viewModelScope.launch {
            while (true) {
                val metadata = youtubeRepository.getLiveMetadata(videoId)
                if (_currentVideo.value?.videoId != videoId) return@launch
                metadata?.viewerCountText?.let { _liveViewerCount.value = it }
                kotlinx.coroutines.delay(LIVE_METADATA_POLL_MS)
            }
        }
    }

    /** Stop both live polls. Safe to call when nothing is live. */
    fun stopLivePolling() {
        liveChatJob?.cancel()
        liveChatJob = null
        liveMetadataJob?.cancel()
        liveMetadataJob = null
    }

    /**
     * Post a message to the live chat.
     *
     * The response echoes the accepted message back, so it goes on screen at
     * once instead of waiting up to a poll interval to come round - it is marked
     * seen, so the poll that eventually redelivers it is a no-op.
     *
     * [onFailure] reports a rejection (slow mode, a word filter, a ban) so the
     * composer can put the text back rather than silently swallowing it.
     */
    fun sendLiveChatMessage(text: String, onFailure: (String) -> Unit = {}) {
        val body = text.trim()
        if (body.isEmpty() || _isSendingLiveChat.value) return
        val params = _liveChatSendParams.value ?: return
        viewModelScope.launch {
            _isSendingLiveChat.value = true
            try {
                val result = youtubeRepository.sendLiveChatMessage(params, body)
                if (result.success) {
                    result.echo?.let { echo ->
                        if (seenLiveChatIds.add(echo.id)) appendLiveChatMessages(listOf(echo))
                    }
                } else {
                    onFailure(result.error ?: "Message not sent")
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayerVM", "Failed to send live chat message", e)
                onFailure("Message not sent")
            } finally {
                _isSendingLiveChat.value = false
            }
        }
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

    /**
     * Subscribe/unsubscribe to the current video's channel, routed by the
     * subscribe-target setting (see [com.ivor.ivormusic.data.SubscriptionActions]).
     * Optimistic with rollback on a failed remote write.
     */
    fun toggleSubscribe() {
        val current = _engagement.value ?: return
        val channelId = current.channelId ?: return
        val video = _currentVideo.value
        val subscribe = !isSubscribedToChannel.value

        // Only the account half is optimistic here; the local half is a
        // synchronous write whose process-wide flow already drives the button.
        // A local-only subscribe must leave the account flag alone, or the
        // next unsubscribe would fire a pointless network call.
        val writesRemote = subscriptionActions.resolveTarget() != SubscriptionStore.LOCAL
        _engagement.value = current.copy(
            isSubscribed = when {
                !subscribe -> false
                writesRemote -> true
                else -> current.isSubscribed
            }
        )
        viewModelScope.launch {
            val ok = subscriptionActions.setSubscribed(
                channel = LocalSubscription(
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

        /**
         * One skip step, shared by the double-tap gesture on the player, the
         * media notification and the Picture-in-Picture controls.
         */
        const val SEEK_STEP_MS = 10_000L

        /** Silent re-prepare attempts before a renderer error reaches the UI. */
        private const val MAX_RENDERER_RETRIES = 2

        /**
         * Silent re-resolve attempts before a source error reaches the UI. One
         * is enough: the first pass already remints a rejected visitorData and
         * fetches brand-new URLs, so a second failure means the video really is
         * unplayable and the user should get the overlay instead of a spinner.
         */
        private const val MAX_SOURCE_RETRIES = 1

        /**
         * Chat backlog kept in memory. Matches the cap YouTube declares in the
         * response itself (itemList.liveChatItemListRenderer.maxItemsToDisplay),
         * so a stream left open for hours cannot grow the list without bound.
         */
        private const val MAX_LIVE_CHAT_MESSAGES = 250

        /** Consecutive failed polls tolerated before the chat is given up on. */
        private const val MAX_LIVE_CHAT_POLL_FAILURES = 3
        private const val LIVE_CHAT_RETRY_DELAY_MS = 5_000L

        /**
         * Bounds on the server's requested poll interval. Live streams ask for
         * 10s; a replay can ask for much less, and a misparse must not turn into
         * a hot loop.
         */
        private const val LIVE_CHAT_MIN_WINDOW_MS = 2_000L
        private const val LIVE_CHAT_MAX_WINDOW_MS = 30_000L

        /**
         * Pacing bounds for releasing a batch. The floor keeps a burst from
         * animating one message per frame; the ceiling stops a sparse batch from
         * sitting in the queue for seconds when there is nothing behind it.
         */
        private const val LIVE_CHAT_MIN_GAP_MS = 45L
        private const val LIVE_CHAT_MAX_GAP_MS = 700L

        /** Poll interval of the drain loop while the queue is empty. */
        private const val LIVE_CHAT_DRAIN_IDLE_MS = 120L

        /**
         * Viewer-count refresh. The endpoint asks for 5s; a phone showing one
         * number does not need it that often.
         */
        private const val LIVE_METADATA_POLL_MS = 25_000L
    }

    override fun onCleared() {
        super.onCleared()
        // Remove quality change listener to prevent leaks
        qualityChangeListener?.let { _exoPlayer?.removeListener(it) }
        qualityChangeListener = null
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = null
        stopLivePolling()
        // Before the release below, not after: stop() drops the MediaSession
        // synchronously on this thread, and a session outliving the player it
        // wraps crashes the next time Media3 reads state off it.
        com.ivor.ivormusic.service.VideoPlaybackService.stop(context)
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
