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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
    contentPadding: PaddingValues
) {
    val historyVideos by viewModel.historyVideos.collectAsState()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsState()
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    val backgroundColor = MaterialTheme.colorScheme.background
    
    // Initial fetch
    LaunchedEffect(Unit) {
        if (isYouTubeConnected && historyVideos.isEmpty()) {
            viewModel.loadYouTubeHistory()
        }
    }
    
    // Animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    if (!isYouTubeConnected) {
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
                    text = "Log in to see your YouTube watch history here.",
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

    PullToRefreshBox(
        isRefreshing = isHistoryLoading,
        onRefresh = { viewModel.loadYouTubeHistory() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
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
            
            if (isHistoryLoading && historyVideos.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                         CircularProgressIndicator()
                    }
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
    val accentColor = Color(0xFF9C27B0) // Purple for History
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
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
                // History Icon container
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "Watch",
                        style = MaterialTheme.typography.displayLarge,
                        color = textColor
                    )
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.displayLarge,
                        color = accentColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Pick up where you left off",
                style = MaterialTheme.typography.titleMedium,
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
