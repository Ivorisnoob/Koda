package com.ivor.ivormusic.ui.video

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
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.domain.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Content for the full Video Player Overlay.
 * Replaces old VideoPlayerScreen by using VideoPlayerViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerContent(
    viewModel: VideoPlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    // Local UI State
    var showControls by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Progress polling
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }

    val currentVideo = video
    val streamUrl = currentQuality?.url

    if (currentVideo == null) return

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Quality Sheet State
    var showQualitySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // ---------------- UI ----------------

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                // Consume clicks to prevent interaction with underlying app
            }
    ) {
        if (isFullscreen) {
            // Fullscreen Layout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                FullscreenPlayerContent(
                    streamUrl = streamUrl,
                    isPlaying = isPlaying,
                    showControls = showControls,
                    onToggleControls = { showControls = !showControls },
                    hasError = playbackError != null,
                    errorMessage = playbackError ?: "",
                    isLoading = isLoading,
                    isBuffering = isBuffering,
                    isLooping = isLooping,
                    currentPosition = currentPosition,
                    duration = duration,
                    progress = progress,
                    videoTitle = currentVideo.title,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSeek = { newProgress ->
                        val seekMs = (newProgress * duration).toLong()
                        currentPosition = seekMs
                        progress = newProgress
                        viewModel.seekTo(seekMs)
                    },
                    onBack = { isFullscreen = false },
                    onFullscreenToggle = { isFullscreen = false },
                    onSettings = { showQualitySheet = true },
                    onLoopToggle = { viewModel.toggleLooping() },
                    isAutoPlayEnabled = isAutoPlayEnabled,
                    onAutoPlayToggle = { viewModel.toggleAutoPlay() }
                )
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
                        streamUrl = streamUrl,
                        isPlaying = isPlaying,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls },
                        hasError = playbackError != null,
                        errorMessage = playbackError ?: "",
                        isLoading = isLoading,
                        isBuffering = isBuffering,
                        isLooping = isLooping,
                        currentPosition = currentPosition,
                        duration = duration,
                        progress = progress,
                        videoTitle = currentVideo.title,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { newProgress ->
                            val seekMs = (newProgress * duration).toLong()
                            currentPosition = seekMs
                            progress = newProgress
                            viewModel.seekTo(seekMs)
                        },
                        onBack = onBackClick,
                        onFullscreenToggle = { isFullscreen = true },
                        onSettings = { showQualitySheet = true },
                        onLoopToggle = { viewModel.toggleLooping() },
                        isAutoPlayEnabled = isAutoPlayEnabled,
                        onAutoPlayToggle = { viewModel.toggleAutoPlay() }
                    )
                }

                // Info Area
                VideoInfoSection(
                    video = currentVideo,
                    relatedVideos = relatedVideos,
                    onVideoSelect = { viewModel.playVideo(it) },
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
