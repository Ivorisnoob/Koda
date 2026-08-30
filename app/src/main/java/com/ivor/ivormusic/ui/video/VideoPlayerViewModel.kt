package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.util.KLog

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
import androidx.media3.cast.CastPlayer
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ivor.ivormusic.data.CaptionBackground
import com.ivor.ivormusic.data.CaptionTextColor
import com.ivor.ivormusic.data.CaptionTrack
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.CommentItem
import com.ivor.ivormusic.data.DownloadedVideo
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
import com.ivor.ivormusic.data.VideoPlaybackCacheStream
import com.ivor.ivormusic.data.VideoSeekPreview
import com.ivor.ivormusic.data.VttCue
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.videoPlaybackCacheKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class VideoEndAction {
    STOP,
    NEXT_IN_QUEUE,
    NEXT_RELATED
}

/**
 * Resolves one completed video into exactly one outcome. Keeping this decision
 * free of ExoPlayer makes the precedence explicit and unit-testable: autoplay
 * is the master gate, a user-chosen playlist beats recommendations, and PiP
 * may continue that playlist but never wanders into related videos.
 */
internal fun resolveVideoEndAction(
    autoplayEnabled: Boolean,
    queueHasNext: Boolean,
    isInPipMode: Boolean,
    hasRelatedVideo: Boolean
): VideoEndAction = when {
    !autoplayEnabled -> VideoEndAction.STOP
    queueHasNext -> VideoEndAction.NEXT_IN_QUEUE
    !isInPipMode && hasRelatedVideo -> VideoEndAction.NEXT_RELATED
    else -> VideoEndAction.STOP
}

