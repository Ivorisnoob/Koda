package com.ivor.ivormusic.ui.artist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song

/**
 * Segmented list shape helper for Expressive design
 */
@Composable
private fun getSegmentedShape(index: Int, count: Int, cornerSize: androidx.compose.ui.unit.Dp = 28.dp): Shape {
    return when {
        count == 1 -> RoundedCornerShape(cornerSize)
        index == 0 -> RoundedCornerShape(topStart = cornerSize, topEnd = cornerSize)
        index == count - 1 -> RoundedCornerShape(bottomStart = cornerSize, bottomEnd = cornerSize)
        else -> RectangleShape
    }
}

// Note: PolygonShape is shared from ArtistScreen.kt (internal visibility)

/**
 * 🌟 Material 3 Expressive Album Screen
 * 
 * Design Features:
 * - Large album artwork with organic decorative shapes
 * - Big centered 8-sided play button
 * - Premium segmented song list
 * - Gradient background
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumName: String,
    artistName: String,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Theme colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    
    // Get album art from first song
    val albumArt = remember(songs) {
        songs.firstOrNull()?.let { 
            it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() 
        }
    }
    
    // Create 8-sided polygon shape for Play button
    val octagonShape = remember {
        PolygonShape(
            RoundedPolygon(
                numVertices = 8,
                rounding = CornerRounding(radius = 0.2f)
            )
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ========== HERO HEADER ==========
            item {
                AlbumHeroHeader(
                    albumName = albumName,
                    artistName = artistName,
                    songCount = songs.size,
                    albumArt = albumArt,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    tertiaryContainerColor = tertiaryContainerColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    octagonShape = octagonShape,
                    onBack = onBack,
                    onPlayAll = { onPlayQueue(songs, null) }
                )
            }
            
            // ========== SONGS SECTION ==========
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tracks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        "${songs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Song list with segmented card design
            itemsIndexed(songs) { index, song ->
                AlbumSongCard(
                    song = song,
                    trackNumber = index + 1,
                    onClick = { onPlayQueue(songs, song) },
                    cardColor = cardColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    primaryColor = primaryColor,
                    shape = getSegmentedShape(index, songs.size),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                if (index < songs.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 44.dp),
                        color = textColor.copy(alpha = 0.06f)
                    )
                }
            }
        }
    }
}

/**
 * Album Hero Header with large artwork and play button
 */
@Composable
private fun AlbumHeroHeader(
    albumName: String,
    artistName: String,
    songCount: Int,
    albumArt: String?,
    primaryColor: Color,
    primaryContainerColor: Color,
    tertiaryContainerColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    octagonShape: Shape,
    onBack: () -> Unit,
    onPlayAll: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        val width = maxWidth
        val height = maxHeight
        
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            primaryContainerColor.copy(alpha = 0.5f),
                            tertiaryContainerColor.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
        
        // Decorative shapes in background
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = width - 60.dp, y = (-30).dp)
                .graphicsLayer { alpha = 0.12f }
                .clip(RoundedCornerShape(40.dp))
                .background(primaryColor)
        )
        
        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(x = (-20).dp, y = height - 160.dp)
                .graphicsLayer { alpha = 0.08f }
                .clip(CircleShape)
                .background(tertiaryContainerColor)
        )
        
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp)
        ) {
            // Back button
            FilledIconButton(
                onClick = onBack,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Album artwork
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(180.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = primaryContainerColor,
                    shadowElevation = 20.dp
                ) {
                    if (albumArt != null) {
                        AsyncImage(
                            model = albumArt,
                            contentDescription = albumName,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Album,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = primaryColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Album name
            Text(
                text = albumName.takeIf { it.isNotBlank() && !it.startsWith("Unknown") } ?: "Unknown Album",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Artist name
            Text(
                text = artistName.takeIf { it.isNotBlank() && !it.startsWith("Unknown") } ?: "Unknown Artist",
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Song count
            Text(
                text = "$songCount tracks",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Big centered 8-sided Play button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.92f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "playButtonScale"
                )
                
                Surface(
                    onClick = onPlayAll,
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer { 
                            scaleX = scale
                            scaleY = scale
                        },
                    shape = octagonShape,
                    color = primaryColor,
                    shadowElevation = 12.dp,
                    interactionSource = interactionSource
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play All",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Song card for album song list
 */
@Composable
private fun AlbumSongCard(
    song: Song,
    trackNumber: Int,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    primaryColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = cardColor,
        tonalElevation = 1.dp
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Track $trackNumber",
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                // Format duration if available
                val durationText = if (song.duration > 0) {
                    val minutes = song.duration / 60000
                    val seconds = (song.duration % 60000) / 1000
                    "$minutes:${seconds.toString().padStart(2, '0')}"
                } else null
                
                durationText?.let {
                    Text(
                        text = it,
                        color = secondaryTextColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            leadingContent = {
                // Track number in a nice circle
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.1f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$trackNumber",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                }
            },
            trailingContent = {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}
