package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Subscriptions and episode cache for RSS podcasts.
 *
 * Persistence mirrors [LikedSongsRepository]: feed metadata lives in
 * SharedPreferences for fast lookup, full feed+episode rows live in a JSON
 * file, and state is held in process-wide flows so a feed added anywhere is
 * visible everywhere immediately. No Room involved - the app stores
 * everything else the same way.
 */
class PodcastRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val storeFile: File = File(appContext.filesDir, "$STORE_FILE_BASE.json")
    private val mutex = Mutex()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val TAG = "PodcastRepository"
        internal const val PREFS_NAME = "ivor_music_podcasts"
        internal const val STORE_FILE_BASE = "podcast_store"
        private const val KEY_FEED_IDS = "feed_ids"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        // Process-wide state (see class doc).
        private val _feeds = MutableStateFlow<List<PodcastFeed>>(emptyList())
        private val _episodesByFeed = MutableStateFlow<Map<String, List<PodcastEpisode>>>(emptyMap())
        private var loaded = false
        private val loadMutex = Mutex()
    }

    val feeds: StateFlow<List<PodcastFeed>> = _feeds.asStateFlow()
    val episodesByFeed: StateFlow<Map<String, List<PodcastEpisode>>> =
        _episodesByFeed.asStateFlow()

    /** Episodes across all feeds, newest first. */
    fun allEpisodes(): List<PodcastEpisode> =
        _episodesByFeed.value.values.flatten().sortedByDescending { it.pubDate }

    suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                try {
                    if (storeFile.exists()) {
                        val store = json.decodeFromString<PodcastStore>(
                            storeFile.readText()
                        )
                        _feeds.value = store.feeds.sortedBy { it.addedAt }
                        _episodesByFeed.value = store.episodes
                    }
                    loaded = true
                } catch (e: Exception) {
                    KLog.w(TAG, "Failed to load podcast store", e)
                    loaded = true
                }
            }
        }
    }

    /**
     * Fetches [rawUrl] as an RSS feed, parses it and subscribes.
     * Returns the feed on success; re-adding an existing URL just refreshes it.
     */
    suspend fun addFeed(rawUrl: String): Result<PodcastFeed> {
        ensureLoaded()
        val url = normalizeFeedUrl(rawUrl)
            ?: return Result.failure(IllegalArgumentException("Not a valid feed URL"))
        return try {
            val parsed = fetchAndParse(url)
            mutex.withLock {
                val id = feedIdFor(url)
                val existing = _feeds.value.find { it.id == id }
                val feed = PodcastFeed(
                    id = id,
                    title = parsed.title,
                    feedUrl = url,
                    siteUrl = parsed.siteUrl,
                    description = parsed.description,
                    artworkUrl = parsed.artworkUrl,
                    addedAt = existing?.addedAt ?: System.currentTimeMillis(),
                )
                val episodes = parsed.episodes.map { ep ->
                    PodcastEpisode(
                        id = ep.guid?.takeIf { it.isNotBlank() } ?: ep.audioUrl,
                        feedId = id,
                        title = ep.title,
                        audioUrl = ep.audioUrl,
                        artworkUrl = ep.artworkUrl ?: parsed.artworkUrl,
                        description = ep.description,
                        pubDate = ep.pubDate,
                        durationMs = ep.durationMs,
                    )
                }
                _feeds.value = (_feeds.value.filterNot { it.id == id } + feed)
                    .sortedBy { it.addedAt }
                _episodesByFeed.value = _episodesByFeed.value + (id to episodes)
                persistLocked()
                Result.success(feed)
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to add podcast feed $url", e)
            Result.failure(e)
        }
    }

    /** Re-fetches a subscribed feed, replacing its episodes. */
    suspend fun refreshFeed(feedId: String): Result<Int> {
        ensureLoaded()
        val feed = _feeds.value.find { it.id == feedId }
            ?: return Result.failure(IllegalArgumentException("Unknown feed"))
        return try {
            val parsed = fetchAndParse(feed.feedUrl)
            mutex.withLock {
                val episodes = parsed.episodes.map { ep ->
                    PodcastEpisode(
                        id = ep.guid?.takeIf { it.isNotBlank() } ?: ep.audioUrl,
                        feedId = feedId,
                        title = ep.title,
                        audioUrl = ep.audioUrl,
                        artworkUrl = ep.artworkUrl ?: parsed.artworkUrl,
                        description = ep.description,
                        pubDate = ep.pubDate,
                        durationMs = ep.durationMs,
                    )
                }
                val updated = feed.copy(
                    title = parsed.title,
                    siteUrl = parsed.siteUrl ?: feed.siteUrl,
                    description = parsed.description ?: feed.description,
                    artworkUrl = parsed.artworkUrl ?: feed.artworkUrl,
                )
                _feeds.value = _feeds.value.map { if (it.id == feedId) updated else it }
                _episodesByFeed.value = _episodesByFeed.value + (feedId to episodes)
                persistLocked()
                Result.success(episodes.size)
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to refresh podcast feed ${feed.feedUrl}", e)
            Result.failure(e)
        }
    }

    suspend fun removeFeed(feedId: String) {
        ensureLoaded()
        mutex.withLock {
            _feeds.value = _feeds.value.filterNot { it.id == feedId }
            _episodesByFeed.value = _episodesByFeed.value - feedId
            persistLocked()
        }
    }

    fun isSubscribed(feedUrl: String): Boolean {
        val id = feedIdFor(normalizeFeedUrl(feedUrl) ?: return false)
        return _feeds.value.any { it.id == id }
    }

    /**
     * Adapts an episode to the player's [Song] model so episodes play through
     * the normal music pipeline (queue, notification, Auto) with the show as
     * artist/album and its artwork.
     */
    fun episodeToSong(episode: PodcastEpisode, feed: PodcastFeed?): Song {
        return Song(
            id = "${EpisodeProgressRepository.PODCAST_SONG_ID_PREFIX}${episode.id}",
            title = episode.title,
            artist = feed?.title ?: "Podcast",
            album = feed?.title ?: "Podcast",
            duration = episode.durationMs,
            uri = Uri.parse(episode.audioUrl),
            thumbnailUrl = episode.artworkUrl,
            source = SongSource.PODCAST,
            dateAdded = episode.pubDate.takeIf { it > 0L },
        )
    }

    // --- internals ---

    private fun normalizeFeedUrl(raw: String): String? {
        var url = raw.trim()
        if (url.isEmpty()) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url.takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        }
    }

    private fun feedIdFor(feedUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(feedUrl.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }

    private suspend fun fetchAndParse(feedUrl: String): ParsedPodcastFeed =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(feedUrl)
                .header(
                    "User-Agent",
                    "KodaPodcasts/1.0 (RSS reader; +https://github.com/Ivorisnoob/Koda)"
                )
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Feed request failed: HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: throw IllegalStateException("Empty feed response")
                val parsed = PodcastRssParser.parse(body)
                if (parsed.episodes.isEmpty()) {
                    throw IllegalStateException("No playable episodes found in feed")
                }
                parsed
            }
        }

    /** Must hold [mutex]. */
    private fun persistLocked() {
        try {
            prefs.edit().putStringSet(KEY_FEED_IDS, _feeds.value.map { it.id }.toSet())
                .apply()
            storeFile.writeText(
                json.encodeToString(
                    PodcastStore(
                        feeds = _feeds.value,
                        episodes = _episodesByFeed.value,
                    )
                )
            )
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to persist podcast store", e)
        }
    }

    @kotlinx.serialization.Serializable
    private data class PodcastStore(
        val feeds: List<PodcastFeed> = emptyList(),
        val episodes: Map<String, List<PodcastEpisode>> = emptyMap(),
    )
}
