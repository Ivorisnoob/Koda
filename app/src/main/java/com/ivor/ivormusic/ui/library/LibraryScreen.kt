package com.ivor.ivormusic.ui.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
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
import androidx.compose.ui.graphics.graphicsLayer
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
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.artist.ArtistScreen
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.mutableFloatStateOf
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.launch

/**
 * The Main Library Navigation Hub.
 * Manages transitions between:
 * - Main Library View (Lists/Grid)
 * - Playlist/Album Details
 * - Artist Details
 * - Statistics
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit = {},
    onPlayQueue: (List<Song>, Song?) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    isLocalLibrary: Boolean = true,
    onDownloadsClick: () -> Unit = {},
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

    // Expressive motion physics for screen pushes/pops
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = currentRoute,
        label = "LibraryNavigation",
        transitionSpec = {
            if (targetState == LibraryRoute.Main) {
                slideInHorizontally(animationSpec = spatialSpec) { -it } + fadeIn(animationSpec = effectsSpec) togetherWith
                        slideOutHorizontally(animationSpec = spatialSpec) { it } + fadeOut(animationSpec = effectsSpec)
            } else {
                slideInHorizontally(animationSpec = spatialSpec) { it } + fadeIn(animationSpec = effectsSpec) togetherWith
                        slideOutHorizontally(animationSpec = spatialSpec) { -it / 3 } + fadeOut(animationSpec = effectsSpec)
            }
        }
    ) { route ->
        when (route) {
            LibraryRoute.Main -> {
                LibraryMainScreen(
                    songs = songs,
                    isLocalLibrary = isLocalLibrary,
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    onSongClick = onSongClick,
                    onPlayQueue = onPlayQueue,
                    onDownloadsClick = onDownloadsClick,
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
                        onOpenAlbum = { albumItem ->
                            selectedPlaylist = albumItem
                            currentRoute = LibraryRoute.Playlist
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

enum class LibrarySortOption(val label: String) {
    Title("Title"),
    Artist("Artist"),
    Album("Album")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryMainScreen(
    songs: List<Song>,
    isLocalLibrary: Boolean,
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onDownloadsClick: () -> Unit,
    onNavigateToPlaylist: (PlaylistDisplayItem) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String, List<Song>) -> Unit,
    onNavigateToStats: () -> Unit
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val localPlaylistIds by viewModel.localPlaylistIds.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.All) }
    var sortOption by rememberSaveable { mutableStateOf(LibrarySortOption.Title) }

    LaunchedEffect(Unit) {
        viewModel.refreshRecentlyPlayed()
    }

    // The library is what the user OWNS or SAVED — never the recommendation
    // feed. Local mode: device songs + downloads. YouTube mode: downloads +
    // liked songs (the `songs` param carries home recommendations there).
    val librarySongs = remember(songs, downloadedSongs, likedSongs, isLocalLibrary) {
        if (isLocalLibrary) {
            (songs + downloadedSongs).distinctBy { it.id }
        } else {
            (downloadedSongs + likedSongs).distinctBy { it.id }
        }
    }

    val sortedSongs = remember(librarySongs, sortOption) {
        when (sortOption) {
            LibrarySortOption.Title -> librarySongs.sortedBy { it.title.lowercase() }
            LibrarySortOption.Artist -> librarySongs.sortedBy { it.artist.lowercase() }
            LibrarySortOption.Album -> librarySongs.sortedBy { it.album.lowercase() }
        }
    }

    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    Icon(Icons.Rounded.Insights, contentDescription = "Listening stats", modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(28.dp))

            // M3 Expressive connected button group — replaces the segmented
            // button pattern for view switching (shape-morphs on select).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                LibraryTab.entries.forEachIndexed { index, tab ->
                    val selected = selectedTab == tab
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = { selectedTab = tab },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            LibraryTab.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // --- Main Content ---
        ExpressivePullToRefresh(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh() }
        ) {
            // Using AnimatedContent for tab switching (expressive motion physics)
            val tabSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
            val tabEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tabEffectsSpec) +
                    slideInVertically(animationSpec = tabSpatialSpec) { it / 20 } togetherWith
                    fadeOut(animationSpec = tabEffectsSpec) +
                    slideOutVertically(animationSpec = tabSpatialSpec) { -it / 20 }
                },
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    LibraryTab.All -> {
                        AllSongsList(
                            songs = sortedSongs,
                            likedSongs = likedSongs,
                            downloadedSongs = downloadedSongs,
                            recentlyPlayed = recentlyPlayed,
                            sortOption = sortOption,
                            onSortOptionChange = { sortOption = it },
                            onSongClick = onSongClick,
                            onPlayQueue = onPlayQueue,
                            onDownloadsClick = onDownloadsClick,
                            onLikedSongsClick = {
                                onNavigateToPlaylist(PlaylistDisplayItem("Liked Songs", "LM", "You", likedSongs.size, null))
                            },
                            contentPadding = contentPadding
                        )
                    }
                    LibraryTab.Playlists -> {
                        PlaylistsGrid(
                            playlists = userPlaylists,
                            localPlaylistIds = localPlaylistIds,
                            likedSongs = likedSongs,
                            onPlaylistClick = onNavigateToPlaylist,
                            onEditPlaylist = { playlist, newName, newDescription ->
                                if (localPlaylistIds.contains(playlist.id)) {
                                    viewModel.updateLocalPlaylist(playlist.id, newName, newDescription)
                                } else {
                                    viewModel.renameYouTubePlaylist(playlist.id, newName, newDescription)
                                }
                            },
                            onDeletePlaylist = { playlist ->
                                if (localPlaylistIds.contains(playlist.id)) {
                                    viewModel.deleteLocalPlaylist(playlist.id)
                                } else {
                                    viewModel.deleteYouTubePlaylist(playlist.id)
                                }
                            },
                            onLikedSongsClick = {
                                onNavigateToPlaylist(PlaylistDisplayItem("Liked Songs", "LM", "You", likedSongs.size, null))
                            },
                            contentPadding = contentPadding
                        )
                    }
                    LibraryTab.Artists -> {
                        ArtistsGrid(
                            songs = librarySongs,
                            onArtistClick = onNavigateToArtist,
                            contentPadding = contentPadding
                        )
                    }
                    LibraryTab.Albums -> {
                        AlbumsGrid(
                            songs = librarySongs,
                            onAlbumClick = onNavigateToAlbum,
                            contentPadding = contentPadding
                        )
                    }
                }
            }
        }
    }

    // --- M3E FAB menu: quick library actions ---
    FloatingActionButtonMenu(
        expanded = fabMenuExpanded,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 4.dp, bottom = 8.dp),
        button = {
            ToggleFloatingActionButton(
                checked = fabMenuExpanded,
                onCheckedChange = { fabMenuExpanded = it }
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = if (fabMenuExpanded) "Close menu" else "Library actions",
                    // + rotates into × as the menu blossoms open
                    modifier = Modifier.graphicsLayer { rotationZ = checkedProgress * 45f }
                )
            }
        }
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                fabMenuExpanded = false
                showCreatePlaylistDialog = true
            },
            icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
            text = { Text("New playlist") }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                fabMenuExpanded = false
                onDownloadsClick()
            },
            icon = { Icon(Icons.Rounded.DownloadDone, null) },
            text = { Text("Downloads") }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                fabMenuExpanded = false
                onNavigateToStats()
            },
            icon = { Icon(Icons.Rounded.Insights, null) },
            text = { Text("Statistics") }
        )
    }

    if (showCreatePlaylistDialog) {
        EditPlaylistDialog(
            title = "New playlist",
            initialName = "",
            initialDescription = null,
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name, description ->
                viewModel.createLocalPlaylist(name, description)
                showCreatePlaylistDialog = false
            }
        )
    }
    }
}

// ============ SUB-SCREENS (Lists & Grids) ============

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AllSongsList(
    songs: List<Song>,
    likedSongs: List<Song>,
    downloadedSongs: List<Song>,
    recentlyPlayed: List<Song>,
    sortOption: LibrarySortOption,
    onSortOptionChange: (LibrarySortOption) -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onDownloadsClick: () -> Unit,
    onLikedSongsClick: () -> Unit,
    contentPadding: PaddingValues
) {
    val likedIds = remember(likedSongs) { likedSongs.map { it.id }.toSet() }
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.map { it.id }.toSet() }

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
        // Liked Songs hero banner
        if (likedSongs.isNotEmpty()) {
            item(key = "liked_hero") {
                ExpressiveLikedSongsCard(
                    count = likedSongs.size,
                    onClick = onLikedSongsClick
                )
            }
        }

        // Downloads quick access
        if (downloadedSongs.isNotEmpty()) {
            item(key = "downloads_card") {
                DownloadsQuickCard(
                    count = downloadedSongs.size,
                    onClick = onDownloadsClick
                )
            }
        }

        // Recently played rail
        if (recentlyPlayed.isNotEmpty()) {
            item(key = "recent_header") {
                Text(
                    "Recently played",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            item(key = "recent_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(recentlyPlayed, key = { "recent_${it.id}" }) { song ->
                        RecentSongCard(
                            song = song,
                            onClick = { onPlayQueue(recentlyPlayed, song) }
                        )
                    }
                }
            }
        }

        // All tracks header: count + sort menu
        item(key = "tracks_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (songs.isEmpty()) "All tracks" else "All tracks • ${songs.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Box {
                    var showSortMenu by remember { mutableStateOf(false) }
                    FilledTonalIconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(40.dp),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.Rounded.SwapVert,
                            contentDescription = "Sort by ${sortOption.label}",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        LibrarySortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSortOptionChange(option)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (option == sortOption) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            item(key = "empty_state") {
                EmptyLibraryState(
                    icon = Icons.Rounded.MusicNote,
                    title = "Your library is empty",
                    subtitle = "Songs you download or like will show up here"
                )
            }
        } else {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                    isLiked = song.id in likedIds,
                    isDownloaded = song.id in downloadedIds,
                    showDuration = true,
                    onClick = { onPlayQueue(songs, song) }
                )
            }
        }
    }
}

@Composable
private fun DownloadsQuickCard(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.DownloadDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "$count songs available offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun RecentSongCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(112.dp)
        ) {
            val art = song.albumArtUri ?: song.highResThumbnailUrl ?: song.thumbnailUrl
            if (art != null) {
                AsyncImage(model = art, contentDescription = song.title, contentScale = ContentScale.Crop)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
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
            // YouTube playlists are editable through InnerTube; the synthesized
            // Supermix ("RTM") and Your Likes ("LM") entries are not real playlists
            val isYouTubeEditable = !isLocalPlaylist &&
                playlist.id != "RTM" && playlist.id != "LM"
            ExpressivePlaylistCard(
                name = playlist.name ?: "Untitled",
                count = playlist.itemCount,
                subtitle = if (playlist.itemCount < 0) playlist.uploaderName.ifBlank { "Playlist" } else null,
                thumbnailUrl = playlist.thumbnailUrl,
                description = playlist.description,
                isEditable = isLocalPlaylist || isYouTubeEditable,
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
    val artists = remember(songs) {
        songs.filter { it.artist.isNotBlank() && !it.artist.startsWith("Unknown", ignoreCase = true) }
            .groupBy { it.artist }
            .toList()
            .sortedBy { (name, _) -> name.lowercase() }
    }

    if (artists.isEmpty()) {
        EmptyLibraryState(
            icon = Icons.Rounded.Person,
            title = "No artists yet",
            subtitle = "Artists from your songs will show up here"
        )
        return
    }

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
        items(artists, key = { (name, _) -> name }) { (artist, artistSongs) ->
             Column(
                 horizontalAlignment = Alignment.CenterHorizontally,
                 modifier = Modifier
                     .clip(RoundedCornerShape(20.dp))
                     .clickable { onArtistClick(artist) }
             ) {
                 Surface(
                     shape = CircleShape,
                     modifier = Modifier.size(140.dp),
                     color = MaterialTheme.colorScheme.surfaceContainerHigh,
                     shadowElevation = 6.dp
                 ) {
                     val art = artistSongs.firstOrNull { it.albumArtUri != null }?.albumArtUri
                         ?: artistSongs.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
                     if (art != null) {
                         AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop)
                     } else {
                         Box(contentAlignment = Alignment.Center) {
                             Icon(Icons.Rounded.Person, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                         }
                     }
                 }
                 Spacer(Modifier.height(12.dp))
                 Text(artist, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                 Text(
                     if (artistSongs.size == 1) "1 song" else "${artistSongs.size} songs",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant
                 )
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
    // Filter BEFORE building grid items — filtering inside the item lambda
    // leaves blank holes in the grid for skipped albums.
    val albums = remember(songs) {
        songs.filter { it.album.isNotBlank() && !it.album.startsWith("Unknown", ignoreCase = true) }
            .groupBy { it.album }
            .toList()
            .sortedBy { (name, _) -> name.lowercase() }
    }

    if (albums.isEmpty()) {
        EmptyLibraryState(
            icon = Icons.Rounded.Album,
            title = "No albums yet",
            subtitle = "Albums from your songs will show up here"
        )
        return
    }

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
        items(albums, key = { (name, _) -> name }) { (album, albumSongs) ->
            val art = albumSongs.firstOrNull { it.albumArtUri != null }?.albumArtUri?.toString()
                ?: albumSongs.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
            ExpressivePlaylistCard(
                name = album,
                count = albumSongs.size,
                thumbnailUrl = art,
                subtitle = albumSongs.firstOrNull()?.artist,
                onClick = { onAlbumClick(album, albumSongs) }
            )
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
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp
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
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle ?: "$count songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (isEditable) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            text = { Text("This will permanently remove \"$name\".") },
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
    onConfirm: (name: String, description: String?) -> Unit,
    title: String = "Edit playlist"
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    showDuration: Boolean = false,
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
            trailingContent = trailingContent ?: if (isLiked || isDownloaded || showDuration) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isDownloaded) {
                            Icon(
                                Icons.Rounded.DownloadDone,
                                contentDescription = "Downloaded",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isLiked) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = "Liked",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (showDuration && song.duration > 0) {
                            Text(
                                formatSongDuration(song.duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else null,
            modifier = Modifier.clickable(onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

private fun formatSongDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
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
fun EmptyLibraryState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.AutoMirrored.Rounded.List
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
    // YouTube playlists can be edited through InnerTube. "LM" (Your Likes) only
    // supports song removal (= removing the like), not rename/delete, and the
    // synthesized Supermix ("RTM") and radio mixes are not editable at all.
    val isYouTubePlaylist = !isAlbum && !isLocalPlaylist
    val canEditYouTubeSongs = isYouTubePlaylist &&
        (playlist.id.startsWith("PL") || playlist.id == "LM")
    val canRenameDeleteYouTube = isYouTubePlaylist && playlist.id.startsWith("PL")
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
                    if (isLocalPlaylist || canEditYouTubeSongs) {
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
                                contentDescription = if (isReorderMode) "Finish editing" else "Edit songs"
                            )
                        }
                    }
                    if (isLocalPlaylist || canRenameDeleteYouTube) {
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
                // M3E split button: Play + menu (shuffle, radio)
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val radioSeed = filteredSongs.firstOrNull {
                    it.source == com.ivor.ivormusic.data.SongSource.YOUTUBE
                }
                com.ivor.ivormusic.ui.artist.PlaySplitButton(
                    onPlay = { onPlayQueue(filteredSongs, filteredSongs.first()) },
                    onShuffle = { onPlayQueue(filteredSongs.shuffled(), null) },
                    onStartRadio = if (radioSeed != null) {
                        {
                            scope.launch {
                                val radio = viewModel.getRadioSongs(radioSeed.id)
                                if (radio.isNotEmpty()) {
                                    onPlayQueue(listOf(radioSeed) + radio, radioSeed)
                                }
                            }
                        }
                    } else null
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
                        // Manage mode: song removal for local and YouTube playlists;
                        // drag reordering stays local-only (InnerTube move needs
                        // setVideoIds the parser does not keep).
                        val manageEnabled = isReorderMode && searchQuery.isBlank() &&
                            (isLocalPlaylist || canEditYouTubeSongs)
                        val reorderEnabled = manageEnabled && isLocalPlaylist
                        val isDragging = draggingSongId == song.id
                        val animatedContainerColor by animateColorAsState(
                            targetValue = when {
                                isDragging -> MaterialTheme.colorScheme.primaryContainer
                                manageEnabled -> MaterialTheme.colorScheme.surfaceContainerLow
                                else -> Color.Transparent
                            },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "playlist_row_container"
                        )
                        val animatedTonalElevation by animateDpAsState(
                            targetValue = when {
                                isDragging -> 8.dp
                                manageEnabled -> 2.dp
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
                                .padding(horizontal = 12.dp, vertical = if (manageEnabled) 4.dp else 0.dp)
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
                            shape = if (manageEnabled) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp),
                            trailingContent = if (manageEnabled) {
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
                                                if (isLocalPlaylist) {
                                                    viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
                                                    reorderDirty = false
                                                } else {
                                                    viewModel.removeSongFromYouTubePlaylist(resolvedPlaylist.id, song)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Remove from playlist",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            )
                                        }
                                        if (reorderEnabled) {
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
                                }
                            } else null,
                            onClick = {
                                if (!manageEnabled) {
                                    onPlayQueue(filteredSongs, song)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog && (isLocalPlaylist || canRenameDeleteYouTube)) {
        EditPlaylistDialog(
            initialName = resolvedPlaylist.name,
            initialDescription = resolvedPlaylist.description,
            onDismiss = { showEditDialog = false },
            onConfirm = { newName, newDescription ->
                if (isLocalPlaylist) {
                    viewModel.updateLocalPlaylist(resolvedPlaylist.id, newName, newDescription)
                } else {
                    viewModel.renameYouTubePlaylist(resolvedPlaylist.id, newName, newDescription)
                }
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog && (isLocalPlaylist || canRenameDeleteYouTube)) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete playlist?") },
            text = { Text("This will permanently remove \"${resolvedPlaylist.name}\".") },
            confirmButton = {
                TextButton(onClick = {
                    if (isLocalPlaylist) {
                        viewModel.deleteLocalPlaylist(resolvedPlaylist.id)
                    } else {
                        viewModel.deleteYouTubePlaylist(resolvedPlaylist.id)
                    }
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
