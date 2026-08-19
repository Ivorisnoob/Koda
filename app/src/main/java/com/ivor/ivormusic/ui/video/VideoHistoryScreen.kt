package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.components.SkeletonList
import com.ivor.ivormusic.ui.components.VideoCardSkeleton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoHistoryContent(
    viewModel: HomeViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onLoginClick: () -> Unit,
    contentPadding: PaddingValues,
    showHero: Boolean = true,
    /** Queue a video from here. Null where there is no player to queue into. */
    onEnqueueVideo: ((VideoItem, Boolean) -> Unit)? = null,
    /** Open a creator's page, from the long-press sheet. */
    onOpenChannel: ((String) -> Unit)? = null
) {
    val historyVideos by viewModel.historyVideos.collectAsState()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsState()
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    val backgroundColor = MaterialTheme.colorScheme.background

    // A long press means the same thing here as on every other video card.
    // History is the list people come back to precisely to re-watch or keep
    // something, so it is the last surface that should have been read-only.
    var optionsTarget by remember { mutableStateOf<VideoItem?>(null) }
    optionsTarget?.let { video ->
        VideoOptionsSheetHost(
            video = video,
            viewModel = viewModel,
            onDismiss = { optionsTarget = null },
            onEnqueue = onEnqueueVideo?.let { enqueue -> { next -> enqueue(video, next) } },
            onOpenChannel = onOpenChannel
        )
    }

    // Initial fetch — works logged out too (locally persisted history)
    LaunchedEffect(Unit) {
        if (historyVideos.isEmpty()) {
            viewModel.loadYouTubeHistory()
        }
    }

    // Animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    // Login wall only when logged out AND nothing watched locally yet
    if (!isYouTubeConnected && historyVideos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Watch History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Videos you watch will show up here. Log in to also sync your YouTube history.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Login, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log in to YouTube")
                }
            }
        }
        return
    }

    ExpressivePullToRefresh(
        // The pull spinner only represents a refresh over existing history; the
        // empty first load is the skeleton's job below. Bound to the same flag
        // they both run at once.
        isRefreshing = isHistoryLoading && historyVideos.isNotEmpty(),
        onRefresh = { viewModel.loadYouTubeHistory() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            // Embedded in the Library tab a SubPageTopBar already sits in the
            // status bar area, so only the standalone screen takes the top
            // inset - and it takes it as content padding, so the hero scrolls
            // under the status bar instead of stopping at it.
            contentPadding = if (showHero) {
                contentPadding
            } else {
                PaddingValues(bottom = contentPadding.calculateBottomPadding())
            },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            if (showHero) {
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn() + slideInVertically(
                            initialOffsetY = { -40 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                    ) {
                        HistoryHeroSection()
                    }
                }
            }
            
            if (isHistoryLoading && historyVideos.isEmpty()) {
                item {
                    SkeletonList(
                        count = 3,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) { alpha -> VideoCardSkeleton(alpha = alpha) }
                }
            } else if (historyVideos.isEmpty()) {
                 item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No history found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(historyVideos) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        onLongClick = { optionsTarget = video },
                        onOpenChannel = onOpenChannel,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            
             item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun HistoryHeroSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Watch History",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Pick up where you left off",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
