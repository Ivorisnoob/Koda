package com.ivor.ivormusic.ui.artist

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.launch

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

// MaterialShapes.toShape() is used directly - no custom PolygonShape needed

/**
 * 🌟 Material 3 Expressive Artist Screen
 * 
 * Design Features:
 * - Large hero header with organic shapes and gradient background
 * - Animated floating album art thumbnails
 * - Big centered 8-sided play button
 * - Fetches data from internet if no local songs
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
    onAlbumClick: ((String, List<Song>) -> Unit)? = null,
    viewModel: HomeViewModel? = null,
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
    
    // State for fetched songs
    var artistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var visibleSongCount by remember { mutableIntStateOf(20) }
    var hasLocalSongs by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Fetch songs - first check local, then fetch from internet
    LaunchedEffect(artistName, songs) {
        isLoading = true
        visibleSongCount = 20
        
        // First, filter local songs by artist
        val localArtistSongs = songs.filter { 
            it.artist.equals(artistName, ignoreCase = true) 
        }
        
        if (localArtistSongs.isNotEmpty()) {
            // Use local songs if available
            artistSongs = localArtistSongs
            hasLocalSongs = true
            isLoading = false
        } else if (viewModel != null) {
            // Fetch from internet if no local songs
            val fetchedSongs = viewModel.searchArtistSongs(artistName)
            artistSongs = fetchedSongs
            hasLocalSongs = false
            isLoading = false
        } else {
            artistSongs = emptyList()
            hasLocalSongs = false
            isLoading = false
        }
    }
    
    // Get unique albums (only meaningful for local songs with album metadata)
    val albums = remember(artistSongs, hasLocalSongs) {
        if (hasLocalSongs) {
            artistSongs.groupBy { it.album }.keys.toList()
        } else {
            emptyList() // YouTube search results don't have proper album info
        }
    }
    
    // Sample thumbnails for the hero section (up to 4)
    val sampleThumbnails = remember(artistSongs) {
        artistSongs.take(4).mapNotNull { it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() }
    }
    
    // Songs currently visible (with pagination)
    val displayedSongs = remember(artistSongs, visibleSongCount) {
        artistSongs.take(visibleSongCount)
    }
    val hasMoreSongs = artistSongs.size > visibleSongCount || (!hasLocalSongs && viewModel != null)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = primaryColor
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
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
                        onPlayAll = { 
                            if (artistSongs.isNotEmpty()) {
                                onPlayQueue(artistSongs, null) 
                            }
                        }
                    )
                }
                
                // ========== ALBUMS SECTION (Local songs only) ==========
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
                                    onClick = { 
                                        onAlbumClick?.invoke(album, albumSongs) 
                                            ?: onPlayQueue(albumSongs, null) 
                                    }
                                )
                            }
                        }
                    }
                }
                
                // ========== SONGS SECTION ==========
                if (displayedSongs.isNotEmpty()) {
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
                    itemsIndexed(displayedSongs) { index, song ->
                        ArtistSongCard(
                            song = song,
                            index = index + 1,
                            onClick = { onPlayQueue(artistSongs, song) },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            primaryColor = primaryColor,
                            shape = if (index == displayedSongs.size - 1 && !hasMoreSongs) {
                                getSegmentedShape(index, displayedSongs.size)
                            } else if (index == 0) {
                                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            } else {
                                RectangleShape
                            },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        if (index < displayedSongs.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                        }
                    }
                    
                    // Show More button
                    if (hasMoreSongs) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                                    .clickable(enabled = !isLoadingMore) {
                                        if (artistSongs.size > visibleSongCount) {
                                            // Show more from existing list
                                            visibleSongCount += 20
                                        } else if (viewModel != null && !hasLocalSongs) {
                                            // Load more from YouTube
                                            scope.launch {
                                                isLoadingMore = true
                                                val moreSongs = viewModel.loadMoreResults(artistName)
                                                if (moreSongs.isNotEmpty()) {
                                                    artistSongs = artistSongs + moreSongs
                                                    visibleSongCount += 20
                                                }
                                                isLoadingMore = false
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                                color = cardColor,
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isLoadingMore) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = primaryColor
                                        )
                                    } else {
                                        Text(
                                            "Show More",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (!isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No songs found for this artist",
                                color = secondaryTextColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
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
 * - Big centered 8-sided play button
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
    onPlayAll: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
    ) {
        // Guard against invalid dimensions during transitions
        if (maxWidth <= 0.dp || maxHeight <= 0.dp) {
            return@BoxWithConstraints
        }
        
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
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                // Main artist circle (or music note if no thumbnails)
                Surface(
                    modifier = Modifier.size(120.dp),
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
                                modifier = Modifier.size(56.dp),
                                tint = primaryColor
                            )
                        }
                    }
                }
                
                // Floating album thumbnails around the main circle
                if (thumbnails.size > 1) {
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .offset(x = (-65).dp, y = (-15).dp)
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
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .offset(x = 70.dp, y = (-25).dp)
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
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(x = 75.dp, y = 45.dp)
                            .graphicsLayer { rotationZ = 5f },
                        shape = RoundedCornerShape(10.dp),
                        color = tertiaryContainerColor,
                        shadowElevation = 4.dp
                    ) {
                        AsyncImage(
                            model = thumbnails.getOrNull(3) ?: thumbnails.first(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Artist name
            Text(
                text = artistName.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
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
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // Seated Floating Play Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 40.dp) // Seat it on the edge of the header (half overlap)
        ) {
            val playButtonShape = MaterialShapes.Cookie9Sided.toShape()
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.92f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "playAllButtonScale"
            )
            
            Surface(
                onClick = onPlayAll,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer { 
                        scaleX = scale
                        scaleY = scale
                    },
                shape = playButtonShape,
                color = primaryColor,
                shadowElevation = 8.dp,
                interactionSource = interactionSource
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play All",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
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
