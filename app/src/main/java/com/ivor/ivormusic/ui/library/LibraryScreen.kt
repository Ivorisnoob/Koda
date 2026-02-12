package com.ivor.ivormusic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.artist.ArtistScreen

/**
 * Public entry point for the Library tab content.
 * Handles navigation to Artist details if initialArtist is provided.
 */
@Composable
fun LibraryContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit = {},
    onPlayQueue: (List<Song>, Song?) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    initialArtist: String? = null,
    onInitialArtistConsumed: () -> Unit = {},
    onStatsClick: () -> Unit = {}
) {
    var viewedArtist by remember { mutableStateOf<String?>(null) }
    var viewedAlbum by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    
    // Handle initial artist navigation
    LaunchedEffect(initialArtist) {
        if (initialArtist != null) {
            viewedArtist = initialArtist
            onInitialArtistConsumed()
        }
    }

    BackHandler(enabled = viewedArtist != null || viewedAlbum != null) {
        when {
            viewedAlbum != null -> viewedAlbum = null
            viewedArtist != null -> viewedArtist = null
        }
    }

    AnimatedContent(
        targetState = when {
            viewedAlbum != null -> "album"
            viewedArtist != null -> "artist"
            else -> "library"
        },
        label = "LibraryNav",
        transitionSpec = {
            if (targetState == "library") {
                slideInHorizontally { -it / 3 } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            } else {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
            }
        }
    ) { screen ->
        when (screen) {
            "artist" -> {
                viewedArtist?.let { artistName ->
                    ArtistScreen(
                        artistName = artistName,
                        artistId = artistName, // Using name as ID for local artists usually
                        songs = emptyList(), // Let vm fetch
                        onBack = { viewedArtist = null },
                        onPlayQueue = onPlayQueue,
                        onSongClick = onSongClick,
                        onAlbumClick = { album, songs -> viewedAlbum = album to songs },
                        viewModel = viewModel
                    )
                }
            }
            "album" -> {
                viewedAlbum?.let { (albumName, albumSongs) ->
                    // Reuse PlaylistDetailScreen for Album view as they are similar
                    PlaylistDetailScreen(
                        playlist = PlaylistDisplayItem(
                            name = albumName,
                            url = albumName,
                            uploaderName = albumSongs.firstOrNull()?.artist ?: "Unknown Artist",
                            itemCount = albumSongs.size,
                            thumbnailUrl = albumSongs.firstOrNull()?.albumArtUri.toString()
                        ),
                        onBack = { viewedAlbum = null },
                        onPlayQueue = onPlayQueue,
                        viewModel = viewModel,
                        isDarkMode = isDarkMode,
                        preloadedSongs = albumSongs,
                        isAlbum = true
                    )
                }
            }
            "library" -> {
                LibraryScreenInternal(
                    songs = songs,
                    onSongClick = onSongClick,
                    onPlayQueue = onPlayQueue,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = { viewedArtist = it },
                    onAlbumClick = { album, songs -> viewedAlbum = album to songs },
                    contentPadding = contentPadding,
                    viewModel = viewModel,
                    onStatsClick = onStatsClick
                )
            }
        }
    }
}

