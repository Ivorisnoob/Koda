package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SubgenreShelf

private enum class ExploreFilter {
    All, Liked, Downloads, Recent
}

/**
 * The Explore tab: a place to discover what's in the library, separate from
 * Search. Quick Picks filtered by source, plus an Artist Spotlight for the
 * most-played artist.
 */
@Composable
fun ExploreContent(
    songs: List<Song>,
    likedSongs: List<Song>,
    downloadedSongs: List<Song>,
    recentlyPlayed: List<Song>,
    topArtists: List<TopArtist>,
    artistPhotos: Map<String, String>,
    newFromLiked: List<Song> = emptyList(),
    subgenreShelves: List<SubgenreShelf> = emptyList(),
    recommendations: List<Song> = emptyList(),
    onPlaySongs: (List<Song>, Song?) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    onArtistClick: (String) -> Unit,
    onArtistRadio: (String) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
) {
    var filter by rememberSaveable { mutableStateOf(ExploreFilter.All) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // When searching, filter the library; otherwise show Explore content.
    val searchResults = remember(searchQuery, songs) {
        val q = searchQuery.trim()
        if (q.isBlank()) emptyList()
        else songs.filter {
            it.title.contains(q, ignoreCase = true) ||
            it.artist.contains(q, ignoreCase = true) ||
            it.album.contains(q, ignoreCase = true)
        }.distinctBy { it.id }.take(50)
    }

    val quickPicks = remember(filter, songs, likedSongs, downloadedSongs, recentlyPlayed) {
        when (filter) {
            ExploreFilter.All -> songs
            ExploreFilter.Liked -> likedSongs
            ExploreFilter.Downloads -> downloadedSongs
            ExploreFilter.Recent -> recentlyPlayed
        }.distinctBy { it.id }.take(24)
    }

    val spotlightArtist = remember(topArtists) { topArtists.firstOrNull() }
    val spotlightSongs = remember(spotlightArtist, songs, likedSongs) {
        val name = spotlightArtist?.name ?: return@remember emptyList()
        (songs + likedSongs).distinctBy { it.id }
            .filter { it.artist.equals(name, ignoreCase = true) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "explore_header") {
            Text(
                stringResource(R.string.tab_explore),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // Search bar: no auto-focus, keyboard only opens on tap.
        item(key = "explore_search") {
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        androidx.compose.material3.IconButton(
                            onClick = { searchQuery = "" }
                        ) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = null,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        }

        item(key = "explore_chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ExploreFilter.entries) { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = {
                            Text(
                                when (entry) {
                                    ExploreFilter.All -> stringResource(R.string.explore_filter_all)
                                    ExploreFilter.Liked -> stringResource(R.string.explore_filter_liked)
                                    ExploreFilter.Downloads -> stringResource(R.string.explore_filter_downloads)
                                    ExploreFilter.Recent -> stringResource(R.string.explore_filter_recent)
                                }
                            )
                        },
                    )
                }
            }
        }

        if (quickPicks.isNotEmpty()) {
            item(key = "quick_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.explore_quick_picks),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "quick_picks") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(quickPicks, key = { it.id }) { song ->
                        ExplorePickCard(
                            song = song,
                            onClick = { onPlaySongs(quickPicks, song) },
                        )
                    }
                }
            }
        }

        if (spotlightArtist != null) {
            item(key = "spotlight") {
                val photo = artistPhotos[spotlightArtist.name]
                    ?: spotlightArtist.fallbackArtwork
                val topTrack = spotlightSongs.firstOrNull()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.explore_artist_spotlight),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (photo != null) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    spotlightArtist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (topTrack != null) {
                                    Text(
                                        stringResource(
                                            R.string.explore_top_track,
                                            topTrack.title,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onArtistRadio(spotlightArtist.name) },
                                enabled = spotlightSongs.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.explore_artist_radio))
                            }
                            OutlinedButton(
                                onClick = { onArtistClick(spotlightArtist.name) },
                            ) {
                                Text(stringResource(R.string.explore_view_artist))
                            }
                        }
                    }
                }
            }

            // New tracks by the listener's most-liked artists.
            if (newFromLiked.isNotEmpty()) {
                item(key = "explore_new_header") {
                    Text(
                        stringResource(R.string.explore_new_from_liked),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                item(key = "explore_new_rail") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(newFromLiked, key = { "new_${it.id}" }) { song ->
                            ExplorePickCard(
                                song = song,
                                onClick = { onPlaySongs(newFromLiked, song) },
                            )
                        }
                    }
                }
            }

            // Subgenre shelves: popular songs from similar artists.
            subgenreShelves.forEachIndexed { index, shelf ->
                item(key = "explore_subgenre_header_$index") {
                    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                        Text(
                            stringResource(R.string.explore_subgenre_title, shelf.subgenre),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (shelf.basedOn.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.explore_subgenre_sub,
                                    shelf.basedOn.take(2).joinToString(", "),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "explore_subgenre_rail_$index") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shelf.songs, key = { "sub_${index}_${it.id}" }) { song ->
                            ExplorePickCard(
                                song = song,
                                onClick = { onPlaySongs(shelf.songs, song) },
                            )
                        }
                    }
                }
            }

            // Top artists rail: the listener's most-played artists.
            if (topArtists.isNotEmpty()) {
                item(key = "explore_artists_header") {
                    Text(
                        stringResource(R.string.explore_top_artists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                item(key = "explore_artists_rail") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(topArtists.take(10), key = { "artist_${it.name}" }) { artist ->
                            ExploreArtistCard(
                                artist = artist,
                                photo = artistPhotos[artist.name] ?: artist.fallbackArtwork,
                                onClick = { onArtistClick(artist.name) },
                            )
                        }
                    }
                }
            }

            // Recommended for you.
            if (recommendations.isNotEmpty()) {
                item(key = "explore_recommended_header") {
                    Text(
                        stringResource(R.string.explore_recommended),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                item(key = "explore_recommended_rail") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recommendations, key = { "rec_${it.id}" }) { song ->
                            ExplorePickCard(
                                song = song,
                                onClick = { onPlaySongs(recommendations, song) },
                            )
                        }
                    }
                }
            }
        }

        if (quickPicks.isEmpty() && spotlightArtist == null) {
            item(key = "explore_empty") {
                Text(
                    stringResource(R.string.explore_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                )
            }
        } else {
            // Search results: songs matching the query.
            if (searchResults.isEmpty()) {
                item(key = "search_empty") {
                    Text(
                        stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                    )
                }
            } else {
                items(
                    items = searchResults,
                    key = { it.id },
                ) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaySongs(searchResults, song) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorePickCard(
    song: Song,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        val art = song.albumArtUri ?: song.highResThumbnailUrl ?: song.thumbnailUrl
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                modifier = Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A circular artist card for the Top Artists rail: photo, name, play count.
 */
@Composable
private fun ExploreArtistCard(
    artist: TopArtist,
    photo: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.explore_artist_plays, artist.plays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
