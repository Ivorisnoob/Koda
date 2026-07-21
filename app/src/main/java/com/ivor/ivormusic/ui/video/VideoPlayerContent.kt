package com.ivor.ivormusic.ui.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.CaptionTrack
import com.ivor.ivormusic.data.VideoChapter
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    val availableQualities by viewModel.availableQualities.collectAsState()
    val currentQuality by viewModel.currentQuality.collectAsState()
    val relatedVideos by viewModel.relatedVideos.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val captionTracks by viewModel.captionTracks.collectAsState()
    val selectedCaption by viewModel.selectedCaption.collectAsState()
    val isCaptionsLoading by viewModel.isCaptionsLoading.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val engagement by viewModel.engagement.collectAsState()
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
    val channelVideos by viewModel.channelVideos.collectAsState()
    val isChannelVideosLoading by viewModel.isChannelVideosLoading.collectAsState()

    // Local UI State
    var showControls by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showCaptionsSheet by remember { mutableStateOf(false) }
    // Timed comments overlay toggle; persists across videos while the player is open
    var timedCommentsActive by remember { mutableStateOf(false) }
    
    // Progress polling (ViewModel doesn't poll, so we do it here or update ViewModel to poll)
    // Ideally ViewModel should emit progress, but for smoother slider we often poll in UI or VM. 
    // Let's poll in UI for now as we have the ExoPlayer instance in VM
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }
    var bufferedProgress by remember { mutableFloatStateOf(0f) }
    
    val exoPlayer = viewModel.exoPlayer
    val currentVideo = video

    if (currentVideo == null || exoPlayer == null) return

    // Double-tap seek helper: jump relative to the live playhead, clamped to the clip.
    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs)
            .coerceIn(0L, if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE)
        exoPlayer.seekTo(target)
    }

    LaunchedEffect(exoPlayer, currentVideo) {
        while (isActive) {
            if (exoPlayer.duration > 0) {
                duration = exoPlayer.duration
                currentPosition = exoPlayer.currentPosition
                progress = currentPosition.toFloat() / duration.toFloat()
                bufferedProgress = exoPlayer.bufferedPosition.toFloat() / duration.toFloat()
            }
            delay(500)
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Fetch the first page of comments once the overlay is active and the
    // comments entry token has arrived (engagement loads asynchronously)
    val commentsToken = engagement?.commentsToken
    LaunchedEffect(timedCommentsActive, commentsToken) {
        if (timedCommentsFeatureEnabled && timedCommentsActive && commentsToken != null) {
            viewModel.ensureCommentsLoaded()
        }
    }
    
    // Fullscreen / Immersive. The app is portrait-locked (MainActivity), so
    // fullscreen temporarily requests sensor landscape and every exit path
    // restores PORTRAIT — never UNSPECIFIED, which used to leave the whole
    // app free-rotating in broken half-landscape states.
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        fun setCutoutMode(mode: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
                window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = mode }
            }
        }

        if (isFullscreen) {
            // Allow content to draw behind system bars first
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            // Draw into the camera cutout area too, otherwise the system
            // letterboxes the window and background shows around the notch
            setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
            // Sensor landscape: both landscape directions work, like YouTube
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT)
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT)
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
                if (orientation == 1 && !isFullscreen) {
                    isFullscreen = true
                } else if (orientation == 0 && isFullscreen) {
                    isFullscreen = false
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    
    // Quality Sheet State
    var showQualitySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Comments Sheet + Sign-in Dialog State
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }

    // Save-to-playlist sheet (Save button or long-press on an Up Next video)
    // and the channel page sheet (tap on the channel row)
    var saveTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var showChannelSheet by remember { mutableStateOf(false) }

    // Gate authenticated actions behind login
    fun requireLogin(action: () -> Unit) {
        if (isLoggedIn) action() else showSignInDialog = true
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
            ) {
                FullscreenPlayerContent(
                exoPlayer = exoPlayer,
                showControls = showControls,
                onToggleControls = { showControls = !showControls },
                hasError = playbackError != null,
                errorMessage = playbackError?.message ?: "",
                isLoading = isLoading,
                isBuffering = isBuffering,
                isPlaying = isPlaying,
                isLooping = isLooping,
                currentPosition = currentPosition,
                duration = duration,
                progress = progress,
                bufferedProgress = bufferedProgress,
                videoTitle = currentVideo.title,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { newProgress -> exoPlayer.seekTo((newProgress * duration).toLong()) },
                onSeekBackward = { seekBy(-10_000L) },
                onSeekForward = { seekBy(10_000L) },
                onBack = { isFullscreen = false },
                onFullscreenToggle = { isFullscreen = false },
                onSettings = { showQualitySheet = true },
                onLoopToggle = { viewModel.toggleLooping() },
                showTimedCommentsButton = timedCommentsFeatureEnabled,
                timedCommentsActive = timedCommentsActive,
                onTimedCommentsToggle = { timedCommentsActive = !timedCommentsActive },
                chapters = chapters,
                onOpenChapters = { showChaptersSheet = true },
                captionsActive = selectedCaption != null,
                onCaptionsClick = {
                    viewModel.ensureCaptionsLoaded()
                    showCaptionsSheet = true
                }
            )

                if (timedCommentsFeatureEnabled && timedCommentsActive) {
                    TimedCommentsOverlay(
                        timedComments = timedComments,
                        positionMs = currentPosition,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, end = 24.dp, bottom = 104.dp)
                    )
                }
            }
        } else {
            // Portrait Layout
             Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                // Video Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    PortraitPlayerContent(
                        exoPlayer = exoPlayer,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls },
                        hasError = playbackError != null,
                        errorMessage = playbackError?.message ?: "",
                        isLoading = isLoading,
                        isBuffering = isBuffering,
                        isPlaying = isPlaying,
                        isLooping = isLooping,
                        currentPosition = currentPosition,
                        duration = duration,
                        progress = progress,
                        bufferedProgress = bufferedProgress,
                        videoTitle = currentVideo.title,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { newProgress -> exoPlayer.seekTo((newProgress * duration).toLong()) },
                        onSeekBackward = { seekBy(-10_000L) },
                        onSeekForward = { seekBy(10_000L) },
                        onBack = onBackClick,
                        onFullscreenToggle = { isFullscreen = true },
                        onSettings = { showQualitySheet = true },
                        onLoopToggle = { viewModel.toggleLooping() },
                        showTimedCommentsButton = timedCommentsFeatureEnabled,
                        timedCommentsActive = timedCommentsActive,
                        onTimedCommentsToggle = { timedCommentsActive = !timedCommentsActive },
                        chapters = chapters,
                        onOpenChapters = { showChaptersSheet = true },
                        captionsActive = selectedCaption != null,
                        onCaptionsClick = {
                            viewModel.ensureCaptionsLoaded()
                            showCaptionsSheet = true
                        },
                        minimizeDragEnabled = true,
                        onMinimizeDragDelta = onMinimizeDragDelta,
                        onMinimizeDragRelease = onMinimizeDragRelease
                    )

                    if (timedCommentsFeatureEnabled && timedCommentsActive) {
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
                            .background(MaterialTheme.colorScheme.surface),
                        engagement = engagement,
                        onLikeClick = { requireLogin { viewModel.toggleLike() } },
                        onDislikeClick = { requireLogin { viewModel.toggleDislike() } },
                        onSubscribeClick = { requireLogin { viewModel.toggleSubscribe() } },
                        onCommentsClick = {
                            viewModel.ensureCommentsLoaded()
                            showCommentsSheet = true
                        },
                        onSaveClick = {
                            requireLogin {
                                viewModel.loadVideoPlaylists()
                                saveTargetVideo = currentVideo
                            }
                        },
                        onChannelClick = {
                            viewModel.loadChannelVideos()
                            showChannelSheet = true
                        },
                        onRelatedLongPress = { related ->
                            requireLogin {
                                viewModel.loadVideoPlaylists()
                                saveTargetVideo = related
                            }
                        }
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Back closes the comments panel before collapsing the player
    androidx.activity.compose.BackHandler(enabled = showCommentsSheet && !isFullscreen) {
        showCommentsSheet = false
    }

    // Save to Watch Later / playlist sheet
    saveTargetVideo?.let { target ->
        SaveToPlaylistSheet(
            video = target,
            playlists = videoPlaylists,
            isLoading = isVideoPlaylistsLoading,
            onSave = { playlistId, onResult ->
                viewModel.addVideoToPlaylist(playlistId, target, onResult)
            },
            onDismiss = { saveTargetVideo = null }
        )
    }

    // Channel page sheet (latest uploads + subscribe)
    if (showChannelSheet) {
        ChannelSheet(
            channelName = currentVideo.channelName,
            channelIconUrl = currentVideo.channelIconUrl,
            subscriberCountText = engagement?.subscriberCountText ?: currentVideo.subscriberCount,
            isSubscribed = engagement?.isSubscribed == true,
            canSubscribe = engagement?.channelId != null,
            videos = channelVideos,
            isLoading = isChannelVideosLoading,
            onSubscribeClick = { requireLogin { viewModel.toggleSubscribe() } },
            onVideoClick = { video ->
                showChannelSheet = false
                viewModel.playVideo(video)
            },
            onDismiss = { showChannelSheet = false }
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
            isLoading = isCaptionsLoading,
            onSelect = {
                viewModel.setCaptionTrack(it)
                showCaptionsSheet = false
            },
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

    // Playback settings: bottom sheet in portrait, side panel over the video
    // in fullscreen landscape so the video stays visible while adjusting
    androidx.activity.compose.BackHandler(enabled = showQualitySheet && isFullscreen) {
        showQualitySheet = false
    }

    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showQualitySheet,
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
                        ) { showQualitySheet = false }
                )
            }
            AnimatedVisibility(
                visible = showQualitySheet,
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
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
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
                                text = "Playback settings",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalIconButton(onClick = { showQualitySheet = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerSettingsSections(
                            isLoading = isLoading,
                            qualities = availableQualities,
                            currentQualityUrl = currentQuality?.url,
                            onQualitySelected = { viewModel.setQuality(it) },
                            playbackSpeed = playbackSpeed,
                            onSpeedSelected = { viewModel.setPlaybackSpeed(it) }
                        )
                    }
                }
            }
        }
    } else if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Playback settings",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                PlayerSettingsSections(
                    isLoading = isLoading,
                    qualities = availableQualities,
                    currentQualityUrl = currentQuality?.url,
                    onQualitySelected = { viewModel.setQuality(it) },
                    playbackSpeed = playbackSpeed,
                    onSpeedSelected = { viewModel.setPlaybackSpeed(it) }
                )
            }
        }
    }
}

