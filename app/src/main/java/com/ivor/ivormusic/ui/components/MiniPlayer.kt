package com.ivor.ivormusic.ui.components
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.player.rememberPlayerHaptics

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayerContent(
    currentSong: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    isCasting: Boolean,
    castDeviceName: String?,
    onClick: () -> Unit
) {
    val playerHaptics = rememberPlayerHaptics()
    val playLabel = stringResource(R.string.cd_play)
    val pauseLabel = stringResource(R.string.cd_pause)

    // Toggling while a track is still resolving would call play() again rather
    // than cancelling the pending start (togglePlayPause keys off isPlaying),
    // so the artwork goes inert for exactly the window the play/pause button
    // is replaced by the loading indicator. A tap then falls through to the
    // pill's own onClick and expands, as it always did.
    val artworkTogglesPlayback = !(isBuffering && playWhenReady)

    // Transparent: the ExpandablePlayer container draws the pill background.
    // A second opaque surface here created a visible "pill behind a pill"
    // (its own shadow + tonal tint), and its drag handler swallowed the
    // swipe-up-to-expand gesture the container listens for.
    Surface(
        modifier = Modifier.fillMaxSize(),
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(50) // Full pill shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art with Circular Progress Ring — doubles as the
            // play/pause target, so the most-hit part of the pill toggles
            // playback instead of only expanding the player.
            val artworkInteraction = remember { MutableInteractionSource() }
            val artworkPressed by artworkInteraction.collectIsPressedAsState()
            val artworkScale by animateFloatAsState(
                targetValue = if (artworkPressed) 0.92f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "miniArtworkScale"
            )

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = artworkInteraction,
                        indication = null,
                        enabled = artworkTogglesPlayback,
                        onClickLabel = if (isPlaying) pauseLabel else playLabel,
                        role = Role.Button,
                        onClick = {
                            playerHaptics.playPause(!isPlaying)
                            onPlayPauseClick()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                val progressColor = MaterialTheme.colorScheme.primary

                // Progress ring behind the album art
                Canvas(modifier = Modifier.size(52.dp)) {
                    val strokeWidth = 3.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    
                    // Track (background ring)
                    drawCircle(
                        color = trackColor,
                        radius = radius,
                        style = Stroke(width = strokeWidth)
                    )
                    
                    // Progress arc
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    )
                }
                
                // Album art thumbnail (slightly smaller to fit inside ring).
                // Only the cover springs on press — the ring is a progress
                // readout and would read as a glitch if it scaled with it.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(artworkScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val imageUrl = currentSong.highResThumbnailUrl ?: currentSong.thumbnailUrl
                    val localUri = currentSong.albumArtUri
                    
                    if (imageUrl != null || localUri != null) {
                        coil.compose.SubcomposeAsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(localUri ?: imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.cd_album_art),
                            modifier = Modifier.size(44.dp),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            error = {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentSong.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isCasting && castDeviceName != null) {
                        "Casting to $castDeviceName"
                    } else {
                        currentSong.artist
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCasting) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play/Pause Button with shape morphing or Loading
            if (isBuffering && playWhenReady) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    //Organic morphing loading with MaterialShapes
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        polygons = listOf(
                            MaterialShapes.SoftBurst,
                            MaterialShapes.Cookie9Sided,
                            MaterialShapes.Pill,
                            MaterialShapes.Sunny
                        )
                    )
                }
            } else {
                FilledIconButton(
                    onClick = {
                        // Same action as the artwork tap, so same feedback.
                        playerHaptics.playPause(!isPlaying)
                        onPlayPauseClick()
                    },
                    modifier = Modifier.size(44.dp),
                    shapes = IconButtonDefaults.shapes(), // Bouncy shape morphing
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) pauseLabel else playLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Next Button with shape morphing
            FilledIconButton(
                onClick = onNextClick,
                modifier = Modifier.size(44.dp),
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.cd_next),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
