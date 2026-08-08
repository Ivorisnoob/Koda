package com.ivor.ivormusic.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.IconToggleButton
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import androidx.activity.compose.BackHandler
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.ui.components.MusicVideoToggle
import com.ivor.ivormusic.ui.components.MusicVideoToggleState
import com.ivor.ivormusic.ui.components.rememberMusicVideoToggleState
import com.ivor.ivormusic.ui.components.rememberPermissionState
import com.ivor.ivormusic.ui.components.scrollToTop
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.ExpandablePlayer
import com.ivor.ivormusic.ui.player.PlayerSheetContent
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.compose.material3.MaterialShapes
import androidx.compose.animation.with
import kotlinx.coroutines.launch
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.video.VideoHomeContent
import com.ivor.ivormusic.ui.library.LibraryContent
import androidx.compose.animation.ExperimentalAnimationApi
import com.ivor.ivormusic.BuildConfig
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.UpdateRepository
import com.ivor.ivormusic.data.UpdateResult

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    onSongClick: (Song) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: HomeViewModel = viewModel(),
    isDarkMode: Boolean = true,
    onThemeToggle: (Boolean) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToVideoPlayer: (VideoItem) -> Unit = {},
    onOpenShorts: (List<com.ivor.ivormusic.data.ShortsItem>, Int) -> Unit = { _, _ -> },
    shortsEnabled: Boolean = false,
    loadLocalSongs: Boolean = true,
    excludedFolders: Set<String> = emptySet(),
    ambientBackground: Boolean = true,
    playerArtworkColors: Boolean = true,
    videoMode: Boolean = false,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    playerStyle: PlayerStyle = PlayerStyle.CLASSIC,
    onPlayerStyleChange: (PlayerStyle) -> Unit = {},
    manualScan: Boolean = false,
    localOnly: Boolean = false,
    hasVideoMiniPlayer: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localSongs by viewModel.songs.collectAsState()
    val youtubeSongs by viewModel.youtubeSongs.collectAsState()
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    
    // Use local songs or YouTube songs (which includes fallback search results if not logged in)
    val songs = if (loadLocalSongs) localSongs else youtubeSongs

    // Local play history, for the "Jump back in" rail. Free (a file read, no
    // network), and refreshed whenever the Home tab comes back into view so a
    // song played since the last look shows up.
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val isBuffering by playerViewModel.isBuffering.collectAsState()
    val playWhenReady by playerViewModel.playWhenReady.collectAsState()
    val progress by playerViewModel.progress.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    
    val progressFraction = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f
    
    // Bottom sheet state for player - skip partial expand for direct full-screen
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlayerSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val permissionState = rememberPermissionState(
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    // Load songs based on setting
    LaunchedEffect(Unit, loadLocalSongs, excludedFolders, manualScan) {
        viewModel.checkYouTubeConnection()
        if (loadLocalSongs) {
            if (!permissionState.isGranted) {
                permissionState.launchPermissionRequest()
            } else {
                viewModel.loadSongs(excludedFolders, manualScan)
            }
        } else {
            // Load YouTube recommendations when not using local songs
            viewModel.loadYouTubeRecommendations()
        }
    }

    LaunchedEffect(permissionState.isGranted, loadLocalSongs, excludedFolders, manualScan) {
        if (permissionState.isGranted && loadLocalSongs) {
            viewModel.loadSongs(excludedFolders, manualScan)
        }
    }
    
    // Video mode state
    val trendingVideos by viewModel.trendingVideos.collectAsState()
    val isVideoLoading by viewModel.isVideoLoading.collectAsState()
    val shortsFeed by viewModel.shortsFeed.collectAsState()
    
    // Load videos when video mode is enabled
    LaunchedEffect(videoMode) {
        if (videoMode) {
            viewModel.loadTrendingVideos()
        }
    }

    // Fetch the Shorts shelf when the user opts in mid-session (the load
    // itself also gates on the preference)
    LaunchedEffect(videoMode, shortsEnabled) {
        if (videoMode && shortsEnabled) {
            viewModel.loadShortsFeed()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Every tab's scroll position, remembered HERE rather than inside the tab
    // content, for two reasons.
    //
    // Anything remembered inside the AnimatedContent below is scoped to that
    // target's own composition and disposed once the transition settles, so a
    // state living down there is discarded on every tab switch - leave Home
    // halfway down, glance at Search, come back, and you are at the top. Above
    // the AnimatedContent the position survives.
    //
    // It also has to be reachable from the nav bar, so re-tapping the current
    // tab can send its list back to the top.
    //
    // One per tab AND per mode where the mode swaps the content: video Home and
    // music Home are different lists, and sharing a state between them would
    // restore one list's index into the other.
    val videoHomeScrollState = rememberLazyListState()
    val musicHomeScrollState = rememberLazyListState()
    val searchScrollState = rememberLazyListState()
    val subscriptionsScrollState = rememberLazyListState()
    val musicLibraryScrollState = rememberLazyListState()
    val videoLibraryScrollState = rememberLazyListState()

    // Which of the above the visible tab is currently driving.
    val currentTabScrollState = when (selectedTab) {
        0 -> if (videoMode) videoHomeScrollState else musicHomeScrollState
        1 -> searchScrollState
        2 -> if (videoMode) subscriptionsScrollState else musicLibraryScrollState
        else -> videoLibraryScrollState
    }

    // Lives outside the mode-swapped content so the thumb keeps animating
    // while the music/video home content cross-fades underneath it
    val modeToggleState = rememberMusicVideoToggleState(videoMode)

    // Handle back button to return to Home tab if on Search or Library
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    // The Subscriptions/History tabs (2/3) only exist in video mode
    LaunchedEffect(videoMode) {
        if (!videoMode && selectedTab > 2) selectedTab = 0
    }

    // Re-read the history when Home becomes the active tab. Playback writes an
    // entry only after 15s, so coming back from the player is exactly when the
    // rail has something new to show.
    LaunchedEffect(selectedTab, videoMode) {
        if (selectedTab == 0 && !videoMode) viewModel.refreshRecentlyPlayed()
    }

    // Auth Dialog State
    var showAuthDialog by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }

    // The avatar always opens the profile switcher now, signed in or not: with
    // device-only profiles there is always something to switch between, and
    // sending a signed-out user straight to a Google login was the app assuming
    // an account is the only way to have an identity.
    val onProfileClick: () -> Unit = { showAccountSheet = true }

    val backgroundColor = MaterialTheme.colorScheme.background
    
    // Loading state for playlist fetch
    var isPlaylistLoading by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Artist screen state (for navigation from player)
    var viewedArtistFromPlayer by remember { mutableStateOf<String?>(null) }
    
    // Update check state
    val updateRepository = remember { UpdateRepository() }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    // Held separately so the pill's label survives its exit animation
    var latestVersion by remember { mutableStateOf("") }

    // Check for updates on app launch (only for release builds)
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) {
            updateResult = updateRepository.checkForUpdate(
                repoPath = BuildConfig.GITHUB_REPO,
                currentVersion = BuildConfig.VERSION_NAME
            )
            (updateResult as? UpdateResult.UpdateAvailable)?.let {
                latestVersion = it.latestVersion
            }
        }
    }

    // How much clearance bottom-anchored UI needs above the nav bar inset to
    // stay clear of the floating overlays: the nav pill always, the music
    // pill (top edge at 180dp) and/or the video mini player (top edge at
    // 188dp, stacked to 284dp when the music pill is also alive). Animated so
    // FABs glide instead of jumping when a mini player appears.
    val musicPillVisible = currentSong != null
    val bottomOverlayInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = when {
            musicPillVisible && hasVideoMiniPlayer -> 284.dp
            hasVideoMiniPlayer -> 196.dp
            musicPillVisible -> 188.dp
            else -> 88.dp
        },
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bottomOverlayInset"
    )
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Scroll clearance for the tab lists (they don't apply insets themselves)
    val listBottomPadding = PaddingValues(bottom = bottomOverlayInset + navBarInset + 16.dp)

    // Use Box overlay instead of Scaffold for truly floating navbar
    androidx.compose.runtime.CompositionLocalProvider(
        com.ivor.ivormusic.ui.components.LocalBottomOverlayInset provides bottomOverlayInset
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Main content
        if (!loadLocalSongs || permissionState.isGranted) {
            androidx.compose.animation.AnimatedContent(
                targetState = selectedTab,
                label = "TabTransition",
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    if (direction > 0) {
                        // Moving forward (Right): New enters from Right, Old leaves to Left
                        (androidx.compose.animation.slideInHorizontally { width -> width } + 
                                androidx.compose.animation.fadeIn()) togetherWith
                                (androidx.compose.animation.slideOutHorizontally { width -> -width / 3 } + 
                                        androidx.compose.animation.fadeOut())
                    } else {
                        // Moving backward (Left): New enters from Left, Old leaves to Right
                        (androidx.compose.animation.slideInHorizontally { width -> -width / 3 } + 
                                androidx.compose.animation.fadeIn()) togetherWith
                                (androidx.compose.animation.slideOutHorizontally { width -> width } + 
                                        androidx.compose.animation.fadeOut())
                    }
                }
            ) { targetTab ->
                when (targetTab) {
                    0 -> {
                        // Mode swap morphs the page while the hoisted toggle
                        // thumb keeps sliding above it. Spec is read here because
                        // motionScheme is composable and transitionSpec is not.
                        val modeScaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                        androidx.compose.animation.AnimatedContent(
                            targetState = videoMode,
                            label = "ModeTransition",
                            transitionSpec = {
                                (androidx.compose.animation.fadeIn(
                                    androidx.compose.animation.core.tween(durationMillis = 260, delayMillis = 60)
                                ) + androidx.compose.animation.scaleIn(
                                    initialScale = 0.92f,
                                    animationSpec = modeScaleSpec
                                )) togetherWith (androidx.compose.animation.fadeOut(
                                    androidx.compose.animation.core.tween(durationMillis = 160)
                                ) + androidx.compose.animation.scaleOut(
                                    targetScale = 1.05f,
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 220)
                                ))
                            }
                        ) { videoModeContent ->
                            // Video Mode: Show video content
                            if (videoModeContent && localOnly) {
                                com.ivor.ivormusic.ui.components.LocalOnlyNotice(
                                    subtitle = "Video mode needs the internet. Turn off Local only in Settings to watch videos.",
                                    onOpenSettings = onNavigateToSettings
                                )
                            } else if (videoModeContent) {
                                VideoHomeContent(
                                    videos = trendingVideos,
                                    isLoading = isVideoLoading,
                                    onVideoClick = { video ->
                                        // Navigate to video player screen
                                        onNavigateToVideoPlayer(video)
                                    },
                                    shorts = if (shortsEnabled) shortsFeed else emptyList(),
                                    onShortClick = { index -> onOpenShorts(shortsFeed, index) },
                                    onProfileClick = onProfileClick,
                                    onSettingsClick = onNavigateToSettings,
                                    onDownloadsClick = onNavigateToDownloads,
                                    onRefresh = { viewModel.refreshVideos() },
                                    isDarkMode = isDarkMode,
                                    contentPadding = listBottomPadding,
                                    viewModel = viewModel,
                                    videoMode = videoMode,
                                    onVideoModeToggle = onVideoModeToggle,
                                    showModeToggle = showModeToggle,
                                    modeToggleState = modeToggleState,
                                    listState = videoHomeScrollState
                                )
                            }
                            // Music Mode: Show original content. The first load
                            // renders the real screen with placeholders in the
                            // data-backed sections rather than a full-screen
                            // spinner - the top bar, titles and nav have
                            // nothing to wait for.
                            else {
                                YourMixContent(
                                    songs = songs,
                                    isInitialLoading = isLoading && songs.isEmpty(),
                                    recentlyPlayed = recentlyPlayed,
                                    onRecentClick = { song ->
                                        // Resume from the history rail: the
                                        // recents are the queue, not the mix.
                                        playerViewModel.playQueue(recentlyPlayed, song)
                                        showPlayerSheet = true
                                    },
                                    onShowAllInLibrary = { selectedTab = 2 },
                                    onSongClick = { song ->
                                        playerViewModel.playQueue(songs, song)
                                        showPlayerSheet = true
                                    },
                                    onPlayClick = {
                                        if (songs.isNotEmpty()) {
                                            playerViewModel.playQueue(songs)
                                            showPlayerSheet = true
                                        }
                                    },
                                    onProfileClick = onProfileClick,
                                    onSettingsClick = onNavigateToSettings,
                                    onDownloadsClick = onNavigateToDownloads,
                                    isDarkMode = isDarkMode,
                                    contentPadding = listBottomPadding,
                                    viewModel = viewModel,
                                    excludedFolders = excludedFolders,
                                    manualScan = manualScan,
                                    videoMode = videoMode,
                                    onVideoModeToggle = onVideoModeToggle,
                                    showModeToggle = showModeToggle,
                                    modeToggleState = modeToggleState,
                                    listState = musicHomeScrollState
                                )
                            }
                        }
                    }
                    1 -> if (videoMode && localOnly) {
                        com.ivor.ivormusic.ui.components.LocalOnlyNotice(
                            subtitle = "Video search needs the internet. Turn off Local only in Settings to search videos.",
                            onOpenSettings = onNavigateToSettings
                        )
                    } else SearchContent(
                        songs = songs,
                        onSongClick = { song ->
                            // Fallback: Pass all songs to enable Next/Previous navigation
                            playerViewModel.playQueue(songs, song)
                            showPlayerSheet = true
                        },
                        onPlayQueue = { songList, song ->
                            // Use the visible song list (YouTube results or filtered local songs)
                            playerViewModel.playQueue(songList, song)
                            showPlayerSheet = true
                        },
                        onPlayRadio = { song ->
                            playerViewModel.playSongRadio(song)
                            showPlayerSheet = true
                        },
                        onVideoClick = { video ->
                            // Navigate to video player screen
                            onNavigateToVideoPlayer(video)
                        },
                        onProfileClick = onProfileClick,
                        contentPadding = listBottomPadding,
                        viewModel = viewModel,
                        isDarkMode = isDarkMode,
                        videoMode = videoMode,
                        localOnly = localOnly,
                        listState = searchScrollState
                    )
                    2 -> {
                        if (videoMode && localOnly) {
                            com.ivor.ivormusic.ui.components.LocalOnlyNotice(
                                subtitle = "Subscriptions need the internet. Turn off Local only in Settings to see them.",
                                onOpenSettings = onNavigateToSettings
                            )
                        } else if (videoMode) {
                            com.ivor.ivormusic.ui.video.SubscriptionsContent(
                                viewModel = viewModel,
                                onVideoClick = { video ->
                                    onNavigateToVideoPlayer(video)
                                },
                                onLoginClick = { showAuthDialog = true },
                                onManageSubscriptions = onNavigateToSubscriptions,
                                contentPadding = listBottomPadding,
                                feedListState = subscriptionsScrollState
                            )
                        } else {
                            LibraryContent(
                                songs = songs,
                                isLocalLibrary = loadLocalSongs,
                                onDownloadsClick = onNavigateToDownloads,
                                onSongClick = { song: Song ->
                                    // Pass all songs to enable Next/Previous navigation
                                    playerViewModel.playQueue(songs, song)
                                    showPlayerSheet = true
                                },
                                onPlaylistClick = { playlist: com.ivor.ivormusic.data.PlaylistDisplayItem ->
                                    // Optional: navigate to playlist detail or handled by parent
                                },
                                onPlayQueue = { songs: List<Song>, selectedSong: Song? ->
                                    playerViewModel.playQueue(songs, selectedSong)
                                    showPlayerSheet = true
                                },
                                contentPadding = listBottomPadding,
                                viewModel = viewModel,
                                isDarkMode = isDarkMode,
                                initialArtist = viewedArtistFromPlayer,
                                onInitialArtistConsumed = { viewedArtistFromPlayer = null },
                                onStatsClick = onNavigateToStats,
                                allSongsListState = musicLibraryScrollState
                            )
                        }
                    }
                    3 -> {
                        // Video mode only: Library (playlists, Watch Later,
                        // liked videos, watch history)
                        if (videoMode && localOnly) {
                            com.ivor.ivormusic.ui.components.LocalOnlyNotice(
                                subtitle = "The video library needs the internet. Turn off Local only in Settings to see it.",
                                onOpenSettings = onNavigateToSettings
                            )
                        } else if (videoMode) {
                            com.ivor.ivormusic.ui.video.VideoLibraryContent(
                                viewModel = viewModel,
                                onVideoClick = { video ->
                                    onNavigateToVideoPlayer(video)
                                },
                                onLoginClick = { showAuthDialog = true },
                                contentPadding = listBottomPadding,
                                rootListState = videoLibraryScrollState
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Permission required to load songs", color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
        
        // Playlist Loading Overlay
        if (isPlaylistLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // Block clicks
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White
                )
            }
        }
        
        // Floating Navigation bar - truly floating overlay using Material 3 Expressive HorizontalFloatingToolbar
        val navBarHaptics = LocalHapticFeedback.current
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            content = {
                val tabs = if (videoMode) listOf(
                    Triple(0, "Home", Pair(Icons.Rounded.Home, Icons.Outlined.Home)),
                    Triple(1, "Search", Pair(Icons.Filled.Search, Icons.Outlined.Search)),
                    Triple(2, "Subs", Pair(Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions)),
                    Triple(3, "Library", Pair(Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary))
                ) else listOf(
                    Triple(0, "Home", Pair(Icons.Rounded.Home, Icons.Outlined.Home)),
                    Triple(1, "Search", Pair(Icons.Filled.Search, Icons.Outlined.Search)),
                    Triple(2, "Library", Pair(Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic))
                )

                tabs.forEach { (index, label, icons) ->
                    val selected = selectedTab == index
                    val (filledIcon, outlinedIcon) = icons
                    
                    // fastSpatialSpec: snappy expressive motion — StiffnessLow
                    // springs took ~1s to settle and felt sluggish here.
                    val animatedPadding by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (selected) 20.dp else 12.dp,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "padding"
                    )
                    
                    val animatedContainerColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                        label = "containerColor"
                    )
                    
                    val animatedContentColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                        label = "contentColor"
                    )
                    
                    Surface(
                        selected = selected,
                        onClick = {
                            // A re-tap is a different gesture from a switch, and
                            // the two are told apart here.
                            if (selected) {
                                // Re-tap: back to the top, the thing every tab
                                // bar people use daily does. Only worth a tick
                                // when there is somewhere to go, otherwise
                                // tapping an already-topped tab buzzes for
                                // nothing.
                                if (currentTabScrollState.canScrollBackward) {
                                    navBarHaptics.performHapticFeedback(
                                        HapticFeedbackType.ContextClick
                                    )
                                    scope.launch { currentTabScrollState.scrollToTop() }
                                }
                            } else {
                                // Switching commits something the finger has no
                                // preview of, so the tick is the confirmation.
                                navBarHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                selectedTab = index
                            }
                        },
                        shape = CircleShape,
                        color = animatedContainerColor,
                        contentColor = animatedContentColor,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = animatedPadding)
                                .animateContentSize(
                                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                                ),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selected) filledIcon else outlinedIcon,
                                contentDescription = label,
                                modifier = Modifier.size(24.dp)
                            )
                            androidx.compose.animation.AnimatedVisibility(
                                visible = selected,
                                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(
                                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                                ),
                                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally(
                                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )

        // Expandable Player (Mini <-> Full Screen)
        ExpandablePlayer(
            isExpanded = showPlayerSheet,
            onExpandChange = { showPlayerSheet = it },
            currentSong = currentSong,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            playWhenReady = playWhenReady,
            progress = progressFraction,
            duration = playerViewModel.duration.collectAsState().value,
            onPlayPauseClick = { playerViewModel.togglePlayPause() },
            onNextClick = { playerViewModel.skipToNext() },
            viewModel = playerViewModel,
            ambientBackground = ambientBackground,
            artworkColors = playerArtworkColors,
            playerStyle = playerStyle,
            onPlayerStyleChange = onPlayerStyleChange,
            onArtistClick = { artistName ->
                // Collapse player and navigate to Library tab to show artist
                showPlayerSheet = false
                viewedArtistFromPlayer = artistName
                selectedTab = 2 // Library tab
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // Update available indicator. Anchored bottom-start above the floating
        // overlays (shared bottomOverlayInset) rather than the top bar, where it
        // used to sit on top of the settings/profile icons.
        androidx.compose.animation.AnimatedVisibility(
            visible = updateResult is UpdateResult.UpdateAvailable,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(
                initialScale = 0.8f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
            ),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(
                targetScale = 0.8f
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = bottomOverlayInset + 8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onNavigateToUpdate() },
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "v$latestVersion",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
    }

    // Auth Dialog
    if (showAuthDialog) {
        com.ivor.ivormusic.ui.auth.YouTubeAuthDialog(
            onDismiss = { showAuthDialog = false },
            onAuthSuccess = {
                showAuthDialog = false
                // Refresh login state, account info and the feeds so the UI
                // reflects the account immediately instead of after a restart
                viewModel.checkYouTubeConnection()
                if (videoMode) {
                    viewModel.loadTrendingVideos()
                    viewModel.loadYouTubeHistory()
                } else {
                    viewModel.loadYouTubeRecommendations()
                }
            }
        )
    }

    // The profile switcher. Replaces the old account sheet, whose "Switch
    // account" logged you out and made you sign in again - with a roster of
    // stored sessions, switching is instant and needs no network at all.
    if (showAccountSheet) {
        com.ivor.ivormusic.ui.account.AccountSwitcherSheet(
            onDismiss = { showAccountSheet = false },
            onAddYouTubeAccount = {
                // Google's login page auto-continues as whoever the WebView jar
                // already holds, so adding a second account without clearing it
                // silently hands back the first one. Stored sessions live in
                // EncryptedSharedPreferences and are untouched by this.
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
                showAuthDialog = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixContent(
    songs: List<Song>,
    /**
     * First load with nothing to show yet. The screen still renders in full;
     * only the sections that are actually waiting on data are replaced by
     * placeholders of the same size.
     */
    isInitialLoading: Boolean = false,
    /** Local play history for the "Jump back in" rail. Empty for a new user. */
    recentlyPlayed: List<Song> = emptyList(),
    onRecentClick: (Song) -> Unit = {},
    /**
     * Carousels on a vertically-scrolling page need a way to reach every item
     * without scrolling sideways; this backs the arrow button in each header.
     * The Library tab is that page - it renders the same [songs] list
     * vertically, plus its own recently-played rail.
     */
    onShowAllInLibrary: () -> Unit = {},
    onSongClick: (Song) -> Unit,
    onPlayClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    isDarkMode: Boolean,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    excludedFolders: Set<String> = emptySet(),
    manualScan: Boolean = false,
    videoMode: Boolean = false,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    modeToggleState: MusicVideoToggleState = rememberMusicVideoToggleState(videoMode),
    /** Hoisted by HomeScreen: survives tab switches, reachable by the nav bar. */
    listState: LazyListState = rememberLazyListState()
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground

    val isRefreshing by viewModel.isLoading.collectAsState()

    // One pulse shared by every placeholder on the screen, so they breathe in
    // step instead of twinkling independently.
    val skeletonAlpha = com.ivor.ivormusic.ui.components.rememberSkeletonAlpha()

    ExpressivePullToRefresh(
        // The refresh spinner is for a refresh the user asked for. On first
        // load the placeholders already say "loading", and showing both reads
        // as two competing indicators.
        isRefreshing = isRefreshing && !isInitialLoading,
        onRefresh = { viewModel.refresh(excludedFolders, manualScan) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
        ) {
            item { 
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                Box(Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    translationY = if (visible) 0f else -20f
                }.animateContentSize()) {
                    TopBarSection(onProfileClick = onProfileClick, onSettingsClick = onSettingsClick, onDownloadsClick = onDownloadsClick, isDarkMode = isDarkMode, viewModel = viewModel, videoMode = videoMode, onVideoModeToggle = onVideoModeToggle, showModeToggle = showModeToggle, modeToggleState = modeToggleState)
                }
            }
            
            item { 
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(100); visible = true }
                Box(Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    translationY = if (visible) 0f else 40f
                }) {
                    HeroSection(
                        songs = songs,
                        onPlayClick = onPlayClick,
                        isDarkMode = isDarkMode,
                        isLoading = isInitialLoading,
                        skeletonAlpha = skeletonAlpha
                    )
                }
            }

            item {
                if (isInitialLoading) {
                    OrganicSongLayoutSkeleton(skeletonAlpha = skeletonAlpha)
                } else if (songs.isNotEmpty()) {
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(200); visible = true }
                    Box(Modifier.graphicsLayer {
                        alpha = if (visible) 1f else 0f
                        scaleX = if (visible) 1f else 0.9f
                        scaleY = if (visible) 1f else 0.9f
                    }) {
                        OrganicSongLayout(songs = songs, onSongClick = onSongClick)
                    }
                }
            }
            
            item {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); visible = true }
                Column(Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    translationY = if (visible) 0f else 30f
                }) {
                    Spacer(modifier = Modifier.height(32.dp))
                    if (isInitialLoading) {
                        HomeCarouselSkeleton(
                            title = "Recent Albums",
                            itemWidth = 200.dp,
                            itemHeight = 240.dp,
                            skeletonAlpha = skeletonAlpha
                        )
                    } else {
                        RecentAlbumsSection(
                            songs = songs,
                            onSongClick = onSongClick,
                            isDarkMode = isDarkMode,
                            onShowAll = onShowAllInLibrary
                        )
                    }
                }
            }
            
            item {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(400); visible = true }
                Column(Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    translationY = if (visible) 0f else 30f
                }) {
                    if (isInitialLoading) {
                        Spacer(modifier = Modifier.height(24.dp))
                        HomeCarouselSkeleton(
                            title = "Jump back in",
                            itemWidth = 140.dp,
                            itemHeight = 140.dp,
                            captionLines = true,
                            skeletonAlpha = skeletonAlpha
                        )
                    } else if (recentlyPlayed.isNotEmpty()) {
                        // Nothing to resume for a brand new user, and an empty
                        // "Jump back in" is worse than no section at all.
                        Spacer(modifier = Modifier.height(24.dp))
                        JumpBackInSection(
                            songs = recentlyPlayed,
                            onSongClick = onRecentClick,
                            onShowAll = onShowAllInLibrary
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBarSection(
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    isDarkMode: Boolean,
    viewModel: HomeViewModel,
    videoMode: Boolean = false,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    modeToggleState: MusicVideoToggleState = rememberMusicVideoToggleState(videoMode)
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val iconColor = MaterialTheme.colorScheme.onSurface
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val context = androidx.compose.ui.platform.LocalContext.current

    val userAvatar by viewModel.userAvatar.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile avatar. Tap opens the switcher; long-press flips straight
        // back to the last profile, which is the whole point of a switcher for
        // someone bouncing between two accounts.
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        val accountSwitcher = remember(context) {
            com.ivor.ivormusic.data.AccountSwitcher(context)
        }
        val isSwitching by accountSwitcher.switching.collectAsState()
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(surfaceColor)
                .combinedClickable(
                    onClick = onProfileClick,
                    onLongClick = {
                        // A long-press that does nothing reads as broken, so
                        // this only fires when there is somewhere to go.
                        if (accountSwitcher.quickSwitchTarget() != null) {
                            haptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                            )
                            accountSwitcher.quickSwitch()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (userAvatar != null) {
                AsyncImage(
                    model = userAvatar,
                    contentDescription = "Profile",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            // Progress rides on the avatar rather than blocking the screen: the
            // switch itself is instant, but the feeds behind it are refetching,
            // and the status belongs where the user just tapped.
            androidx.compose.animation.AnimatedVisibility(
                visible = isSwitching,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Right side icons with shape morphing
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Downloads Button with badge if downloading
            Box {
                IconButton(
                    onClick = onDownloadsClick,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = containerColor,
                        contentColor = iconColor
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Download,
                        contentDescription = "Downloads",
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Show badge if downloads are active
                if (downloadingIds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            
            IconButton(
                onClick = onSettingsClick,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = containerColor,
                    contentColor = iconColor
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Music/Video mode switch, anchored in the corner so it stays put
            // when the home content swaps between modes. Can be hidden from
            // Settings (Home Screen Mode Toggle).
            if (showModeToggle) {
                MusicVideoToggle(
                    videoMode = videoMode,
                    onVideoModeChange = onVideoModeToggle,
                    state = modeToggleState
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeroSection(
    songs: List<Song>,
    onPlayClick: () -> Unit,
    isDarkMode: Boolean = true,
    /** First load: the artist line has no data yet, the rest of this is static. */
    isLoading: Boolean = false,
    skeletonAlpha: Float = com.ivor.ivormusic.ui.components.rememberSkeletonAlpha()
) {
    val firstSong = songs.firstOrNull()
    val secondSong = songs.getOrNull(1)
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left side - Title and subtitle
        Column {
            Text(
                text = "Your",
                style = MaterialTheme.typography.displayLarge,
                color = textColor
            )
            Text(
                text = "Mix",
                style = MaterialTheme.typography.displayLarge,
                color = textColor
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                // Same 20dp band the artist line occupies, so the title above
                // and the layout below do not shift when the names arrive.
                com.ivor.ivormusic.ui.components.SkeletonTextLine(
                    width = 168.dp,
                    height = 14.dp,
                    modifier = Modifier.padding(vertical = 3.dp),
                    alpha = skeletonAlpha
                )
            } else {
                Text(
                    text = (firstSong?.artist.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Unknown Artist").let { artist ->
                        (secondSong?.artist.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) })?.let { second -> "$artist, $second" } ?: artist
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        
        // Right side - Large Play button with shape morphing
        Box(modifier = Modifier.padding(top = 32.dp)) {
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                shapes = IconButtonDefaults.shapes(), // Enables shape morphing on press
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(IconButtonDefaults.largeIconSize)
                )
            }
        }
    }
}

/**
 * [OrganicSongLayout] with the artwork not yet loaded: the same rotated pill
 * and two circles, at the same sizes and offsets, so the collage does not
 * rearrange itself when the songs arrive.
 */
@Composable
private fun OrganicSongLayoutSkeleton(
    skeletonAlpha: Float
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
    ) {
        if (maxWidth <= 0.dp || maxHeight <= 0.dp) return@BoxWithConstraints

        val boxWidth = maxWidth
        val boxHeight = maxHeight

        com.ivor.ivormusic.ui.components.SkeletonBox(
            modifier = Modifier
                .width(260.dp)
                .height(500.dp)
                .align(Alignment.Center)
                .offset(x = 0.dp, y = 30.dp)
                .graphicsLayer { rotationZ = 30f },
            shape = RoundedCornerShape(50),
            alpha = skeletonAlpha
        )

        com.ivor.ivormusic.ui.components.SkeletonBox(
            modifier = Modifier
                .size(boxWidth * 0.29f)
                .align(Alignment.TopStart)
                .offset(x = boxWidth * 0.04f, y = boxHeight * 0.05f)
                .graphicsLayer { rotationZ = -10f },
            shape = CircleShape,
            alpha = skeletonAlpha
        )

        com.ivor.ivormusic.ui.components.SkeletonBox(
            modifier = Modifier
                .size(boxWidth * 0.26f)
                .align(Alignment.BottomEnd)
                .offset(x = boxWidth * (-0.05f), y = 0.dp)
                .graphicsLayer { rotationZ = 5f },
            shape = CircleShape,
            alpha = skeletonAlpha
        )
    }
}

/**
 * Stand-in for one of the home carousels. The section title is real - it never
 * depended on the data - and only the cards are placeholders.
 *
 * A plain Row rather than a carousel: the M3 carousels are driven by an item
 * count and a scroll state that would be thrown away a moment later, and the
 * user cannot meaningfully scroll placeholders anyway.
 */
@Composable
private fun HomeCarouselSkeleton(
    title: String,
    itemWidth: Dp,
    itemHeight: Dp,
    skeletonAlpha: Float,
    /** Quick Picks puts a title/artist under each card; Recent Albums does not. */
    captionLines: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), enabled = false)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(if (captionLines) 12.dp else 8.dp)
        ) {
            repeat(4) {
                Column(modifier = Modifier.width(itemWidth)) {
                    com.ivor.ivormusic.ui.components.SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        // Matches the M3 carousel item radius the real cards use
                        shape = RoundedCornerShape(28.dp),
                        alpha = skeletonAlpha
                    )
                    if (captionLines) {
                        Spacer(modifier = Modifier.height(10.dp))
                        com.ivor.ivormusic.ui.components.SkeletonTextLine(
                            width = itemWidth * 0.85f,
                            height = 12.dp,
                            alpha = skeletonAlpha
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        com.ivor.ivormusic.ui.components.SkeletonTextLine(
                            width = itemWidth * 0.55f,
                            height = 10.dp,
                            alpha = skeletonAlpha
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrganicSongLayout(
    songs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
    ) {
        // Guard against invalid dimensions during transitions
        if (maxWidth <= 0.dp || maxHeight <= 0.dp) {
            return@BoxWithConstraints
        }
        
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val context = androidx.compose.ui.platform.LocalContext.current
        
        // Circle sizes - percentage of screen width
        val circle1Size = boxWidth * 0.29f  // Top-left circle
        val circle2Size = boxWidth * 0.26f  // Bottom-right circle
        
        // Main: Large Pill shape - rotated diagonally right-to-left
        if (songs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(500.dp)
                    .align(Alignment.Center)
                    .offset(x = 0.dp, y = 30.dp)
                    .graphicsLayer { rotationZ = 30f }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onSongClick(songs[0]) },
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = songs[0].highResThumbnailUrl ?: songs[0].thumbnailUrl
                val localUri = songs[0].albumArtUri
                
                if (imageUrl != null || localUri != null) {
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(localUri ?: imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = songs[0].title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                )
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        // Circle 1 - Top Left (responsive size)
        if (songs.size > 1) {
            Box(
                modifier = Modifier
                    .size(circle1Size)
                    .align(Alignment.TopStart)
                    .offset(x = boxWidth * 0.04f, y = boxHeight * 0.05f)
                    .graphicsLayer { rotationZ = -10f }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onSongClick(songs[1]) },
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = songs[1].highResThumbnailUrl ?: songs[1].thumbnailUrl
                val localUri = songs[1].albumArtUri
                
                if (imageUrl != null || localUri != null) {
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(localUri ?: imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = songs[1].title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Circle 2 - Bottom Right (responsive size)
        if (songs.size > 2) {
            Box(
                modifier = Modifier
                    .size(circle2Size)
                    .align(Alignment.BottomEnd)
                    .offset(x = boxWidth * (-0.05f), y = boxHeight * (0.0f))
                    .graphicsLayer { rotationZ = 5f }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onSongClick(songs[2]) },
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = songs[2].highResThumbnailUrl ?: songs[2].thumbnailUrl
                val localUri = songs[2].albumArtUri
                
                if (imageUrl != null || localUri != null) {
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(localUri ?: imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = songs[2].title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SongStripCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val imageUrl = song.highResThumbnailUrl ?: song.thumbnailUrl
        val localUri = song.albumArtUri
        
        if (imageUrl != null || localUri != null) {
            coil.compose.SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(localUri ?: imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit = { _, song -> song?.let { onSongClick(it) } },
    onPlayRadio: (Song) -> Unit = { song -> onPlayQueue(listOf(song), song) },
    onVideoClick: (VideoItem) -> Unit = {},
    onProfileClick: () -> Unit = {},
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    videoMode: Boolean = false,
    localOnly: Boolean = false,
    /** Hoisted by HomeScreen: survives tab switches, reachable by the nav bar. */
    listState: LazyListState = rememberLazyListState()
) {
    var viewedPlaylist by remember { mutableStateOf<com.ivor.ivormusic.data.PlaylistDisplayItem?>(null) }
    var viewedArtist by remember { mutableStateOf<com.ivor.ivormusic.data.ArtistItem?>(null) }
    var viewedVideoPlaylist by remember { mutableStateOf<com.ivor.ivormusic.data.VideoPlaylist?>(null) }

    // Handle system back button for nested screens.
    // Playlist/album is the deepest layer (search → artist → album), so it
    // pops first; backing out of an album returns to the artist page.
    BackHandler(enabled = viewedPlaylist != null || viewedArtist != null || viewedVideoPlaylist != null) {
        when {
            viewedVideoPlaylist != null -> viewedVideoPlaylist = null
            viewedPlaylist != null -> viewedPlaylist = null
            viewedArtist != null -> viewedArtist = null
        }
    }

    val currentScreen = when {
        viewedVideoPlaylist != null -> "videoPlaylist"
        viewedPlaylist != null -> "playlist"
        viewedArtist != null -> "artist"
        else -> "search"
    }

    // Expressive motion physics for screen pushes/pops
    val searchNavSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val searchNavEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    androidx.compose.animation.AnimatedContent(
        targetState = currentScreen,
        label = "SearchNav",
        transitionSpec = {
            if (targetState != "search") {
                // Push (Going deeper)
                (androidx.compose.animation.slideInHorizontally(animationSpec = searchNavSpatialSpec) { width -> width } +
                        androidx.compose.animation.fadeIn(animationSpec = searchNavEffectsSpec)) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(animationSpec = searchNavSpatialSpec) { width -> -width / 3 } +
                                androidx.compose.animation.fadeOut(animationSpec = searchNavEffectsSpec))
            } else {
                // Pop (Going back)
                (androidx.compose.animation.slideInHorizontally(animationSpec = searchNavSpatialSpec) { width -> -width / 3 } +
                        androidx.compose.animation.fadeIn(animationSpec = searchNavEffectsSpec)) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(animationSpec = searchNavSpatialSpec) { width -> width } +
                                androidx.compose.animation.fadeOut(animationSpec = searchNavEffectsSpec))
            }
        }
    ) { screen ->
        when (screen) {
            "artist" -> {
                 viewedArtist?.let { artistItem ->
                    com.ivor.ivormusic.ui.artist.ArtistScreen(
                        artistName = artistItem.name,
                        artistId = artistItem.id,
                        songs = emptyList(), // We let the screen fetch songs via viewModel
                        onBack = { viewedArtist = null },
                        onPlayQueue = onPlayQueue,
                        onSongClick = onSongClick,
                        onAlbumClick = { album, albumSongs ->
                             // Optional: Handle playing album from artist screen
                             onPlayQueue(albumSongs, null)
                        },
                        onOpenAlbum = { albumItem -> viewedPlaylist = albumItem },
                        viewModel = viewModel
                    )
                }
            }

            "playlist" -> {
                 viewedPlaylist?.let { playlist ->
                    com.ivor.ivormusic.ui.library.PlaylistDetailScreen(
                        playlist = playlist,
                        onBack = { viewedPlaylist = null },
                        onPlayQueue = onPlayQueue,
                        viewModel = viewModel
                    )
                }
            }

            "videoPlaylist" -> {
                viewedVideoPlaylist?.let { playlist ->
                    com.ivor.ivormusic.ui.video.VideoPlaylistDetail(
                        playlist = playlist,
                        viewModel = viewModel,
                        onVideoClick = onVideoClick,
                        onBack = { viewedVideoPlaylist = null },
                        contentPadding = contentPadding,
                        // Search results aren't the user's own playlists
                        allowRemove = false
                    )
                }
            }
            else -> {
                com.ivor.ivormusic.ui.search.SearchScreen(
                    songs = songs,
                    onSongClick = onSongClick,
                    onPlayQueue = onPlayQueue,
                    onPlayRadio = onPlayRadio,
                    onVideoClick = onVideoClick,
                    onArtistClick = { artistItem -> viewedArtist = artistItem },
                    onAlbumClick = { albumItem -> viewedPlaylist = albumItem },
                    onPlaylistClick = { playlistItem -> viewedPlaylist = playlistItem },
                    onVideoPlaylistClick = { videoPlaylist ->
                        viewModel.loadPlaylistVideos(videoPlaylist.playlistId)
                        viewedVideoPlaylist = videoPlaylist
                    },
                    onProfileClick = onProfileClick,
                    contentPadding = contentPadding,
                    viewModel = viewModel,
                    isDarkMode = isDarkMode,
                    videoMode = videoMode,
                    localOnly = localOnly,
                    listState = listState
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentAlbumsSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    isDarkMode: Boolean = true,
    onShowAll: (() -> Unit)? = null
) {
    if (songs.isEmpty()) return

    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // We need at least one large, one medium, one small for full effect,
    // but the component handles fewer items gracefully.
    val state = rememberCarouselState { songs.size }

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(title = "Recent Albums", onShowAll = onShowAll)

        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 200.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) { index ->
            val song = songs[index]
            Box(
                modifier = Modifier
                    // 28dp is the M3 carousel item radius; shapes.medium (12dp)
                    // made these read as generic cards.
                    .maskClip(RoundedCornerShape(28.dp))
                    .background(cardBgColor)
                    .clickable { onSongClick(song) }
            ) {
                if (song.albumArtUri != null || song.thumbnailUrl != null) {
                    AsyncImage(
                        model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section header for a horizontal shelf: the title, plus the arrow button
 * Material requires so every item is reachable without scrolling sideways.
 *
 * [onShowAll] is nullable because the arrow is only honest when there is
 * somewhere fuller to go.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeSectionHeader(
    title: String,
    onShowAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onShowAll != null) {
            // Default size on purpose: M3's own 40dp container carries a 48dp
            // touch target, and pinning it smaller would break that.
            FilledTonalIconButton(
                onClick = onShowAll,
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Show all $title",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Square artwork edge, and the rail's item width. */
private val ARTWORK_SIZE = 140.dp

/** Space between artwork and the first caption line. */
private val CAPTION_GAP = 8.dp

/**
 * The user's own play history, newest first - the "resume what you were doing"
 * rail. Distinct from the mix above it on purpose: this is the one section on
 * the screen that is not a recommendation.
 *
 * A LazyRow of cards, not a carousel. Both M3 carousel layouts mask their items
 * to a shrinking rect at the container edges, and that mask covers the whole
 * item - so captions under the artwork get sliced mid-word ("Let Down" renders
 * as "et Down"). Material's own guidance points here: if carousel items need
 * real text, use a series of cards instead. Recent Albums above keeps the
 * carousel because its items are pure artwork with nothing to clip.
 */
@Composable
fun JumpBackInSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onShowAll: (() -> Unit)? = null
) {
    if (songs.isEmpty()) return

    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(title = "Jump back in", onShowAll = onShowAll)

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(songs, key = { "recent_${it.id}" }) { song ->
                // No clip on this column: a rounded clip here is what rounds
                // the corners off the caption text underneath the artwork.
                // Only the artwork itself gets a shape.
                Column(
                    modifier = Modifier
                        .width(ARTWORK_SIZE)
                        .clickable { onSongClick(song) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(ARTWORK_SIZE)
                            .clip(RoundedCornerShape(28.dp))
                            .background(cardBgColor)
                    ) {
                        if (song.albumArtUri != null || song.thumbnailUrl != null) {
                            AsyncImage(
                                model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                                contentDescription = song.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(CAPTION_GAP))

                    // No fixed row height: the column wraps its content, so the
                    // captions grow with the user's font scale instead of being
                    // cut off by a hardcoded carousel height.
                    Text(
                        text = song.title.takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Untitled Song",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                    Text(
                        text = song.artist.takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Unknown Artist",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}