@UnstableApi
class VideoPlayerViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val youtubeRepository = YouTubeRepository(context)
    private val themePreferences = ThemePreferences(context)
    private val videoHistoryRepository = com.ivor.ivormusic.data.VideoHistoryRepository(context)

    // Device-held video playlists. Its state is process-wide, so a save taken
    // here shows up in the Library tab's own HomeViewModel without either of
    // them knowing about the other.
    private val localVideoPlaylistsRepository =
        com.ivor.ivormusic.data.LocalVideoPlaylistsRepository(context)

    // Playback session snapshots for resume-on-reopen, the video-mode
    // counterpart of PlayerViewModel's playbackSessionRepository. See
    // restoreVideoSession() / saveVideoPlaybackSession() below.
    private val videoPlaybackSessionRepository =
        com.ivor.ivormusic.data.VideoPlaybackSessionRepository(context)
    private var lastSessionSaveAt = 0L
    private var sessionPersistenceJob: kotlinx.coroutines.Job? = null

    // Player Instance
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    // ---------------- Chromecast ----------------

    /**
     * Cast session plumbing: route discovery, connect/disconnect and the two
     * receiver-side events CastPlayer cannot express. See [VideoCastManager].
     */
    private val castManager = VideoCastManager(context)

    /** Whether this device can cast at all (Play services present). */
    val castAvailable: Boolean get() = castManager.available

    /** Receivers currently visible on the network, for the device sheet. */
    val castReceivers: StateFlow<List<CastRoute>> = castManager.receivers

    /** Friendly name of the connected receiver, null when not casting. */
    val castDeviceName: StateFlow<String?> = castManager.deviceName

    /** True while a connect attempt to a receiver is in flight. */
    val isCastConnecting: StateFlow<Boolean> = castManager.isConnecting

    /**
     * True while the video is on a Chromecast. The master switch for the
     * active-player split below: every transport call routes by it, so nothing
     * in the UI needs to know which pipeline is actually producing the media.
     */
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    /**
     * The receiver-side player. Non-null exactly while [_isCasting] is true;
     * released - never left dangling - on disconnect, loss or close.
     */
    private var _castPlayer: CastPlayer? = null

    /**
     * The player transport commands go to.
     *
     * While casting this is the [CastPlayer]: play/pause, seek, speed, repeat
     * and even end-of-queue autoplay are Player operations, and Media3's cast
     * facade implements them against the receiver. Everything that is
     * ExoPlayer-specific (media sources with merged audio, track-selection
     * caps, surface rendering) stays guarded behind [_castPlayer] checks
     * rather than being abstracted away, because pretending both players are
     * identical is how silent no-ops happen.
     */
    private fun activePlayer(): Player? = _castPlayer ?: _exoPlayer

    // State
    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo

    /** True when ExoPlayer is reading a completed device download, not YouTube. */
    private val _isLocalPlayback = MutableStateFlow(false)
    val isLocalPlayback: StateFlow<Boolean> = _isLocalPlayback.asStateFlow()
    private var localDownloadsById: Map<String, DownloadedVideo> = emptyMap()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    // Playback progress. Polled rather than pushed, because ExoPlayer has no
    // position callback - something has to tick - but it belongs here with the
    // rest of the player state rather than in a composable. The seek bar, the
    // chapter chip, the timed-comments overlay and the chapter list all read
    // it, and a loop living in one composable meant every other reader either
    // duplicated the poll or did without.
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _bufferedProgress = MutableStateFlow(0f)
    val bufferedProgress: StateFlow<Float> = _bufferedProgress

    private var progressJob: Job? = null

    // Initial stream resolution and watch-next metadata belong to one specific
    // startVideo invocation. Cancellation handles the normal case; the
    // generation check is still required because NewPipe's blocking fetch can
    // finish after cancellation and because a forced retry can reuse the same
    // video id.
    private var streamLoadJob: Job? = null
    private var watchNextJob: Job? = null
    private var videoLoadGeneration = 0L

    // A cold-process restore initially rebuilds only the visible player state.
    // Keeping the resume point here avoids preparing an ExoPlayer source (and
    // therefore buffering a video) before the user has asked playback to
    // resume. Nullable distinguishes a pending restore at 0 from no restore.
    private var deferredRestorePositionMs: Long? = null

    // Qualities and Related
    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities

    /**
     * What the quality menu may offer right now. While casting a live stream,
     * only Auto: the receiver runs its own ABR inside the manifest and has no
     * track-selector cap to pin a rung with, so every other row would be a
     * control that does nothing - worse than no control.
     */
    val selectableQualities: StateFlow<List<VideoQuality>> =
        combine(_availableQualities, _isCasting) { qualities, casting ->
            selectableVideoQualities(qualities, casting)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentQuality = MutableStateFlow<VideoQuality?>(null)
    val currentQuality: StateFlow<VideoQuality?> = _currentQuality

    private val notInterestedRepository =
        com.ivor.ivormusic.data.NotInterestedRepository(context)

    /**
     * The playlist being watched through, or null when this is a one-off video.
     *
     * See [com.ivor.ivormusic.data.VideoQueue]. Set only by [playQueue]; every
     * other entry point into playback clears it, because leaving the playlist
     * is exactly what tapping a related video or a search result means.
     */
    private val _queue = MutableStateFlow<com.ivor.ivormusic.data.VideoQueue?>(null)
    val queue: StateFlow<com.ivor.ivormusic.data.VideoQueue?> = _queue.asStateFlow()

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

    /** Local hide plus best-effort account propagation - see NotInterestedActions. */
    private val notInterestedActions =
        com.ivor.ivormusic.data.NotInterestedActions(notInterestedRepository, youtubeRepository)

    /**
     * Re-read the playing video's account state when the profile changes.
     *
     * Like, dislike, subscribe and the comment list are all the *account's*
     * view of this video, so after a switch they describe somebody else.
     * Playback itself is left running - the stream is already resolved, and
     * stopping it because the user checked another account would be a bad
     * trade - but everything account-shaped around it is refetched.
     *
     * Related videos come back with the engagement refetch, since both are
     * parsed from the same watch-next response.
     */
    private fun observeProfileSwitches() {
        viewModelScope.launch {
            com.ivor.ivormusic.data.ProfileManager(context)
                .activeProfileId
                .drop(1)
                .distinctUntilChanged()
                .collect {
                    youtubeRepository.clearSessionScopedInstanceCaches()
                    _engagement.value = null
                    _comments.value = emptyList()
                    val playing = _currentVideo.value ?: return@collect
                    val refreshed = runCatching {
                        youtubeRepository.getWatchNextData(playing.videoId, playing)
                    }.getOrNull() ?: return@collect
                    // The user can have moved on while this was in flight.
                    if (_currentVideo.value?.videoId != playing.videoId) return@collect
                    _engagement.value = refreshed.engagement
                    if (refreshed.relatedVideos.isNotEmpty()) {
                        _relatedVideos.value = refreshed.relatedVideos
                    }
                }
        }
    }

    /** Hide one video from every recommendation feed. */
    fun markNotInterested(video: VideoItem) =
        notInterestedActions.hideVideo(video, viewModelScope)

    /** Stop recommending anything from this video's channel. */
    fun blockChannelFor(video: VideoItem) =
        notInterestedActions.blockChannel(video, viewModelScope)

    // Chapter markers for the current video (empty when the video has none)
    private val _chapters = MutableStateFlow<List<com.ivor.ivormusic.data.VideoChapter>>(emptyList())
    val chapters: StateFlow<List<com.ivor.ivormusic.data.VideoChapter>> = _chapters.asStateFlow()

    // YouTube storyboard sprite harvested during stream extraction, or a local
    // URI whose frames can be decoded directly for downloaded playback. Null
    // for live streams and videos that do not expose either source.
    private val _seekPreview = MutableStateFlow<VideoSeekPreview?>(null)
    val seekPreview: StateFlow<VideoSeekPreview?> = _seekPreview.asStateFlow()

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

    val captionTextSize: StateFlow<Float> = themePreferences.captionTextSize
    val captionTextColor: StateFlow<CaptionTextColor> = themePreferences.captionTextColor
    val captionBackground: StateFlow<CaptionBackground> = themePreferences.captionBackground
    val captionsEnabled: StateFlow<Boolean> = themePreferences.captionsEnabled

    private var captionCuesJob: Job? = null

    fun setCaptionTextSize(size: Float) = themePreferences.setCaptionTextSize(size)

    fun setCaptionTextColor(color: CaptionTextColor) = themePreferences.setCaptionTextColor(color)

    fun setCaptionBackground(background: CaptionBackground) =
        themePreferences.setCaptionBackground(background)

    // ---------------- Picture-in-Picture ----------------

    // Width/height of the video being played, so the PiP window and the watch
    // page's video box both take the shape of the video instead of a hardcoded
    // 16:9 that letterboxes everything else.
    //
    // Seeded from the stream dimensions when the quality list arrives and
    // refined by onVideoSizeChanged once a frame has decoded, so it is usually
    // known before the box is laid out. Null only for a source that declared no
    // dimensions, where consumers fall back to 16:9.
    private val _videoAspectRatio = MutableStateFlow<Float?>(null)
    val videoAspectRatio: StateFlow<Float?> = _videoAspectRatio.asStateFlow()

    // Where the video surface sits on screen, in window coordinates. Handed to
    // PictureInPictureParams as the source rect hint so the system animates the
    // PiP window out of the video itself; without it the transition scales down
    // the entire activity window, app chrome and all.
    private val _videoSurfaceBounds = MutableStateFlow<android.graphics.Rect?>(null)
    val videoSurfaceBounds: StateFlow<android.graphics.Rect?> = _videoSurfaceBounds.asStateFlow()

    // Kept separate from the expanded/fullscreen surface bounds. Reusing one
    // rectangle across both layouts briefly leaves the expanded window bounds
    // attached to a collapsed player, which lets Android snapshot the whole UI
    // when Home is pressed during that hand-off.
    private val _miniVideoSurfaceBounds = MutableStateFlow<android.graphics.Rect?>(null)
    val miniVideoSurfaceBounds: StateFlow<android.graphics.Rect?> =
        _miniVideoSurfaceBounds.asStateFlow()

    fun setVideoSurfaceBounds(bounds: android.graphics.Rect?) {
        _videoSurfaceBounds.value = bounds
    }

    fun setMiniVideoSurfaceBounds(bounds: android.graphics.Rect?) {
        _miniVideoSurfaceBounds.value = bounds
    }

    // True while the app is in system Picture-in-Picture. Set by the
    // composition so the STATE_ENDED auto-play can stand down: advancing
    // to the next video while in PiP means the user returns to a video
    // they did not put there.
    private var _isInPipMode = false

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode = inPip
    }

    // End-of-video behavior sticks across videos and app restarts. Autoplay is
    // the master gate: when it is off, a stored repeat flag is ignored and
    // normalized away during init so the UI and ExoPlayer cannot disagree.
    private val _isAutoplayEnabled =
        MutableStateFlow(themePreferences.isVideoAutoplayEnabled())
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    private val _isLooping = MutableStateFlow(
        _isAutoplayEnabled.value && themePreferences.isVideoRepeatEnabled()
    )
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

    // Videos skipped in a row because they would not play at all. Playlists
    // collect deleted, private and region-blocked entries over time, and one of
    // those must not end the run - but a network that has gone away fails every
    // video too, and racing to the end of the playlist on that is worse than
    // stopping. Bounded, and reset the moment anything actually plays.
    private var queueErrorSkipCount = 0

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

    // The account's own; the device's are merged in by [videoPlaylists] below.
    private val _videoPlaylists = MutableStateFlow<List<com.ivor.ivormusic.data.VideoPlaylist>>(emptyList())

    /**
     * Everything the save sheet can save into, device playlists first. Mirrors
     * `HomeViewModel.videoPlaylists`, because the sheet is opened from both and
     * must offer the same targets either way.
     */
    val videoPlaylists: StateFlow<List<com.ivor.ivormusic.data.VideoPlaylist>> = combine(
        localVideoPlaylistsRepository.playlists,
        _videoPlaylists
    ) { local, account ->
        local.map { it.toVideoPlaylist() } + account
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isVideoPlaylistsLoading = MutableStateFlow(false)
    val isVideoPlaylistsLoading: StateFlow<Boolean> = _isVideoPlaylistsLoading.asStateFlow()

    /**
     * The device's playlists with their videos attached, for marking the ones a
     * video is already in. Mirrors `HomeViewModel.localVideoPlaylists`; see
     * there for why the account's are not covered.
     */
    val localVideoPlaylists: StateFlow<List<com.ivor.ivormusic.data.LocalVideoPlaylist>> =
        localVideoPlaylistsRepository.playlists

    /**
     * Stream data source factory: CacheDataSource keeps bytes already fetched
     * for VOD seeking/replay, while ChunkedStreamDataSource supplies cache
     * misses as bounded googlevideo ranges (open-ended requests are paced to
     * the media bitrate) with the User-Agent matching the URL's issuing client.
     *
     * Declared before [init] on purpose: the ExoPlayer built there installs it
     * as the player-wide MediaSource factory, so a lazy declared further down
     * the class would still be an uninitialised delegate at that point.
     */
    private val streamDataSourceFactory = run {
        // MusicService used to be the cache's only initializer. Video owns a
        // separate player and can run without that service, so it must make the
        // shared process cache available before constructing its data source.
        CacheManager.initialize(context, themePreferences.maxCacheSizeMb.value)
        CacheManager.createPlaybackDataSourceFactory(context) {
            themePreferences.cacheEnabled.value
        }
    }

    init {
        observeProfileSwitches()

        if (!_isAutoplayEnabled.value && themePreferences.isVideoRepeatEnabled()) {
            themePreferences.setVideoRepeatEnabled(false)
        }

        // Near-instant first frame without retaining several minutes of video
        // samples in the app heap. The disk cache preserves fetched compressed
        // bytes for later seeks; decoded surfaces/codecs still consume memory,
        // so a bounded one-minute read-ahead and 64MB allocator ceiling leave
        // headroom for artwork, Compose and the decoder on lower-memory phones.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,  // keep playback resilient through short network dips
                60_000,  // never retain five minutes of long-form video
                1_000,   // buffer before first frame
                2_500    // buffer after a rebuffer
            )
            .setTargetBufferBytes(64 * 1024 * 1024)
            .setBackBuffer(15_000, true)
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
            attachPlaybackListener(this)
        }

        // Session listeners, remote finish/failure hooks and adoption of a
        // cast session that already exists when this ViewModel is built.
        attachCastObservation()

        // The position poll runs for as long as the player exists. Started here
        // rather than per video, so nothing has to remember to restart it.
        startProgressUpdates()

        // Warm the visitorData cache so the first playback doesn't pay for
        // the youtube.com bootstrap download on its critical path.
        viewModelScope.launch { youtubeRepository.prefetchVisitorData() }

        // Only meaningful the moment this ViewModel is freshly (re)created,
        // which is exactly the guard below - restoreVideoSession() itself
        // does nothing once something is already playing.
        restoreVideoSession()
    }

    /**
     * The playback listener both players carry. The local ExoPlayer gets it at
     * build time; the CastPlayer when a session starts. Keeping one
     * implementation means cast playback drives the same buffering spinner,
     * progress poll, retry budgets and end-of-video autoplay the local path
     * does - with two differences below.
     */
    private fun attachPlaybackListener(player: Player) {
        player.addListener(object : Player.Listener {
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
                    queueErrorSkipCount = 0
                }
                if (playbackState == Player.STATE_ENDED) {
                    // CastPlayer maps a finished broadcast to STATE_IDLE, not
                    // STATE_ENDED - the receiver's finish arrives through
                    // VideoCastManager.onRemoteFinished instead. The guard
                    // keeps the two paths from ever double-advancing the queue.
                    if (_isCasting.value) return

                    // Repeat-one normally prevents STATE_ENDED entirely.
                    // The guard keeps a transient player/state mismatch
                    // from advancing away from a video meant to loop.
                    if (_isLooping.value) return
                    viewModelScope.launch { handlePlaybackEnded() }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // CastPlayer never reports errors at all (its getPlayerError is
                // hardcoded null); receiver-side failures arrive through
                // recoverFromCastFailure(). Everything below is the local
                // player's story.

                // A renderer/decoder failure is not a broken stream: the
                // codec lost its surface or was reclaimed. Re-prepare in
                // place (position is kept) instead of dead-ending the
                // player on an error overlay the user cannot dismiss.
                if (isTransientRendererError(error) && rendererRetryCount < MAX_RENDERER_RETRIES) {
                    rendererRetryCount++
                    KLog.w(
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
                if (!_isLocalPlayback.value &&
                    isRecoverableSourceError(error) &&
                    sourceRetryCount < MAX_SOURCE_RETRIES
                ) {
                    sourceRetryCount++
                    KLog.w(
                        "VideoPlayerVM",
                        "Source error (attempt $sourceRetryCount/$MAX_SOURCE_RETRIES); re-resolving stream",
                        error
                    )
                    recoverFromSourceError(error)
                    return
                }

                // Both retry budgets are spent, so this video is genuinely
                // unplayable. Inside a playlist that is almost always one
                // deleted or private entry among many, and stopping the run
                // on it is not what the user asked for by opening the
                // playlist - so step over it, up to the bounded number of
                // times above.
                val activeQueue = _queue.value
                if (activeQueue != null &&
                    activeQueue.hasNext &&
                    queueErrorSkipCount < MAX_QUEUE_ERROR_SKIPS
                ) {
                    queueErrorSkipCount++
                    KLog.w(
                        "VideoPlayerVM",
                        "Unplayable video in the queue at ${activeQueue.index} " +
                            "(skip $queueErrorSkipCount/$MAX_QUEUE_ERROR_SKIPS); moving on",
                        error
                    )
                    viewModelScope.launch { playQueueIndex(activeQueue.index + 1) }
                    return
                }

                _playbackError.value = error
                _isBuffering.value = false
            }
        })
    }

    /**
     * One decision for "the video finished", shared by the local player's
     * STATE_ENDED and the receiver's IDLE_REASON_FINISHED: autoplay is the
     * master gate, a user-chosen playlist beats recommendations, and PiP may
     * continue that playlist but never wanders into related videos. Routing
     * the cast case through the same function is what makes casting a playlist
     * advance exactly like watching it locally.
     */
    private suspend fun handlePlaybackEnded() {
        val activeQueue = _queue.value
        val nextRelated = relatedVideos.value.firstOrNull()
        when (
            resolveVideoEndAction(
                autoplayEnabled = _isAutoplayEnabled.value,
                queueHasNext = activeQueue?.hasNext == true,
                isInPipMode = _isInPipMode,
                hasRelatedVideo = nextRelated != null
            )
        ) {
            VideoEndAction.STOP -> {
                // STATE_ENDED is not always exposed as paused by media
                // controls. Clear playWhenReady so the UI, PiP and
                // notification all agree it stopped.
                activePlayer()?.pause()
            }

            VideoEndAction.NEXT_IN_QUEUE -> {
                if (activeQueue != null && activePlayer() != null) {
                    playQueueIndex(activeQueue.index + 1)
                }
            }

            VideoEndAction.NEXT_RELATED -> {
                // Use the filtered list: autoplaying something the viewer
                // marked not interested is worse than stopping.
                if (nextRelated != null && activePlayer() != null) {
                    playVideo(nextRelated)
                }
            }
        }
    }

    // ---------------- Chromecast: session plumbing ----------------

    private fun attachCastObservation() {
        castManager.beginObservation()
        castManager.onRemoteFinished = {
            viewModelScope.launch { handlePlaybackEnded() }
        }
        castManager.onRemoteFailed = { recoverFromCastFailure() }
        castManager.onSessionLost = { position -> resumeAfterCastLoss(position) }
        viewModelScope.launch {
            castManager.isSessionActive.collect { active ->
                // A session that appeared without this ViewModel starting it -
                // a framework reconnect after process death, or a receiver
                // this phone was already joined to - is adopted rather than
                // torn down: stopping someone's TV because they reopened the
                // app is not what reopening means.
                if (active && _castPlayer == null) adoptExistingCastSession()
            }
        }
    }

    /** Build the receiver-side player with Koda's load converter attached. */
    private fun newCastPlayer(): CastPlayer? {
        val player = castManager.createPlayer() ?: return null
        attachPlaybackListener(player)
        player.repeatMode =
            if (_isLooping.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        return player
    }

    /**
     * The user picked a receiver. Connect, then hand whatever is playing over
     * to it at the current position - the whole existing load pipeline reruns
     * with [_castPlayer] set, so stream resolution, quality choice, captions
     * and the notification all behave exactly as they do locally.
     */
    fun startCast(routeId: String) {
        if (!castManager.available) return
        viewModelScope.launch {
            val ok = castManager.connect(routeId)
            if (!ok || _castPlayer != null) return@launch

            val video = _currentVideo.value
            val player = newCastPlayer()
            if (player == null) {
                castManager.endSession(stopOnReceiver = true)
                return@launch
            }

            val localPos = deferredRestorePositionMs
                ?: _exoPlayer?.currentPosition?.coerceAtLeast(0L)
                ?: 0L
            val wasPlaying = _exoPlayer?.isPlaying == true
            // Pause the phone first: two devices playing the same audio for
            // even a second is worse than a beat of silence while the TV spins up.
            _exoPlayer?.pause()
            _castPlayer = player
            _isCasting.value = true

            if (video == null) {
                // Connected with nothing playing. Controls will route to the
                // receiver the moment the user picks something; there is
                // nothing to load now.
                return@launch
            }
            if (_isLocalPlayback.value) {
                // Offline files live on this device and cannot be fetched by a
                // receiver. Casting wins: the download stays on the shelf.
                leaveLocalPlayback()
            }
            startVideo(
                video,
                forceRestart = true,
                resumePositionMs = localPos.takeIf { it > 0 },
                resumePaused = !wasPlaying,
                expand = _isExpanded.value
            )
        }
    }

    /** Route discovery for the device sheet. Cheap to start, costly to leave on. */
    fun startCastDiscovery() = castManager.startDiscovery()

    fun stopCastDiscovery() = castManager.stopDiscovery()

    /** The user tapped Disconnect. The TV stops; the phone picks up where it was. */
    fun stopCasting() {        val position = _castPlayer?.currentPosition?.coerceAtLeast(0L)
            ?: castManager.remotePositionMs()
        val wasPlaying = _castPlayer?.isPlaying == true
        castManager.endSession(stopOnReceiver = true)
        releaseCastPlayer()
        _isCasting.value = false
        val video = _currentVideo.value ?: return

        // Fresh URLs for the local resume. Whatever the receiver had may be
        // client-bound or expired by the time the phone needs it again, and
        // replaying them is exactly what reresolveAndReload exists to avoid.
        _isLoading.value = true
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = viewModelScope.launch {
            if (!reresolveAndReload(video, playWhenReady = wasPlaying, seekToMs = position)) {
                _playbackError.value = Exception("Unable to resume playback")
                _isLoading.value = false
            }
        }
    }

    /**
     * The session died without this app asking (receiver power-off, network
     * loss, another sender taking over). Resume locally at the last position
     * the receiver reported, playing if it was playing.
     */
    private fun resumeAfterCastLoss(positionMs: Long) {
        releaseCastPlayer()
        _isCasting.value = false
        val video = _currentVideo.value ?: return

        _isLoading.value = true
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = viewModelScope.launch {
            val ok = reresolveAndReload(
                video,
                playWhenReady = true,
                seekToMs = positionMs.takeIf { it > 0 }
            )
            if (!ok && _currentVideo.value?.videoId == video.videoId) {
                _playbackError.value = Exception("Unable to resume playback")
                _isLoading.value = false
            }
        }
    }

    /**
     * A load or playback failure on the receiver. Same escalation ladder as
     * the local source-error path: one bounded re-resolve-and-reload, then
     * queue skipping, then the error overlay.
     */
    private fun recoverFromCastFailure() {
        if (!_isCasting.value) return
        if (sourceRetryCount < MAX_SOURCE_RETRIES) {
            sourceRetryCount++
            val video = _currentVideo.value ?: return
            KLog.w(
                "VideoPlayerVM",
                "Cast playback failed (attempt $sourceRetryCount/$MAX_SOURCE_RETRIES); re-resolving"
            )
            _isLoading.value = true
            sourceRecoveryJob?.cancel()
            sourceRecoveryJob = viewModelScope.launch {
                if (!reresolveAndReload(video)) {
                    _playbackError.value = Exception("Unable to cast this video")
                    _isLoading.value = false
                }
            }
            return
        }

        val activeQueue = _queue.value
        if (activeQueue != null && activeQueue.hasNext &&
            queueErrorSkipCount < MAX_QUEUE_ERROR_SKIPS
        ) {
            queueErrorSkipCount++
            viewModelScope.launch { playQueueIndex(activeQueue.index + 1) }
            return
        }

        _playbackError.value = Exception("Unable to play this video on the cast device")
        _isBuffering.value = false
    }

    /**
     * Join a cast session that already exists when this ViewModel is built -
     * the framework reconnects automatically after process death, so "reopen
     * the app" mid-cast has to land back on the TV, not on a phone that
     * forgot it was casting.
     */
    private fun adoptExistingCastSession() {
        val player = newCastPlayer() ?: return
        _castPlayer = player
        _isCasting.value = true

        val status = try {
            castManager.currentMediaStatus()
        } catch (_: Exception) {
            null
        }
        val remoteInfo = status?.mediaInfo
        if (remoteInfo != null) {
            // The receiver is already showing something. If it is not the
            // video this process knows about, the receiver wins: it is the
            // thing visibly playing in the room. Rebuild just enough of the
            // item for chrome, history gating and transport to make sense;
            // the queue does not survive the trip.
            val knownId = _currentVideo.value?.videoId
            if (remoteInfo.contentId != knownId) {
                val md = remoteInfo.metadata
                _currentVideo.value = VideoItem(
                    videoId = remoteInfo.contentId ?: "",
                    title = md?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)
                        ?: "",
                    channelName = md?.getString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST)
                        ?: "",
                    thumbnailUrl = md?.images?.firstOrNull()?.url?.toString(),
                    duration = ((remoteInfo.streamDuration.takeIf { it > 0 } ?: 0L) / 1000L),
                    viewCount = ""
                )
                _queue.value = null
                _isExpanded.value = false
            }
        } else if (_currentVideo.value != null && !_isLocalPlayback.value) {
            // Joined to an idle receiver while a video sits open here: move
            // that video onto the TV where the session says the user is
            // watching, paused or playing as it was on the phone.
            val video = _currentVideo.value!!
            val pos = deferredRestorePositionMs
                ?: _exoPlayer?.currentPosition?.coerceAtLeast(0L)
                ?: 0L
            val wasPlaying = _exoPlayer?.isPlaying == true
            _exoPlayer?.pause()
            startVideo(
                video,
                forceRestart = true,
                resumePositionMs = pos.takeIf { it > 0 },
                resumePaused = !wasPlaying,
                expand = _isExpanded.value
            )
        }
        // Republish to the system's media controls so lock-screen buttons
        // drive the receiver from a fresh process too.
        com.ivor.ivormusic.service.VideoPlaybackService.start(context, player)
    }

    private fun releaseCastPlayer() {
        _castPlayer?.let { player ->
            try {
                player.release()
            } catch (e: Exception) {
                KLog.w("VideoPlayerVM", "Releasing CastPlayer failed", e)
            }
        }
        _castPlayer = null
    }

    /**
     * Restore the video (or playlist) that was open when the process died,
     * paused at the position it was left at - the user decides when to jump
     * back in, same as PlayerViewModel.restoreLastSession() does for music.
     *
     * Never fires on a genuinely fresh app open: this only runs from init(),
     * and a ViewModel this early already has nothing playing, so the sole
     * purpose of the guard is protecting against playback starting while the
     * file read below is in flight.
     */
    private fun restoreVideoSession() {
        if (_currentVideo.value != null) return
        viewModelScope.launch {
            val session = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                videoPlaybackSessionRepository.load()
            }
            if (session == null) return@launch
            if (_currentVideo.value != null) return@launch

            val videos = session.videos.map { it.toVideoItem() }
            val target = videos.getOrNull(session.currentIndex) ?: return@launch

            if (videos.size > 1) {
                _queue.value = com.ivor.ivormusic.data.VideoQueue(
                    videos = videos,
                    index = session.currentIndex,
                    title = session.queueTitle
                        ?: com.ivor.ivormusic.data.VideoQueue.AD_HOC_TITLE,
                    playlistId = session.queuePlaylistId
                )
            }

            KLog.d(
                "VideoPlayerVM",
                "Restoring video session: ${videos.size} videos, " +
                    "index=${session.currentIndex}, pos=${session.positionMs}"
            )

            // Collapsed rather than expanded: the user is arriving fresh at
            // whatever screen they left, and popping straight into a
            // fullscreen player would be a jump-scare, not a convenience. The
            // mini player is enough of a "you left this running" cue. Rebuild
            // only its metadata and progress here; the stream is resolved on
            // the first Play action, so a bad prior session cannot create a
            // repeated launch-time memory/network failure.
            startVideo(
                target,
                resumePositionMs = session.positionMs,
                resumePaused = true,
                expand = false,
                deferStreamLoad = true
            )
        }
    }

    private fun VideoItem.toSnapshot() = com.ivor.ivormusic.data.PersistedVideoSnapshot(
        videoId = videoId,
        title = title,
        channelName = channelName,
        channelId = channelId,
        channelIconUrl = channelIconUrl,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        viewCount = viewCount
    )

    private fun com.ivor.ivormusic.data.PersistedVideoSnapshot.toVideoItem() = VideoItem(
        videoId = videoId,
        title = title,
        channelName = channelName,
        channelId = channelId,
        channelIconUrl = channelIconUrl,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        viewCount = viewCount
    )

    /**
     * Snapshot the current video (or queue), and position, for resume-on-
     * reopen. Skipped for a live broadcast: there is no "position" to return
     * to, and the manifest a resume would reopen has likely rolled off its
     * DVR window by the time the app is next opened.
     */
    private fun saveVideoPlaybackSession() {
        val video = _currentVideo.value ?: return
        // The persisted snapshot does not carry a durable local URI. Restoring
        // it as an online item would silently turn an offline session into a
        // network request, so downloaded playback deliberately has no resume-
        // after-process-death entry.
        if (_isLocalPlayback.value) return
        // Feed/search items often already know they are live before Phase 1
        // resolves the stream qualities. Checking both signals prevents the
        // progress poll from briefly persisting a broadcast as a resumable
        // VOD. startVideo() clears the prior snapshot once per broadcast.
        if (_isLive.value || video.isLive) return
        // Read off whichever player is producing the media: while casting the
        // position lives on the receiver, and a snapshot of the phone's stale
        // local position would resurrect the wrong moment on restore.
        val position = deferredRestorePositionMs
            ?: activePlayer()?.currentPosition?.coerceAtLeast(0L)
            ?: return
        val activeQueue = _queue.value

        val videos: List<com.ivor.ivormusic.data.PersistedVideoSnapshot>
        val index: Int
        val title: String?
        val playlistId: String?
        if (activeQueue != null) {
            videos = activeQueue.videos.map { it.toSnapshot() }
            index = activeQueue.index
            title = activeQueue.title
            playlistId = activeQueue.playlistId
        } else {
            videos = listOf(video.toSnapshot())
            index = 0
            title = null
            playlistId = null
        }

        enqueueSessionPersistence {
            videoPlaybackSessionRepository.save(videos, index, title, playlistId, position)
        }
    }

    /**
     * Keep snapshot writes and clears ordered. A progress checkpoint can still
     * be writing when the user closes the player or opens a live broadcast;
     * independent IO launches let that older save finish after the clear and
     * bring the supposedly removed session back.
     */
    private fun enqueueSessionPersistence(action: () -> Unit) {
        val previous = sessionPersistenceJob
        sessionPersistenceJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            previous?.join()
            action()
        }
    }

    private fun clearVideoPlaybackSession() {
        enqueueSessionPersistence { videoPlaybackSessionRepository.clear() }
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
        // Backgrounding is the natural checkpoint for a resume snapshot: it is
        // exactly the moment process death becomes possible, and it fires
        // whether or not the track was already suspended, so it belongs above
        // that guard rather than inside it.
        saveVideoPlaybackSession()
        // Nothing is decoding locally while casting - the receiver owns the
        // media, the phone has no surface to tear down, and suspending its
        // (idle) video track would be bookkeeping for nobody. Playback itself
        // continues server-side regardless of this process.
        if (_isCasting.value) return
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
     * Starts the position poll, replacing any run already going.
     *
     * Idempotent, so it is safe to call from wherever the player is (re)built.
     * The tick is cheap even while paused: an unchanged position writes the same
     * value to a [MutableStateFlow], which conflates it and emits nothing.
     */
    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                // The poll reads whichever player is producing the media. For
                // the CastPlayer this is the receiver's reported position,
                // refreshed over its own progress channel - the seek bar and
                // chapter chip keep working unchanged on a cast.
                activePlayer()?.let { player ->
                    // A non-positive duration means "not known yet" (and is the
                    // normal case for a live stream), so leave the last good
                    // values alone rather than dividing by it.
                    val duration = player.duration
                    if (duration > 0) {
                        val position = player.currentPosition.coerceAtLeast(0L)
                        _durationMs.value = duration
                        _positionMs.value = position
                        _progress.value = (position.toFloat() / duration).coerceIn(0f, 1f)
                        _bufferedProgress.value =
                            (player.bufferedPosition.toFloat() / duration).coerceIn(0f, 1f)

                        // Backstop for the case that never backgrounds: a
                        // process kill while the app is in the foreground
                        // (OOM, a crash elsewhere in the system) still gets a
                        // recent checkpoint rather than relying solely on
                        // onEnterBackground(). Throttled well below the poll
                        // rate - this is a disk write, the seek bar tick is not.
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastSessionSaveAt >= SESSION_SAVE_INTERVAL_MS) {
                            lastSessionSaveAt = now
                            saveVideoPlaybackSession()
                        }
                    }
                }
                delay(PROGRESS_POLL_MS)
            }
        }
    }

    /** Zeroes the progress state, so a new video does not inherit the old position. */
    private fun resetProgress() {
        _positionMs.value = 0L
        _durationMs.value = 0L
        _progress.value = 0f
        _bufferedProgress.value = 0f
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
        if (_isLocalPlayback.value) {
            startVideo(video, forceRestart = true)
            return
        }
        if (_exoPlayer == null) {
            // startVideo, not playVideo: retrying the video that failed is not
            // leaving the playlist it belongs to.
            startVideo(video, forceRestart = true)
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
     * Re-resolve [video]'s stream URLs and rebuild the media source, keeping
     * the quality the user was watching when it is still on offer.
     *
     * @param playWhenReady whether the rebuilt source should play. Recovery
     * from an error wants playback to continue; the post-cast resume honours
     * what the receiver was doing at hand-off instead of assuming.
     * @param seekToMs explicit target position. Null means "wherever the
     * active player is now" - which is only meaningful while that player still
     * exists, so the cast teardown paths pass a captured value rather than
     * trusting a released CastPlayer's last word.
     *
     * Returns false when resolution yielded nothing usable, so the caller can
     * surface an error; true also covers "the user moved on mid-flight", where
     * there is nothing left to recover.
     */
    private suspend fun reresolveAndReload(
        video: VideoItem,
        playWhenReady: Boolean = true,
        seekToMs: Long? = null
    ): Boolean {
        if (_exoPlayer == null && _castPlayer == null) return false
        val qualities = try {
            youtubeRepository.getVideoStreamQualities(video.videoId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // playVideo() cancels this job when the user moves on. Swallowing
            // that would let a dead recovery keep writing loading/error state
            // over the video that replaced it.
            throw e
        } catch (e: Exception) {
            KLog.w("VideoPlayerVM", "Re-resolve failed for ${video.videoId}", e)
            emptyList()
        }
        if (_currentVideo.value?.videoId != video.videoId) {
            _isLoading.value = false
            return true
        }
        if (qualities.isEmpty()) return false

        _availableQualities.value = qualities
        val previousLabel = _currentQuality.value?.resolution
        val chosen = if (_castPlayer != null) {
            pickDefaultCastReceiverQuality(qualities, previousLabel) ?: return false
        } else {
            localVideoQualityOptions(qualities)
                .firstOrNull { it.resolution == previousLabel }
                ?: pickDefaultQuality(qualities)
        }

        val player = activePlayer() ?: return false
        val position = seekToMs ?: player.currentPosition

        // Remove any existing quality change listener to prevent leaks
        qualityChangeListener?.let { player.removeListener(it) }

        loadQuality(chosen)

        // Wait for player to be ready before seeking to preserved position
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    player.seekTo(position.coerceAtLeast(0L))
                    if (playWhenReady) player.play()
                    player.removeListener(this)
                    qualityChangeListener = null
                }
            }
        }
        qualityChangeListener = listener
        player.addListener(listener)

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

    fun setAutoplayEnabled(enabled: Boolean) {
        _isAutoplayEnabled.value = enabled
        themePreferences.setVideoAutoplayEnabled(enabled)
        if (!enabled) setLooping(false)
    }

    fun toggleLooping() {
        val enableLoop = !_isLooping.value
        // A direct tap on Loop should work, not bounce against a disabled
        // master setting. It opts back into end-of-video behavior explicitly.
        if (enableLoop && !_isAutoplayEnabled.value) {
            setAutoplayEnabled(true)
        }
        setLooping(enableLoop)
    }

    private fun setLooping(enabled: Boolean) {
        _isLooping.value = enabled
        activePlayer()?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        themePreferences.setVideoRepeatEnabled(enabled)
    }

    /** Set the playback speed for the current video. Resets to 1x on video change. */
    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        // CastPlayer forwards this as a playback-rate change on the receiver.
        activePlayer()?.setPlaybackSpeed(speed)
    }

    /** Jump to a chapter's start position. */
    fun seekToChapter(chapter: com.ivor.ivormusic.data.VideoChapter) {
        activePlayer()?.seekTo(chapter.startMs)
    }

    /**
     * Play a video on its own, leaving whatever playlist was being watched.
     *
     * Every entry point that is not the queue itself comes through here - a
     * related video, a channel row, a search result, a shared link - and each
     * of them means "I am done with that playlist", so the queue is dropped.
     * Keeping it would leave the "Playing from" card and the next/previous
     * buttons pointing at a list the user has walked away from.
     *
     * @param forceRestart reload even when this video is already current, for
     * the retry path - tapping the same video otherwise only re-expands.
     */
    fun playVideo(video: VideoItem, forceRestart: Boolean = false) {
        leaveLocalPlayback()
        _queue.value = null
        // The queue this belonged to is gone, so restoring into the next one
        // would drop a video into a list it was never part of.
        lastQueueRemoval = null
        startVideo(video, forceRestart)
    }

    /**
     * Start watching [queue] at the position it carries.
     *
     * The one way a queue is established. Opening the video that is already
     * playing does not restart it - coming to a playlist that contains what is
     * on screen should attach the playlist, not throw away the position.
     */
    fun playQueue(queue: com.ivor.ivormusic.data.VideoQueue) {
        if (queue.videos.isEmpty()) return
        leaveLocalPlayback()
        val normalized = queue.at(queue.index)
        _queue.value = normalized
        lastQueueRemoval = null
        // A new playlist gets the full skip budget: whatever was wrong with the
        // last one has nothing to say about this one.
        queueErrorSkipCount = 0
        val target = normalized.current ?: return
        if (_currentVideo.value?.videoId == target.videoId) {
            _isExpanded.value = true
            return
        }
        startVideo(target)
    }

    /**
     * Play completed downloads through Koda's normal video surface. The queue
     * is a snapshot, matching online playlist playback, and every source stays
     * a content/file URI so advancing never performs network resolution.
     */
    fun playDownloadedVideos(videos: List<DownloadedVideo>, selected: DownloadedVideo) {
        val index = videos.indexOfFirst { it.id == selected.id }
        if (index < 0) return
        localDownloadsById = videos.associateBy { it.id }
        _isLocalPlayback.value = true
        val items = videos.map { it.toVideoItem() }
        _queue.value = com.ivor.ivormusic.data.VideoQueue(
            videos = items,
            index = index,
            title = "Downloads"
        )
        lastQueueRemoval = null
        queueErrorSkipCount = 0
        startVideo(items[index], forceRestart = true)
    }

    private fun leaveLocalPlayback() {
        _isLocalPlayback.value = false
        localDownloadsById = emptyMap()
    }

    private fun DownloadedVideo.toVideoItem() = VideoItem(
        videoId = id,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        duration = durationMs / 1000L,
        viewCount = ""
    )

    /**
     * Jump to a position in the active queue.
     *
     * Always a forced restart: a playlist can list the same video twice, the
     * index is what addresses the queue, and [startVideo]'s id check would
     * otherwise swallow the jump and leave the queue pointing somewhere the
     * player is not.
     */
    fun playQueueIndex(index: Int) {
        val active = _queue.value ?: return
        val target = active.videos.getOrNull(index) ?: return
        _queue.value = active.copy(index = index)
        startVideo(target, forceRestart = true)
    }

    // ---------------- Editing the queue ----------------

    /**
     * Reorder the queue.
     *
     * Nothing is asked of the player: unlike music, video mode holds one media
     * item at a time and reads the next one out of the queue when the current
     * one ends, so the order is only ever consulted at that moment. Moving the
     * playing entry therefore cannot interrupt it.
     */
    fun moveQueueItem(from: Int, to: Int) {
        val active = _queue.value ?: return
        _queue.value = active.moved(from, to)
    }

    /**
     * Drop an entry. Refused for the playing entry and for the last one left;
     * see [com.ivor.ivormusic.data.VideoQueue.removedAt].
     */
    fun removeQueueItem(at: Int) {
        val active = _queue.value ?: return
        val video = active.videos.getOrNull(at) ?: return
        val next = active.removedAt(at) ?: return
        lastQueueRemoval = QueueRemoval(video, at)
        _queue.value = next
    }

    /** A video just taken out of the queue, kept so the snackbar can put it back. */
    data class QueueRemoval(val video: VideoItem, val at: Int)

    private var lastQueueRemoval: QueueRemoval? = null

    /** Put the last removed video back where it was. */
    fun undoQueueRemoval() {
        val removal = lastQueueRemoval ?: return
        lastQueueRemoval = null
        val active = _queue.value ?: return
        _queue.value = active.withInserted(listOf(removal.video), removal.at)
    }

    /**
     * Add [video] to the queue, either straight after what is playing or at the
     * end.
     *
     * With no queue yet, one is started from the video already playing so the
     * addition has something to be *after* - which is what makes this work from
     * a feed or from search, where there is no playlist in sight. With nothing
     * playing at all there is no "next", so it just plays.
     */
    fun enqueueVideo(video: VideoItem, playNext: Boolean) {
        val active = _queue.value
        if (active == null) {
            val current = _currentVideo.value
            if (current == null) {
                playVideo(video)
                return
            }
            _queue.value = com.ivor.ivormusic.data.VideoQueue.adHoc(current, listOf(video))
            return
        }
        _queue.value = if (playNext) {
            active.withPlayingNext(listOf(video))
        } else {
            active.withAppended(listOf(video))
        }
    }

    /** Next video in the playlist. No-op at the end of it. */
    fun playNextInQueue() {
        val active = _queue.value ?: return
        if (active.hasNext) playQueueIndex(active.index + 1)
    }

    /** Previous video in the playlist. No-op at the start of it. */
    fun playPreviousInQueue() {
        val active = _queue.value ?: return
        if (active.hasPrevious) playQueueIndex(active.index - 1)
    }

    /**
     * @param resumePositionMs set by [restoreVideoSession] and the cast
     * hand-off: seek here once the stream is ready instead of starting from
     * zero.
     * @param resumePaused whether a [resumePositionMs] seek lands paused. True
     * for the cold-process restore - the user decides when to jump back in -
     * false for the cast hand-off, where the phone was playing and the TV
     * should simply carry on.
     * @param expand false only for a cold-process restore, where popping
     * straight into a fullscreen player would be a jump-scare rather than the
     * "you left this running" cue a mini player gives.
     * @param deferStreamLoad cold-restore-only path which reconstructs player
     * chrome without resolving or preparing media until the user presses Play.
     */
    private fun startVideo(
        video: VideoItem,
        forceRestart: Boolean = false,
        resumePositionMs: Long? = null,
        resumePaused: Boolean = false,
        expand: Boolean = true,
        deferStreamLoad: Boolean = false
    ) {
        if (!forceRestart && _currentVideo.value?.videoId == video.videoId) {
            // Already playing this video, just expand
            _isExpanded.value = true
            return
        }

        val loadGeneration = ++videoLoadGeneration
        streamLoadJob?.cancel()
        watchNextJob?.cancel()
        if (!deferStreamLoad) deferredRestorePositionMs = null

        // Decide from the item, not merely from the queue that introduced it.
        // This keeps ad-hoc online items added after an offline download from
        // being mislabeled or having their network features suppressed.
        val localDownload = localDownloadsById[video.videoId]
        _isLocalPlayback.value = localDownload != null

        _currentVideo.value = video
        if (video.isLive) {
            // Live broadcasts have no stable resume position. Clear the VOD
            // watched before this one so it is not resurrected if the process
            // dies while the broadcast is open.
            clearVideoPlaybackSession()
        }
        _isExpanded.value = expand
        _isLoading.value = true
        // Zero the progress for the new video rather than letting the previous
        // one's position sit in the seek bar until the first poll lands.
        resetProgress()
        _availableQualities.value = emptyList()
        _currentQuality.value = null
        _relatedVideos.value = emptyList() // Clear previous related
        _chapters.value = emptyList() // Clear previous chapters
        _seekPreview.value = null // Never show a frame from the previous video
        _captionTracks.value = emptyList() // Clear previous caption tracks
        _selectedCaption.value = null // Re-applied below when captions are persistently on
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
        // Carrying the previous video's shape over would size the watch page's
        // box for the wrong video until the first frame of this one decodes -
        // the same snap the parse-time read exists to avoid, just one video
        // late. 16:9 is the right thing to show while the shape is unknown.
        _videoAspectRatio.value = null
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
        activePlayer()?.setPlaybackSpeed(1f)

        if (deferStreamLoad) {
            val position = resumePositionMs?.coerceAtLeast(0L) ?: 0L
            val duration = (video.duration.coerceAtLeast(0L) * 1_000L)
            deferredRestorePositionMs = position
            _positionMs.value = position
            _durationMs.value = duration
            _progress.value = if (duration > 0L) {
                (position.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            _bufferedProgress.value = 0f
            _isPlaying.value = false
            _isBuffering.value = false
            _isLoading.value = false
            return
        }

        // Captions that were on for the last video stay on for this one: the
        // track list is fetched up front rather than waiting for a CC tap.
        if (themePreferences.isCaptionsEnabled() && !_isLocalPlayback.value) {
            ensureCaptionsLoaded()
        }

        // Publish to the system's media controls. Started here, from a user tap,
        // because a foreground service may not be started from the background;
        // repeat calls once it is up are no-ops. The session wraps whichever
        // player is producing the media, so lock-screen buttons drive the TV
        // while casting.
        activePlayer()?.let {
            com.ivor.ivormusic.service.VideoPlaybackService.start(context, it)
        }

        if (localDownload != null) {
            // Offline files cannot be cast: the receiver has no access to this
            // device's storage. An explicit tap on a download while connected
            // means "play it here", so the session ends (stopping the TV) and
            // playback continues on the phone - predictable, not silent.
            if (_castPlayer != null) {
                castManager.endSession(stopOnReceiver = true)
                releaseCastPlayer()
                _isCasting.value = false
            }
            playbackReportJob?.cancel()
            clearVideoPlaybackSession()
            try {
                _exoPlayer?.stop()
                _exoPlayer?.clearMediaItems()
                val offlineQuality = VideoQuality(
                    resolution = localDownload.quality?.let { "$it • Offline" } ?: "Offline",
                    url = localDownload.uri.toString(),
                    format = "video/mp4"
                )
                _availableQualities.value = listOf(offlineQuality)
                _currentQuality.value = offlineQuality
                _seekPreview.value = VideoSeekPreview.local(localDownload.uri.toString())
                _exoPlayer?.setMediaItem(nowPlayingMediaItem(localDownload.uri.toString()))
                _exoPlayer?.prepare()
                if (resumePositionMs != null && resumePositionMs > 0) {
                    _exoPlayer?.seekTo(resumePositionMs)
                }
                if (resumePaused) {
                    _exoPlayer?.pause()
                } else {
                    _exoPlayer?.play()
                }
                _isLoading.value = false
            } catch (e: Exception) {
                _playbackError.value = e
                _isLoading.value = false
            }
            return
        }

        // ========== PHASE 1: START PLAYBACK ASAP (fast) ==========
        // Uses lightweight getVideoStreamQualities() which ONLY fetches stream URLs
        streamLoadJob = viewModelScope.launch {
            try {
                if (!isCurrentVideoLoad(video.videoId, loadGeneration)) return@launch
                // While casting the local player is left exactly as it was -
                // paused, holding nothing that matters - because disconnecting
                // rebuilds its source from fresh URLs anyway.
                if (_castPlayer == null) {
                    _exoPlayer?.stop()
                    _exoPlayer?.clearMediaItems()
                }
                
                // NewPipe's fetchPage() is blocking. A coroutine withTimeout
                // cannot interrupt it; when extraction took 17-21 seconds it
                // waited for valid qualities and then discarded them as timed
                // out. The downloader's connect/read timeouts are the actual
                // network bounds, while the generation checks below still
                // prevent a cancelled load from touching a newer video.
                run resolve@ {
                    // FAST: Get stream URLs only (no metadata, no related, no channel avatar)
                    val streamResult = youtubeRepository.getVideoStreamResult(video.videoId)
                    if (!isCurrentVideoLoad(video.videoId, loadGeneration)) return@resolve
                    val qualities = streamResult.qualities
                    _availableQualities.value = qualities
                    _seekPreview.value = streamResult.seekPreview
                    _isLive.value = qualities.any { it.isLive }
                    if (_isLive.value) {
                        // Some entry points do not know a broadcast is live
                        // until its stream qualities arrive. Remove any prior
                        // snapshot once that verdict is definitive.
                        clearVideoPlaybackSession()
                    }
                    // Before the first frame: the stream dimensions are the only
                    // shape signal there is, and both the vertical live layout
                    // and the watch page's video box are sized from it.
                    qualities.firstNotNullOfOrNull { it.sourceAspectRatio }?.let { ratio ->
                        _videoAspectRatio.value = ratio
                        if (ratio < 1f) _isPortraitVideo.value = true
                    }
                    if (_isLive.value) startLiveMetadataPolling(video.videoId)

                    if (qualities.isNotEmpty()) {
                        // A receiver cannot merge a video-only file with its
                        // audio twin the way MergingMediaSource does locally,
                        // so while casting the choice is narrowed to sources it
                        // can play whole: live HLS (its own ABR inside) or a
                        // self-contained progressive VOD. VOD DASH is excluded
                        // because affected receivers can select video without
                        // the matching audio adaptation set.
                        val chosen = if (_castPlayer != null) {
                            pickDefaultCastReceiverQuality(qualities)
                        } else {
                            pickDefaultQuality(qualities)
                        }
                        if (chosen == null) {
                            _playbackError.value =
                                Exception("This stream cannot be cast")
                            _isLoading.value = false
                            return@resolve
                        }
                        loadQuality(chosen)
                        seekAndResumeAfterLoad(resumePositionMs, resumePaused)
                    } else {
                        // Fallback to legacy stream URL
                        val streamUrl = youtubeRepository.getVideoStreamUrl(video.videoId)
                        if (!isCurrentVideoLoad(video.videoId, loadGeneration)) return@resolve
                        if (streamUrl != null) {
                            _currentQuality.value = VideoQuality(
                                resolution = "Auto",
                                url = streamUrl,
                                isDASH = false,
                                audioUrl = null
                            )
                            if (_castPlayer != null) {
                                loadQuality(_currentQuality.value!!)
                            } else {
                                val source = ProgressiveMediaSource.Factory(streamDataSourceFactory)
                                    .createMediaSource(
                                        cachedProgressiveMediaItem(
                                            uri = streamUrl,
                                            stream = VideoPlaybackCacheStream.MUXED,
                                            fallbackVariant = "auto-muxed",
                                        )
                                    )
                                _exoPlayer?.setMediaSource(source)
                                _exoPlayer?.prepare()
                            }
                            seekAndResumeAfterLoad(resumePositionMs, resumePaused)
                        } else {
                            _playbackError.value = Exception("Unable to load video stream")
                            _isLoading.value = false
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentVideoLoad(video.videoId, loadGeneration)) {
                    KLog.e("VideoPlayerVM", "Initial stream load failed for ${video.videoId}", e)
                    _playbackError.value = e
                    _isLoading.value = false
                }
            }
        }
        
        // ========== PHASE 2: ENGAGEMENT + METADATA + RELATED ==========
        // One watch-next call answers all three (the old code paid for an
        // engagement /next call AND a full NewPipe extraction here, competing
        // with Phase 1's initial buffering for bandwidth).
        watchNextJob = viewModelScope.launch {
            try {
                val watchNext = youtubeRepository.getWatchNextData(video.videoId, video)
                // Guard against a video switch that happened mid-flight
                if (!isCurrentVideoLoad(video.videoId, loadGeneration)) return@launch
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Phase 2 errors are non-critical - playback already started
                if (isCurrentVideoLoad(video.videoId, loadGeneration)) {
                    KLog.w("VideoPlayerVM", "Failed to load watch-next data", e)
                }
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

    private fun isCurrentVideoLoad(videoId: String, generation: Long): Boolean =
        videoLoadGeneration == generation && _currentVideo.value?.videoId == videoId

    /**
     * Seek-and-resume tail shared by every load path. Lives here rather than
     * inline because both players need it: for the CastPlayer the seek lands
     * once the receiver reports its timeline, which is exactly when
     * STATE_READY fires on the facade too.
     */
    private fun seekAndResumeAfterLoad(resumePositionMs: Long?, resumePaused: Boolean) {
        val player = activePlayer() ?: run {
            _isLoading.value = false
            return
        }
        if (resumePositionMs != null && resumePositionMs > 0) {
            player.seekTo(resumePositionMs)
        }
        if (resumePaused) {
            player.pause()
        } else {
            // Force play: override any previous paused state, matching the
            // local path's behaviour on a fresh open.
            player.play()
        }
        _isLoading.value = false
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
        val options = localVideoQualityOptions(qualities)
        val preferred = themePreferences.getDefaultVideoQuality()

        // The live ladder leads with an "Auto" entry (height 0) that the VOD
        // branches below would skip over, so it picks its own entry: Auto when
        // the setting says auto, otherwise the best rendition at or below the
        // target, falling back to Auto rather than to the lowest available.
        if (options.firstOrNull()?.isLive == true) {
            if (preferred == ThemePreferences.VIDEO_QUALITY_AUTO) return options.first()
            val targetHeight = height(preferred)
            return options.firstOrNull { height(it.resolution) in 1..targetHeight }
                ?: options.first()
        }

        if (preferred == ThemePreferences.VIDEO_QUALITY_AUTO) {
            return options.firstOrNull { height(it.resolution) > 0 } ?: options.first()
        }
        val targetHeight = height(preferred)
        return options.firstOrNull { height(it.resolution) in 1..targetHeight }
            ?: options.lastOrNull { height(it.resolution) > 0 }
            ?: options.first()
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
        if (_castPlayer != null && !quality.isDefaultCastReceiverCompatible) {
            // Playback code must never bypass the same policy that drives the
            // quality sheet. Failing closed here prevents a future caller from
            // handing the receiver a video-only URL and recreating silent TV
            // playback.
            KLog.w(
                "VideoPlayerVM",
                "Rejected non-Cast-compatible ${quality.delivery} source for ${quality.resolution}"
            )
            _playbackError.value = Exception("This video quality cannot be cast with audio")
            _isLoading.value = false
            return
        }
        _currentQuality.value = quality

        // The receiver runs its own ABR; the local track-selector cap has no
        // counterpart there (see pickDefaultCastReceiverQuality).
        if (_castPlayer == null && quality.isLive) {
            // The whole ladder is one manifest, so the cap has to be applied
            // alongside preparing it - loadQuality is the only entry point that
            // runs for the initial pick.
            applyLiveQualityCap(quality)
        }

        if (_castPlayer != null) {
            loadOnCast(quality)
            return
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
                    .createMediaSource(
                        cachedProgressiveMediaItem(
                            uri = quality.url,
                            stream = VideoPlaybackCacheStream.VIDEO,
                            fallbackVariant = quality.cacheVariant,
                        )
                    )
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(
                        cachedProgressiveMediaItem(
                            uri = audioUrl,
                            stream = VideoPlaybackCacheStream.AUDIO,
                            fallbackVariant = "original-audio",
                            includeNowPlayingMetadata = false,
                        )
                    )
                MergingMediaSource(true, videoSource, audioSource)
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(
                        cachedProgressiveMediaItem(
                            uri = quality.url,
                            stream = VideoPlaybackCacheStream.MUXED,
                            fallbackVariant = quality.cacheVariant,
                        )
                    )
            }

            _exoPlayer?.setMediaSource(primarySource)
        }
        _exoPlayer?.prepare()
    }

    /**
     * Load [quality] onto the receiver.
     *
     * The MediaItem must carry a MIME type (the cast converter refuses
     * anything else) and, for a live broadcast, the tag the converter reads to
     * declare STREAM_TYPE_LIVE - without it a broadcast presents on the TV as
     * a finite video with a broken seek bar. The selected caption track rides
     * along as a WebVTT text track; the Default Receiver renders it natively,
     * which is why the phone-side cue overlay stands down while casting.
     */
    private fun loadOnCast(quality: VideoQuality) {
        val player = _castPlayer ?: return
        KLog.i(
            "VideoPlayerVM",
            "Loading Cast source delivery=${quality.delivery} " +
                "format=${quality.format ?: "unknown"} quality=${quality.resolution} " +
                "live=${quality.isLive}"
        )
        val mime = if (quality.isDASH) {
            adaptiveMimeType(quality)
        } else {
            // The source policy only admits self-contained progressive files
            // here. format carries NewPipe's container suffix ("mp4",
            // "webm", "3gpp"), not a MIME type, so it is translated.
            when (quality.format?.lowercase()) {
                "webm" -> "video/webm"
                "3gpp" -> "video/3gpp"
                else -> "video/mp4"
            }
        }
        val builder = nowPlayingMediaItem(quality.url)
            .buildUpon()
            .setMimeType(mime)
        if (quality.isLive) builder.setTag(CAST_LIVE_TAG)

        _selectedCaption.value?.let { track ->
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track.vttUrl))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(track.languageCode)
                        .setLabel(track.name)
                        .build()
                )
            )
        }

        player.setMediaItem(builder.build())
        player.prepare()
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
     * Progressive item whose cache identity survives googlevideo URL refreshes.
     *
     * Split video/audio sources must never share one key: their byte offsets
     * describe different files. The rendition's itag normally distinguishes
     * qualities; [fallbackVariant] covers the rare provider URL without one.
     */
    private fun cachedProgressiveMediaItem(
        uri: String,
        stream: VideoPlaybackCacheStream,
        fallbackVariant: String,
        includeNowPlayingMetadata: Boolean = true,
    ): MediaItem {
        val item = if (includeNowPlayingMetadata) {
            nowPlayingMediaItem(uri)
        } else {
            MediaItem.fromUri(uri)
        }
        val videoId = _currentVideo.value?.videoId ?: return item
        return item.buildUpon()
            .setCustomCacheKey(
                videoPlaybackCacheKey(
                    videoId = videoId,
                    stream = stream,
                    sourceUrl = uri,
                    fallbackVariant = fallbackVariant,
                )
            )
            .build()
    }

    private val VideoQuality.cacheVariant: String
        get() = listOfNotNull(resolution, format, codec)
            .joinToString("-")

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
            KLog.w("VideoPlayerVM", "Could not refresh now-playing metadata", e)
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
            // The receiver owns ABR while casting - its player has no
            // track-selector cap to reach. The UI only offers Auto in this
            // state (selectableQualities), so reaching here with a rung means
            // a stale sheet; ignoring it beats pretending.
            if (_castPlayer != null) return
            _currentQuality.value = quality
            applyLiveQualityCap(quality)
            return
        }
        val selected = if (_castPlayer != null) {
            defaultCastReceiverQualityOptions(_availableQualities.value)
                .firstOrNull { it.resolution == quality.resolution }
                ?: run {
                    // A sheet that was open while the session connected may
                    // still deliver a local-only row. Ignore that stale tap;
                    // interrupting valid playback with a fallback quality is
                    // more surprising than leaving the current stream alone.
                    KLog.w(
                        "VideoPlayerVM",
                        "Ignored stale non-Cast quality selection ${quality.resolution}"
                    )
                    return
                }
        } else {
            quality
        }
        reloadPreservingPosition(selected)
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
     * playback position. Only quality switches and caption changes need this -
     * both players take the same route: capture the active position, load,
     * seek back on READY.
     */
    private fun reloadPreservingPosition(quality: VideoQuality) {
        val player = activePlayer() ?: return
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
        if (_isLocalPlayback.value) return
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
                    if (themePreferences.isCaptionsEnabled() && _selectedCaption.value == null) {
                        restoreSavedCaptionTrack(tracks)
                    }
                }
            } finally {
                if (_currentVideo.value?.videoId == video.videoId) {
                    _isCaptionsLoading.value = false
                }
            }
        }
    }

    /**
     * Re-apply the persisted caption choice to a fresh track list: the exact
     * language first, then the same base language in another region ("en" vs
     * "en-US" or an auto-generated track), then whatever the video offers.
     * A video with no tracks simply leaves captions off.
     */
    private fun restoreSavedCaptionTrack(tracks: List<CaptionTrack>) {
        val wanted = themePreferences.getCaptionLanguageCode()
        val match = tracks.firstOrNull { it.languageCode == wanted }
            ?: tracks.firstOrNull {
                wanted != null && it.languageCode.substringBefore('-') == wanted.substringBefore('-')
            }
            ?: tracks.firstOrNull()
        match?.let { setCaptionTrack(it) }
    }

    /**
     * Select a caption track, or null to turn captions off.
     *
     * The choice is persisted: the on/off state and the track's language come
     * back on the next video, so a user who watches subtitled does not tap CC
     * every time. [startVideo] re-applies the saved language once the new
     * video's tracks arrive.
     *
     * The playback pipeline is deliberately untouched here. Captions used to be
     * a text track merged into the media source, so switching them rebuilt that
     * source: playback re-prepared and its active sample buffer was discarded.
     * Cues are fetched and parsed on their own instead, and the overlay draws
     * them over the video surface, which makes the CC toggle instant and free
     * no matter how many times it is pressed, including when disk caching is off.
     */
    fun setCaptionTrack(track: CaptionTrack?) {
        if (_selectedCaption.value == track) return
        _selectedCaption.value = track
        themePreferences.setCaptionsEnabled(track != null)
        themePreferences.setCaptionLanguageCode(track?.languageCode)

        captionCuesJob?.cancel()
        if (_castPlayer != null) {
            // The receiver renders captions from the text track attached to
            // the load, so a toggle rebuilds that load at the current
            // position. Heavier than the local cue swap, but it is what makes
            // subtitles exist on the TV at all - and quality switches already
            // pay the same price there.
            _currentQuality.value?.let { reloadPreservingPosition(it) }
            if (track == null) _captionCues.value = emptyList()
            return
        }
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
                    KLog.w(
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
        qualityChangeListener?.let { activePlayer()?.removeListener(it) }
        qualityChangeListener = null
        // Ending the session before releasing the player: CastPlayer.release()
        // itself ends the session with stopOnReceiver=false, which would leave
        // the TV sitting on a paused poster. An explicit close means "stop".
        if (_castPlayer != null) {
            castManager.endSession(stopOnReceiver = true)
            releaseCastPlayer()
            _isCasting.value = false
        }
        _exoPlayer?.stop()
        // Track selection outlives media items: a player closed while the video
        // track is suspended would come back audio-only on the next video.
        onEnterForeground()
        stopLivePolling()
        liveChatStartedForVideoId = null
        _currentVideo.value = null
        _queue.value = null
        deferredRestorePositionMs = null
        leaveLocalPlayback()
        _isExpanded.value = false
        // Nothing is playing any more, so nothing should be on the lock screen.
        com.ivor.ivormusic.service.VideoPlaybackService.stop(context)
        // An explicit close means "I'm done with this video" - the opposite
        // of what the resume snapshot is for, so it must not reappear next
        // launch.
        clearVideoPlaybackSession()
    }

    fun togglePlayPause() {
        deferredRestorePositionMs?.let { position ->
            val video = _currentVideo.value ?: return
            startVideo(
                video = video,
                forceRestart = true,
                resumePositionMs = position,
                resumePaused = false,
                expand = _isExpanded.value
            )
            return
        }
        val player = activePlayer() ?: return
        if (_isPlaying.value) {
            player.pause()
        } else {
            // After autoplay deliberately stops at the end, Play means replay
            // this video. ExoPlayer does not leave STATE_ENDED on play() alone.
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekToDefaultPosition()
            }
            player.play()
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
        deferredRestorePositionMs?.let { restoredPosition ->
            val duration = _durationMs.value.takeIf { it > 0L } ?: Long.MAX_VALUE
            val position = (restoredPosition + deltaMs).coerceIn(0L, duration)
            deferredRestorePositionMs = position
            _positionMs.value = position
            _progress.value = if (duration != Long.MAX_VALUE) {
                (position.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            return
        }
        val player = activePlayer() ?: return
        val duration = player.duration
        val upperBound = if (duration > 0) duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, upperBound))
    }

    /** Absolute seek which also works before a restored session has loaded media. */
    fun seekTo(positionMs: Long) {
        deferredRestorePositionMs?.let {
            val duration = _durationMs.value.takeIf { value -> value > 0L } ?: Long.MAX_VALUE
            val position = positionMs.coerceIn(0L, duration)
            deferredRestorePositionMs = position
            _positionMs.value = position
            _progress.value = if (duration != Long.MAX_VALUE) {
                (position.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            return
        }
        activePlayer()?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** Pause without closing the player (music or Shorts playback started). */
    fun pause() {
        activePlayer()?.pause()
    }

    /** External play/pause entry points (the system PiP window's buttons). */
    fun playFromExternal() {
        deferredRestorePositionMs?.let { position ->
            val video = _currentVideo.value ?: return
            startVideo(
                video = video,
                forceRestart = true,
                resumePositionMs = position,
                resumePaused = false,
                expand = _isExpanded.value
            )
            return
        }
        val player = activePlayer() ?: return
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        player.play()
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
                KLog.w("VideoPlayerVM", "Failed to send live chat message", e)
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
     * Add a video to a playlist. Reports the outcome on the main thread so the
     * save sheet can show inline feedback.
     *
     * Same three-way routing as `HomeViewModel.addVideoToPlaylist`: a device
     * playlist, the device's Watch Later while signed out, or the account.
     * Duplicated rather than shared because the two ViewModels have no common
     * base and no DI to hand one an instance of the other; the store underneath
     * is process-wide, so both see the same list either way.
     */
    fun addVideoToPlaylist(playlistId: String, video: VideoItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val local = com.ivor.ivormusic.data.LocalVideoPlaylistsRepository
            onResult(
                when {
                    local.isLocal(playlistId) ->
                        localVideoPlaylistsRepository.addVideo(playlistId, video)
                    playlistId == "WL" && !_isLoggedIn.value ->
                        localVideoPlaylistsRepository.addVideo(
                            localVideoPlaylistsRepository.ensureWatchLater(),
                            video
                        )
                    else -> youtubeRepository.addToYouTubePlaylist(
                        playlistId,
                        video.videoId,
                        music = false
                    )
                }
            )
        }
    }

    /** Create a playlist on the device, from the save sheet. */
    fun createLocalVideoPlaylist(name: String, onCreated: (String?) -> Unit) {
        viewModelScope.launch { onCreated(localVideoPlaylistsRepository.create(name)) }
    }

    // ---------------- Channel page ----------------

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
         * Unplayable videos stepped over in a row before the error overlay wins.
         *
         * Three, because that is comfortably more than the run of dead entries a
         * real playlist has in one place and far fewer than it takes to burn
         * through a playlist when the network is the thing that is broken. The
         * count resets on the first video that actually reaches STATE_READY.
         */
        private const val MAX_QUEUE_ERROR_SKIPS = 3

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

        /**
         * How often the playhead is sampled for the seek bar.
         *
         * Twice a second: the bar is a thin progress line, so a finer tick buys
         * nothing visible, and the chapter chip only changes at chapter
         * boundaries. The music player ticks once a second, but it is driving a
         * timestamp rather than a scrub bar being watched.
         */
        private const val PROGRESS_POLL_MS = 500L

        /**
         * Floor between two resume-snapshot writes from the progress poll.
         * onEnterBackground() saves immediately on its own; this only guards
         * the foreground-kill backstop above, where every-500ms would be a
         * disk write nobody asked for.
         */
        private const val SESSION_SAVE_INTERVAL_MS = 15_000L
    }

    override fun onCleared() {
        super.onCleared()
        // Remove quality change listener to prevent leaks
        qualityChangeListener?.let { _exoPlayer?.removeListener(it) }
        qualityChangeListener = null
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = null
        streamLoadJob?.cancel()
        streamLoadJob = null
        watchNextJob?.cancel()
        watchNextJob = null
        progressJob?.cancel()
        progressJob = null
        stopLivePolling()
        // Cast teardown before the local player's: the receiver must be told
        // to stop (an explicit close is not "keep playing"), and the session
        // listener detached so its loss callback cannot fire into a dying
        // ViewModel.
        if (_castPlayer != null) {
            castManager.endSession(stopOnReceiver = true)
            releaseCastPlayer()
            _isCasting.value = false
        }
        castManager.endObservation()
        // Before the release below, not after: stop() drops the MediaSession
        // synchronously on this thread, and a session outliving the player it
        // wraps crashes the next time Media3 reads state off it.
        com.ivor.ivormusic.service.VideoPlaybackService.stop(context)
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
