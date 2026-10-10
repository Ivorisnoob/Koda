package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Audiotrack
import com.ivor.ivormusic.service.FrameInterpolationStatus
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.CaptionBackground
import com.ivor.ivormusic.data.CaptionTextColor
import com.ivor.ivormusic.data.CAPTION_TEXT_SCALE_MAX
import com.ivor.ivormusic.data.CAPTION_TEXT_SCALE_MIN
import com.ivor.ivormusic.data.CaptionTrack
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.data.LocalVideo
import com.ivor.ivormusic.data.VideoChapter
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.PlayerTrackOption
import com.ivor.ivormusic.data.VideoQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** The shape of the watch page's video box when the source shape is unknown. */
private const val DEFAULT_VIDEO_ASPECT = 16f / 9f

/**
 * The tallest the watch page's video box may grow, as a fraction of the screen.
 *
 * A 9:16 video at full width wants about 80% of the height of a modern phone,
 * which leaves the watch page with room for nothing. This is the line where the
 * title, the channel row and the action row still fit underneath - the point of
 * keeping the video on the watch page at all. Past it the video letterboxes
 * rather than pushing the page off the screen.
 */
private const val MAX_VIDEO_BOX_HEIGHT_FRACTION = 0.62f

/**
 * Content for the full Video Player Overlay.
 * Replaces old VideoPlayerScreen by using VideoPlayerViewModel.
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerContent(
    viewModel: VideoPlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    timedCommentsFeatureEnabled: Boolean = false,
    /** False drops the related-videos list; the queue is unaffected. */
    showRelatedVideos: Boolean = true,
    /**
     * Open the playing video's creator. Routed out to the host: the channel
     * page is a NavHost destination and this player is drawn above the NavHost,
     * so the host is the layer that both navigates and minimises this player on
     * the way there.
     */
    onOpenChannel: (String) -> Unit = {},
    /**
     * Move the playing video into the music queue ("Listen as music").
     * Owned by the host: it spans both ViewModels and the mode toggle.
     */
    onListenAsMusic: () -> Unit = {},
    // Swipe-down-to-minimize: raw drag deltas / release velocity from the
    // portrait video surface, driving the overlay's expand progress
    onMinimizeDragDelta: (Float) -> Unit = {},
    onMinimizeDragRelease: (Float) -> Unit = {},
    /**
     * Whether the portrait box draws the picture right now, rather than the
     * mini bar's frame the minimize transition is handing it to. Read inside
     * the video view's update block, so it moves the picture without
     * recomposing this page.
     */
    holdsVideoSurface: () -> Boolean = { true }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // State from ViewModel
    val video by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentQuality by viewModel.currentQuality.collectAsState()
    val relatedVideos by viewModel.relatedVideos.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val seekPreview by viewModel.seekPreview.collectAsState()
    val captionTracks by viewModel.captionTracks.collectAsState()
    val selectedCaption by viewModel.selectedCaption.collectAsState()
    // Tracks the media file itself carries, populated only for device videos.
    val audioTracks by viewModel.audioTracks.collectAsState()
    val embeddedTextTracks by viewModel.embeddedTextTracks.collectAsState()
    val embeddedCueText by viewModel.embeddedCueText.collectAsState()
    val captionCues by viewModel.captionCues.collectAsState()
    val captionTextSize by viewModel.captionTextSize.collectAsState()
    val allSponsorSegments by viewModel.sponsorSegments.collectAsState()
    val sponsorShowOnSeekBar by viewModel.sponsorShowOnSeekBar.collectAsState()
    // Skipping and drawing are separate choices: someone can want segments
    // skipped without wanting the bar striped, so the toggle empties what the
    // seek bar is given rather than what the player acts on.
    val sponsorSegments = if (sponsorShowOnSeekBar) allSponsorSegments else emptyList()
    val manualSegment by viewModel.manualSegment.collectAsState()
    val skipNotice by viewModel.skipNotice.collectAsState()
    val resumedFromMs by viewModel.resumedFromMs.collectAsState()
    // Both chips claim the same corner. A SponsorBlock skip is the more urgent
    // of the two and expires on its own, so the resume notice steps aside and
    // comes back rather than being stacked or dropped.
    val visibleResumedFromMs = resumedFromMs
        ?.takeIf { skipNotice == null && manualSegment == null }
    val captionTextColor by viewModel.captionTextColor.collectAsState()
    val captionBackground by viewModel.captionBackground.collectAsState()
    val videoAspectRatio by viewModel.videoAspectRatio.collectAsState()

    // PiP is a device capability, not a given: Android TV and a few OEM builds
    // ship without it, and the button must not sit there doing nothing.
    val pipSupported = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
            )
    }
    val isCaptionsLoading by viewModel.isCaptionsLoading.collectAsState()
    val isAutoplayEnabled by viewModel.isAutoplayEnabled.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()
    val dislikeCount by viewModel.dislikeCount.collectAsState()
    val sleepTimerEndsAt by viewModel.sleepTimerEndsAt.collectAsState()
    val sleepTimerEndOfVideo by viewModel.sleepTimerEndOfVideo.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val connectionAdvice by viewModel.connectionAdvice.collectAsState()
    val engagement by viewModel.engagement.collectAsState()
    // Account subscription OR device subscription - engagement only knows the
    // first, and read alone it showed "Subscribe" for locally followed channels.
    val isSubscribedToChannel by viewModel.isSubscribedToChannel.collectAsState()
    val bellWrites by viewModel.bellWrites.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsState()
    val isLoadingMoreComments by viewModel.isLoadingMoreComments.collectAsState()
    val commentReplies by viewModel.replies.collectAsState()
    val loadingReplyIds by viewModel.loadingReplyIds.collectAsState()
    val timedComments by viewModel.timedComments.collectAsState()
    val canComment by viewModel.canComment.collectAsState()
    val isPostingComment by viewModel.isPostingComment.collectAsState()
    val videoPlaylists by viewModel.videoPlaylists.collectAsState()
    val isVideoPlaylistsLoading by viewModel.isVideoPlaylistsLoading.collectAsState()
    val isLive by viewModel.isLive.collectAsState()
    val liveTargetOffsetMs by viewModel.liveTargetOffsetMs.collectAsState()
    val isLocalPlayback by viewModel.isLocalPlayback.collectAsState()
    val isPortraitVideo by viewModel.isPortraitVideo.collectAsState()
    val liveViewerCount by viewModel.liveViewerCount.collectAsState()
    val liveChatMessages by viewModel.liveChatMessages.collectAsState()
    val liveChatBanner by viewModel.liveChatBanner.collectAsState()
    val isLiveChatAvailable by viewModel.isLiveChatAvailable.collectAsState()
    val isLiveChatLoading by viewModel.isLiveChatLoading.collectAsState()
    val canSendLiveChat by viewModel.canSendLiveChat.collectAsState()
    val isSendingLiveChat by viewModel.isSendingLiveChat.collectAsState()
    val liveChatMaxLength by viewModel.liveChatMaxLength.collectAsState()
    val liveChatRestriction by viewModel.liveChatRestriction.collectAsState()

    val selectableQualities by viewModel.selectableQualities.collectAsState()

    // "Listen as music" migrates into MusicService, which resolves YouTube
    // audio streams: live broadcasts, device files and one-off external
    // grants have no audio-only path there, so the row stays hidden for them.
    // A local copy because a delegated property cannot be smart-cast.
    val nowPlayingVideo = video
    val showListenAsMusic = nowPlayingVideo != null && !isLive && !isLocalPlayback &&
        !LocalVideo.isDeviceVideoId(nowPlayingVideo.videoId) && !nowPlayingVideo.videoId.startsWith("external:")

    // Local UI State
    var showControls by remember { mutableStateOf(false) }
    // Keyed to the video so a source switch can never inherit a drag that
    // belonged to the old timeline.
    var isSeekScrubbing by remember(video?.videoId) { mutableStateOf(false) }
    // The zoom-to-fill request, keyed the same way: opening a new video
    // always starts fitted rather than inheriting the last video's crop.
    var isZoomedToFill by remember(video?.videoId) { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showCaptionsSheet by remember { mutableStateOf(false) }
    // Keyed on the playlist, so the flag cannot outlive the queue it belongs to.
    // Running off the end of a playlist drops the queue and takes the sheet off
    // screen without ever calling its onDismiss; left un-keyed, the flag would
    // still read true and the next playlist would open with the sheet already
    // up. A `remember` key rather than an effect: this sits above the early
    // return below, where effects corrupt the slot table.
    var showQueueSheet by remember(queue?.playlistId, queue?.title) {
        mutableStateOf(false)
    }
    var showLiveChat by remember { mutableStateOf(false) }
    // A portrait live stream opens full-bleed: the standard layout gives a 9:16
    // frame about a third of the width and pillarboxes the rest, which is the
    // worst presentation of the one thing the user opened. Leaving for the
    // watch page is a deliberate tap, and it only holds for the current video -
    // the next one gets the treatment its own shape deserves.
    val verticalLiveAvailable = isLive && isPortraitVideo
    var showVideoPageForVerticalLive by remember(video?.videoId) { mutableStateOf(false) }
    val verticalLiveImmersive = verticalLiveAvailable &&
        !showVideoPageForVerticalLive &&
        !isFullscreen

    /**
     * Fullscreen has two shapes, and which one it takes follows the video.
     *
     * Fullscreen used to mean "rotate to landscape" unconditionally, which is
     * right for the 16:9 uploads that are most of YouTube and exactly wrong for
     * a 9:16 one: asking to fill the screen turned the phone sideways and put
     * the video in a letterboxed strip using less of the screen than the watch
     * page had just given it. A vertical video fills a phone held upright, so
     * that is what fullscreen does for it.
     *
     * **Live portrait streams are excluded on purpose.** For those, fullscreen
     * already means something: rotating is how the docked chat column appears,
     * and the space beside a 9:16 stream in landscape is chat-shaped. Their
     * upright full-bleed layout is `VerticalLivePlayerContent`, which they open
     * in by default.
     */
    // Which shape fullscreen takes is decided at the point it is entered: the
    // button and the swipe follow the video, physically rotating the device
    // does not (see the orientation listener below).
    //
    // Everything effectful about this lives after the early return further
    // down, with the rest of this composable's effects. This composable returns
    // early while the video is still resolving, and putting a LaunchedEffect or
    // a local function above that return desynchronised the slot table enough
    // that the restart lambda came back with a corrupt argument list -
    // "ClassCastException: EmptyCoroutineContext cannot be cast to Function1",
    // on every video open. Plain values and remember are fine here; effects are
    // not.
    val portraitFullscreenAvailable = isPortraitVideo && !isLive
    var fullscreenIsPortrait by remember { mutableStateOf(false) }

    // Landscape chat column: about a third of the screen, bounded so it stays
    // readable on a small phone and does not eat a tablet.
    // Measured in the scaled dp (windowDpSize), not Configuration's: at any
    // interface scale but 100% the platform figure is a different unit.
    val configuration = LocalConfiguration.current
    val windowWidth = com.ivor.ivormusic.ui.theme.windowDpSize().width
    val landscapeChatWidth = remember(windowWidth) {
        (windowWidth * 0.34f).coerceIn(260.dp, 360.dp)
    }

    /**
     * The shape of the video box on the watch page.
     *
     * A fixed 16:9 frame is right for the overwhelming majority of uploads and
     * wrong for the rest: a 9:16 video inside it gets about a third of the width
     * and pillarbox down both sides, which is the worst possible presentation of
     * the one thing the user opened. So a portrait source gets a box its own
     * shape instead, capped by [MAX_VIDEO_BOX_HEIGHT_FRACTION] so the watch page
     * underneath survives.
     *
     * **Landscape sources are deliberately left at 16:9**, including 4:3, which
     * this could just as easily follow. Nothing is badly broken there, it is the
     * shape every feed thumbnail and the mini player already use, and changing
     * the common path is not what this is for.
     *
     * **The video is fitted inside the box, never zoomed to fill it.** The
     * vertical live player crops the sides of a 9:16 frame to fill the screen,
     * which is the bargain Shorts makes and is fine there; here the box is never
     * narrower than the video, so filling it would crop the top and bottom
     * instead - exactly where a vertical upload puts faces and captions. Fitting
     * means an uncapped source lands on an exact fit with no bars at all, and
     * only a very tall video on a very tall phone keeps a slim pair, far less
     * than 16:9 was giving it. The MAX_ACCEPTABLE_CROP judgement the vertical
     * live player makes about 4:5 and 1:1 not being "vertical" in the Shorts
     * sense is inherited for free: those get a 4:5 or 1:1 box and no crop.
     */
    val targetVideoBoxAspect = run {
        val source = videoAspectRatio?.takeIf { it.isFinite() && it > 0f }
        if (source == null || source >= 1f) {
            DEFAULT_VIDEO_ASPECT
        } else {
            // The narrowest box that still fits the height budget. Written as
            // an aspect so the whole thing stays one number the layout can
            // animate; coerced rather than coerceIn because a window wider than
            // it is tall would put the floor above the ceiling and throw.
            val heightCapAspect = configuration.screenWidthDp.toFloat() /
                (configuration.screenHeightDp.toFloat() * MAX_VIDEO_BOX_HEIGHT_FRACTION)
                    .coerceAtLeast(1f)
            maxOf(source, heightCapAspect).coerceAtMost(DEFAULT_VIDEO_ASPECT)
        }
    }
    // The shape is usually known from the stream dimensions before the first
    // frame decodes, but not before the player opens, so the box would still
    // snap from 16:9 the moment the quality list lands. Animated, it reads as
    // the frame opening out to meet the video. Non-bouncy on purpose: an
    // overshoot on an aspect ratio drives the box past the screen, and an
    // undershoot below zero throws.
    val videoBoxAspect by animateFloatAsState(
        targetValue = targetVideoBoxAspect,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "videoBoxAspect"
    )
    // Timed comments overlay toggle; persists across videos while the player is open
    var timedCommentsActive by remember { mutableStateOf(false) }
    
    // Playback progress comes from the ViewModel, which owns the poll along with
    // the rest of the player state.
    val currentPosition by viewModel.positionMs.collectAsState()
    val duration by viewModel.durationMs.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val bufferedProgress by viewModel.bufferedProgress.collectAsState()

    val exoPlayer = viewModel.exoPlayer
    val currentVideo = video

    if (currentVideo == null || exoPlayer == null) return

    // Double-tap seek helper: jump relative to the live playhead, clamped to the clip.
    // Delegated rather than done here: the PiP window and the media
    // notification skip through the same ViewModel call, and a gesture that
    // clamped differently from the buttons would be a bug waiting to happen.
    fun seekBy(deltaMs: Long) = viewModel.seekBy(deltaMs)

    /**
     * Whether pinch-to-zoom may fill the screen for this video in this window.
     *
     * A 16:9 upload on a tall phone loses a slim band top and bottom, which is
     * the feature; a 9:16, 4:5 or 1:1 source in a landscape window would lose
     * most of the picture, so those keep their letterbox under the same
     * MAX_ACCEPTABLE_CROP bargain the vertical live player makes. Unknown
     * shapes fall back on the parse-time orientation, which lands before the
     * first frame decodes, so a portrait upload cannot sneak a zoom in while
     * its dimensions are still missing. Only read while fullscreen: the window
     * there is what the crop is measured against.
     */
    val zoomToFillAvailable = run {
        val source = videoAspectRatio?.takeIf { it.isFinite() && it > 0f }
        if (source != null) {
            val windowAspect = configuration.screenWidthDp.toFloat() /
                configuration.screenHeightDp.toFloat().coerceAtLeast(1f)
            isZoomToFillAvailable(source, windowAspect)
        } else {
            !isPortraitVideo
        }
    }

    // Autoplay can hand a landscape video to a fullscreen still locked upright
    // for the portrait one before it, which plays the next video in a
    // letterboxed strip with the phone held the wrong way round.
    LaunchedEffect(portraitFullscreenAvailable) {
        if (!portraitFullscreenAvailable) fullscreenIsPortrait = false
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, isSeekScrubbing) {
        if (showControls && isPlaying && !isSeekScrubbing) {
            // The user's own delay, read as the timer starts so a change in
            // Settings applies to the very next time the controls come up.
            delay(com.ivor.ivormusic.data.ThemePreferences.videoControlsHideMs(context))
            showControls = false
        }
    }

    // The chat stream only starts once chat is actually on screen, and a video
    // that turns out not to be live must not leave the panel showing. The
    // vertical live layout counts as on screen: its ticker is always visible,
    // so it needs the poll running without anyone opening a panel.
    val liveChatOnScreen = isLive && (showLiveChat || verticalLiveImmersive)
    LaunchedEffect(liveChatOnScreen, isLive) {
        if (liveChatOnScreen) viewModel.ensureLiveChatStarted() else viewModel.stopLiveChat()
        if (!isLive) showLiveChat = false
    }

    // Landscape is the one shape where a 9:16 video and a chat column both fit
    // without either giving anything up - the space beside the video is chat
    // sized - so rotating a vertical live stream brings chat with it. Coming
    // back to portrait hands the job back to the ticker.
    LaunchedEffect(isFullscreen, verticalLiveAvailable) {
        if (verticalLiveAvailable) showLiveChat = isFullscreen
    }

    // Fetch the first page of comments once the overlay is active and the
    // comments entry token has arrived (engagement loads asynchronously)
    val commentsToken = engagement?.commentsToken
    LaunchedEffect(timedCommentsActive, commentsToken, isLive) {
        if (timedCommentsFeatureEnabled && timedCommentsActive && !isLive && commentsToken != null) {
            viewModel.ensureCommentsLoaded()
        }
    }
    
    // Fullscreen / Immersive. The watch page holds portrait while it is open
    // and fullscreen temporarily requests sensor landscape. Leaving the page
    // hands back to the app's own policy (AppOrientation) - never
    // UNSPECIFIED, which used to leave the app free-rotating in broken
    // half-landscape states, and no longer a hardcoded PORTRAIT, which would
    // re-lock a tablet or a phone set to rotate with the device.
    DisposableEffect(isFullscreen, fullscreenIsPortrait) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        if (isFullscreen) {
            // Allow content to draw behind system bars first
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            // A vertical video fills the screen held upright, so fullscreen
            // holds it there rather than rotating into a letterboxed strip.
            // Everything else gets sensor landscape, both directions, like
            // YouTube.
            activity?.requestedOrientation = if (fullscreenIsPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            insetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.let { com.ivor.ivormusic.ui.theme.AppOrientation.apply(it) }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            // The app is edge-to-edge (enableEdgeToEdge in MainActivity), so
            // keep decorFits false — restoring true here used to break the
            // edge-to-edge layout for the rest of the session
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        }
    }

    // YouTube-style rotation while the player is open: physically turning the
    // device to landscape enters fullscreen, turning it upright again exits.
    // Only orientation *transitions* act — so fullscreen entered with the
    // button while holding the phone upright is not immediately exited — and
    // the system auto-rotate lock is respected.
    DisposableEffect(activity) {
        var lastDeviceOrientation = -1 // 0 = portrait, 1 = landscape
        val listener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return
                val autoRotateOn = android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.ACCELEROMETER_ROTATION, 0
                ) == 1
                if (!autoRotateOn) return
                // Only classify when clearly near an axis (30-degree window)
                // so jitter around the diagonals cannot flip the state
                val orientation = when {
                    degrees <= 30 || degrees >= 330 || degrees in 150..210 -> 0
                    degrees in 60..120 || degrees in 240..300 -> 1
                    else -> return
                }
                if (orientation == lastDeviceOrientation) return
                val isFirstReading = lastDeviceOrientation == -1
                lastDeviceOrientation = orientation
                if (isFirstReading) return
                // Turning the phone sideways means landscape fullscreen, even
                // from an upright fullscreen: the user has just said which way
                // round they want it, and for a vertical video a pillarboxed
                // frame they asked for beats a portrait lock they did not.
                if (orientation == 1 && (!isFullscreen || fullscreenIsPortrait)) {
                    fullscreenIsPortrait = false
                    isFullscreen = true
                } else if (orientation == 0 && isFullscreen && !fullscreenIsPortrait) {
                    // Upright does not end a fullscreen that is already
                    // upright, which is the whole point of the portrait one.
                    isFullscreen = false
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    
    // Playback-settings sheet state
    var showPlaybackSettings by remember { mutableStateOf(false) }
    val playbackSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Sleep timer sheet. Opened from playback settings, which closes first:
    // a picker sheet stacked over the settings sheet is the nesting the
    // comments and queue sheets already avoid by the same hand-off.
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    // Comments Sheet + Sign-in Dialog State
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }

    // The sheet outlives the video it was opened on, so playing a live stream
    // from an open comments sheet would leave the previous video's comments
    // sitting over a player that no longer offers a way back to them.
    LaunchedEffect(isLive) {
        if (isLive) showCommentsSheet = false
    }

    // Save-to-playlist sheet (Save button or long-press on an Up Next video),
    // the download sheet it hands off to, and the channel page sheet (tap on
    // the channel row)
    var saveTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var downloadTargetVideo by remember { mutableStateOf<VideoItem?>(null) }

    // Gate authenticated actions behind login
    fun requireLogin(action: () -> Unit) {
        if (isLoggedIn) action() else showSignInDialog = true
    }

    /**
     * Subscribing has a signed-out path now - it saves to the device unless
     * the user explicitly picked the YouTube-account target - so it gets its
     * own gate instead of the blanket login wall the other actions use.
     */
    fun requireSubscribeLogin(action: () -> Unit) {
        if (viewModel.subscribeNeedsLogin()) showSignInDialog = true else action()
    }

    /**
     * Pull the watch page down to minimize, from anywhere on it.
     *
     * Swiping the video surface has always worked, but that surface is a 16:9
     * strip at the top of the screen - and most of it is covered by the
     * transport controls whenever they are up, since a single tap brings them
     * out. People reached for the gesture on the page below, where nothing
     * happened, and concluded it was fussy. Everything under the video is one
     * scrolling column, so once it is at the top the leftover pull is exactly
     * the minimize drag, fed through the same callbacks the video surface uses.
     *
     * Deliberately attached to the info column rather than the box around it:
     * the comments panel slides up inside that box and has its own dismiss, so
     * pulling its list down must not drag the player away underneath it.
     */
    val minimizeDelta by rememberUpdatedState(onMinimizeDragDelta)
    val minimizeRelease by rememberUpdatedState(onMinimizeDragRelease)
    val pullToMinimize = remember {
        object : NestedScrollConnection {
            /** True once this gesture has taken the page over. */
            private var pulling = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Once the pull has started it keeps the gesture to the end.
                // Handing it back on the first upward flick would scroll the
                // list under a player left sitting half way down the screen.
                if (!pulling || source != NestedScrollSource.UserInput) return Offset.Zero
                minimizeDelta(available.y)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Leftover downward drag means the list is already at the top.
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                pulling = true
                minimizeDelta(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Only ends a pull this connection actually started, so a fling
                // that merely runs out of list at the top is left alone.
                if (!pulling) return Velocity.Zero
                pulling = false
                minimizeRelease(available.y)
                return available
            }
        }
    }

    // ---------------- UI ----------------
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                // Consume clicks to prevent interaction with underlying app
            }
    ) {
        if (isFullscreen) {
            // Clearance for anything floating over the frame above the bottom
            // bar. Portrait's bar is taller and further off the edge, so the
            // landscape figure would put the SponsorBlock chip behind it.
            val fullscreenOverlayBottom = if (fullscreenIsPortrait) {
                FULLSCREEN_COMPACT_BOTTOM_BAR
            } else {
                104.dp
            }
            // Fullscreen Layout - ensure it fills entire screen including cutout areas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black) // Extra black background to prevent any bleed
                    // Fullscreen reports its own bounds: the portrait video box
                    // is not composed here, so without this the PiP source rect
                    // would still describe the small inline player.
                    .onGloballyPositioned { coords ->
                        val rect = coords.boundsInWindow()
                        viewModel.setVideoSurfaceBounds(
                            android.graphics.Rect(
                                rect.left.toInt(),
                                rect.top.toInt(),
                                rect.right.toInt(),
                                rect.bottom.toInt()
                            )
                        )
                    }
            ) {
                FullscreenPlayerContent(
                exoPlayer = exoPlayer,
                videoId = currentVideo.videoId,
                showControls = showControls,
                onToggleControls = { showControls = !showControls },
                hasError = playbackError != null,
                errorMessage = playbackError?.message ?: "",
                connectionAdvice = connectionAdvice,
                isLoading = isLoading,
                isBuffering = isBuffering,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                progress = progress,
                bufferedProgress = bufferedProgress,
                seekPreview = seekPreview,
                videoTitle = currentVideo.title,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { newProgress -> viewModel.seekTo((newProgress * duration).toLong()) },
                onScrubbingChanged = { isSeekScrubbing = it },
                onSeekBackward = { seekBy(-VideoPlayerViewModel.SEEK_STEP_MS) },
                onSeekForward = { seekBy(VideoPlayerViewModel.SEEK_STEP_MS) },
                onBack = {
                    isFullscreen = false
                    fullscreenIsPortrait = false
                },
                onFullscreenToggle = {
                    isFullscreen = false
                    fullscreenIsPortrait = false
                },
                onSettings = { showPlaybackSettings = true },
                chapters = chapters,
                sponsorSegments = sponsorSegments,
                onOpenChapters = { showChaptersSheet = true },
                captionsActive = selectedCaption != null || embeddedTextTracks.any { it.isSelected },
                onCaptionsClick = {
                    viewModel.ensureCaptionsLoaded()
                    showCaptionsSheet = true
                },
                captionCues = captionCues,
                embeddedCueText = embeddedCueText,
                captionTextSize = captionTextSize,
                captionTextColor = captionTextColor,
                captionBackground = captionBackground,
                showQueueControls = queue != null,
                hasPreviousInQueue = queue?.hasPrevious == true,
                hasNextInQueue = queue?.hasNext == true,
                onPreviousInQueue = { viewModel.playPreviousInQueue() },
                onNextInQueue = { viewModel.playNextInQueue() },
                isLive = isLive,
                liveTargetOffsetMs = liveTargetOffsetMs,
                onSeekToLive = { exoPlayer.seekToDefaultPosition() },
                // A pillarboxed 9:16 stream and a docked chat column are the
                // one pairing where landscape wastes nothing - but only if the
                // video moves out from under the panel.
                videoEndPadding = if (showLiveChat && isLive && isPortraitVideo) {
                    landscapeChatWidth
                } else {
                    0.dp
                },
                compactChrome = fullscreenIsPortrait,
                isZoomedToFill = isZoomedToFill,
                onZoomedToFillChange = { isZoomedToFill = it },
                zoomToFillAvailable = zoomToFillAvailable,
                videoAspectRatio = videoAspectRatio,
                onRetry = { viewModel.retryPlayback() }
            )

                // Timed comments are anchored to a position in a finished
                // video, so they have nothing to say on a live broadcast -
                // live chat is the running commentary instead.
                if (timedCommentsFeatureEnabled && timedCommentsActive && !isLive) {
                    TimedCommentsOverlay(
                        timedComments = timedComments,
                        positionMs = currentPosition,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = 24.dp,
                                end = 24.dp,
                                bottom = fullscreenOverlayBottom
                            )
                    )
                }

                // Outside the controls' visibility gate on purpose: a skip
                // happens without being asked for, so its undo has to be
                // reachable whether or not the chrome is up.
                SponsorBlockOverlay(
                    skipNotice = skipNotice,
                    manualSegment = manualSegment,
                    onUndoSkip = { viewModel.undoSponsorSkip() },
                    onSkipSegment = { viewModel.skipCurrentSegment() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = fullscreenOverlayBottom)
                )

                ResumePlaybackChip(
                    resumedFromMs = visibleResumedFromMs,
                    onPlayFromStart = { viewModel.playFromBeginning() },
                    onDismiss = { viewModel.dismissResumeNotice() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = fullscreenOverlayBottom)
                )

                // Regular comments stay inside immersive landscape as a
                // detached trailing panel. The video remains fullscreen behind
                // it; opening comments never rotates or returns to the watch
                // page, and the panel has its own close/back path.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showCommentsSheet && !isLive && !fullscreenIsPortrait,
                    enter = slideInHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { it }
                    ) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                ) {
                    CommentsPanel(
                        onOpenAuthor = onOpenChannel,
                        comments = comments,
                        replies = commentReplies,
                        loadingReplyIds = loadingReplyIds,
                        isLoading = isCommentsLoading,
                        isLoadingMore = isLoadingMoreComments,
                        commentsAvailable = commentsToken != null,
                        canComment = canComment,
                        isPosting = isPostingComment,
                        onLoadMore = { viewModel.loadMoreComments() },
                        onLoadReplies = { viewModel.loadReplies(it) },
                        onPostComment = { viewModel.postComment(it) },
                        onPostReply = { target, threadParent, text ->
                            viewModel.postReply(target, threadParent, text)
                        },
                        onLikeComment = { comment ->
                            requireLogin { viewModel.toggleCommentLike(comment) }
                        },
                        onDeleteComment = { comment -> viewModel.deleteComment(comment) },
                        onDismiss = { showCommentsSheet = false },
                        onSeekTo = { seconds -> viewModel.seekTo(seconds * 1000L, precise = true) },
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(400.dp)
                            .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                }

                // Landscape chat: a column docked to the right of the video
                // rather than a sheet over it, so the stream stays watchable
                // while chat scrolls. Slides in from the edge it lives on.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLiveChat && isLive,
                    enter = slideInHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { it }
                    ) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                ) {
                    LiveChatPanel(
                        messages = liveChatMessages,
                        banner = liveChatBanner,
                        isLoading = isLiveChatLoading,
                        isAvailable = isLiveChatAvailable,
                        canSend = canSendLiveChat,
                        isSending = isSendingLiveChat,
                        maxMessageLength = liveChatMaxLength,
                        restriction = liveChatRestriction,
                        onSend = { body, onFailure ->
                            viewModel.sendLiveChatMessage(body, onFailure)
                        },
                        onDismiss = { showLiveChat = false },
                        compact = true,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(landscapeChatWidth)
                            .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                            // Swallow taps: the gesture surface underneath
                            // would otherwise toggle the player controls when
                            // the user taps a gap between messages.
                            .clickable(
                                interactionSource = remember {
                                    androidx.compose.foundation.interaction.MutableInteractionSource()
                                },
                                indication = null
                            ) {}
                    )
                }
            }
        } else if (verticalLiveImmersive) {
            // Vertical live: the video is the screen. See
            // VerticalLivePlayerContent for why this is a layout decision and
            // not a different kind of content.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Full-bleed, so the PiP source rect is the whole window
                    // rather than the inline video box that is not composed here.
                    .onGloballyPositioned { coords ->
                        val rect = coords.boundsInWindow()
                        viewModel.setVideoSurfaceBounds(
                            android.graphics.Rect(
                                rect.left.toInt(),
                                rect.top.toInt(),
                                rect.right.toInt(),
                                rect.bottom.toInt()
                            )
                        )
                    }
            ) {
                VerticalLivePlayerContent(
                    exoPlayer = exoPlayer,
                    video = currentVideo,
                    showControls = showControls,
                    onToggleControls = { showControls = !showControls },
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    isBuffering = isBuffering,
                    hasError = playbackError != null,
                    errorMessage = playbackError?.message ?: "",
                    connectionAdvice = connectionAdvice,
                    progress = progress,
                    bufferedProgress = bufferedProgress,
                    duration = duration,
                    liveViewerCount = liveViewerCount,
                    chatMessages = liveChatMessages,
                    isChatAvailable = isLiveChatAvailable,
                    canSendChat = canSendLiveChat,
                    captionsActive = selectedCaption != null || embeddedTextTracks.any { it.isSelected },
                    captionCues = captionCues,
                    embeddedCueText = embeddedCueText,
                    captionTextSize = captionTextSize,
                    captionTextColor = captionTextColor,
                    captionBackground = captionBackground,
                    videoAspectRatio = videoAspectRatio,
                    // Account OR device subscription, same as everywhere else -
                    // engagement.isSubscribed only knows about the account.
                    isSubscribed = isSubscribedToChannel,
                    likeStatus = engagement?.likeStatus ?: LikeStatus.INDIFFERENT,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSeek = { newProgress -> viewModel.seekTo((newProgress * duration).toLong()) },
                    onScrubbingChanged = { isSeekScrubbing = it },
                    onSeekBackward = { seekBy(-VideoPlayerViewModel.SEEK_STEP_MS) },
                    onSeekForward = { seekBy(VideoPlayerViewModel.SEEK_STEP_MS) },
                    liveTargetOffsetMs = liveTargetOffsetMs,
                    onSeekToLive = { exoPlayer.seekToDefaultPosition() },
                    onBack = onBackClick,
                    onExitToPage = { showVideoPageForVerticalLive = true },
                    onOpenFullChat = { showLiveChat = true },
                    onCaptionsClick = {
                        viewModel.ensureCaptionsLoaded()
                        showCaptionsSheet = true
                    },
                    onSettings = { showPlaybackSettings = true },
                    onSubscribeClick = { requireSubscribeLogin { viewModel.toggleSubscribe() } },
                    onLikeClick = { requireLogin { viewModel.toggleLike() } },
                    onRetry = { viewModel.retryPlayback() },
                    onMinimizeDragDelta = onMinimizeDragDelta,
                    onMinimizeDragRelease = onMinimizeDragRelease
                )

                // The full panel, for sending and for reading back - the ticker
                // underneath is deliberately read-only.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLiveChat,
                    enter = slideInVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        initialOffsetY = { it }
                    ) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxHeight(0.6f)
                ) {
                    LiveChatPanel(
                        messages = liveChatMessages,
                        banner = liveChatBanner,
                        isLoading = isLiveChatLoading,
                        isAvailable = isLiveChatAvailable,
                        canSend = canSendLiveChat,
                        isSending = isSendingLiveChat,
                        maxMessageLength = liveChatMaxLength,
                        restriction = liveChatRestriction,
                        onSend = { body, onFailure ->
                            viewModel.sendLiveChatMessage(body, onFailure)
                        },
                        onDismiss = { showLiveChat = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // Portrait Layout
             Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Top only. The video stays below the status bar, on black,
                    // the way YouTube's portrait watch page does - taking it up
                    // there would crop a 16:9 frame and put the clock over the
                    // picture. The bottom inset is not applied here because it
                    // would clip the info list at the navigation bar;
                    // VideoInfoSection carries it as scrolling padding instead,
                    // so related videos pass under the bar.
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            ) {
                // Video Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Follows the video rather than assuming 16:9 - see
                        // videoBoxAspect above for why, and why only upward.
                        .aspectRatio(videoBoxAspect.coerceAtLeast(0.1f))
                        .background(Color.Black)
                        // Reported so PictureInPictureParams can animate the
                        // window out of the video rect instead of the whole
                        // screen. Window coordinates are what the system wants.
                        .onGloballyPositioned { coords ->
                            val rect = coords.boundsInWindow()
                            viewModel.setVideoSurfaceBounds(
                                android.graphics.Rect(
                                    rect.left.toInt(),
                                    rect.top.toInt(),
                                    rect.right.toInt(),
                                    rect.bottom.toInt()
                                )
                            )
                        }
                ) {
                    PortraitPlayerContent(
                        exoPlayer = exoPlayer,
                        // An HDR rendition needs the SurfaceView, and gives up
                        // the animated minimize for it.
                        useTextureSurface = supportsAnimatedMinimize(currentQuality),
                        holdsVideoSurface = holdsVideoSurface,
                        videoId = currentVideo.videoId,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls },
                        hasError = playbackError != null,
                        errorMessage = playbackError?.message ?: "",
                        connectionAdvice = connectionAdvice,
                        isLoading = isLoading,
                        isBuffering = isBuffering,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        progress = progress,
                        bufferedProgress = bufferedProgress,
                        seekPreview = seekPreview,
                        videoTitle = currentVideo.title,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { newProgress -> viewModel.seekTo((newProgress * duration).toLong()) },
                        onScrubbingChanged = { isSeekScrubbing = it },
                        onSeekBackward = { seekBy(-VideoPlayerViewModel.SEEK_STEP_MS) },
                        onSeekForward = { seekBy(VideoPlayerViewModel.SEEK_STEP_MS) },
                        onBack = onBackClick,
                        onFullscreenToggle = {
                            // Fullscreen from the button or the swipe-up takes
                            // the shape the video wants.
                            fullscreenIsPortrait = portraitFullscreenAvailable
                            isFullscreen = true
                        },
                        onSettings = { showPlaybackSettings = true },
                        chapters = chapters,
                        sponsorSegments = sponsorSegments,
                        onOpenChapters = { showChaptersSheet = true },
                        captionsActive = selectedCaption != null || embeddedTextTracks.any { it.isSelected },
                        onCaptionsClick = {
                            viewModel.ensureCaptionsLoaded()
                            showCaptionsSheet = true
                        },
                        captionCues = captionCues,
                        embeddedCueText = embeddedCueText,
                        captionTextSize = captionTextSize,
                        captionTextColor = captionTextColor,
                        captionBackground = captionBackground,
                        showQueueControls = queue != null,
                        hasPreviousInQueue = queue?.hasPrevious == true,
                        hasNextInQueue = queue?.hasNext == true,
                        onPreviousInQueue = { viewModel.playPreviousInQueue() },
                        onNextInQueue = { viewModel.playNextInQueue() },
                        isLive = isLive,
                        liveTargetOffsetMs = liveTargetOffsetMs,
                        onSeekToLive = { exoPlayer.seekToDefaultPosition() },
                        minimizeDragEnabled = true,
                        onMinimizeDragDelta = { delta ->
                            // The chrome shrinks with the page now, and a
                            // seek bar rendered at thumbnail size is noise
                            // rather than a control. Dropping it as the
                            // gesture starts leaves the picture travelling on
                            // its own, which is the whole point of the move.
                            if (showControls) showControls = false
                            onMinimizeDragDelta(delta)
                        },
                        onMinimizeDragRelease = onMinimizeDragRelease,
                        onRetry = { viewModel.retryPlayback() }
                    )

                    if (timedCommentsFeatureEnabled && timedCommentsActive && !isLive) {
                        // Keep the card clear of the seek bar while controls are up
                        val overlayBottomPadding by animateDpAsState(
                            targetValue = if (showControls) 64.dp else 12.dp,
                            label = "timedCommentsPadding"
                        )
                        TimedCommentsOverlay(
                            timedComments = timedComments,
                            positionMs = currentPosition,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, end = 12.dp, bottom = overlayBottomPadding)
                        )
                    }

                    // Same lift as the timed-comments card so the chip clears
                    // the seek bar while the controls are up, and drops closer
                    // to the edge once they go. Outside the control visibility
                    // gate deliberately: an automatic skip must be undoable
                    // whether or not the chrome happens to be showing.
                    val sponsorBottomPadding by animateDpAsState(
                        targetValue = if (showControls) 64.dp else 12.dp,
                        label = "sponsorChipPadding"
                    )
                    SponsorBlockOverlay(
                        skipNotice = skipNotice,
                        manualSegment = manualSegment,
                        onUndoSkip = { viewModel.undoSponsorSkip() },
                        onSkipSegment = { viewModel.skipCurrentSegment() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = sponsorBottomPadding)
                    )

                    ResumePlaybackChip(
                        resumedFromMs = visibleResumedFromMs,
                        onPlayFromStart = { viewModel.playFromBeginning() },
                        onDismiss = { viewModel.dismissResumeNotice() },
                        compact = true,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = sponsorBottomPadding)
                    )
                }
                
                // Info Area. The comments panel slides up over it, keeping the
                // video playing and interactive above while the list scrolls.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    VideoInfoSection(
                        video = currentVideo,
                        // Emptied rather than flagged: the section is
                        // already conditional on having something to show, so
                        // the switch reuses that path instead of adding a
                        // second way for it to be absent.
                        relatedVideos = if (showRelatedVideos) relatedVideos else emptyList(),
                        onVideoSelect = { viewModel.playVideo(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(pullToMinimize)
                            .background(MaterialTheme.colorScheme.surface),
                        engagement = engagement,
                        isSubscribed = isSubscribedToChannel,
                        onLikeClick = { requireLogin { viewModel.toggleLike() } },
                        onDislikeClick = { requireLogin { viewModel.toggleDislike() } },
                        dislikeCount = dislikeCount,
                        onSubscribeClick = { requireSubscribeLogin { viewModel.toggleSubscribe() } },
                        onBellChosen = { channelId, level ->
                            viewModel.setChannelBell(channelId, level, currentVideo.channelName)
                        },
                        bellWrites = bellWrites,
                        onCommentsClick = {
                            viewModel.ensureCommentsLoaded()
                            showCommentsSheet = true
                        },
                        onSaveClick = {
                            // No sign-in wall: device playlists are always a
                            // valid target, so only the account's half waits
                            // on a session.
                            if (isLoggedIn) viewModel.loadVideoPlaylists()
                            saveTargetVideo = currentVideo
                        },
                        onDownloadClick = { downloadTargetVideo = currentVideo },
                        onChannelClick = {
                            val channelId = engagement?.channelId ?: currentVideo.channelId
                            if (channelId != null) onOpenChannel(channelId)
                        },
                        onOpenChannelId = onOpenChannel,
                        onSeekTo = { seconds -> viewModel.seekTo(seconds * 1000L, precise = true) },
                        isOffline = isLocalPlayback,
                        onRelatedLongPress = { related ->
                            if (isLoggedIn) viewModel.loadVideoPlaylists()
                            saveTargetVideo = related
                        },
                        queue = queue,
                        onOpenQueue = { showQueueSheet = true },
                        isLive = isLive,
                        liveViewerCount = liveViewerCount,
                        onLiveChatClick = { showLiveChat = true },
                        showListenAsMusic = showListenAsMusic,
                        onListenAsMusic = onListenAsMusic
                    )

                    // Qualified: inside this Box the outer Column's scoped
                    // AnimatedVisibility extension would otherwise win overload
                    // resolution and fail to compile
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showCommentsSheet,
                        enter = slideInVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            initialOffsetY = { it }
                        ) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CommentsPanel(
                            onOpenAuthor = onOpenChannel,
                            comments = comments,
                            replies = commentReplies,
                            loadingReplyIds = loadingReplyIds,
                            isLoading = isCommentsLoading,
                            isLoadingMore = isLoadingMoreComments,
                            commentsAvailable = engagement?.commentsToken != null,
                            canComment = canComment,
                            isPosting = isPostingComment,
                            onLoadMore = { viewModel.loadMoreComments() },
                            onLoadReplies = { viewModel.loadReplies(it) },
                            onPostComment = { viewModel.postComment(it) },
                            onPostReply = { target, threadParent, text ->
                                viewModel.postReply(target, threadParent, text)
                            },
                            onLikeComment = { comment -> requireLogin { viewModel.toggleCommentLike(comment) } },
                            onDeleteComment = { comment -> viewModel.deleteComment(comment) },
                            onDismiss = { showCommentsSheet = false },
                            modifier = Modifier.fillMaxSize(),
                            onSeekTo = { seconds ->
                                viewModel.seekTo(seconds * 1000L, precise = true)
                                // Jumping to the moment a comment is about is
                                // pointless if the video stays paused behind
                                // the panel, so surface the player again.
                                showCommentsSheet = false
                            }
                        )
                    }

                    // Portrait chat: same slide-up treatment as comments, so
                    // the video keeps playing above while chat scrolls.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showLiveChat && isLive,
                        enter = slideInVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            initialOffsetY = { it }
                        ) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LiveChatPanel(
                            messages = liveChatMessages,
                            banner = liveChatBanner,
                            isLoading = isLiveChatLoading,
                            isAvailable = isLiveChatAvailable,
                            canSend = canSendLiveChat,
                            isSending = isSendingLiveChat,
                            maxMessageLength = liveChatMaxLength,
                            restriction = liveChatRestriction,
                            onSend = { body, onFailure ->
                                viewModel.sendLiveChatMessage(body, onFailure)
                            },
                            onDismiss = { showLiveChat = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Back closes an open panel before collapsing the player
    androidx.activity.compose.BackHandler(enabled = showCommentsSheet) {
        showCommentsSheet = false
    }

    androidx.activity.compose.BackHandler(enabled = showLiveChat) {
        showLiveChat = false
    }

    // Long-press options sheet, and the "save" action in the info area
    saveTargetVideo?.let { target ->
        val localVideoPlaylists by viewModel.localVideoPlaylists.collectAsState()
        val accountContaining by viewModel.accountPlaylistsContainingVideo.collectAsState()
        LaunchedEffect(target.videoId, isLoggedIn) {
            if (isLoggedIn && !isLocalPlayback) viewModel.loadVideoPlaylistMembership(target.videoId)
        }
        VideoOptionsSheet(
            video = target,
            playlists = videoPlaylists,
            isLoading = isVideoPlaylistsLoading,
            onSave = { playlistId, onResult ->
                viewModel.addVideoToPlaylist(playlistId, target, onResult)
            },
            onRemove = { playlistId, onResult ->
                viewModel.removeVideoFromPlaylist(playlistId, target, onResult)
            },
            onDownload = {
                saveTargetVideo = null
                downloadTargetVideo = target
            },
            onDismiss = { saveTargetVideo = null },
            // Only offered for Up Next rows. Long-pressing the video that is
            // currently playing and telling the app to hide it would leave the
            // user watching something they just dismissed.
            onNotInterested = if (target.videoId != currentVideo.videoId) {
                { viewModel.markNotInterested(target) }
            } else null,
            onBlockChannel = { viewModel.blockChannelFor(target) },
            isSignedOut = !isLoggedIn,
            onCreatePlaylist = { name, onCreated ->
                viewModel.createLocalVideoPlaylist(name, onCreated)
            },
            // Offered on Up Next rows only. Queueing the video that is already
            // playing has nothing to mean, and "Play next" on it would put it
            // after itself.
            onEnqueue = if (target.videoId != currentVideo.videoId) {
                { playNext -> viewModel.enqueueVideo(target, playNext) }
            } else null,
            onOpenChannel = target.channelId
                ?.takeIf { it.startsWith("UC") }
                ?.let { id -> { onOpenChannel(id) } },
            alreadyIn = run {
                val ids = localVideoPlaylists
                    .filter { list -> list.videos.any { it.videoId == target.videoId } }
                    .map { it.id }
                    .toSet()
                // Signed out the pinned Watch later row saves into the device
                // list, so that is what says whether it is already there.
                when {
                    !isLoggedIn &&
                        com.ivor.ivormusic.data.LocalVideoPlaylistsRepository.WATCH_LATER_ID in ids -> ids + "WL"
                    isLoggedIn -> ids + accountContaining
                    else -> ids
                }
            }
        )
    }

    downloadTargetVideo?.let { target ->
        VideoDownloadSheet(
            video = target,
            onDismiss = { downloadTargetVideo = null }
        )
    }

    // The playlist behind this video. Hosted here rather than inside the info
    // column so it is reachable from fullscreen too, where that column is not
    // composed at all.
    queue?.let { activeQueue ->
        if (showQueueSheet) {
            VideoQueueSheet(
                queue = activeQueue,
                onSelect = { index ->
                    viewModel.playQueueIndex(index)
                    showQueueSheet = false
                },
                onDismiss = { showQueueSheet = false },
                keepSystemBarsHidden = isFullscreen,
                onMove = { from, to -> viewModel.moveQueueItem(from, to) },
                onRemove = { index -> viewModel.removeQueueItem(index) },
                onUndoRemove = { viewModel.undoQueueRemoval() },
                onSaveAsPlaylist = { name, onSaved -> viewModel.saveQueueAsPlaylist(name, onSaved) }
            )
        }
    }

    // Sleep timer sheet. Sibling of the other sheets rather than nested in
    // playback settings, which closes on the way here.
    if (showSleepTimerSheet) {
        com.ivor.ivormusic.ui.components.SleepTimerSheet(
            endsAt = sleepTimerEndsAt,
            endOfTrack = sleepTimerEndOfVideo,
            onStartMinutes = { viewModel.startSleepTimer(it) },
            onStartEndOfTrack = { viewModel.startSleepTimerEndOfVideo() },
            onCancel = { viewModel.cancelSleepTimer() },
            onDismiss = { showSleepTimerSheet = false },
            endOfMediaLabel = stringResource(R.string.sleep_timer_end_of_video),
            endOfMediaDetail = stringResource(R.string.sleep_timer_detail_video),
            statusMediaHeadline = stringResource(R.string.sleep_timer_status_video),
            durationDetail = stringResource(R.string.sleep_timer_detail_duration_video)
        )
    }

    // Chapters list sheet
    if (showChaptersSheet) {
        ChaptersSheet(
            chapters = chapters,
            currentPositionMs = currentPosition,
            onChapterClick = {
                viewModel.seekToChapter(it)
                showChaptersSheet = false
            },
            onDismiss = { showChaptersSheet = false },
            keepSystemBarsHidden = isFullscreen
        )
    }

    // Captions / subtitles sheet
    if (showCaptionsSheet) {
        CaptionsSheet(
            tracks = captionTracks,
            selected = selectedCaption,
            embeddedTracks = embeddedTextTracks,
            onEmbeddedSelect = {
                viewModel.setEmbeddedTextTrack(it)
                showCaptionsSheet = false
            },
            isLocalMedia = isLocalPlayback,
            isLoading = isCaptionsLoading,
            textSize = captionTextSize,
            textColor = captionTextColor,
            background = captionBackground,
            onSelect = {
                viewModel.setCaptionTrack(it)
                showCaptionsSheet = false
            },
            onTextSizeChanged = viewModel::setCaptionTextSize,
            onTextColorChanged = viewModel::setCaptionTextColor,
            onBackgroundChanged = viewModel::setCaptionBackground,
            onDismiss = { showCaptionsSheet = false },
            keepSystemBarsHidden = isFullscreen
        )
    }

    // Sign-in dialog for like/dislike/subscribe when logged out
    if (showSignInDialog) {
        com.ivor.ivormusic.ui.auth.YouTubeAuthDialog(
            onDismiss = { showSignInDialog = false },
            onAuthSuccess = {
                showSignInDialog = false
                viewModel.onLoginStateChanged()
            }
        )
    }

    // One settings body serves the portrait sheet and fullscreen side panel.
    // Keeping the action wiring here prevents the two surfaces from drifting
    // back into different feature sets.
    val smoothMotionStatus by viewModel.frameInterpolationStatus.collectAsState()
    val smoothMotionAvailable by viewModel.smoothMotionAvailable.collectAsState()
    val smoothMotionOn by viewModel.smoothMotionOn.collectAsState()
    val smoothMotionText = when {
        !smoothMotionOn -> stringResource(R.string.vpc_smooth_motion_off)
        // Switched on a moment ago: the next progress poll reports what it is doing.
        else -> smoothMotionStatusText(smoothMotionStatus)
            ?: stringResource(R.string.vpc_smooth_motion_measuring)
    }
    val playbackSettingsContent: @Composable () -> Unit = {
        PlayerSettingsSections(
            isLoading = isLoading,
            qualities = selectableQualities,
            currentQuality = currentQuality,
            onQualitySelected = { viewModel.setQuality(it) },
            audioTracks = audioTracks,
            onAudioTrackSelected = { viewModel.setAudioTrack(it) },
            playbackSpeed = playbackSpeed,
            onSpeedPreview = { viewModel.setPlaybackSpeed(it, persist = false) },
            onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
            showEndBehavior = !isLive,
            autoplayEnabled = isAutoplayEnabled,
            onAutoplayChanged = viewModel::setAutoplayEnabled,
            isLooping = isLooping,
            onLoopChanged = { enabled ->
                if (enabled != isLooping) viewModel.toggleLooping()
            },
            sleepTimerEndsAt = sleepTimerEndsAt,
            sleepTimerEndOfVideo = sleepTimerEndOfVideo,
            onSleepTimerClick = {
                showPlaybackSettings = false
                showSleepTimerSheet = true
            },
            showPip = pipSupported,
            onPipClick = {
                showPlaybackSettings = false
                val host = activity as? androidx.activity.ComponentActivity
                if (host != null) enterPipMode(host, viewModel)
            },
            showComments = !isLive && commentsToken != null,
            commentsActive = showCommentsSheet,
            onCommentsClick = {
                showPlaybackSettings = false
                if (showCommentsSheet) {
                    showCommentsSheet = false
                } else {
                    viewModel.ensureCommentsLoaded()
                    showCommentsSheet = true
                    showControls = false
                }
            },
            showQueue = queue != null,
            onQueueClick = {
                showPlaybackSettings = false
                showQueueSheet = true
            },
            showTimedComments = timedCommentsFeatureEnabled && !isLive,
            timedCommentsActive = timedCommentsActive,
            onTimedCommentsChanged = { timedCommentsActive = it },
            showLiveChat = isLive && isLiveChatAvailable == true,
            liveChatActive = showLiveChat,
            onLiveChatChanged = { enabled ->
                showPlaybackSettings = false
                showLiveChat = enabled
            },
            showVerticalLive = verticalLiveAvailable && showVideoPageForVerticalLive,
            onVerticalLiveClick = {
                showPlaybackSettings = false
                showVideoPageForVerticalLive = false
            },
            // Fullscreen only: the portrait box fits by design and has no zoom
            // to toggle, and a crop past MAX_ACCEPTABLE_CROP hides the row
            // rather than offering a switch that does nothing.
            showZoomToFill = isFullscreen && zoomToFillAvailable,
            zoomToFillActive = isZoomedToFill,
            // Kept open like the timed-comments switch: the video stays
            // visible beside the panel, so the crop change answers instantly.
            onZoomToFillChanged = { isZoomedToFill = it },
            showListenAsMusic = showListenAsMusic,
            // The migration tears the player down, so the settings close
            // behind it the way the channel hand-off already does.
            onListenAsMusic = {
                showPlaybackSettings = false
                onListenAsMusic()
            },
            showSmoothMotion = smoothMotionAvailable,
            smoothMotionOn = smoothMotionOn,
            onSmoothMotionChanged = viewModel::setSmoothMotionOn,
            smoothMotionStatus = smoothMotionText
        )
    }

    // Playback settings: bottom sheet in portrait, side panel over the video
    // in fullscreen landscape so the video stays visible while adjusting
    androidx.activity.compose.BackHandler(enabled = showPlaybackSettings && isFullscreen) {
        showPlaybackSettings = false
    }

    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showPlaybackSettings,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                // No dimming: quality, zoom and Smooth motion are judged on the
                // picture beside the panel, so the tap-to-close layer is clear.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showPlaybackSettings = false }
                )
            }
            AnimatedVisibility(
                visible = showPlaybackSettings,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(
                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f),
                    initialOffsetX = { it }
                ) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(12.dp)
                        .width(360.dp),
                    shape = RoundedCornerShape(28.dp),
                    // The sheet's own container, so the deck and the tiles
                    // stand off it here as they do in portrait.
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.vpc_playback_settings),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalIconButton(onClick = { showPlaybackSettings = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        playbackSettingsContent()
                    }
                }
            }
        }
    } else if (showPlaybackSettings) {
        ModalBottomSheet(
            onDismissRequest = { showPlaybackSettings = false },
            sheetState = playbackSettingsSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.vpc_playback_settings),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                playbackSettingsContent()
            }
        }
    }
}

