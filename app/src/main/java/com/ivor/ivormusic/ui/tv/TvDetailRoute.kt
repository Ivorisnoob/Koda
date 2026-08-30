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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivor.ivormusic.data.tv.TvItemCache
import com.ivor.ivormusic.data.tv.TvProgress

/**
 * Binds [TvDetailViewModel] to [TvDetailScreen].
 *
 * The ViewModel is scoped to this nav entry, so a related title opened from
 * here gets its own instance and back finds this one as it was left - the same
 * contract `ChannelScreen` has, and for the same reason.
 */
@Composable
fun TvDetailRoute(
    type: String,
    id: String,
    onBack: () -> Unit,
    onPlayTrailer: (String, String) -> Unit,
    onOpenAddons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TvDetailViewModel = viewModel()
    val context = LocalContext.current
    var showSources by remember { mutableStateOf(false) }

    // The item the user actually tapped, remembered as it went past in a
    // catalog or search response. Present almost always, and its absence costs
    // one spinner rather than a failure.
    val seed = remember(id) { TvItemCache.get(id) }

    LaunchedEffect(type, id) { viewModel.load(type, id, seed) }

    val item by viewModel.item.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val progress by viewModel.progress.collectAsState()

    val episodes = remember(item, selectedSeason, selectedRange) { viewModel.episodesToShow() }
    val ranges = remember(item, selectedSeason) { viewModel.rangesForCurrentSeason() }
    val watchedIds = remember(progress) {
        progress.filterValues { it.isWatched }.keys.toSet()
    }
    val resume: TvProgress? = remember(progress, item) { viewModel.resumePoint() }

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
                p.season != null && p.episode != null -> "Resume S${p.season} E${p.episode}"
                else -> "Resume"
            }
        },
        watchedEpisodeIds = watchedIds,
        episodeProgress = { episodeId -> progress[episodeId]?.fraction ?: 0f },
        onBack = onBack,
        onSelectSeason = viewModel::selectSeason,
        onSelectRange = viewModel::selectRange,
        onToggleWatchlist = { viewModel.toggleWatchlist() },
        // Playback arrives in the next phase. Until it does, both actions open
        // the source sheet, which is where the honest explanation lives - a
        // primary button that appears to work and silently does nothing is
        // worse than one that says what is missing.
        onPlay = { showSources = true },
        onPlayEpisode = { showSources = true },
        onToggleWatched = viewModel::markWatched,
        onPlayTrailer = { youtubeId -> onPlayTrailer(youtubeId, item?.name.orEmpty()) },
        onShare = {
            val current = item ?: return@TvDetailScreen
            val link = shareLinkFor(current.imdbId ?: current.id)
            if (link != null) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    this.type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${current.name}\n$link")
                }
                context.startActivity(Intent.createChooser(send, current.name))
            }
        },
        onRetry = { viewModel.load(type, id, seed, forceFresh = true) },
        modifier = modifier,
    )

    if (showSources) {
        TvSourceSheet(
            title = item?.name.orEmpty(),
            hasStreamSource = viewModel.hasStreamSource(),
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
    id.startsWith("tt") -> "https://www.imdb.com/title/$id/"
    id.startsWith("kitsu:") -> "https://kitsu.app/anime/${id.removePrefix("kitsu:")}"
    else -> null
}
