package com.ivor.ivormusic.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.ui.components.FloatingPillNavBar
import com.ivor.ivormusic.ui.components.MusicVideoToggle
import com.ivor.ivormusic.ui.components.MusicVideoToggleState
import com.ivor.ivormusic.ui.components.rememberMusicVideoToggleState
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

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToVideoPlayer: (VideoItem) -> Unit = {},
    onOpenShorts: (List<com.ivor.ivormusic.data.ShortsItem>, Int) -> Unit = { _, _ -> },
    shortsEnabled: Boolean = false,
    loadLocalSongs: Boolean = true,
    excludedFolders: Set<String> = emptySet(),
    ambientBackground: Boolean = true,
    playerArtworkColors: Boolean = false,
    videoMode: Boolean = false,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    playerStyle: PlayerStyle = PlayerStyle.CLASSIC,
    onPlayerStyleChange: (PlayerStyle) -> Unit = {},
    manualScan: Boolean = false,
    localOnly: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localSongs by viewModel.songs.collectAsState()
    val youtubeSongs by viewModel.youtubeSongs.collectAsState()
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    
    // Use local songs or YouTube songs (which includes fallback search results if not logged in)
    val songs = if (loadLocalSongs) localSongs else youtubeSongs
    
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
            if (!permissionState.status.isGranted) {
                permissionState.launchPermissionRequest()
            } else {
                viewModel.loadSongs(excludedFolders, manualScan)
            }
        } else {
            // Load YouTube recommendations when not using local songs
            viewModel.loadYouTubeRecommendations()
        }
    }

    LaunchedEffect(permissionState.status.isGranted, loadLocalSongs, excludedFolders, manualScan) {
        if (permissionState.status.isGranted && loadLocalSongs) {
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

    // Auth Dialog State
    var showAuthDialog by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }

    // When logged in the profile button shows the account sheet, not the login dialog
    val onProfileClick: () -> Unit = {
        if (isYouTubeConnected) showAccountSheet = true else showAuthDialog = true
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    
    // Loading state for playlist fetch
    var isPlaylistLoading by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Artist screen state (for navigation from player)
    var viewedArtistFromPlayer by remember { mutableStateOf<String?>(null) }
    
    // Update check state
    val updateRepository = remember { UpdateRepository() }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    
    // Check for updates on app launch (only for release builds)
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) {
            updateResult = updateRepository.checkForUpdate(
                repoPath = BuildConfig.GITHUB_REPO,
                currentVersion = BuildConfig.VERSION_NAME
            )
        }
    }

    // Use Box overlay instead of Scaffold for truly floating navbar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Main content
        if (!loadLocalSongs || permissionState.status.isGranted) {
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
                                    contentPadding = PaddingValues(bottom = 160.dp),
                                    viewModel = viewModel,
                                    videoMode = videoMode,
                                    onVideoModeToggle = onVideoModeToggle,
                                    showModeToggle = showModeToggle,
                                    modeToggleState = modeToggleState
                                )
                            }
                            // Music Mode: Show original content
                            else if (isLoading && songs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                YourMixContent(
                                    songs = songs,
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
                                    contentPadding = PaddingValues(bottom = 160.dp), // Space for navbar + miniplayer
                                    viewModel = viewModel,
                                    excludedFolders = excludedFolders,
                                    manualScan = manualScan,
                                    videoMode = videoMode,
                                    onVideoModeToggle = onVideoModeToggle,
                                    showModeToggle = showModeToggle,
                                    modeToggleState = modeToggleState
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
                        onVideoClick = { video ->
                            // Navigate to video player screen
                            onNavigateToVideoPlayer(video)
                        },
                        onProfileClick = onProfileClick,
                        contentPadding = PaddingValues(bottom = 160.dp),
                        viewModel = viewModel,
                        isDarkMode = isDarkMode,
                        videoMode = videoMode,
                        localOnly = localOnly
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
                                contentPadding = PaddingValues(bottom = 160.dp)
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
                                contentPadding = PaddingValues(bottom = 160.dp),
                                viewModel = viewModel,
                                isDarkMode = isDarkMode,
                                initialArtist = viewedArtistFromPlayer,
                                onInitialArtistConsumed = { viewedArtistFromPlayer = null },
                                onStatsClick = onNavigateToStats
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
                                contentPadding = PaddingValues(bottom = 160.dp)
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
                        onClick = { selectedTab = index },
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
        
        // Update available indicator
        if (updateResult is UpdateResult.UpdateAvailable) {
            val update = updateResult as UpdateResult.UpdateAvailable
            Surface(
                modifier = Modifier
                    .padding(top = 16.dp, end = 20.dp)
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp) // Below profile icon
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
                        text = "v${update.latestVersion}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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

    // Account sheet (shown when tapping the avatar while logged in)
    if (showAccountSheet) {
        val userAvatar by viewModel.userAvatar.collectAsState()
        val userName by viewModel.userName.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (userAvatar != null) {
                        coil.compose.AsyncImage(
                            model = userAvatar,
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = userName ?: "YouTube Account",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = {
                        showAccountSheet = false
                        viewModel.logout()
                        if (videoMode) {
                            viewModel.loadTrendingVideos()
                            viewModel.loadYouTubeHistory()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Log out")
                }

                TextButton(
                    onClick = {
                        showAccountSheet = false
                        viewModel.logout()
                        showAuthDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Switch account")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixContent(
    songs: List<Song>,
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
    modeToggleState: MusicVideoToggleState = rememberMusicVideoToggleState(videoMode)
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    
    val isRefreshing by viewModel.isLoading.collectAsState()
    
    ExpressivePullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh(excludedFolders, manualScan) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
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
                    HeroSection(songs = songs, onPlayClick = onPlayClick, isDarkMode = isDarkMode)
                }
            }
            
            item {
                if (songs.isNotEmpty()) {
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
                    RecentAlbumsSection(songs = songs, onSongClick = onSongClick, isDarkMode = isDarkMode)
                }
            }
            
            item {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(400); visible = true }
                Column(Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    translationY = if (visible) 0f else 30f
                }) {
                    Spacer(modifier = Modifier.height(24.dp))
                    QuickPicksSection(songs = songs, onSongClick = onSongClick, isDarkMode = isDarkMode)
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
    
    val userAvatar by viewModel.userAvatar.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(surfaceColor)
                .clickable(onClick = onProfileClick),
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
    isDarkMode: Boolean = true
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
    onVideoClick: (VideoItem) -> Unit = {},
    onProfileClick: () -> Unit = {},
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    videoMode: Boolean = false,
    localOnly: Boolean = false
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
                    localOnly = localOnly
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
    isDarkMode: Boolean = true
) {
    if (songs.isEmpty()) return
    
    val textColor = MaterialTheme.colorScheme.onSurface
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    
    // We need at least one large, one medium, one small for full effect,
    // but the component handles fewer items gracefully.
    val state = rememberCarouselState { songs.size }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recent Albums",
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        
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
                    .maskClip(MaterialTheme.shapes.medium)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickPicksSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    isDarkMode: Boolean = true
) {
    if (songs.isEmpty()) return
    
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    
    val state = rememberCarouselState { songs.size }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Quick Picks",
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        
        HorizontalUncontainedCarousel(
            state = state,
            itemWidth = 140.dp,
            itemSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) { index ->
            val song = songs[index]
            Column(
                 modifier = Modifier
                    .width(140.dp)
                    .clickable { onSongClick(song) }
            ) {
                // Song Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
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
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Song Title
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Untitled Song",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                  Text(
                    text = song.artist.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Unknown Artist",
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor
                )
            }
        }
    }
}



