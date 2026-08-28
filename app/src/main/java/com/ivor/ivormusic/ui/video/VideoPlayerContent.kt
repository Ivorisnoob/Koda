package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.ivor.ivormusic.data.VideoChapter
import com.ivor.ivormusic.data.VideoItem
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
    /**
     * Open the playing video's creator. Routed out to the host: the channel
     * page is a NavHost destination and this player is drawn above the NavHost,
     * so the host is the layer that both navigates and minimises this player on
     * the way there.
     */
    onOpenChannel: (String) -> Unit = {},
    // Swipe-down-to-minimize: raw drag deltas / release velocity from the
    // portrait video surface, driving the overlay's expand progress
    onMinimizeDragDelta: (Float) -> Unit = {},
    onMinimizeDragRelease: (Float) -> Unit = {}
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
    val captionCues by viewModel.captionCues.collectAsState()
    val captionTextSize by viewModel.captionTextSize.collectAsState()
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
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val engagement by viewModel.engagement.collectAsState()
    // Account subscription OR device subscription - engagement only knows the
    // first, and read alone it showed "Subscribe" for locally followed channels.
    val isSubscribedToChannel by viewModel.isSubscribedToChannel.collectAsState()
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

    // Cast state. The video card, both chromes and the settings sheet all
    // need to know where the media actually is.
    val isCasting by viewModel.isCasting.collectAsState()
    val castDeviceName by viewModel.castDeviceName.collectAsState()
    val selectableQualities by viewModel.selectableQualities.collectAsState()

    // Local UI State
    var showControls by remember { mutableStateOf(false) }
    // Keyed to the video so a source switch can never inherit a drag that
    // belonged to the old timeline.
    var isSeekScrubbing by remember(video?.videoId) { mutableStateOf(false) }
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
    var showCastSheet by remember { mutableStateOf(false) }

    // Discovery is a radio cost, not a background service: it runs exactly
    // while the device sheet is open and stops the moment it closes.
    LaunchedEffect(showCastSheet) {
        if (showCastSheet) {
            viewModel.startCastDiscovery()
        } else {
            viewModel.stopCastDiscovery()
        }
    }

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
    val configuration = LocalConfiguration.current
    val landscapeChatWidth = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * 0.34f).dp.coerceIn(260.dp, 360.dp)
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

    // Autoplay can hand a landscape video to a fullscreen still locked upright
    // for the portrait one before it, which plays the next video in a
    // letterboxed strip with the phone held the wrong way round.
    LaunchedEffect(portraitFullscreenAvailable) {
        if (!portraitFullscreenAvailable) fullscreenIsPortrait = false
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, isSeekScrubbing) {
        if (showControls && isPlaying && !isSeekScrubbing) {
            delay(4000)
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
    
    // Fullscreen / Immersive. The app is portrait-locked (MainActivity), so
    // fullscreen temporarily requests sensor landscape and every exit path
    // restores PORTRAIT — never UNSPECIFIED, which used to leave the whole
    // app free-rotating in broken half-landscape states.
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
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                onOpenChapters = { showChaptersSheet = true },
                casting = isCasting,
                castDeviceName = castDeviceName,
                castingArtworkUrl = currentVideo.thumbnailUrl,
                onCastClick = { showCastSheet = true },
                captionsActive = selectedCaption != null,
                onCaptionsClick = {
                    viewModel.ensureCaptionsLoaded()
                    showCaptionsSheet = true
                },
                captionCues = if (isCasting) emptyList() else captionCues,
                captionTextSize = captionTextSize,
                captionTextColor = captionTextColor,
                captionBackground = captionBackground,
                showQueueControls = queue != null,
                hasPreviousInQueue = queue?.hasPrevious == true,
                hasNextInQueue = queue?.hasNext == true,
                onPreviousInQueue = { viewModel.playPreviousInQueue() },
                onNextInQueue = { viewModel.playNextInQueue() },
                isLive = isLive,
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
                            .padding(start = 24.dp, end = 24.dp, bottom = 104.dp)
                    )
                }

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
                        onSeekTo = { seconds -> viewModel.seekTo(seconds * 1000L) },
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
                    progress = progress,
                    bufferedProgress = bufferedProgress,
                    duration = duration,
                    liveViewerCount = liveViewerCount,
                    chatMessages = liveChatMessages,
                    isChatAvailable = isLiveChatAvailable,
                    canSendChat = canSendLiveChat,
                    captionsActive = selectedCaption != null,
                    captionCues = captionCues,
                    captionTextSize = captionTextSize,
                    captionTextColor = captionTextColor,
                    captionBackground = captionBackground,
                    videoAspectRatio = videoAspectRatio,
                    // Account OR device subscription, same as everywhere else -
                    // engagement.isSubscribed only knows about the account.
                    isSubscribed = isSubscribedToChannel,
                    likeStatus = engagement?.likeStatus ?: LikeStatus.INDIFFERENT,
                    casting = isCasting,
                    castDeviceName = castDeviceName,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSeek = { newProgress -> viewModel.seekTo((newProgress * duration).toLong()) },
                    onScrubbingChanged = { isSeekScrubbing = it },
                    onSeekBackward = { seekBy(-VideoPlayerViewModel.SEEK_STEP_MS) },
                    onSeekForward = { seekBy(VideoPlayerViewModel.SEEK_STEP_MS) },
                    onSeekToLive = { exoPlayer.seekToDefaultPosition() },
                    onBack = onBackClick,
                    onExitToPage = { showVideoPageForVerticalLive = true },
                    onOpenFullChat = { showLiveChat = true },
                    onCaptionsClick = {
                        viewModel.ensureCaptionsLoaded()
                        showCaptionsSheet = true
                    },
                    onCastClick = { showCastSheet = true },
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
                        videoId = currentVideo.videoId,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls },
                        hasError = playbackError != null,
                        errorMessage = playbackError?.message ?: "",
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
                        onOpenChapters = { showChaptersSheet = true },
                        casting = isCasting,
                        castDeviceName = castDeviceName,
                        castingArtworkUrl = currentVideo.thumbnailUrl,
                        onCastClick = { showCastSheet = true },
                        captionsActive = selectedCaption != null,
                        onCaptionsClick = {
                            viewModel.ensureCaptionsLoaded()
                            showCaptionsSheet = true
                        },
                        captionCues = if (isCasting) emptyList() else captionCues,
                        captionTextSize = captionTextSize,
                        captionTextColor = captionTextColor,
                        captionBackground = captionBackground,
                        showQueueControls = queue != null,
                        hasPreviousInQueue = queue?.hasPrevious == true,
                        hasNextInQueue = queue?.hasNext == true,
                        onPreviousInQueue = { viewModel.playPreviousInQueue() },
                        onNextInQueue = { viewModel.playNextInQueue() },
                        isLive = isLive,
                        onSeekToLive = { exoPlayer.seekToDefaultPosition() },
                        minimizeDragEnabled = true,
                        onMinimizeDragDelta = onMinimizeDragDelta,
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
                        relatedVideos = relatedVideos,
                        onVideoSelect = { viewModel.playVideo(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(pullToMinimize)
                            .background(MaterialTheme.colorScheme.surface),
                        engagement = engagement,
                        isSubscribed = isSubscribedToChannel,
                        onLikeClick = { requireLogin { viewModel.toggleLike() } },
                        onDislikeClick = { requireLogin { viewModel.toggleDislike() } },
                        onSubscribeClick = { requireSubscribeLogin { viewModel.toggleSubscribe() } },
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
                        onSeekTo = { seconds -> viewModel.seekTo(seconds * 1000L) },
                        isOffline = isLocalPlayback,
                        onRelatedLongPress = { related ->
                            if (isLoggedIn) viewModel.loadVideoPlaylists()
                            saveTargetVideo = related
                        },
                        queue = queue,
                        onOpenQueue = { showQueueSheet = true },
                        isLive = isLive,
                        liveViewerCount = liveViewerCount,
                        onLiveChatClick = { showLiveChat = true }
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
                                viewModel.seekTo(seconds * 1000L)
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
        VideoOptionsSheet(
            video = target,
            playlists = videoPlaylists,
            isLoading = isVideoPlaylistsLoading,
            onSave = { playlistId, onResult ->
                viewModel.addVideoToPlaylist(playlistId, target, onResult)
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
                if (!isLoggedIn &&
                    com.ivor.ivormusic.data.LocalVideoPlaylistsRepository.WATCH_LATER_ID in ids
                ) ids + "WL" else ids
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
                onUndoRemove = { viewModel.undoQueueRemoval() }
            )
        }
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

    // Cast device sheet
    if (showCastSheet) {
        CastSheet(
            viewModel = viewModel,
            onDismiss = { showCastSheet = false }
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
    val playbackSettingsContent: @Composable () -> Unit = {
        PlayerSettingsSections(
            isLoading = isLoading,
            qualities = selectableQualities,
            currentQuality = currentQuality,
            onQualitySelected = { viewModel.setQuality(it) },
            playbackSpeed = playbackSpeed,
            onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
            showEndBehavior = !isLive,
            autoplayEnabled = isAutoplayEnabled,
            onAutoplayChanged = viewModel::setAutoplayEnabled,
            isLooping = isLooping,
            onLoopChanged = { enabled ->
                if (enabled != isLooping) viewModel.toggleLooping()
            },
            // Nothing local renders while casting, so there is no window to
            // shrink into a PiP card - the entry point would open a picture of
            // the casting card, which helps nobody.
            showPip = pipSupported && !isCasting,
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
            }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
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
                        Spacer(modifier = Modifier.height(16.dp))
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
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.vpc_playback_settings),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                playbackSettingsContent()
            }
        }
    }
}

