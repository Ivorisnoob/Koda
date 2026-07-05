package com.ivor.ivormusic.ui.video

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import com.ivor.ivormusic.data.VideoItem
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
    timedCommentsFeatureEnabled: Boolean = false
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
    val isAutoPlayEnabled by viewModel.isAutoPlayEnabled.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val engagement by viewModel.engagement.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsState()
    val isLoadingMoreComments by viewModel.isLoadingMoreComments.collectAsState()
    val commentReplies by viewModel.replies.collectAsState()
    val loadingReplyIds by viewModel.loadingReplyIds.collectAsState()
    val timedComments by viewModel.timedComments.collectAsState()

    // Local UI State
    var showControls by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    // Timed comments overlay toggle; persists across videos while the player is open
    var timedCommentsActive by remember { mutableStateOf(false) }
    
    // Progress polling (ViewModel doesn't poll, so we do it here or update ViewModel to poll)
    // Ideally ViewModel should emit progress, but for smoother slider we often poll in UI or VM. 
    // Let's poll in UI for now as we have the ExoPlayer instance in VM
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }
    
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
    
    // Fullscreen / Immersive
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        
        if (isFullscreen) {
            // Allow content to draw behind system bars first
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            insetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
        
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            // Restore normal window behavior
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
        }
    }
    
    // Quality Sheet State
    var showQualitySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Comments Sheet + Sign-in Dialog State
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }

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
                videoTitle = currentVideo.title,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { newProgress -> exoPlayer.seekTo((newProgress * duration).toLong()) },
                onSeekBackward = { seekBy(-10_000L) },
                onSeekForward = { seekBy(10_000L) },
                onBack = { isFullscreen = false },
                onFullscreenToggle = { isFullscreen = false },
                onSettings = { showQualitySheet = true },
                onLoopToggle = { viewModel.toggleLooping() },
                isAutoPlayEnabled = isAutoPlayEnabled,
                onAutoPlayToggle = { viewModel.toggleAutoPlay() },
                showTimedCommentsButton = timedCommentsFeatureEnabled,
                timedCommentsActive = timedCommentsActive,
                onTimedCommentsToggle = { timedCommentsActive = !timedCommentsActive }
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
                        videoTitle = currentVideo.title,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { newProgress -> exoPlayer.seekTo((newProgress * duration).toLong()) },
                        onSeekBackward = { seekBy(-10_000L) },
                        onSeekForward = { seekBy(10_000L) },
                        onBack = onBackClick,
                        onFullscreenToggle = { isFullscreen = true },
                        onSettings = { showQualitySheet = true },
                        onLoopToggle = { viewModel.toggleLooping() },
                        isAutoPlayEnabled = isAutoPlayEnabled,
                        onAutoPlayToggle = { viewModel.toggleAutoPlay() },
                        showTimedCommentsButton = timedCommentsFeatureEnabled,
                        timedCommentsActive = timedCommentsActive,
                        onTimedCommentsToggle = { timedCommentsActive = !timedCommentsActive }
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
                
                // Info Area
                VideoInfoSection(
                    video = currentVideo,
                    relatedVideos = relatedVideos,
                    onVideoSelect = { viewModel.playVideo(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface),
                    engagement = engagement,
                    onLikeClick = { requireLogin { viewModel.toggleLike() } },
                    onDislikeClick = { requireLogin { viewModel.toggleDislike() } },
                    onSubscribeClick = { requireLogin { viewModel.toggleSubscribe() } },
                    onCommentsClick = {
                        viewModel.ensureCommentsLoaded()
                        showCommentsSheet = true
                    }
                )
            }
        }
    }
    
    // Comments Sheet
    if (showCommentsSheet) {
        CommentsSheet(
            comments = comments,
            replies = commentReplies,
            loadingReplyIds = loadingReplyIds,
            isLoading = isCommentsLoading,
            isLoadingMore = isLoadingMoreComments,
            commentsAvailable = engagement?.commentsToken != null,
            onLoadMore = { viewModel.loadMoreComments() },
            onLoadReplies = { viewModel.loadReplies(it) },
            onDismiss = { showCommentsSheet = false }
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

    // Quality Sheet
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Quality",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (availableQualities.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No qualities available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    availableQualities.forEach { quality ->
                        ListItem(
                            headlineContent = { Text(
                                text = quality.resolution,
                                style = MaterialTheme.typography.bodyLarge
                            ) },
                            leadingContent = {
                                if (quality.url == currentQuality?.url) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(24.dp))
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.setQuality(quality)
                                showQualitySheet = false
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}
