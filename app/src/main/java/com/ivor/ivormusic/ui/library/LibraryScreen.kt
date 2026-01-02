package com.ivor.ivormusic.ui.library

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.home.HomeViewModel

/**
 * Library Screen with Material 3 Expressive design
 * - YouTube playlists and liked songs integration
 * - Premium card designs with beautiful rounded corners
 * - Category tabs
 * - Quick access cards
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable
fun LibraryScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit = {},
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    // Theme colors
    val backgroundColor = if (isDarkMode) Color.Black else Color(0xFFF8F8F8)
    val surfaceColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White
    val cardColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFFB3B3B3) else Color(0xFF666666)
    val accentColor = if (isDarkMode) Color(0xFF3D5AFE) else Color(0xFF6200EE)
    val chipBgColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE8E8E8)
    
    // YouTube connection status
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    
    // YouTube playlists
    var userPlaylists by remember { mutableStateOf<List<com.ivor.ivormusic.data.PlaylistDisplayItem>>(emptyList()) }

    // Liked songs from YouTube
    var likedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingLiked by remember { mutableStateOf(false) }
    
    // Tab state
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Songs", "Playlists", "Artists", "Albums")
    
    // Load liked songs and playlists when YouTube is connected
    LaunchedEffect(isYouTubeConnected) {
        if (isYouTubeConnected) {
            isLoadingLiked = true
            likedSongs = viewModel.getLikedMusic()
            userPlaylists = viewModel.getUserPlaylists()
            isLoadingLiked = false
        }
    }

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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (tabs[selectedTab]) {
                "All" -> {
                    if (isLoadingLiked) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = accentColor
                                )
                            }
                        }
                    } else {
                        // YouTube Liked Songs Section
                        if (isYouTubeConnected && likedSongs.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Liked on YouTube Music",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                        Text(
                                            "${likedSongs.size} songs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryTextColor
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFFF0000).copy(alpha = 0.15f),
                                        modifier = Modifier.clickable { /* Play all logic */ }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = Color(0xFFFF0000),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Play All",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color(0xFFFF0000),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            
                            items(likedSongs.take(5)) { song ->
                                LibrarySongCard(
                                    song = song,
                                    onClick = { onSongClick(song) },
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    accentColor = accentColor,
                                    isYouTube = true
                                )
                            }
                        }

                        // Local Library Section
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "Local Library",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (songs.isNotEmpty()) {
                            items(songs.take(10)) { song ->
                                LibrarySongCard(
                                    song = song,
                                    onClick = { onSongClick(song) },
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    accentColor = accentColor
                                )
                            }
                        } else {
                            item {
                                 Text("No local songs found", color = secondaryTextColor)
                            }
                        }
                    }
                }
                
                "Songs" -> {
                    // Show all songs (YouTube Liked + Local)
                    val allSongs = if (isYouTubeConnected) likedSongs + songs else songs
                    if (allSongs.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No songs found",
                                subtitle = "Add music or connect YouTube Music",
                                icon = Icons.Rounded.MusicNote,
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    } else {
                        items(allSongs) { song ->
                            LibrarySongCard(
                                song = song,
                                onClick = { onSongClick(song) },
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = accentColor,
                                isYouTube = song.source == com.ivor.ivormusic.data.SongSource.YOUTUBE
                            )
                        }
                    }
                }
                
                "Playlists" -> {
                    if (userPlaylists.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Playlists Found",
                                subtitle = if (isYouTubeConnected) "Could not load library playlists" else "Connect YouTube to see your playlists",
                                icon = Icons.Rounded.PlaylistPlay,
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    } else {
                        items(userPlaylists) { playlist ->
                            // Custom Playlist Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardColor)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardColor)
                                    .clickable { onPlaylistClick(playlist) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Playlist Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Gray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (playlist.thumbnailUrl != null) {
                                         AsyncImage(
                                            model = playlist.thumbnailUrl,
                                            contentDescription = playlist.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.PlaylistPlay,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column {
                                    Text(
                                        text = playlist.name ?: "Unknown Playlist",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Text(
                                        text = playlist.uploaderName ?: "Unknown Author",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryTextColor
                                    )
                                }
                            }
                        }
                    }
                }
                
                "Artists" -> {
                    val artists = songs.groupBy { it.artist }.keys.toList().sorted()
                    items(artists) { artist ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { /* Handle artist click */ },
                            color = Color.Transparent
                        ) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
                
                "Albums" -> {
                    val albums = songs.groupBy { it.album }.keys.toList().sorted()
                    items(albums) { album ->
                         Surface(
                            modifier = Modifier.fillMaxWidth().clickable { /* Handle album click */ },
                            color = Color.Transparent
                        ) {
                            Text(
                                text = album,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradientColors))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
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
    isYouTube: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null || song.thumbnailUrl != null) {
                    AsyncImage(
                        model = song.albumArtUri ?: song.thumbnailUrl,
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
            
            // Song info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = song.title.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Untitled Song",
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isYouTube) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFF0000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "YT",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = song.artist.takeIf { it.isNotBlank() && it != "Unknown Artist" } ?: "Unknown Artist",
                        color = secondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Play indicator
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
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