/**
 * Shared content for the portrait playback sheet and fullscreen side panel.
 * End behavior is explicit, quality and speed remain quick pill choices, and
 * low-frequency actions are labeled rows instead of mystery icons over video.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerSettingsSections(
    isLoading: Boolean,
    qualities: List<VideoQuality>,
    currentQuality: VideoQuality?,
    onQualitySelected: (VideoQuality) -> Unit,
    playbackSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    showEndBehavior: Boolean,
    autoplayEnabled: Boolean,
    onAutoplayChanged: (Boolean) -> Unit,
    isLooping: Boolean,
    onLoopChanged: (Boolean) -> Unit,
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
    onVerticalLiveClick: () -> Unit
) {
    val optionColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (showEndBehavior) {
        SettingsSectionLabel(icon = Icons.Rounded.PlayArrow, label = "When video ends")
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column {
                SettingsToggleRow(
                    icon = Icons.Rounded.PlayArrow,
                    title = stringResource(R.string.vpc_autoplay),
                    supportingText = if (autoplayEnabled) {
                        stringResource(R.string.vpc_autoplay_on)
                    } else {
                        stringResource(R.string.vpc_autoplay_off)
                    },
                    checked = autoplayEnabled,
                    onCheckedChange = onAutoplayChanged
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 64.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.RepeatOne,
                    title = stringResource(R.string.vpc_loop),
                    supportingText = if (!autoplayEnabled) {
                        stringResource(R.string.vpc_loop_note)
                    } else if (isLooping) {
                        stringResource(R.string.vpc_loop_off)
                    } else {
                        stringResource(R.string.vpc_loop_on)
                    },
                    checked = isLooping,
                    onCheckedChange = onLoopChanged
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    SettingsSectionLabel(icon = Icons.Rounded.Tune, label = "Quality")
    Spacer(modifier = Modifier.height(12.dp))
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator()
        }
    } else if (qualities.isEmpty()) {
        Text(
            text = stringResource(R.string.vpc_no_qualities),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            qualities.forEach { quality ->
                // Compared by label, not URL: every rendition of a live
                // stream points at the same HLS manifest, so a URL comparison
                // would light up the whole ladder at once.
                val selected = currentQuality != null &&
                    quality.resolution == currentQuality.resolution &&
                    quality.url == currentQuality.url
                ToggleButton(
                    checked = selected,
                    onCheckedChange = { if (!selected) onQualitySelected(quality) },
                    colors = optionColors
                ) {
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(quality.resolution)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    SettingsSectionLabel(icon = Icons.Rounded.Speed, label = "Playback speed")
    Spacer(modifier = Modifier.height(12.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoPlayerViewModel.PLAYBACK_SPEED_OPTIONS.forEach { speed ->
            val selected = speed == playbackSpeed
            val label = if (speed == 1f) stringResource(R.string.vpc_normal)
                else "${speed.toString().removeSuffix(".0")}x"
            ToggleButton(
                checked = selected,
                onCheckedChange = { if (!selected) onSpeedSelected(speed) },
                colors = optionColors
            ) {
                Text(label)
            }
        }
    }

    val hasSecondaryActions = showPip || showComments || showQueue ||
        showTimedComments || showLiveChat || showVerticalLive
    if (hasSecondaryActions) {
        Spacer(modifier = Modifier.height(24.dp))
        SettingsSectionLabel(icon = Icons.Rounded.Tune, label = "More controls")
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column {
                if (showPip) {
                    SettingsActionRow(
                        icon = Icons.Rounded.PictureInPictureAlt,
                        title = stringResource(R.string.vpc_pip),
                        supportingText = stringResource(R.string.vpc_pip_sub),
                        onClick = onPipClick
                    )
                }
                if (showComments) {
                    SettingsActionRow(
                        icon = Icons.AutoMirrored.Rounded.Comment,
                        title = if (commentsActive) stringResource(R.string.cd_close_comments) else stringResource(R.string.cd_comments),
                        supportingText = if (commentsActive) {
                            stringResource(R.string.vpc_return_to_video)
                        } else {
                            stringResource(R.string.vpc_browse_conversation)
                        },
                        onClick = onCommentsClick
                    )
                }
                if (showQueue) {
                    SettingsActionRow(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        title = stringResource(R.string.your_playlists),
                        supportingText = stringResource(R.string.vpc_queue_sub),
                        onClick = onQueueClick
                    )
                }
                if (showTimedComments) {
                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Rounded.Comment,
                        title = stringResource(R.string.sp_timed_comments),
                        supportingText = stringResource(R.string.vpc_timed_comments_sub),
                        checked = timedCommentsActive,
                        onCheckedChange = onTimedCommentsChanged
                    )
                }
                if (showLiveChat) {
                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Rounded.Chat,
                        title = stringResource(R.string.vp_live_chat),
                        supportingText = stringResource(R.string.vpc_live_chat_sub),
                        checked = liveChatActive,
                        onCheckedChange = onLiveChatChanged
                    )
                }
                if (showVerticalLive) {
                    SettingsActionRow(
                        icon = Icons.Rounded.StayCurrentPortrait,
                        title = stringResource(R.string.vpc_vertical_live),
                        supportingText = stringResource(R.string.vpc_vertical_live_sub),
                        onClick = onVerticalLiveClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        supportingContent = { Text(supportingText) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    ) { Text(title) }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    supportingText: String,
    onClick: () -> Unit
) {
    ListItem(
        supportingContent = { Text(supportingText) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    ) { Text(title) }
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
                            label = "Off",
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

@Composable
private fun SettingsSectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
