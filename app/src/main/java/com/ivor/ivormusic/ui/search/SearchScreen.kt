package com.ivor.ivormusic.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.video.VideoCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Segmented list shape helper for Expressive design
 */
@Composable
private fun getSegmentedShape(index: Int, count: Int, hasMore: Boolean = false, cornerSize: androidx.compose.ui.unit.Dp = 28.dp): Shape {
    return when {
        count == 1 && !hasMore -> RoundedCornerShape(cornerSize)
        index == 0 -> RoundedCornerShape(topStart = cornerSize, topEnd = cornerSize)
        index == count - 1 && !hasMore -> RoundedCornerShape(bottomStart = cornerSize, bottomEnd = cornerSize)
        else -> RectangleShape
    }
}

/**
 * 🌟 Material 3 Expressive Search Screen
 * 
 * Design Features:
 * - Gradient header with decorative organic shapes
 * - Beautiful rounded search field with depth
 * - Premium segmented card design for results
 * - YouTube Music integration with pagination
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song) -> Unit = { _, song -> onSongClick(song) },
    onVideoClick: (VideoItem) -> Unit = {},
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    videoMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var youtubeResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var videoResults by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var visibleLocalCount by remember { mutableIntStateOf(20) }
    val scope = rememberCoroutineScope()
    
    // Theme colors from MaterialTheme
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    // Filter local songs based on query
    val filteredLocalSongs = remember(query, songs) {
        if (query.isEmpty()) songs
        else songs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
            song.artist.contains(query, ignoreCase = true) ||
            song.album.contains(query, ignoreCase = true)
        }
    }
    
    // Search YouTube/Videos when query changes
    LaunchedEffect(query, videoMode) {
        if (query.length >= 3) {
            delay(500) // Debounce
            isLoading = true
            if (videoMode) {
                // Search for videos in video mode
                videoResults = viewModel.searchVideos(query)
                youtubeResults = emptyList()
            } else {
                // Search for songs in music mode
                youtubeResults = viewModel.searchYouTube(query)
                videoResults = emptyList()
            }
            isLoading = false
        } else {
            youtubeResults = emptyList()
            videoResults = emptyList()
        }
    }
    
    // Reset visible count when query changes
    LaunchedEffect(query) {
        visibleLocalCount = 20
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 140.dp)
        ) {
            // ========== HERO HEADER WITH SEARCH ==========
            item {
                SearchHeroHeader(
                    query = query,
                    onQueryChange = { query = it },
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    tertiaryContainerColor = tertiaryContainerColor,
                    surfaceColor = surfaceColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )
            }
            
            // ========== CONTENT ==========
            when {
                isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LoadingIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = primaryColor
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (videoMode) "Searching YouTube Videos..." else "Searching YouTube Music...",
                                    color = secondaryTextColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                query.isEmpty() -> {
                    // Browse section when no search
                    item {
                        Text(
                            "Browse Your Library",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    
                    val displaySongs = songs.take(visibleLocalCount)
                    val hasMoreLocal = songs.size > visibleLocalCount
                    
                    itemsIndexed(displaySongs) { index, song ->
                        SearchSongCard(
                            song = song,
                            onClick = { onPlayQueue(songs, song) },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = primaryColor,
                            shape = getSegmentedShape(index, displaySongs.size, hasMoreLocal),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        if (index < displaySongs.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                        }
                    }
                    
                    // Show more button for local browse
                    if (hasMoreLocal) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                            ShowMoreButton(
                                onClick = { visibleLocalCount += 20 },
                                cardColor = cardColor,
                                primaryColor = primaryColor
                            )
                        }
                    }
                }
                
                // Video Mode Results
                videoMode && videoResults.isNotEmpty() -> {
                    // Video Search Results Section
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF0000).copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.TravelExplore,
                                        contentDescription = null,
                                        tint = Color(0xFFFF0000),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                "YouTube Videos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "${videoResults.size} results",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor
                            )
                        }
                    }
                    
                    // Display video results
                    itemsIndexed(videoResults) { index, video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                
                youtubeResults.isNotEmpty() -> {
                    // YouTube Search Results Section
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF0000).copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.TravelExplore,
                                        contentDescription = null,
                                        tint = Color(0xFFFF0000),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                "YouTube Music",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "${youtubeResults.size} results",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor
                            )
                        }
                    }
                    
                    itemsIndexed(youtubeResults) { index, song ->
                        SearchSongCard(
                            song = song,
                            onClick = { onPlayQueue(youtubeResults, song) },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = primaryColor,
                            isYouTube = true,
                            shape = getSegmentedShape(index, youtubeResults.size, hasMore = true),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        if (index < youtubeResults.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                        }
                    }
                    
                    // Load More Button for YouTube results
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
                                    scope.launch {
                                        isLoadingMore = true
                                        val newResults = viewModel.loadMoreResults(query)
                                        if (newResults.isNotEmpty()) {
                                            youtubeResults = youtubeResults + newResults
                                        }
                                        isLoadingMore = false
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
                                    Icon(
                                        Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        "Load More",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryColor
                                    )
                                }
                            }
                        }
                    }
                    
                    // Local Library matches section
                    if (filteredLocalSongs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = primaryColor.copy(alpha = 0.15f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = primaryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    "Local Library",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${filteredLocalSongs.size} matches",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                            }
                        }
                        
                        val localDisplayed = filteredLocalSongs.take(visibleLocalCount)
                        val hasMoreLocalMatches = filteredLocalSongs.size > visibleLocalCount
                        
                        itemsIndexed(localDisplayed) { index, song ->
                            SearchSongCard(
                                song = song,
                                onClick = { onPlayQueue(filteredLocalSongs, song) },
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = primaryColor,
                                shape = getSegmentedShape(index, localDisplayed.size, hasMoreLocalMatches),
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            if (index < localDisplayed.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 44.dp),
                                    color = textColor.copy(alpha = 0.06f)
                                )
                            }
                        }
                        
                        if (hasMoreLocalMatches) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 44.dp),
                                    color = textColor.copy(alpha = 0.06f)
                                )
                                ShowMoreButton(
                                    onClick = { visibleLocalCount += 20 },
                                    cardColor = cardColor,
                                    primaryColor = primaryColor
                                )
                            }
                        }
                    }
                }
                
                filteredLocalSongs.isEmpty() && youtubeResults.isEmpty() && query.isNotEmpty() -> {
                    // No results
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = secondaryTextColor.copy(alpha = 0.1f),
                                    modifier = Modifier.size(100.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = secondaryTextColor.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
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
                }
                
                else -> {
                    // Local search results only (when no YouTube results but have local matches)
                    item {
                        Text(
                            "${filteredLocalSongs.size} results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    
                    val displayedLocal = filteredLocalSongs.take(visibleLocalCount)
                    val hasMoreLocal = filteredLocalSongs.size > visibleLocalCount
                    
                    itemsIndexed(displayedLocal) { index, song ->
                        SearchSongCard(
                            song = song,
                            onClick = { onPlayQueue(filteredLocalSongs, song) },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = primaryColor,
                            shape = getSegmentedShape(index, displayedLocal.size, hasMoreLocal),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        if (index < displayedLocal.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                        }
                    }
                    
                    if (hasMoreLocal) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                            ShowMoreButton(
                                onClick = { visibleLocalCount += 20 },
                                cardColor = cardColor,
                                primaryColor = primaryColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hero Header with Search Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchHeroHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    primaryColor: Color,
    primaryContainerColor: Color,
    tertiaryContainerColor: Color,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        primaryContainerColor.copy(alpha = 0.4f),
                        tertiaryContainerColor.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                )
            )
    ) {
        val width = maxWidth
        
        // Decorative shapes
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(x = width - 50.dp, y = (-30).dp)
                .graphicsLayer { alpha = 0.1f }
                .clip(CircleShape)
                .background(primaryColor)
        )
        
        Box(
            modifier = Modifier
                .size(80.dp)
                .offset(x = (-20).dp, y = 80.dp)
                .graphicsLayer { alpha = 0.08f }
                .clip(CircleShape)
                .background(tertiaryContainerColor)
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Text(
                "Search",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Search Field with beautiful rounded corners
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = surfaceColor,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { 
                        Text("Search songs, artists, albums...", color = secondaryTextColor) 
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = primaryColor
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = query.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            IconButton(onClick = { onQueryChange("") }) {
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
                        cursorColor = primaryColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Show More Button for segmented lists
 */
@Composable
private fun ShowMoreButton(
    onClick: () -> Unit,
    cardColor: Color,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .clickable(onClick = onClick),
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
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                "Show More",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor
            )
        }
    }
}

/**
 * Song Card for search results
 */
@Composable
private fun SearchSongCard(
    song: Song,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    isYouTube: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp),
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
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null || song.thumbnailUrl != null) {
                        AsyncImage(
                            model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isYouTube) Color(0xFFFF0000).copy(alpha = 0.15f) else accentColor.copy(alpha = 0.15f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = if (isYouTube) Color(0xFFFF0000) else accentColor,
                                    modifier = Modifier.size(24.dp)
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
