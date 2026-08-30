package com.ivor.ivormusic.data.tv

import android.content.Context
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * URL construction for the Stremio addon protocol, hoisted out of the client so
 * it can be tested without a network or a Context.
 *
 * The shapes are `/{resource}/{type}/{id}.json` and, when there are extras,
 * `/{resource}/{type}/{id}/{k=v&k=v}.json`, both relative to the addon's
 * transport URL with `manifest.json` stripped. A configured addon carries its
 * configuration as path segments before that - `https://host/<config>/manifest.json`
 * - so stripping the filename rather than parsing the host is what keeps a
 * configured addon working.
 */
object StremioUrls {

    /** Everything before `manifest.json`, with exactly one trailing slash. */
    fun baseOf(transportUrl: String): String {
        val trimmed = transportUrl.trim()
        val withoutManifest = trimmed.removeSuffix("manifest.json")
        return if (withoutManifest.endsWith("/")) withoutManifest else "$withoutManifest/"
    }

    /**
     * Percent-encode one path segment the way `encodeURIComponent` does.
     *
     * `java.net.URLEncoder` cannot be used: it encodes a space as `+`, which is
     * a literal plus inside a path segment, and it leaves `:` alone. Anime ids
     * are `kitsu:46474` and episode ids are `kitsu:46474:1`, so the colon has to
     * become `%3A` for addons that route on exact segments.
     */
    fun encodeSegment(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in UNRESERVED) {
                append(c)
            } else {
                append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
            }
        }
    }

    /**
     * Serialise catalog extras. Keys and separators stay literal; only values
     * are encoded, because `genre=Sci-Fi&skip=20` has to keep its `=` and `&`.
     */
    fun encodeExtras(extras: List<Pair<String, String>>): String =
        extras.joinToString("&") { (k, v) -> "$k=${encodeSegment(v)}" }

    fun resource(
        transportUrl: String,
        resource: String,
        type: String,
        id: String,
        extras: List<Pair<String, String>> = emptyList(),
    ): String {
        val head = "${baseOf(transportUrl)}$resource/${encodeSegment(type)}/${encodeSegment(id)}"
        return if (extras.isEmpty()) "$head.json" else "$head/${encodeExtras(extras)}.json"
    }

    private const val UNRESERVED = "-_.~!*'()"
    private val HEX = "0123456789ABCDEF".toCharArray()
}

/**
 * HTTP for the addon protocol.
 *
 * **This is a separate OkHttpClient from `YouTubeRepository`'s, and it must
 * stay that way.** That client carries a [com.ivor.ivormusic.data.SessionCookieJar]
 * bound to the user's Google session; addons are arbitrary third-party servers,
 * and attaching that jar would send the user's YouTube cookies to every one of
 * them. Nothing here sends a credential of any kind. The local-only-mode
 * interceptor is shared in spirit but re-declared, because a mode that means
 * "no internet" has to mean it here too.
 *
 * Every call is a plain GET, which means - unlike the InnerTube layer, whose
 * POSTs OkHttp will never cache - responses genuinely cache. Addons send
 * `cacheMaxAge` and Cinemeta's catalogs send 4 hours of it.
 */
class StremioClient(context: Context) {

    private val appContext = context.applicationContext

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            if (ThemePreferences.isLocalOnly(appContext)) {
                throw java.io.IOException("Local only mode is on: network disabled")
            }
            chain.proceed(chain.request())
        }
        .apply { cache(appContext)?.let { cache(it) } }
        // Cinemeta's catalog endpoints answer 307 to cinemeta-catalogs.strem.io,
        // so redirects must be followed and the target must not be treated as
        // canonical. This is OkHttp's default; it is stated because turning it
        // off would silently empty every shelf.
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Raw GET returning the body, or null on any failure. Never throws upward. */
    private suspend fun getBody(url: String, forceFresh: Boolean): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .apply {
                        if (forceFresh) cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                    }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        KLog.w(TAG, "HTTP ${response.code} for $url")
                        return@use null
                    }
                    response.body?.string()
                }
            } catch (e: java.io.IOException) {
                KLog.w(TAG, "Request failed for $url: ${e.message}")
                null
            } catch (e: IllegalArgumentException) {
                // A malformed transportUrl the user pasted. Not a crash.
                KLog.w(TAG, "Bad URL $url: ${e.message}")
                null
            }
        }

    private inline fun <reified T> decode(body: String?, what: String): T? {
        if (body.isNullOrBlank()) return null
        return try {
            TvJson.instance.decodeFromString<T>(body)
        } catch (e: Exception) {
            // Addons are third-party servers: a malformed response is a normal
            // event, not an exceptional one, and must degrade to "this addon
            // had nothing" rather than taking down the surface that asked.
            KLog.w(TAG, "Malformed $what: ${e.message}")
            null
        }
    }

    suspend fun manifest(transportUrl: String, forceFresh: Boolean = false): AddonManifest? {
        val url = StremioUrls.baseOf(transportUrl) + "manifest.json"
        return decode<AddonManifest>(getBody(url, forceFresh), "manifest")
            ?.takeIf { it.id.isNotBlank() }
    }

    suspend fun catalog(
        transportUrl: String,
        type: String,
        catalogId: String,
        extras: List<Pair<String, String>> = emptyList(),
        forceFresh: Boolean = false,
    ): TvCatalogResponse? = decode(
        getBody(StremioUrls.resource(transportUrl, "catalog", type, catalogId, extras), forceFresh),
        "catalog"
    )

    suspend fun meta(
        transportUrl: String,
        type: String,
        id: String,
        forceFresh: Boolean = false,
    ): TvItem? = decode<TvMetaResponse>(
        getBody(StremioUrls.resource(transportUrl, "meta", type, id), forceFresh),
        "meta"
    )?.meta

    /** The addon directory an addon can publish about other addons. */
    suspend fun addonCatalog(
        transportUrl: String,
        type: String,
        catalogId: String,
    ): List<AddonDescriptor> = decode<AddonCatalogResponse>(
        getBody(StremioUrls.resource(transportUrl, "addon_catalog", type, catalogId), false),
        "addon_catalog"
    )?.addons.orEmpty()

    companion object {
        private const val TAG = "StremioClient"

        /**
         * Deliberately plain. Addons do not need to know which app is asking,
         * and a distinctive agent is one more thing that identifies a user.
         */
        private const val USER_AGENT = "Koda"

        private const val CACHE_DIR_NAME = "tv_addon_cache"
        private const val CACHE_BYTES = 8L * 1024 * 1024

        /**
         * Companion-level for the reason `YouTubeRepository`'s is: OkHttp's
         * `Cache` is a DiskLruCache holding an exclusive lock on its directory,
         * and this app builds repositories per ViewModel. Several `Cache`
         * objects over one directory is corruption, not contention.
         */
        @Volatile private var sharedCache: okhttp3.Cache? = null
        private val cacheLock = Any()

        private fun cache(context: Context): okhttp3.Cache? {
            sharedCache?.let { return it }
            return synchronized(cacheLock) {
                sharedCache ?: try {
                    okhttp3.Cache(
                        java.io.File(context.cacheDir, CACHE_DIR_NAME),
                        CACHE_BYTES,
                    ).also { sharedCache = it }
                } catch (e: Exception) {
                    KLog.w(TAG, "Addon cache unavailable: ${e.message}")
                    null
                }
            }
        }
    }
}
