package com.ivor.ivormusic.data.tv

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One Home shelf: where it came from, and what to ask for the next page. */
data class TvShelf(
    val addonId: String,
    val addonName: String,
    val transportUrl: String,
    val catalogId: String,
    val type: String,
    val title: String,
    val genres: List<String> = emptyList(),
    val supportsSkip: Boolean = false,
    val items: List<TvItem> = emptyList(),
    val selectedGenre: String? = null,
    val isLoading: Boolean = false,
    /** Set when this shelf's own fetch failed, so the UI can say which one. */
    val failed: Boolean = false,
) {
    /** Stable across genre changes and reloads, so lazy list keys do not churn. */
    val key: String get() = "$addonId/$type/$catalogId"
}

/** Search results for one content type, merged across every catalog addon. */
data class TvSearchGroup(
    val type: String,
    val items: List<TvItem>,
)

/**
 * Reads across every installed addon.
 *
 * Two rules shape everything here, both inherited rather than invented:
 *
 * **Frugal per user action.** Cinemeta's catalog returns full meta objects, so
 * Home is one request per shelf and nothing else - no per-item `meta` call to
 * fill in a poster. Opening a detail page is one `meta` call, and only when the
 * item does not already carry its episodes.
 *
 * **A failing addon never blocks the addons that answered.** Every fan-out
 * collects failures alongside results and returns both; nothing here throws
 * upward. That is the same shape as the subscriptions feed, which had to learn
 * it the hard way.
 */
class TvRepository(context: Context) {

    private val client = StremioClient(context)
    private val addonRepository = AddonRepository(context)

    val addons: AddonRepository get() = addonRepository

    /**
     * Shelf descriptors for Home, before any content is fetched.
     *
     * Built from the installed manifests rather than a hardcoded list, which is
     * the same principle the channel page runs on: an addon describes its own
     * catalogs and genres, so a fixed list would draw empty shelves for addons
     * that lack them and hide real ones from addons that have more.
     */
    fun shelves(): List<TvShelf> = addonRepository.catalogProviders().flatMap { addon ->
        addon.browsableCatalogs.map { catalog ->
            TvShelf(
                addonId = addon.id,
                addonName = addon.name,
                transportUrl = addon.transportUrl,
                catalogId = catalog.id,
                type = catalog.type,
                title = catalog.name,
                genres = catalog.genreOptions,
                supportsSkip = catalog.supportsSkip,
            )
        }
    }

    /** One shelf's page. Returns null on failure so the caller can mark it. */
    suspend fun loadShelf(
        shelf: TvShelf,
        skip: Int = 0,
        genre: String? = null,
        forceFresh: Boolean = false,
    ): List<TvItem>? {
        val extras = buildList {
            genre?.takeIf { it.isNotBlank() }?.let { add("genre" to it) }
            if (skip > 0 && shelf.supportsSkip) add("skip" to skip.toString())
        }
        val response = client.catalog(
            shelf.transportUrl, shelf.type, shelf.catalogId, extras, forceFresh
        ) ?: return null
        return response.metas.filter { it.id.isNotBlank() }.also { TvItemCache.putAll(it) }
    }

    /**
     * Fetch the first page of every shelf, bounded.
     *
     * [CONCURRENCY] is 6, the same ceiling the subscriptions feed uses and for
     * the same reason: a user with a dozen catalog addons should not open a
     * dozen sockets the instant Home appears.
     */
    suspend fun loadAllShelves(
        shelves: List<TvShelf>,
        forceFresh: Boolean = false,
    ): List<TvShelf> = coroutineScope {
        val gate = Semaphore(CONCURRENCY)
        shelves.map { shelf ->
            async {
                gate.withPermit {
                    val items = loadShelf(shelf, forceFresh = forceFresh)
                    if (items == null) shelf.copy(failed = true, isLoading = false)
                    else shelf.copy(items = items, failed = false, isLoading = false)
                }
            }
        }.awaitAll()
    }

    /**
     * Full metadata for one item, asked of the addons that claim its id.
     *
     * Tried in installed order and the first usable answer wins, rather than
     * merged: two addons describing the same film disagree about episode
     * numbering often enough that interleaving them produces a list that exists
     * nowhere.
     */
    suspend fun meta(type: String, id: String, forceFresh: Boolean = false): TvItem? {
        val providers = addonRepository.metaProviders()
            .filter { it.handlesEnabled("meta", type, id) }
        if (providers.isEmpty()) {
            KLog.w(TAG, "No meta provider handles type=$type")
            return null
        }
        for (addon in providers) {
            val result = client.meta(addon.transportUrl, type, id, forceFresh)
            if (result != null && result.id.isNotBlank()) {
                TvItemCache.put(result)
                return result
            }
        }
        return null
    }

