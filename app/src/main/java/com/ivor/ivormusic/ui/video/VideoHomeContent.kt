package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.home.HomeViewModel

/**
 * Video Home Screen Content for Video Mode.
 * Displays trending/recommended videos with thumbnails, channel names, views, etc.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoHomeContent(
    videos: List<VideoItem>,
    isLoading: Boolean,
    onVideoClick: (VideoItem) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    onRefresh: () -> Unit,
    isDarkMode: Boolean,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    
    // Animation state for staggered entry
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    ExpressivePullToRefresh(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading && videos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .windowInsetsPadding(WindowInsets.statusBars),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Bar
                item {
                    VideoTopBarSection(
                        onProfileClick = onProfileClick,
                        onSettingsClick = onSettingsClick,
                        onDownloadsClick = onDownloadsClick,
                        viewModel = viewModel
                    )
                }
                
                // Hero Section with "Video" branding
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn() + slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                    ) {
                        VideoHeroSection(isDarkMode = isDarkMode)
                    }
                }
                
                // Section title - changes based on whether user is logged in
                item {
                    Text(
                        text = if (isYouTubeConnected) "Recommended For You" else "Trending Videos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                
                // Video cards
                items(videos) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                // Empty state
                if (videos.isEmpty() && !isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No videos found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoTopBarSection(
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    viewModel: HomeViewModel
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
        
        // Right side icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Downloads Button
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
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Downloads",
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Badge for active downloads
                if (downloadingIds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.TopEnd)
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
        }
    }
}

@Composable
private fun VideoHeroSection(isDarkMode: Boolean) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = Color(0xFFFF0000) // YouTube red
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
                    )
                )
            )
            .padding(24.dp)
    ) {
        // Abstract shape
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = (-20).dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.05f))
        )
        
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Icon container
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = "Video",
                            style = MaterialTheme.typography.displayLarge,
                            color = textColor
                        )
                        Text(
                            text = "Mode",
                            style = MaterialTheme.typography.displayLarge,
                            color = accentColor
                        )
                    }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Literally whole youtube but with no ads",
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Video Card component - displays a single video with thumbnail, title, channel, views.
 */
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column {
            // Thumbnail with duration overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                AsyncImage(
                    model = video.highResThumbnailUrl ?: video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Gradient overlay at bottom for duration
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )
                
                // Duration badge
                if (!video.isLive) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = video.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    // Live badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF0000)
                    ) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            
            // Video info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Channel avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (!video.channelIconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = video.channelIconUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = video.channelName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    // Video title
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Channel name, views, date
                    Text(
                        text = buildString {
                            append(video.channelName)
                            if (video.viewCount.isNotEmpty()) {
                                append(" • ")
                                append(video.viewCount)
                            }
                            if (!video.uploadedDate.isNullOrEmpty()) {
                                append(" • ")
                                append(video.uploadedDate)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
