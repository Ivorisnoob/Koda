package com.ivor.ivormusic.ui.library
import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R
import com.ivor.ivormusic.ui.components.displaySubtitle

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.ivor.ivormusic.ui.components.PredictiveBackStack
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.albumArtistLabel
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.data.groupSongsByAlbum
import com.ivor.ivormusic.data.groupSongsByArtist
import com.ivor.ivormusic.data.isUnknownAlbum
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.sortedInAlbumOrder
import com.ivor.ivormusic.ui.artist.ArtistScreen
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.components.VideoThumbnail
import com.ivor.ivormusic.ui.downloads.MusicPlaylistDownloadAction
import com.ivor.ivormusic.ui.player.rememberArtworkColorScheme
import com.ivor.ivormusic.ui.theme.MontserratFamily
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.mutableFloatStateOf
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.rounded.CloudUpload
import kotlin.math.roundToInt

/**
 * Feeds the app synthesizes rather than fetches, and the account's own
 * built-ins. None of them is a playlist that could be kept: "LM" (Your Likes)
 * and "LL" are assembled locally, "RTM" is the generated Supermix, and Watch
 * Later is the account's own list, already in the library by definition.
 */
internal val NON_SAVABLE_PLAYLIST_IDS =
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
internal val NON_SHAREABLE_PLAYLIST_IDS = setOf("RTM", READY_OFFLINE_ID)

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
    /** Play a collection with shuffle mode on; see PlayerViewModel.playQueueShuffled. */
    onShuffleQueue: (List<Song>) -> Unit,
    /** Play a curated AI DJ mix: strictly ordered, shuffle unavailable. */
    onPlayDjMix: (List<Song>) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    isLocalLibrary: Boolean = true,
    onDownloadsClick: () -> Unit = {},
    initialArtist: String? = null,
    onInitialArtistConsumed: () -> Unit = {},
    /**
     * Whether the next hand-off ([initialArtist], [initialPlaylist] or
     * [initialAlbum]) came from another tab. Back from what it opens - or from
     * anything reached inside it - returns to that tab through
     * [onReturnToCaller] instead of the Library root: the excursion was never
     * part of this tab's flow, and dropping the user on the root reads as the
     * navigation having lost their place.
     */
    initialReturnToCaller: Boolean = false,
    /** End an excursion that began on another tab: go back to that tab. */
    onReturnToCaller: () -> Unit = {},
    /**
     * Open straight onto a playlist, for callers outside the Library that have
     * one in hand - Spotlight's shortcut grid and shelves. Same hand-off shape
     * as [initialArtist]: the caller clears it through the consumed callback so
     * coming back to the tab later does not re-open the playlist.
     */
    initialPlaylist: PlaylistDisplayItem? = null,
    onInitialPlaylistConsumed: () -> Unit = {},
    /**
     * Open straight onto a device album, named rather than passed whole: the
     * album route is built from a name plus that album's songs, and the player
     * - the one caller - has a [Song] and no list. The songs are gathered here
     * from the library this screen already holds.
     */
    initialAlbum: String? = null,
    onInitialAlbumConsumed: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    /** Open Settings, for the AI DJ screen's key/prompt setup. */
    onOpenAiDjSettings: () -> Unit = {},
    /**
     * Open straight onto the AI DJ screen, for the Home tab's DJ card.
     * Same hand-off shape as [initialPlaylist]: consumed on arrival so
     * returning to the tab later lands on the list.
     */
    initialAiDj: Boolean = false,
    onInitialAiDjConsumed: () -> Unit = {},
    /**
     * Long-press on a song row. Hoisted rather than handled here because the
     * options sheet acts on the player, and HomeScreen is where the
     * PlayerViewModel lives; hosting one sheet up there also keeps a single
     * instance alive across the Library's own sub-routes.
     */
    onSongLongPress: ((Song) -> Unit)? = null,
    /**
     * Queue a song from a playlist row. Same hoisting as [onSongLongPress]:
     * the queue lives in HomeScreen's PlayerViewModel. Null disarms the queue
     * swipe action. The boolean is play-next.
     */
    onEnqueueSong: ((Song, Boolean) -> Unit)? = null,
    /**
     * Hoisted by HomeScreen for the main route's All tab, which is the one the
     * tab opens on. The other sub-tabs and the playlist/artist/album routes keep
     * their own states, since each is a different list and sharing one would
     * restore another list's index.
     */
    allSongsListState: LazyListState = rememberLazyListState(),
    /**
     * Increment to pop the Library back to its Main landing page. Used when
     * the Library tab is re-tapped while already selected.
     */
    resetSignal: Int = 0,
) {
    // Navigation State
    var currentRoute by rememberSaveable { mutableStateOf(LibraryRoute.Main) }

    // Re-tapping the Library tab pops back to the landing page and scrolls
    // to the top.
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            currentRoute = LibraryRoute.Main
            allSongsListState.scrollToItem(0)
        }
    }

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
    var selectedPodcastFeed by remember { mutableStateOf<com.ivor.ivormusic.data.PodcastFeed?>(null) }
    var selectedArtistName by remember { mutableStateOf<String?>(null) }
    // The UC id behind the artist route when one resolved it (similar-artist
    // taps carry it); null means the screen resolves from the name itself.
    var selectedArtistId by remember { mutableStateOf<String?>(null) }
    // True while the open route is an excursion handed over from another tab:
    // back from it, or from anything opened inside it, ends the excursion on
    // that tab rather than on the Library root. Set with the hand-off that
    // opened it, cleared when back leaves.
    var returnsToCaller by remember { mutableStateOf(false) }
    var selectedAlbumName by remember { mutableStateOf<String?>(null) }
    var selectedAlbumSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Handle initial deep link to artist
    LaunchedEffect(initialArtist) {
        if (initialArtist != null) {
            selectedArtistName = initialArtist
            selectedArtistId = null
            returnsToCaller = initialReturnToCaller
            currentRoute = LibraryRoute.Artist
            onInitialArtistConsumed()
        }
    }

    // Handle initial deep link to a playlist
    LaunchedEffect(initialPlaylist) {
        if (initialPlaylist != null) {
            selectedPlaylist = initialPlaylist
            returnsToCaller = initialReturnToCaller
            currentRoute = LibraryRoute.Playlist
            onInitialPlaylistConsumed()
        }
    }

    // Handle the Home tab's DJ card hand-off: open the AI DJ route once, then
    // consume it so later visits to the tab land on the list.
    LaunchedEffect(initialAiDj) {
        if (initialAiDj) {
            currentRoute = LibraryRoute.AiDj
            onInitialAiDjConsumed()
        }
    }

    // Handle initial deep link to an album. Keyed on the song list as well, so
    // a request arriving before the device scan finishes is not resolved to an
    // empty album; an album with no songs is not opened at all, which is the
    // honest outcome for a name the library does not have.
    LaunchedEffect(initialAlbum, songs) {
        if (initialAlbum != null) {
            val requested = initialAlbum.trim()
            val albumSongs = songs.filter {
                it.album.trim().equals(requested, ignoreCase = true)
            }.sortedInAlbumOrder()
            if (albumSongs.isNotEmpty()) {
                selectedAlbumName = initialAlbum
                selectedAlbumSongs = albumSongs
                returnsToCaller = initialReturnToCaller
                currentRoute = LibraryRoute.Album
                onInitialAlbumConsumed()
            }
        }
    }

    // A Spotify link shared into the app opens the import screen on it. The
    // link is held here rather than read from the flow by the screen, so it
    // survives being consumed.
    var spotifyImportLink by remember { mutableStateOf<String?>(null) }
    val pendingSpotifyImport by viewModel.pendingSpotifyImport.collectAsState()
    LaunchedEffect(pendingSpotifyImport) {
        pendingSpotifyImport?.let { link ->
            spotifyImportLink = link
            returnsToCaller = false
            currentRoute = LibraryRoute.SpotifyImport
            viewModel.consumeSpotifyImportRequest()
        }
    }

    // Expressive motion physics for screen pushes/pops
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // Back from any Library route: out to the tab an excursion began on, or
    // to the root. One function, so every screen's back control and the
    // system gesture agree with each other. ImportSongs and AddSongs are the
    // two-level routes: they are only ever reached from an open playlist, so
    // back returns there - and deliberately before the excursion check, which
    // must keep waiting for the back that leaves the playlist itself.
    fun back() {
        if (currentRoute == LibraryRoute.ImportSongs || currentRoute == LibraryRoute.AddSongs) {
            currentRoute = LibraryRoute.Playlist
        } else if (returnsToCaller) {
            returnsToCaller = false
            onReturnToCaller()
        } else {
            currentRoute = LibraryRoute.Main
        }
    }

    // The root and the routes, as blocks both layouts below draw from.
    val mainScreen: @Composable () -> Unit = {
            LibraryMainScreen(
                songs = songs,
                isLocalLibrary = isLocalLibrary,
                viewModel = viewModel,
                contentPadding = contentPadding,
                onSongClick = onSongClick,
                onPlayQueue = onPlayQueue,
                onShuffleQueue = onShuffleQueue,
                onDownloadsClick = onDownloadsClick,
                onNavigateToPlaylist = { playlist ->
                    selectedPlaylist = playlist
                    currentRoute = LibraryRoute.Playlist
                },
                onNavigateToArtist = { artist ->
                    selectedArtistName = artist
                    selectedArtistId = null
                    returnsToCaller = false
                    currentRoute = LibraryRoute.Artist
                },
                // A followed artist carries the id it was followed by, so
                // the page opens on that artist rather than on whoever a
                // search for the name finds first.
                onNavigateToFollowedArtist = { artist ->
                    selectedArtistName = artist.name
                    selectedArtistId = artist.id.takeIf { it.startsWith("UC") }
                    returnsToCaller = false
                    currentRoute = LibraryRoute.Artist
                },
                onNavigateToAlbum = { album, songs ->
                    selectedAlbumName = album
                    selectedAlbumSongs = songs
                    currentRoute = LibraryRoute.Album
                },
                onNavigateToPodcast = { feed ->
                    selectedPodcastFeed = feed
                    currentRoute = LibraryRoute.PodcastDetail
                },
                onNavigateToAiDj = {
                    currentRoute = LibraryRoute.AiDj
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
                onCreatePlaylist = {
                    currentRoute = LibraryRoute.CreatePlaylist
                },
                onSongLongPress = onSongLongPress,
                allSongsListState = allSongsListState
            )
    }

    val routeContent: @Composable (LibraryRoute) -> Unit = { route ->
        when (route) {
            // Main lives underneath now; this layer is empty over it, and full
            // size so both states measure the same.
            LibraryRoute.Main -> Spacer(Modifier.fillMaxSize())
            LibraryRoute.Playlist -> {
                selectedPlaylist?.let { playlist ->
                    PlaylistDetailScreen(
                        playlist = playlist,
                        onBack = { back() },
                        onPlayQueue = onPlayQueue,
                        onShuffleQueue = onShuffleQueue,
                        viewModel = viewModel,
                        isAlbum = false,
                        onSongLongPress = onSongLongPress,
                        onEnqueueSong = onEnqueueSong,
                        onAddSongsRequest = { currentRoute = LibraryRoute.ImportSongs },
                        onSearchSongsRequest = { currentRoute = LibraryRoute.AddSongs }
                    )
                }
            }
            LibraryRoute.PodcastDetail -> {
                selectedPodcastFeed?.let { feed ->
                    com.ivor.ivormusic.ui.podcast.PodcastDetailScreen(
                        feed = feed,
                        onBack = { back() },
                        onPlayQueue = onPlayQueue,
                        onShuffleQueue = onShuffleQueue,
                        contentPadding = contentPadding,
                    )
                }
            }
            LibraryRoute.Album -> {
                selectedAlbumName?.let { album ->
                    // Construct a pseudo-playlist item for the album wrapper.
                    // The artist line names the album's artist, or Various
                    // Artists for a compilation - never one contributor.
                    val albumItem = PlaylistDisplayItem(
                        name = album,
                        url = album, // ID is the name for local albums usually
                        uploaderName = albumArtistLabel(
                            selectedAlbumSongs,
                            stringResource(R.string.various_artists)
                        ),
                        itemCount = selectedAlbumSongs.size,
                        thumbnailUrl = selectedAlbumSongs.firstOrNull()?.albumArtUri.toString()
                    )
                    PlaylistDetailScreen(
                        playlist = albumItem,
                        onBack = { back() },
                        onPlayQueue = onPlayQueue,
                        onShuffleQueue = onShuffleQueue,
                        viewModel = viewModel,
                        preloadedSongs = selectedAlbumSongs,
                        isAlbum = true,
                        onSongLongPress = onSongLongPress,
                        onEnqueueSong = onEnqueueSong,
                        // An album is not an editable local playlist.
                        onAddSongsRequest = null,
                        onSearchSongsRequest = null
                    )
                }
            }
            LibraryRoute.Artist -> {
                selectedArtistName?.let { artist ->
                    ArtistScreen(
                        artistName = artist,
                        artistId = selectedArtistId ?: artist,
                        songs = songs, // Pass all songs, screen filters locally or fetches
                        onBack = { back() },
                        onPlayQueue = onPlayQueue,
                        onShuffleQueue = onShuffleQueue,
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
                        onOpenChannel = onOpenChannel,
                        onOpenArtist = { name, id ->
                            selectedArtistName = name
                            selectedArtistId = id
                            currentRoute = LibraryRoute.Artist
                        },
                        onOpenPlaylist = { playlist ->
                            selectedPlaylist = playlist
                            currentRoute = LibraryRoute.Playlist
                        }
                    )
                }
            }
            LibraryRoute.Stats -> {
                StatsScreen(
                    onBack = { back() },
                    viewModel = viewModel,
                    contentPadding = contentPadding
                )
            }
            LibraryRoute.History -> {
                ListeningHistoryScreen(
                    onBack = { back() },
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
                    onBack = { back() },
                    onPlayQueue = onPlayQueue,
                    onShuffleQueue = onShuffleQueue,
                    viewModel = viewModel,
                    preloadedSongs = readyOffline.songs,
                    isAlbum = false,
                    onSongLongPress = onSongLongPress,
                    onEnqueueSong = onEnqueueSong,
                    // A cache view, not an editable local playlist.
                    onAddSongsRequest = null,
                    onSearchSongsRequest = null
                )
            }
            LibraryRoute.CreatePlaylist -> {
                PlaylistStudioScreen(
                    viewModel = viewModel,
                    onBack = { back() },
                    onImportFromSpotify = {
                        spotifyImportLink = null
                        currentRoute = LibraryRoute.SpotifyImport
                    },
                    onCreated = { created ->
                        // Straight onto the new playlist; back from there goes
                        // to the root, the same as any playlist opened from it.
                        selectedPlaylist = created
                        returnsToCaller = false
                        currentRoute = LibraryRoute.Playlist
                    }
                )
            }
            LibraryRoute.ImportSongs -> {
                selectedPlaylist?.let { target ->
                    PlaylistImportScreen(
                        targetPlaylist = target,
                        viewModel = viewModel,
                        onBack = { back() },
                        onDone = { back() }
                    )
                }
            }
            LibraryRoute.SpotifyImport -> {
                SpotifyImportScreen(
                    initialLink = spotifyImportLink,
                    viewModel = viewModel,
                    onBack = { back() },
                    onCreated = { created ->
                        selectedPlaylist = created
                        returnsToCaller = false
                        currentRoute = LibraryRoute.Playlist
                    }
                )
            }
            LibraryRoute.AiDj -> {
                AiDjScreen(
                    viewModel = viewModel,
                    onBack = { back() },
                    onPlayQueue = { songs, song -> onPlayQueue(songs, song) },
                    onPlayDjMix = onPlayDjMix,
                    onOpenSettings = onOpenAiDjSettings,
                    contentPadding = contentPadding,
                )
            }
            LibraryRoute.AddSongs -> {
                selectedPlaylist?.let { target ->
                    val localIds by viewModel.localPlaylistIds.collectAsState()
                    PlaylistAddSongsScreen(
                        targetPlaylist = target,
                        targetIsLocal = target.id in localIds,
                        viewModel = viewModel,
                        onBack = { back() },
                        onDone = { back() }
                    )
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    if (maxWidth >= LIBRARY_TWO_PANE_MIN_WIDTH) {
        // List and detail side by side: the library stays where it is and
        // what it opens appears beside it, so moving between playlists is a
        // tap each rather than a push and a pop. Back closes the detail in
        // place; nothing departs the screen, so it is not a peel.
        BackHandler(enabled = currentRoute != LibraryRoute.Main) { back() }
        val listWidth = (maxWidth * 0.36f).coerceIn(340.dp, 440.dp)
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight()
            ) {
                // The list marks what is open beside it.
                val openItem = when (currentRoute) {
                    LibraryRoute.Playlist, LibraryRoute.ImportSongs, LibraryRoute.AddSongs ->
                        LibraryOpenItem(playlistId = selectedPlaylist?.id)
                    LibraryRoute.Album -> LibraryOpenItem(albumName = selectedAlbumName)
                    LibraryRoute.Artist -> LibraryOpenItem(artistName = selectedArtistName)
                    else -> null
                }
                CompositionLocalProvider(LocalLibraryOpenItem provides openItem) {
                    mainScreen()
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                AnimatedContent(
                    targetState = currentRoute,
                    label = "LibraryDetailPane",
                    transitionSpec = {
                        (fadeIn(animationSpec = effectsSpec) +
                            slideInHorizontally(animationSpec = spatialSpec) { it / 12 }) togetherWith
                            fadeOut(animationSpec = effectsSpec) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    }
                ) { route ->
                    if (route == LibraryRoute.Main) {
                        LibraryDetailPlaceholder(contentPadding = contentPadding)
                    } else {
                        routeContent(route)
                    }
                }
            }
        }
    } else
    PredictiveBackStack(
        childOpen = currentRoute != LibraryRoute.Main,
        onBack = { back() },
        // An excursion's back lands on another tab, not on the root the peel
        // would reveal, so it is not previewed; the tab slide carries it.
        // ImportSongs and AddSongs back onto the playlist, not the root the
        // peel shows, so they are not previewed either.
        previewable = currentRoute != LibraryRoute.Main && !returnsToCaller &&
            currentRoute != LibraryRoute.ImportSongs && currentRoute != LibraryRoute.AddSongs,
        background = mainScreen
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
        routeContent(route)
    }
    }
    }
}

enum class LibraryRoute {
    Main, Playlist, Album, Artist, Stats, History, ReadyOffline,
    /** A podcast feed's detail page (episodes, like a playlist). */
    PodcastDetail,
    /** The immersive local-playlist creation flow (PlaylistStudioScreen). */
    CreatePlaylist,
    /** "Add from your playlists" into the open local playlist (PlaylistImportScreen). */
    ImportSongs,
    /** Recents, likes and a YouTube Music search into the open playlist (PlaylistAddSongsScreen). */
    AddSongs,
    /** A Spotify playlist or album, matched on YouTube Music into a new device playlist (SpotifyImportScreen). */
    SpotifyImport,
    /** The AI DJ mix generator (AiDjScreen). */
    AiDj
}

enum class LibraryTab(val label: String) {
    All("All"),
    Playlists("Playlists"),
    Artists("Artists"),
    Albums("Albums"),
    Podcasts("Podcasts")
}

/**
 * Sort orders for the All tab's track list. Labels state their own direction
 * ("Most played", not "Play count") so the list needs no asc/desc toggle.
 */
enum class PlaylistSortAction(val label: Int) {
    Title(R.string.lib_sort_title),
    Artist(R.string.lib_sort_artist),
    Album(R.string.lib_sort_album),
    Reverse(R.string.lib_sort_reverse),
    Shuffle(R.string.lib_sort_shuffle),
}

/**
 * Sort orders for the Artists and Albums tabs, which had none (feedback,
 * September 2026: "organize them by the number of songs"). Persisted by name,
 * so the constants are frozen.
 */
enum class LibraryGroupSort(val label: Int, val icon: ImageVector) {
    Name(R.string.lib_group_sort_name, Icons.Rounded.SortByAlpha),
    MostSongs(R.string.lib_group_sort_songs, Icons.Rounded.LibraryMusic),
    MostPlayed(R.string.lib_group_sort_played, Icons.Rounded.TrendingUp);

    /** [groups] (a name and its songs) in this order; ties fall back to the name. */
    fun <T> apply(groups: List<Pair<String, List<T>>>, plays: (T) -> Int): List<Pair<String, List<T>>> {
        val byName = compareBy<Pair<String, List<T>>> { it.first.lowercase() }
        return when (this) {
            Name -> groups.sortedWith(byName)
            MostSongs -> groups.sortedWith(compareByDescending<Pair<String, List<T>>> { it.second.size }.then(byName))
            MostPlayed -> groups.sortedWith(
                compareByDescending<Pair<String, List<T>>> { g -> g.second.sumOf(plays) }.then(byName)
            )
        }
    }

    companion object {
        fun from(name: String?): LibraryGroupSort = entries.firstOrNull { it.name == name } ?: Name
    }
}

/** The count and sort button above the Artists and Albums grids. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupSortHeader(
    countLabel: String,
    sort: LibraryGroupSort,
    onSortChange: (LibraryGroupSort) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            countLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Box {
            var open by remember { mutableStateOf(false) }
            val sortLabel = stringResource(sort.label)
            FilledTonalIconButton(
                onClick = { open = true },
                modifier = Modifier.size(40.dp),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.Rounded.SwapVert,
                    contentDescription = stringResource(R.string.lib_group_sort_cd, sortLabel),
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                LibraryGroupSort.entries.forEach { option ->
                    val selected = option == sort
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(option.label),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSortChange(option)
                            open = false
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
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }
}

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
    /** Play a collection with shuffle mode on; see PlayerViewModel.playQueueShuffled. */
    onShuffleQueue: (List<Song>) -> Unit,
    onDownloadsClick: () -> Unit,
    onNavigateToPlaylist: (PlaylistDisplayItem) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToFollowedArtist: (com.ivor.ivormusic.data.TasteArtist) -> Unit = {},
    onNavigateToAlbum: (String, List<Song>) -> Unit,
    onNavigateToPodcast: (com.ivor.ivormusic.data.PodcastFeed) -> Unit = {},
    onNavigateToAiDj: () -> Unit = {},
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit,
    /** Songs cached in full; the entry point hides itself when nothing is. */
    readyOfflineCount: Int = 0,
    readyOfflineBytes: Long = 0L,
    readyOfflineUnnamed: Int = 0,
    readyOfflineHistoryDisabled: Boolean = false,
    onReadyOfflineClick: () -> Unit = {},
    /** Opens the immersive creation flow. Required: a silent default here is
     * a New playlist button that does nothing. */
    onCreatePlaylist: () -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    allSongsListState: LazyListState = rememberLazyListState()
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val localPlaylistIds by viewModel.localPlaylistIds.collectAsState()
    val savedPlaylistIds by viewModel.savedPlaylistIds.collectAsState()
    val hiddenPlaylists by viewModel.hiddenPlaylists.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val playCounts by viewModel.playCounts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()

    // Sort order sticks across launches, so it lives in prefs rather than in
    // rememberSaveable. Matched by name instead of valueOf so an option
    // removed in a later version falls back to Title instead of throwing.
    val context = LocalContext.current
    val todayLabel = stringResource(R.string.sc_today)
    val yesterdayLabel = stringResource(R.string.lh_yesterday)
    val themePreferences = remember(context) { ThemePreferences(context) }

    // The open tab is persisted for the same reason, and it is the same bug:
    // rememberSaveable survived rotation but not leaving the screen, and this
    // screen lives inside Home's AnimatedContent, so every trip out to a
    // playlist and back landed on All again. Someone who lives in Albums had
    // to re-select it all day.
    val storedTabName by themePreferences.libraryTab.collectAsState()
    val selectedTab = remember(storedTabName) {
        LibraryTab.entries.firstOrNull { it.name == storedTabName } ?: LibraryTab.All
    }
    val tabHaptics = com.ivor.ivormusic.util.rememberKodaHaptics()
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                // headlineLarge matches the music Search title beside it.
                Text(
                    text = stringResource(R.string.tab_library),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // New playlist sits in the header, not in a floating button.
                // A FAB anchored above the nav pill and both mini players rode
                // halfway up the screen whenever music and a video were both
                // loaded (#299), over the very rows it was meant to sit below.
                // Downloads and History, the menu's other entries, already have
                // rows of their own in the All tab's shortcuts card.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = onCreatePlaylist,
                        modifier = Modifier.size(56.dp),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.action_new_playlist), modifier = Modifier.size(24.dp))
                    }
                    FilledTonalIconButton(
                        onClick = onNavigateToAiDj,
                        modifier = Modifier.size(56.dp),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = stringResource(R.string.aidj_title), modifier = Modifier.size(24.dp))
                    }
                    FilledTonalIconButton(
                        onClick = onNavigateToStats,
                        modifier = Modifier.size(56.dp),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Insights, contentDescription = stringResource(R.string.cd_listening_stats), modifier = Modifier.size(24.dp))
                    }
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
                        onCheckedChange = {
                            if (tab != selectedTab) {
                                tabHaptics.subtle()
                                themePreferences.setLibraryTab(tab.name)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            LibraryTab.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
            // No pager here, by decision: the Home shell owns the horizontal
            // flick (switching main tabs), and a pager inside it both ate
            // those flicks and fought them. Tab moves go through the strip
            // above, with a directional slide for orientation.
            AnimatedContent(
                targetState = selectedTab,
                label = "LibraryTab",
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    if (forward) {
                        (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -width / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> width } + fadeOut())
                    }
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
                            onShuffleQueue = onShuffleQueue,
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
                            hiddenPlaylists = hiddenPlaylists,
                            onHidePlaylist = { playlist -> viewModel.hidePlaylist(playlist) },
                            onUnhidePlaylist = { id -> viewModel.unhidePlaylist(id) },
                            contentPadding = contentPadding,
                            isLoggedIn = isYouTubeConnected,
                            importAction = { PlaylistImportButton(viewModel) },
                            onCreatePlaylist = onCreatePlaylist,
                            fromVideoMode = viewModel.videoPlaylistsForMusic.collectAsState().value
                        )
                    }
                    LibraryTab.Artists -> {
                        val artistSort by themePreferences.libraryArtistSort.collectAsState()
                        val tasteContext = LocalContext.current
                        val tasteStore = remember(tasteContext) {
                            com.ivor.ivormusic.data.TasteProfileStore(tasteContext)
                        }
                        val tasteProfile by tasteStore.profile.collectAsState()
                        ArtistsGrid(
                            songs = librarySongs,
                            followed = tasteProfile.artists,
                            onFollowedClick = onNavigateToFollowedArtist,
                            onArtistClick = onNavigateToArtist,
                            contentPadding = contentPadding,
                            sort = LibraryGroupSort.from(artistSort),
                            onSortChange = { themePreferences.setLibraryArtistSort(it.name) },
                            playCounts = playCounts
                        )
                    }
                    LibraryTab.Albums -> {
                        val albumSort by themePreferences.libraryAlbumSort.collectAsState()
                        AlbumsGrid(
                            songs = librarySongs,
                            onAlbumClick = onNavigateToAlbum,
                            contentPadding = contentPadding,
                            sort = LibraryGroupSort.from(albumSort),
                            onSortChange = { themePreferences.setLibraryAlbumSort(it.name) },
                            playCounts = playCounts
                        )
                    }
                    LibraryTab.Podcasts -> {
                        com.ivor.ivormusic.ui.podcast.PodcastsScreen(
                            onFeedClick = onNavigateToPodcast,
                            onPlayEpisode = { song -> onPlayQueue(listOf(song), song) },
                            contentPadding = contentPadding,
                            showTitle = false,
                        )
                    }
                }
            }
        }
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
    /** Play a collection with shuffle mode on; see PlayerViewModel.playQueueShuffled. */
    onShuffleQueue: (List<Song>) -> Unit,
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
    val todayLabel = stringResource(R.string.sc_today)
    val yesterdayLabel = stringResource(R.string.lh_yesterday)
    val likedIds = remember(likedSongs) { likedSongs.map { it.id }.toSet() }
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.map { it.id }.toSet() }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        // Liked Songs stays the single hero. Downloads, Ready offline and
        // Listening history collapse into one grouped shortcuts card below it,
        // so three full-width banners no longer stack before the music starts.
        if (likedSongs.isNotEmpty()) {
            item(key = "liked_hero") {
                ExpressiveLikedSongsCard(
                    count = likedSongs.size,
                    onClick = onLikedSongsClick
                )
            }
        }

        item(key = "library_shortcuts") {
            LibraryShortcutsCard(
                downloadCount = downloadedSongs.size.takeIf { it > 0 },
                onDownloadsClick = onDownloadsClick,
                readyOfflineCount = readyOfflineCount,
                readyOfflineBytes = readyOfflineBytes,
                readyOfflineUnnamed = readyOfflineUnnamed,
                readyOfflineHistoryDisabled = readyOfflineHistoryDisabled,
                onReadyOfflineClick = onReadyOfflineClick,
                historyPlayCount = playCounts.values.sum(),
                onHistoryClick = onNavigateToHistory
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
                        stringResource(R.string.recently_played),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onNavigateToHistory) {
                        Text(stringResource(R.string.action_see_all))
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
                            onClick = { onPlayQueue(recentlyPlayed, song) },
                            onLongPress = onSongLongPress?.let { press -> { press(song) } }
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
                                onShuffleQueue(songs)
                            },
                            modifier = Modifier.size(40.dp),
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = stringResource(R.string.cd_shuffle),
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
                    title = stringResource(R.string.library_empty_title),
                    subtitle = stringResource(R.string.library_empty_subtitle)
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
                        ?.let { pluralStringResource(R.plurals.n_plays, it, it) }
                    LibrarySortOption.RecentlyAdded -> song.dateAdded?.let { formatRelativeDate(it, todayLabel, yesterdayLabel) }
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

/**
 * Downloads, Ready offline and Listening history as one grouped shortcuts card.
 *
 * Liked Songs stays the single hero above this; these three are utilities, not
 * heroes, and each had a full-width 76dp banner in its own container color,
 * which is what stacked into ~370dp of chrome before any music. Grouping them
 * into one [surfaceContainerLow] card with settings-hub rows keeps every
 * destination (including the permanent history entry point) while costing one
 * card slot. The leading icon dots carry the old per-card colors so nothing
 * loses its identity: secondary for downloads, primary for ready-offline,
 * tertiary for history. All colors resolve through ColorScheme; press feedback
 * is a spring scale per DESIGN.md.
 */
@Composable
private fun LibraryShortcutsCard(
    downloadCount: Int?,
    onDownloadsClick: () -> Unit,
    readyOfflineCount: Int,
    readyOfflineBytes: Long,
    readyOfflineUnnamed: Int,
    readyOfflineHistoryDisabled: Boolean,
    onReadyOfflineClick: () -> Unit,
    historyPlayCount: Int,
    onHistoryClick: () -> Unit
) {
    val showReadyOffline =
        readyOfflineCount > 0 || (readyOfflineHistoryDisabled && readyOfflineUnnamed > 0)
    val readyOfflineSubtitle = when {
        readyOfflineCount > 0 -> buildString {
            append(if (readyOfflineCount == 1) "1 song" else "$readyOfflineCount songs")
            if (readyOfflineBytes > 0) append(" · ${formatCacheSize(readyOfflineBytes)}")
        }
        // Deliberately names the setting rather than saying "unavailable": the
        // fix is one toggle and the user is the only one who can make it.
        readyOfflineUnnamed == 1 -> "1 cached song. Turn on listening history to list it"
        else -> "$readyOfflineUnnamed cached songs. Turn on listening history to list them"
    }
    // Not clickable in the explanatory state - there is nothing behind it,
    // and a row that opens an empty screen is worse than one that does not move.
    val readyOfflineClickable = readyOfflineCount > 0

    // Segmented, like Settings: each shortcut its own container.
    com.ivor.ivormusic.ui.components.SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        run {
            // Always listed, even with nothing finished: Downloads is also where
            // a download in progress is watched and paused, and this row is
            // now the Library's only way there.
            LibraryShortcutRow(
                icon = Icons.Rounded.DownloadDone,
                iconContainerColor = MaterialTheme.colorScheme.secondary,
                iconContentColor = MaterialTheme.colorScheme.onSecondary,
                title = stringResource(R.string.fab_downloads),
                subtitle = if (downloadCount != null) {
                    "$downloadCount songs available offline"
                } else {
                    stringResource(R.string.library_downloads_none)
                },
                onClick = onDownloadsClick
            )
            var needsDivider = true
            if (showReadyOffline) {
                if (needsDivider) LibraryShortcutDivider()
                LibraryShortcutRow(
                    icon = Icons.Rounded.OfflineBolt,
                    iconContainerColor = if (readyOfflineClickable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    iconContentColor = if (readyOfflineClickable) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    title = stringResource(R.string.ready_offline),
                    subtitle = readyOfflineSubtitle,
                    onClick = if (readyOfflineClickable) onReadyOfflineClick else null
                )
                needsDivider = true
            }
            if (needsDivider) LibraryShortcutDivider()
            LibraryShortcutRow(
                icon = Icons.Rounded.History,
                iconContainerColor = MaterialTheme.colorScheme.tertiary,
                iconContentColor = MaterialTheme.colorScheme.onTertiary,
                title = stringResource(R.string.fab_listening_history),
                subtitle = when {
                    historyPlayCount <= 0 -> stringResource(R.string.lib_everything_in_order)
                    else -> pluralStringResource(R.plurals.n_plays, historyPlayCount, historyPlayCount)
                },
                onClick = onHistoryClick
            )
        }
    }
}

@Composable
private fun LibraryShortcutRow(
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "shortcutRowScale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconContainerColor,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconContentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

/** Bytes as MB or GB, matching how the cache size reads in Settings. */
private fun formatCacheSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024) else "${mb.toInt()} MB"
}

