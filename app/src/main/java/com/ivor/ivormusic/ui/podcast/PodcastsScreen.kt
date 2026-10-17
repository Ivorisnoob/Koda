package com.ivor.ivormusic.ui.podcast

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.ivor.ivormusic.data.EpisodeProgress
import com.ivor.ivormusic.data.PodcastEpisode
import com.ivor.ivormusic.data.Song
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PodcastFeed

/**
 * The Podcasts tab: a list of subscribed feeds. Tapping a feed opens its
 * detail page (episodes). A Material 3 FAB in the bottom-right adds feeds.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PodcastsScreen(
    onFeedClick: (PodcastFeed) -> Unit,
    onPlayEpisode: (Song) -> Unit = {},
    contentPadding: PaddingValues,
    listState: LazyGridState = rememberLazyGridState(),
    showTitle: Boolean = true,
    viewModel: PodcastsViewModel = viewModel(),
) {
    val feeds by viewModel.feeds.collectAsState()
    // Collected as state (not one-shot reads): episodes arrive from disk
    // slightly after the ViewModel is created, and a plain
    // episodesByFeed.value read during composition would freeze a
    // momentary empty map into "0 episodes" forever.
    val episodesByFeed by viewModel.episodesByFeed.collectAsState()
    val progressByEpisode by viewModel.episodeProgress.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val addingState by viewModel.addingState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var feedMenuFor by remember { mutableStateOf<String?>(null) }

    // New episodes rail: anything published in the last 7 days, newest first.
    val newEpisodes = remember(episodesByFeed) {
        val now = System.currentTimeMillis()
        val cutoff = now - NEW_EPISODE_WINDOW_MS
        episodesByFeed.values.flatten()
            .filter { it.pubDate in cutoff..now }
            .sortedByDescending { it.pubDate }
            .take(MAX_NEW_EPISODES)
    }
    val feedTitleById = remember(feeds) { feeds.associate { it.id to it.title } }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (showTitle) {
                Text(
                    text = stringResource(R.string.podcasts_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            when {
                isLoading && feeds.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                feeds.isEmpty() -> {
                    EmptyPodcastsMessage(
                        onAddClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    var sort by remember { mutableStateOf(PodcastSort.Name) }
                    val sortedFeeds = remember(feeds, episodesByFeed, sort) {
                        when (sort) {
                            PodcastSort.Name -> feeds.sortedBy { it.title.lowercase() }
                            PodcastSort.MostEpisodes -> feeds.sortedWith(
                                compareByDescending<PodcastFeed> {
                                    episodesByFeed[it.id]?.size ?: 0
                                }.thenBy { it.title.lowercase() }
                            )
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = contentPadding.calculateBottomPadding() + 16.dp,
                            start = 16.dp,
                            end = 16.dp
                        ),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                    ) {
                        // New episodes rail, mirroring the Recently played rail
                        // on the All tab: horizontal cards for anything
                        // published in the last 7 days.
                        if (newEpisodes.isNotEmpty()) {
                            item(
                                key = "new_episodes_header",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Text(
                                    text = stringResource(R.string.podcast_new_episodes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                )
                            }
                            item(
                                key = "new_episodes_rail",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                ) {
                                    items(newEpisodes, key = { "new_${it.id}" }) { episode ->
                                        NewEpisodeCard(
                                            episode = episode,
                                            feedTitle = feedTitleById[episode.feedId].orEmpty(),
                                            progress = progressByEpisode[episode.id],
                                            onClick = { onPlayEpisode(viewModel.songForEpisode(episode)) },
                                        )
                                    }
                                }
                            }
                        }
                        item(
                            key = "podcast_header",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            PodcastSortHeader(
                                feedCount = feeds.size,
                                sort = sort,
                                onSortChange = { sort = it },
                                onAddClick = { showAddDialog = true },
                            )
                        }
                        items(sortedFeeds, key = { it.id }) { feed ->
                            val count = episodesByFeed[feed.id]?.size ?: 0
                            PodcastGridCard(
                                feed = feed,
                                episodeCount = count,
                                onClick = { onFeedClick(feed) },
                                onLongPress = { feedMenuFor = feed.id },
                            )
                            if (feedMenuFor == feed.id) {
                                DropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { feedMenuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.podcast_refresh)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                        },
                                        onClick = {
                                            feedMenuFor = null
                                            viewModel.refreshFeed(feed.id)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.podcast_remove)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        },
                                        onClick = {
                                            feedMenuFor = null
                                            viewModel.removeFeed(feed.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    if (showAddDialog) {
        AddPodcastDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url ->
                showAddDialog = false
                viewModel.addFeed(url)
            },
        )
    }

    (addingState as? PodcastsViewModel.AddFeedState.Error)?.let { errorState ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeAddFeedState() },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeAddFeedState() }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.podcast_error_title)) },
            text = { Text(errorState.message) },
        )
    }
}

@Composable
private fun FeedRow(
    feed: PodcastFeed,
    episodeCount: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (feed.artworkUrl != null) {
            AsyncImage(
                model = feed.artworkUrl,
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
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feed.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (episodeCount > 0) {
                    stringResource(R.string.podcast_episode_count, episodeCount)
                } else {
                    feed.description?.take(80) ?: ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyPodcastsMessage(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = stringResource(R.string.podcasts_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.padding(4.dp))
        Text(
            text = stringResource(R.string.podcasts_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.padding(16.dp))
        TextButton(onClick = onAddClick) {
            Text(stringResource(R.string.podcast_add))
        }
    }
}

@Composable
private fun AddPodcastDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.podcast_add_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.podcast_add_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.podcast_add_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim()) },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.podcast_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/** Sort orders for the Podcasts tab. */
private enum class PodcastSort {
    Name, MostEpisodes
}

