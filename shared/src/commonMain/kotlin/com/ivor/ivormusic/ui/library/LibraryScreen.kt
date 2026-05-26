package com.ivor.ivormusic.ui.library

import com.ivor.ivormusic.platform.PlatformBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.ivor.ivormusic.domain.PlaylistDisplayItem
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.ui.artist.ArtistScreen
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.mutableFloatStateOf
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
    PlatformBackHandler(enabled = currentRoute != LibraryRoute.Main) {
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
    val localPlaylistIds by viewModel.localPlaylistIds.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.All) }

    // Use raw lists as filtering/sorting is removed from UI
    val filteredSongs = songs
    val filteredPlaylists = userPlaylists

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
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Top Row: Title + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                FilledTonalIconButton(
                    onClick = onNavigateToStats,
                    modifier = Modifier.size(56.dp),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.Insights, null, modifier = Modifier.size(24.dp))
                }
            }
            
            Spacer(Modifier.height(28.dp))
            
            // Expressive Horizontal Toolbar for Tabs
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                content = {
                    for (tab in LibraryTab.entries) {
                        val selected = selectedTab == tab
                        val contentColor = if (selected) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { selectedTab = tab },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            )
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
                            localPlaylistIds = localPlaylistIds,
                            likedSongs = likedSongs,
                            onPlaylistClick = onNavigateToPlaylist,
                            onEditPlaylist = { playlist, newName, newDescription ->
                                viewModel.updateLocalPlaylist(playlist.id, newName, newDescription)
                            },
                            onDeletePlaylist = { playlist ->
                                viewModel.deleteLocalPlaylist(playlist.id)
                            },
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                    onClick = { onPlayQueue(songs, song) }
                )
            }
        }
    }
}