@Composable
private fun LibraryShortcutDivider() {
    if (com.ivor.ivormusic.ui.components.LocalInSegmentedColumn.current) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun RecentSongCard(
    song: Song,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .songRowClick(onClick = onClick, onLongClick = onLongPress)
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
        // 4dp in and 8dp of floor under the artist line, so both clear the
        // card's rounded clip; flush against it, the bottom corners cut the
        // ends and descenders off the artist.
        Text(
            song.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
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
    onRemoveSavedPlaylist: (PlaylistDisplayItem) -> Unit = {},
    /**
     * Playlists the user told Koda not to list, and the two ways back. Passed
     * in rather than read from a repository here so this grid stays a pure
     * rendering of what it is given, as the rest of its arguments are.
     */
    hiddenPlaylists: List<com.ivor.ivormusic.data.HiddenPlaylist> = emptyList(),
    onHidePlaylist: (PlaylistDisplayItem) -> Unit = {},
    onUnhidePlaylist: (String) -> Unit = {},
    /** Signed in, so the account's own playlists live on YouTube for real. */
    isLoggedIn: Boolean = false,
    importAction: @Composable () -> Unit,
    /** The empty state's own way out: it tells the user to make a playlist. */
    onCreatePlaylist: () -> Unit,
    /** Video mode's device playlists; see HomeViewModel.videoPlaylistsForMusic. */
    fromVideoMode: List<PlaylistDisplayItem> = emptyList()
) {
    var showHiddenSheet by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
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

        // The header stands whenever there is either a playlist or something
        // hidden, because the hidden count is the only route back and a
        // library whose every playlist is hidden is exactly when it is needed.
        run {
            item(key = "playlists_header", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your playlists • ${playlists.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    importAction()
                    if (hiddenPlaylists.isNotEmpty()) {
                        TextButton(onClick = { showHiddenSheet = true }) {
                            Icon(
                                Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    R.string.lib_hidden_count,
                                    hiddenPlaylists.size
                                )
                            )
                        }
                    }
                }
            }
        }
        if (playlists.isEmpty()) {
            item(key = "playlists_empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyLibraryState(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    title = stringResource(R.string.spotlight_empty_no_playlists),
                    subtitle = stringResource(R.string.library_start_playlist_hint),
                    action = {
                        Button(onClick = onCreatePlaylist, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.action_new_playlist))
                        }
                    }
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
                name = playlist.name ?: stringResource(R.string.untitled_song),
                count = playlist.itemCount,
                // A saved playlist's author is the thing that tells it apart
                // from the user's own at a glance, so it wins the subtitle.
                subtitle = when {
                    isSavedPlaylist || playlist.itemCount < 0 ->
                        playlist.displaySubtitle().ifBlank { stringResource(R.string.label_playlist) }
                    else -> null
                },
                thumbnailUrl = playlist.thumbnailUrl,
                description = playlist.description,
                isEditable = isLocalPlaylist || isYouTubeEditable,
                isSaved = isSavedPlaylist,
                affectsYouTubeAccount = isYouTubeEditable && isLoggedIn,
                onEditConfirmed = { name, description -> onEditPlaylist(playlist, name, description) },
                onDeleteConfirmed = { onDeletePlaylist(playlist) },
                onRemoveSaved = { onRemoveSavedPlaylist(playlist) },
                onHide = { onHidePlaylist(playlist) },
                selected = LocalLibraryOpenItem.current?.playlistId == playlist.id,
                onClick = { onPlaylistClick(playlist) }
            )
        }

        // Video mode's own device playlists, playable here as songs. Read-only
        // in this mode (no edit menu): they are edited where they live.
        if (fromVideoMode.isNotEmpty()) {
            item(key = "from_video_header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.lib_from_video_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(fromVideoMode, key = { "from_video_${it.id}" }) { playlist ->
                ExpressivePlaylistCard(
                    name = playlist.name ?: stringResource(R.string.untitled_song),
                    count = playlist.itemCount,
                    subtitle = playlist.displaySubtitle(),
                    thumbnailUrl = playlist.thumbnailUrl,
                    onHide = { onHidePlaylist(playlist) },
                    selected = LocalLibraryOpenItem.current?.playlistId == playlist.id,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
    }

    if (showHiddenSheet) {
        HiddenPlaylistsSheet(
            hidden = hiddenPlaylists,
            onUnhide = onUnhidePlaylist,
            onDismiss = { showHiddenSheet = false }
        )
    }
}

/**
 * What has been hidden, and the way back.
 *
 * This screen is the whole reason hiding is safe to offer. Without it a
 * mis-tap weeks ago is an invisible hole in the library that the user has no
 * way to explain or repair - the same reason `NotInterestedScreen` exists for
 * blocked channels. The entry point is a count beside the section header,
 * shown only when there is something to show, so it costs nothing until it
 * matters.
 *
 * Un-hiding is immediate and needs no confirmation: nothing is destroyed in
 * either direction, and the playlist reappears in the grid behind this sheet
 * on the next frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenPlaylistsSheet(
    hidden: List<com.ivor.ivormusic.data.HiddenPlaylist>,
    onUnhide: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.lib_hidden_playlists),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.lib_hidden_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // A LazyColumn, and weighted rather than wrapped: someone can hide
            // a great many playlists, and a Column of them would run off the
            // bottom of the sheet with nothing to scroll.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hidden, key = { it.playlistId }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!entry.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = entry.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name.ifBlank {
                                    stringResource(R.string.label_playlist)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            entry.uploaderName.takeIf { it.isNotBlank() }?.let { by ->
                                Text(
                                    text = by,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onUnhide(entry.playlistId) }) {
                            Text(stringResource(R.string.lib_unhide))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsGrid(
    songs: List<Song>,
    onArtistClick: (String) -> Unit,
    contentPadding: PaddingValues,
    sort: LibraryGroupSort = LibraryGroupSort.Name,
    onSortChange: (LibraryGroupSort) -> Unit = {},
    playCounts: Map<String, Int> = emptyMap(),
    /** Artists followed on this device, shown above the ones the library's songs name. */
    followed: List<com.ivor.ivormusic.data.TasteArtist> = emptyList(),
    onFollowedClick: (com.ivor.ivormusic.data.TasteArtist) -> Unit = {}
) {
    val artists = remember(songs, sort, playCounts) {
        sort.apply(
            songs.filterNot { isUnknownArtist(it.artist) }.groupSongsByArtist()
        ) { song -> playCounts[song.id] ?: 0 }
    }

    if (artists.isEmpty() && followed.isEmpty()) {
        EmptyLibraryState(
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.no_artists_yet),
            subtitle = stringResource(R.string.no_artists_yet_subtitle)
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp), // Slightly smaller for artists
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Following first: these are the artists the user chose by name,
        // where the list below is whoever their songs happen to credit. An
        // artist can be in both, and is: one says "I follow them", the other
        // "I have their songs".
        if (followed.isNotEmpty()) {
            item(key = "followed_header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.lib_following_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            items(followed, key = { "followed_" + it.key }) { artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onFollowedClick(artist) }
                ) {
                    Surface(
                        shape = CircleShape,
                        modifier = Modifier.size(140.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp,
                        border = libraryOpenBorder(LocalLibraryOpenItem.current?.artistName == artist.name)
                    ) {
                        if (!artist.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = artist.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Person,
                                    null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.artist_following),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        if (artists.isNotEmpty()) item(key = "artists_sort", span = { GridItemSpan(maxLineSpan) }) {
            GroupSortHeader(
                countLabel = pluralStringResource(R.plurals.lib_artist_count, artists.size, artists.size),
                sort = sort,
                onSortChange = onSortChange
            )
        }
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
                     shadowElevation = 6.dp,
                     border = libraryOpenBorder(LocalLibraryOpenItem.current?.artistName == artist)
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
    contentPadding: PaddingValues,
    sort: LibraryGroupSort = LibraryGroupSort.Name,
    onSortChange: (LibraryGroupSort) -> Unit = {},
    playCounts: Map<String, Int> = emptyMap()
) {
    // Filter BEFORE building grid items — filtering inside the item lambda
    // leaves blank holes in the grid for skipped albums.
    val albums = remember(songs, sort, playCounts) {
        sort.apply(
            songs.filterNot { isUnknownAlbum(it.album) }.groupSongsByAlbum()
        ) { song -> playCounts[song.id] ?: 0 }
    }

    if (albums.isEmpty()) {
        EmptyLibraryState(
            icon = Icons.Rounded.Album,
            title = stringResource(R.string.no_albums_yet),
            subtitle = stringResource(R.string.no_albums_yet_subtitle)
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "albums_sort", span = { GridItemSpan(maxLineSpan) }) {
            GroupSortHeader(
                countLabel = pluralStringResource(R.plurals.lib_album_count, albums.size, albums.size),
                sort = sort,
                onSortChange = onSortChange
            )
        }
        items(albums, key = { (name, _) -> name }) { (album, albumSongs) ->
            val art = albumSongs.firstOrNull { it.albumArtUri != null }?.albumArtUri?.toString()
                ?: albumSongs.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
            ExpressivePlaylistCard(
                name = album,
                count = albumSongs.size,
                thumbnailUrl = art,
                subtitle = albumArtistLabel(
                    albumSongs,
                    stringResource(R.string.various_artists)
                ),
                selected = LocalLibraryOpenItem.current?.albumName == album,
                onClick = { onAlbumClick(album, albumSongs) }
            )
        }
    }
}

// ============ UI COMPONENTS ============

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLikedSongsCard(count: Int, onClick: () -> Unit) {
    // A row, not a banner: the same height and rhythm as the shortcut rows
    // under it, so it reads as the first entry of the Library rather than a
    // poster in front of it. It keeps the tinted container and the heart in a
    // SoftBurst - that is what makes it findable - and drops the 120dp height
    // and headline type that made it shout.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "likedCardScale"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        color = MaterialTheme.colorScheme.primaryContainer,
        border = libraryOpenBorder(LocalLibraryOpenItem.current?.playlistId == "LM")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Favorite,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.liked_songs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (count == 1) "1 song" else "$count songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1
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
    /**
     * Deleting this card destroys a playlist on the signed-in YouTube account,
     * not just on the device. Signed out, or for a local playlist, it is false
     * and the confirmation says nothing about YouTube.
     */
    affectsYouTubeAccount: Boolean = false,
    /**
     * Stop listing this playlist in Koda. Null where hiding makes no sense;
     * non-null it is offered on every card, editable or not.
     */
    onHide: (() -> Unit)? = null,
    onEditConfirmed: (String, String?) -> Unit = { _, _ -> },
    onDeleteConfirmed: () -> Unit = {},
    onRemoveSaved: () -> Unit = {},
    /** Open in the detail pane beside the Library list; see [LocalLibraryOpenItem]. */
    selected: Boolean = false,
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
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = libraryOpenBorder(selected)
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
                        contentDescription = stringResource(R.string.cd_save_to_library),
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
            if (isEditable || isSaved || onHide != null) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        // Hiding is offered on every card, including the ones
                        // with nothing else in this menu: the synthesized
                        // Supermix and Your Likes entries cannot be renamed,
                        // deleted or unsaved, and they are exactly the rows
                        // people want out of the way.
                        onHide?.let { hide ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lib_hide_playlist)) },
                                onClick = {
                                    showMenu = false
                                    hide()
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null)
                                }
                            )
                        }
                        if (isSaved) {
                            // Removing the reference, not the playlist: no
                            // confirmation dialog, because nothing is destroyed
                            // and saving it again is one tap on its page.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cd_remove_from_library)) },
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
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = {
                                    showMenu = false
                                    showEditDialog = true
                                },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
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
            title = { Text(stringResource(R.string.delete_playlist_q)) },
            text = {
                Text(
                    if (affectsYouTubeAccount) {
                        "This will permanently remove \"$name\" from your YouTube account. " +
                            stringResource(R.string.lib_delete_body)
                    } else {
                        "This will permanently remove \"$name\"."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConfirmed()
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
internal fun EditPlaylistDialog(
    initialName: String,
    initialDescription: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit,
    title: String? = null
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
        title = { Text(title ?: stringResource(R.string.lib_edit_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_optional_label)) },
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim().ifBlank { null }) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
                                contentDescription = stringResource(R.string.song_options_downloaded),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isLiked) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = stringResource(R.string.cd_favorite),
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
