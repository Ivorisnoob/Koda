package com.ivor.ivormusic.ui.tv

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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

    /**
     * Whether a press of Play is still waiting on the source fan-out.
     *
     * Pressing Play means "watch this", not "show me a list": the sheet is a
     * tool for the minority who want a specific release, and making everyone
     * pass through it to reach the majority answer - the one auto-select was
     * already computing - put a decision in front of every single play. While
     * this is set the page shows a spinner and nothing else, the same as
     * opening a video, and the sheet appears only if nothing turned out to be
     * playable.
     */
    var awaitingAutoPlay by remember { mutableStateOf(false) }

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
    val failedAddons by sourcesViewModel.failedAddons.collectAsState()

    val episodes = remember(item, selectedSeason, selectedRange) { viewModel.episodesToShow() }
    val ranges = remember(item, selectedSeason) { viewModel.rangesForCurrentSeason() }
    val watchedIds = remember(progress) {
        progress.filterValues { it.isWatched }.keys.toSet()
    }
    val resume: TvProgress? = remember(progress, item) { viewModel.resumePoint() }

    // Resolve a pending Play once the fan-out has actually finished. Gated on
    // `loaded` rather than on the list being non-empty, because "no addon
    // answered yet" and "no addon has this" are different and only the second
    // is an answer.
    LaunchedEffect(awaitingAutoPlay, sourcesLoaded, sourcesLoading, autoPick) {
        if (!awaitingAutoPlay || sourcesLoading || !sourcesLoaded) return@LaunchedEffect
        val current = item ?: return@LaunchedEffect
        awaitingAutoPlay = false
        val chosen = autoPick?.source
        if (chosen != null) {
            playerViewModel.play(current, sourcesFor, chosen)
        } else {
            // Nothing startable. The sheet is the right place to land: it
            // already names the addons that did not answer and dims the rows
            // that need a debrid service, which is the whole explanation.
            showSources = true
        }
    }

    /** Open the sheet for one episode, or for the film itself. */
    fun openSources(episode: TvEpisode?) {
        val current = item ?: return
        awaitingAutoPlay = false
        sourcesFor = episode
        showSources = true
        // Exhaustive: the sheet is open because the viewer wants the field.
        sourcesViewModel.load(current.type, episode?.id ?: current.id, exhaustive = true)
    }

    /**
     * Press Play: fan out, take auto-select's answer, start.
     *
     * The same load the sheet uses, so choosing manually afterwards costs no
     * second fan-out - the result is already in the ViewModel.
     */
    fun beginPlayback(episode: TvEpisode?) {
        val current = item ?: return
        sourcesFor = episode

        // Nothing installed can produce a file, so there is nothing to wait
        // for. Spinning and then fanning out across zero addons only to land
        // on the same explanation makes a known answer look like a failure -
        // say it immediately instead.
        if (!sourcesViewModel.hasStreamSource()) {
            awaitingAutoPlay = false
            showSources = true
            return
        }

        showSources = false
        awaitingAutoPlay = true
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
        canShare = shareLinkFor(item?.imdbId ?: item?.id.orEmpty()) != null,
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
            beginPlayback(resumeEpisode ?: current?.videos?.firstOrNull())
        },
        onPlayEpisode = { episode -> beginPlayback(episode) },
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

    // Covers the page while Play is resolving, so the wait reads as "it is
    // starting" rather than as a dead button. It also swallows input, which
    // stops a second press queueing another fan-out behind the first.
    if (awaitingAutoPlay) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) {
            LoadingIndicator()
        }
    }

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
            failedAddons = failedAddons,
            onPlay = { source -> startPlayback(source) },
            onOpenExternal = { link ->
                // Handed to the system rather than opened in a WebView: this is
                // a third-party page Koda knows nothing about, and wrapping it
                // would imply it is part of the app.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link))
                    )
                }
            },
            onSetResolution = sourcesViewModel::setResolution,
            onSetLanguage = sourcesViewModel::setLanguage,
            onSetSourceQuality = sourcesViewModel::setSourceQuality,
            onSetCachedOnly = sourcesViewModel::setCachedOnly,
            onSetDub = sourcesViewModel::setDub,
            onClearFilters = sourcesViewModel::clearFilters,
            onRetry = {
                val current = item
                if (current != null) {
                    sourcesViewModel.load(
                        current.type,
                        sourcesFor?.id ?: current.id,
                        force = true,
                    )
                }
            },
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
