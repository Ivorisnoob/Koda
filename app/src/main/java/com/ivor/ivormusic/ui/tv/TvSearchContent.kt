package com.ivor.ivormusic.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.tv.TvItem
import com.ivor.ivormusic.data.tv.TvSearchGroup
import com.ivor.ivormusic.ui.components.SearchField

/**
 * TV mode's Search.
 *
 * Results group by **type** rather than by addon, because nobody searching for
 * a title cares which catalog answered. Deduplication across addons happens in
 * the repository, where it can be tested.
 *
 * Empty query shows the genres the installed manifests declare - the one thing
 * this surface can offer that costs no network at all.
 */
@Composable
fun TvSearchContent(
    query: String,
    results: List<TvSearchGroup>,
    isSearching: Boolean,
    genres: List<String>,
    onQueryChange: (String) -> Unit,
    onItemClick: (TvItem) -> Unit,
    onToggleWatchlist: (TvItem) -> Unit,
    onGenreClick: (String) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.tv_search_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = 8.dp,
                ),
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                query.isBlank() && genres.isNotEmpty() -> {
                    item(key = "genres") {
                        Column {
                            TvSectionHeader(
                                title = stringResource(R.string.tv_browse_by_genre),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            GenreGrid(genres = genres, onGenreClick = onGenreClick)
                        }
                    }
                }

                query.isBlank() -> Unit

                isSearching && results.isEmpty() -> {
                    item(key = "searching") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.LoadingIndicator()
                        }
                    }
                }

                results.isEmpty() -> {
                    item(key = "no-results") {
                        TvEmptyState(
                            title = stringResource(R.string.tv_no_results_title),
                            body = stringResource(R.string.tv_no_results_body, query),
                        )
                    }
                }

                else -> {
                    items(results, key = { it.type }) { group ->
                        Column {
                            TvSectionHeader(
                                title = typeLabel(group.type),
                                trailing = group.items.size.toString(),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                items(group.items, key = { it.id }) { item ->
                                    TvPosterCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onToggleWatchlist(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Types come from addons and are arbitrary strings, so the known ones get a
 * localized label and anything else is shown as the addon named it rather than
 * being hidden or forced into a bucket it does not belong in.
 */
@Composable
private fun typeLabel(type: String): String = when (type.lowercase()) {
    "movie" -> stringResource(R.string.tv_type_movies)
    "series" -> stringResource(R.string.tv_type_series)
    "anime" -> stringResource(R.string.tv_type_anime)
    "tv" -> stringResource(R.string.tv_type_channels)
    else -> type.replaceFirstChar { it.uppercase() }
}

@Composable
private fun GenreGrid(genres: List<String>, onGenreClick: (String) -> Unit) {
    // A flow of chips rather than a grid: genre names vary wildly in length and
    // a fixed column count leaves either ragged gaps or truncated labels.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        genres.forEach { genre ->
            androidx.compose.material3.SuggestionChip(
                onClick = { onGenreClick(genre) },
                label = { Text(genre, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}
