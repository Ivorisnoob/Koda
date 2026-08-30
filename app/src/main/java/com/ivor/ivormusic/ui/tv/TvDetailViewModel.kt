package com.ivor.ivormusic.ui.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.tv.TvEpisode
import com.ivor.ivormusic.data.tv.TvItem
import com.ivor.ivormusic.data.tv.TvProgress
import com.ivor.ivormusic.data.tv.TvProgressRepository
import com.ivor.ivormusic.data.tv.TvRepository
import com.ivor.ivormusic.data.tv.TvWatchlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A contiguous block of episodes, for shows too long to list in one go. */
data class EpisodeRange(val first: Int, val last: Int) {
    val label: String get() = "$first-$last"
}

/**
 * One title's detail page.
 *
 * Scoped to its navigation entry rather than the app, the same as
 * `ChannelViewModel`: opening a related title from inside a detail page gives
 * the second its own instance, and back finds the first exactly as it was left.
 */
class TvDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TvRepository(application)
    private val watchlistRepository = TvWatchlistRepository(application)
    private val progressRepository = TvProgressRepository(application)

    private val _item = MutableStateFlow<TvItem?>(null)
    val item: StateFlow<TvItem?> = _item.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private val _selectedSeason = MutableStateFlow<Int?>(null)
    val selectedSeason: StateFlow<Int?> = _selectedSeason.asStateFlow()

    private val _selectedRange = MutableStateFlow<EpisodeRange?>(null)
    val selectedRange: StateFlow<EpisodeRange?> = _selectedRange.asStateFlow()

    val watchlist = watchlistRepository.watchlist
    val progress: StateFlow<Map<String, TvProgress>> = progressRepository.progress

    private var loadedKey: String? = null

    /**
     * Load a title.
     *
     * [seed] is the catalog item the user tapped, which already carries poster,
     * backdrop, logo, synopsis and cast because Cinemeta's catalogs return full
     * metas. Publishing it first is what makes the page appear instantly with
     * real content rather than a spinner, while the `meta` call fetches the one
     * thing a catalog item lacks: the episode list.
     */
    fun load(type: String, id: String, seed: TvItem? = null, forceFresh: Boolean = false) {
        val key = "$type/$id"
        if (loadedKey == key && !forceFresh && _item.value != null) return
        loadedKey = key

        if (seed != null && seed.id == id) _item.value = seed

        // A movie that arrived with everything needs no second request at all,
        // and a series that already carries its episodes does not either.
        if (seed != null && seed.id == id && (seed.hasEpisodes || !seed.type.needsEpisodes())) {
            selectInitialSeason(seed)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _failed.value = false
            val full = repository.meta(type, id, forceFresh)
            if (full != null) {
                _item.value = full
                selectInitialSeason(full)
            } else if (_item.value == null) {
                _failed.value = true
            }
            _isLoading.value = false
        }
    }

    /**
     * Open on the season the viewer is partway through, else the first real one.
     *
     * Specials are season 0 and never lead: a show opening on its OVAs rather
     * than episode one is the page arguing with the viewer.
     */
    private fun selectInitialSeason(item: TvItem) {
        if (!item.hasEpisodes) return
        val inProgress = progressRepository.resumePointFor(item.id)?.season
        val seasons = item.seasons
        val season = inProgress?.takeIf { seasons.contains(it) }
            ?: seasons.firstOrNull()
            ?: item.videos.firstOrNull()?.season
        _selectedSeason.value = season
        _selectedRange.value = rangesFor(item, season).firstOrNull()
        // Land on the range holding the resume point rather than the first.
        val resumeEpisode = progressRepository.resumePointFor(item.id)?.episode
        if (resumeEpisode != null) {
            rangesFor(item, season).firstOrNull { resumeEpisode in it.first..it.last }
                ?.let { _selectedRange.value = it }
        }
    }

    fun selectSeason(season: Int) {
        _selectedSeason.value = season
        _selectedRange.value = rangesFor(_item.value, season).firstOrNull()
    }

    fun selectRange(range: EpisodeRange) {
        _selectedRange.value = range
    }

    fun episodesToShow(): List<TvEpisode> {
        val item = _item.value ?: return emptyList()
        val season = _selectedSeason.value ?: return item.videos
        val episodes = item.episodesInSeason(season)
        val range = _selectedRange.value ?: return episodes
        return episodes.filter { (it.episodeNumber ?: 0) in range.first..range.last }
    }

    fun rangesForCurrentSeason(): List<EpisodeRange> =
        rangesFor(_item.value, _selectedSeason.value)

    /** Whether anything installed could play this. Read fresh, not observed. */
    fun hasStreamSource(): Boolean = !repository.addons.hasNoStreamSource()

    fun isSaved(): Boolean = watchlistRepository.isSaved(_item.value?.id)

    fun toggleWatchlist(): Boolean {
        val item = _item.value ?: return false
        return watchlistRepository.toggle(item)
    }

    fun resumePoint(): TvProgress? = _item.value?.let { progressRepository.resumePointFor(it.id) }

    fun progressFor(episodeId: String): TvProgress? = progressRepository.forEpisode(episodeId)

    fun markWatched(episode: TvEpisode, watched: Boolean) {
        val item = _item.value ?: return
        if (watched) {
            // No duration is known before playback, so a manual mark stores a
            // nominal one. It only ever feeds isWatched, never a progress bar.
            progressRepository.markWatched(item, episode.id, NOMINAL_DURATION_MS)
        } else {
            progressRepository.record(item, episode.id, 0, NOMINAL_DURATION_MS,
                episode.season, episode.episodeNumber)
        }
    }

    companion object {
        /** Beyond this, a season gets range chips instead of one long list. */
        const val RANGE_THRESHOLD = 60
        const val RANGE_SIZE = 50
        private const val NOMINAL_DURATION_MS = 1L

        /**
         * Split a long season into blocks.
         *
         * A thousand-episode anime cannot be one list, and neither can it be a
         * lazy list the viewer scrolls for a minute to reach episode 900.
         * Returns empty for anything short enough not to need it, so the chips
         * do not appear on a six-episode season.
         */
        fun rangesFor(item: TvItem?, season: Int?): List<EpisodeRange> {
            if (item == null) return emptyList()
            val episodes = if (season != null) item.episodesInSeason(season) else item.videos
            if (episodes.size <= RANGE_THRESHOLD) return emptyList()
            val numbers = episodes.mapNotNull { it.episodeNumber }
            val min = numbers.minOrNull() ?: return emptyList()
            val max = numbers.maxOrNull() ?: return emptyList()
            return buildList {
                var start = min
                while (start <= max) {
                    add(EpisodeRange(start, (start + RANGE_SIZE - 1).coerceAtMost(max)))
                    start += RANGE_SIZE
                }
            }
        }
    }
}

/**
 * Whether a type is expected to carry an episode list.
 *
 * Only used to decide whether a `meta` call is worth making for a seed that has
 * no `videos`. It is a hint, not a classification: the authority on whether
 * something has episodes is still whether `videos` came back non-empty.
 */
private fun String.needsEpisodes(): Boolean =
    lowercase() !in setOf("movie", "tv", "channel")