@Composable
fun PlaylistsGrid(
    playlists: List<PlaylistDisplayItem>,
    localPlaylistIds: Set<String>,
    likedSongs: List<Song>,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit,
    onEditPlaylist: (playlist: PlaylistDisplayItem, newName: String, newDescription: String?) -> Unit,
    onDeletePlaylist: (playlist: PlaylistDisplayItem) -> Unit,
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
            val isLocalPlaylist = localPlaylistIds.contains(playlist.id)
            ExpressivePlaylistCard(
                name = playlist.name ?: "Untitled",
                count = playlist.itemCount,
                thumbnailUrl = playlist.thumbnailUrl,
                description = playlist.description,
                isEditable = isLocalPlaylist,
                onEditConfirmed = { name, description -> onEditPlaylist(playlist, name, description) },
                onDeleteConfirmed = { onDeletePlaylist(playlist) },
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
    description: String? = null,
    isLiked: Boolean = false,
    isEditable: Boolean = false,
    onEditConfirmed: (String, String?) -> Unit = { _, _ -> },
    onDeleteConfirmed: () -> Unit = {},
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle ?: "$count songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (isEditable) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                showEditDialog = true
                            },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditPlaylistDialog(
            initialName = name,
            initialDescription = description,
            onDismiss = { showEditDialog = false },
            onConfirm = { newName, newDescription ->
                onEditConfirmed(newName, newDescription)
                showEditDialog = false
            }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete playlist?") },
            text = { Text("This will permanently remove \"$name\" and its local metadata.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConfirmed()
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditPlaylistDialog(
    initialName: String,
    initialDescription: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim().ifBlank { null }) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp),
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = shape,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation
    ) {
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
            trailingContent = trailingContent,
            modifier = Modifier.clickable(onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun ReorderModeCard(
    trackCount: Int,
    onDone: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reorder Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Long-press any track and drag it anywhere. $trackCount tracks will keep this new order.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
                    )
                }
            }

            FilledTonalButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Rounded.Done, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Done Reordering", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistDisplayItem,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    viewModel: HomeViewModel,
    preloadedSongs: List<Song>? = null,
    isAlbum: Boolean = false
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val localPlaylistIds by viewModel.localPlaylistIds.collectAsState()
    val resolvedPlaylist = remember(playlist, userPlaylists) {
        userPlaylists.firstOrNull { it.id == playlist.id } ?: playlist
    }
    val isLocalPlaylist = remember(playlist.id, localPlaylistIds, isAlbum) {
        !isAlbum && localPlaylistIds.contains(playlist.id)
    }
    var songs by remember { mutableStateOf(preloadedSongs ?: emptyList()) }
    val isLoading by viewModel.isLoading.collectAsState()
    val isFetching = remember { mutableStateOf(songs.isEmpty()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }
    var draggingSongId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var reorderDirty by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemHeightPx = with(density) { 72.dp.toPx() }

    // Search State
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Scroll State for FAB and App Bar
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isCollapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    
    // Filtered Songs
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs 
        else songs.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.artist.contains(searchQuery, ignoreCase = true) 
        }
    }

    LaunchedEffect(resolvedPlaylist.id) {
        if (preloadedSongs == null) {
            isFetching.value = true
            songs = viewModel.fetchPlaylistSongs(resolvedPlaylist.id)
            isFetching.value = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    AnimatedVisibility(
                        visible = isCollapsed,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        Text(
                            resolvedPlaylist.name ?: "Unknown",
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (reorderDirty) {
                            viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
                            reorderDirty = false
                        }
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isLocalPlaylist) {
                        IconButton(
                            onClick = {
                                val enablingReorder = !isReorderMode
                                isReorderMode = enablingReorder
                                draggingSongId = null
                                dragOffsetY = 0f
                                if (!enablingReorder && reorderDirty) {
                                    viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
                                    reorderDirty = false
                                }
                                if (enablingReorder) {
                                    isSearchActive = false
                                    searchQuery = ""
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isReorderMode) Icons.Rounded.Done else Icons.Rounded.DragHandle,
                                contentDescription = if (isReorderMode) "Finish reordering" else "Reorder playlist"
                            )
                        }
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Rename playlist")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist")
                        }
                    }
                    IconButton(onClick = {
                        if (isReorderMode) {
                            isReorderMode = false
                            draggingSongId = null
                            dragOffsetY = 0f
                            if (reorderDirty) {
                                viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
                                reorderDirty = false
                            }
                        }
                        isSearchActive = !isSearchActive
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search in playlist"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isCollapsed) MaterialTheme.colorScheme.surface else Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (filteredSongs.isNotEmpty() && !isReorderMode) {
                ExtendedFloatingActionButton(
                    text = { Text("Play All") },
                    icon = { Icon(Icons.Rounded.PlayArrow, null) },
                    onClick = { onPlayQueue(filteredSongs, filteredSongs.first()) },
                    expanded = !isCollapsed,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = padding.calculateTopPadding(), bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Album Art
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .size(240.dp) // Larger
                                .padding(top = 16.dp),
                            shadowElevation = 12.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                             if (resolvedPlaylist.thumbnailUrl != null && resolvedPlaylist.thumbnailUrl != "null") {
                                 AsyncImage(model = resolvedPlaylist.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop)
                             } else {
                                Box(contentAlignment = Alignment.Center) {
                                     Icon(if (isAlbum) Icons.Rounded.Album else Icons.AutoMirrored.Rounded.PlaylistPlay, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                             }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        // Title & Subtitle
                        Text(
                            text = resolvedPlaylist.name ?: "Unknown",
                            style = MaterialTheme.typography.displaySmall, // Expressive Typography
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        
                        Text(
                            text = if (isAlbum) "Album • ${resolvedPlaylist.uploaderName} • ${songs.size} tracks" else "Playlist • ${songs.size} tracks",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp),
                            textAlign = TextAlign.Center
                        )

                        if (isLocalPlaylist && isReorderMode) {
                            ReorderModeCard(
                                trackCount = songs.size,
                                onDone = {
                                    isReorderMode = false
                                    draggingSongId = null
                                    dragOffsetY = 0f
                                    if (reorderDirty) {
                                        viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
                                        reorderDirty = false
                                    }
                                }
                            )
                        }

                        if (!resolvedPlaylist.description.isNullOrBlank()) {
                            Text(
                                text = resolvedPlaylist.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 12.dp)
                            )
                        }
                    }
                }
            }

            // Search Bar (Sticky)
            if (isSearchActive) {
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                        shadowElevation = 4.dp
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find in playlist...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, null) } }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ), // Single line
                            singleLine = true
                        )
                    }
                }
            }

            // Loading / List
            if (isFetching.value) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(
                            modifier = Modifier.width(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                if (filteredSongs.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No songs found matching '$searchQuery'", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> "playlist_${resolvedPlaylist.id}_${song.id}" }
                    ) { index, song ->
                        val reorderEnabled = isLocalPlaylist && isReorderMode && searchQuery.isBlank()
                        val isDragging = draggingSongId == song.id
                        val animatedContainerColor by animateColorAsState(
                            targetValue = when {
                                isDragging -> MaterialTheme.colorScheme.primaryContainer
                                reorderEnabled -> MaterialTheme.colorScheme.surfaceContainerLow
                                else -> Color.Transparent
                            },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "playlist_row_container"
                        )
                        val animatedTonalElevation by animateDpAsState(
                            targetValue = when {
                                isDragging -> 8.dp
                                reorderEnabled -> 2.dp
                                else -> 0.dp
                            },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "playlist_row_tonal"
                        )
                        val animatedShadowElevation by animateDpAsState(
                            targetValue = if (isDragging) 10.dp else 0.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "playlist_row_shadow"
                        )

                        SongListItem(
                            song = song,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = if (reorderEnabled) 4.dp else 0.dp)
                                .then(if (isDragging) Modifier else Modifier.animateItem())
                                .zIndex(if (isDragging) 2f else 0f)
                                .offset(y = if (isDragging) with(density) { dragOffsetY.toDp() } else 0.dp)
                                .then(
                                    if (reorderEnabled) {
                                        Modifier.pointerInput(song.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingSongId = song.id
                                                    dragOffsetY = 0f
                                                },
                                                onDragEnd = {
                                                    draggingSongId = null
                                                    dragOffsetY = 0f
                                                    if (reorderDirty) {
                                                        viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
                                                        reorderDirty = false
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggingSongId = null
                                                    dragOffsetY = 0f
                                                    if (reorderDirty) {
                                                        viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
                                                        reorderDirty = false
                                                    }
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (draggingSongId == null) return@detectDragGesturesAfterLongPress
                                                    dragOffsetY += dragAmount.y
                                                    var activeIndex = songs.indexOfFirst { it.id == draggingSongId }
                                                    while (dragOffsetY > itemHeightPx && activeIndex in 0 until songs.lastIndex) {
                                                        val fromIndex = activeIndex
                                                        val toIndex = fromIndex + 1
                                                        songs = songs.toMutableList().apply {
                                                            add(toIndex, removeAt(fromIndex))
                                                        }
                                                        reorderDirty = true
                                                        activeIndex = toIndex
                                                        dragOffsetY -= itemHeightPx
                                                    }
                                                    while (dragOffsetY < -itemHeightPx && activeIndex > 0) {
                                                        val fromIndex = activeIndex
                                                        val toIndex = fromIndex - 1
                                                        songs = songs.toMutableList().apply {
                                                            add(toIndex, removeAt(fromIndex))
                                                        }
                                                        reorderDirty = true
                                                        activeIndex = toIndex
                                                        dragOffsetY += itemHeightPx
                                                    }
                                                }
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            containerColor = animatedContainerColor,
                            tonalElevation = animatedTonalElevation,
                            shadowElevation = animatedShadowElevation,
                            shape = if (reorderEnabled) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp),
                            trailingContent = if (reorderEnabled) {
                                {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.clickable(enabled = !isDragging) {
                                                songs = songs.toMutableList().apply {
                                                    removeAll { it.id == song.id }
                                                }
                                                viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
                                                reorderDirty = false
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Remove from playlist",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (isDragging) {
                                                    MaterialTheme.colorScheme.tertiaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DragHandle,
                                                    contentDescription = "Drag to reorder",
                                                    tint = if (isDragging) {
                                                        MaterialTheme.colorScheme.onTertiaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                )
                                            }
                                            Text(
                                                text = if (isDragging) "Moving" else "Hold",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else null,
                            onClick = {
                                if (!reorderEnabled) {
                                    onPlayQueue(filteredSongs, song)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog && isLocalPlaylist) {
        EditPlaylistDialog(
            initialName = resolvedPlaylist.name,
            initialDescription = resolvedPlaylist.description,
            onDismiss = { showEditDialog = false },
            onConfirm = { newName, newDescription ->
                viewModel.updateLocalPlaylist(resolvedPlaylist.id, newName, newDescription)
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog && isLocalPlaylist) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete playlist?") },
            text = { Text("This will permanently remove \"${resolvedPlaylist.name}\" and its local metadata.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLocalPlaylist(resolvedPlaylist.id)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
