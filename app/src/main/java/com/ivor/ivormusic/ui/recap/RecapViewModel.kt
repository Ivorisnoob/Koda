package com.ivor.ivormusic.ui.recap

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.recap.Recap
import com.ivor.ivormusic.data.recap.RecapEngine
import com.ivor.ivormusic.data.recap.RecapKind
import com.ivor.ivormusic.data.recap.RecapPeriod
import com.ivor.ivormusic.data.recap.RecapPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Recap: the shelf of periods, and the story of one.
 *
 * Scoped to the activity, so the shelf (a route) and the story (an overlay
 * above everything) share it. Everything is computed here, on the device,
 * from the active profile's listening history: [refresh] reads it whenever the
 * shelf is shown, so a profile switch or a fresh play is never stale.
 */
class RecapViewModel(application: Application) : AndroidViewModel(application) {

    private val stats = StatsRepository(application)
    private val colourStore = RecapCoverColours(application)

    data class Shelf(
        val loading: Boolean = true,
        val kind: RecapKind = RecapKind.YEAR,
        /** Newest first. */
        val previews: List<RecapPreview> = emptyList(),
        val selectedId: String? = null,
        /** Whether new plays are being kept at all, so an empty shelf can say why. */
        val historyOn: Boolean = true,
        val totalPlays: Int = 0,
    ) {
        val selected: RecapPreview? get() = previews.firstOrNull { it.period.id == selectedId } ?: previews.firstOrNull()
    }

    private val _shelf = MutableStateFlow(Shelf())
    val shelf: StateFlow<Shelf> = _shelf.asStateFlow()

    /** The recap being told, or null when the story is closed. */
    private val _story = MutableStateFlow<Recap?>(null)
    val story: StateFlow<Recap?> = _story.asStateFlow()

    /** The shelf's chosen period, built, for its card's pressing. */
    private val _hero = MutableStateFlow<Recap?>(null)
    val hero: StateFlow<Recap?> = _hero.asStateFlow()
    private var heroJob: Job? = null

    /** Cover colours by song id, as extracted; filled in progressively. */
    private val _colours = MutableStateFlow<Map<String, Color>>(emptyMap())
    val colours: StateFlow<Map<String, Color>> = _colours.asStateFlow()

    private var history: List<PlayHistoryEntry> = emptyList()
    private var kindChosen = false
    private var colourJob: Job? = null
    private var shelfColourJob: Job? = null

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val weekStart: DayOfWeek get() = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    fun refresh() {
        viewModelScope.launch {
            val loaded = stats.loadHistory()
            val historyOn = ThemePreferences(getApplication()).isSaveMusicHistoryEnabled()
            history = loaded
            if (_colours.value.isEmpty()) _colours.value = colourStore.known()
            // The longest kind the history can fill, until someone picks one.
            val kind = if (kindChosen) _shelf.value.kind else defaultKind(loaded)
            val previews = withContext(Dispatchers.Default) {
                RecapEngine.previews(kind, loaded, zone, LocalDate.now(zone), weekStart)
            }
            _shelf.update {
                it.copy(
                    loading = false,
                    kind = kind,
                    previews = previews,
                    selectedId = it.selectedId?.takeIf { id -> previews.any { p -> p.period.id == id } },
                    historyOn = historyOn,
                    totalPlays = loaded.size,
                )
            }
            fillShelfColours(previews)
            // The history was just read again: the chosen period may have new plays.
            buildHero(force = true)
        }
    }

    private fun buildHero(force: Boolean = false) {
        heroJob?.cancel()
        val period = _shelf.value.selected?.period ?: run { _hero.value = null; return }
        if (!force && _hero.value?.period == period) return
        heroJob = viewModelScope.launch {
            _hero.value = withContext(Dispatchers.Default) {
                RecapEngine.build(period, history, zone, LocalDate.now(zone))
            }
            _hero.value?.let { recap ->
                val thumbs = thumbnails()
                val wanted = recap.songs.values.sortedByDescending { it.heardMs }.map { it.id }
                    .filter { it !in _colours.value }
                    .map { it to (recap.songs[it]?.thumbnailUrl ?: thumbs[it]) }
                colourStore.fill(wanted) { id, colour -> _colours.update { it + (id to colour) } }
            }
        }
    }

