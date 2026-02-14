package com.ivor.ivormusic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.artist.ArtistScreen
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.home.HomeViewModel

/**
 * The Main Library Navigation Hub.
 * Manages transitions between:
 * - Main Library View (Lists/Grid)
 * - Playlist/Album Details
 * - Artist Details
 * - Statistics
 */
@OptIn(ExperimentalAnimationApi::class)
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
    // Navigation State
    var currentRoute by rememberSaveable { mutableStateOf(LibraryRoute.Main) }
    
    // Arguments for routes
    var selectedPlaylist by remember { mutableStateOf<PlaylistDisplayItem?>(null) }
    var selectedArtistName by remember { mutableStateOf<String?>(null) }
    var selectedAlbumName by remember { mutableStateOf<String?>(null) }
    var selectedAlbumSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Handle initial deep link to artist
    LaunchedEffect(initialArtist) {
        if (initialArtist != null) {
            selectedArtistName = initialArtist
            currentRoute = LibraryRoute.Artist
            onInitialArtistConsumed()
        }
    }

    // Back Handler
    BackHandler(enabled = currentRoute != LibraryRoute.Main) {
        currentRoute = LibraryRoute.Main
    }

    AnimatedContent(
        targetState = currentRoute,
        label = "LibraryNavigation",
        transitionSpec = {
            if (targetState == LibraryRoute.Main) {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            } else {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
            }
        }
    ) { route ->
        when (route) {
            LibraryRoute.Main -> {
                LibraryMainScreen(
                    songs = songs,
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    onSongClick = onSongClick,
                    onPlayQueue = onPlayQueue,
                    onNavigateToPlaylist = { playlist ->
                        selectedPlaylist = playlist
                        currentRoute = LibraryRoute.Playlist
                    },
                    onNavigateToArtist = { artist ->
                        selectedArtistName = artist
                        currentRoute = LibraryRoute.Artist
                    },
                    onNavigateToAlbum = { album, songs ->
                        selectedAlbumName = album
                        selectedAlbumSongs = songs
                        currentRoute = LibraryRoute.Album
                    },
                    onNavigateToStats = {
                        currentRoute = LibraryRoute.Stats
                    }
                )
            }
            LibraryRoute.Playlist -> {
                selectedPlaylist?.let { playlist ->
                    PlaylistDetailScreen(
                        playlist = playlist,
                        onBack = { currentRoute = LibraryRoute.Main },
                        onPlayQueue = onPlayQueue,
                        viewModel = viewModel,
                        isAlbum = false
                    )
                }
            }
            LibraryRoute.Album -> {
                selectedAlbumName?.let { album ->
                    // Construct a pseudo-playlist item for the album wrapper
                    val albumItem = PlaylistDisplayItem(
                        name = album,
                        url = album, // ID is the name for local albums usually
                        uploaderName = selectedAlbumSongs.firstOrNull()?.artist ?: "Unknown Artist",
                        itemCount = selectedAlbumSongs.size,
                        thumbnailUrl = selectedAlbumSongs.firstOrNull()?.albumArtUri.toString()
                    )
                    PlaylistDetailScreen(
                        playlist = albumItem,
                        onBack = { currentRoute = LibraryRoute.Main },
                        onPlayQueue = onPlayQueue,
                        viewModel = viewModel,
                        preloadedSongs = selectedAlbumSongs,
                        isAlbum = true
                    )
                }
            }
            LibraryRoute.Artist -> {
                selectedArtistName?.let { artist ->
                    ArtistScreen(
                        artistName = artist,
                        artistId = artist,
                        songs = songs, // Pass all songs, screen filters locally or fetches
                        onBack = { currentRoute = LibraryRoute.Main },
                        onPlayQueue = onPlayQueue,
                        onSongClick = onSongClick,
                        onAlbumClick = { album, songs ->
                            selectedAlbumName = album
                            selectedAlbumSongs = songs
                            currentRoute = LibraryRoute.Album
                        },
                        viewModel = viewModel
                    )
                }
            }
            LibraryRoute.Stats -> {
                StatsScreen(
                    onBack = { currentRoute = LibraryRoute.Main },
                    viewModel = viewModel,
                    contentPadding = contentPadding
                )
            }
        }
    }
}

enum class LibraryRoute {
    Main, Playlist, Album, Artist, Stats
}

