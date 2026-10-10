package com.ivor.ivormusic.ui.taste

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.ArtistTasteSample
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.MusicShelfItem
import com.ivor.ivormusic.data.NotInterestedRepository
import com.ivor.ivormusic.data.RecommendationEngine
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.TasteArtist
import com.ivor.ivormusic.data.TasteGenre
import com.ivor.ivormusic.data.TasteProfileStore
import com.ivor.ivormusic.data.TasteSong
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The four screens of taste setup, in order. */
enum class TasteStep { ARTISTS, GENRES, DECK, DONE }

/** Why a song is in the deck, shown on its card. */
sealed interface DeckReason {
    data class Artist(val name: String) : DeckReason
    data class Genre(val title: String) : DeckReason
    data object Discovery : DeckReason
}

data class DeckCard(val song: Song, val reason: DeckReason)

enum class DeckVerdict { YES, NO, LOVE }

/**
 * Taste setup: pick artists (each pick brings more like it), pick genres, then
 * say yes or no to a deck of songs built from both.
 *
 * **The grid starts from the user, not from a chart.** Someone with a
 * listening history opens it with their own artists picked, followed by the
 * artists YouTube Music says their listeners also like. Charts only fill in
 * behind that, and are the whole grid only for an install with no history:
 * the local chart taken in turn with the US one, because a single country's
 * chart is one culture and, in testing, an embarrassing guess at a stranger.
 *
 * **Genres are a fixed list of genres.** [scar October 2026] YouTube Music's
 * own "Genres" grid is built per country and is mostly languages and regions
 * (Bhojpuri, Bengali, Arabic, African), which is not what the word means to
 * the person being asked. The names here are also what recommendations
 * search for, so they stay in English.
 *
 * Artist data comes from endpoints the app already reads: the charts page, an
 * artist's own page for "more like this" and their top songs, and a song
 * radio for the deck cards that are neither picked nor from a genre.
 * [verified October 2026, signed out, WEB_REMIX]
 *
 * **Every pick is saved as it is made**, to [TasteProfileStore], so leaving
 * half-way keeps what was chosen and there is no "apply" step to forget.
 */
class TasteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = YouTubeRepository(application)
    private val store = TasteProfileStore(application)
    private val engine = RecommendationEngine(application, repository)
    private val likedSongs = LikedSongsRepository(application)
    private val notInterested = NotInterestedRepository(application)
    val preview = TastePreviewPlayer(application, repository, viewModelScope)

    private val _step = MutableStateFlow(TasteStep.ARTISTS)
    val step: StateFlow<TasteStep> = _step.asStateFlow()

    /** The grid: picks first, then suggestions, with "more like this" inserted after its source. */
    private val _artists = MutableStateFlow<List<TasteArtist>>(emptyList())
    val artists: StateFlow<List<TasteArtist>> = _artists.asStateFlow()

    private val _picked = MutableStateFlow<List<TasteArtist>>(emptyList())
    val picked: StateFlow<List<TasteArtist>> = _picked.asStateFlow()

    private val _isLoadingArtists = MutableStateFlow(true)
    val isLoadingArtists: StateFlow<Boolean> = _isLoadingArtists.asStateFlow()

    /** Nothing could be loaded at all, which for this screen means no connection. */
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TasteArtist>>(emptyList())
    val searchResults: StateFlow<List<TasteArtist>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _genres = MutableStateFlow(GENRES.map(::TasteGenre))
    val genres: StateFlow<List<TasteGenre>> = _genres.asStateFlow()

    private val _pickedGenres = MutableStateFlow<List<TasteGenre>>(emptyList())
    val pickedGenres: StateFlow<List<TasteGenre>> = _pickedGenres.asStateFlow()

    private val _deck = MutableStateFlow<List<DeckCard>>(emptyList())
    val deck: StateFlow<List<DeckCard>> = _deck.asStateFlow()

    private val _deckIndex = MutableStateFlow(0)
    val deckIndex: StateFlow<Int> = _deckIndex.asStateFlow()

    private val _isDeckLoading = MutableStateFlow(false)
    val isDeckLoading: StateFlow<Boolean> = _isDeckLoading.asStateFlow()

    private val _keptCount = MutableStateFlow(0)
    val keptCount: StateFlow<Int> = _keptCount.asStateFlow()

    private val samples = HashMap<String, ArtistTasteSample>()
    private var started = false
    private var startedFresh = false
    private var searchJob: Job? = null
    private var deckJob: Job? = null

    /**
     * Load the screen. [ignoreExisting] starts from nothing even when there is
     * a saved profile or a listening history to fill it from; it exists so the
     * new-user flow can be seen on an install that has both.
     */
    fun start(ignoreExisting: Boolean) {
        if (started) return
        started = true
        startedFresh = ignoreExisting
        viewModelScope.launch {
            val saved = if (ignoreExisting) null else store.current().takeUnless { it.isEmpty }
            if (saved != null) {
                _picked.value = saved.artists
                // A genre saved from the per-country list this used to show
                // is not offered any more, so it is not kept either.
                val keptGenres = saved.genres.filter { it.title in GENRES }
                if (keptGenres != saved.genres) store.setGenres(keptGenres)
                _pickedGenres.value = keptGenres
                _artists.value = saved.artists
            }
            val charts = async { loadChartArtists() }
            // Someone who has been using the app arrives with their history's
            // artists already picked, to keep or remove; nothing saved and
            // nothing played is the blank start a new install gets.
            if (saved == null && !ignoreExisting) {
                val fromHistory = artistsFromHistory()
                if (fromHistory.isNotEmpty()) {
                    _picked.value = fromHistory
                    _artists.value = fromHistory
                    store.setArtists(fromHistory)
                }
            }
            // The picks are on screen already; what follows them is who their
            // listeners also like, and only then the charts.
            if (_picked.value.isNotEmpty()) _isLoadingArtists.value = false
            val similar = similarToPicked()
            _artists.value = (_artists.value + similar).distinctBy { it.key }
            _artists.value = (_artists.value + charts.await()).distinctBy { it.key }
            _isLoadingArtists.value = false
            _loadFailed.value = _artists.value.isEmpty()
        }
    }

    fun retry() {
        started = false
        _isLoadingArtists.value = true
        _loadFailed.value = false
        start(ignoreExisting = startedFresh)
    }

    /** The local chart and the US chart, an artist from each in turn. */
    private suspend fun loadChartArtists(): List<TasteArtist> = try {
        val local = viewModelScope.async { repository.getChartArtists() }
        val broad = viewModelScope.async { repository.getChartArtists(CHART_BROAD) }
        val first = local.await()
        val second = broad.await()
        buildList {
            for (index in 0 until maxOf(first.size, second.size)) {
                second.getOrNull(index)?.let(::add)
                first.getOrNull(index)?.let(::add)
            }
        }.map { TasteArtist(it.id, it.name, it.thumbnailUrl) }.distinctBy { it.key }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        KLog.w(TAG, "Chart artists failed", e)
        emptyList()
    }

    /** "Fans also like" for the first few picks, an artist from each in turn. */
    private suspend fun similarToPicked(): List<TasteArtist> {
        val seeds = _picked.value.filter { it.id.startsWith("UC") }.take(SIMILAR_SEEDS)
        if (seeds.isEmpty()) return emptyList()
        val lists = seeds.map { seed ->
            viewModelScope.async { sampleFor(seed.id)?.similar.orEmpty() }
        }.awaitAll()
        return buildList {
            for (index in 0 until (lists.maxOfOrNull { it.size } ?: 0)) {
                lists.forEach { list -> list.getOrNull(index)?.let(::add) }
            }
        }.map { TasteArtist(it.id, it.name, it.thumbnailUrl) }
            .filterNot { candidate -> _picked.value.any { it.sameArtistAs(candidate) } }
            .distinctBy { it.key }
    }

    /** The history's top artists, each resolved to an id and a picture where a search finds the same name. */
    private suspend fun artistsFromHistory(): List<TasteArtist> {
        val names = engine.buildTasteProfile().topArtists.filterNot { isUnknownArtist(it) }
        if (names.isEmpty()) return emptyList()
        return names.chunked(RESOLVE_CONCURRENCY).flatMap { batch ->
            batch.map { name ->
                viewModelScope.async {
                    val match = runCatching { repository.searchArtists(name) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        .getOrDefault(emptyList())
                        .firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                    // No exact match keeps the name alone: a history tag is often
                    // a credit the first search hit for is somebody else.
                    if (match != null) TasteArtist(match.id, match.name, match.thumbnailUrl)
                    else TasteArtist("", name)
                }
            }.awaitAll()
        }
    }

    fun isPicked(artist: TasteArtist): Boolean = _picked.value.any { it.sameArtistAs(artist) }

    fun toggleArtist(artist: TasteArtist) {
        if (isPicked(artist)) {
            _picked.value = _picked.value.filterNot { it.sameArtistAs(artist) }
            store.unfollow(artist)
        } else {
            _picked.value = _picked.value + artist
            store.follow(artist)
            if (_artists.value.none { it.sameArtistAs(artist) }) {
                _artists.value = listOf(artist) + _artists.value
            }
            showMoreLike(artist)
        }
    }

    /** Insert a few of [artist]'s "fans also like" right after them in the grid. */
    private fun showMoreLike(artist: TasteArtist) {
        if (!artist.id.startsWith("UC")) return
        viewModelScope.launch {
            val sample = sampleFor(artist.id) ?: return@launch
            val shown = _artists.value
            val fresh = sample.similar
                .map { TasteArtist(it.id, it.name, it.thumbnailUrl) }
                .filter { candidate -> shown.none { it.sameArtistAs(candidate) } }
                .take(SIMILAR_PER_PICK)
            if (fresh.isEmpty()) return@launch
            val at = shown.indexOfFirst { it.sameArtistAs(artist) }
            // Unpicked again before the answer arrived: nothing to sit beside.
            if (at < 0 || !isPicked(artist)) return@launch
            _artists.value = shown.subList(0, at + 1) + fresh + shown.subList(at + 1, shown.size)
        }
    }

    private suspend fun sampleFor(artistId: String): ArtistTasteSample? =
        samples[artistId] ?: repository.getArtistTasteSample(artistId)?.also { samples[artistId] = it }

    fun setQuery(text: String) {
        _query.value = text
        searchJob?.cancel()
        if (text.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = runCatching { repository.searchArtists(text.trim()) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrDefault(emptyList())
                .map { TasteArtist(it.id, it.name, it.thumbnailUrl) }
                .distinctBy { it.key }
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun toggleGenre(genre: TasteGenre) {
        val current = _pickedGenres.value
        _pickedGenres.value = if (current.any { it.title == genre.title }) {
            current.filterNot { it.title == genre.title }
        } else {
            current + genre
        }
        store.setGenres(_pickedGenres.value)
    }

    /** Forget every pick, here and in the store, and start the screen over. */
    fun clearAll() {
        deckJob?.cancel()
        preview.stop()
        store.clear()
        _picked.value = emptyList()
        _pickedGenres.value = emptyList()
        _deck.value = emptyList()
        _deckIndex.value = 0
        _keptCount.value = 0
        _query.value = ""
        _searchResults.value = emptyList()
        _step.value = TasteStep.ARTISTS
    }

    fun goTo(step: TasteStep) {
        if (step != TasteStep.DECK) preview.stop()
        _step.value = step
        if (step == TasteStep.DECK) buildDeck()
    }

    /** One step back, or false when already on the first. */
    fun back(): Boolean {
        val previous = when (_step.value) {
            TasteStep.ARTISTS -> return false
            TasteStep.GENRES -> TasteStep.ARTISTS
            TasteStep.DECK -> TasteStep.GENRES
            TasteStep.DONE -> TasteStep.GENRES
        }
        goTo(previous)
        return true
    }

    private fun buildDeck() {
        deckJob?.cancel()
        _deck.value = emptyList()
        _deckIndex.value = 0
        _isDeckLoading.value = true
        deckJob = viewModelScope.launch {
            try {
                val pickedNames = _picked.value.map { it.name.trim().lowercase() }.toSet()
                val artistSeeds = _picked.value.filter { it.id.startsWith("UC") }.shuffled().take(DECK_ARTISTS)
                val artistCards = artistSeeds.map { artist ->
                    async {
                        sampleFor(artist.id)?.topSongs.orEmpty().shuffled().take(SONGS_PER_ARTIST)
                            .map { DeckCard(it, DeckReason.Artist(artist.name)) }
                    }
                }
                val genreCards = _pickedGenres.value.shuffled().take(DECK_GENRES).map { genre ->
                    async {
                        val tracks = runCatching {
                            repository.search("${genre.title} songs", YouTubeRepository.FILTER_SONGS)
                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                            .getOrDefault(emptyList())
                        tracks.shuffled().take(SONGS_PER_GENRE)
                            .map { DeckCard(it, DeckReason.Genre(genre.title)) }
                    }
                }
                val fromArtists = artistCards.awaitAll().flatten()
                // Songs that fit but were not asked for: the radio of one picked
                // artist's song, minus everyone already picked.
                val discovery = fromArtists.firstOrNull()?.let { seed ->
                    repository.getRelatedSongs(seed.song.id, limit = 20)
                        .filter { it.artist.trim().lowercase() !in pickedNames }
                        .take(DISCOVERY_SONGS)
                        .map { DeckCard(it, DeckReason.Discovery) }
                }.orEmpty()
                val buckets = listOf(fromArtists, genreCards.awaitAll().flatten(), discovery)
                    .filter { it.isNotEmpty() }
                val cards = buildList {
                    for (index in 0 until (buckets.maxOfOrNull { it.size } ?: 0)) {
                        buckets.forEach { bucket -> bucket.getOrNull(index)?.let(::add) }
                    }
                }.distinctBy { it.song.id }
                    .filterNot { notInterested.isVideoHidden(it.song.id) }
                    .take(DECK_SIZE)
                _deck.value = cards
                _isDeckLoading.value = false
                if (cards.isEmpty()) _step.value = TasteStep.DONE else playCard(0)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                KLog.w(TAG, "Deck failed", e)
                _isDeckLoading.value = false
                _step.value = TasteStep.DONE
            }
        }
    }

    /** Answer the top card and move to the next. */
    fun answer(verdict: DeckVerdict) {
        val index = _deckIndex.value
        val card = _deck.value.getOrNull(index) ?: return
        val song = card.song
        when (verdict) {
            DeckVerdict.NO -> {
                store.removeSong(song.id)
                notInterested.hideSong(song)
                // The hide is the answer itself here, given on purpose and one
                // of many; the app-wide Undo bar is for a hide made in passing.
                notInterested.clearLastAction()
            }
            DeckVerdict.YES, DeckVerdict.LOVE -> {
                store.addSong(TasteSong(song.id, song.title, song.artist, song.thumbnailUrl))
                _keptCount.value += 1
                if (verdict == DeckVerdict.LOVE && !likedSongs.isLiked(song.id)) likedSongs.toggleLike(song)
            }
        }
        val next = index + 1
        _deckIndex.value = next
        val upcoming = _deck.value.getOrNull(next)
        if (upcoming == null) {
            preview.stop()
            _step.value = TasteStep.DONE
        } else {
            playCard(next)
        }
    }

    /** Play the card at [index] and hand the player the two after it to get ready. */
    private fun playCard(index: Int) {
        val cards = _deck.value
        val card = cards.getOrNull(index) ?: return
        preview.play(
            card.song,
            upcoming = cards.drop(index + 1).take(PREVIEW_LOOKAHEAD).map { it.song }
        )
    }

    fun togglePreview() = preview.toggle(_deck.value.getOrNull(_deckIndex.value)?.song)

    /** The screen is being left, finished or skipped: it has been offered. */
    fun markSeen() {
        store.setupSeen = true
        preview.stop()
    }

    override fun onCleared() {
        preview.release()
    }

    private companion object {
        const val TAG = "TasteViewModel"
        /** The chart mixed with the local one for an install with no history. */
        const val CHART_BROAD = "US"

        /** Picks whose "fans also like" lead the suggestions; one request each. */
        const val SIMILAR_SEEDS = 4

        /** Genres, and only genres: no languages, countries, moods or decades. */
        val GENRES = listOf(
            "Pop", "Hip-hop", "Rock", "R&B", "Electronic", "Indie", "Alternative", "Metal",
            "Jazz", "Classical", "Country", "Folk", "Soul", "Funk", "Blues", "Punk",
            "House", "Techno", "Drum and bass", "Lo-fi", "Ambient", "Reggae", "K-Pop",
            "Afrobeats", "Reggaeton", "Soundtracks"
        )

        const val SIMILAR_PER_PICK = 4
        const val SEARCH_DEBOUNCE_MS = 350L
        const val RESOLVE_CONCURRENCY = 3

        /** A deck is a minute or two, not a chore. */
        const val DECK_SIZE = 18
        const val DECK_ARTISTS = 5
        const val SONGS_PER_ARTIST = 2
        const val DECK_GENRES = 3
        const val SONGS_PER_GENRE = 3
        const val DISCOVERY_SONGS = 5

        /** Cards ahead of the top one that the preview player prepares for. */
        const val PREVIEW_LOOKAHEAD = 2
    }
}
