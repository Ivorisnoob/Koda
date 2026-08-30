package com.ivor.ivormusic.ui.tv

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivor.ivormusic.data.tv.TvEpisode
import com.ivor.ivormusic.data.tv.TvItemCache
import com.ivor.ivormusic.data.tv.TvProgress
import com.ivor.ivormusic.data.tv.TvSource

/**
 * Binds [TvDetailViewModel] to [TvDetailScreen], and is where a title becomes
 * playback.
 *
 * The detail ViewModel is scoped to this nav entry, so a related title opened
 * from here gets its own instance and back finds this one as it was left - the
 * same contract `ChannelScreen` has. [TvPlayerViewModel] is the opposite: it is
 * passed in from the activity, because playback has to outlive this screen.
 */
@Composable
fun TvDetailRoute(
    type: String,
    id: String,
    playerViewModel: TvPlayerViewModel,
    onBack: () -> Unit,
    onPlayTrailer: (String, String) -> Unit,
    onOpenAddons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TvDetailViewModel = viewModel()
    val sourcesViewModel: TvSourcesViewModel = viewModel()
    val context = LocalContext.current

    // Which episode the sheet is currently about. Null while it is closed, and
    // set to the item's own id for a film - the same shape the progress store
    // and the stream endpoint both use, so nothing has to reconstruct it.
    var sourcesFor by remember { mutableStateOf<TvEpisode?>(null) }
    var showSources by remember { mutableStateOf(false) }

    // The item the user actually tapped, remembered as it went past in a
    // catalog or search response. Present almost always, and its absence costs
    // one spinner rather than a failure.
    val seed = remember(id) { TvItemCache.get(id) }

    LaunchedEffect(type, id) { viewModel.load(type, id, seed) }


    // Installing an addon happens on another screen, and this one holds its own
    // repository instance. Without a re-read on resume, adding a source addon
    // and coming back leaves the sheet still saying nothing is installed.
    LifecycleResumeEffect(sourcesViewModel) {
        sourcesViewModel.refreshAddons()
        onPauseOrDispose { }
    }

    val item by viewModel.item.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val progress by viewModel.progress.collectAsState()

    val sourcesLoading by sourcesViewModel.isLoading.collectAsState()
    val sourcesLoaded by sourcesViewModel.loaded.collectAsState()
    val visibleSources by sourcesViewModel.visible.collectAsState()
    val autoPick by sourcesViewModel.autoPick.collectAsState()
    val facets by sourcesViewModel.facets.collectAsState()
    val filter by sourcesViewModel.filter.collectAsState()
    val sourcesTotal by sourcesViewModel.totalCount.collectAsState()

    val episodes = remember(item, selectedSeason, selectedRange) { viewModel.episodesToShow() }
    val ranges = remember(item, selectedSeason) { viewModel.rangesForCurrentSeason() }
    val watchedIds = remember(progress) {
        progress.filterValues { it.isWatched }.keys.toSet()
    }
    val resume: TvProgress? = remember(progress, item) { viewModel.resumePoint() }

    // The player asking for a different release. It has already closed itself,
    // and this page is still composed underneath, so the sheet simply reopens
    // on the same episode with the list it already has - no second fan-out.
    val sourceChangeRequest by playerViewModel.sourceChangeRequest.collectAsState()
    LaunchedEffect(sourceChangeRequest) {
        val requested = sourceChangeRequest ?: return@LaunchedEffect
        val current = item
        if (current != null) {
            sourcesFor = current.videos.firstOrNull { it.id == requested }
            showSources = true
            sourcesViewModel.load(current.type, requested)
        }
        playerViewModel.consumeSourceChangeRequest()
    }

    /** Open the sheet for one episode, or for the film itself. */
    fun openSources(episode: TvEpisode?) {
        val current = item ?: return
        sourcesFor = episode
        showSources = true
        sourcesViewModel.load(current.type, episode?.id ?: current.id)
    }

    /** Play a chosen release, and leave the sheet behind rather than under it. */
    fun startPlayback(source: TvSource) {
        val current = item ?: return
        showSources = false
        playerViewModel.play(current, sourcesFor, source)
    }

    TvDetailScreen(
        item = item,
        isLoading = isLoading,
        failed = failed,
        isSaved = watchlist.any { it.id == id },
        seasons = item?.seasons.orEmpty(),
        selectedSeason = selectedSeason,
        ranges = ranges,
        selectedRange = selectedRange,
        episodes = episodes,
        // The Play button says what it will actually do. "Play" on something
        // half-watched is the button lying about where it will start.
        resumeLabel = resume?.let { p ->
            when {
                p.season != null && p.episode != null -> "Resume S" + p.season + " E" + p.episode
                else -> "Resume"
            }
        },
        watchedEpisodeIds = watchedIds,
        episodeProgress = { episodeId -> progress[episodeId]?.fraction ?: 0f },
        onBack = onBack,
        onSelectSeason = viewModel::selectSeason,
        onSelectRange = viewModel::selectRange,
        onToggleWatchlist = { viewModel.toggleWatchlist() },
        // Play resumes where the viewer left off, which for a series means the
        // episode they were partway through rather than the first one.
        onPlay = {
            val current = item
            val resumeEpisode = resume?.episodeId
                ?.let { episodeId -> current?.videos?.firstOrNull { it.id == episodeId } }
            openSources(resumeEpisode ?: current?.videos?.firstOrNull())
        },
        onPlayEpisode = { episode -> openSources(episode) },
        onToggleWatched = viewModel::markWatched,
        onPlayTrailer = { youtubeId -> onPlayTrailer(youtubeId, item?.name.orEmpty()) },
        onShare = {
            val current = item ?: return@TvDetailScreen
            val link = shareLinkFor(current.imdbId ?: current.id)
            if (link != null) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    this.type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, current.name + "\n" + link)
                }
                context.startActivity(Intent.createChooser(send, current.name))
            }
        },
        onRetry = { viewModel.load(type, id, seed, forceFresh = true) },
        modifier = modifier,
    )

    if (showSources) {
        TvSourceSheet(
            title = listOfNotNull(
                item?.name,
                sourcesFor?.let { episodeLabel(it) }?.takeIf { it.isNotBlank() },
            ).joinToString("  "),
            hasStreamSource = sourcesViewModel.hasStreamSource(),
            isLoading = sourcesLoading,
            loaded = sourcesLoaded,
            sources = visibleSources,
            totalCount = sourcesTotal,
            autoPick = autoPick,
            facets = facets,
            filter = filter,
            onPlay = { source -> startPlayback(source) },
            onSetResolution = sourcesViewModel::setResolution,
            onSetLanguage = sourcesViewModel::setLanguage,
            onSetSourceQuality = sourcesViewModel::setSourceQuality,
            onSetCachedOnly = sourcesViewModel::setCachedOnly,
            onSetDub = sourcesViewModel::setDub,
            onClearFilters = sourcesViewModel::clearFilters,
            onBrowseAddons = {
                showSources = false
                onOpenAddons()
            },
            onDismiss = { showSources = false },
        )
    }
}

/**
 * A shareable link for an item.
 *
 * IMDb ids get an imdb.com link, which is the one destination anyone receiving
 * it can open. Anime-native ids have no equivalent public page that is reliably
 * correct, so sharing is not offered rather than sharing something wrong.
 */
internal fun shareLinkFor(id: String): String? = when {
    id.startsWith("tt") -> "https://www.imdb.com/title/" + id + "/"
    id.startsWith("kitsu:") -> "https://kitsu.app/anime/" + id.removePrefix("kitsu:")
    else -> null
}
