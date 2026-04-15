package com.ivor.ivormusic.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.GlobalStats
import com.ivor.ivormusic.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel,
    contentPadding: PaddingValues
) {
    val searchHistory by viewModel.searchHistory.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val globalStats by viewModel.globalStats.collectAsState()
    
    // Refresh stats on entry
    LaunchedEffect(Unit) {
        viewModel.refreshStats()
    }
    
    val topArtist = globalStats.topArtists.firstOrNull()?.name ?: "N/A"
    // Format duration from seconds
    val hours = globalStats.totalPlayTimeSeconds / 3600
    val minutes = (globalStats.totalPlayTimeSeconds % 3600) / 60
    val formattedTime = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopAppBar(
            title = { Text("Statistics") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        
        LazyColumn(
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Overview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        title = "Liked Songs",
                        value = likedSongs.size.toString(),
                        icon = Icons.Rounded.Favorite,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Total Plays",
                        value = globalStats.totalPlays.toString(),
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                StatCard(
                    title = "Top Artist",
                    value = topArtist,
                    icon = Icons.Rounded.MusicNote,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                StatCard(
                    title = "Total Play Time",
                    value = formattedTime,
                    icon = Icons.Rounded.History,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (globalStats.topSongs.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    Text("Top Songs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(globalStats.topSongs) { songStats ->
                    ListItem(
                        headlineContent = { Text(songStats.title) },
                        supportingContent = { Text("${songStats.artist} • ${songStats.playCount} plays") },
                        leadingContent = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                if (songStats.thumbnailUrl != null) {
                                    AsyncImage(model = songStats.thumbnailUrl, contentDescription = null)
                                } else {
                                    Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.padding(8.dp))
                                }
                            }
                        }
                    )
                }
            }

            if (searchHistory.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    Text("Recent Searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(searchHistory) { query ->
                    ListItem(
                        headlineContent = { Text(query) },
                        leadingContent = { Icon(Icons.Rounded.History, null) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeFromSearchHistory(query) }) {
                                Icon(Icons.Rounded.Close, null)
                            }
                        }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding())) }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, null, tint = color)
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