enum class LibraryTab(val label: String) {
    All("All"),
    Playlists("Playlists"),
    Artists("Artists"),
    Albums("Albums")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryMainScreen(
    songs: List<Song>,
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onNavigateToPlaylist: (PlaylistDisplayItem) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String, List<Song>) -> Unit,
    onNavigateToStats: () -> Unit
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.All) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    // Filtering logic
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs else songs.filter {
            it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true)
        }
    }
    
    val filteredPlaylists = remember(userPlaylists, searchQuery) {
        if (searchQuery.isBlank()) userPlaylists else userPlaylists.filter {
            (it.name ?: "").contains(searchQuery, true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // --- Header Section ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Top Row: Title + Actions
            if (isSearchActive) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { },
                            expanded = false,
                            onExpandedChange = { if (!it) isSearchActive = false },
                            placeholder = { Text("Search library...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = { IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.Rounded.Close, null) } }
                        )
                    },
                    expanded = false,
                    onExpandedChange = { if (!it) isSearchActive = false },
                    modifier = Modifier.fillMaxWidth()
                ) {}
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Rounded.Search, "Search")
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onNavigateToStats,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.Insights, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Stats")
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Tabs Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(LibraryTab.values()) { tab ->
                    val selected = selectedTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.Transparent,
                            selected = selected,
                            enabled = true
                        )
                    )
                }
            }
        }

        // --- Main Content ---
        ExpressivePullToRefresh(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh() }
        ) {
            // Using AnimatedContent for tab switching
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    slideInVertically { it / 20 } togetherWith
                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                    slideOutVertically { -it / 20 }
                },
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    LibraryTab.All -> {
                        AllSongsList(
                            songs = filteredSongs,
                            likedSongs = likedSongs,
                            onSongClick = onSongClick,
                            onPlayQueue = onPlayQueue,
                            onLikedSongsClick = {
                                onNavigateToPlaylist(PlaylistDisplayItem("Liked Songs", "LM", "You", likedSongs.size, null))
                            },
                            contentPadding = contentPadding
                        )
                    }
                    LibraryTab.Playlists -> {
                        PlaylistsGrid(
                            playlists = filteredPlaylists,
                            likedSongs = likedSongs,
                            onPlaylistClick = onNavigateToPlaylist,
                            onLikedSongsClick = {
                                onNavigateToPlaylist(PlaylistDisplayItem("Liked Songs", "LM", "You", likedSongs.size, null))
                            },
                            contentPadding = contentPadding
                        )
                    }
                    LibraryTab.Artists -> {
                        ArtistsGrid(
                            songs = filteredSongs,
                            onArtistClick = onNavigateToArtist,
                            contentPadding = contentPadding
                        )
                    }
                    LibraryTab.Albums -> {
                        AlbumsGrid(
                            songs = filteredSongs,
                            onAlbumClick = onNavigateToAlbum,
                            contentPadding = contentPadding
                        )
                    }
                }
            }
        }
    }
}

// ============ SUB-SCREENS (Lists & Grids) ============

@Composable
fun AllSongsList(
    songs: List<Song>,
    likedSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onLikedSongsClick: () -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Liked Songs Banner
            if (likedSongs.isNotEmpty()) {
                ExpressiveLikedSongsCard(
                    count = likedSongs.size,
                    onClick = onLikedSongsClick
                )
                Spacer(Modifier.height(24.dp))
                Text("All Tracks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (songs.isEmpty()) {
            item { EmptyLibraryState("No songs found", "Try importing or downloading music") }
        } else {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { onPlayQueue(songs, song) }
                )
            }
        }
    }
}

