package com.ivor.ivormusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.theme.IvorMusicTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    
    var isFavorite by remember { mutableStateOf(false) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var isRepeatOn by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
                    }
                },
                actions = {
                    IconButton(onClick = { /* More options */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    
    // Background gradient based on theme
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            surfaceColor,
            surfaceColor.copy(alpha = 0.8f),
            surfaceColor.copy(alpha = 0.95f),
            surfaceColor
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Album Art with Large Expressive Corner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(48.dp),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (currentSong?.albumArtUri != null || currentSong?.thumbnailUrl != null) {
                        AsyncImage(
                            model = currentSong?.highResThumbnailUrl ?: currentSong?.albumArtUri,
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(140.dp),
                            tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Song Info and Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = (currentSong?.title.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown") } ?: "Untitled Song"),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.1f),
                                offset = Offset(0f, 4f),
                                blurRadius = 8f
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = onSurfaceColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (currentSong?.artist.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown") } ?: "Unknown Artist"),
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurfaceVariantColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                IconToggleButton(
                    checked = isFavorite,
                    onCheckedChange = { isFavorite = it },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else onSurfaceColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Wavy Progress Bar with Enhanced Styling
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(contentAlignment = Alignment.Center) {
                    val progressFraction = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = progressFraction,
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                        label = "Progress"
                    )
                    
                    val thickStrokeWidth = with(LocalDensity.current) { 6.dp.toPx() }
                    val thickStroke = Stroke(width = thickStrokeWidth, cap = StrokeCap.Round)

                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        stroke = thickStroke,
                        trackStroke = thickStroke,
                        color = primaryColor,
                        trackColor = onSurfaceVariantColor.copy(alpha = 0.15f)
                    )

                    // Transparent Slider for interaction
                    Slider(
                        value = progress.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(progress),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurfaceVariantColor
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurfaceVariantColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main Playback Controls with Shape Morphing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { viewModel.skipToPrevious() },
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = secondaryContainerColor
                    )
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
                }

                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(96.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = RoundedCornerShape(32.dp)
                    ),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    // Logic fix: Show loader if we are explicitly buffering.
                    // MusicService ensures playWhenReady is true even during resolution errors, 
                    // causing PlayerViewModel to set isBuffering=true.
                    if (isBuffering) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 4.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                FilledIconButton(
                    onClick = { viewModel.skipToNext() },
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = secondaryContainerColor
                    )
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Secondary Controls (Shuffle & Repeat)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconToggleButton(
                    checked = isShuffleOn,
                    onCheckedChange = { isShuffleOn = it },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(28.dp),
                        tint = if (isShuffleOn) primaryColor else onSurfaceVariantColor
                    )
                }
                
                IconToggleButton(
                    checked = isRepeatOn,
                    onCheckedChange = { isRepeatOn = it },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        modifier = Modifier.size(28.dp),
                        tint = if (isRepeatOn) primaryColor else onSurfaceVariantColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

