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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage

/**
 * Player content designed to be shown inside a ModalBottomSheet.
 * Uses Material 3 Expressive components with bouncy animations.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSheetContent(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    
    var isFavorite by remember { mutableStateOf(false) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var isRepeatOn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar with collapse button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = Color.White
                )
            }
            IconButton(onClick = { /* More options */ }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Album Art with rounded corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (currentSong?.albumArtUri != null) {
                AsyncImage(
                    model = currentSong?.albumArtUri,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = Color(0xFF666666)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Song Info with Favorite
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentSong?.title ?: "No Song Playing",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFB3B3B3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            IconToggleButton(
                checked = isFavorite,
                onCheckedChange = { isFavorite = it }
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFF6B6B) else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Wavy Progress Bar
        Column(modifier = Modifier.fillMaxWidth()) {
            val progressFraction = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "Progress"
            )
            
            val thickStrokeWidth = with(LocalDensity.current) { 4.dp.toPx() }
            val thickStroke = Stroke(width = thickStrokeWidth, cap = StrokeCap.Round)

            Box(contentAlignment = Alignment.Center) {
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = Color(0xFFB8D4FF),
                    trackColor = Color(0xFF333333),
                    stroke = thickStroke,
                    trackStroke = thickStroke,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB3B3B3)
                )
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB3B3B3)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Main Playback Controls with bouncy shape-morphing buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous button with shape morphing
            FilledIconButton(
                onClick = { viewModel.skipToPrevious() },
                modifier = Modifier.size(56.dp),
                shapes = IconButtonDefaults.shapes(), // Enables bouncy shape morphing
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF2A2A2A),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
            }

            // Play/Pause button - larger with shape morphing
            FilledIconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(80.dp),
                shapes = IconButtonDefaults.shapes(), // Bouncy animation on press
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFB8D4FF),
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp)
                )
            }

            // Next button with shape morphing
            FilledIconButton(
                onClick = { viewModel.skipToNext() },
                modifier = Modifier.size(56.dp),
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF2A2A2A),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Secondary Controls (Shuffle & Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconToggleButton(
                checked = isShuffleOn,
                onCheckedChange = { isShuffleOn = it }
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffleOn) Color(0xFFB8D4FF) else Color(0xFFB3B3B3)
                )
            }
            
            IconToggleButton(
                checked = isRepeatOn,
                onCheckedChange = { isRepeatOn = it }
            ) {
                Icon(
                    Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (isRepeatOn) Color(0xFFB8D4FF) else Color(0xFFB3B3B3)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
