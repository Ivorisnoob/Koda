package com.ivor.ivormusic.data.tv

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Enough of an item to draw its card without asking an addon again.
 *
 * A snapshot rather than a reference, which is the opposite of how saved
 * playlists work, and for a reason: a saved playlist is *someone else's list*
 * whose contents legitimately change, while a film's poster and year do not.
 * The snapshot is what makes the watchlist and Continue Watching render with no
 * network and no addons installed.
 */
@Serializable
data class TvLibraryEntry(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val releaseInfo: String? = null,
    val addedAt: Long = 0L,
) {
    companion object {
        fun from(item: TvItem, addedAt: Long = System.currentTimeMillis()) = TvLibraryEntry(
            id = item.id,
            type = item.type,
            name = item.name,
            poster = item.poster,
            background = item.background,
            logo = item.logo,
            releaseInfo = item.releaseInfo,
            addedAt = addedAt,
        )
    }
}

/**
 * The watchlist.
 *
 * **Process-wide**, and it earns that the way the existing seven do: it is
 * written from the detail screen, from a long-press on a Home shelf and from
 * search, and read by TV Library and Home's watchlist row - surfaces that hold
 * their own repository instances. A write on one has to be visible on the
 * others without a refetch.
 *
 * Device-local by design. The signed-out path is a first-class path here in the
 * strongest sense: there is no account in TV mode at all.
 */
class TvWatchlistRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (shared == null) shared = MutableStateFlow(load())
        }
    }

    private val state: MutableStateFlow<List<TvLibraryEntry>> get() = shared!!

    /** Most recently added first. */
    val watchlist: StateFlow<List<TvLibraryEntry>> get() = state.asStateFlow()

    fun isSaved(id: String?): Boolean =
        !id.isNullOrBlank() && state.value.any { it.id == id }

    fun add(item: TvItem) {
        if (item.id.isBlank()) return
        val entry = TvLibraryEntry.from(item)
        // Re-adding refreshes the snapshot without moving it up the list, so
        // the order stays "when I decided to watch this".
        val existing = state.value.firstOrNull { it.id == item.id }
        val next = if (existing != null) {
            state.value.map { if (it.id == item.id) entry.copy(addedAt = existing.addedAt) else it }
        } else {
            listOf(entry) + state.value
        }
        save(next)
    }

    fun remove(id: String) {
        if (state.value.none { it.id == id }) return
        save(state.value.filterNot { it.id == id })
    }

    fun toggle(item: TvItem): Boolean {
        val nowSaved = !isSaved(item.id)
        if (nowSaved) add(item) else remove(item.id)
        return nowSaved
    }

    fun clear() = save(emptyList())

    private fun save(list: List<TvLibraryEntry>) {
        val ordered = list.sortedByDescending { it.addedAt }
        try {
            prefs.edit().putString(KEY, TvJson.instance.encodeToString(ordered)).apply()
        } catch (e: Exception) {
            KLog.w(TAG, "Could not persist watchlist: ${e.message}")
        }
        state.value = ordered
    }

    private fun load(): List<TvLibraryEntry> = try {
        prefs.getString(KEY, null)?.let {
            TvJson.instance.decodeFromString<List<TvLibraryEntry>>(it)
        }.orEmpty().sortedByDescending { it.addedAt }
    } catch (e: Exception) {
        KLog.w(TAG, "Watchlist unreadable: ${e.message}")
        emptyList()
    }

    companion object {
        private const val TAG = "TvWatchlistRepo"
        const val PREFS_NAME = "tv_watchlist"
        private const val KEY = "entries"
        private val LOCK = Any()
        @Volatile private var shared: MutableStateFlow<List<TvLibraryEntry>>? = null
    }
}

/**
 * Where the viewer got to, per item and per episode.
 *
 * [episodeId] is the addon-facing id (`tt0903747:1:1`), which is also what a
 * stream is requested for, so nothing has to reconstruct it. A movie stores one
 * row whose episodeId equals its item id.
 */
@Serializable
data class TvProgress(
    val itemId: String,
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val season: Int? = null,
    val episode: Int? = null,
) {
    /**
     * Anything past this is treated as watched and stops offering to resume.
     *
     * 92% rather than 100% because credits are not the film, and a viewer who
     * stopped during them has finished it.
     */
    val isWatched: Boolean
        get() = durationMs > 0 && positionMs >= durationMs * WATCHED_FRACTION

    /**
     * Whether this is worth showing in Continue Watching. The lower bound
     * exists so that opening something by accident does not put it there.
     */
    val isResumable: Boolean
        get() = !isWatched && durationMs > 0 && positionMs >= MIN_RESUME_MS

    val fraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    companion object {
        const val WATCHED_FRACTION = 0.92f
        const val MIN_RESUME_MS = 60_000L
    }
}

/**
 * Resume positions and watched flags.
 *
 * **Process-wide** for the same reason as the watchlist: it is written by the
 * player, which is an overlay, and read by Home's Continue Watching row and the
 * episode list on the detail screen, which are different ViewModels entirely.
 */
class TvProgressRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (sharedProgress == null) sharedProgress = MutableStateFlow(loadProgress())
            if (sharedItems == null) sharedItems = MutableStateFlow(loadItems())
        }
    }

    private val progressState: MutableStateFlow<Map<String, TvProgress>> get() = sharedProgress!!
    private val itemState: MutableStateFlow<List<TvLibraryEntry>> get() = sharedItems!!

    /** Keyed by episode id. */
    val progress: StateFlow<Map<String, TvProgress>> get() = progressState.asStateFlow()

    /**
     * Card data for everything with progress, so Continue Watching can render
     * without a network call or an installed catalog addon.
     */
    val watchedItems: StateFlow<List<TvLibraryEntry>> get() = itemState.asStateFlow()

    fun forEpisode(episodeId: String?): TvProgress? =
        episodeId?.let { progressState.value[it] }

    /** The furthest-along resumable entry for an item, for its Play button. */
    fun resumePointFor(itemId: String): TvProgress? =
        progressState.value.values
            .filter { it.itemId == itemId && it.isResumable }
            .maxByOrNull { it.updatedAt }

    fun isWatched(episodeId: String?): Boolean = forEpisode(episodeId)?.isWatched == true

    /** Continue Watching: one row per item, most recent first. */
    fun continueWatching(): List<Pair<TvLibraryEntry, TvProgress>> {
        val latestPerItem = progressState.value.values
            .filter { it.isResumable }
            .groupBy { it.itemId }
            .mapValues { (_, list) -> list.maxByOrNull { it.updatedAt }!! }
        val items = itemState.value.associateBy { it.id }
        return latestPerItem.values
            .sortedByDescending { it.updatedAt }
            .mapNotNull { p -> items[p.itemId]?.let { it to p } }
    }

    fun record(item: TvItem, episodeId: String, positionMs: Long, durationMs: Long,
               season: Int? = null, episode: Int? = null) {
        if (item.id.isBlank() || episodeId.isBlank() || durationMs <= 0) return
        val entry = TvProgress(
            itemId = item.id,
            episodeId = episodeId,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
            season = season,
            episode = episode,
        )
        saveProgress(progressState.value + (episodeId to entry))
        saveItems(listOf(TvLibraryEntry.from(item)) + itemState.value.filterNot { it.id == item.id })
    }

    fun markWatched(item: TvItem, episodeId: String, durationMs: Long) =
        record(item, episodeId, durationMs, durationMs)

    fun clearForItem(itemId: String) {
        saveProgress(progressState.value.filterValues { it.itemId != itemId })
        saveItems(itemState.value.filterNot { it.id == itemId })
    }

    fun clearAll() {
        saveProgress(emptyMap())
        saveItems(emptyList())
    }

    private fun saveProgress(map: Map<String, TvProgress>) {
        // Bounded: an unbounded history grows forever in a preference file that
        // is read whole on every launch.
        val trimmed = if (map.size <= MAX_ROWS) map
        else map.entries.sortedByDescending { it.value.updatedAt }
            .take(MAX_ROWS).associate { it.key to it.value }
        try {
            prefs.edit().putString(KEY_PROGRESS, TvJson.instance.encodeToString(trimmed)).apply()
        } catch (e: Exception) {
            KLog.w(TAG, "Could not persist progress: ${e.message}")
        }
        progressState.value = trimmed
    }

    private fun saveItems(list: List<TvLibraryEntry>) {
        val trimmed = list.distinctBy { it.id }.take(MAX_ROWS)
        try {
            prefs.edit().putString(KEY_ITEMS, TvJson.instance.encodeToString(trimmed)).apply()
        } catch (e: Exception) {
            KLog.w(TAG, "Could not persist watched items: ${e.message}")
        }
        itemState.value = trimmed
    }

    private fun loadProgress(): Map<String, TvProgress> = try {
        prefs.getString(KEY_PROGRESS, null)?.let {
            TvJson.instance.decodeFromString<Map<String, TvProgress>>(it)
        }.orEmpty()
    } catch (e: Exception) {
        KLog.w(TAG, "Progress unreadable: ${e.message}")
        emptyMap()
    }

    private fun loadItems(): List<TvLibraryEntry> = try {
        prefs.getString(KEY_ITEMS, null)?.let {
            TvJson.instance.decodeFromString<List<TvLibraryEntry>>(it)
        }.orEmpty()
    } catch (e: Exception) {
        KLog.w(TAG, "Watched items unreadable: ${e.message}")
        emptyList()
    }

    companion object {
        private const val TAG = "TvProgressRepo"
        const val PREFS_NAME = "tv_progress"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_ITEMS = "items"
        private const val MAX_ROWS = 500
        private val LOCK = Any()
        @Volatile private var sharedProgress: MutableStateFlow<Map<String, TvProgress>>? = null
        @Volatile private var sharedItems: MutableStateFlow<List<TvLibraryEntry>>? = null
    }
}
