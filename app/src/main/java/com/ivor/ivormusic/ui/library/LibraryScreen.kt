package com.ivor.ivormusic.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.home.HomeViewModel

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
 * Library Screen with Material 3 Expressive design
 * - YouTube playlists and liked songs integration
 * - Premium card designs with beautiful rounded corners
 * - Category tabs
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onPlaylistClick: (com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String, List<Song>) -> Unit = { _, _ -> },
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    // Theme colors from MaterialTheme
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val chipBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    
    // Data from ViewModel
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Tab state
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Playlists", "Artists", "Albums")
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Header
        Text(
            "Your Library",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
        
        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tabs.size) { index ->
                val isSelected = selectedTab == index
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    label = { 
                        Text(
                            tabs[index],
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = chipBgColor,
                        labelColor = secondaryTextColor,
                        selectedContainerColor = accentColor,
                        selectedLabelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Color.Transparent,
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        // Main Content
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                when (tabs[selectedTab]) {
                    "All" -> {
                        // 1. Liked Songs Highlight
                        if (likedSongs.isNotEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .clickable { onPlayQueue(likedSongs, null) },
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFFE91E63), Color(0xFFFF5252))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.Favorite,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(16.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Liked Songs",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )
                                            Text(
                                                "${likedSongs.size} songs • Auto-playlist",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = secondaryTextColor
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = { onPlayQueue(likedSongs, null) },
                                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                                containerColor = accentColor
                                            )
                                        ) {
                                            Icon(
                                                Icons.Rounded.PlayArrow,
                                                contentDescription = "Play",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 2. Playlists Horizontal Scroller (if any)
                        if (userPlaylists.isNotEmpty()) {
                            item {
                                Text(
                                    "Your Playlists",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)
                                )
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(userPlaylists) { playlist ->
                                        Column(
                                            modifier = Modifier
                                                .width(140.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onPlaylistClick(playlist) }
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .size(140.dp)
                                                    .clip(RoundedCornerShape(16.dp)),
                                                color = cardColor,
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                if (playlist.thumbnailUrl != null) {
                                                    AsyncImage(
                                                        model = playlist.thumbnailUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Rounded.PlaylistPlay,
                                                            contentDescription = null,
                                                            tint = secondaryTextColor,
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                playlist.name ?: "Untitled",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = textColor
                                            )
                                            Text(
                                                "${playlist.itemCount} songs",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = secondaryTextColor
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                        
                        // 3. Local Songs (Recently Added / All)
                        if (songs.isNotEmpty()) {
                            item {
                                Text(
                                    "Your Tracks",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            
                            itemsIndexed(songs.take(20)) { index, song -> 
                                LibrarySongCard(
                                    song = song,
                                    onClick = { onPlayQueue(songs, song) },
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    accentColor = accentColor,
                                    shape = getSegmentedShape(index, songs.take(20).size),
                                    isLocalOriginal = true
                                )
                                if (index < songs.take(20).size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        color = textColor.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }
                    
                    "Playlists" -> {
                        // Liked Songs
                        if (likedSongs.isNotEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                        .clickable { onPlayQueue(likedSongs, null) },
                                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                    color = cardColor
                                ) {
                                    ListItem(
                                        headlineContent = { Text("Liked Songs", fontWeight = FontWeight.Bold, color = textColor) },
                                        supportingContent = { Text("${likedSongs.size} songs", color = secondaryTextColor) },
                                        leadingContent = {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFFFF5252)))),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Rounded.Favorite, null, tint = Color.White)
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                                if (userPlaylists.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = textColor.copy(alpha = 0.08f))
                                }
                            }
                        }
                        
                        // User Playlists
                        itemsIndexed(userPlaylists) { index, playlist ->
                            val shape = if (likedSongs.isEmpty()) {
                                getSegmentedShape(index, userPlaylists.size)
                            } else {
                                if (index == userPlaylists.lastIndex) RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp) else RectangleShape
                            }
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .clickable { onPlaylistClick(playlist) },
                                shape = shape,
                                color = cardColor
                            ) {
                                ListItem(
                                    headlineContent = { Text(playlist.name ?: "Unknown", fontWeight = FontWeight.Bold, color = textColor) },
                                    supportingContent = { Text("${playlist.itemCount} songs", color = secondaryTextColor) },
                                    leadingContent = {
                                        Surface(
                                            modifier = Modifier.size(56.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            if (playlist.thumbnailUrl != null) {
                                                AsyncImage(
                                                    model = playlist.thumbnailUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Rounded.PlaylistPlay, null, tint = secondaryTextColor)
                                                }
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                            if (index < userPlaylists.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = textColor.copy(alpha = 0.08f))
                            }
                        }
                        
                        if (likedSongs.isEmpty() && userPlaylists.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Playlists",
                                    subtitle = "Create playlists or connect YouTube Music",
                                    icon = Icons.Rounded.PlaylistPlay,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                        }
                    }
                    
                    "Artists" -> {
                        val artists = songs.groupBy { it.artist }.keys.toList().sorted()
                        if (artists.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Artists",
                                    subtitle = "Add music to your library",
                                    icon = Icons.Rounded.MusicNote,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                        } else {
                            itemsIndexed(artists) { index, artist ->
                                val shape = getSegmentedShape(index, artists.size)
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clip(shape).clickable { onArtistClick(artist) },
                                    shape = shape,
                                    color = cardColor
                                ) {
                                    ListItem(
                                        headlineContent = { Text(artist, fontWeight = FontWeight.Bold, color = textColor) },
                                        leadingContent = {
                                            Surface(
                                                modifier = Modifier.size(48.dp),
                                                shape = CircleShape,
                                                color = accentColor.copy(alpha = 0.1f)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Rounded.MusicNote, null, tint = accentColor)
                                                }
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                                if (index < artists.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = textColor.copy(alpha = 0.08f))
                                }
                            }
                        }
                    }
                    
                    "Albums" -> {
                        val albums = songs.groupBy { it.album }.keys.toList().sorted()
                        if (albums.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Albums",
                                    subtitle = "Add music to your library",
                                    icon = Icons.Rounded.Album,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                        } else {
                            itemsIndexed(albums) { index, album ->
                                val shape = getSegmentedShape(index, albums.size)
                                val albumSongs = songs.filter { it.album == album }
                                val artist = albumSongs.firstOrNull()?.artist ?: "Unknown Artist"
                                
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clip(shape).clickable { onAlbumClick(album, albumSongs) },
                                    shape = shape,
                                    color = cardColor
                                ) {
                                    ListItem(
                                        headlineContent = { Text(album, fontWeight = FontWeight.Bold, color = textColor) },
                                        supportingContent = { Text(artist, color = secondaryTextColor) },
                                        leadingContent = {
                                            Surface(
                                                modifier = Modifier.size(48.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                color = accentColor.copy(alpha = 0.1f)
                                            ) {
                                                // Try to get art from first song
                                                val artUri = albumSongs.firstOrNull()?.albumArtUri
                                                if (artUri != null) {
                                                    AsyncImage(
                                                        model = artUri,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Rounded.Album, null, tint = accentColor)
                                                    }
                                                }
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                                if (index < albums.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = textColor.copy(alpha = 0.08f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}




@Composable
private fun LibrarySongCard(
    song: Song,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    isYouTube: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp),
    onDownloadClick: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isLocalOriginal: Boolean = false,
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
                    text = song.artist.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Unknown Artist",
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null || song.thumbnailUrl != null) {
                        AsyncImage(
                            model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isYouTube) Color(0xFFFF0000).copy(alpha = 0.2f)
                                    else accentColor.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = if (isYouTube) Color(0xFFFF0000) else accentColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                     // Download Status / Button
                    if (isDownloading) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = accentColor
                        )
                    } else if (isDownloaded) {
                        IconButton(onClick = { onDownloadClick?.invoke() }) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else if (isLocalOriginal) {
                         Icon(
                            Icons.Rounded.Smartphone,
                            contentDescription = "Local File",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (onDownloadClick != null) {
                         IconButton(onClick = onDownloadClick) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Download",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = textColor,
                supportingColor = secondaryTextColor
            )
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(24.dp),
        color = cardColor,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = secondaryTextColor.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: com.ivor.ivormusic.data.PlaylistDisplayItem,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    viewModel: HomeViewModel,
    isDarkMode: Boolean
) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val backgroundColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer

    LaunchedEffect(playlist.id) {
        val listId = if (playlist.url?.contains("list=") == true) playlist.url.substringAfter("list=") else playlist.id
        if (listId.isNotEmpty()) {
            songs = viewModel.fetchPlaylistSongs(listId)
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = primaryColor
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // ========== HERO HEADER ==========
                item {
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
                                            backgroundColor
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
                            Surface(
                                onClick = onBack,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(48.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Playlist artwork
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
                                    if (playlist.thumbnailUrl != null) {
                                        AsyncImage(
                                            model = playlist.thumbnailUrl,
                                            contentDescription = playlist.name,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(24.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(primaryContainerColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.PlaylistPlay,
                                                contentDescription = null,
                                                modifier = Modifier.size(80.dp),
                                                tint = primaryColor.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Playlist name
                            Text(
                                text = playlist.name ?: "Unknown Playlist",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Author
                            Text(
                                text = "by ${playlist.uploaderName ?: "Unknown"}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = secondaryTextColor,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Song count
                            Text(
                                text = "${songs.size} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Big centered Play button with 8-sided shape
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Create octagon shape
                                val octagonShape = remember {
                                    object : Shape {
                                        override fun createOutline(
                                            size: androidx.compose.ui.geometry.Size,
                                            layoutDirection: LayoutDirection,
                                            density: Density
                                        ): Outline {
                                            val polygon = androidx.graphics.shapes.RoundedPolygon(
                                                numVertices = 8,
                                                rounding = androidx.graphics.shapes.CornerRounding(radius = 0.2f)
                                            )
                                            val bounds = polygon.calculateBounds()
                                            val path = polygon.toPath().asComposePath()
                                            val scaleMatrix = androidx.compose.ui.graphics.Matrix()
                                            val scaleX = size.width / bounds.width
                                            val scaleY = size.height / bounds.height
                                            scaleMatrix.scale(scaleX, scaleY)
                                            scaleMatrix.translate(-bounds.left * scaleX, -bounds.top * scaleY)
                                            path.transform(scaleMatrix)
                                            return Outline.Generic(path)
                                        }
                                    }
                                }
                                
                                Surface(
                                    onClick = { if (songs.isNotEmpty()) onPlayQueue(songs, null) },
                                    modifier = Modifier.size(80.dp),
                                    shape = octagonShape,
                                    color = primaryColor,
                                    shadowElevation = 12.dp
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
                
                // Songs List with proper padding
                itemsIndexed(songs) { index, song ->
                    LibrarySongCard(
                        song = song,
                        onClick = { onPlayQueue(songs, song) },
                        cardColor = cardColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        accentColor = primaryColor,
                        isYouTube = true,
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
                
                if (songs.isEmpty() && !isLoading) {
                    item {
                        Box(
                             modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                             contentAlignment = Alignment.Center
                        ) {
                             Text(
                                 "No songs found in this playlist",
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

@Composable
fun LibraryContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit = {},
    onPlayQueue: (List<Song>, Song?) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean
    initialArtist: String? = null,
    onInitialArtistConsumed: () -> Unit = {}
) {
    var viewedPlaylist by remember { mutableStateOf<com.ivor.ivormusic.data.PlaylistDisplayItem?>(null) }
    var viewedArtist by remember { mutableStateOf<String?>(null) }
    // Album navigation state: Pair<albumName, List<Song>>
    var viewedAlbum by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    
    // Handle initial artist navigation from player
    LaunchedEffect(initialArtist) {
        if (initialArtist != null) {
            viewedArtist = initialArtist
            // Clear other views to ensure artist is shown
            viewedAlbum = null
            viewedPlaylist = null
            onInitialArtistConsumed()
        }
    }
    
    // Handle system back button for nested screens
    androidx.activity.compose.BackHandler(enabled = viewedPlaylist != null || viewedArtist != null || viewedAlbum != null) {
        when {
            viewedAlbum != null -> viewedAlbum = null
            viewedArtist != null -> viewedArtist = null
            viewedPlaylist != null -> viewedPlaylist = null
        }
    }
    
    // Determine current navigation state
    val currentScreen = when {
        viewedAlbum != null -> "album"
        viewedArtist != null -> "artist"
        viewedPlaylist != null -> "playlist"
        else -> "library"
    }
    
    androidx.compose.animation.AnimatedContent(
        targetState = currentScreen,
        label = "LibraryNav",
        transitionSpec = {
            androidx.compose.animation.slideInHorizontally { width -> width } + 
                androidx.compose.animation.fadeIn() togetherWith
            androidx.compose.animation.slideOutHorizontally { width -> -width } + 
                androidx.compose.animation.fadeOut()
        }
    ) { screen ->
        when (screen) {
            "album" -> {
                viewedAlbum?.let { (albumName, albumSongs) ->
                    com.ivor.ivormusic.ui.artist.AlbumScreen(
                        albumName = albumName,
                        artistName = albumSongs.firstOrNull()?.artist ?: "Unknown Artist",
                        songs = albumSongs,
                        onBack = { viewedAlbum = null },
                        onPlayQueue = onPlayQueue
                    )
                }
            }
            "artist" -> {
                viewedArtist?.let { artistName ->
                    com.ivor.ivormusic.ui.artist.ArtistScreen(
                        artistName = artistName,
                        songs = songs,
                        onBack = { viewedArtist = null },
                        onPlayQueue = onPlayQueue,
                        onSongClick = onSongClick,
                        onAlbumClick = { album, albumSongs -> 
                            viewedAlbum = album to albumSongs 
                        },
                        viewModel = viewModel
                    )
                }
            }
            "playlist" -> {
                viewedPlaylist?.let { playlist ->
                    PlaylistDetailScreen(
                        playlist = playlist,
                        onBack = { viewedPlaylist = null },
                        onPlayQueue = onPlayQueue,
                        viewModel = viewModel,
                        isDarkMode = isDarkMode
                    )
                }
            }
            else -> {
                LibraryScreen(
                    songs = songs,
                    onSongClick = onSongClick,
                    onPlayQueue = onPlayQueue,
                    onPlaylistClick = { viewedPlaylist = it },
                    onArtistClick = { 
                        viewedArtist = it
                        viewedAlbum = null
                        viewedPlaylist = null
                    },
                    onAlbumClick = { albumName, albumSongs -> 
                        viewedAlbum = albumName to albumSongs 
                    },
                    contentPadding = contentPadding,
                    viewModel = viewModel,
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}
