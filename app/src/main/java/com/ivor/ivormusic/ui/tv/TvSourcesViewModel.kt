package com.ivor.ivormusic.ui.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.tv.DubPreference
import com.ivor.ivormusic.data.tv.SourceQuality
import com.ivor.ivormusic.data.tv.TvAutoPick
import com.ivor.ivormusic.data.tv.TvAutoSelectProfile
import com.ivor.ivormusic.data.tv.TvSource
import com.ivor.ivormusic.data.tv.TvSourceFacets
import com.ivor.ivormusic.data.tv.TvSourceFilter
import com.ivor.ivormusic.data.tv.TvStreamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The source list for one item or episode.
 *
 * Held by the detail route rather than the sheet, so that dismissing the sheet
 * and reopening it on the same episode does not re-run the fan-out - opening
 * this sheet is the most expensive thing TV mode does, one request per stream
 * addon, and someone comparing two releases opens it more than once.
 */
class TvSourcesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TvStreamRepository(application)

    private val _sources = MutableStateFlow<List<TvSource>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _filter = MutableStateFlow(TvSourceFilter())
    val filter: StateFlow<TvSourceFilter> = _filter.asStateFlow()

    private val _facets = MutableStateFlow(TvSourceFacets())
    val facets: StateFlow<TvSourceFacets> = _facets.asStateFlow()

    private val _autoPick = MutableStateFlow<TvAutoPick?>(null)
    val autoPick: StateFlow<TvAutoPick?> = _autoPick.asStateFlow()

    /** Ranked and filtered, which is what the list draws. */
    private val _visible = MutableStateFlow<List<TvSource>>(emptyList())
    val visible: StateFlow<List<TvSource>> = _visible.asStateFlow()

    /**
     * How many the addons returned, before filtering.
     *
     * Separate from [visible] because the sheet has to tell "no addon has this
     * title" apart from "your chips exclude everything", and those two need
     * different words and different actions.
     */
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    /**
     * Addons that were asked and did not answer usefully.
     *
     * Surfaced rather than logged, because the most common cause is an addon
     * that quietly requires an account - it installs from a perfectly valid
     * manifest, then answers 400 to every title. Without naming it, that reads
     * as "Koda finds nothing", which is the app taking the blame for someone
     * else's login wall.
     */
    private val _failedAddons = MutableStateFlow<List<String>>(emptyList())
    val failedAddons: StateFlow<List<String>> = _failedAddons.asStateFlow()

    /** True once a fan-out has completed for the current key. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var loadedKey: String? = null
    private var job: Job? = null
    private var profile = TvAutoSelectProfile()

    /** Whether anything installed could produce a file at all. A fresh read. */
    fun hasStreamSource(): Boolean = !repository.addons.hasNoStreamSource()

    fun refreshAddons() = repository.addons.reload()

    /**
     * Load sources for [id], which is the addon-facing id - `tt0903747` for a
     * film, `tt0903747:1:1` for an episode.
     *
     * The profile is read here rather than held, because the network can change
     * between opening a title and opening its sources and the Wi-Fi / mobile
     * split is only honest if it is read at the moment of the decision.
     */
    fun load(
        type: String,
        id: String,
        force: Boolean = false,
        /**
         * Ask every addon rather than stopping at the first tier that answered.
         *
         * The sheet passes true because the viewer is looking at the field and
         * expects to see it; Play passes false because it wants the fastest
         * startable file. See [TvStreamRepository.sources].
         */
        exhaustive: Boolean = false,
    ) {
        // Exhaustiveness is part of the key: a short list fetched for Play must
        // not satisfy a later request to see everything, or opening the sheet
        // after an automatic start would show only the tier that won the race.
        val key = type + "/" + id + if (exhaustive) "/all" else ""
        if (loadedKey == key && !force && _loaded.value) return
        loadedKey = key
        job?.cancel()

        _sources.value = emptyList()
        _visible.value = emptyList()
        _totalCount.value = 0
        _failedAddons.value = emptyList()
        _facets.value = TvSourceFacets()
        _autoPick.value = null
        _filter.value = TvSourceFilter()
        _loaded.value = false
        _isLoading.value = true

        job = viewModelScope.launch {
            profile = TvAutoSelectProfile.forCurrentNetwork(getApplication())
            val result = repository.sources(type, id, exhaustive = exhaustive)
            val found = result.sources
            _sources.value = found
            _totalCount.value = found.size
            _failedAddons.value = result.failedAddons
            // Facets come from the whole set and never change as chips are
            // used: a filter row whose options vanish as you touch them is
            // unusable. The pick below is the opposite - see recompute().
            _facets.value = TvStreamRepository.facets(found)
            recompute()
            _isLoading.value = false
            _loaded.value = true
        }
    }

    fun setResolution(value: Int?) = update { it.copy(resolution = value) }

    fun setLanguage(value: String?) = update { it.copy(language = value) }

    fun setSourceQuality(value: SourceQuality?) = update { it.copy(sourceQuality = value) }

    fun setCachedOnly(value: Boolean) = update { it.copy(cachedOnly = value) }

    fun setDub(value: DubPreference) = update { it.copy(dub = value) }

    fun clearFilters() = update { TvSourceFilter() }

    private fun update(transform: (TvSourceFilter) -> TvSourceFilter) {
        _filter.value = transform(_filter.value)
        recompute()
    }

    /**
     * Re-rank, and re-pick.
     *
     * **The hero card is recomputed over the filtered set, not the whole one.**
     * Filtering to Cached and then being offered an uncached release as the
     * automatic choice is the sheet contradicting itself, and the hero is the
     * control most people use - it has to describe what they are looking at.
     */
    private fun recompute() {
        val filtered = TvStreamRepository.filter(_sources.value, _filter.value)
        _visible.value = TvStreamRepository.ranked(filtered, profile)
        _autoPick.value = TvStreamRepository.autoPick(filtered, profile)
    }
}