@Composable
fun PlaylistsGrid(
    playlists: List<PlaylistDisplayItem>,
    likedSongs: List<Song>,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit,
    onLikedSongsClick: () -> Unit,
    contentPadding: PaddingValues
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Liked songs as a square card in grid
            ExpressivePlaylistCard(
                name = "Liked Songs",
                count = likedSongs.size,
                thumbnailUrl = null,
                isLiked = true,
                onClick = onLikedSongsClick
            )
        }
        items(playlists) { playlist ->
            ExpressivePlaylistCard(
                name = playlist.name ?: "Untitled",
                count = playlist.itemCount,
                thumbnailUrl = playlist.thumbnailUrl,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

@Composable
fun ArtistsGrid(
    songs: List<Song>,
    onArtistClick: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val artists = remember(songs) { songs.groupBy { it.artist }.keys.sorted() }
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp), // Slightly smaller for artists
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(artists) { artist ->
             val artistSongs = songs.filter { it.artist == artist }
             Column(
                 horizontalAlignment = Alignment.CenterHorizontally,
                 modifier = Modifier.clickable { onArtistClick(artist) }
             ) {
                 Surface(
                     shape = CircleShape,
                     modifier = Modifier.size(140.dp),
                     color = MaterialTheme.colorScheme.surfaceContainerHigh,
                     shadowElevation = 6.dp
                 ) {
                     val art = artistSongs.firstOrNull { it.albumArtUri != null }?.albumArtUri
                     if (art != null) {
                         AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop)
                     } else {
                         Box(contentAlignment = Alignment.Center) {
                             Icon(Icons.Rounded.Person, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                         }
                     }
                 }
                 Spacer(Modifier.height(12.dp))
                 Text(artist, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                 Text("${artistSongs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
             }
        }
    }
}

@Composable
fun AlbumsGrid(
    songs: List<Song>,
    onAlbumClick: (String, List<Song>) -> Unit,
    contentPadding: PaddingValues
) {
    val albums = remember(songs) { songs.groupBy { it.album }.keys.sorted() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums) { album ->
            if (album.isNotBlank() && album != "Unknown Album") {
                val albumSongs = songs.filter { it.album == album }
                ExpressivePlaylistCard(
                    name = album,
                    count = albumSongs.size,
                    thumbnailUrl = albumSongs.firstOrNull()?.albumArtUri.toString(),
                    subtitle = albumSongs.firstOrNull()?.artist,
                    onClick = { onAlbumClick(album, albumSongs) }
                )
            }
        }
    }
}

// ============ UI COMPONENTS ============

@Composable
fun ExpressiveLikedSongsCard(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        color = Color.Unspecified,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column {
                    Text("Liked Songs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("$count tracks • Auto-playlist", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun ExpressivePlaylistCard(
    name: String,
    count: Int,
    thumbnailUrl: String?,
    subtitle: String? = null,
    isLiked: Boolean = false,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.clickable { onClick() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(),
            color = if (isLiked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp
        ) {
            if (thumbnailUrl != null && thumbnailUrl != "null") {
                AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.PlaylistPlay,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle ?: "$count songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun SongListItem(song: Song, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(48.dp)
            ) {
                if (song.albumArtUri != null || song.thumbnailUrl != null)
                    AsyncImage(model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop)
                else
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun EmptyLibraryState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.AutoMirrored.Rounded.List, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.surfaceContainerHigh)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============ PLAYLIST DETAIL SCREEN ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistDisplayItem,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    viewModel: HomeViewModel,
    preloadedSongs: List<Song>? = null,
    isAlbum: Boolean = false
) {
    var songs by remember { mutableStateOf(preloadedSongs ?: emptyList()) }
    val isLoading by viewModel.isLoading.collectAsState()
    val isFetching = remember { mutableStateOf(songs.isEmpty()) }

    LaunchedEffect(playlist.id) {
        if (preloadedSongs == null) {
            isFetching.value = true
            songs = viewModel.fetchPlaylistSongs(playlist.id)
            isFetching.value = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", modifier = Modifier.padding(8.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Parallax-style Header Concept
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    // Background blur logic or gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Big Album Art
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.size(200.dp),
                            shadowElevation = 16.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                             if (playlist.thumbnailUrl != null && playlist.thumbnailUrl != "null") {
                                 AsyncImage(model = playlist.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop)
                             } else {
                                Box(contentAlignment = Alignment.Center) {
                                     Icon(if (isAlbum) Icons.Rounded.Album else Icons.AutoMirrored.Rounded.PlaylistPlay, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                             }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Text(
                            text = playlist.name ?: "Unknown",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        
                        Text(
                            text = if (isAlbum) "Album • ${playlist.uploaderName}" else "Playlist • ${songs.size} tracks",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // Action Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Play All
                    FilledTonalButton(
                        onClick = { if (songs.isNotEmpty()) onPlayQueue(songs, songs.first()) },
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play All")
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }

            if (isFetching.value) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(songs) { song ->
                    SongListItem(song = song, onClick = { onPlayQueue(songs, song) })
                }
            }
        }
    }
}
