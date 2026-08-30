package com.ivor.ivormusic.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.tv.TvEpisode
import com.ivor.ivormusic.data.tv.TvItem

/**
 * One title's page: backdrop, actions, synopsis, episodes.
 *
 * A NavHost route rather than in-tab state, because it is entered from Home,
 * Search and Library and back has to unwind through whichever of them was used.
 * Both player overlays step aside on the way in, the same as the channel screen.
 */
@Composable
fun TvDetailScreen(
    item: TvItem?,
    isLoading: Boolean,
    failed: Boolean,
    isSaved: Boolean,
    seasons: List<Int>,
    selectedSeason: Int?,
    ranges: List<EpisodeRange>,
    selectedRange: EpisodeRange?,
    episodes: List<TvEpisode>,
    resumeLabel: String?,
    watchedEpisodeIds: Set<String>,
    episodeProgress: (String) -> Float,
    onBack: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectRange: (EpisodeRange) -> Unit,
    onToggleWatchlist: () -> Unit,
    onPlay: () -> Unit,
    onPlayEpisode: (TvEpisode) -> Unit,
    onToggleWatched: (TvEpisode, Boolean) -> Unit,
    onPlayTrailer: (String) -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            item == null && isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                LoadingIndicator()
            }

            item == null && failed -> TvEmptyState(
                title = stringResource(R.string.tv_detail_failed_title),
                body = stringResource(R.string.tv_detail_failed_body),
                actionLabel = stringResource(R.string.tv_retry),
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )

            item != null -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "backdrop") { DetailBackdrop(item) }

                item(key = "meta") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = listOfNotNull(
                                item.releaseInfo?.takeIf { it.isNotBlank() },
                                item.runtime?.takeIf { it.isNotBlank() },
                                item.imdbRating?.takeIf { it.isNotBlank() }?.let { "$it/10" },
                                item.country?.takeIf { it.isNotBlank() },
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.genres.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(item.genres, key = { it }) { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp, vertical = 5.dp
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "actions") {
                    DetailActions(
                        item = item,
                        isSaved = isSaved,
                        resumeLabel = resumeLabel,
                        onPlay = onPlay,
                        onToggleWatchlist = onToggleWatchlist,
                        onPlayTrailer = onPlayTrailer,
                        onShare = onShare,
                    )
                }

                item.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
                    item(key = "synopsis") { Synopsis(synopsis) }
                }

                if (item.hasEpisodes) {
                    if (seasons.size > 1) {
                        item(key = "seasons") {
                            SeasonSelector(seasons, selectedSeason, onSelectSeason)
                        }
                    }
                    if (ranges.isNotEmpty()) {
                        item(key = "ranges") {
                            RangeSelector(ranges, selectedRange, onSelectRange)
                        }
                    }
                    items(episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            watched = episode.id in watchedEpisodeIds,
                            progress = episodeProgress(episode.id),
                            onClick = { onPlayEpisode(episode) },
                            onToggleWatched = {
                                onToggleWatched(episode, episode.id !in watchedEpisodeIds)
                            },
                        )
                    }
                }

                if (item.cast.isNotEmpty()) {
                    item(key = "cast") { CastRow(item.cast) }
                }
            }
        }

        // A back affordance over the backdrop, which has no app bar of its own.
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun DetailBackdrop(item: TvItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        AsyncImage(
            model = item.background ?: item.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        BoxScopeScrim(heightFraction = 0.85f)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TvArtwork(
                url = item.poster,
                title = item.name,
                modifier = Modifier
                    .width(76.dp)
                    .aspectRatio(TV_POSTER_RATIO),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
                if (!item.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = item.logo,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier
                            .heightIn(max = 60.dp)
                            .widthIn(max = 210.dp),
                    )
                } else {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailActions(
    item: TvItem,
    isSaved: Boolean,
    resumeLabel: String?,
    onPlay: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onPlayTrailer: (String) -> Unit,
    onShare: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(resumeLabel ?: stringResource(R.string.tv_play))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onToggleWatchlist, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (isSaved) R.string.tv_in_watchlist else R.string.tv_watchlist_add
                    ),
                    maxLines = 1,
                )
            }
            // Koda plays YouTube natively, so a trailer opens in the app's own
            // player rather than an external one. Stremio needs a separate addon
            // for this; here it is the video pipeline that already exists.
            item.trailerYoutubeId?.let { trailerId ->
                FilledTonalButton(onClick = { onPlayTrailer(trailerId) }) {
                    Icon(
                        Icons.Rounded.Videocam,
                        contentDescription = stringResource(R.string.tv_trailer),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            FilledTonalButton(onClick = onShare) {
                Icon(
                    Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.action_share),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun Synopsis(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        ) {
            Text(
                stringResource(if (expanded) R.string.tv_show_less else R.string.tv_show_more)
            )
        }
    }
}

@Composable
private fun SeasonSelector(
    seasons: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(seasons, key = { it }) { season ->
            FilterChip(
                selected = season == selected,
                onClick = { onSelect(season) },
                label = {
                    Text(
                        if (season == 0) stringResource(R.string.tv_specials)
                        else stringResource(R.string.tv_season, season)
                    )
                },
            )
        }
    }
}

@Composable
private fun RangeSelector(
    ranges: List<EpisodeRange>,
    selected: EpisodeRange?,
    onSelect: (EpisodeRange) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(ranges, key = { it.label }) { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: TvEpisode,
    watched: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                TvArtwork(
                    url = episode.thumbnail,
                    title = episode.displayTitle,
                    modifier = Modifier
                        .width(118.dp)
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(10.dp),
                )
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.45f),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = episode.episodeNumber?.let {
                        stringResource(R.string.tv_episode_prefix, it, episode.displayTitle)
                    } ?: episode.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (watched) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            IconButton(onClick = onToggleWatched, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.tv_mark_watched),
                    tint = if (watched) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Cast as names, not avatars.
 *
 * Cinemeta gives names and no photographs. A row of grey circles with initials
 * would be a worse row than one made of the information that actually exists.
 */
@Composable
private fun CastRow(cast: List<String>) {
    Column(Modifier.padding(top = 12.dp)) {
        TvSectionHeader(
            title = stringResource(R.string.tv_cast),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(cast.take(20), key = { it }) { name ->
                AssistChip(onClick = {}, label = { Text(name) })
            }
        }
    }
}
