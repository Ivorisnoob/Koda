package com.ivor.ivormusic.ui.home

import android.Manifest
import android.os.Build
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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
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
import com.ivor.ivormusic.ui.components.MiniPlayer
import com.ivor.ivormusic.ui.components.FloatingPillNavBar
import com.ivor.ivormusic.ui.player.PlayerViewModel
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


import androidx.compose.animation.ExperimentalAnimationApi

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    onSongClick: (Song) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: HomeViewModel = viewModel(),
    isDarkMode: Boolean = true,
    onThemeToggle: (Boolean) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    loadLocalSongs: Boolean = true
) {
    val localSongs by viewModel.songs.collectAsState()
    val youtubeSongs by viewModel.youtubeSongs.collectAsState()
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    
    // Use local songs or YouTube songs based on setting
    val songs = if (loadLocalSongs) localSongs else youtubeSongs
    
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
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
    LaunchedEffect(Unit, loadLocalSongs) {
        viewModel.checkYouTubeConnection()
        if (loadLocalSongs) {
            if (!permissionState.status.isGranted) {
                permissionState.launchPermissionRequest()
            } else {
                viewModel.loadSongs()
            }
        } else {
            // Load YouTube recommendations when not using local songs
            viewModel.loadYouTubeRecommendations()
        }
    }

    LaunchedEffect(permissionState.status.isGranted, loadLocalSongs) {
        if (permissionState.status.isGranted && loadLocalSongs) {
            viewModel.loadSongs()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Auth Dialog State
    var showAuthDialog by remember { mutableStateOf(false) }

    val backgroundColor = if (isDarkMode) Color.Black else Color(0xFFF8F8F8)
    
    // Loading state for playlist fetch
    var isPlaylistLoading by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()

    // Use Box overlay instead of Scaffold for truly floating navbar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Main content
        if (permissionState.status.isGranted) {
            androidx.compose.animation.AnimatedContent(
                targetState = selectedTab,
                label = "TabTransition",
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) +
                            androidx.compose.animation.slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { width -> width / 3 }) with
                            (androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) +
                                    androidx.compose.animation.slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { width -> -width / 3 })
                }
            ) { targetTab ->
                when (targetTab) {
                    0 -> {
                        if (isLoading && songs.isEmpty()) {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = if (isDarkMode) Color.White else Color.Black
                                )
                            }
                        } else {
                            YourMixContent(
                                songs = songs,
                                onSongClick = onSongClick,
                                onPlayClick = {
                                    if (songs.isNotEmpty()) {
                                        onSongClick(songs[0])
                                    }
                                },
                                onProfileClick = { showAuthDialog = true },
                                onSettingsClick = onNavigateToSettings,
                                isDarkMode = isDarkMode,
                                contentPadding = PaddingValues(bottom = 160.dp), // Space for navbar + miniplayer
                                viewModel = viewModel
                            )
                        }
                    }
                    1 -> SearchContent(
                        songs = songs,
                        onSongClick = onSongClick,
                        contentPadding = PaddingValues(bottom = 160.dp),
                        viewModel = viewModel,
                        isDarkMode = isDarkMode
                    )
                    2 -> LibraryContent(
                        songs = songs,
                        onSongClick = onSongClick,
                        onPlaylistClick = { playlist ->
                             scope.launch {
                                 isPlaylistLoading = true
                                 val url = playlist.url ?: ""
                                 val listId = if (url.contains("list=")) url.substringAfter("list=") else ""
                                 if (listId.isNotEmpty()) {
                                     val playlistSongs = viewModel.fetchPlaylistSongs(listId)
                                     if (playlistSongs.isNotEmpty()) {
                                         playerViewModel.playQueue(playlistSongs)
                                         showPlayerSheet = true
                                     }
                                 }
                                 isPlaylistLoading = false
                             }
                        },
                        contentPadding = PaddingValues(bottom = 160.dp),
                        viewModel = viewModel,
                        isDarkMode = isDarkMode
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Permission required to load songs", color = if (isDarkMode) Color.White else Color.Black)
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
        
        // Floating MiniPlayer - positioned above navbar
        MiniPlayer(
            currentSong = currentSong,
            isPlaying = isPlaying,
            progress = progressFraction,
            onPlayPauseClick = { playerViewModel.togglePlayPause() },
            onNextClick = { playerViewModel.skipToNext() },
            onClick = { showPlayerSheet = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .navigationBarsPadding()
        )
        
        // Floating Navigation bar - truly floating overlay
        FloatingPillNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            isDarkMode = isDarkMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
    
    // Auth Dialog welp i guess we are doing it then
    if (showAuthDialog) {
        com.ivor.ivormusic.ui.auth.YouTubeAuthDialog(
            onDismiss = { showAuthDialog = false },
            onAuthSuccess = { showAuthDialog = false }
        )
    }
    
    // Swipe-up Player Bottom Sheet with M3 Expressive spring animations Cause i fucking Love it 
    if (showPlayerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlayerSheet = false },
            sheetState = sheetState,
            containerColor = Color.Black,
            contentColor = Color.White,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = Color.White.copy(alpha = 0.4f)
                )
            },
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            PlayerSheetContent(
                viewModel = playerViewModel,
                onCollapse = {
                    scope.launch {
                        sheetState.hide()
                        showPlayerSheet = false
                    }
                }
            )
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
    isDarkMode: Boolean,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel
) {
    val backgroundColor = if (isDarkMode) Color.Black else Color(0xFFF8F8F8)
    val textColor = if (isDarkMode) Color.White else Color.Black
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
    ) {
        item { TopBarSection(onProfileClick = onProfileClick, onSettingsClick = onSettingsClick, isDarkMode = isDarkMode, viewModel = viewModel) }
        item { HeroSection(songs = songs, onPlayClick = onPlayClick, isDarkMode = isDarkMode) }
        item {
            if (songs.isNotEmpty()) {
                OrganicSongLayout(songs = songs, onSongClick = onSongClick)
            }
        }
        item {
            Spacer(modifier = Modifier.height(32.dp))
            RecentAlbumsSection(songs = songs, onSongClick = onSongClick, isDarkMode = isDarkMode)
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
            QuickPicksSection(songs = songs, onSongClick = onSongClick, isDarkMode = isDarkMode)
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBarSection(
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isDarkMode: Boolean,
    viewModel: HomeViewModel
) {
    val surfaceColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)
    val iconColor = if (isDarkMode) Color.White else Color.Black
    val containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF0F0F0)
    
    val userAvatar by viewModel.userAvatar.collectAsState()
    
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
            IconButton(
                onClick = { },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = containerColor,
                    contentColor = iconColor
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    modifier = Modifier.size(22.dp)
                )
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
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFFB3B3B3) else Color(0xFF666666)
    
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
                text = firstSong?.artist?.let { artist ->
                    secondSong?.artist?.let { "$artist, $it" } ?: artist
                } ?: "Traveler, Water Houses",
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
                    containerColor = if (isDarkMode) Color(0xFFB8D4FF) else Color(0xFF6200EE),
                    contentColor = if (isDarkMode) Color.Black else Color.White
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
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        
        // Circle sizes - percentage of screen width
        val circle1Size = boxWidth * 0.28f  // Top-left circle
        val circle2Size = boxWidth * 0.24f  // Bottom-right circle
        
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
                    .background(Color(0xFFE8E8E8))
                    .clickable { onSongClick(songs[0]) },
                contentAlignment = Alignment.Center
            ) {
                if (songs[0].albumArtUri != null || songs[0].thumbnailUrl != null) {
                    AsyncImage(
                        model = songs[0].albumArtUri ?: songs[0].thumbnailUrl,
                        contentDescription = songs[0].title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF666666)
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
                    .background(Color(0xFF3A3A3A))
                    .clickable { onSongClick(songs[1]) },
                contentAlignment = Alignment.Center
            ) {
                if (songs[1].albumArtUri != null || songs[1].thumbnailUrl != null) {
                    AsyncImage(
                        model = songs[1].albumArtUri ?: songs[1].thumbnailUrl,
                        contentDescription = songs[1].title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF888888)
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
                    .offset(x = boxWidth * (-0.05f), y = boxHeight * (-0.06f))
                    .graphicsLayer { rotationZ = 5f }
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A3A))
                    .clickable { onSongClick(songs[2]) },
                contentAlignment = Alignment.Center
            ) {
                if (songs[2].albumArtUri != null || songs[2].thumbnailUrl != null) {
                    AsyncImage(
                        model = songs[2].albumArtUri ?: songs[2].thumbnailUrl,
                        contentDescription = songs[2].title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF888888)
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
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (song.albumArtUri != null || song.thumbnailUrl != null) {
            AsyncImage(
                model = song.albumArtUri ?: song.thumbnailUrl,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A2A2A))
            )
        }
    }
}