/** Window for the "New episodes" rail: episodes published in the last 7 days. */
private const val NEW_EPISODE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
private const val MAX_NEW_EPISODES = 20

/** The count and sort button above the podcast list, matching the Albums tab. */
@Composable
private fun PodcastSortHeader(
    feedCount: Int,
    sort: PodcastSort,
    onSortChange: (PodcastSort) -> Unit,
    onAddClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$feedCount podcasts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = onAddClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.podcast_add),
                    modifier = Modifier.size(20.dp)
                )
            }
            androidx.compose.foundation.layout.Box {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { open = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Rounded.SwapVert,
                    contentDescription = "Sort podcasts",
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("Name") },
                    onClick = { onSortChange(PodcastSort.Name); open = false },
                    leadingIcon = {
                        if (sort == PodcastSort.Name) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Most episodes") },
                    onClick = { onSortChange(PodcastSort.MostEpisodes); open = false },
                    leadingIcon = {
                        if (sort == PodcastSort.MostEpisodes) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
            }
        }
    }
}

/**
 * A podcast in the grid, styled like an album card: square artwork,
 * podcast name below (like album title), episode count below that
 * (like artist name).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastGridCard(
    feed: PodcastFeed,
    episodeCount: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        if (feed.artworkUrl != null) {
            AsyncImage(
                model = feed.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = feed.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.podcast_episode_count, episodeCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A new-episode card for the rail at the top of the Podcasts tab, mirroring
 * the Recently played cards on the All tab: square artwork, episode title,
 * show name, and a thin progress bar when playback is in progress.
 */
@Composable
private fun NewEpisodeCard(
    episode: PodcastEpisode,
    feedTitle: String,
    progress: EpisodeProgress?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(112.dp)
        ) {
            Box {
                if (episode.artworkUrl != null) {
                    AsyncImage(
                        model = episode.artworkUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (progress?.completed == true) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.podcast_episode_played_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                RoundedCornerShape(11.dp)
                            )
                            .padding(2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            episode.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            feedTitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
        )
        val showBar = progress != null && !progress.completed &&
            progress.durationMs > 0 && progress.positionMs > 0
        if (showBar) {
            LinearProgressIndicator(
                progress = {
                    (progress!!.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
