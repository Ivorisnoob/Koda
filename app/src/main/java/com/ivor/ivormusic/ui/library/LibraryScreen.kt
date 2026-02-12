package com.ivor.ivormusic.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
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
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import com.ivor.ivormusic.data.ArtistStats
import com.ivor.ivormusic.data.SongStats
import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.GlobalStats
import com.ivor.ivormusic.data.StatsRepository

@Composable
private fun getSegmentedShape(index: Int, count: Int, cornerSize: androidx.compose.ui.unit.Dp = 28.dp): Shape {
    return when {
        count == 1 -> RoundedCornerShape(cornerSize)
        index == 0 -> RoundedCornerShape(topStart = cornerSize, topEnd = cornerSize)
        index == count - 1 -> RoundedCornerShape(bottomStart = cornerSize, bottomEnd = cornerSize)
        else -> RectangleShape
    }
}

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
    onStatsClick: () -> Unit = {},
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Your Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            
            IconButton(
                onClick = onStatsClick,
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(Icons.Rounded.Insights, contentDescription = "Statistics")
            }
        }
        
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
        ExpressivePullToRefresh(
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
                                                if (playlist.itemCount > 0) "${playlist.itemCount} songs" else "Playlist",
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
                            
                            itemsIndexed(songs) { index, song -> 
                                LibrarySongCard(
                                    song = song,
                                    onClick = { onPlayQueue(songs, song) },
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    accentColor = accentColor,
                                    shape = getSegmentedShape(index, songs.size),
                                    isLocalOriginal = true
                                )
                                if (index < songs.size - 1) {
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
                                    supportingContent = { Text(if (playlist.itemCount > 0) "${playlist.itemCount} songs" else "Playlist", color = secondaryTextColor) },
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
                item {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    ) {
                        // Guard against invalid dimensions during transitions
                        if (maxWidth <= 0.dp || maxHeight <= 0.dp) {
                            return@BoxWithConstraints
                        }
                        
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
                            
                        }
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 40.dp) // Seat it on the edge of the header (half overlap)
                        ) {
                            val octagonShape = MaterialShapes.Cookie9Sided.toShape()
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
                                onClick = { if (songs.isNotEmpty()) onPlayQueue(songs, null) },
                                modifier = Modifier
                                    .size(80.dp)
                                    .graphicsLayer { 
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                shape = octagonShape,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel,
    contentPadding: PaddingValues
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val statsRepository = remember { StatsRepository(context) }
    var globalStats by remember { mutableStateOf<GlobalStats?>(null) }
    var history by remember { mutableStateOf<List<PlayHistoryEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        globalStats = statsRepository.getGlobalStats()
        history = statsRepository.loadHistory()
        isLoading = false
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Insights", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            statsRepository.clearHistory()
                            globalStats = null
                            history = emptyList()
                        }
                    }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear History")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        val currentStats = globalStats
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else if (currentStats == null || currentStats.totalPlays == 0) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyStateCard(
                    title = "No Statistics Yet",
                    subtitle = "Start listening to some music!",
                    icon = Icons.Rounded.Insights,
                    cardColor = cardColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatSummaryCard(
                                label = "Total Plays",
                                value = currentStats.totalPlays.toString(),
                                icon = Icons.Rounded.PlayArrow,
                                modifier = Modifier.weight(1.0f),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                delay = 0
                            )
                            StatSummaryCard(
                                label = "Play Time",
                                value = formatTime(currentStats.totalPlayTimeSeconds),
                                icon = Icons.Rounded.BarChart,
                                modifier = Modifier.weight(1.0f),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                delay = 100
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatSummaryCard(
                                label = "Artists",
                                value = currentStats.uniqueArtists.toString(),
                                icon = Icons.Rounded.MusicNote,
                                modifier = Modifier.weight(1.0f),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                delay = 200
                            )
                            StatSummaryCard(
                                label = "Songs",
                                value = currentStats.uniqueSongs.toString(),
                                icon = Icons.Rounded.Album,
                                modifier = Modifier.weight(1.0f),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                delay = 300
                            )
                        }
                    }
                }

                if (currentStats.topSongs.isNotEmpty()) {
                    item {
                        Text(
                            "Top Tracks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    itemsIndexed(currentStats.topSongs.take(5)) { index, songStat ->
                        TopSongItem(songStat, cardColor, textColor, secondaryTextColor, index = index)
                    }
                }

                if (currentStats.topArtists.isNotEmpty()) {
                    item {
                        Text(
                            "Favorite Artists",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            itemsIndexed(currentStats.topArtists.take(10)) { index, artist ->
                                TopArtistCard(artist, cardColor, textColor, secondaryTextColor, index = index)
                            }
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    item {
                        Text(
                            "Playback History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    itemsIndexed(history.take(50)) { index, entry ->
                        HistoryItem(entry, textColor, secondaryTextColor, index = index)
                        if (index < history.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = textColor.copy(alpha = 0.05f))
                        }
                    }
                    
                    item {
                        Button(
                            onClick = { 
                                scope.launch { 
                                    statsRepository.clearHistory()
                                    globalStats = null
                                    history = emptyList()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer, 
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Clear History and Stats")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatSummaryCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    delay: Int,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "StatCardScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "StatCardAlpha"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        shape = RoundedCornerShape(28.dp), // Extra Extra Large corner for expressive feel
        color = color,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun TopSongItem(
    stat: SongStats,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    index: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200L + (index * 50))
        visible = true
    }

    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "alpha")
    val translationY by animateFloatAsState(if (visible) 0f else 40f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), label = "trans")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translationY
            },
        shape = RoundedCornerShape(20.dp),
        color = cardColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = stat.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stat.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stat.artist, style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stat.playCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("plays", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
            }
        }
    }
}

@Composable
fun TopArtistCard(
    stat: ArtistStats,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    index: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400L + (index * 80))
        visible = true
    }

    val scale by animateFloatAsState(if (visible) 1f else 0.5f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "scale")

    Surface(
        modifier = Modifier
            .width(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(28.dp),
        color = cardColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Using an expressive shape for the artist icon container
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stat.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
            Text("${stat.playCount} sessions", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
        }
    }
}

@Composable
fun HistoryItem(
    entry: PlayHistoryEntry,
    textColor: Color,
    secondaryTextColor: Color,
    index: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(600L + (index * 30))
        visible = true
    }

    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "alpha")
    val slide by animateFloatAsState(if (visible) 0f else 20f, spring(Spring.DampingRatioNoBouncy), label = "slide")

    ListItem(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationX = slide
        },
        headlineContent = { Text(entry.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${entry.artist} • ${formatTimestamp(entry.timestamp)}", color = secondaryTextColor, maxLines = 1) },
        leadingContent = {
            AsyncImage(
                model = entry.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val formatter = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
    return formatter.format(date)
}


@Composable
fun LibraryContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit = {},
    onPlayQueue: (List<Song>, Song?) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    initialArtist: String? = null,
    onInitialArtistConsumed: () -> Unit = {},
    onStatsClick: () -> Unit = {}
) {
    var viewedPlaylist by remember { mutableStateOf<com.ivor.ivormusic.data.PlaylistDisplayItem?>(null) }
    var viewedArtist by remember { mutableStateOf<String?>(null) }
    var viewedAlbum by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    
    LaunchedEffect(initialArtist) {
        if (initialArtist != null) {
            viewedArtist = initialArtist
            viewedAlbum = null
            viewedPlaylist = null
            onInitialArtistConsumed()
        }
    }
    
    androidx.activity.compose.BackHandler(enabled = viewedPlaylist != null || viewedArtist != null || viewedAlbum != null) {
        when {
            viewedAlbum != null -> viewedAlbum = null
            viewedArtist != null -> viewedArtist = null
            viewedPlaylist != null -> viewedPlaylist = null
        }
    }
    
    val currentScreen = when {
        viewedAlbum != null -> "album"
        viewedArtist != null -> "artist"
        viewedPlaylist != null -> "playlist"
        else -> "library"
    }
    
    val emphasizedEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    
    androidx.compose.animation.AnimatedContent(
        targetState = currentScreen,
        label = "LibraryNav",
        transitionSpec = {
            val emphasizedTweenEnter = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(
                durationMillis = 500,
                easing = emphasizedEasing
            )
            val emphasizedTweenFade = androidx.compose.animation.core.tween<Float>(
                durationMillis = 400,
                easing = emphasizedEasing
            )
            
            androidx.compose.animation.slideInHorizontally(animationSpec = emphasizedTweenEnter) { width -> width } + 
                androidx.compose.animation.fadeIn(animationSpec = emphasizedTweenFade) togetherWith
            androidx.compose.animation.slideOutHorizontally(animationSpec = emphasizedTweenEnter) { width -> -width / 4 } + 
                androidx.compose.animation.fadeOut(animationSpec = emphasizedTweenFade)
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
                    isDarkMode = isDarkMode,
                    onStatsClick = onStatsClick
                )
            }
        }
    }
}
