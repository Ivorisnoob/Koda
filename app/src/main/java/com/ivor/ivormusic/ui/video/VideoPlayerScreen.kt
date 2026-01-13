package com.ivor.ivormusic.ui.video

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.VideoItem
import java.util.Locale

// VideoPlayerScreen function removed.
// Logic moved to VideoPlayerViewModel and VideoPlayerContent.
// Keeping helper composables for reuse.

// ---------------- Sub-Composables ----------------

@kotlin.OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullscreenPlayerContent(
    exoPlayer: ExoPlayer,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    hasError: Boolean,
    errorMessage: String,
    isLoading: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    isLooping: Boolean,
    isAutoPlayEnabled: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    onLoopToggle: () -> Unit,
    onAutoPlayToggle: () -> Unit
) {
    // Stable shapes to prevent "square flash"
    val stableShapes = IconButtonDefaults.shapes()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FilledIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(0.5f),
                            contentColor = Color.White
                        ),
                        shapes = stableShapes
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    
                    Text(
                        text = videoTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Auto Play Toggle
                    FilledTonalIconButton(
                        onClick = onAutoPlayToggle,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isAutoPlayEnabled) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                            contentColor = if (isAutoPlayEnabled) MaterialTheme.colorScheme.onPrimary else Color.White
                        ),
                        shapes = stableShapes
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.Autorenew,
                            contentDescription = "Auto Play"
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onLoopToggle,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isLooping) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                            contentColor = if (isLooping) MaterialTheme.colorScheme.onPrimary else Color.White
                        ),
                        shapes = stableShapes
                    ) {
                         Icon(
                            if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Loop"
                        )
                    }

                    FilledIconButton(
                        onClick = onSettings,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(0.5f),
                            contentColor = Color.White
                        ),
                        shapes = stableShapes
                    ) {
                        Icon(Icons.Rounded.Settings, "Quality")
                    }

                    FilledIconButton(
                        onClick = onFullscreenToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(0.5f),
                            contentColor = Color.White
                        ),
                        shapes = stableShapes
                    ) {
                        Icon(Icons.Rounded.FullscreenExit, "Exit Fullscreen")
                    }
                }
                
                // Center Play/Pause
                Box(modifier = Modifier.align(Alignment.Center)) {
                    ExpressivePlayPauseButton(
                        isPlaying = isPlaying, 
                        isBuffering = isBuffering, 
                        onClick = onPlayPause,
                        size = 80.dp
                    )
                }
                
                // Bottom Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(0.8f))))
                        .padding(horizontal = 32.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

@kotlin.OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PortraitPlayerContent(
    exoPlayer: ExoPlayer,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    hasError: Boolean,
    errorMessage: String,
    isLoading: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    isLooping: Boolean,
    isAutoPlayEnabled: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    videoTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettings: () -> Unit,
    onLoopToggle: () -> Unit,
    onAutoPlayToggle: () -> Unit
) {
    // Stable shapes
    val stableShapes = IconButtonDefaults.shapes()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledIconButton(
                        onClick = onBack,
                         colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(0.5f),
                            contentColor = Color.White
                        ),
                        shapes = stableShapes
                    ) {
                         Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Auto Play Toggle
                         FilledTonalIconButton(
                            onClick = onAutoPlayToggle,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isAutoPlayEnabled) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                                contentColor = if (isAutoPlayEnabled) MaterialTheme.colorScheme.onPrimary else Color.White
                            ),
                            shapes = stableShapes
                        ) {
                            Icon(
                                Icons.Rounded.Autorenew,
                                contentDescription = "Auto Play"
                            )
                        }
                        
                         FilledTonalIconButton(
                            onClick = onLoopToggle,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isLooping) MaterialTheme.colorScheme.primary else Color.Black.copy(0.5f),
                                contentColor = if (isLooping) MaterialTheme.colorScheme.onPrimary else Color.White
                            ),
                            shapes = stableShapes
                        ) {
                             Icon(
                                if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = "Loop"
                            )
                        }
                        FilledIconButton(
                            onClick = onSettings,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(0.5f),
                                contentColor = Color.White
                            ),
                            shapes = stableShapes
                        ) {
                            Icon(Icons.Rounded.Settings, "Quality")
                        }
                        FilledIconButton(
                            onClick = onFullscreenToggle,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(0.5f),
                                contentColor = Color.White
                            ),
                            shapes = stableShapes
                        ) {
                            Icon(Icons.Rounded.Fullscreen, "Fullscreen")
                        }
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
            .padding(24.dp)
            .padding(bottom = 80.dp), // Bottom padding for navigation bar
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Title & Stats Group
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = buildString {
                    if (video.viewCount.isNotEmpty()) append("${video.viewCount} views")
                    if (!video.uploadedDate.isNullOrEmpty()) append(" • ${video.uploadedDate}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Channel Info Surface
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                         text = "${video.subscriberCount ?: "Unknown"}",
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
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )
        }
        
        // Description Surface
        if (!video.description.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    var isDescriptionExpanded by remember { mutableStateOf(false) }
                    val cleanedDescription = remember(video.description) {
                        if (video.description != null) {
                            androidx.core.text.HtmlCompat.fromHtml(
                                video.description,
                                androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
                            ).toString().trim()
                        } else ""
                    }
                    
                    if (cleanedDescription.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                        ) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = cleanedDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            if (cleanedDescription.length > 100 || cleanedDescription.count { it == '\n' } > 2) {
                                Text(
                                    text = if (isDescriptionExpanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Related Videos Section
        if (relatedVideos.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
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
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = relatedVideo.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
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
        }
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
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = tint
        )
    ) {
        Icon(icon, contentDescription)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 72.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Expressive Spring Animation for Scale
    val scale by animateDpAsState(
        targetValue = if (isPressed) size * 0.9f else size,
        animationSpec = spring(
            dampingRatio = 0.4f, // Bouncy!
            stiffness = 600f
        ),
        label = "ButtonScale"
    )
    
    // Shape Morphing
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) size / 3 else size / 2, // Morph from circle to squircle
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ButtonShape"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.size(scale),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        interactionSource = interactionSource,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isBuffering && !isPlaying) {
                // Expressive Loading Indicator
                 LoadingIndicator(
                    modifier = Modifier.size(size * 0.5f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    polygons = listOf(
                        MaterialShapes.SoftBurst,
                        MaterialShapes.Cookie9Sided,
                        MaterialShapes.Pill,
                        MaterialShapes.Sunny
                    )
                )
            } else {
                // Animated Icon with Scale/Rotate transition potential (kept simple for now)
                val iconSize = size * 0.45f
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(iconSize)
                )
            }
        }
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
            Icon(Icons.Rounded.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
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
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
