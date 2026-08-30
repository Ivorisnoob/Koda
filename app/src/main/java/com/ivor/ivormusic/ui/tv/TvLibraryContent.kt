package com.ivor.ivormusic.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.tv.TvLibraryEntry

/**
 * TV mode's Library: Continue Watching, then the watchlist.
 *
 * Everything here is device-local and renders with no network and no addons
 * installed, which is the point - a library that needs a catalog addon to show
 * you what you saved is not a library.
 *
 * **`items` on a grid scope is the count-based member**, which shadows the
 * list-taking extension and fails on argument names rather than falling
 * through. Every list here goes through `spanItems`, the same route
 * `ChannelTabs.kt` established for exactly this reason.
 */
@Composable
fun TvLibraryContent(
    continueWatching: List<TvContinueRow>,
    watchlist: List<TvLibraryEntry>,
    onEntryClick: (TvLibraryEntry) -> Unit,
    onContinueClick: (TvContinueRow) -> Unit,
    onRemoveFromWatchlist: (TvLibraryEntry) -> Unit,
    onClearProgress: (TvLibraryEntry) -> Unit,
    onBrowse: () -> Unit,
    contentPadding: PaddingValues,
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        state = gridState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
            Text(
                text = stringResource(R.string.tab_library),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (continueWatching.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                Column {
                    TvSectionHeader(title = stringResource(R.string.tv_continue_watching))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueWatching, key = { it.entry.id }) { row ->
                            TvContinueCard(
                                entry = row.entry,
                                progressFraction = row.fraction,
                                subtitle = row.subtitle,
                                onClick = { onContinueClick(row) },
                                onLongClick = { onClearProgress(row.entry) },
                            )
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "watchlist-header") {
            TvSectionHeader(
                title = stringResource(R.string.tv_watchlist),
                trailing = watchlist.size.takeIf { it > 0 }?.toString(),
            )
        }

        if (watchlist.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "watchlist-empty") {
                TvEmptyState(
                    title = stringResource(R.string.tv_watchlist_empty_title),
                    body = stringResource(R.string.tv_watchlist_empty_body),
                    actionLabel = stringResource(R.string.tv_watchlist_empty_action),
                    onAction = onBrowse,
                )
            }
        } else {
            spanItems(watchlist, key = { it.id }) { entry ->
                TvLibraryCard(
                    entry = entry,
                    onClick = { onEntryClick(entry) },
                    onLongClick = { onRemoveFromWatchlist(entry) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Route a list through `LazyGridScope`'s count-based `items` member.
 *
 * Same helper and same reason as `ChannelTabs.kt`: the list-taking extension is
 * shadowed by the member of the same name, and importing both into one file
 * makes it worse rather than better.
 */
private fun <T> androidx.compose.foundation.lazy.grid.LazyGridScope.spanItems(
    list: List<T>,
    key: (T) -> Any,
    span: ((T) -> GridItemSpan)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    items(
        count = list.size,
        key = { index -> key(list[index]) },
        span = span?.let { resolve -> { index -> resolve(list[index]) } },
    ) { index ->
        itemContent(list[index])
    }
}
