package com.ivor.ivormusic.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import com.ivor.ivormusic.ui.player.PlayerViewModel

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onSongClick: (Song) -> Unit,
    onPlayerExpand: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: HomeViewModel = viewModel()
) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val progress by playerViewModel.progress.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    
    val progressFraction = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f
    
    val permissionState = rememberPermissionState(
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        } else {
            viewModel.loadSongs()
        }
    }

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            viewModel.loadSongs()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Column {
                MiniPlayer(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    progress = progressFraction,
                    onPlayPauseClick = { playerViewModel.togglePlayPause() },
                    onNextClick = { playerViewModel.skipToNext() },
                    onClick = onPlayerExpand
                )
                
                ExpressiveNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        if (permissionState.status.isGranted) {
            when (selectedTab) {
                0 -> YourMixContent(
                    songs = songs,
                    onSongClick = onSongClick,
                    onPlayClick = {
                        songs.firstOrNull()?.let { playerViewModel.playSong(it) }
                    },
                    contentPadding = innerPadding
                )
                1 -> SearchContent(innerPadding)
                2 -> LibraryContent(innerPadding)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Permission required to load songs", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
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
    contentPadding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(contentPadding)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item { TopBarSection() }
            item { HeroSection(songs = songs, onPlayClick = onPlayClick) }
            item {
                if (songs.isNotEmpty()) {
                    OrganicSongLayout(songs = songs, onSongClick = onSongClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBarSection() {
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
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
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
                    containerColor = Color(0xFF1E1E1E),
                    contentColor = Color.White
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
                onClick = { },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF1E1E1E),
                    contentColor = Color.White
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
    onPlayClick: () -> Unit
) {
    val firstSong = songs.firstOrNull()
    val secondSong = songs.getOrNull(1)
    
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
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 68.sp
                ),
                color = Color.White
            )
            Text(
                text = "Mix",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 68.sp
                ),
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = firstSong?.artist?.let { artist ->
                    secondSong?.artist?.let { "$artist, $it" } ?: artist
                } ?: "Traveler, Water Houses",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color(0xFFB3B3B3)
            )
        }
        
        // Right side - Play button with notch effect (using shape morphing)
        Box(modifier = Modifier.padding(top = 32.dp)) {
            Button(
                onClick = onPlayClick,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB8D4FF),
                    contentColor = Color.Black
                ),
                modifier = Modifier.size(76.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(32.dp)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .padding(horizontal = 8.dp)
    ) {
        // Small circular image - top left, rotated
        if (songs.size > 2) {
            SongCircleCard(
                song = songs[2],
                onClick = { onSongClick(songs[2]) },
                rotation = -10f,
                modifier = Modifier
                    .size(85.dp)
                    .align(Alignment.TopStart)
                    .offset(x = 16.dp, y = 30.dp)
            )
        }
        
        // Main large pill shape - center right, ROTATED DIAGONALLY
        if (songs.isNotEmpty()) {
            SongPillCard(
                song = songs[0],
                onClick = { onSongClick(songs[0]) },
                rotation = 15f, // Diagonal rotation like in the design
                modifier = Modifier
                    .width(180.dp)
                    .height(280.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-24).dp, y = 10.dp)
            )
        }
        
        // Medium circular image - left side, rotated
        if (songs.size > 3) {
            SongCircleCard(
                song = songs[3],
                onClick = { onSongClick(songs[3]) },
                rotation = 5f,
                modifier = Modifier
                    .size(70.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = 8.dp, y = 80.dp)
            )
        }
        
        // Large circular image - bottom right
        if (songs.size > 1) {
            SongCircleCard(
                song = songs[1],
                onClick = { onSongClick(songs[1]) },
                rotation = -8f,
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-24).dp, y = (-30).dp)
            )
        }
        
        // Bottom strip image - full width at bottom
        if (songs.size > 4) {
            SongStripCard(
                song = songs[4],
                onClick = { onSongClick(songs[4]) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun SongPillCard(
    song: Song,
    onClick: () -> Unit,
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE8E8E8))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color(0xFF666666)
                )
            }
        }
    }
}

@Composable
fun SongCircleCard(
    song: Song,
    onClick: () -> Unit,
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .clip(CircleShape)
            .background(Color(0xFF3A3A3A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3A)),
                contentAlignment = Alignment.Center
            ) {
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
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
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


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    ShortNavigationBar(
        containerColor = Color(0xFF1A1A1A),
        contentColor = Color.White
    ) {
        ShortNavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.People,
                    contentDescription = null
                )
            },
            label = { Text("Your Mix", fontSize = 12.sp) },
            iconPosition = NavigationItemIconPosition.Top
        )
        ShortNavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == 1) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = null
                )
            },
            label = { Text("Search", fontSize = 12.sp) },
            iconPosition = NavigationItemIconPosition.Top
        )
        ShortNavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == 2) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic,
                    contentDescription = null
                )
            },
            label = { Text("Library", fontSize = 12.sp) },
            iconPosition = NavigationItemIconPosition.Top
        )
    }
}

@Composable
fun SearchContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Search - Coming Soon",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
    }
}

@Composable
fun LibraryContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Library - Coming Soon",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
    }
}