/**
 * Shared content for the portrait playback sheet and fullscreen side panel.
 *
 * **A control panel, the video twin of the music player's
 * `NowPlayingOptionsSheet`.** Two blocks. The top is the **values**: quality,
 * speed and (where there is a choice) the audio track, as a connected row of
 * tabs that each name their live value, over one deck that shows the options
 * for whichever tab is selected. The bottom is the **action grid**: the
 * switches as filled tiles, then the places this panel can leave to, unfilled
 * on the same four columns.
 *
 * It was one list of up to fifteen rows, with quality and speed as accordions
 * over sideways-scrolling chip strips: two taps and a swipe to reach a rung
 * that was off the edge, and every switch a full-width row with a sentence
 * under it. Here the deck is already open on quality when the panel appears,
 * every rung is on screen at once, and a switch carries its state in its fill.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerSettingsSections(
    isLoading: Boolean,
    qualities: List<VideoQuality>,
    currentQuality: VideoQuality?,
    onQualitySelected: (VideoQuality) -> Unit,
    /**
     * Audio tracks to choose between: a device file's own tracks, or a YouTube
     * video's original and dubs. Empty when there is only one, so the tab
     * simply does not appear.
     */
    audioTracks: List<PlayerTrackOption>,
    onAudioTrackSelected: (PlayerTrackOption) -> Unit,
    playbackSpeed: Float,
    /** One frame of a slider drag: applied so it can be judged, not remembered. */
    onSpeedPreview: (Float) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    showEndBehavior: Boolean,
    autoplayEnabled: Boolean,
    onAutoplayChanged: (Boolean) -> Unit,
    isLooping: Boolean,
    onLoopChanged: (Boolean) -> Unit,
    sleepTimerEndsAt: Long?,
    sleepTimerEndOfVideo: Boolean,
    onSleepTimerClick: () -> Unit,
    showPip: Boolean,
    onPipClick: () -> Unit,
    showComments: Boolean,
    commentsActive: Boolean,
    onCommentsClick: () -> Unit,
    showQueue: Boolean,
    onQueueClick: () -> Unit,
    showTimedComments: Boolean,
    timedCommentsActive: Boolean,
    onTimedCommentsChanged: (Boolean) -> Unit,
    showLiveChat: Boolean,
    liveChatActive: Boolean,
    onLiveChatChanged: (Boolean) -> Unit,
    showVerticalLive: Boolean,
    onVerticalLiveClick: () -> Unit,
    showZoomToFill: Boolean,
    zoomToFillActive: Boolean,
    onZoomToFillChanged: (Boolean) -> Unit,
    showListenAsMusic: Boolean,
    onListenAsMusic: () -> Unit,
    /** Smooth motion is turned on in Settings (and this GPU runs it), so the panel offers its switch. */
    showSmoothMotion: Boolean,
    smoothMotionOn: Boolean,
    onSmoothMotionChanged: (Boolean) -> Unit,
    /** What Smooth motion is doing for this video, or that it is switched off. */
    smoothMotionStatus: String
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val optionColors = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Exactly one deck, quality first: it is what this panel is most often
    // opened for, so it costs one tap rather than two.
    var deck by remember { mutableStateOf(SettingsPicker.QUALITY) }
    // The next video in a queue can have a single audio track, which takes the
    // tab away under an open panel.
    val shownDeck = if (deck == SettingsPicker.AUDIO && audioTracks.isEmpty()) {
        SettingsPicker.QUALITY
    } else {
        deck
    }

    // Ladder split for the quality deck: the HDR/Standard switch and the
    // follow-the-source effect live with the rungs they filter.
    val hdrQualities = qualities.filter(VideoQuality::isHdr)
    val standardQualities = qualities.filterNot(VideoQuality::isHdr)
    val showDynamicRangePicker = hdrQualities.isNotEmpty() && standardQualities.isNotEmpty()
    var showHdrQualities by remember(qualities) {
        mutableStateOf(currentQuality?.isHdr == true)
    }

    // Follow a real source change, including automatic HDR fallback. A
    // tab tap by itself does not change currentQuality, so it remains free
    // to browse the other ladder without snapping back.
    LaunchedEffect(currentQuality?.dynamicRange, qualities) {
        if (showDynamicRangePicker && currentQuality != null) {
            showHdrQualities = currentQuality.isHdr
        }
    }

    val visibleQualities = when {
        !showDynamicRangePicker -> qualities
        showHdrQualities -> hdrQualities
        else -> standardQualities
    }
    // Compared by label as well as URL: every rendition of a live stream
    // points at the same HLS manifest, so a URL comparison alone would light
    // up the whole ladder at once.
    fun isQualitySelected(quality: VideoQuality): Boolean = currentQuality != null &&
        quality.resolution == currentQuality.resolution &&
        quality.dynamicRange == currentQuality.dynamicRange &&
        quality.url == currentQuality.url

    val normalSpeedLabel = stringResource(R.string.vpc_normal)
    fun speedLabel(speed: Float): String = if (speed == 1f) normalSpeedLabel
        else "${speed.toString().removeSuffix(".0")}x"

    val qualityValue: String? = when {
        isLoading || qualities.isEmpty() -> null
        else -> currentQuality?.displayLabel ?: stringResource(R.string.vpc_quality_auto)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val tabs = buildList {
                    add(Triple(SettingsPicker.QUALITY, "Quality", qualityValue))
                    add(
                        Triple(
                            SettingsPicker.SPEED,
                            stringResource(R.string.song_options_speed),
                            speedLabel(playbackSpeed)
                        )
                    )
                    if (audioTracks.isNotEmpty()) {
                        add(
                            Triple(
                                SettingsPicker.AUDIO,
                                stringResource(R.string.vpc_audio_track),
                                audioTracks.firstOrNull { it.isSelected }?.label
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Tabs of one height even when one value wraps.
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(
                        ButtonGroupDefaults.ConnectedSpaceBetween
                    )
                ) {
                    tabs.forEachIndexed { index, (picker, label, value) ->
                        val selected = shownDeck == picker
                        ToggleButton(
                            checked = selected,
                            onCheckedChange = {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    deck = picker
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 60.dp),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            colors = optionColors,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (picker == SettingsPicker.QUALITY && isLoading) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = LocalContentColor.current
                                    )
                                } else {
                                    Text(
                                        // A dash rather than an empty line, so a
                                        // tab with nothing to name keeps its height.
                                        text = value ?: "-",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedContent(
                    targetState = shownDeck,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "PlaybackSettingsDeck"
                ) { target ->
                    when (target) {
                        SettingsPicker.QUALITY -> when {
                            isLoading -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ContainedLoadingIndicator()
                            }
                            qualities.isEmpty() -> Text(
                                text = stringResource(R.string.vpc_no_qualities),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            )
                            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (showDynamicRangePicker) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            ButtonGroupDefaults.ConnectedSpaceBetween
                                        )
                                    ) {
                                        listOf(
                                            true to stringResource(R.string.vpc_quality_hdr),
                                            false to stringResource(R.string.vpc_quality_standard),
                                        ).forEachIndexed { index, (showsHdr, label) ->
                                            val selected = showHdrQualities == showsHdr
                                            ToggleButton(
                                                checked = selected,
                                                onCheckedChange = { if (!selected) showHdrQualities = showsHdr },
                                                modifier = Modifier.weight(1f),
                                                shapes = if (index == 0) {
                                                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                } else {
                                                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                },
                                                colors = optionColors,
                                            ) {
                                                if (selected) {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(label)
                                            }
                                        }
                                    }
                                }
                                // The whole ladder on the panel's columns, so no
                                // rung is hidden behind a sideways scroll.
                                SettingsGrid(
                                    visibleQualities.map { quality ->
                                        { modifier ->
                                            val selected = isQualitySelected(quality)
                                            DeckCell(
                                                label = if (showDynamicRangePicker) {
                                                    quality.resolution
                                                } else {
                                                    quality.displayLabel
                                                },
                                                selected = selected,
                                                modifier = modifier,
                                                onClick = {
                                                    if (!selected) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                        onQualitySelected(quality)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        SettingsPicker.SPEED -> SpeedDeck(
                            playbackSpeed = playbackSpeed,
                            speedLabel = ::speedLabel,
                            onSpeedPreview = onSpeedPreview,
                            onSpeedSelected = onSpeedSelected
                        )

                        // Rows rather than cells: a track name carries a language,
                        // a codec and a channel layout, and any of those truncated
                        // into a cell is exactly the part that distinguishes it
                        // from the track above it.
                        SettingsPicker.AUDIO -> Column(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        ) {
                            audioTracks.forEach { track ->
                                com.ivor.ivormusic.ui.player.OptionRow(
                                    icon = Icons.Rounded.Audiotrack,
                                    title = track.label,
                                    subtitle = track.detail,
                                    trailing = if (track.isSelected) {
                                        com.ivor.ivormusic.ui.player.OptionRowTrailing.CHECK
                                    } else {
                                        com.ivor.ivormusic.ui.player.OptionRowTrailing.NONE
                                    },
                                    onClick = { if (!track.isSelected) onAudioTrackSelected(track) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // The switches, as tiles whose fill is the state. Autoplay, loop and
        // sleep lead because they answer the same question: what happens next.
        val sleepTimerActive = sleepTimerEndOfVideo || sleepTimerEndsAt != null
        val switches = buildList<@Composable (Modifier) -> Unit> {
            if (showEndBehavior) {
                add { modifier ->
                    com.ivor.ivormusic.ui.player.OptionTile(
                        icon = Icons.Rounded.PlayArrow,
                        label = stringResource(R.string.vpc_autoplay),
                        selected = autoplayEnabled,
                        modifier = modifier,
                        onClick = {
                            haptics.performHapticFeedback(
                                if (autoplayEnabled) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                            )
                            onAutoplayChanged(!autoplayEnabled)
                        }
                    )
                }
                add { modifier ->
                    com.ivor.ivormusic.ui.player.OptionTile(
                        icon = Icons.Rounded.RepeatOne,
                        label = stringResource(R.string.vpc_loop),
                        selected = isLooping,
                        modifier = modifier,
                        onClick = {
                            haptics.performHapticFeedback(
                                if (isLooping) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                            )
                            onLoopChanged(!isLooping)
                        }
                    )
                }
            }
            add { modifier ->
                com.ivor.ivormusic.ui.player.OptionTile(
                    icon = Icons.Rounded.Bedtime,
                    // A running timer names what is left in place of its title.
                    // Coarse minutes, no ticker: the picker sheet itself counts
                    // down once opened.
                    label = when {
                        sleepTimerEndOfVideo -> stringResource(R.string.sleep_timer_status_video)
                        sleepTimerEndsAt != null -> {
                            val remainingMin = ((sleepTimerEndsAt - System.currentTimeMillis()) / 60_000L)
                                .coerceAtLeast(1L).toInt()
                            stringResource(R.string.minutes_short, remainingMin)
                        }
                        else -> stringResource(R.string.sleep_timer_title)
                    },
                    selected = sleepTimerActive,
                    modifier = modifier,
                    onClick = onSleepTimerClick
                )
            }
            if (showZoomToFill) add { modifier ->
                com.ivor.ivormusic.ui.player.OptionTile(
                    icon = Icons.Rounded.ZoomIn,
                    label = stringResource(R.string.vpc_zoom_to_fill),
                    selected = zoomToFillActive,
                    modifier = modifier,
                    onClick = {
                        haptics.performHapticFeedback(
                            if (zoomToFillActive) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                        )
                        onZoomToFillChanged(!zoomToFillActive)
                    }
                )
            }
            // Settings makes Smooth motion available (behind its warning); this
            // tile turns it on and off from the video itself and is remembered.
            if (showSmoothMotion) add { modifier ->
                com.ivor.ivormusic.ui.player.OptionTile(
                    icon = Icons.Rounded.Animation,
                    label = stringResource(R.string.vpc_smooth_motion),
                    selected = smoothMotionOn,
                    modifier = modifier,
                    onClick = {
                        haptics.performHapticFeedback(
                            if (smoothMotionOn) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                        )
                        onSmoothMotionChanged(!smoothMotionOn)
                    }
                )
            }
            if (showTimedComments) add { modifier ->
                com.ivor.ivormusic.ui.player.OptionTile(
                    icon = Icons.AutoMirrored.Rounded.Comment,
                    label = stringResource(R.string.sp_timed_comments),
                    selected = timedCommentsActive,
                    modifier = modifier,
                    onClick = {
                        haptics.performHapticFeedback(
                            if (timedCommentsActive) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                        )
                        onTimedCommentsChanged(!timedCommentsActive)
                    }
                )
            }
            if (showLiveChat) add { modifier ->
                com.ivor.ivormusic.ui.player.OptionTile(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    label = stringResource(R.string.vp_live_chat),
                    selected = liveChatActive,
                    modifier = modifier,
                    onClick = { onLiveChatChanged(!liveChatActive) }
                )
            }
        }
        SettingsGrid(switches)

        // The one switch whose effect depends on the video, so it keeps the
        // line that answers "is it doing anything right now?".
        if (showSmoothMotion) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Animation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = smoothMotionStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // The places this panel leaves to, unfilled on the tiles' columns: each
        // closes the panel behind it, so none of them has a state to show.
        val shortcuts = buildList<@Composable (Modifier) -> Unit> {
            if (showPip) add { modifier ->
                com.ivor.ivormusic.ui.player.OptionUtility(
                    icon = Icons.Rounded.PictureInPictureAlt,
                    label = stringResource(R.string.vpc_pip),
                    modifier = modifier,
                    onClick = onPipClick
                )
            }
            if (showQueue) add { modifier ->
                com.ivor.ivormusic.ui.player.OptionUtility(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    label = stringResource(R.string.swipe_action_queue),
                    contentDescription = stringResource(R.string.vpc_queue_sub),
                    modifier = modifier,
                    onClick = onQueueClick
                )
            }
            if (showComments) add { modifier ->
                com.ivor.ivormusic.ui.player.OptionUtility(
                    icon = Icons.AutoMirrored.Rounded.Comment,
                    label = if (commentsActive) {
                        stringResource(R.string.cd_close_comments)
                    } else {
                        stringResource(R.string.cd_comments)
                    },
                    modifier = modifier,
                    onClick = onCommentsClick
                )
            }
            if (showListenAsMusic) add { modifier ->
                com.ivor.ivormusic.ui.player.OptionUtility(
                    icon = Icons.Rounded.MusicNote,
                    label = stringResource(R.string.vpc_listen_as_music),
                    contentDescription = stringResource(R.string.vpc_listen_as_music_sub),
                    modifier = modifier,
                    onClick = onListenAsMusic
                )
            }
            if (showVerticalLive) add { modifier ->
                com.ivor.ivormusic.ui.player.OptionUtility(
                    icon = Icons.Rounded.StayCurrentPortrait,
                    label = stringResource(R.string.vpc_vertical_live),
                    contentDescription = stringResource(R.string.vpc_vertical_live_sub),
                    modifier = modifier,
                    onClick = onVerticalLiveClick
                )
            }
        }
        SettingsGrid(shortcuts)
    }
}

/**
 * The playback rate: a slider for any value, over the four rates people
 * actually pick as one-tap cells.
 *
 * The rate applies as the thumb moves and is remembered once, when the finger
 * lifts - the rule the music player's speed slider follows, for the same
 * reason: speed is judged by watching, and a preference write per frame of a
 * drag is forty writes for one decision. The track is logarithmic so halving
 * and doubling get equal travel, and it detents on 1x with a haptic because
 * that is the one value people want to land on exactly.
 */
@Composable
private fun SpeedDeck(
    playbackSpeed: Float,
    speedLabel: (Float) -> String,
    onSpeedPreview: (Float) -> Unit,
    onSpeedSelected: (Float) -> Unit
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    var dragging by remember { mutableStateOf(false) }
    var sliderSpeed by remember { mutableFloatStateOf(playbackSpeed) }
    // Follow the player while the control is at rest - a preset, a new video
    // or a live stream can change the rate under an open panel - but never
    // under the finger, where it would fight the drag.
    LaunchedEffect(playbackSpeed, dragging) { if (!dragging) sliderSpeed = playbackSpeed }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = videoSpeedToSlider(sliderSpeed),
                onValueChange = { position ->
                    dragging = true
                    val snapped = snapVideoSpeed(videoSliderToSpeed(position))
                    if (snapped != sliderSpeed) {
                        if (snapped == 1f) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                        sliderSpeed = snapped
                        onSpeedPreview(snapped)
                    }
                },
                onValueChangeFinished = {
                    dragging = false
                    onSpeedSelected(sliderSpeed)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            // Only where it leads somewhere: at 1x there is nothing to reset.
            AnimatedVisibility(visible = sliderSpeed != 1f) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        dragging = false
                        sliderSpeed = 1f
                        onSpeedSelected(1f)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_speed_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        SettingsGrid(
            SPEED_PRESETS.map { preset ->
                { modifier ->
                    val selected = sliderSpeed == preset
                    DeckCell(
                        label = speedLabel(preset),
                        selected = selected,
                        modifier = modifier,
                        onClick = {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                dragging = false
                                sliderSpeed = preset
                                onSpeedSelected(preset)
                            }
                        }
                    )
                }
            }
        )
    }
}

/**
 * One option inside a deck - a quality rung, a speed preset. The selected one
 * fills and squares off, the selection language of the tiles below it.
 */
@Composable
private fun DeckCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corner by animateDpAsState(
        targetValue = if (selected) 12.dp else 24.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "deckCellCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "deckCellContainer"
    )
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                // Two lines: "2160p Dolby Vision" in a quarter of the panel.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Cells laid on the panel's four columns, a row at a time. A short last row
 * keeps its columns and leaves the rest empty rather than stretching, so every
 * cell in the panel sits under the one above it; cells in a row share the
 * height of the tallest, for the label that wraps at a large font scale.
 */
@Composable
private fun SettingsGrid(cells: List<@Composable (Modifier) -> Unit>) {
    if (cells.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(SETTINGS_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { cell ->
                    cell(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                repeat(SETTINGS_COLUMNS - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Which deck the playback settings are showing; exactly one, chosen by the tabs. */
private enum class SettingsPicker { QUALITY, SPEED, AUDIO }

/** Columns in the playback settings panel: deck cells, tiles and shortcuts share them. */
private const val SETTINGS_COLUMNS = 4

/** The rates worth a one-tap cell; the slider reaches everything between and beyond. */
private val SPEED_PRESETS = listOf(1f, 1.25f, 1.5f, 2f)

/** The slider's floor, the one [VideoPlayerViewModel.setPlaybackSpeed] enforces. */
private const val VIDEO_MIN_SPEED = 0.25f

private val VIDEO_SPEED_LOG_SPAN =
    kotlin.math.ln(com.ivor.ivormusic.data.ThemePreferences.MAX_PLAYBACK_SPEED / VIDEO_MIN_SPEED)

private fun videoSpeedToSlider(speed: Float): Float = (kotlin.math.ln(
    speed.coerceIn(VIDEO_MIN_SPEED, com.ivor.ivormusic.data.ThemePreferences.MAX_PLAYBACK_SPEED) / VIDEO_MIN_SPEED
) / VIDEO_SPEED_LOG_SPAN).coerceIn(0f, 1f)

private fun videoSliderToSpeed(position: Float): Float =
    VIDEO_MIN_SPEED * kotlin.math.exp(position.coerceIn(0f, 1f) * VIDEO_SPEED_LOG_SPAN)

/**
 * Round a raw slider position to five percent (a quarter above 2x, where a
 * finger moves a lot of speed), with a wider catch around 1x so a drag settles
 * on exactly normal speed rather than on the step either side of it.
 */
private fun snapVideoSpeed(raw: Float): Float {
    val bounded = raw.coerceIn(VIDEO_MIN_SPEED, com.ivor.ivormusic.data.ThemePreferences.MAX_PLAYBACK_SPEED)
    if (kotlin.math.abs(bounded - 1f) < 0.03f) return 1f
    return if (bounded <= 2f) (bounded * 20f).roundToInt() / 20f else (bounded * 4f).roundToInt() / 4f
}

/** The Smooth motion read-out for the playback settings panel, or null when the setting is off. */
@Composable
private fun smoothMotionStatusText(status: FrameInterpolationStatus): String? = when (status) {
    FrameInterpolationStatus.Disabled -> null
    FrameInterpolationStatus.NeedsRestart -> stringResource(R.string.vpc_smooth_motion_restart)
    FrameInterpolationStatus.Measuring -> stringResource(R.string.vpc_smooth_motion_measuring)
    is FrameInterpolationStatus.Active -> when (status.limit) {
        FrameInterpolationStatus.RateLimit.NONE ->
            stringResource(R.string.vpc_smooth_motion_active, status.sourceFps, status.outputFps)
        FrameInterpolationStatus.RateLimit.RESOLUTION -> stringResource(
            R.string.vpc_smooth_motion_active_resolution, status.sourceFps, status.outputFps, status.targetFps
        )
        FrameInterpolationStatus.RateLimit.SCREEN ->
            stringResource(R.string.vpc_smooth_motion_active_screen, status.sourceFps, status.outputFps)
    }
    is FrameInterpolationStatus.NotNeeded ->
        stringResource(R.string.vpc_smooth_motion_not_needed, status.playingFps)
    FrameInterpolationStatus.Live -> stringResource(R.string.vpc_smooth_motion_live)
    FrameInterpolationStatus.Hdr -> stringResource(R.string.vpc_smooth_motion_hdr)
    FrameInterpolationStatus.Hot -> stringResource(R.string.vpc_smooth_motion_hot)
    FrameInterpolationStatus.BatterySaver -> stringResource(R.string.vpc_smooth_motion_battery)
    FrameInterpolationStatus.CannotKeepUp -> stringResource(R.string.vpc_smooth_motion_cannot_keep_up)
    FrameInterpolationStatus.Unsupported -> stringResource(R.string.vpc_smooth_motion_unsupported)
}

/**
 * Keeps immersive fullscreen intact while a ModalBottomSheet is open. The
 * sheet lives in its own window, which does not inherit the activity's
 * hidden-system-bars state — so the moment it opens, Android re-shows the
 * status/navigation bars over the video. Hiding them on the sheet's own
 * window prevents that. Best-effort: if the sheet implementation is not
 * dialog-backed this quietly does nothing.
 */
@Composable
internal fun KeepSystemBarsHidden(enabled: Boolean) {
    if (!enabled) return
    val view = LocalView.current
    DisposableEffect(view) {
        var parent: android.view.ViewParent? = view.parent
        while (parent != null && parent !is DialogWindowProvider) parent = parent.parent
        (parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
}

/**
 * Bottom sheet listing the video's chapters. The chapter containing the
 * current playback position is highlighted; tapping a row seeks to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersSheet(
    chapters: List<VideoChapter>,
    currentPositionMs: Long,
    onChapterClick: (VideoChapter) -> Unit,
    onDismiss: () -> Unit,
    keepSystemBarsHidden: Boolean = false
) {
    val activeIndex = currentChapterIndex(chapters, currentPositionMs)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        KeepSystemBarsHidden(keepSystemBarsHidden)
        Text(
            text = stringResource(R.string.vp_chapters),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            itemsIndexed(chapters) { index, chapter ->
                val isActive = index == activeIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterClick(chapter) }
                        .background(
                            if (isActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!chapter.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = chapter.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .width(96.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                        Text(
                            text = formatChapterTime(chapter.startMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isActive) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.cd_now_playing),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet listing available caption tracks with an "Off" option at the
 * top. The selected track is checked. Shows a spinner while the track list is
 * still loading and an empty-state when the video has no captions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptionsSheet(
    tracks: List<CaptionTrack>,
    selected: CaptionTrack?,
    /**
     * Subtitle tracks inside the file being played, for device videos. When
     * this is non-empty it replaces [tracks] in the language list: the two can
     * never coexist, since one comes from a YouTube watch page and the other
     * from a container on disk.
     */
    embeddedTracks: List<PlayerTrackOption>,
    onEmbeddedSelect: (PlayerTrackOption?) -> Unit,
    /** Changes only the empty-state wording, which differs materially. */
    isLocalMedia: Boolean,
    isLoading: Boolean,
    textSize: Float,
    textColor: CaptionTextColor,
    background: CaptionBackground,
    onSelect: (CaptionTrack?) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onTextColorChanged: (CaptionTextColor) -> Unit,
    onBackgroundChanged: (CaptionBackground) -> Unit,
    onDismiss: () -> Unit,
    keepSystemBarsHidden: Boolean = false
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        KeepSystemBarsHidden(keepSystemBarsHidden)
        Text(
            text = stringResource(R.string.vp_captions),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.vpc_language),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            when {
                embeddedTracks.isNotEmpty() -> {
                    item {
                        CaptionRow(
                            label = stringResource(R.string.vpc_off),
                            checked = embeddedTracks.none { it.isSelected },
                            onClick = { onEmbeddedSelect(null) }
                        )
                    }
                    items(embeddedTracks, key = { it.id }) { track ->
                        CaptionRow(
                            label = track.label,
                            checked = track.isSelected,
                            onClick = { onEmbeddedSelect(track) }
                        )
                    }
                }
                // A local file's subtitle tracks are known the moment it is
                // read, so there is nothing to wait for and no spinner: an
                // empty list here is the answer, not a pending one. A download
                // is local too but brings the captions saved beside it, which
                // arrive in [tracks] and are listed below.
                isLocalMedia && tracks.isEmpty() && !isLoading -> item {
                    Text(
                        text = stringResource(R.string.vpc_no_embedded_subtitles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                    )
                }
                isLoading && tracks.isEmpty() -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
                tracks.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.vpc_no_captions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                    )
                }
                else -> {
                    item {
                        CaptionRow(
                            label = stringResource(R.string.vpc_off),
                            checked = selected == null,
                            onClick = { onSelect(null) }
                        )
                    }
                    itemsIndexed(
                        items = tracks,
                        key = { index, track -> "${track.languageCode}:${track.isAutoGenerated}:$index" }
                    ) { _, track ->
                        val label = if (track.isAutoGenerated) "${track.name} (auto)" else track.name
                        CaptionRow(
                            label = label,
                            checked = selected == track,
                            onClick = { onSelect(track) }
                        )
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = stringResource(R.string.settings_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            item {
                CaptionTextSizeSlider(
                    value = textSize,
                    onValueCommitted = onTextSizeChanged
                )
            }
            item {
                CaptionChoiceRow(
                    label = "Text color",
                    options = listOf(
                        CaptionTextColor.WHITE to stringResource(R.string.vpc_white),
                        CaptionTextColor.YELLOW to stringResource(R.string.vpc_yellow)
                    ),
                    selected = textColor,
                    onSelect = onTextColorChanged
                )
            }
            item {
                CaptionChoiceRow(
                    label = "Background",
                    options = listOf(
                        CaptionBackground.NONE to stringResource(R.string.vpc_none),
                        CaptionBackground.TRANSLUCENT to stringResource(R.string.vpc_soft),
                        CaptionBackground.SOLID to stringResource(R.string.vpc_solid)
                    ),
                    selected = background,
                    onSelect = onBackgroundChanged
                )
            }
        }
    }
}

@Composable
private fun CaptionTextSizeSlider(
    value: Float,
    onValueCommitted: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    val percent = (sliderValue * 100f).roundToInt()

    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.vpc_text_size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { raw ->
                sliderValue = ((raw * 4f).roundToInt() / 4f)
                    .coerceIn(CAPTION_TEXT_SCALE_MIN, CAPTION_TEXT_SCALE_MAX)
            },
            onValueChangeFinished = { onValueCommitted(sliderValue) },
            valueRange = CAPTION_TEXT_SCALE_MIN..CAPTION_TEXT_SCALE_MAX,
            steps = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "75%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "250%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun <T> CaptionChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, optionLabel) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = {
                        Text(
                            text = optionLabel,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CaptionRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
            color = if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (checked) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Index of the chapter that contains [positionMs], or -1 when none applies. */
internal fun currentChapterIndex(chapters: List<VideoChapter>, positionMs: Long): Int =
    chapters.indexOfLast { positionMs >= it.startMs }

/** mm:ss or h:mm:ss for a chapter start time. */
internal fun formatChapterTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%d:%02d", m, s)
}
