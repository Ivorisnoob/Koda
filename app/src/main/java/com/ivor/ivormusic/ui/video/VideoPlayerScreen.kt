package com.ivor.ivormusic.ui.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.YouTubeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen video player for Video Mode.
 * Redesigned with Material 3 Expressive guidelines.
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerScreen(
    video: VideoItem,
    onBackClick: () -> Unit,
    onVideoSelect: (VideoItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val youtubeRepository = remember { YouTubeRepository(context) }
    
    // ExoPlayer Setup (Initialized early for use in PiP receiver)
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    
    // Player state
    var isLoading by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) } // Default hidden per user request
    var isFullscreen by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }
    
    // Quality state
    var availableQualities by remember { mutableStateOf<List<VideoQuality>>(emptyList()) }
    var currentQuality by remember { mutableStateOf<VideoQuality?>(null) }
    var relatedVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var showQualitySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    // PiP configuration
    // PiP Broadcast Receiver
    val pipReceiver = remember {
        object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    "PIP_PAUSE" -> exoPlayer.pause()
                    "PIP_PLAY" -> exoPlayer.play()
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        val filter = android.content.IntentFilter().apply {
            addAction("PIP_PAUSE")
            addAction("PIP_PLAY")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(pipReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(pipReceiver, filter)
        }
        onDispose { 
            try { context.unregisterReceiver(pipReceiver) } catch(e: Exception) {} 
        }
    }
    
    // Update PiP Params(Actions)
    LaunchedEffect(isPlaying) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             val playIntent = android.app.PendingIntent.getBroadcast(context, 0, android.content.Intent("PIP_PLAY"), android.app.PendingIntent.FLAG_IMMUTABLE)
             val pauseIntent = android.app.PendingIntent.getBroadcast(context, 1, android.content.Intent("PIP_PAUSE"), android.app.PendingIntent.FLAG_IMMUTABLE)
             val playAction = android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_play), "Play", "Play", playIntent)
             val pauseAction = android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_pause), "Pause", "Pause", pauseIntent)
             
             val actions = if (isPlaying) listOf(pauseAction) else listOf(playAction)
             
             val paramsBuilder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(actions)
                
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                 paramsBuilder.setAutoEnterEnabled(true)
             }
             activity?.setPictureInPictureParams(paramsBuilder.build())
        }
    }
    
    // Handle Buffering State
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Handle Loop Mode
    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    
    // Load video
    LaunchedEffect(video.videoId) {
        isLoading = true
        hasError = false
        try {
            // Fetch details (qualities + related)
            val details = youtubeRepository.getVideoDetails(video.videoId)
            val qualities = details.qualities
            availableQualities = qualities
            relatedVideos = details.relatedVideos
            
            if (qualities.isNotEmpty()) {
                // Default to 1080p60 or 1080p, else fallback to Auto (DASH), else first
                val bestQuality = qualities.find { it.resolution.contains("1080p60") }
                    ?: qualities.find { it.resolution.contains("1080p") }
                    ?: qualities.find { it.isDASH }
                    ?: qualities.first()
                    
                currentQuality = bestQuality
                
                val mediaItemBuilder = MediaItem.Builder().setUri(bestQuality.url)
                if (bestQuality.isDASH) {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                
                if (bestQuality.audioUrl != null) {
                    // Create MergingMediaSource for separate video and audio
                    val dataSourceFactory = DefaultDataSource.Factory(context)
                    val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(bestQuality.url))
                    val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(bestQuality.audioUrl!!))
                    
                    val mergingSource = MergingMediaSource(videoSource, audioSource)
                    exoPlayer.setMediaSource(mergingSource)
                } else {
                    exoPlayer.setMediaItem(mediaItemBuilder.build())
                }
                exoPlayer.prepare()
            } else {
                // Fallback
                val streamUrl = youtubeRepository.getVideoStreamUrl(video.videoId)
                if (streamUrl != null) {
                    val mediaItem = MediaItem.fromUri(streamUrl)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                } else {
                    hasError = true
                    errorMessage = "Stream unavailble"
                }
            }
        } catch (e: Exception) {
            hasError = true
            errorMessage = e.message ?: "Failed to load"
        }
        isLoading = false
    }
    
    // Report Playback to History
    LaunchedEffect(video.videoId, currentPosition) {
        // Report playback once distinct start happens (e.g. > 10s)
        if (currentPosition > 10000 && duration > 30000) {
           youtubeRepository.reportPlayback(video.videoId)
        }
    }

    // Progress Loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.duration > 0) {
                duration = exoPlayer.duration
                currentPosition = exoPlayer.currentPosition
                progress = currentPosition.toFloat() / duration.toFloat()
            }
            isPlaying = exoPlayer.isPlaying
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
    
    // Fullscreen / Immersive
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        
        if (isFullscreen) {
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
        }
    }
    
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    
    // ---------------- UI ----------------
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isFullscreen) {
            // Fullscreen Layout
            FullscreenPlayerContent(
                exoPlayer = exoPlayer,
                showControls = showControls,
                onToggleControls = { showControls = !showControls },
                hasError = hasError,
                errorMessage = errorMessage,
                isLoading = isLoading,
                isBuffering = isBuffering,
                isPlaying = isPlaying,
                isLooping = isLooping,
                currentPosition = currentPosition,
                duration = duration,
                progress = progress,
                videoTitle = video.title,
                onPlayPause = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onSeek = { newProgress -> exoPlayer.seekTo((newProgress * duration).toLong()) },
                onBack = { isFullscreen = false },
                onFullscreenToggle = { isFullscreen = false },
                onSettings = { showQualitySheet = true },
                onLoopToggle = { isLooping = !isLooping }
            )
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
                        hasError = hasError,
                        errorMessage = errorMessage,
                        isLoading = isLoading,
                        isBuffering = isBuffering,
                        isPlaying = isPlaying,
                        isLooping = isLooping,
                        currentPosition = currentPosition,
                        duration = duration,
                        progress = progress,
                        videoTitle = video.title,
                        onPlayPause = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        onSeek = { newProgress -> exoPlayer.seekTo((newProgress * duration).toLong()) },
                        onBack = onBackClick,
                        onFullscreenToggle = { isFullscreen = true },
                        onSettings = { showQualitySheet = true },
                        onLoopToggle = { isLooping = !isLooping }
                    )
                }
                
                // Info Area
                VideoInfoSection(
                    video = video,
                    relatedVideos = relatedVideos,
                    onVideoSelect = onVideoSelect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }
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
                
                if (availableQualities.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
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
                                    Spacer(Modifier.size(24.dp))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                .clickable {
                                    currentQuality = quality
                                    val pos = exoPlayer.currentPosition
                                    
                                    val mediaItemBuilder = MediaItem.Builder().setUri(quality.url)
                                    if (quality.isDASH) {
                                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                                    }
                                    
                                    if (quality.audioUrl != null) {
                                        val dataSourceFactory = DefaultDataSource.Factory(context)
                                        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                            .createMediaSource(MediaItem.fromUri(quality.url))
                                        val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                            .createMediaSource(MediaItem.fromUri(quality.audioUrl!!))
                                        
                                        val mergingSource = MergingMediaSource(videoSource, audioSource)
                                        exoPlayer.setMediaSource(mergingSource)
                                    } else {
                                        exoPlayer.setMediaItem(mediaItemBuilder.build())
                                    }
                                    
                                    exoPlayer.seekTo(pos)
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                    scope.launch { showQualitySheet = false }
                                }
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Sub-Composables ----------------

@kotlin.OptIn(UnstableApi::class)
@Composable
private fun FullscreenPlayerContent(
    exoPlayer: ExoPlayer,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    hasError: Boolean,
    errorMessage: String,
    isLoading: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    isLooping: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    onLoopToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggleControls() }
    ) {
        // Video View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Overlays
        if (hasError) {
            ErrorOverlay(errorMessage)
        } else if (isLoading || (isBuffering && !showControls)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
        }
        
        // Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(0.7f), Color.Transparent)))
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Text(
                        text = videoTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(Modifier.width(16.dp))
                    
                    PlayerIconButton(
                        icon = if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Loop",
                        onClick = onLoopToggle,
                        tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White
                    )
                    Spacer(Modifier.width(16.dp))
                    PlayerIconButton(Icons.Rounded.Settings, "Quality", onSettings)
                    Spacer(Modifier.width(16.dp))
                    PlayerIconButton(Icons.Rounded.FullscreenExit, "Exit Fullscreen", onFullscreenToggle)
                }
                
                // Center Play/Pause
                Box(modifier = Modifier.align(Alignment.Center)) {
                    ExpressivePlayPauseButton(isPlaying = isPlaying, isBuffering = isBuffering, onClick = onPlayPause)
                }
                
                // Bottom Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(0.8f))))
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(formatDuration(currentPosition), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        
                        Slider(
                            value = progress,
                            onValueChange = onSeek,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(UnstableApi::class)
@Composable
private fun PortraitPlayerContent(
    exoPlayer: ExoPlayer,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    hasError: Boolean,
    errorMessage: String,
    isLoading: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    isLooping: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    onLoopToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggleControls() }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        if (hasError) ErrorOverlay(errorMessage)
        if (isLoading || (isBuffering && !showControls)) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ContainedLoadingIndicator()
        }
        
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PlayerIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerIconButton(
                            icon = if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Loop",
                            onClick = onLoopToggle,
                            tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White
                        )
                        PlayerIconButton(Icons.Rounded.Settings, "Quality", onSettings)
                        PlayerIconButton(Icons.Rounded.Fullscreen, "Fullscreen", onFullscreenToggle)
                    }
                }
                
                // Center
                Box(modifier = Modifier.align(Alignment.Center)) {
                    ExpressivePlayPauseButton(isPlaying = isPlaying, isBuffering = isBuffering, onClick = onPlayPause)
                }
                
                // Bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(0.8f))))
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(formatDuration(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        
                        Slider(
                            value = progress,
                            onValueChange = onSeek,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoInfoSection(
    video: VideoItem,
    relatedVideos: List<VideoItem>,
    onVideoSelect: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp) // Bottom padding for navigation bar if needed
    ) {
        // Title
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        
        // Stats
        Text(
            text = buildString {
                if (video.viewCount.isNotEmpty()) append("${video.viewCount} views")
                if (!video.uploadedDate.isNullOrEmpty()) append(" • ${video.uploadedDate}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Actions

        
        Spacer(Modifier.height(24.dp))
        
        // Channel
        ListItem(
            headlineContent = { 
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                ) 
            },
            supportingContent = {
                Text(
                     text = "${video.subscriberCount ?: "Unknown"} subscribers",
                     style = MaterialTheme.typography.bodySmall
                )
            },
            leadingContent = {
                if (!video.channelIconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.channelIconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = video.channelName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            trailingContent = {
                FilledTonalButton(onClick = { /* TODO */ }) {
                    Text("Subscribe")
                }
            },
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        )
        
        if (!video.description.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = video.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Related Videos
        if (relatedVideos.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            relatedVideos.forEach { relatedVideo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onVideoSelect(relatedVideo) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Thumbnail
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .aspectRatio(16f/9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        if (relatedVideo.thumbnailUrl != null) {
                            AsyncImage(
                                model = relatedVideo.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        // Duration
                        if (relatedVideo.duration > 0) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = relatedVideo.formattedDuration,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    // Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = relatedVideo.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = relatedVideo.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = relatedVideo.viewCount + " • " + (relatedVideo.uploadedDate ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

// ---------------- Helpers ----------------

@Composable
fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = tint
        )
    ) {
        Icon(icon, contentDescription, tint = tint)
    }
}

@Composable
fun ExpressivePlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Scale animation on press
    val scale by animateDpAsState(
        targetValue = if (isPressed) 72.dp else 80.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
    )
    
    // Shape Morphing
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 24.dp else 40.dp, 
        animationSpec = spring()
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.size(scale),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        interactionSource = interactionSource,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isBuffering && !isPlaying) {
                 LoadingIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    polygons = listOf(
                        MaterialShapes.SoftBurst,
                        MaterialShapes.Cookie9Sided,
                        MaterialShapes.Pill,
                        MaterialShapes.Sunny
                    )
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ErrorOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.White)
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val seconds = millis / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
