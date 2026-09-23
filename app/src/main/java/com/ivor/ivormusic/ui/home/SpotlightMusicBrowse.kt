package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ArtistItem
import com.ivor.ivormusic.data.MusicShelfItem
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.googleImageAtSize

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.musicBrowseItems(
    state: HomeViewModel.MusicBrowseState?,
    moodTitle: String?,
    onCloseMood: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit,
    onArtistClick: (ArtistItem) -> Unit,
    onPlayTracks: (List<Song>, Song?) -> Unit,
    onMoodClick: (MusicShelfItem.Mood) -> Unit,
    onSongLongPress: ((Song) -> Unit)?,
) {
    if (moodTitle != null) {
        item(key = "mood-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCloseMood) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Text(stringResource(R.string.filter_explore), modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    text = moodTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
    val shelves = state?.shelves.orEmpty()
    if (shelves.isEmpty()) {
        item(key = "browse-status") {
            Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                if (state?.failed == true) {
                    FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
                } else {
                    LoadingIndicator()
                }
            }
        }
        return
    }
    shelves.forEachIndexed { index, shelf ->
        val tracks = shelf.items.filterIsInstance<MusicShelfItem.Track>().map { it.song }
        val artists = shelf.items.filterIsInstance<MusicShelfItem.Artist>().map { it.artist }
        val moods = shelf.items.filterIsInstance<MusicShelfItem.Mood>()
        val collections = shelf.items.filterIsInstance<MusicShelfItem.Collection>()
        if (shelf.title.isNotBlank()) {
            item(key = "bh-$index") {
                SpotlightSectionHeader(
                    title = shelf.title,
                    actionLabel = if (tracks.size > 1) stringResource(R.string.action_play_all) else null,
                    onAction = if (tracks.size > 1) ({ onPlayTracks(tracks, tracks.first()) }) else null,
                )
            }
        }
        item(key = "bs-$index") {
            when {
                moods.isNotEmpty() -> MoodRail(moods, onMoodClick)
                artists.size >= shelf.items.size / 2 && artists.isNotEmpty() ->
                    SpotlightArtistRail(artists = artists, onClick = onArtistClick)
                else -> SpotlightShelf(
                    items = shelf.items.mapNotNull { item ->
                        when (item) {
                            is MusicShelfItem.Collection -> ShelfItem(
                                item.key, item.playlist.name, item.playlist.uploaderName,
                                item.playlist.thumbnailUrl, googleImageAtSize(item.playlist.thumbnailUrl, 512)
                            )
                            is MusicShelfItem.Track -> ShelfItem(
                                item.key, item.song.title, item.song.artist,
                                item.song.thumbnailUrl, item.song.highResThumbnailUrl
                            )
                            else -> null
                        }
                    },
                    onClick = { key ->
                        collections.find { it.key == key }?.let { onPlaylistClick(it.playlist) }
                            ?: tracks.find { "t_${it.id}" == key }?.let { onPlayTracks(tracks, it) }
                    },
                )
            }
        }
    }
    if (state?.continuation != null) {
        item(key = "browse-more") {
            LaunchedEffect(shelves.size) { onLoadMore() }
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }
    }
}

/** Moods and genres as a two-row rail of tonal tiles. */
@androidx.compose.runtime.Composable
private fun MoodRail(moods: List<MusicShelfItem.Mood>, onClick: (MusicShelfItem.Mood) -> Unit) {
    val rows = moods.chunked((moods.size + 1) / 2)
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(row, key = { it.key }) { mood ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.width(148.dp).height(56.dp).clickable { onClick(mood) }
                    ) {
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = mood.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
