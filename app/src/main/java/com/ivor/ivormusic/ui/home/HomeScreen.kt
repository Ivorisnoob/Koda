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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Movie
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.ivor.ivormusic.ui.library.songRowClick
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.data.AppMode
import com.ivor.ivormusic.ui.components.AppModeToggle
import com.ivor.ivormusic.ui.components.AppModeToggleState
import com.ivor.ivormusic.ui.components.rememberAppModeToggleState
import com.ivor.ivormusic.ui.tv.TvPlaceholder
import com.ivor.ivormusic.ui.tv.TvPlaceholderTab
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
import androidx.compose.ui.res.stringResource
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
    /** Play completed video downloads as a local queue, without URL resolution. */
    onPlayDownloadedVideos: (
        List<com.ivor.ivormusic.data.DownloadedVideo>,
        com.ivor.ivormusic.data.DownloadedVideo
    ) -> Unit = { _, _ -> },
    /**
     * Open the video player on a whole playlist rather than one video, so the
     * rest of it plays after this one and is browsable from the player.
     *
     * Nullable rather than defaulting to `{}`: the playlist screens fall back to
     * [onNavigateToVideoPlayer] when this is absent, and a do-nothing default
     * would make a tap on a playlist row silently do nothing at all.
     */
    /**
     * Queue a video from a long press, either next or at the end. Routed to the
     * video player's ViewModel, which owns the queue.
     */
    onEnqueueVideo: ((com.ivor.ivormusic.data.VideoItem, Boolean) -> Unit)? = null,
    onPlayVideoQueue: ((com.ivor.ivormusic.data.VideoQueue) -> Unit)? = null,
    onOpenShorts: (List<com.ivor.ivormusic.data.ShortsItem>, Int) -> Unit = { _, _ -> },
    /**
     * Open a creator's channel page. Threaded down to every surface that shows
     * a channel name, so tapping one means the same thing everywhere.
     */
    onOpenChannel: (String) -> Unit = {},
    shortsEnabled: Boolean = false,
    loadLocalSongs: Boolean = false,
    excludedFolders: Set<String> = emptySet(),
    ambientBackground: Boolean = true,
    playerArtworkColors: Boolean = true,
    appMode: AppMode = AppMode.MUSIC,
    onAppModeChange: (AppMode) -> Unit = {},
    showModeToggle: Boolean = true,
    playerStyle: PlayerStyle = PlayerStyle.EDITORIAL,
    onPlayerStyleChange: (PlayerStyle) -> Unit = {},
    manualScan: Boolean = false,
    localOnly: Boolean = false,
    hasVideoMiniPlayer: Boolean = false,
    /** Spotlight: the alternative music Home. Off by default. */
    spotlightHome: Boolean = false,
    /** Use Material 3's compact bar instead of the default floating toolbar. */
    nonExpressiveNavigationBar: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val homePreferences = remember(context) { com.ivor.ivormusic.data.ThemePreferences(context) }
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
    val isVideoHomeOffline by viewModel.isVideoHomeOffline.collectAsState()
    val downloadedVideos by viewModel.downloadedVideos.collectAsState()
    val shortsFeed by viewModel.shortsFeed.collectAsState()
    
    // Load videos when video mode is enabled
    LaunchedEffect(appMode) {
        if (appMode.isVideo) {
            viewModel.loadTrendingVideos()
        }
    }

    // Fetch the Home-only Shorts shelf when the user opts in mid-session (the
    // load itself also gates on the preference).
    LaunchedEffect(appMode, shortsEnabled) {
        if (appMode.isVideo && shortsEnabled) {
            viewModel.loadShortsFeed()
        }
    }

    // Saveable, not just remembered. Opening a channel page is a real
    // navigation, so this composable is disposed and restored on the way back,
    // and a plainly-remembered tab index would drop the user on Home every time
    // they looked at a creator from the Subscriptions feed. The scroll states
    // below already survive it, because rememberLazyListState is saveable.
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable(appMode) {
        mutableIntStateOf(homePreferences.getLastHomeTab(appMode))
    }

    LaunchedEffect(selectedTab, appMode) {
        homePreferences.setLastHomeTab(appMode, selectedTab)
    }

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
    val tvHomeScrollState = rememberLazyListState()
    val tvSearchScrollState = rememberLazyListState()
    val tvLibraryScrollState = rememberLazyListState()

    // Which of the above the visible tab is currently driving.
    val currentTabScrollState = when (selectedTab) {
        0 -> when (appMode) {
            AppMode.VIDEO -> videoHomeScrollState
            AppMode.TV -> tvHomeScrollState
            AppMode.MUSIC -> musicHomeScrollState
        }
        1 -> if (appMode.isTv) tvSearchScrollState else searchScrollState
        2 -> when (appMode) {
            AppMode.VIDEO -> subscriptionsScrollState
            AppMode.TV -> tvLibraryScrollState
            AppMode.MUSIC -> musicLibraryScrollState
        }
        else -> videoLibraryScrollState
    }

    // Lives outside the mode-swapped content so the thumb keeps animating
    // while the music/video home content cross-fades underneath it
    val modeToggleState = rememberAppModeToggleState(appMode)

    // Handle back button to return to Home tab if on Search or Library
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    // Tab sets differ per mode (video has a fourth), so a mode change can
    // leave selectedTab pointing past the end of the new set.
    LaunchedEffect(appMode) {
        if (selectedTab > appMode.lastTabIndex) selectedTab = 0
    }

    // Re-read the history when Home becomes the active tab. Playback writes an
    // entry only after 15s, so coming back from the player is exactly when the
    // rail has something new to show.
    LaunchedEffect(selectedTab, appMode) {
        if (selectedTab == 0 && appMode.isMusic) viewModel.refreshRecentlyPlayed()
    }

    // Auth Dialog State
    var showAuthDialog by remember { mutableStateOf(false) }
    var addAuthAsNewProfile by remember { mutableStateOf(false) }
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

    // The same hand-off, asked for from outside this screen entirely: the
    // "Open music artist page" cross-link on a creator's channel page pops back
    // to home and leaves the request on the ViewModel, because a NavHost
    // destination cannot reach the tab state that lives in here.
    val pendingArtistPage by viewModel.pendingArtistPage.collectAsState()
    LaunchedEffect(pendingArtistPage) {
        pendingArtistPage?.let { artist ->
            // The artist page is the music-mode view of this creator, and tab 2
            // is video history while the video toggle is on. Asking for it is
            // therefore asking to be in music mode; leaving the toggle alone
            // would land on the wrong tab and look like the link did nothing.
            if (!appMode.isMusic) onAppModeChange(AppMode.MUSIC)
            viewedArtistFromPlayer = artist
            selectedTab = 2
            viewModel.consumeArtistPageRequest()
        }
    }
    // Set by Spotlight's shortcut grid and shelves; consumed by LibraryContent
    // as soon as the tab renders, so returning to Library later lands on the
    // list rather than re-opening the playlist.
    var viewedPlaylistFromHome by remember {
        mutableStateOf<com.ivor.ivormusic.data.PlaylistDisplayItem?>(null)
    }
    // The video-mode counterpart, handed to VideoLibraryContent the same way.
    var viewedVideoPlaylistFromHome by remember {
        mutableStateOf<com.ivor.ivormusic.data.VideoPlaylist?>(null)
    }

    // A playlist link shared or opened into the app, which lands at
    // MainActivity and cannot reach the tab state from there. Same hand-off as
    // pendingArtistPage, and it sets the mode for the same reason: the Library
    // that can show this playlist only exists on one side of the video toggle,
    // so leaving the toggle alone would land on the wrong tab and read as the
    // share having done nothing.
    val pendingPlaylistPage by viewModel.pendingPlaylistPage.collectAsState()
    LaunchedEffect(pendingPlaylistPage) {
        pendingPlaylistPage?.let { playlist ->
            if (!appMode.isMusic) onAppModeChange(AppMode.MUSIC)
            viewedPlaylistFromHome = playlist
            selectedTab = 2
            viewModel.consumePlaylistPageRequest()
        }
    }
    val pendingVideoPlaylistPage by viewModel.pendingVideoPlaylistPage.collectAsState()
    LaunchedEffect(pendingVideoPlaylistPage) {
        pendingVideoPlaylistPage?.let { playlist ->
            if (!appMode.isVideo) onAppModeChange(AppMode.VIDEO)
            viewedVideoPlaylistFromHome = playlist
            // Video mode's Library is tab 3; music's is tab 2.
            selectedTab = 3
            viewModel.consumeVideoPlaylistPageRequest()
        }
    }
    // Long-pressed song, for the options sheet. Hosted here, once, rather than
    // per screen: the sheet acts on the PlayerViewModel, which lives at this
    // level, and the Library's sub-routes would each otherwise need their own.
    var songOptionsTarget by remember { mutableStateOf<Song?>(null) }

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
    // The standard non-expressive NavigationBar is 80dp tall. The expressive
    // toolbar occupies 84dp including its bottom breathing room. Keep the same
    // clearance above either variant so overlaid controls do not jump or
    // collide when this preference changes.
    val navigationOverlayInset = if (nonExpressiveNavigationBar) 84.dp else 88.dp
    val bottomOverlayInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = when {
            musicPillVisible && hasVideoMiniPlayer -> navigationOverlayInset + 196.dp
            hasVideoMiniPlayer -> navigationOverlayInset + 108.dp
            musicPillVisible -> navigationOverlayInset + 100.dp
            else -> navigationOverlayInset
        },
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bottomOverlayInset"
    )
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The one inset every tab list scrolls inside, top and bottom. It belongs
    // in each list's contentPadding and never on a parent modifier: padding a
    // scroll container shrinks its viewport, so content is clipped at the
    // status bar instead of passing under it, and the top of the screen reads
    // as a system title bar. The bottom already worked this way.
    val listContentPadding = PaddingValues(
        top = statusBarInset,
        bottom = bottomOverlayInset + navBarInset + 16.dp
    )

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
                            targetState = appMode,
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
                        ) { modeContent ->
                            // TV mode is not account-derived and has no
                            // local-only variant: nothing it shows comes from
                            // YouTube, so the notice the other two modes need
                            // would be answering a question TV never asks.
                            if (modeContent.isTv) {
                                TvPlaceholder(
                                    tab = TvPlaceholderTab.HOME,
                                    contentPadding = listContentPadding
                                )
                            }
                            // Video Mode: Show video content
                            else if (modeContent.isVideo && localOnly) {
                                com.ivor.ivormusic.ui.components.LocalOnlyNotice(
                                    subtitle = stringResource(R.string.local_only_video_mode_subtitle),
                                    onOpenSettings = onNavigateToSettings
                                )
                            } else if (modeContent.isVideo) {
                                VideoHomeContent(
                                    videos = trendingVideos,
                                    isLoading = isVideoLoading,
                                    isOffline = isVideoHomeOffline,
                                    downloadedVideos = downloadedVideos,
                                    onVideoClick = { video ->
                                        // Navigate to video player screen
                                        onNavigateToVideoPlayer(video)
                                    },
                                    onDownloadedVideoClick = { video ->
                                        onPlayDownloadedVideos(downloadedVideos, video)
                                    },
                                    shorts = if (shortsEnabled) shortsFeed else emptyList(),
                                    onShortClick = { index -> onOpenShorts(shortsFeed, index) },
                                    // Without this the long-press sheet loses
                                    // its whole queue section, on the one feed
                                    // people spend the most time in.
                                    onEnqueueVideo = onEnqueueVideo,
                                    onOpenChannel = onOpenChannel,
                                    onProfileClick = onProfileClick,
                                    onSettingsClick = onNavigateToSettings,
                                    onDownloadsClick = onNavigateToDownloads,
                                    onRefresh = { viewModel.refreshVideos() },
                                    isDarkMode = isDarkMode,
                                    contentPadding = listContentPadding,
                                    viewModel = viewModel,
                                    appMode = appMode,
                                    onAppModeChange = onAppModeChange,
                                    showModeToggle = showModeToggle,
                                    modeToggleState = modeToggleState,
                                    listState = videoHomeScrollState
                                )
                            }
                            // Music Mode, Spotlight: the shortcut-grid and
                            // shelves alternative. Same flows, same overlays,
                            // same tab system - only the composition of this one
                            // tab differs, which is the move the video toggle
                            // already established.
                            else if (spotlightHome) {
                                val spotlightPlaylists by viewModel.userPlaylists.collectAsState()
                                val spotlightLiked by viewModel.likedSongs.collectAsState()
                                SpotlightHomeContent(
                                    songs = songs,
                                    recentlyPlayed = recentlyPlayed,
                                    likedSongs = spotlightLiked,
                                    playlists = spotlightPlaylists,
                                    isInitialLoading = isLoading && songs.isEmpty(),
                                    onSongLongPress = { song -> songOptionsTarget = song },
                                    onSongClick = { song ->
                                        playerViewModel.playQueue(songs, song)
                                        showPlayerSheet = true
                                    },
                                    onPlaySongs = { queue, start ->
                                        playerViewModel.playQueue(queue, start)
                                        showPlayerSheet = true
                                    },
                                    onRecentClick = { song ->
                                        playerViewModel.playQueue(recentlyPlayed, song)
                                        showPlayerSheet = true
                                    },
                                    // Playlist detail lives in LibraryContent, so
                                    // Spotlight hands the playlist over and
                                    // switches tab: the Library opens straight
                                    // onto it, the same deep-link shape the
                                    // player already uses for artists.
                                    onPlaylistClick = { playlist ->
                                        viewedPlaylistFromHome = playlist
                                        selectedTab = 2
                                    },
                                    onOpenLiked = { selectedTab = 2 },
                                    onShowAllInLibrary = { selectedTab = 2 },
                                    onProfileClick = onProfileClick,
                                    onSettingsClick = onNavigateToSettings,
                                    onDownloadsClick = onNavigateToDownloads,
                                    isDarkMode = isDarkMode,
                                    contentPadding = listContentPadding,
                                    viewModel = viewModel,
                                    excludedFolders = excludedFolders,
                                    manualScan = manualScan,
                                    appMode = appMode,
                                    onAppModeChange = onAppModeChange,
                                    showModeToggle = showModeToggle,
                                    modeToggleState = modeToggleState,
                                    listState = musicHomeScrollState
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
                                    onSongLongPress = { song -> songOptionsTarget = song },
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
                                    contentPadding = listContentPadding,
                                    viewModel = viewModel,
                                    excludedFolders = excludedFolders,
                                    manualScan = manualScan,
                                    appMode = appMode,
                                    onAppModeChange = onAppModeChange,
                                    showModeToggle = showModeToggle,
                                    modeToggleState = modeToggleState,
                                    listState = musicHomeScrollState
                                )
                            }
                        }
                    }
                    1 -> if (appMode.isTv) {
                        TvPlaceholder(
                            tab = TvPlaceholderTab.SEARCH,
                            contentPadding = listContentPadding
                        )
                    } else if (appMode.isVideo && localOnly) {
                        com.ivor.ivormusic.ui.components.LocalOnlyNotice(
                            subtitle = stringResource(R.string.local_only_video_search_subtitle),
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
                        onPlayVideoQueue = onPlayVideoQueue,
                        onEnqueueVideo = onEnqueueVideo,
                        onProfileClick = onProfileClick,
                        onOpenChannel = onOpenChannel,
                        onSongLongPress = { song -> songOptionsTarget = song },
                        contentPadding = listContentPadding,
                        viewModel = viewModel,
                        isDarkMode = isDarkMode,
                        videoMode = appMode.isVideo,
                        localOnly = localOnly,
                        listState = searchScrollState
                    )
                    2 -> {
                        if (appMode.isTv) {
                            TvPlaceholder(
                                tab = TvPlaceholderTab.LIBRARY,
                                contentPadding = listContentPadding
                            )
                        } else if (appMode.isVideo && localOnly) {
                            com.ivor.ivormusic.ui.components.LocalOnlyNotice(
                                subtitle = stringResource(R.string.local_only_subscriptions_subtitle),
                                onOpenSettings = onNavigateToSettings
                            )
                        } else if (appMode.isVideo) {
                            com.ivor.ivormusic.ui.video.SubscriptionsContent(
                                viewModel = viewModel,
                                onEnqueueVideo = onEnqueueVideo,
                                onVideoClick = { video ->
                                    onNavigateToVideoPlayer(video)
                                },
                                onLoginClick = { showAuthDialog = true },
                                onOpenChannel = onOpenChannel,
                                onManageSubscriptions = onNavigateToSubscriptions,
                                contentPadding = listContentPadding,
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
                                contentPadding = listContentPadding,
                                viewModel = viewModel,
                                isDarkMode = isDarkMode,
                                initialArtist = viewedArtistFromPlayer,
                                onInitialArtistConsumed = { viewedArtistFromPlayer = null },
                                initialPlaylist = viewedPlaylistFromHome,
                                onInitialPlaylistConsumed = { viewedPlaylistFromHome = null },
                                onStatsClick = onNavigateToStats,
                                onOpenChannel = onOpenChannel,
                                onSongLongPress = { song -> songOptionsTarget = song },
                                allSongsListState = musicLibraryScrollState
                            )
                        }
                    }
                    3 -> {
                        // Video mode only: Library (playlists, Watch Later,
                        // liked videos, watch history)
                        if (appMode.isVideo && localOnly) {
                            com.ivor.ivormusic.ui.components.LocalOnlyNotice(
                                subtitle = stringResource(R.string.local_only_video_library_subtitle),
                                onOpenSettings = onNavigateToSettings
                            )
                        } else if (appMode.isVideo) {
                            com.ivor.ivormusic.ui.video.VideoLibraryContent(
                                viewModel = viewModel,
                                onOpenChannel = onOpenChannel,
                                onVideoClick = { video ->
                                    onNavigateToVideoPlayer(video)
                                },
                                onPlayQueue = onPlayVideoQueue,
                                onLoginClick = { showAuthDialog = true },
                                contentPadding = listContentPadding,
                                onEnqueueVideo = onEnqueueVideo,
                                initialPlaylist = viewedVideoPlaylistFromHome,
                                onInitialPlaylistConsumed = { viewedVideoPlaylistFromHome = null },
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
                    Text(stringResource(R.string.permission_required_title), color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionState.launchPermissionRequest() }) {
                        Text(stringResource(R.string.action_grant_permission))
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
        
        // Both navigation variants use the same destinations and interaction
        // contract. Only their Material container and item presentation differ.
        val navBarHaptics = com.ivor.ivormusic.util.rememberKodaHaptics()
        val homeTab = Triple(0, stringResource(R.string.tab_home), Pair(Icons.Rounded.Home, Icons.Outlined.Home))
        val searchTab = Triple(1, stringResource(R.string.tab_search), Pair(Icons.Filled.Search, Icons.Outlined.Search))
        val navTabs = when (appMode) {
            AppMode.VIDEO -> listOf(
                homeTab,
                searchTab,
                Triple(2, stringResource(R.string.tab_subs), Pair(Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions)),
                Triple(3, stringResource(R.string.tab_library), Pair(Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary))
            )
            AppMode.TV -> listOf(
                homeTab,
                searchTab,
                Triple(2, stringResource(R.string.tab_library), Pair(Icons.Filled.Movie, Icons.Outlined.Movie))
            )
            AppMode.MUSIC -> listOf(
                homeTab,
                searchTab,
                Triple(2, stringResource(R.string.tab_library), Pair(Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic))
            )
        }
        val selectNavTab: (Int) -> Unit = { index ->
            if (selectedTab == index) {
                if (currentTabScrollState.canScrollBackward) {
                    navBarHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    scope.launch { currentTabScrollState.scrollToTop() }
                }
            } else {
                navBarHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                selectedTab = index
            }
        }

        if (nonExpressiveNavigationBar) {
            NavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                navTabs.forEach { (index, label, icons) ->
                    val selected = selectedTab == index
                    val (filledIcon, outlinedIcon) = icons
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = selected,
                        onClick = { selectNavTab(index) },
                        icon = {
                            Icon(
                                imageVector = if (selected) filledIcon else outlinedIcon,
                                contentDescription = label
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                content = {
                    navTabs.forEach { (index, label, icons) ->
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
                            onClick = { selectNavTab(index) },
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
        }

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
            collapsedBottomSpacing = if (nonExpressiveNavigationBar) 96.dp else 100.dp,
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
                        text = stringResource(R.string.update_badge_version, latestVersion),
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
    // Long-press a song anywhere in music mode: play next, add to queue, add to
    // a playlist, like, download. The only route to addToQueue in the app.
    songOptionsTarget?.let { song ->
        com.ivor.ivormusic.ui.player.SongOptionsSheet(
            song = song,
            viewModel = playerViewModel,
            onDismiss = { songOptionsTarget = null }
        )
    }

    if (showAuthDialog) {
        com.ivor.ivormusic.ui.auth.YouTubeAuthDialog(
            onDismiss = {
                showAuthDialog = false
                addAuthAsNewProfile = false
            },
            onAuthSuccess = {
                showAuthDialog = false
                addAuthAsNewProfile = false
                // Refresh login state, account info and the feeds so the UI
                // reflects the account immediately instead of after a restart
                viewModel.checkYouTubeConnection()
                if (appMode.isVideo) {
                    viewModel.loadTrendingVideos()
                    viewModel.loadYouTubeHistory()
                } else if (appMode.isMusic) {
                    viewModel.loadYouTubeRecommendations()
                }
            },
            addAsNewProfile = addAuthAsNewProfile
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
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    // removeAllCookies is asynchronous. Opening the login page
                    // before its callback can immediately recapture the old
                    // account and make "Add account" appear to sign it out.
                    cookieManager.flush()
                    addAuthAsNewProfile = true
                    showAuthDialog = true
                }
            },
            onReconnectProfile = {
                // Same jar wipe as adding an account, for the same reason: the
                // login page auto-continues as whoever WebView still holds, so
                // without this a reconnect can silently store the *other*
                // account's session against this profile.
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    // False, unlike the add path: the profile already exists
                    // and is now active, so the session has to land on it
                    // rather than in a new row. addYouTubeProfileAndSwitch
                    // cannot match it either - the datasyncId is only known
                    // after an authenticated call, so it would mint a fresh id
                    // and orphan this profile's subscriptions and blocklist.
                    addAuthAsNewProfile = false
                    showAuthDialog = true
                }
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
    onSongLongPress: ((Song) -> Unit)? = null,
    onPlayClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    isDarkMode: Boolean,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    excludedFolders: Set<String> = emptySet(),
    manualScan: Boolean = false,
    appMode: AppMode = AppMode.MUSIC,
    onAppModeChange: (AppMode) -> Unit = {},
    showModeToggle: Boolean = true,
    modeToggleState: AppModeToggleState = rememberAppModeToggleState(appMode),
    /** Hoisted by HomeScreen: survives tab switches, reachable by the nav bar. */
    listState: LazyListState = rememberLazyListState()
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground

    val isRefreshing by viewModel.isLoading.collectAsState()

    // Do not leave an infinite transition running behind the loaded Home.
    // Reading its value here invalidates this whole composition on every
    // animation frame, which made ordinary vertical scrolling compete with an
    // invisible skeleton pulse. One pulse is still shared while placeholders
    // are actually on screen.
    val skeletonAlpha = if (isInitialLoading) {
        com.ivor.ivormusic.ui.components.rememberSkeletonAlpha()
    } else {
        1f
    }

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
                .background(backgroundColor),
            contentPadding = contentPadding
        ) {
            item { 
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                Box(Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    translationY = if (visible) 0f else -20f
                }.animateContentSize()) {
                    TopBarSection(onProfileClick = onProfileClick, onSettingsClick = onSettingsClick, onDownloadsClick = onDownloadsClick, isDarkMode = isDarkMode, viewModel = viewModel, appMode = appMode, onAppModeChange = onAppModeChange, showModeToggle = showModeToggle, modeToggleState = modeToggleState)
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
                        OrganicSongLayout(
                            songs = songs,
                            onSongClick = onSongClick,
                            onSongLongPress = onSongLongPress
                        )
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
                            title = stringResource(R.string.home_section_recent_albums),
                            itemWidth = 200.dp,
                            itemHeight = 240.dp,
                            skeletonAlpha = skeletonAlpha
                        )
                    } else {
                        RecentAlbumsSection(
                            songs = songs,
                            onSongClick = onSongClick,
                            onSongLongPress = onSongLongPress,
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
                            title = stringResource(R.string.home_section_jump_back_in),
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
                            onSongLongPress = onSongLongPress,
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
    appMode: AppMode = AppMode.MUSIC,
    onAppModeChange: (AppMode) -> Unit = {},
    showModeToggle: Boolean = true,
    modeToggleState: AppModeToggleState = rememberAppModeToggleState(appMode)
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
        val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
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
                    contentDescription = stringResource(R.string.cd_profile),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(R.string.cd_profile),
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
                        contentDescription = stringResource(R.string.cd_downloads),
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
                    contentDescription = stringResource(R.string.cd_settings),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Music/Video mode switch, anchored in the corner so it stays put
            // when the home content swaps between modes. Can be hidden from
            // Settings (Home Screen Mode Toggle).
            if (showModeToggle) {
                AppModeToggle(
                    mode = appMode,
                    onModeChange = onAppModeChange,
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
                text = stringResource(R.string.your_mix_line1),
                style = MaterialTheme.typography.displayLarge,
                color = textColor
            )
            Text(
                text = stringResource(R.string.your_mix_line2),
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
                    text = (firstSong?.artist.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: stringResource(R.string.unknown_artist)).let { artist ->
                        (secondSong?.artist.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) })?.let { second -> stringResource(R.string.artists_joined, artist, second) } ?: artist
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
                    contentDescription = stringResource(R.string.cd_play),
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
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null
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
                    .songRowClick(
                        onClick = { onSongClick(songs[0]) },
                        onLongClick = onSongLongPress?.let { press -> { press(songs[0]) } }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = songs[0].highResThumbnailUrl ?: songs[0].thumbnailUrl
                val localUri = songs[0].albumArtUri
                
                if (imageUrl != null || localUri != null) {
                    val request = remember(context, localUri, imageUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(localUri ?: imageUrl)
                            .crossfade(true)
                            .build()
                    }
                    coil.compose.SubcomposeAsyncImage(
                        model = request,
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
                    .songRowClick(
                        onClick = { onSongClick(songs[1]) },
                        onLongClick = onSongLongPress?.let { press -> { press(songs[1]) } }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = songs[1].highResThumbnailUrl ?: songs[1].thumbnailUrl
                val localUri = songs[1].albumArtUri
                
                if (imageUrl != null || localUri != null) {
                    val request = remember(context, localUri, imageUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(localUri ?: imageUrl)
                            .crossfade(true)
                            .build()
                    }
                    coil.compose.SubcomposeAsyncImage(
                        model = request,
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
                    .songRowClick(
                        onClick = { onSongClick(songs[2]) },
                        onLongClick = onSongLongPress?.let { press -> { press(songs[2]) } }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = songs[2].highResThumbnailUrl ?: songs[2].thumbnailUrl
                val localUri = songs[2].albumArtUri
                
                if (imageUrl != null || localUri != null) {
                    val request = remember(context, localUri, imageUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(localUri ?: imageUrl)
                            .crossfade(true)
                            .build()
                    }
                    coil.compose.SubcomposeAsyncImage(
                        model = request,
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
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .songRowClick(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        val imageUrl = song.highResThumbnailUrl ?: song.thumbnailUrl
        val localUri = song.albumArtUri
        
        if (imageUrl != null || localUri != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val request = remember(context, localUri, imageUrl) {
                coil.request.ImageRequest.Builder(context)
                    .data(localUri ?: imageUrl)
                    .crossfade(true)
                    .build()
            }
            coil.compose.SubcomposeAsyncImage(
                model = request,
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
    /**
     * Play a playlist found through search as a queue. Null falls back to
     * [onVideoClick], which plays the tapped video alone.
     */
    onPlayVideoQueue: ((com.ivor.ivormusic.data.VideoQueue) -> Unit)? = null,
    onEnqueueVideo: ((com.ivor.ivormusic.data.VideoItem, Boolean) -> Unit)? = null,
    onProfileClick: () -> Unit = {},
    /** Open a creator's page, from a Channels result or the long-press sheet. */
    onOpenChannel: (String) -> Unit = {},
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    videoMode: Boolean = false,
    localOnly: Boolean = false,
    onSongLongPress: ((Song) -> Unit)? = null,
    /** Hoisted by HomeScreen: survives tab switches, reachable by the nav bar. */
    listState: LazyListState = rememberLazyListState()
) {
    var viewedPlaylist by remember { mutableStateOf<com.ivor.ivormusic.data.PlaylistDisplayItem?>(null) }
    var viewedArtist by remember { mutableStateOf<com.ivor.ivormusic.data.ArtistItem?>(null) }
    var viewedVideoPlaylist by remember { mutableStateOf<com.ivor.ivormusic.data.VideoPlaylist?>(null) }

    val currentScreen = when {
        viewedVideoPlaylist != null -> "videoPlaylist"
        viewedPlaylist != null -> "playlist"
        viewedArtist != null -> "artist"
        else -> "search"
    }

    // Expressive motion physics for screen pushes/pops
    val searchNavSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val searchNavEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // Playlist/album is the deepest layer (search -> artist -> album), so it
    // pops first; backing out of an album returns to the artist page. Only the
    // last step out is previewed: popping an album back to the artist reveals
    // another child, not the search screen underneath.
    com.ivor.ivormusic.ui.components.PredictiveBackStack(
        childOpen = currentScreen != "search",
        onBack = {
            when {
                viewedVideoPlaylist != null -> viewedVideoPlaylist = null
                viewedPlaylist != null -> viewedPlaylist = null
                viewedArtist != null -> viewedArtist = null
            }
        },
        previewable = listOfNotNull(viewedVideoPlaylist, viewedPlaylist, viewedArtist).size == 1,
        background = {
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
                onEnqueueVideo = onEnqueueVideo,
                onOpenChannel = onOpenChannel,
                onSongLongPress = onSongLongPress,
                contentPadding = contentPadding,
                viewModel = viewModel,
                isDarkMode = isDarkMode,
                videoMode = videoMode,
                localOnly = localOnly,
                listState = listState
            )
        }
    ) { committedByGesture ->
    androidx.compose.animation.AnimatedContent(
        targetState = currentScreen,
        label = "SearchNav",
        transitionSpec = {
            val content = when {
                // The finger already performed this exit.
                committedByGesture ->
                    androidx.compose.animation.EnterTransition.None togetherWith
                        androidx.compose.animation.ExitTransition.None
                targetState != "search" ->
                    // Push (Going deeper)
                    (androidx.compose.animation.slideInHorizontally(animationSpec = searchNavSpatialSpec) { width -> width } +
                            androidx.compose.animation.fadeIn(animationSpec = searchNavEffectsSpec)) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(animationSpec = searchNavSpatialSpec) { width -> -width / 3 } +
                                    androidx.compose.animation.fadeOut(animationSpec = searchNavEffectsSpec))
                else ->
                    // Pop (Going back). The search screen is underneath and
                    // staying put, so the child carries the whole movement.
                    androidx.compose.animation.fadeIn(animationSpec = searchNavEffectsSpec) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(animationSpec = searchNavSpatialSpec) { width -> width } +
                                    androidx.compose.animation.fadeOut(animationSpec = searchNavEffectsSpec))
            }
            // The search state of this layer is empty, so the default
            // SizeTransform would animate the container between nothing and
            // full screen and clip the child to it on the way.
            content using androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                androidx.compose.animation.core.snap()
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
                        viewModel = viewModel,
                        onSongLongPress = onSongLongPress,
                        onOpenChannel = onOpenChannel
                    )
                }
            }

            "playlist" -> {
                 viewedPlaylist?.let { playlist ->
                    com.ivor.ivormusic.ui.library.PlaylistDetailScreen(
                        playlist = playlist,
                        onBack = { viewedPlaylist = null },
                        onPlayQueue = onPlayQueue,
                        viewModel = viewModel,
                        onSongLongPress = onSongLongPress
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
                        allowRemove = false,
                        onPlayQueue = onPlayVideoQueue,
                        onEnqueueVideo = onEnqueueVideo
                    )
                }
            }
            // Search lives underneath now; this layer is empty over it, and
            // full size so both states measure the same.
            else -> Spacer(Modifier.fillMaxSize())
        }
    }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentAlbumsSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    isDarkMode: Boolean = true,
    onShowAll: (() -> Unit)? = null
) {
    if (songs.isEmpty()) return

    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // We need at least one large, one medium, one small for full effect,
    // but the component handles fewer items gracefully.
    val state = rememberCarouselState { songs.size }

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(title = stringResource(R.string.home_section_recent_albums), onShowAll = onShowAll)

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
                    .songRowClick(
                        onClick = { onSongClick(song) },
                        onLongClick = onSongLongPress?.let { press -> { press(song) } }
                    )
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
                    contentDescription = stringResource(R.string.cd_show_all, title),
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
    onSongLongPress: ((Song) -> Unit)? = null,
    onShowAll: (() -> Unit)? = null
) {
    if (songs.isEmpty()) return

    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(title = stringResource(R.string.home_section_jump_back_in), onShowAll = onShowAll)

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
                        .songRowClick(
                            onClick = { onSongClick(song) },
                            onLongClick = onSongLongPress?.let { press -> { press(song) } }
                        )
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
                        text = song.title.takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: stringResource(R.string.untitled_song),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                    Text(
                        text = song.artist.takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: stringResource(R.string.unknown_artist),
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