enum class SortOption(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryScreenInternal(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String, List<Song>) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    onStatsClick: () -> Unit
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("All", "Playlists", "Artists", "Albums")
    
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var sortOption by rememberSaveable { mutableStateOf(SortOption.TITLE) }
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    val filteredSongs by remember(songs, searchQuery, sortOption) {
        derivedStateOf {
            val filtered = if (searchQuery.isBlank()) songs else songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
            when (sortOption) {
                SortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
                SortOption.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
                SortOption.ALBUM -> filtered.sortedBy { it.album.lowercase() }
                SortOption.DURATION -> filtered.sortedBy { it.duration }
            }
        }
    }

    val filteredPlaylists by remember(userPlaylists, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) userPlaylists else userPlaylists.filter {
                (it.name ?: "").contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
// HEADER
        AnimatedContent(
            targetState = isSearchActive,
            label = "HeaderTransition"
        ) { active ->
            if (!active) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isSearchActive = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Rounded.Search, "Search")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onStatsClick,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.Insights, "Statistics")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(tabs.size) { index ->
                            val selected = selectedTab == index
                            FilterChip(
                                selected = selected,
                                onClick = { selectedTab = index },
                                label = { Text(tabs[index]) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = Color.Transparent,
                                    selectedBorderColor = Color.Transparent,
                                    enabled = true,
                                    selected = selected
                                ),
                                shape = CircleShape,
                                modifier = Modifier.height(36.dp)
                            )
                        }
                    }
                }
            } else {
                 SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { focusManager.clearFocus() },
                            expanded = false,
                            onExpandedChange = { if (!it) isSearchActive = false },
                            placeholder = { Text("Search your library...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = { 
                                IconButton(onClick = { 
                                    if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchActive = false 
                                }) {
                                    Icon(Icons.Rounded.Close, "Clear") 
                                }
                            },
                        )
                    },
                    expanded = false,
                    onExpandedChange = { if (!it) isSearchActive = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        dividerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    content = {}
                )
                BackHandler { isSearchActive = false }
            }
        }

        // CONTROLS & CONTENT
        // We wrap everything in a rounded Surface to give the "sheet" look
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow // Slightly different bg
        ) {
            Column {
                if (selectedTab == 0 || selectedTab == 2 || selectedTab == 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val count = when(selectedTab) {
                             0 -> "${filteredSongs.size} tracks"
                             2 -> "${filteredSongs.groupBy { it.artist }.size} artists"
                             3 -> "${filteredSongs.groupBy { it.album }.size} albums"
                             else -> ""
                        }
                        Text(
                            text = count,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedTab == 2 || selectedTab == 3) {
                                IconButton(
                                    onClick = { isGridView = !isGridView },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                        contentDescription = "Toggle View",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Box {
                                FilledTonalButton(
                                    onClick = { isSortMenuExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(sortOption.label, style = MaterialTheme.typography.labelMedium)
                                }
                                
                                DropdownMenu(
                                    expanded = isSortMenuExpanded,
                                    onDismissRequest = { isSortMenuExpanded = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    SortOption.values().forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                sortOption = option
                                                isSortMenuExpanded = false
                                            },
                                            leadingIcon = if (sortOption == option) {
                                                { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ExpressivePullToRefresh(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.weight(1f) // Fill remaining space
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn() + slideInVertically { it / 10 } togetherWith fadeOut() + slideOutVertically { -it / 10 }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { tabIndex ->
                        when (tabIndex) {
                            0 -> AllSongsList(
                                songs = filteredSongs,
                                likedSongs = likedSongs,
                                onSongClick = { onPlayQueue(filteredSongs, it) },
                                onLikedSongsClick = { 
                                    onPlaylistClick(PlaylistDisplayItem(
                                        name = "Liked Songs",
                                        url = "LM",
                                        uploaderName = "You",
                                        itemCount = likedSongs.size,
                                        thumbnailUrl = null
                                    ))
                                },
                                onDownloadClick = { viewModel.toggleDownload(it) },
                                isDownloading = { viewModel.isDownloading(it.id) },
                                isDownloaded = { viewModel.isDownloaded(it.id) },
                                padding = contentPadding
                            )
                            1 -> PlaylistsList(
                                playlists = filteredPlaylists,
                                likedSongs = likedSongs,
                                onPlaylistClick = onPlaylistClick,
                                onLikedSongsClick = { 
                                     onPlaylistClick(PlaylistDisplayItem(
                                        name = "Liked Songs",
                                        url = "LM",
                                        uploaderName = "You",
                                        itemCount = likedSongs.size,
                                        thumbnailUrl = null
                                    ))
                                },
                                padding = contentPadding
                            )
                            2 -> ArtistsList(
                                songs = filteredSongs,
                                isGrid = isGridView,
                                onArtistClick = onArtistClick,
                                padding = contentPadding
                            )
                            3 -> AlbumsList(
                                songs = filteredSongs,
                                isGrid = isGridView,
                                onAlbumClick = onAlbumClick,
                                padding = contentPadding
                            )
                        }
                    }
                }
            }
        }
    }
}

// ... Use same AllSongsList, PlaylistsList, ArtistsList, AlbumsList, Components as before (updated with proper imports and logic) ...

// ============ COMPONENT DEFINITIONS ============

@Composable
private fun AllSongsList(
    songs: List<Song>,
    likedSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    onLikedSongsClick: () -> Unit,
    onDownloadClick: (Song) -> Unit,
    isDownloading: (Song) -> Boolean,
    isDownloaded: (Song) -> Boolean,
    padding: PaddingValues
) {
    if (songs.isEmpty() && likedSongs.isEmpty()) {
        EmptyState(Icons.Rounded.MusicNote, "No songs found", "Try adding music to your device or checking your filters.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            bottom = 100.dp + padding.calculateBottomPadding(),
            top = 0.dp,
            start = 16.dp,
            end = 16.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        if (likedSongs.isNotEmpty()) item {
            PlaylistListItem(
                name = "Liked Songs",
                count = likedSongs.size,
                thumbnailUrl = null,
                isLikedSongs = true,
                onClick = onLikedSongsClick
            )
        }

        items(songs, key = { it.id }) { song ->
            SongListItem(
                song = song,
                onClick = { onSongClick(song) },
                onDownloadClick = { onDownloadClick(song) },
                isDownloading = isDownloading(song),
                isDownloaded = isDownloaded(song)
            )
        }
    }
}

@Composable
private fun PlaylistsList(
    playlists: List<PlaylistDisplayItem>,
    likedSongs: List<Song>,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit,
    onLikedSongsClick: () -> Unit,
    padding: PaddingValues
) {
    if (playlists.isEmpty() && likedSongs.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Rounded.PlaylistPlay, "No Playlists", "Create a playlist to get started.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            bottom = 100.dp + padding.calculateBottomPadding(),
            top = 0.dp,
            start = 16.dp,
            end = 16.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        if (likedSongs.isNotEmpty()) {
            item {
                PlaylistListItem(name = "Liked Songs", count = likedSongs.size, thumbnailUrl = null, isLikedSongs = true, onClick = onLikedSongsClick)
            }
        }
        items(playlists) { playlist ->
            PlaylistListItem(name = playlist.name ?: "Untitled", count = playlist.itemCount, thumbnailUrl = playlist.thumbnailUrl, onClick = { onPlaylistClick(playlist) })
        }
    }
}

@Composable
private fun ArtistsList(songs: List<Song>, isGrid: Boolean, onArtistClick: (String) -> Unit, padding: PaddingValues) {
    val artists = remember(songs) { songs.groupBy { it.artist }.toList().sortedBy { it.first } }
    if (artists.isEmpty()) { EmptyState(Icons.Rounded.MusicNote, "No Artists", "Add music to see artists here."); return }

    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 24.dp,
                bottom = 100.dp + padding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(artists) { (artist, artistSongs) -> MediaGridItem(artist, "${artistSongs.size} tracks", artistSongs.firstOrNull()?.albumArtUri.toString(), Icons.Rounded.MusicNote) { onArtistClick(artist) } }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                bottom = 100.dp + padding.calculateBottomPadding(),
                start = 16.dp,
                end = 16.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(artists) { (artist, artistSongs) -> MediaListItem(artist, "${artistSongs.size} tracks", artistSongs.firstOrNull()?.albumArtUri.toString(), Icons.Rounded.MusicNote) { onArtistClick(artist) } }
        }
    }
}

@Composable
private fun AlbumsList(songs: List<Song>, isGrid: Boolean, onAlbumClick: (String, List<Song>) -> Unit, padding: PaddingValues) {
    val albums = remember(songs) { songs.groupBy { it.album }.toList().sortedBy { it.first } }
    if (albums.isEmpty()) { EmptyState(Icons.Rounded.Album, "No Albums", "Add music to see albums here."); return }

    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 24.dp,
                bottom = 100.dp + padding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(albums) { (album, albumSongs) -> MediaGridItem(album, albumSongs.firstOrNull()?.artist ?: "Unknown", albumSongs.firstOrNull()?.albumArtUri.toString(), Icons.Rounded.Album) { onAlbumClick(album, albumSongs) } }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                bottom = 100.dp + padding.calculateBottomPadding(),
                start = 16.dp,
                end = 16.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(albums) { (album, albumSongs) -> MediaListItem(album, albumSongs.firstOrNull()?.artist ?: "Unknown", albumSongs.firstOrNull()?.albumArtUri.toString(), Icons.Rounded.Album) { onAlbumClick(album, albumSongs) } }
        }
    }
}

@Composable
fun SongListItem(song: Song, onClick: () -> Unit, onDownloadClick: () -> Unit, isDownloading: Boolean, isDownloaded: Boolean) {
    Column {
        ListItem(
            headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = { 
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(48.dp)) { 
                    if (song.albumArtUri != null || song.thumbnailUrl != null) 
                        AsyncImage(model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) 
                    else 
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } 
                } 
            },
            trailingContent = { 
                if (isDownloading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) 
                else if (isDownloaded) Icon(Icons.Rounded.Smartphone, "Downloaded", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) 
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick)
        )
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }
}

@Composable
fun PlaylistListItem(name: String, count: Int, thumbnailUrl: String?, isLikedSongs: Boolean = false, onClick: () -> Unit) {
    Column {
        ListItem(
            headlineContent = { Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            supportingContent = { 
                val countText = if (count >= 0) "$count tracks" else "Playlist"
                Text(countText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)) 
            },
            leadingContent = { 
                Surface(shape = RoundedCornerShape(12.dp), color = if (isLikedSongs) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(48.dp)) { 
                    if (thumbnailUrl != null) AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop) 
                    else Box(contentAlignment = Alignment.Center) { Icon(if (isLikedSongs) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = if (isLikedSongs) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) } 
                } 
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick)
        )
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }
}

