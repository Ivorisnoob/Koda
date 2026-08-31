package com.ivor.ivormusic.data.tv

import android.content.Context
import android.content.SharedPreferences
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * One addon the user has installed, plus the local decisions about it.
 *
 * [manifest] is cached rather than re-fetched per launch, so the app knows what
 * an addon offers while offline. [order] is priority and drives both shelf
 * order on Home and result order in the source sheet.
 */
@Serializable
data class InstalledAddon(
    val transportUrl: String,
    val manifest: AddonManifest,
    val order: Int = 0,
    /**
     * Resources switched off locally, by name. Kept as an opt-out set rather
     * than an opt-in one so that an addon which grows a new resource in a later
     * version is usable without the user going back to re-enable anything.
     */
    val disabledResources: Set<String> = emptySet(),
    /** Preinstalled addons can be disabled but not removed, so the list is never empty. */
    val isPreinstalled: Boolean = false,
) {
    val id: String get() = manifest.id
    val name: String get() = manifest.name

    fun providesEnabled(resource: String): Boolean =
        manifest.provides(resource) && resource !in disabledResources

    fun handlesEnabled(resource: String, type: String, itemId: String): Boolean =
        resource !in disabledResources && manifest.handles(resource, type, itemId)

    /** Browsable catalogs only: search-only ones must never become shelves. */
    val browsableCatalogs: List<AddonCatalog>
        get() = if (!providesEnabled("catalog")) emptyList()
        else manifest.catalogs.filter { it.isBrowsable }

    val searchableCatalogs: List<AddonCatalog>
        get() = if (!providesEnabled("catalog")) emptyList()
        else manifest.catalogs.filter { it.supportsSearch }
}

/**
 * The installed addon list.
 *
 * **Not process-wide**, unlike the watchlist and progress stores. Addons are
 * changed on one screen and re-read when a surface resumes, which is the same
 * rule settings already follow: a ViewModel that needs the list at decision
 * time does a fresh read. Making it a process-wide singleton would buy nothing
 * that [reload] does not.
 *
 * **Transport URLs are credentials.** A configured addon carries its debrid API
 * key in its own path, so this store is written to `EncryptedSharedPreferences`
 * and must stay out of backups and out of diagnostics. Nothing here may be
 * logged verbatim; [InstalledAddon.name] is safe, [InstalledAddon.transportUrl]
 * is not.
 */
class AddonRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = securePrefs(appContext)

    private val _addons = MutableStateFlow(load())
    val addons: StateFlow<List<InstalledAddon>> = _addons.asStateFlow()

    /** Enabled addons in priority order. */
    fun enabled(): List<InstalledAddon> = _addons.value.sortedBy { it.order }

    fun catalogProviders(): List<InstalledAddon> = enabled().filter { it.providesEnabled("catalog") }

    fun metaProviders(): List<InstalledAddon> = enabled().filter { it.providesEnabled("meta") }

    fun streamProviders(): List<InstalledAddon> = enabled().filter { it.providesEnabled("stream") }

    fun subtitleProviders(): List<InstalledAddon> =
        enabled().filter { it.providesEnabled("subtitles") }

    /** True when nothing installed can produce a playable file. */
    fun hasNoStreamSource(): Boolean = streamProviders().isEmpty()

    fun reload() {
        _addons.value = load()
    }

    fun install(transportUrl: String, manifest: AddonManifest): Boolean {
        val current = _addons.value
        if (manifest.id.isBlank()) return false
        // Re-installing an addon replaces it rather than duplicating: the same
        // addon at a new URL is the normal shape of "I reconfigured it".
        val without = current.filterNot { it.manifest.id == manifest.id }
        val next = without + InstalledAddon(
            transportUrl = transportUrl,
            manifest = manifest,
            order = (without.maxOfOrNull { it.order } ?: -1) + 1,
        )
        return save(next)
    }

    fun remove(addonId: String) {
        val target = _addons.value.firstOrNull { it.id == addonId } ?: return
        if (target.isPreinstalled) return
        save(_addons.value.filterNot { it.id == addonId })
    }

    /**
     * Replace a stored manifest with a freshly fetched one.
     *
     * [scar] Without this, the seeded manifests in [defaultAddons] were the
     * only ones the preinstalled addons ever had: `manifest()` was called on
     * exactly one code path, installing an addon by hand, so Cinemeta and Kitsu
     * ran forever on whatever shape was hardcoded here. Two Cinemeta shelves
     * and one Kitsu shelf simply did not exist in the app because the seed
     * predated them, and no amount of reloading would have found them.
     *
     * Keyed by id and deliberately narrow: it rewrites the manifest and touches
     * neither [InstalledAddon.order] nor [InstalledAddon.disabledResources],
     * because those are the user's decisions and an addon growing a resource
     * must not silently re-enable one they switched off.
     *
     * Returns true when something actually changed, so a caller can avoid
     * rebuilding shelves for a no-op refresh.
     */
    fun updateManifest(addonId: String, manifest: AddonManifest): Boolean {
        if (manifest.id.isBlank()) return false
        val current = _addons.value.firstOrNull { it.id == addonId } ?: return false
        if (current.manifest == manifest) return false
        return save(_addons.value.map { addon ->
            if (addon.id == addonId) addon.copy(manifest = manifest) else addon
        })
    }

    fun setResourceEnabled(addonId: String, resource: String, enabled: Boolean) {
        save(_addons.value.map { addon ->
            if (addon.id != addonId) addon
            else addon.copy(
                disabledResources = if (enabled) addon.disabledResources - resource
                else addon.disabledResources + resource
            )
        })
    }

    /** Reorder by id. Anything omitted keeps its relative position at the end. */
    fun reorder(orderedIds: List<String>) {
        val rank = orderedIds.withIndex().associate { (i, id) -> id to i }
        save(_addons.value
            .sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            .mapIndexed { index, addon -> addon.copy(order = index) })
    }

    private fun save(list: List<InstalledAddon>): Boolean {
        val normalised = list.mapIndexed { index, addon -> addon.copy(order = index) }
        val secureStore = prefs ?: return false
        val saved = try {
            secureStore.edit()
                .putString(KEY_ADDONS, TvJson.instance.encodeToString(normalised))
                .commit()
        } catch (e: Exception) {
            KLog.w(TAG, "Could not persist addon list: " + e.javaClass.simpleName)
            false
        }
        if (!saved) return false
        _addons.value = normalised
        return true
    }

    private fun load(): List<InstalledAddon> {
        val stored = try {
            prefs?.getString(KEY_ADDONS, null)
        } catch (e: Exception) {
            KLog.w(TAG, "Could not read addon list: " + e.javaClass.simpleName)
            null
        }
        if (stored.isNullOrBlank()) return defaultAddons()
        return try {
            TvJson.instance.decodeFromString<List<InstalledAddon>>(stored)
                .sortedBy { it.order }
                .ifEmpty { defaultAddons() }
        } catch (e: Exception) {
            // A store written by an older shape. Falling back to the defaults
            // loses the user's additions, which is bad, but leaving TV mode with
            // no metadata source at all is worse and unrecoverable from the UI.
            // The serialized value contains configured transport URLs, so an
            // exception message that quotes input must never reach logs.
            KLog.w(TAG, "Addon list unreadable: " + e.javaClass.simpleName)
            defaultAddons()
        }
    }

    private fun securePrefs(context: Context): SharedPreferences? = try {
        val key = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            key,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Keystore failures happen on a small number of devices. TV mode
        // degrading to the preinstalled addons is better than storing a debrid
        // API key in plaintext. Installs fail visibly until secure storage works.
        KLog.w(TAG, "Encrypted prefs unavailable: " + e.javaClass.simpleName)
        null
    }

    companion object {
        private const val TAG = "AddonRepository"
        const val PREFS_NAME = "tv_addons"
        private const val KEY_ADDONS = "installed_addons"

        const val CINEMETA_URL = "https://v3-cinemeta.strem.io/manifest.json"
        const val KITSU_URL = "https://anime-kitsu.strem.fun/manifest.json"
        const val OPENSUBTITLES_URL = "https://opensubtitles-v3.strem.io/manifest.json"

        /**
         * The three that ship installed, and the reason they are these three:
         * all are keyless, and **none of them is a source of files**. They make
         * browsing, searching, watchlisting and subtitle choice work on first
         * launch, and leave the one decision that is genuinely the user's -
         * where video comes from - to the user.
         *
         * Manifests are seeded from the known shape and refreshed on first
         * successful fetch, so TV mode has working shelves before any network
         * call completes and offline on the very first launch.
         */
        fun defaultAddons(): List<InstalledAddon> = listOf(
            InstalledAddon(
                transportUrl = CINEMETA_URL,
                order = 0,
                isPreinstalled = true,
                manifest = AddonManifest(
                    id = "com.linvo.cinemeta",
                    name = "Cinemeta",
                    description = "Movie and series catalogs",
                    types = listOf("movie", "series"),
                    idPrefixes = listOf("tt"),
                    resources = listOf(
                        AddonResource("catalog"),
                        AddonResource("meta"),
                        AddonResource("addon_catalog"),
                    ),
                    // Matches the live manifest [verified August 2026]: eight
                    // catalogs, of which six are browsable. `last-videos` and
                    // `calendar-videos` require id lists Koda cannot supply, so
                    // isBrowsable correctly drops them and they are not seeded.
                    catalogs = listOf(
                        AddonCatalog("movie", "top", "Popular",
                            extra = listOf(AddonExtra("genre"), AddonExtra("search"), AddonExtra("skip"))),
                        AddonCatalog("series", "top", "Popular",
                            extra = listOf(AddonExtra("genre"), AddonExtra("search"), AddonExtra("skip"))),
                        AddonCatalog("movie", "imdbRating", "Featured",
                            extra = listOf(AddonExtra("genre"), AddonExtra("skip"))),
                        AddonCatalog("series", "imdbRating", "Featured",
                            extra = listOf(AddonExtra("genre"), AddonExtra("skip"))),
                        // `genre` really is required on these two, unlike the
                        // rest. It is the one required extra isBrowsable can
                        // satisfy, so they stay browsable.
                        AddonCatalog("movie", "year", "New",
                            extra = listOf(AddonExtra("genre", isRequired = true), AddonExtra("skip"))),
                        AddonCatalog("series", "year", "New",
                            extra = listOf(AddonExtra("genre", isRequired = true), AddonExtra("skip"))),
                    ),
                ),
            ),
            InstalledAddon(
                transportUrl = KITSU_URL,
                order = 1,
                isPreinstalled = true,
                manifest = AddonManifest(
                    id = "community.anime.kitsu",
                    name = "Anime Kitsu",
                    description = "Anime catalogs and metadata",
                    types = listOf("anime", "movie", "series"),
                    idPrefixes = listOf("kitsu", "mal", "anilist", "anidb"),
                    resources = listOf(
                        AddonResource("catalog"), AddonResource("meta"), AddonResource("subtitles")
                    ),
                    // Matches the live manifest [verified August 2026]. The
                    // list catalog is search-only and stays that way; the other
                    // four are shelves.
                    catalogs = listOf(
                        AddonCatalog("anime", "kitsu-anime-trending", "Kitsu Trending"),
                        AddonCatalog("anime", "kitsu-anime-airing", "Kitsu Top Airing",
                            extra = listOf(AddonExtra("genre"), AddonExtra("skip"))),
                        AddonCatalog("anime", "kitsu-anime-popular", "Kitsu Most Popular",
                            extra = listOf(AddonExtra("genre"), AddonExtra("skip"))),
                        AddonCatalog("anime", "kitsu-anime-rating", "Kitsu Highest Rated",
                            extra = listOf(AddonExtra("genre"), AddonExtra("skip"))),
                        AddonCatalog("anime", "kitsu-anime-list", "Kitsu",
                            extra = listOf(
                                AddonExtra("search", isRequired = true), AddonExtra("skip")
                            )),
                    ),
                ),
            ),
            InstalledAddon(
                transportUrl = OPENSUBTITLES_URL,
                order = 2,
                isPreinstalled = true,
                manifest = AddonManifest(
                    id = "org.stremio.opensubtitlesv3",
                    name = "OpenSubtitles v3",
                    description = "Subtitles in many languages",
                    types = listOf("movie", "series"),
                    idPrefixes = listOf("tt"),
                    resources = listOf(AddonResource("subtitles")),
                ),
            ),
        )
    }
}