    /**
     * Search every searchable catalog, grouped by type.
     *
     * Deduplication is by id first, then by normalised title plus year, because
     * the same show legitimately appears as `tt22248376` from Cinemeta and
     * `kitsu:46474` from Anime Kitsu. **The anime-native id wins when both
     * exist**, since it is the one anime stream addons are keyed on - preferring
     * the IMDb id would produce a page nothing can find streams for.
     */
    suspend fun search(query: String): List<TvSearchGroup> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@coroutineScope emptyList()

        val gate = Semaphore(CONCURRENCY)
        val requests = addonRepository.catalogProviders().flatMap { addon ->
            addon.searchableCatalogs.map { catalog -> addon to catalog }
        }
        val results = requests.map { (addon, catalog) ->
            async {
                gate.withPermit {
                    client.catalog(
                        addon.transportUrl, catalog.type, catalog.id,
                        listOf("search" to trimmed)
                    )?.metas.orEmpty()
                }
            }
        }.awaitAll().flatten().filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .also { TvItemCache.putAll(it) }

        dedupe(results)
            .groupBy { it.type.ifBlank { "other" } }
            .map { (type, items) -> TvSearchGroup(type, items) }
            .sortedBy { TYPE_ORDER.indexOf(it.type).takeIf { i -> i >= 0 } ?: TYPE_ORDER.size }
    }

    companion object {
        private const val TAG = "TvRepository"

        /** Matches the subscriptions feed's ceiling, for the same reason. */
        const val CONCURRENCY = 6

        private val TYPE_ORDER = listOf("movie", "series", "anime")

        /**
         * Collapse the same title arriving from several addons.
         *
         * Exposed and pure so it can be tested: this is the one piece of search
         * whose failure mode is silent, showing a user two identical rows or,
         * worse, dropping the one that actually resolves to streams.
         */
        fun dedupe(items: List<TvItem>): List<TvItem> {
            val byId = LinkedHashMap<String, TvItem>()
            for (item in items) if (!byId.containsKey(item.id)) byId[item.id] = item

            val result = LinkedHashMap<String, TvItem>()
            for (item in byId.values) {
                val key = titleKey(item)
                val existing = result[key]
                if (existing == null) {
                    result[key] = item
                } else if (prefer(item, existing)) {
                    result[key] = item
                }
            }
            return result.values.toList()
        }

        /**
         * True when [candidate] should replace [current] as the survivor.
         *
         * An anime-native id beats an IMDb one, because the anime stream addons
         * index on it. Failing that, the entry carrying more artwork wins, since
         * the whole point of keeping one is that it is the one drawn.
         */
        private fun prefer(candidate: TvItem, current: TvItem): Boolean {
            val candidateAnime = candidate.isAnimeNativeId
            val currentAnime = current.isAnimeNativeId
            if (candidateAnime != currentAnime) return candidateAnime
            return candidate.artworkScore > current.artworkScore
        }

        private fun titleKey(item: TvItem): String {
            val year = item.releaseInfo?.take(4)?.filter { it.isDigit() }.orEmpty()
            val name = item.name.lowercase()
                .replace(NON_ALPHANUMERIC, "")
            return "$name|$year"
        }

        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]")
    }
}

/** Ids minted by the anime addons, which anime stream addons index on. */
internal val TvItem.isAnimeNativeId: Boolean
    get() = ANIME_ID_PREFIXES.any { id.startsWith(it) }

private val ANIME_ID_PREFIXES = listOf("kitsu:", "mal:", "anilist:", "anidb:")

internal val TvItem.artworkScore: Int
    get() = listOf(poster, background, logo).count { !it.isNullOrBlank() }


/**
 * The last few items seen in a catalog or a search result, by id.
 *
 * Exists so a detail page can paint from the item the user actually tapped -
 * poster, backdrop, logo, synopsis and cast all arrive with the catalog
 * response - while its episode list loads. Passing the item through a
 * navigation argument was the alternative, and a route argument is a String.
 *
 * In memory only, bounded, and never a source of truth: a miss simply means the
 * page shows a spinner for one request instead of none.
 */
object TvItemCache {
    private const val MAX_ENTRIES = 300
    private val lock = Any()
    private val entries = object : LinkedHashMap<String, TvItem>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TvItem>?): Boolean =
            size > MAX_ENTRIES
    }

    fun put(item: TvItem) {
        if (item.id.isBlank()) return
        synchronized(lock) { entries[item.id] = item }
    }

    fun putAll(items: List<TvItem>) = items.forEach(::put)

    fun get(id: String?): TvItem? {
        if (id.isNullOrBlank()) return null
        return synchronized(lock) { entries[id] }
    }
}
