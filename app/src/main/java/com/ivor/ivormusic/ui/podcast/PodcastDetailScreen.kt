package com.ivor.ivormusic.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PodcastEpisode
import com.ivor.ivormusic.data.PodcastFeed
import com.ivor.ivormusic.data.Song
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A podcast feed's detail page, following the playlist detail layout:
 * artwork with gradient at the top, title and stats, Play all / Shuffle,
 * then a scrollable episode list.
 */
@Composable
fun PodcastDetailScreen(
    feed: PodcastFeed,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onShuffleQueue: (List<Song>) -> Unit,
    contentPadding: PaddingValues,
    viewModel: PodcastsViewModel = viewModel(),
) {
    val episodes by viewModel.episodesByFeed.collectAsState()
    val progressByEpisode by viewModel.episodeProgress.collectAsState()
    val downloadedIds by viewModel.downloadedEpisodeIds.collectAsState()
    val downloadingIds by viewModel.downloadingEpisodeIds.collectAsState()
    val feedEpisodes = (episodes[feed.id].orEmpty()).sortedByDescending { it.pubDate }
    val songs = viewModel.episodeSongs(feed.id)
    val songsById = remember(songs) { songs.associateBy { it.id } }

    // Refresh on open: Patreon and other private feeds use signed URLs that
    // expire. Refreshing ensures the episode audio URLs are fresh when the
    // user taps play.
    androidx.compose.runtime.LaunchedEffect(feed.id) {
        viewModel.refreshFeed(feed.id)
    }

    val totalDurationMs = feedEpisodes.sumOf { it.durationMs ?: 0L }
    val durationText = formatTotalDuration(totalDurationMs)

    val ground = MaterialTheme.colorScheme.background
    val layoutDirection = LocalLayoutDirection.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        // Header: artwork with gradient, back button, title and stats.
        item(key = "header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                // Artwork (or gradient placeholder).
                if (feed.artworkUrl != null) {
                    AsyncImage(
                        model = feed.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // Bottom fade into the page background.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to ground.copy(alpha = 0f),
                                0.55f to ground.copy(alpha = 0f),
                                0.85f to ground.copy(alpha = 0.9f),
                                1f to ground
                            )
                        )
                )

                // Top fade for the back button.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ground.copy(alpha = 0.6f),
                                    ground.copy(alpha = 0f)
                                )
                            )
                        )
                )

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }

                // Title and stats, bottom of the header.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = feed.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.podcast_detail_stats,
                            feedEpisodes.size,
                            durationText
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Play all / Shuffle.
        item(key = "controls") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        if (songs.isNotEmpty()) onPlayQueue(songs, songs.first())
                    },
                    modifier = Modifier.weight(1f),
                    enabled = songs.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_play_all))
                }
                Button(
                    onClick = {
                        if (songs.isNotEmpty()) onShuffleQueue(songs)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    enabled = songs.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_shuffle))
                }
            }
        }

        items(feedEpisodes, key = { "episode:${it.id}" }) { episode ->
            val song = songsById["podcast:${episode.id}"]
            val songId = "${com.ivor.ivormusic.data.EpisodeProgressRepository.PODCAST_SONG_ID_PREFIX}${episode.id}"
            EpisodeDetailRow(
                episode = episode,
                progress = progressByEpisode[episode.id],
                downloadState = when {
                    songId in downloadingIds -> EpisodeDownloadState.Downloading
                    songId in downloadedIds -> EpisodeDownloadState.Downloaded
                    else -> EpisodeDownloadState.NotDownloaded
                },
                onClick = {
                    if (song != null) onPlayQueue(songs, song)
                },
                onDownloadClick = { viewModel.downloadEpisode(episode, feed) },
            )
        }
    }
}

private enum class EpisodeDownloadState { NotDownloaded, Downloading, Downloaded }

@Composable
private fun EpisodeDetailRow(
    episode: PodcastEpisode,
    progress: com.ivor.ivormusic.data.EpisodeProgress?,
    downloadState: EpisodeDownloadState = EpisodeDownloadState.NotDownloaded,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (episode.artworkUrl != null) {
            AsyncImage(
                model = episode.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    episode.pubDate?.let {
                        append(formatEpisodeDate(it))
                        append(" • ")
                    }
                    episode.durationMs?.let {
                        append(formatEpisodeDuration(it))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Playback progress bar under the subtitle.
            val showBar = progress != null && !progress.completed &&
                progress.durationMs > 0 && progress.positionMs > 0
            if (showBar) {
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = {
                        (progress!!.positionMs.toFloat() / progress.durationMs)
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        if (progress?.completed == true) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.podcast_episode_played_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        when (downloadState) {
            EpisodeDownloadState.Downloading -> {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            EpisodeDownloadState.Downloaded -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.podcast_episode_downloaded_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            EpisodeDownloadState.NotDownloaded -> {
                androidx.compose.material3.IconButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(R.string.cd_downloads),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatEpisodeDate(timestamp: Long): String {
    return try {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}

private fun formatEpisodeDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatTotalDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} hr ${minutes} min" else "${minutes} min"
}