    fun selectKind(kind: RecapKind) {
        kindChosen = true
        if (kind == _shelf.value.kind) return
        viewModelScope.launch {
            val previews = withContext(Dispatchers.Default) {
                RecapEngine.previews(kind, history, zone, LocalDate.now(zone), weekStart)
            }
            _shelf.update { it.copy(kind = kind, previews = previews, selectedId = null) }
            fillShelfColours(previews)
            buildHero()
        }
    }

    fun selectPeriod(id: String) {
        _shelf.update { it.copy(selectedId = id) }
        buildHero()
    }

    /** Open the story of [period]. */
    fun open(period: RecapPeriod) {
        viewModelScope.launch {
            val recap = withContext(Dispatchers.Default) {
                RecapEngine.build(period, history, zone, LocalDate.now(zone))
            } ?: return@launch
            _story.value = recap
            fillStoryColours(recap)
        }
    }

    fun close() {
        _story.value = null
    }

    private fun thumbnails(): Map<String, String?> {
        val map = HashMap<String, String?>()
        // Newest first: the newest play's thumbnail wins.
        for (entry in history) if (entry.songId !in map) map[entry.songId] = entry.thumbnailUrl
        return map
    }

    private fun fillShelfColours(previews: List<RecapPreview>) {
        shelfColourJob?.cancel()
        val thumbs = thumbnails()
        val wanted = previews.take(16).flatMap { p -> p.stripe.map { it.first } }.distinct()
            .filter { it !in _colours.value }
            .map { it to thumbs[it] }
        if (wanted.isEmpty()) return
        shelfColourJob = viewModelScope.launch {
            colourStore.fill(wanted) { id, colour -> _colours.update { it + (id to colour) } }
        }
    }

    /**
     * The story's songs, the ones a viewer sees first first: the top songs and
     * artists, the stories' covers, then every other song on the pressing by
     * how much it was heard.
     */
    private fun fillStoryColours(recap: Recap) {
        colourJob?.cancel()
        val thumbs = thumbnails()
        val first = buildList {
            recap.topSongs.forEach { add(it.id) }
            recap.topArtists.forEach { addAll(it.songIds.take(2)) }
            recap.race?.let { add(it.leader.id); add(it.passed.id) }
            recap.cameBack?.let { add(it.song.id) }
            recap.longSitting?.songIds?.let(::addAll)
            recap.newArtist?.let { add(it.songId) }
            recap.oddHour?.let { add(it.songId) }
            recap.hours.bandSongId?.let(::add)
            recap.days.mapNotNullTo(this) { it.songId }
        }
        val rest = recap.songs.values.sortedByDescending { it.heardMs }.map { it.id }
        val wanted = (first + rest).distinct()
            .filter { it !in _colours.value }
            .map { id -> id to (recap.songs[id]?.thumbnailUrl ?: thumbs[id]) }
        if (wanted.isEmpty()) return
        colourJob = viewModelScope.launch {
            colourStore.fill(wanted) { id, colour -> _colours.update { it + (id to colour) } }
        }
    }

    private fun defaultKind(loaded: List<PlayHistoryEntry>): RecapKind {
        if (loaded.isEmpty()) return RecapKind.MONTH
        val spanDays = (loaded.maxOf { it.timestamp } - loaded.minOf { it.timestamp }) / 86_400_000L
        return when {
            spanDays >= 60 -> RecapKind.YEAR
            spanDays >= 14 -> RecapKind.MONTH
            else -> RecapKind.WEEK
        }
    }
}
