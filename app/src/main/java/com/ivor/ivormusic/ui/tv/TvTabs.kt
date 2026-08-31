package com.ivor.ivormusic.ui.tv

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivor.ivormusic.data.AppMode
import com.ivor.ivormusic.ui.components.AppModeToggleState

/**
 * Adapters between [TvViewModel] and the three content composables.
 *
 * They live here rather than in `HomeScreen.kt` so the tab dispatch there stays
 * a list of destinations rather than growing three more bodies of wiring, and
 * so the content composables stay free of a ViewModel dependency and remain
 * previewable.
 */

@Composable
fun TvHomeTab(
    viewModel: TvViewModel?,
    contentPadding: PaddingValues,
    appMode: AppMode,
    onAppModeChange: (AppMode) -> Unit,
    showModeToggle: Boolean,
    modeToggleState: AppModeToggleState,
    listState: LazyListState,
    onOpenDetail: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExtensions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (viewModel == null) return

    // The addon list is not process-wide, so installing one elsewhere is only
    // visible here by asking. Resume is exactly when to ask: coming back from
    // the addon manager is the one path that changes this.
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) {
        viewModel.refreshAddons()
        onPauseOrDispose { }
    }

    val shelves by viewModel.shelves.collectAsState()
    val isLoading by viewModel.isLoadingHome.collectAsState()
    val hasLoaded by viewModel.hasLoadedHome.collectAsState()
    val hasStreamSource by viewModel.hasStreamSource.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val progress by viewModel.progress.collectAsState()

    // Recomputed whenever progress changes, which is what makes a row disappear
    // the moment something is finished rather than on the next Home load.
    val continueRows = remember(progress, viewModel) { viewModel.continueRows() }

    // The hero draws from the leading shelf, so it reflects whichever catalog
    // the user put first rather than a source chosen here.
    val heroItems = remember(shelves) {
        shelves.firstOrNull { it.items.isNotEmpty() }?.items.orEmpty()
    }

    TvHomeContent(
        shelves = shelves,
        heroItems = heroItems,
        continueWatching = continueRows,
        isLoading = isLoading,
        hasLoaded = hasLoaded,
        hasStreamSource = hasStreamSource,
        isSaved = { id -> watchlist.any { it.id == id } },
        onItemClick = { item -> onOpenDetail(item.type, item.id) },
        onContinueClick = { row -> onOpenDetail(row.entry.type, row.entry.id) },
        onContinueDismiss = { row -> viewModel.clearProgressFor(row.entry.id) },
        onToggleWatchlist = { item -> viewModel.toggleWatchlist(item) },
        onSelectGenre = { key, genre -> viewModel.selectGenre(key, genre) },
        onLoadMore = { key -> viewModel.loadMore(key) },
        onRefresh = { viewModel.loadHome(forceFresh = true) },
        onOpenSettings = onOpenSettings,
        onOpenExtensions = onOpenExtensions,
        contentPadding = contentPadding,
        appMode = appMode,
        onAppModeChange = onAppModeChange,
        showModeToggle = showModeToggle,
        modeToggleState = modeToggleState,
        listState = listState,
        modifier = modifier,
    )
}

@Composable
fun TvSearchTab(
    viewModel: TvViewModel?,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onOpenDetail: (String, String) -> Unit,
    onBrowseGenre: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (viewModel == null) return
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val shelves by viewModel.shelves.collectAsState()

    // Genres come from the installed manifests, so the empty-query state costs
    // nothing and reflects what is actually installed.
    val genres = remember(shelves) {
        shelves.flatMap { it.genres }.distinct().sorted()
    }

    TvSearchContent(
        query = query,
        results = results,
        isSearching = isSearching,
        recentSearches = recentSearches,
        genres = genres,
        onQueryChange = { viewModel.search(it) },
        onItemClick = { item ->
            viewModel.rememberSearch()
            onOpenDetail(item.type, item.id)
        },
        onToggleWatchlist = { item -> viewModel.toggleWatchlist(item) },
        onSubmitSearch = { viewModel.rememberSearch() },
        onRecentSearch = { query -> viewModel.search(query) },
        onRemoveRecentSearch = viewModel::removeRecentSearch,
        onClearRecentSearches = viewModel::clearRecentSearches,
        // Filters the catalogs and hands back to Home, rather than running a
        // text search for the word - searching "Action" returns titles with
        // Action in the name, which is not what tapping a genre means.
        onGenreClick = { genre ->
            viewModel.browseGenre(genre)
            onBrowseGenre()
        },
        contentPadding = contentPadding,
        listState = listState,
        modifier = modifier,
    )
}

@Composable
fun TvLibraryTab(
    viewModel: TvViewModel?,
    contentPadding: PaddingValues,
    onOpenDetail: (String, String) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (viewModel == null) return
    val watchlist by viewModel.watchlist.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val watchedItems by viewModel.watchedItems.collectAsState()
    val continueRows = remember(progress, viewModel) { viewModel.continueRows() }
    val watchedHistory = remember(progress, watchedItems) {
        val watchedIds = progress.values.filter { it.isWatched }.mapTo(HashSet()) { it.itemId }
        watchedItems.filter { it.id in watchedIds }
    }

    TvLibraryContent(
        continueWatching = continueRows,
        watchlist = watchlist,
        watchedHistory = watchedHistory,
        onEntryClick = { entry -> onOpenDetail(entry.type, entry.id) },
        onContinueClick = { row -> onOpenDetail(row.entry.type, row.entry.id) },
        onRemoveFromWatchlist = { entry -> viewModel.removeFromWatchlist(entry.id) },
        onClearProgress = { entry -> viewModel.clearProgressFor(entry.id) },
        onClearAllHistory = viewModel::clearAllProgress,
        onBrowse = onBrowse,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

/**
 * Continue Watching rows, with the episode label resolved.
 *
 * The label is assembled without a string resource because these rows are
 * recomputed inside `remember`, which cannot call composables. "S2 E4" is the
 * one label in TV mode that reads the same in every locale it is shown in - it
 * is two letters and two numbers - so this is not a translation gap left open.
 */
private fun TvViewModel.continueRows(): List<TvContinueRow> =
    continueWatching().map { (entry, progress) ->
        TvContinueRow(
            entry = entry,
            fraction = progress.fraction,
            subtitle = when {
                progress.season != null && progress.episode != null ->
                    "S${progress.season} E${progress.episode}"
                progress.episode != null -> "E${progress.episode}"
                else -> null
            },
            episodeId = progress.episodeId,
        )
    }
