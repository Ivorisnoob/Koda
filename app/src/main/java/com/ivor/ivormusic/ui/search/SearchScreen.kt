package com.ivor.ivormusic.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.delay
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

/**
 * Search Screen with Material 3 Expressive design
 * - Beautiful rounded search field
 * - Genre filter chips
 * - YouTube Music search integration
 * - Premium card design for results
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var youtubeResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    val scope = rememberCoroutineScope()
    
    // Theme colors from MaterialTheme
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val chipBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    
    // Genre filter state
    val genres = listOf("All", "Pop", "Rock", "Hip Hop", "Electronic", "Jazz", "Classical")
    val selectedGenres = remember { mutableStateListOf("All") }
    
    // Filter local songs based on query and genres
    val filteredLocalSongs = remember(query, songs, selectedGenres.toList()) {
        songs.filter { song ->
            val matchesQuery = query.isEmpty() || 
                song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true)
            
            val matchesGenre = selectedGenres.contains("All") || 
                selectedGenres.any { genre -> 
                    song.title.contains(genre, ignoreCase = true) ||
                    song.artist.contains(genre, ignoreCase = true)
                }
            
            matchesQuery && matchesGenre
        }
    }
    
    // Search YouTube when query changes
    LaunchedEffect(query) {
        if (query.length >= 3) {
            delay(500) // Debounce
            isLoading = true
            youtubeResults = viewModel.searchYouTube(query)
            isLoading = false
        } else {
            youtubeResults = emptyList()
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
            "Search",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
        
        // Search Field with beautiful rounded corners
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = surfaceColor,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { 
                    Text("Search songs, artists, albums...", color = secondaryTextColor) 
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = accentColor
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = secondaryTextColor
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = accentColor,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Genre Filter Chips with expressive styling
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { genre ->
                val isSelected = selectedGenres.contains(genre)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (genre == "All") {
                            selectedGenres.clear()
                            selectedGenres.add("All")
                        } else {
                            selectedGenres.remove("All")
                            if (isSelected) {
                                selectedGenres.remove(genre)
                                if (selectedGenres.isEmpty()) {
                                    selectedGenres.add("All")
                                }
                            } else {
                                selectedGenres.add(genre)
                            }
                        }
                    },
                    label = { 
                        Text(
                            genre,
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

        Spacer(modifier = Modifier.height(16.dp))

        // Results Section - no padding on Box, pass to LazyColumn instead
        Box(
            modifier = Modifier.weight(1f)
        ) {
            val bottomPadding = contentPadding.calculateBottomPadding()
            when {
                isLoading -> {
                    // M3 Expressive LoadingIndicator
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingIndicator(
                                modifier = Modifier.size(48.dp),
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Searching YouTube Music...",
                                color = secondaryTextColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                query.isEmpty() && selectedGenres.contains("All") -> {
                    // Browse section when no search
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding)
                    ) {
                        item {
                            Text(
                                "Browse Your Library",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val displaySongs = songs.take(20)
                        itemsIndexed(displaySongs) { index, song ->
                            SongCard(
                                song = song,
                                onClick = { onSongClick(song) },
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = accentColor,
                                shape = getSegmentedShape(index, displaySongs.size)
                            )
                            if (index < displaySongs.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = textColor.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
                youtubeResults.isNotEmpty() -> {
                    // YouTube Search Results
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding)
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.TravelExplore,
                                    contentDescription = null,
                                    tint = Color(0xFFFF0000),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    "YouTube Music Results",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }
                        itemsIndexed(youtubeResults) { index, song ->
                            SongCard(
                                song = song,
                                onClick = { onSongClick(song) },
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = accentColor,
                                isYouTube = true,
                                shape = getSegmentedShape(index, youtubeResults.size)
                            )
                            if (index < youtubeResults.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = textColor.copy(alpha = 0.08f)
                                )
                            }
                        }
                        
                        if (filteredLocalSongs.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Local Library",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                            val localResults = filteredLocalSongs.take(10)
                            itemsIndexed(localResults) { index, song ->
                                SongCard(
                                    song = song,
                                    onClick = { onSongClick(song) },
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    accentColor = accentColor,
                                    shape = getSegmentedShape(index, localResults.size)
                                )
                                if (index < localResults.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        color = textColor.copy(alpha = 0.08f)
                                    )
                                }
                            }
                            
                            // Load More Button
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingMore) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = accentColor
                                        )
                                    } else {
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                scope.launch {
                                                    isLoadingMore = true
                                                    val newResults = viewModel.loadMoreResults(query)
                                                    if (newResults.isNotEmpty()) {
                                                        youtubeResults = youtubeResults + newResults
                                                    }
                                                    isLoadingMore = false
                                                }
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = chipBgColor,
                                                contentColor = accentColor
                                            )
                                        ) {
                                            Text("Load More")
                                        }
        
                                }
                            }
                            }
                        }
                    }
                }
                filteredLocalSongs.isEmpty() && youtubeResults.isEmpty() -> {
                    // No results
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = secondaryTextColor.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No results found",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Try different keywords or filters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                        }
                    }
                }
                else -> {
                    // Local search results only
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding)
                    ) {
                        item {
                            Text(
                                "${filteredLocalSongs.size} results",
                                style = MaterialTheme.typography.labelLarge,
                                color = secondaryTextColor,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        itemsIndexed(filteredLocalSongs) { index, song ->
                            SongCard(
                                song = song,
                                onClick = { onSongClick(song) },
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = accentColor,
                                shape = getSegmentedShape(index, filteredLocalSongs.size)
                            )
                            if (index < filteredLocalSongs.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = textColor.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongCard(
    song: Song,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    isYouTube: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    Surface(
        modifier = Modifier
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
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = textColor,
                supportingColor = secondaryTextColor
            )
        )
    }
}
