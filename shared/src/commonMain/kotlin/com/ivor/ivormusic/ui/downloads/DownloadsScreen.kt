package com.ivor.ivormusic.ui.downloads

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ivor.ivormusic.domain.Song

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    downloadedSongs: List<Song>,
    downloadingIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Int> = emptyMap(),
    downloadingSongs: Map<String, Song> = emptyMap(),
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song) -> Unit = { _, song -> onPlaySong(song) },
    onDeleteDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDownloads = downloadingIds
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Downloads")
                        Text(
                            "${downloadedSongs.size} songs • ${activeDownloads.size} downloading",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariantColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active Downloads Section
            if (activeDownloads.isNotEmpty()) {
                item {
                    Text(
                        "Downloading",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(activeDownloads.toList(), key = { "active_$it" }) { songId ->
                    val song = downloadingSongs[songId]
                    val pct = downloadProgress[songId] ?: 0
                    if (song != null) {
                        ActiveDownloadCard(
                            songId = songId,
                            song = song,
                            progressPct = pct,
                            isFailed = false,
                            onCancel = { onCancelDownload(songId) },
                            onRetry = { onRetryDownload(song) },
                            primaryColor = primaryColor,
                            onSurfaceColor = onSurfaceColor,
                            onSurfaceVariantColor = onSurfaceVariantColor
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Downloaded Songs Section
            if (downloadedSongs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Downloaded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )
                        Text(
                            "${downloadedSongs.sumOf { it.duration / 1000 / 60 }} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariantColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(downloadedSongs, key = { "downloaded_${it.id}" }) { song ->
                    DownloadedSongCard(
                        song = song,
                        onPlay = { onPlayQueue(downloadedSongs, song) },
                        onDelete = { onDeleteDownload(song.id) },
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                        onSurfaceVariantColor = onSurfaceVariantColor
                    )
                }
            }

            // Empty State
            if (downloadedSongs.isEmpty() && activeDownloads.isEmpty() && downloadingSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = onSurfaceVariantColor.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No Downloads",
                                style = MaterialTheme.typography.titleLarge,
                                color = onSurfaceVariantColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Downloaded songs will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveDownloadCard(
    songId: String,
    song: Song,
    progressPct: Int,
    isFailed: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    AsyncImage(
                        model = song.thumbnailUrl ?: song.albumArtUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Song Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariantColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Status text
                    Text(
                        text = if (isFailed) "Failed - Tap to retry" else if (progressPct > 0) "$progressPct%" else "Downloading...",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFailed) MaterialTheme.colorScheme.error else primaryColor
                    )
                }

                // Action Button
                if (isFailed) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Retry",
                            tint = primaryColor
                        )
                    }
                } else {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Cancel",
                            tint = onSurfaceVariantColor
                        )
                    }
                }
            }

            // Progress Bar
            if (!isFailed) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progressPct / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = primaryColor,
                    trackColor = primaryColor.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun DownloadedSongCard(
    song: Song,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                AsyncImage(
                    model = song.thumbnailUrl ?: song.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariantColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Downloaded icon
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Downloaded",
                tint = primaryColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = onSurfaceVariantColor
                )
            }
        }
    }
}