@Composable
fun SearchContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean
) {
    com.ivor.ivormusic.ui.search.SearchScreen(
        songs = songs,
        onSongClick = onSongClick,
        contentPadding = contentPadding,
        viewModel = viewModel,
        isDarkMode = isDarkMode
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentAlbumsSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    isDarkMode: Boolean = true
) {
    if (songs.isEmpty()) return
    
    val textColor = if (isDarkMode) Color.White else Color.Black
    val cardBgColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE8E8E8)
    
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
                        model = song.albumArtUri ?: song.thumbnailUrl,
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
    
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color.Gray else Color(0xFF666666)
    val cardBgColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE8E8E8)
    
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
                .height(180.dp)
        ) { index ->
            val song = songs[index]
            Column(
                 modifier = Modifier
                    .width(140.dp)
                    .maskClip(MaterialTheme.shapes.medium)
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
                            model = song.albumArtUri ?: song.thumbnailUrl,
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
                    text = song.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                  Text(
                    text = song.artist,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor
                )
            }
        }
    }
}

@Composable
fun LibraryContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean
) {
    com.ivor.ivormusic.ui.library.LibraryScreen(
        songs = songs,
        onSongClick = onSongClick,
        onPlaylistClick = onPlaylistClick,
        contentPadding = contentPadding,
        viewModel = viewModel,
        isDarkMode = isDarkMode
    )
}