@Composable
fun MediaListItem(title: String, subtitle: String, imageUrl: String?, icon: ImageVector, onClick: () -> Unit) {
    Column {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle) },
            leadingContent = { 
                Surface(shape = RoundedCornerShape(12.dp), modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) { 
                    if (imageUrl != null && imageUrl != "null") 
                        AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop) 
                    else 
                        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) } 
                } 
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick)
        )
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }
}

@Composable
fun MediaGridItem(title: String, subtitle: String, imageUrl: String?, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(160.dp).clickable(onClick = onClick)) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(160.dp)) { if (imageUrl != null && imageUrl != "null") AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop) else Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) } }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.surfaceContainerHigh)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistDisplayItem,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    preloadedSongs: List<Song>? = null,
    isAlbum: Boolean = false
) {
    var songs by remember { mutableStateOf(preloadedSongs ?: emptyList()) }
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Fetch songs if not preloaded
    LaunchedEffect(playlist.id) {
        if (preloadedSongs == null) {
            songs = viewModel.fetchPlaylistSongs(playlist.id)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        TopAppBar(
            title = { Text(if (isAlbum) "Album" else "Playlist") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        
        LazyColumn(contentPadding = PaddingValues(bottom = 160.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 8.dp, modifier = Modifier.size(240.dp)) {
                         if (playlist.thumbnailUrl != null) AsyncImage(model = playlist.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop) 
                         else Box(contentAlignment = Alignment.Center, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) { Icon(if (isAlbum) Icons.Rounded.Album else Icons.AutoMirrored.Rounded.PlaylistPlay, null, modifier = Modifier.size(80.dp)) }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(playlist.name ?: "Untitled", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${songs.size} tracks", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilledTonalButton(onClick = { if (songs.isNotEmpty()) onPlayQueue(songs, null) }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(modifier = Modifier.width(8.dp)); Text("Play All") }
                        FilledTonalButton(onClick = { if (songs.isNotEmpty()) onPlayQueue(songs.shuffled(), null) }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null); Spacer(modifier = Modifier.width(8.dp)); Text("Shuffle") }
                    }
                }
            }
            items(songs) { song ->
                SongListItem(song, onClick = { onPlayQueue(songs, song) }, onDownloadClick = { viewModel.toggleDownload(song) }, isDownloading = viewModel.isDownloading(song.id), isDownloaded = viewModel.isDownloaded(song.id))
            }
        }
    }
}

// StatsScreen and StatCard moved to independent file

// ... (Existing components: SongListItem, PlaylistListItem, MediaListItem, MediaGridItem, PlaylistDetailScreen)

