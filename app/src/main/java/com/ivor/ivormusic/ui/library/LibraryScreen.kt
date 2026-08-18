package com.ivor.ivormusic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.ivor.ivormusic.ui.components.PredictiveBackStack
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.artist.ArtistScreen
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.mutableFloatStateOf
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Feeds the app synthesizes rather than fetches, and the account's own
 * built-ins. None of them is a playlist that could be kept: "LM" (Your Likes)
 * and "LL" are assembled locally, "RTM" is the generated Supermix, and Watch
 * Later is the account's own list, already in the library by definition.
 */
private val NON_SAVABLE_PLAYLIST_IDS =
    setOf("LM", "VLLM", "LL", "VLLL", "RTM", "WL", "VLWL", READY_OFFLINE_ID)

/**
 * The synthetic playlist id behind "Ready offline".
 *
 * Deliberately not id-shaped: it never reaches YouTube, and the point of a
 * value nothing upstream could return is that a mistake routing it somewhere
 * that expects a real playlist id fails loudly instead of quietly fetching
 * someone else's list.
 */
const val READY_OFFLINE_ID = "READY_OFFLINE"

/**
 * Playlists with no page anywhere to link to.
 *
 * A share url is built by pasting the id into a music.youtube.com playlist
 * link, which for a feed assembled on this device produces a link that opens
 * nothing. "LM" is absent because Liked Music genuinely is a YouTube playlist
 * with that id; "RTM" and "Ready offline" are not.
 */
