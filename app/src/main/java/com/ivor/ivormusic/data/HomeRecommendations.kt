package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val HOME_RECOMMENDATION_LIMIT = 30
private const val HOME_RECOMMENDATION_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

/** How many parsed songs a Home fetch keeps for a refresh to rotate through. */
internal const val HOME_RECOMMENDATION_POOL = 90

/** The songs at the head of Home that a refresh is expected to change. */
internal const val HOME_LEAD_SIZE = 6

/**
 * Reorder a fetched Home list so a refresh leads with songs that have not led
 * recently.
 *
 * YouTube Music answers a refresh with much the same list in the same order,
 * and Home draws from the head of it, so pulling to refresh showed the same
 * three covers every time. Songs that have not led go first, in the order
 * YouTube ranked them; the ones that have follow, longest ago first, so the
 * list comes round again once everything has had its turn.
 *
 * @param recentLeads ids that led recently, oldest first.
 */
internal fun rotateHomeRecommendations(
    songs: List<Song>,
    recentLeads: List<String>
): List<Song> {
    if (recentLeads.isEmpty()) return songs
    val ledAt = HashMap<String, Int>()
    recentLeads.forEachIndexed { index, id -> ledAt[id] = index }
    val (led, fresh) = songs.partition { it.id in ledAt }
    return fresh + led.sortedBy { ledAt.getValue(it.id) }
}

/** Drop renderer placeholders before they reach Home's three artwork shapes. */
internal fun usableHomeRecommendations(
    sources: List<List<Song>>,
    limit: Int = HOME_RECOMMENDATION_LIMIT
): List<Song> = sources.asSequence()
    .flatten()
    .filter { song ->
        song.id.isNotBlank() && song.title.trim().lowercase() !in setOf(
            "",
            "none",
            "null",
            "unknown",
            "unknown title"
        )
    }
    .distinctBy { it.id }
    .take(limit)
    .toList()

/**
 * Small profile-scoped metadata cache for Classic Home.
 *
 * This is not media and never affects offline playback. It lets Home render a
 * last-known-good set immediately while a fresh FEmusic_home request runs, and
 * prevents one transient empty response after process start from collapsing
 * the three recommendation artworks to nothing.
 */
internal class HomeRecommendationCache(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(nowMs: Long = System.currentTimeMillis()): List<Song> {
        val encoded = prefs.getString(profileKey(), null) ?: return emptyList()
        return try {
            val snapshot = json.decodeFromString<Snapshot>(encoded)
            if (nowMs - snapshot.savedAtMs !in 0..HOME_RECOMMENDATION_MAX_AGE_MS) {
                emptyList()
            } else {
                usableHomeRecommendations(listOf(snapshot.songs))
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Could not read cached Home recommendations", e)
            emptyList()
        }
    }

    fun save(songs: List<Song>, nowMs: Long = System.currentTimeMillis()) {
        val usable = usableHomeRecommendations(listOf(songs))
        if (usable.isEmpty()) return
        try {
            prefs.edit().putString(
                profileKey(),
                json.encodeToString(Snapshot(savedAtMs = nowMs, songs = usable))
            ).apply()
        } catch (e: Exception) {
            KLog.w(TAG, "Could not cache Home recommendations", e)
        }
    }

    fun clear() {
        prefs.edit().remove(profileKey()).apply()
    }

    private fun profileKey(): String = ProfileManager.profileScopedKey(
        BASE_KEY,
        ProfileManager.activeProfileId(appContext),
        ProfileManager.legacyProfileId(appContext)
    )

    @Serializable
    private data class Snapshot(
        val savedAtMs: Long,
        val songs: List<Song>
    )

    companion object {
        private const val TAG = "HomeRecommendationCache"
        private const val PREFS_NAME = "ivor_music_home_cache"
        private const val BASE_KEY = "recommendations"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
