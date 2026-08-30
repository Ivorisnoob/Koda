package com.ivor.ivormusic.ui.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.tv.TvItem
import com.ivor.ivormusic.data.tv.TvLibraryEntry
import com.ivor.ivormusic.data.tv.TvProgress
import com.ivor.ivormusic.data.tv.TvProgressRepository
import com.ivor.ivormusic.data.tv.TvRepository
import com.ivor.ivormusic.data.tv.TvSearchGroup
import com.ivor.ivormusic.data.tv.TvShelf
import com.ivor.ivormusic.data.tv.TvWatchlistRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for TV mode's Home, Search and Library tabs.
 *
 * One ViewModel across the three tabs rather than one each, because they share
 * the watchlist and progress stores and because Home's shelves are what Search
 * falls back to when a query is cleared. The detail screen gets its own,
 * scoped to its nav entry, the way the channel page does.
 */
class TvViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TvRepository(application)
    private val watchlistRepository = TvWatchlistRepository(application)
    private val progressRepository = TvProgressRepository(application)

    private val _shelves = MutableStateFlow<List<TvShelf>>(emptyList())
    val shelves: StateFlow<List<TvShelf>> = _shelves.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    /** True once a load has completed, so empty states do not flash before it. */
    private val _hasLoadedHome = MutableStateFlow(false)
    val hasLoadedHome: StateFlow<Boolean> = _hasLoadedHome.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TvSearchGroup>>(emptyList())
    val searchResults: StateFlow<List<TvSearchGroup>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val watchlist: StateFlow<List<TvLibraryEntry>> = watchlistRepository.watchlist
    val progress: StateFlow<Map<String, TvProgress>> = progressRepository.progress
    val watchedItems: StateFlow<List<TvLibraryEntry>> = progressRepository.watchedItems

    /**
     * Whether anything installed can produce a playable file.
     *
     * Drives the one card under the hero that explains why nothing plays. Read
     * fresh rather than observed, because the addon list is not process-wide and
     * this is re-read whenever Home loads.
     */
    private val _hasStreamSource = MutableStateFlow(false)
    val hasStreamSource: StateFlow<Boolean> = _hasStreamSource.asStateFlow()

    private var searchJob: Job? = null
    private var homeJob: Job? = null

    /** Continue Watching, newest first. Derived rather than stored. */
    fun continueWatching(): List<Pair<TvLibraryEntry, TvProgress>> =
        progressRepository.continueWatching()

    fun isSaved(id: String?): Boolean = watchlistRepository.isSaved(id)

    fun toggleWatchlist(item: TvItem): Boolean = watchlistRepository.toggle(item)

    fun removeFromWatchlist(id: String) = watchlistRepository.remove(id)

    fun clearProgressFor(itemId: String) = progressRepository.clearForItem(itemId)

    /**
     * Load or reload every shelf.
     *
     * Shelf descriptors are rebuilt from the installed manifests on every call,
     * so installing an addon and coming back shows its catalogs without a
     * restart. The skeleton shelves are published before the fetch so Home has
     * its shape immediately rather than appearing row by row.
     */
    fun loadHome(forceFresh: Boolean = false) {
        if (homeJob?.isActive == true && !forceFresh) return
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _isLoadingHome.value = true
            _hasStreamSource.value = !repository.addons.hasNoStreamSource()

            val descriptors = repository.shelves()
            if (descriptors.isEmpty()) {
                _shelves.value = emptyList()
                _isLoadingHome.value = false
                _hasLoadedHome.value = true
                return@launch
            }

            // Keep whatever is already on screen for shelves that still exist,
            // so a pull-to-refresh does not blank the page it is refreshing.
            val existing = _shelves.value.associateBy { it.key }
            _shelves.value = descriptors.map { shelf ->
                existing[shelf.key]?.copy(
                    title = shelf.title,
                    genres = shelf.genres,
                    isLoading = true,
                    failed = false,
                ) ?: shelf.copy(isLoading = true)
            }

            val loaded = repository.loadAllShelves(descriptors, forceFresh)
            // A shelf that failed keeps whatever it had rather than emptying:
            // a transient failure should not blank a row the user was reading.
            val previous = _shelves.value.associateBy { it.key }
            _shelves.value = loaded.map { shelf ->
                if (shelf.failed) {
                    val old = previous[shelf.key]
                    if (old != null && old.items.isNotEmpty()) {
                        old.copy(isLoading = false, failed = false)
                    } else shelf
                } else shelf
            }
            _isLoadingHome.value = false
            _hasLoadedHome.value = true
        }
    }

    /**
     * Re-read the addon list and reload if it changed.
     *
     * Called when Home resumes, because [com.ivor.ivormusic.data.tv.AddonRepository]
     * is deliberately not process-wide: installing an addon on another screen
     * writes to a different instance, so this surface only learns about it by
     * asking. Without this the "no source installed" card would keep telling a
     * user who just installed one that they have not.
     */
    fun refreshAddons() {
        val repo = repository.addons
        repo.reload()
        _hasStreamSource.value = !repo.hasNoStreamSource()
        val current = _shelves.value.map { it.key }.toSet()
        val next = repository.shelves().map { it.key }.toSet()
        if (current != next) loadHome(forceFresh = false)
    }

    /**
     * Apply a genre to every shelf that offers it.
     *
     * This is what "browse by genre" means: the genre chips on Search come from
     * the installed manifests, and tapping one has to filter the catalogs rather
     * than run a text search for the word - searching "Action" returns titles
     * with Action in the name, which is not what anyone tapping a genre wants.
     */
    fun browseGenre(genre: String) {
        val targets = _shelves.value.filter { it.genres.contains(genre) }
        if (targets.isEmpty()) return
        targets.forEach { selectGenre(it.key, genre) }
    }

    /** Change one shelf's genre filter and refetch only that shelf. */
    fun selectGenre(shelfKey: String, genre: String?) {
        val target = _shelves.value.firstOrNull { it.key == shelfKey } ?: return
        viewModelScope.launch {
            _shelves.value = _shelves.value.map {
                if (it.key == shelfKey) it.copy(selectedGenre = genre, isLoading = true) else it
            }
            val items = repository.loadShelf(target, genre = genre)
            _shelves.value = _shelves.value.map {
                if (it.key != shelfKey) it
                else if (items == null) it.copy(isLoading = false, failed = true)
                else it.copy(items = items, isLoading = false, failed = false)
            }
        }
    }

    /** Append the next page of one shelf. */
    fun loadMore(shelfKey: String) {
        val target = _shelves.value.firstOrNull { it.key == shelfKey } ?: return
        if (target.isLoading || !target.supportsSkip || target.items.isEmpty()) return
        viewModelScope.launch {
            _shelves.value = _shelves.value.map {
                if (it.key == shelfKey) it.copy(isLoading = true) else it
            }
            val more = repository.loadShelf(
                target, skip = target.items.size, genre = target.selectedGenre
            )
            _shelves.value = _shelves.value.map {
                if (it.key != shelfKey) it
                else {
                    // Addons repeat items across page boundaries often enough
                    // that appending blind produces duplicate lazy-list keys,
                    // which crashes rather than merely looking wrong.
                    val known = it.items.mapTo(HashSet()) { existing -> existing.id }
                    val fresh = more.orEmpty().filter { candidate -> known.add(candidate.id) }
                    it.copy(items = it.items + fresh, isLoading = false)
                }
            }
        }
    }

    /**
     * Search every searchable catalog.
     *
     * Debounced, and the previous query is cancelled rather than raced: results
     * arriving out of order would otherwise show the answer to a query the user
     * has already typed past.
     */
    fun search(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _isSearching.value = true
            val results = repository.search(query)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