private val NON_SHAREABLE_PLAYLIST_IDS = setOf("RTM", READY_OFFLINE_ID)

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
    /** Open a musician's video-mode channel page, from the artist screen. */
    onOpenChannel: ((String) -> Unit)? = null,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    isLocalLibrary: Boolean = true,
    onDownloadsClick: () -> Unit = {},
    initialArtist: String? = null,
    onInitialArtistConsumed: () -> Unit = {},
    /**
     * Open straight onto a playlist, for callers outside the Library that have
     * one in hand - Spotlight's shortcut grid and shelves. Same hand-off shape
     * as [initialArtist]: the caller clears it through the consumed callback so
     * coming back to the tab later does not re-open the playlist.
     */
    initialPlaylist: PlaylistDisplayItem? = null,
    onInitialPlaylistConsumed: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    /**
     * Long-press on a song row. Hoisted rather than handled here because the
     * options sheet acts on the player, and HomeScreen is where the
     * PlayerViewModel lives; hosting one sheet up there also keeps a single
     * instance alive across the Library's own sub-routes.
     */
    onSongLongPress: ((Song) -> Unit)? = null,
    /**
     * Hoisted by HomeScreen for the main route's All tab, which is the one the
     * tab opens on. The other sub-tabs and the playlist/artist/album routes keep
     * their own states, since each is a different list and sharing one would
     * restore another list's index.
     */
    allSongsListState: LazyListState = rememberLazyListState()
) {
    // Navigation State
    var currentRoute by rememberSaveable { mutableStateOf(LibraryRoute.Main) }

    // Cached-in-full songs. Re-read whenever the tab comes back to its root,
    // because the cache changes as a side effect of listening: songs arrive by
    // being played and leave by being evicted, so a value collected once at
    // first composition would be stale by the time anyone looked at it again.
    val readyOffline by viewModel.readyOffline.collectAsState()
    LaunchedEffect(currentRoute) {
        if (currentRoute == LibraryRoute.Main) viewModel.refreshReadyOffline()
    }

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

    // Handle initial deep link to a playlist
    LaunchedEffect(initialPlaylist) {
        if (initialPlaylist != null) {
            selectedPlaylist = initialPlaylist
            currentRoute = LibraryRoute.Playlist
            onInitialPlaylistConsumed()
        }
    }

    // Expressive motion physics for screen pushes/pops
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    PredictiveBackStack(
        childOpen = currentRoute != LibraryRoute.Main,
        onBack = { currentRoute = LibraryRoute.Main },
        background = {
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
                },
                onNavigateToHistory = {
                    currentRoute = LibraryRoute.History
                },
                readyOfflineCount = readyOffline.songs.size,
                readyOfflineBytes = readyOffline.totalBytes,
                readyOfflineUnnamed = readyOffline.unnamedCount,
                readyOfflineHistoryDisabled = readyOffline.historyDisabled,
                onReadyOfflineClick = {
                    currentRoute = LibraryRoute.ReadyOffline
                },
                onSongLongPress = onSongLongPress,
                allSongsListState = allSongsListState
            )
        }
    ) { committedByGesture ->
    AnimatedContent(
        targetState = currentRoute,
        label = "LibraryNavigation",
        transitionSpec = {
            val content = when {
                // The finger already performed this exit.
                committedByGesture -> EnterTransition.None togetherWith ExitTransition.None
                targetState == LibraryRoute.Main ->
                    fadeIn(animationSpec = effectsSpec) togetherWith
                        (slideOutHorizontally(animationSpec = spatialSpec) { it } +
                            fadeOut(animationSpec = effectsSpec))
                else ->
                    (slideInHorizontally(animationSpec = spatialSpec) { it } +
                        fadeIn(animationSpec = effectsSpec)) togetherWith
                        (slideOutHorizontally(animationSpec = spatialSpec) { -it / 3 } +
                            fadeOut(animationSpec = effectsSpec))
            }
            // Main is empty on this layer, so the default SizeTransform would
            // animate the container between nothing and full screen and clip
            // the route to it on the way.
            content using SizeTransform(clip = false) { _, _ -> snap() }
        }
    ) { route ->
        when (route) {
            // Main lives underneath now; this layer is empty over it, and full
            // size so both states measure the same.
            LibraryRoute.Main -> Spacer(Modifier.fillMaxSize())
            LibraryRoute.Playlist -> {
                selectedPlaylist?.let { playlist ->
                    PlaylistDetailScreen(
                        playlist = playlist,
                        onBack = { currentRoute = LibraryRoute.Main },
                        onPlayQueue = onPlayQueue,
                        viewModel = viewModel,
                        isAlbum = false,
                        onSongLongPress = onSongLongPress
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
                        isAlbum = true,
                        onSongLongPress = onSongLongPress
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
                        viewModel = viewModel,
                        onSongLongPress = onSongLongPress,
                        onOpenChannel = onOpenChannel
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
            LibraryRoute.History -> {
                ListeningHistoryScreen(
                    onBack = { currentRoute = LibraryRoute.Main },
                    viewModel = viewModel,
                    onPlayQueue = onPlayQueue,
                    contentPadding = contentPadding
                )
            }
            LibraryRoute.ReadyOffline -> {
                // Same shape as the Album route: a synthetic playlist item over
                // preloaded songs, so play-all, shuffle, search and the
                // long-press options sheet all come for free. The sheet is what
                // makes the list actionable - Download on a row promotes a song
                // out of the evictable cache and into a permanent copy.
                val readyOfflineItem = PlaylistDisplayItem(
                    name = "Ready offline",
                    url = READY_OFFLINE_ID,
                    uploaderName = "On this device",
                    itemCount = readyOffline.songs.size,
                    thumbnailUrl = readyOffline.songs.firstOrNull()?.thumbnailUrl
                )
                PlaylistDetailScreen(
                    playlist = readyOfflineItem,
                    onBack = { currentRoute = LibraryRoute.Main },
                    onPlayQueue = onPlayQueue,
                    viewModel = viewModel,
                    preloadedSongs = readyOffline.songs,
                    isAlbum = false,
                    onSongLongPress = onSongLongPress
                )
            }
        }
    }
    }
}

enum class LibraryRoute {
    Main, Playlist, Album, Artist, Stats, History, ReadyOffline
}

enum class LibraryTab(val label: String) {
    All("All"),
    Playlists("Playlists"),
    Artists("Artists"),
    Albums("Albums")
}

/**
 * Sort orders for the All tab's track list. Labels state their own direction
 * ("Most played", not "Play count") so the list needs no asc/desc toggle.
 */
enum class LibrarySortOption(val label: String, val icon: ImageVector) {
    Title("Title", Icons.Rounded.SortByAlpha),
    Artist("Artist", Icons.Rounded.Person),
    Album("Album", Icons.Rounded.Album),
    MostPlayed("Most played", Icons.Rounded.TrendingUp),
    RecentlyAdded("Recently added", Icons.Rounded.NewReleases)
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
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit,
    /** Songs cached in full; the entry point hides itself when nothing is. */
    readyOfflineCount: Int = 0,
    readyOfflineBytes: Long = 0L,
    readyOfflineUnnamed: Int = 0,
    readyOfflineHistoryDisabled: Boolean = false,
    onReadyOfflineClick: () -> Unit = {},
    onSongLongPress: ((Song) -> Unit)? = null,
    allSongsListState: LazyListState = rememberLazyListState()
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val localPlaylistIds by viewModel.localPlaylistIds.collectAsState()
    val savedPlaylistIds by viewModel.savedPlaylistIds.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val playCounts by viewModel.playCounts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.All) }

    // Sort order sticks across launches, so it lives in prefs rather than in
    // rememberSaveable. Matched by name instead of valueOf so an option
    // removed in a later version falls back to Title instead of throwing.
    val context = LocalContext.current
    val themePreferences = remember(context) { ThemePreferences(context) }
    val storedSortName by themePreferences.librarySortOption.collectAsState()
    val sortOption = remember(storedSortName) {
        LibrarySortOption.entries.firstOrNull { it.name == storedSortName }
            ?: LibrarySortOption.Title
    }

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

    val sortedSongs = remember(librarySongs, sortOption, playCounts) {
        when (sortOption) {
            LibrarySortOption.Title -> librarySongs.sortedBy { it.title.lowercase() }
            LibrarySortOption.Artist -> librarySongs.sortedBy { it.artist.lowercase() }
            LibrarySortOption.Album -> librarySongs.sortedBy { it.album.lowercase() }
            // Never-played songs land at the bottom in a stable alphabetical
            // order rather than whatever order the library happened to build in
            LibrarySortOption.MostPlayed -> librarySongs.sortedWith(
                compareByDescending<Song> { playCounts[it.id] ?: 0 }
                    .thenBy { it.title.lowercase() }
            )
            // Songs with no known add date (see Song.dateAdded) sort last
            LibrarySortOption.RecentlyAdded -> librarySongs.sortedWith(
                compareByDescending<Song> { it.dateAdded ?: Long.MIN_VALUE }
                    .thenBy { it.title.lowercase() }
            )
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
                // displayLarge is 57sp and "Library" is most of the width at
                // that size, so this row has space for exactly one action.
                // Listening history is reached from the Recently played rail
                // and the FAB menu instead; a second button here truncated the
                // title, and dropping the title a step would split Home and
                // Library, the only two screens that share this scale.
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
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
            // Unlike the video screens this one never doubled up, because the
            // tab content has no loading state of its own. The problem was the
            // other way round: on first load the pull spinner span over a
            // completely blank tab. It now means only "refreshing what is
            // already here", and the empty first load gets skeletons below.
            isRefreshing = isLoading && librarySongs.isNotEmpty(),
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
                    LibraryTab.All -> if (isLoading && sortedSongs.isEmpty()) {
                        // First load over an empty library: placeholder rows
                        // rather than a bare pull spinner over nothing.
                        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                            TrackSkeletonList()
                        }
                    } else {
                        AllSongsList(
                            songs = sortedSongs,
                            likedSongs = likedSongs,
                            downloadedSongs = downloadedSongs,
                            recentlyPlayed = recentlyPlayed,
                            playCounts = playCounts,
                            sortOption = sortOption,
                            onSortOptionChange = { themePreferences.setLibrarySortOption(it.name) },
                            onSongClick = onSongClick,
                            onPlayQueue = onPlayQueue,
                            onDownloadsClick = onDownloadsClick,
                            onLikedSongsClick = {
                                onNavigateToPlaylist(PlaylistDisplayItem("Liked Songs", "LM", "You", likedSongs.size, null))
                            },
                            onNavigateToHistory = onNavigateToHistory,
                            readyOfflineCount = readyOfflineCount,
                            readyOfflineBytes = readyOfflineBytes,
                            readyOfflineUnnamed = readyOfflineUnnamed,
                            readyOfflineHistoryDisabled = readyOfflineHistoryDisabled,
                            onReadyOfflineClick = onReadyOfflineClick,
                            contentPadding = contentPadding,
                            onSongLongPress = onSongLongPress,
                            listState = allSongsListState
                        )
                    }
                    LibraryTab.Playlists -> {
                        PlaylistsGrid(
                            playlists = userPlaylists,
                            localPlaylistIds = localPlaylistIds,
                            savedPlaylistIds = savedPlaylistIds,
                            likedSongs = likedSongs,
                            onPlaylistClick = onNavigateToPlaylist,
                            onRemoveSavedPlaylist = { playlist ->
                                viewModel.removeSavedPlaylist(playlist.id)
                            },
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
    // Lifted above the floating nav pill and the mini player(s) via the
    // overlay inset HomeScreen provides, so the button is never covered.
    FloatingActionButtonMenu(
        expanded = fabMenuExpanded,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(
                end = 4.dp,
                bottom = com.ivor.ivormusic.ui.components.LocalBottomOverlayInset.current + 8.dp
            ),
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
                onNavigateToHistory()
            },
            icon = { Icon(Icons.Rounded.History, null) },
            text = { Text("Listening history") }
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
    playCounts: Map<String, Int>,
    sortOption: LibrarySortOption,
    onSortOptionChange: (LibrarySortOption) -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onDownloadsClick: () -> Unit,
    onLikedSongsClick: () -> Unit,
    onNavigateToHistory: () -> Unit,
    /** Songs cached in full; the card hides itself when nothing is cached. */
    readyOfflineCount: Int,
    readyOfflineBytes: Long,
    readyOfflineUnnamed: Int,
    readyOfflineHistoryDisabled: Boolean,
    onReadyOfflineClick: () -> Unit,
    contentPadding: PaddingValues,
    onSongLongPress: ((Song) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState()
) {
    val likedIds = remember(likedSongs) { likedSongs.map { it.id }.toSet() }
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.map { it.id }.toSet() }

    LazyColumn(
        state = listState,
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

        if (readyOfflineCount > 0 || (readyOfflineHistoryDisabled && readyOfflineUnnamed > 0)) {
            item(key = "ready_offline_card") {
                ReadyOfflineQuickCard(
                    count = readyOfflineCount,
                    totalBytes = readyOfflineBytes,
                    unnamedCount = readyOfflineUnnamed,
                    onClick = onReadyOfflineClick.takeIf { readyOfflineCount > 0 }
                )
            }
        }

        item(key = "history_card") {
            ListeningHistoryQuickCard(
                playCount = playCounts.values.sum(),
                onClick = onNavigateToHistory
            )
        }

        // Recently played rail
        if (recentlyPlayed.isNotEmpty()) {
            item(key = "recent_header") {
                // The rail is this list deduplicated down to one card a song,
                // so "See all" opening the full log is the honest thing behind
                // it - and it is where someone already looking at recents will
                // reach when the song they want is not one of the fifteen.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Recently played",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onNavigateToHistory) {
                        Text("See all")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (songs.isNotEmpty()) {
                        FilledIconButton(
                            onClick = {
                                val shuffled = songs.shuffled()
                                onPlayQueue(shuffled, shuffled.first())
                            },
                            modifier = Modifier.size(40.dp),
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle all tracks",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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
                                val selected = option == sortOption
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSortOptionChange(option)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            option.icon,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (selected) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
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
                // Under a non-alphabetical sort the order looks arbitrary
                // unless the value it sorted on is visible, so surface it in
                // place of the duration.
                val sortLabel = when (sortOption) {
                    LibrarySortOption.MostPlayed -> playCounts[song.id]
                        ?.takeIf { it > 0 }
                        ?.let { if (it == 1) "1 play" else "$it plays" }
                    LibrarySortOption.RecentlyAdded -> song.dateAdded?.let(::formatRelativeDate)
                    else -> null
                }
                SongListItem(
                    song = song,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                    isLiked = song.id in likedIds,
                    isDownloaded = song.id in downloadedIds,
                    showDuration = sortLabel == null,
                    trailingLabel = sortLabel,
                    onLongClick = onSongLongPress?.let { press -> { press(song) } },
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

/**
 * The way into "Ready offline".
 *
 * Sits below Downloads and reads as its temporary counterpart on purpose: the
 * two are both "plays without a network", and the only thing separating them is
 * that one was chosen and kept and the other happened by listening and can be
 * evicted. Hence the subtitle carrying the size rather than a promise - it is
 * the honest thing to say about a list the app maintains on the user's behalf,
 * and it doubles as the answer to "what is my cache actually holding".
 *
 * Hidden when nothing is cached. It stays when songs *are* cached but cannot be
 * named - listening history off, so there is nothing to resolve the ids against
 * - and then it explains that instead of navigating, because the list behind it
 * would be an empty screen with no way to say why.
 */
@Composable
private fun ReadyOfflineQuickCard(
    count: Int,
    totalBytes: Long,
    unnamedCount: Int,
    onClick: (() -> Unit)?
) {
    val subtitle = when {
        count > 0 -> buildString {
            append(if (count == 1) "1 song" else "$count songs")
            if (totalBytes > 0) append(" · ${formatCacheSize(totalBytes)}")
        }
        // Deliberately names the setting rather than saying "unavailable": the
        // fix is one toggle and the user is the only one who can make it.
        unnamedCount == 1 -> "1 cached song. Turn on listening history to list it"
        else -> "$unnamedCount cached songs. Turn on listening history to list them"
    }

    Surface(
        // Not clickable in the explanatory state - there is nothing behind it,
        // and a card that opens an empty screen is worse than one that does not
        // move.
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                color = if (onClick != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.OfflineBolt,
                        contentDescription = null,
                        tint = if (onClick != null) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ready offline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onClick != null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Bytes as MB or GB, matching how the cache size reads in Settings. */
private fun formatCacheSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024) else "${mb.toInt()} MB"
}

/**
 * The way into the listening history.
 *
 * A card rather than a header button: the Library title is `displayLarge` and
 * leaves room for exactly one action, which Statistics already holds. Shown
 * unconditionally, unlike the Downloads and Liked cards above it, because it is
 * the only permanent entry point and a screen nobody can find is not shipped -
 * the history screen has a proper empty state for the case where there is
 * nothing behind this yet.
 */
@Composable
private fun ListeningHistoryQuickCard(playCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
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
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Listening history",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    // The live value, the same as every settings hub row: a
                    // card that says nothing about what is inside is a worse
                    // version of the link it replaced.
                    when {
                        playCount <= 0 -> "Everything you play, in order"
                        playCount == 1 -> "1 play so far"
                        else -> "$playCount plays so far"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
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
    contentPadding: PaddingValues,
    savedPlaylistIds: Set<String> = emptySet(),
    onRemoveSavedPlaylist: (PlaylistDisplayItem) -> Unit = {}
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Liked songs as a full-width hero banner, same card the Songs tab
        // uses, instead of an odd square tile among the playlists
        item(key = "liked_hero", span = { GridItemSpan(maxLineSpan) }) {
            ExpressiveLikedSongsCard(
                count = likedSongs.size,
                onClick = onLikedSongsClick
            )
        }

        if (playlists.isNotEmpty()) {
            item(key = "playlists_header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Your playlists • ${playlists.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            item(key = "playlists_empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyLibraryState(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    title = "No playlists yet",
                    subtitle = "Create one with the + button below"
                )
            }
        }

        items(playlists) { playlist ->
            val isLocalPlaylist = localPlaylistIds.contains(playlist.id)
            // Kept, not owned: renaming or deleting one would be a write
            // against somebody else's playlist, so it gets "Remove from
            // library" in place of Edit/Delete.
            val isSavedPlaylist = savedPlaylistIds.contains(playlist.id)
            // YouTube playlists are editable through InnerTube; the synthesized
            // Supermix ("RTM") and Your Likes ("LM") entries are not real playlists
            val isYouTubeEditable = !isLocalPlaylist && !isSavedPlaylist &&
                playlist.id != "RTM" && playlist.id != "LM"
            ExpressivePlaylistCard(
                name = playlist.name ?: "Untitled",
                count = playlist.itemCount,
                // A saved playlist's author is the thing that tells it apart
                // from the user's own at a glance, so it wins the subtitle.
                subtitle = when {
                    isSavedPlaylist -> playlist.uploaderName.ifBlank { "Playlist" }
                    playlist.itemCount < 0 -> playlist.uploaderName.ifBlank { "Playlist" }
                    else -> null
                },
                thumbnailUrl = playlist.thumbnailUrl,
                description = playlist.description,
                isEditable = isLocalPlaylist || isYouTubeEditable,
                isSaved = isSavedPlaylist,
                onEditConfirmed = { name, description -> onEditPlaylist(playlist, name, description) },
                onDeleteConfirmed = { onDeletePlaylist(playlist) },
                onRemoveSaved = { onRemoveSavedPlaylist(playlist) },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLikedSongsCard(count: Int, onClick: () -> Unit) {
    // Flat expressive hero: solid container, heart seated in a SoftBurst
    // material shape, press-scale spring instead of a shadow
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "likedCardScale"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Liked Songs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    if (count == 1) "1 track • Auto-playlist" else "$count tracks • Auto-playlist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePlaylistCard(
    name: String,
    count: Int,
    thumbnailUrl: String?,
    subtitle: String? = null,
    description: String? = null,
    isLiked: Boolean = false,
    isEditable: Boolean = false,
    isSaved: Boolean = false,
    onEditConfirmed: (String, String?) -> Unit = { _, _ -> },
    onDeleteConfirmed: () -> Unit = {},
    onRemoveSaved: () -> Unit = {},
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Flat expressive tile: no shadow — depth comes from tonal layering and
    // a press-scale spring, matching the settings rows and the liked hero
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playlistCardScale"
    )

    // No clip on the column: the artwork Surface rounds itself, and clipping
    // here used to shave the corners off the title/subtitle text below it
    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxSize(),
                color = if (isLiked) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                if (thumbnailUrl != null && thumbnailUrl != "null") {
                    AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop)
                } else {
                    // Placeholder art: icon seated in a morphing material shape
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isLiked) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            // Saved marker, opposite corner to the track count. A saved
            // playlist otherwise looks exactly like one the user made, and the
            // actions behind it are not the same.
            if (isSaved) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Bookmark,
                        contentDescription = "Saved to library",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp)
                    )
                }
            }
            // Track-count chip floating on the artwork
            if (count > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle ?: if (count == 1) "1 song" else "$count songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (isEditable || isSaved) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (isSaved) {
                            // Removing the reference, not the playlist: no
                            // confirmation dialog, because nothing is destroyed
                            // and saving it again is one tap on its page.
                            DropdownMenuItem(
                                text = { Text("Remove from library") },
                                onClick = {
                                    showMenu = false
                                    onRemoveSaved()
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.BookmarkRemove, contentDescription = null)
                                }
                            )
                        } else {
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
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
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
    /** Short metric shown where the duration normally sits (e.g. "12 plays"). */
    trailingLabel: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    /** Opens the song options sheet. Null leaves the row tap-only. */
    onLongClick: (() -> Unit)? = null,
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
            trailingContent = trailingContent ?: if (isLiked || isDownloaded || showDuration || trailingLabel != null) {
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
                        if (trailingLabel != null) {
                            Text(
                                trailingLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (showDuration && song.duration > 0) {
                            Text(
                                formatSongDuration(song.duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else null,
            modifier = Modifier.songRowClick(onClick = onClick, onLongClick = onLongClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

/**
 * Tap to play, hold for the options sheet.
 *
 * Falls back to a plain `clickable` when there is no long-press action, because
 * `combinedClickable` with a null `onLongClick` still consumes the long press
 * and swallows it from anything underneath.
 */
@Composable
internal fun Modifier.songRowClick(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
): Modifier {
    if (onLongClick == null) return this.clickable(onClick = onClick)
    val haptics = LocalHapticFeedback.current
    return this.combinedClickable(
        onClick = onClick,
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        }
    )
}

/** Compact "when was this added" label for the Recently added sort. */
private fun formatRelativeDate(timestamp: Long): String {
    val days = (System.currentTimeMillis() - timestamp) / 86_400_000L
    return when {
        days < 0L -> "Today" // Clock skew or a file dated in the future
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7L -> "${days}d ago"
        days < 30L -> "${days / 7}w ago"
        days < 365L -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
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

/** Formats a summed track duration as "1 hr 32 min" / "45 min"; null when unknown. */
private fun formatTotalDuration(totalMs: Long): String? {
    val totalMinutes = totalMs / 60_000
    if (totalMinutes <= 0) return null
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
}

/**
 * Vibrant floating toolbar shown instead of the play split button while the
 * playlist is in edit mode. Vibrant color is the M3 Expressive cue for a
 * temporary change in page behavior, and it keeps Done reachable mid-list.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ManageModeToolbar(
    hint: String,
    icon: ImageVector,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onDone) {
                Icon(Icons.Rounded.Done, contentDescription = "Finish editing")
            }
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp)
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun EmptyLibraryState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.AutoMirrored.Rounded.List,
    /** Optional recovery action, e.g. a Retry button on a failed fetch. */
    action: (@Composable () -> Unit)? = null
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
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

// ============ PLAYLIST DETAIL SCREEN ============

/** Row height used only to size the loading skeleton; real rows measure themselves. */
private val TrackRowHeight = 68.dp

/**
 * Section header above the track list. Carries the count and, for editable
 * playlists, the Edit/Done toggle - the mode switch sits with the content it
 * changes rather than in the app bar.
 */
@Composable
private fun TrackSectionHeader(
    countLabel: String,
    manageEnabled: Boolean,
    canEdit: Boolean,
    onToggleEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tracks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (canEdit) {
            TextButton(
                onClick = onToggleEdit,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (manageEnabled) Icons.Rounded.Done else Icons.Rounded.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (manageEnabled) "Done" else "Edit")
            }
        }
    }
}

/**
 * Placeholder rows shown while the track list is being fetched. Skeletons are
 * the house pattern for content whose shape is known in advance - they say how
 * much is coming, which a bare spinner cannot.
 */
@Composable
internal fun TrackSkeletonList(rows: Int = 6) {
    val transition = rememberInfiniteTransition(label = "trackSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.70f,
        // A pulse is a timed effect, not spatial motion, so a tween is correct here.
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "trackSkeletonAlpha"
    )
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Column {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrackRowHeight)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(placeholder)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(placeholder)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.36f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(placeholder)
                    )
                }
            }
        }
    }
}

/**
 * One track row on the playlist page.
 *
 * Entering edit mode only swaps the trailing controls - rows keep their place
 * and their geometry. Only the row actually being dragged lifts into a card
 * (tonal fill, rounded corners, shadow, slight scale). That is the standard
 * reorder grammar, and it keeps mode entry to a content swap on the handful of
 * visible rows instead of a list-wide layout animation.
 *
 * The drag handle is a real handle: dragging it starts a reorder immediately.
 * A long press anywhere on the row is the accelerator for the same gesture.
 * Because the handle works on touch, no row needs a "hold" caption explaining
 * itself, and the destructive Remove stays a calm outlined glyph instead of a
 * filled block competing with the track for attention.
 *
 * Note on specs: the Dp animations below deliberately use a non-bouncy spring.
 * Corner radius and shadow elevation are clamped non-negative by the framework,
 * and an Expressive *spatial* spec is bouncy - it undershoots past zero on the
 * way back down and throws. Bouncy specs belong on scale, which can overshoot
 * safely.
 */
@Composable
private fun PlaylistTrackRow(
    song: Song,
    manageEnabled: Boolean,
    reorderEnabled: Boolean,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val liftSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val containerColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "trackContainer"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isDragging) 20.dp else 0.dp,
        animationSpec = liftSpec,
        label = "trackCorner"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 0.dp,
        animationSpec = liftSpec,
        label = "trackShadow"
    )
    val liftScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "trackLift"
    )

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = liftScale
            scaleY = liftScale
        },
        color = containerColor,
        shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp)),
        shadowElevation = shadowElevation.coerceAtLeast(0.dp)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (song.albumArtUri != null || song.thumbnailUrl != null) {
                        AsyncImage(
                            model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            // A plain swap, not an AnimatedContent: one transition object per
            // row is what made entering edit mode stutter, and the controls
            // appearing is a content change rather than a moving surface.
            trailingContent = {
                if (manageEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onRemove, enabled = !isDragging) {
                            Icon(
                                imageVector = Icons.Rounded.RemoveCircleOutline,
                                contentDescription = "Remove ${song.title}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        if (reorderEnabled) {
                            Box(
                                modifier = dragHandleModifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragIndicator,
                                    contentDescription = "Reorder ${song.title}",
                                    tint = if (isDragging) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                } else if (song.duration > 0) {
                    Text(
                        formatSongDuration(song.duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            // Manage mode owns the gesture: rows are being dragged and removed
            // there, so a long press must not open a sheet on top of it.
            modifier = if (manageEnabled) {
                Modifier
            } else {
                Modifier.songRowClick(onClick = onClick, onLongClick = onLongClick)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

/**
 * Playlist / album detail page.
 *
 * Layout follows the house grammar: one hero (the artwork), one primary action
 * (the play split button), page actions in the app bar overflow, and the edit
 * mode toggle in the track section header next to what it changes. Destructive
 * actions (delete playlist, remove track) run calm - no bounce, plain glyphs,
 * and an undo where the change is locally reversible.
 *
 * Reordering measures the real laid-out row geometry from the lazy list rather
 * than assuming a fixed row height, swaps at most once per frame from the
 * frame loop (so a burst of pointer events cannot double-swap against stale
 * layout), and auto-scrolls when the dragged row nears a viewport edge.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistDisplayItem,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    viewModel: HomeViewModel,
    preloadedSongs: List<Song>? = null,
    isAlbum: Boolean = false,
    onSongLongPress: ((Song) -> Unit)? = null
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val localPlaylistIds by viewModel.localPlaylistIds.collectAsState()
    val savedPlaylistIds by viewModel.savedPlaylistIds.collectAsState()
    val resolvedPlaylist = remember(playlist, userPlaylists) {
        userPlaylists.firstOrNull { it.id == playlist.id } ?: playlist
    }
    val isLocalPlaylist = remember(playlist.id, localPlaylistIds, isAlbum) {
        !isAlbum && localPlaylistIds.contains(playlist.id)
    }
    val isSavedPlaylist = savedPlaylistIds.contains(playlist.id)
    /**
     * Whether this page can be kept.
     *
     * Anything already in the library that is not a saved reference is the
     * user's own - local or on their account - and saving it would be a second
     * copy of something they already have. The synthesized feeds have no
     * playlist behind them to keep at all.
     */
    val canSavePlaylist = remember(playlist.id, userPlaylists, isLocalPlaylist, isSavedPlaylist) {
        !isLocalPlaylist &&
            playlist.id !in NON_SAVABLE_PLAYLIST_IDS &&
            (isSavedPlaylist || userPlaylists.none { it.id == playlist.id })
    }
    // YouTube playlists can be edited through InnerTube. "LM" (Your Likes) only
    // supports song removal (= removing the like), not rename/delete, and the
    // synthesized Supermix ("RTM") and radio mixes are not editable at all.
    val isYouTubePlaylist = !isAlbum && !isLocalPlaylist
    // Whether the library actually holds this playlist. A "PL" prefix says an
    // id is a real YouTube playlist, not that it is yours: every editing
    // affordance below is a write, and this page is reached from search and
    // from a shared link as well as from the Library, so without this a
    // playlist somebody else owns offered rename, delete and track editing -
    // all of which fail at the endpoint, after the UI has already moved. Being
    // absent from the library is only ever grounds for offering less, so the
    // moment before the list loads costs a hidden button, not a failed write.
    val isInLibrary = userPlaylists.any { it.id == playlist.id }
    val canEditYouTubeSongs = isYouTubePlaylist && !isSavedPlaylist &&
        (playlist.id == "LM" || (isInLibrary && playlist.id.startsWith("PL")))
    // A saved playlist starts with "PL" like any other, but it belongs to
    // whoever made it: rename and delete would be writes the account has no
    // rights to, and offering them is the UI promising something it cannot do.
    val canRenameDeleteYouTube = isYouTubePlaylist && isInLibrary &&
        playlist.id.startsWith("PL") && !isSavedPlaylist
    // Real "PL" playlists can be reordered remotely via edit_playlist moves;
    // "LM" (Your Likes) only supports removal.
    val canReorderRemote = isYouTubePlaylist && isInLibrary && playlist.id.startsWith("PL")
    val canEditSongs = isLocalPlaylist || canEditYouTubeSongs

    var songs by remember { mutableStateOf(preloadedSongs ?: emptyList()) }
    val isFetching = remember { mutableStateOf(songs.isEmpty()) }
    var loadAttempted by remember { mutableStateOf(preloadedSongs != null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }
    var descriptionExpanded by remember { mutableStateOf(false) }
    var reorderDirty by remember { mutableStateOf(false) }
    // videoId -> setVideoId (the per-row playlist item id InnerTube moves
    // address rows by), fetched lazily the first time edit mode opens
    var setVideoIds by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val shareContext = LocalContext.current

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    // Cover art. Only a playlist the user made has artwork of its own to
    // replace - a YouTube playlist's cover belongs to whoever published it.
    val hasCustomCover = resolvedPlaylist.thumbnailUrl?.contains("/custom_") == true
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // Null is the user backing out of the picker, which is not a failure
        // and must not clear the cover they already have.
        if (uri != null) viewModel.setLocalPlaylistCover(resolvedPlaylist.id, uri)
    }
    val pickCover: () -> Unit = {
        coverPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Drag state. The dragged row is addressed by its lazy list key so the
    // reorder maths can read real offsets and sizes out of the layout.
    val trackKeyPrefix = "playlist_${resolvedPlaylist.id}_"
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    // How far the hero header has scrolled away, 0..1. Drives the app bar fade
    // and the artwork parallax continuously - a boolean threshold makes the bar
    // pop, which is exactly the snap the motion rules forbid.
    //
    // These stay as State objects and are only ever read inside graphicsLayer /
    // drawBehind lambdas. Reading them in the composable body would recompose
    // the whole page (LazyColumn content included) on every scroll frame; read
    // from a deferred lambda, a scroll only re-runs the layer or the draw.
    val headerCollapse = remember {
        derivedStateOf {
            val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            when {
                header == null -> 1f
                header.size <= 0 -> 0f
                else -> (-header.offset.toFloat() / header.size).coerceIn(0f, 1f)
            }
        }
    }
    val barAlpha = remember {
        derivedStateOf {
            if (isSearchActive) 1f else (headerCollapse.value / 0.55f).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(resolvedPlaylist.id) {
        if (preloadedSongs == null) {
            isFetching.value = true
            songs = viewModel.fetchPlaylistSongs(resolvedPlaylist.id)
            isFetching.value = false
            loadAttempted = true
        }
    }

    LaunchedEffect(isReorderMode) {
        if (isReorderMode && canReorderRemote && setVideoIds.isEmpty()) {
            setVideoIds = viewModel.fetchYouTubePlaylistSetVideoIds(resolvedPlaylist.id)
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    val commitLocalOrder: () -> Unit = {
        if (reorderDirty) {
            viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
            reorderDirty = false
        }
    }

    val exitReorderMode: () -> Unit = {
        isReorderMode = false
        draggingKey = null
        dragOffsetY = 0f
        commitLocalOrder()
    }

    // Back leaves the transient modes before it leaves the page.
    BackHandler(enabled = isSearchActive || isReorderMode) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            exitReorderMode()
        }
    }

    // Shared end-of-drag handler: local playlists persist the whole new order,
    // YouTube playlists send one edit_playlist move for the dragged row (and
    // resync from the server if the row is not addressable or the move fails).
    val finishReorderDrag: () -> Unit = finish@{
        val movedKey = draggingKey
        val startIndex = dragStartIndex
        draggingKey = null
        dragOffsetY = 0f
        dragStartIndex = -1
        if (movedKey != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (isLocalPlaylist) {
            commitLocalOrder()
            return@finish
        }
        if (movedKey == null || startIndex < 0) return@finish
        val movedId = movedKey.removePrefix(trackKeyPrefix)
        val newIndex = songs.indexOfFirst { it.id == movedId }
        if (newIndex < 0 || newIndex == startIndex) return@finish
        val movedSetId = setVideoIds[movedId]
        val successor = songs.getOrNull(newIndex + 1)
        val successorSetId = successor?.id?.let { setVideoIds[it] }
        if (movedSetId == null || (successor != null && successorSetId == null)) {
            // Row beyond the first browse page - no setVideoId to move it by,
            // so restore the server's order instead of guessing.
            scope.launch { songs = viewModel.fetchPlaylistSongs(resolvedPlaylist.id) }
        } else {
            scope.launch {
                val ok = viewModel.moveSongInYouTubePlaylist(
                    resolvedPlaylist.id, movedSetId, successorSetId
                )
                if (!ok) songs = viewModel.fetchPlaylistSongs(resolvedPlaylist.id)
            }
        }
    }

    val beginDrag: (Song) -> Unit = { song ->
        draggingKey = trackKeyPrefix + song.id
        dragOffsetY = 0f
        dragStartIndex = songs.indexOfFirst { it.id == song.id }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Swaps the dragged row with whichever row its centre currently covers.
    // Offsets come from the live layout, so rows of any height (edit mode adds
    // padding) stay glued to the finger. Called once per frame from the drag
    // loop below, never straight from a pointer event, because layoutInfo only
    // refreshes after relayout and two swaps against one snapshot would jump.
    val settleSwap: () -> Unit = settle@{
        val key = draggingKey ?: return@settle
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@settle
        val centre = current.offset + current.size / 2f + dragOffsetY
        val target = info.visibleItemsInfo.firstOrNull { candidate ->
            val candidateKey = candidate.key
            candidateKey is String &&
                candidateKey != key &&
                candidateKey.startsWith(trackKeyPrefix) &&
                centre >= candidate.offset &&
                centre <= candidate.offset + candidate.size
        } ?: return@settle
        val from = songs.indexOfFirst { trackKeyPrefix + it.id == key }
        val to = songs.indexOfFirst { trackKeyPrefix + it.id == target.key }
        if (from < 0 || to < 0 || from == to) return@settle
        songs = songs.toMutableList().apply { add(to, removeAt(from)) }
        // The row's settled slot just moved to the target's; cancel that out of
        // the drag offset so the row does not visibly jump under the finger.
        dragOffsetY -= (target.offset - current.offset).toFloat()
        if (isLocalPlaylist) reorderDirty = true
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Per-frame drag loop: auto-scroll near the viewport edges, then resolve a
    // swap. Running the swap here (not in onDrag) also means a finger parked at
    // the edge keeps reordering while the list scrolls under it.
    LaunchedEffect(draggingKey) {
        val key = draggingKey ?: return@LaunchedEffect
        val edge = with(density) { 96.dp.toPx() }
        val maxStep = with(density) { 14.dp.toPx() }
        while (isActive) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val current = info.visibleItemsInfo.firstOrNull { it.key == key }
            if (current != null) {
                val top = current.offset + dragOffsetY
                val bottom = top + current.size
                val delta = when {
                    bottom > info.viewportEndOffset - edge ->
                        (bottom - (info.viewportEndOffset - edge)).coerceAtMost(maxStep)
                    top < info.viewportStartOffset + edge ->
                        (top - (info.viewportStartOffset + edge)).coerceAtLeast(-maxStep)
                    else -> 0f
                }
                if (delta != 0f) listState.scrollBy(delta)
            }
            settleSwap()
        }
    }

    val removeSong: (Song) -> Unit = { song ->
        val previous = songs
        songs = songs.filterNot { it.id == song.id }
        if (isLocalPlaylist) {
            viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, songs)
            reorderDirty = false
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Removed ${song.title}",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    songs = previous
                    viewModel.replaceLocalPlaylistSongs(resolvedPlaylist.id, previous)
                }
            }
        } else {
            // Remote removal is not locally reversible (a re-add would land at
            // the end of the playlist), so confirm it rather than offer undo.
            viewModel.removeSongFromYouTubePlaylist(resolvedPlaylist.id, song)
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Removed ${song.title}",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    Scaffold(
        topBar = {
            val barColor = MaterialTheme.colorScheme.surface
            Box(modifier = Modifier.drawBehind { drawRect(barColor, alpha = barAlpha.value) }) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                resolvedPlaylist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.graphicsLayer {
                                    alpha = barAlpha.value
                                    translationY = (1f - barAlpha.value) * 20.dp.toPx()
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                commitLocalOrder()
                                onBack()
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                if (isReorderMode) exitReorderMode()
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = if (isSearchActive) "Close search" else "Search in playlist"
                                )
                            }
                            // Page-level actions live behind one overflow so the
                            // bar keeps to the 1-2 visible actions M3E asks for,
                            // and Delete is not a peer of Rename on the page.
                            val hasRename = isLocalPlaylist || canRenameDeleteYouTube
                            val hasShare = !isLocalPlaylist &&
                                resolvedPlaylist.id !in NON_SHAREABLE_PLAYLIST_IDS
                            if (hasRename || hasShare) {
                                Box {
                                    IconButton(onClick = { showOverflow = true }) {
                                        Icon(Icons.Rounded.MoreVert, "More options")
                                    }
                                    DropdownMenu(
                                        expanded = showOverflow,
                                        onDismissRequest = { showOverflow = false }
                                    ) {
                                        if (hasRename) {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    showEditDialog = true
                                                }
                                            )
                                        }
                                        if (isLocalPlaylist) {
                                            DropdownMenuItem(
                                                text = { Text("Change cover") },
                                                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    pickCover()
                                                }
                                            )
                                            // Only offered once there is
                                            // something to undo: on a generated
                                            // cover it would do nothing visible.
                                            if (hasCustomCover) {
                                                DropdownMenuItem(
                                                    text = { Text("Reset cover") },
                                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                                    onClick = {
                                                        showOverflow = false
                                                        viewModel.resetLocalPlaylistCover(resolvedPlaylist.id)
                                                    }
                                                )
                                            }
                                        }
                                        if (hasShare) {
                                            DropdownMenuItem(
                                                text = { Text("Share") },
                                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                                onClick = {
                                                    showOverflow = false
                                                    val shareUrl = if (isAlbum) {
                                                        "https://music.youtube.com/browse/${resolvedPlaylist.id}"
                                                    } else {
                                                        "https://music.youtube.com/playlist?list=${resolvedPlaylist.id}"
                                                    }
                                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                                                    }
                                                    shareContext.startActivity(
                                                        android.content.Intent.createChooser(send, "Share playlist")
                                                    )
                                                }
                                            )
                                        }
                                        if (hasRename) {
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "Delete playlist",
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                onClick = {
                                                    showOverflow = false
                                                    showDeleteDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        )
                    )
                    // Pinned rather than sticky in the list: the field is always
                    // reachable while searching, and it composes immediately so
                    // it can take focus without waiting for a scroll.
                    // Default (non-bouncy) size springs on purpose: an
                    // Expressive spatial spec bounces, and a bouncing height
                    // undershoots negative. Bounce belongs on scale and
                    // translation, not on anything that measures.
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find in ${if (isAlbum) "album" else "playlist"}") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, "Clear search")
                                    }
                                }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                                .focusRequester(searchFocus),
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(
                    bottom = com.ivor.ivormusic.ui.components.LocalBottomOverlayInset.current
                )
            )
        },
        floatingActionButton = {
            if (isReorderMode) {
                // Vibrant floating toolbar = the M3 Expressive signal for a
                // temporary edit mode; stays reachable however far the list scrolls
                val canReorder = isLocalPlaylist || (canReorderRemote && setVideoIds.isNotEmpty())
                ManageModeToolbar(
                    hint = if (canReorder) "Drag a handle to reorder" else "Tap to remove tracks",
                    icon = if (canReorder) Icons.Rounded.DragIndicator else Icons.Rounded.RemoveCircleOutline,
                    onDone = exitReorderMode,
                    modifier = Modifier.padding(
                        bottom = com.ivor.ivormusic.ui.components.LocalBottomOverlayInset.current
                    )
                )
            } else if (filteredSongs.isNotEmpty()) {
                // M3E split button: Play + menu (shuffle, radio). This is the
                // one primary action on the page.
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
                    } else null,
                    // Clear the floating nav pill and mini player(s)
                    modifier = Modifier.padding(
                        bottom = com.ivor.ivormusic.ui.components.LocalBottomOverlayInset.current
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            // Enough scroll clearance for the floating overlays plus the FAB
            contentPadding = PaddingValues(
                bottom = com.ivor.ivormusic.ui.components.LocalBottomOverlayInset.current +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
            ),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Hero header
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
                            .padding(top = padding.calculateTopPadding(), bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.padding(top = 16.dp)) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .size(220.dp)
                                    .then(
                                        // The artwork is the affordance: 220dp of
                                        // obvious target, exactly where someone
                                        // looking to change the picture looks.
                                        if (isLocalPlaylist) {
                                            Modifier.clickable(onClick = pickCover)
                                        } else Modifier
                                    )
                                    .graphicsLayer {
                                        // Scroll-driven, not time-driven: the art
                                        // trails the list instead of animating on
                                        // its own, so there is nothing to reduce.
                                        val collapse = headerCollapse.value
                                        translationY = collapse * 220.dp.toPx() * 0.28f
                                        val shrink = 1f - collapse * 0.12f
                                        scaleX = shrink
                                        scaleY = shrink
                                        alpha = 1f - collapse * 0.75f
                                    },
                                shadowElevation = 12.dp,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                if (resolvedPlaylist.thumbnailUrl != null && resolvedPlaylist.thumbnailUrl != "null") {
                                    AsyncImage(
                                        model = resolvedPlaylist.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // Expressive placeholder: icon seated in a material
                                    // shape, matching the liked-songs hero treatment
                                    Box(contentAlignment = Alignment.Center) {
                                        Surface(
                                            shape = MaterialShapes.Cookie9Sided.toShape(),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(120.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (isAlbum) Icons.Rounded.Album else Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(56.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Badge on the corner, so the artwork reads as
                            // editable without a caption telling people to tap it.
                            // Rides the same collapse as the art it sits on.
                            if (isLocalPlaylist) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .graphicsLayer {
                                            val collapse = headerCollapse.value
                                            translationY = collapse * 220.dp.toPx() * 0.28f
                                            val shrink = 1f - collapse * 0.12f
                                            scaleX = shrink
                                            scaleY = shrink
                                            alpha = 1f - collapse * 0.75f
                                        }
                                        .clickable(onClick = pickCover)
                                ) {
                                    Icon(
                                        Icons.Rounded.PhotoCamera,
                                        contentDescription = "Change cover art",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = resolvedPlaylist.name,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        val totalDurationLabel = remember(songs) {
                            formatTotalDuration(songs.sumOf { it.duration })
                        }
                        Text(
                            text = listOfNotNull(
                                if (isAlbum) "Album" else "Playlist",
                                resolvedPlaylist.uploaderName.takeIf { isAlbum && it.isNotBlank() },
                                if (songs.size == 1) "1 track" else "${songs.size} tracks",
                                totalDurationLabel
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 10.dp),
                            textAlign = TextAlign.Center
                        )

                        if (!resolvedPlaylist.description.isNullOrBlank()) {
                            Text(
                                text = resolvedPlaylist.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 32.dp)
                                    .padding(top = 12.dp)
                                    .animateContentSize()
                                    .clickable { descriptionExpanded = !descriptionExpanded }
                            )
                        }

                        // Keeping a playlist is the whole point of arriving
                        // here from search, so it sits in the header rather
                        // than behind the overflow. Play is still the one
                        // primary action - that is the button at the bottom.
                        if (canSavePlaylist) {
                            FilledTonalButton(
                                onClick = {
                                    val nowSaved = viewModel.toggleSavedPlaylist(
                                        resolvedPlaylist,
                                        isAlbum = isAlbum
                                    )
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (nowSaved) "Saved to your library"
                                            else "Removed from your library"
                                        )
                                    }
                                },
                                modifier = Modifier.padding(top = 20.dp)
                            ) {
                                // Crossfade rather than a spatial spec: the
                                // button must not resize under the finger that
                                // is still on it.
                                AnimatedContent(
                                    targetState = isSavedPlaylist,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "savePlaylist"
                                ) { saved ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (saved) Icons.Rounded.BookmarkAdded
                                                else Icons.Rounded.BookmarkAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (saved) "Saved" else "Save")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Track section header. Hidden while searching - the search field
            // already labels what the list below it is.
            if (!isSearchActive && !isFetching.value && songs.isNotEmpty()) {
                item {
                    TrackSectionHeader(
                        countLabel = if (songs.size == 1) "1 track" else "${songs.size} tracks",
                        manageEnabled = isReorderMode,
                        canEdit = canEditSongs,
                        onToggleEdit = {
                            if (isReorderMode) {
                                exitReorderMode()
                            } else {
                                isReorderMode = true
                                isSearchActive = false
                                searchQuery = ""
                            }
                        }
                    )
                }
            }

            if (isSearchActive && searchQuery.isNotBlank() && filteredSongs.isNotEmpty()) {
                item {
                    Text(
                        text = if (filteredSongs.size == 1) "1 result" else "${filteredSongs.size} results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
            }

            when {
                isFetching.value -> item { TrackSkeletonList() }

                filteredSongs.isEmpty() && searchQuery.isNotBlank() -> item {
                    EmptyLibraryState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No matches",
                        subtitle = "Nothing in this ${if (isAlbum) "album" else "playlist"} matches \"$searchQuery\""
                    )
                }

                songs.isEmpty() && loadAttempted && !isLocalPlaylist && !isAlbum -> item {
                    // A remote playlist that came back empty is more often a
                    // failed fetch than a genuinely empty playlist, so this
                    // state offers the recovery instead of dead-ending.
                    EmptyLibraryState(
                        icon = Icons.Rounded.CloudOff,
                        title = "Couldn't load tracks",
                        subtitle = "This playlist came back empty. Check your connection and try again.",
                        action = {
                            Button(onClick = {
                                scope.launch {
                                    isFetching.value = true
                                    songs = viewModel.fetchPlaylistSongs(resolvedPlaylist.id)
                                    isFetching.value = false
                                }
                            }) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    )
                }

                songs.isEmpty() -> item {
                    EmptyLibraryState(
                        icon = Icons.Rounded.MusicNote,
                        title = "Nothing in here yet",
                        subtitle = "Songs you add to this ${if (isAlbum) "album" else "playlist"} will show up here"
                    )
                }

                else -> itemsIndexed(
                    items = filteredSongs,
                    key = { _, song -> trackKeyPrefix + song.id }
                ) { _, song ->
                    // Manage mode: song removal for local and YouTube playlists.
                    // Drag reordering works locally and on owned "PL" playlists
                    // (remote rows become draggable once their setVideoIds load).
                    val manageEnabled = isReorderMode && searchQuery.isBlank() && canEditSongs
                    val reorderEnabled = manageEnabled &&
                        (isLocalPlaylist || (canReorderRemote && setVideoIds.isNotEmpty()))
                    val isDragging = draggingKey == trackKeyPrefix + song.id

                    // The handle drags on touch (the discoverable path); long
                    // pressing anywhere on the row is the accelerator for the
                    // same reorder. Both feed the one drag state.
                    val handleDrag = if (reorderEnabled) {
                        Modifier.pointerInput(song.id) {
                            detectDragGestures(
                                onDragStart = { beginDrag(song) },
                                onDragEnd = { finishReorderDrag() },
                                onDragCancel = { finishReorderDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY += amount.y
                                }
                            )
                        }
                    } else Modifier
                    val rowDrag = if (reorderEnabled) {
                        Modifier.pointerInput(song.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { beginDrag(song) },
                                onDragEnd = { finishReorderDrag() },
                                onDragCancel = { finishReorderDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY += amount.y
                                }
                            )
                        }
                    } else Modifier

                    PlaylistTrackRow(
                        song = song,
                        manageEnabled = manageEnabled,
                        reorderEnabled = reorderEnabled,
                        isDragging = isDragging,
                        dragHandleModifier = handleDrag,
                        onClick = { onPlayQueue(filteredSongs, song) },
                        onLongClick = onSongLongPress?.let { press -> { press(song) } },
                        onRemove = { removeSong(song) },
                        modifier = Modifier
                            .fillMaxWidth()
                            // Constant inset, never animated: this feeds
                            // Modifier.padding, which throws on a negative
                            // value, and it aligns row text to the 24dp the
                            // header and section titles use.
                            .padding(horizontal = 8.dp)
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 2f else 0f)
                            .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                            .then(rowDrag)
                    )
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
        // Destructive confirmation runs calm: no bounce, no scale-in entrance.
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    "This will permanently remove \"${resolvedPlaylist.name}\".",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isLocalPlaylist) {
                            viewModel.deleteLocalPlaylist(resolvedPlaylist.id)
                        } else {
                            viewModel.deleteYouTubePlaylist(resolvedPlaylist.id)
                        }
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
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
