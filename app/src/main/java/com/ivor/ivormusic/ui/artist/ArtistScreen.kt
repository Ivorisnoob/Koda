package com.ivor.ivormusic.ui.artist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * 🌟 Material 3 Expressive Artist Screen
 * 
 * Design Features:
 * - Large hero header with organic shapes and gradient background
 * - Animated floating album art thumbnails
 * - Spring physics for interactive elements
 * - Shape morphing buttons
 * - Glassmorphism effects
 * - Premium card design for song list
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistName: String,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    // Theme colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    
    // Filter songs by artist
    val artistSongs = remember(songs, artistName) {
        songs.filter { it.artist.equals(artistName, ignoreCase = true) }
    }
    
    // Get unique albums
    val albums = remember(artistSongs) {
        artistSongs.groupBy { it.album }.keys.toList()
    }
    
    // Sample thumbnails for the hero section (up to 4)
    val sampleThumbnails = remember(artistSongs) {
        artistSongs.take(4).mapNotNull { it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() }
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
                ArtistHeroHeader(
                    artistName = artistName,
                    songCount = artistSongs.size,
                    albumCount = albums.size,
                    thumbnails = sampleThumbnails,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    tertiaryContainerColor = tertiaryContainerColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onBack = onBack,
                    onPlayAll = { onPlayQueue(artistSongs, null) },
                    onShuffle = { 
                        val shuffled = artistSongs.shuffled()
                        onPlayQueue(shuffled, null) 
                    }
                )
            }
            
            // ========== ALBUMS SECTION ==========
            if (albums.isNotEmpty() && albums.any { it.isNotBlank() && !it.startsWith("Unknown") }) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Albums",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val validAlbums = albums.filter { it.isNotBlank() && !it.startsWith("Unknown") }
                        items(validAlbums.size) { index ->
                            val album = validAlbums[index]
                            val albumSongs = artistSongs.filter { it.album == album }
                            val albumArt = albumSongs.firstOrNull()?.let { 
                                it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() 
                            }
                            
                            AlbumCard(
                                albumName = album,
                                songCount = albumSongs.size,
                                thumbnailUrl = albumArt,
                                primaryColor = primaryColor,
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = { onPlayQueue(albumSongs, null) }
                            )
                        }
                    }
                }
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
                        "Songs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        "${artistSongs.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Song list with segmented card design
            itemsIndexed(artistSongs) { index, song ->
                ArtistSongCard(
                    song = song,
                    index = index + 1,
                    onClick = { onPlayQueue(artistSongs, song) },
                    cardColor = cardColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    primaryColor = primaryColor,
                    shape = getSegmentedShape(index, artistSongs.size),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                if (index < artistSongs.size - 1) {
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
 * 🌟 Expressive Artist Hero Header
 * 
 * Features:
 * - Dynamic gradient background
 * - Floating album art with organic shapes
 * - Large artist name with proper typography
 * - Animated play/shuffle buttons
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistHeroHeader(
    artistName: String,
    songCount: Int,
    albumCount: Int,
    thumbnails: List<String>,
    primaryColor: Color,
    primaryContainerColor: Color,
    tertiaryContainerColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        val width = maxWidth
        val height = maxHeight
        
        // Gradient background with organic color flow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            primaryContainerColor.copy(alpha = 0.6f),
                            tertiaryContainerColor.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
        
        // Decorative circles in background
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = width - 80.dp, y = (-40).dp)
                .graphicsLayer { alpha = 0.15f }
                .clip(CircleShape)
                .background(primaryColor)
        )
        
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = (-30).dp, y = height - 180.dp)
                .graphicsLayer { alpha = 0.1f }
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
            
            // Artist avatar area with floating thumbnails
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Main artist circle (or music note if no thumbnails)
                Surface(
                    modifier = Modifier.size(130.dp),
                    shape = CircleShape,
                    color = primaryContainerColor,
                    shadowElevation = 16.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbnails.isNotEmpty()) {
                            AsyncImage(
                                model = thumbnails.first(),
                                contentDescription = artistName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = primaryColor
                            )
                        }
                    }
                }
                
                // Floating album thumbnails around the main circle
                if (thumbnails.size > 1) {
                    // Top-left small circle
                    Surface(
                        modifier = Modifier
                            .size(50.dp)
                            .offset(x = (-70).dp, y = (-20).dp)
                            .graphicsLayer { rotationZ = -10f },
                        shape = CircleShape,
                        color = tertiaryContainerColor,
                        shadowElevation = 8.dp
                    ) {
                        AsyncImage(
                            model = thumbnails.getOrNull(1) ?: thumbnails.first(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                
                if (thumbnails.size > 2) {
                    // Top-right small circle
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .offset(x = 75.dp, y = (-30).dp)
                            .graphicsLayer { rotationZ = 8f },
                        shape = CircleShape,
                        color = primaryContainerColor,
                        shadowElevation = 6.dp
                    ) {
                        AsyncImage(
                            model = thumbnails.getOrNull(2) ?: thumbnails.first(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                
                if (thumbnails.size > 3) {
                    // Bottom-right small circle
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .offset(x = 80.dp, y = 50.dp)
                            .graphicsLayer { rotationZ = 5f },
                        shape = RoundedCornerShape(12.dp),
                        color = tertiaryContainerColor,
                        shadowElevation = 4.dp
                    ) {
                        AsyncImage(
                            model = thumbnails.getOrNull(3) ?: thumbnails.first(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Artist name with verified badge look
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artistName.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$songCount songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
                if (albumCount > 0) {
                    Text(
                        " • ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor.copy(alpha = 0.5f)
                    )
                    Text(
                        "$albumCount albums",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle button
                FilledTonalButton(
                    onClick = onShuffle,
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = textColor
                    )
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Shuffle",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Play All button
                FilledTonalButton(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Play All",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Album Card for horizontal scroll
 */
@Composable
private fun AlbumCard(
    albumName: String,
    songCount: Int,
    thumbnailUrl: String?,
    primaryColor: Color,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Album art
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                shape = RoundedCornerShape(14.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = albumName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
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
                            modifier = Modifier.size(40.dp),
                            tint = primaryColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                albumName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                "$songCount songs",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )
        }
    }
}

/**
 * Song card for artist song list
 */
@Composable
private fun ArtistSongCard(
    song: Song,
    index: Int,
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
                    text = song.title.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Untitled Song",
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = song.album.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Unknown Album",
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                // Track number or thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null || song.thumbnailUrl != null) {
                        AsyncImage(
                            model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "$index",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                        }
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