/**
 * Shared quality + speed pickers for the playback settings surface. Options
 * are expressive ToggleButton pills (shape-morph on select) laid out in a
 * FlowRow, so they wrap to the available width in both the portrait sheet
 * and the landscape side panel.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerSettingsSections(
    isLoading: Boolean,
    qualities: List<VideoQuality>,
    currentQualityUrl: String?,
    onQualitySelected: (VideoQuality) -> Unit,
    playbackSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val optionColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

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
            text = "No qualities available",
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
                val selected = quality.url == currentQualityUrl
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
            val label = if (speed == 1f) "Normal"
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
private fun KeepSystemBarsHidden(enabled: Boolean) {
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
            text = "Chapters",
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
                            contentDescription = "Now playing",
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
    onSelect: (CaptionTrack?) -> Unit,
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
            text = "Captions",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        when {
            isLoading && tracks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }
            tracks.isEmpty() -> {
                Text(
                    text = "No captions available for this video",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                )
            }
            else -> {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    CaptionRow(
                        label = "Off",
                        checked = selected == null,
                        onClick = { onSelect(null) }
                    )
                    tracks.forEach { track ->
                        val label = if (track.isAutoGenerated) "${track.name} (auto)" else track.name
                        CaptionRow(
                            label = label,
                            checked = selected == track,
                            onClick = { onSelect(track) }
                        )
                    }
                }
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
                contentDescription = "Selected",
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
