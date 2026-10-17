package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single listening event for the DJ profile.
 */
data class ListenEvent(
    val songId: String,
    val title: String,
    val artist: String,
    val timestamp: Long,
    val completed: Boolean, // true if played to end, false if skipped
    val playDurationMs: Long, // how long it played before skip/end
)

/**
 * The DJ user profile: listening history, skips, and saves.
 * This is the "Data Collection" layer of the live DJ pipeline.
 * Persisted to SharedPreferences as JSON.
 */
class DjUserProfile(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /**
     * Record a listen event. Called when a track ends or is skipped.
     */
    suspend fun recordListen(
        song: Song,
        completed: Boolean,
        playDurationMs: Long,
    ) = withContext(Dispatchers.IO) {
        val events = getEvents().toMutableList()
        events.add(
            ListenEvent(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                timestamp = System.currentTimeMillis(),
                completed = completed,
                playDurationMs = playDurationMs,
            )
        )
        // Keep the last MAX_EVENTS; drop older.
        val trimmed = events.takeLast(MAX_EVENTS)
        prefs.edit().putString(KEY_EVENTS, eventsToJson(trimmed)).apply()
    }

    /**
     * Record a save (like). Called when the user likes a track.
     */
    suspend fun recordSave(song: Song) = withContext(Dispatchers.IO) {
        val saves = getSaves().toMutableSet()
        saves.add(song.id)
        // takeLast is List-only; convert for trimming.
        val trimmed = if (saves.size > MAX_SAVES) {
            saves.toList().takeLast(MAX_SAVES).toSet()
        } else {
            saves
        }
        prefs.edit().putStringSet(KEY_SAVES, trimmed).apply()
    }

    /**
     * Record an unsave (unlike).
     */
    suspend fun recordUnsave(songId: String) = withContext(Dispatchers.IO) {
        val saves = getSaves().toMutableSet()
        saves.remove(songId)
        prefs.edit().putStringSet(KEY_SAVES, saves).apply()
    }

    /** All recorded listen events, oldest first. */
    suspend fun getEvents(): List<ListenEvent> = withContext(Dispatchers.IO) {
        getEventsSync()
    }

    // Synchronous version for use in scoring (called off main thread).
    fun getEventsSync(): List<ListenEvent> {
        val json = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return jsonToEvents(json)
    }

    /** Set of saved (liked) song IDs. */
    fun getSaves(): Set<String> =
        prefs.getStringSet(KEY_SAVES, emptySet()) ?: emptySet()

    /**
     * Build a compact profile summary for the Live API session.
     * Includes top artists, skip rate, and recent taste signals.
     */
    fun buildProfileSummary(): String {
        val events = getEventsSync()
        if (events.isEmpty()) return "New listener, no history yet."

        val artistPlays = events.groupingBy { it.artist }.eachCount()
        val topArtists = artistPlays.entries
            .sortedByDescending { it.value }
            .take(10)
            .joinToString(", ") { "${it.key} (${it.value}x)" }

        val total = events.size
        val skipped = events.count { !it.completed }
        val skipRate = if (total > 0) (skipped * 100 / total) else 0

        val recent = events.takeLast(20)
        val recentArtists = recent.map { it.artist }.distinct().take(8).joinToString(", ")

        return buildString {
            appendLine("Listener profile:")
            appendLine("- Total plays tracked: $total, skip rate: $skipRate%")
            appendLine("- Top artists: $topArtists")
            appendLine("- Recently played artists: $recentArtists")
            appendLine("- Saved tracks: ${getSaves().size}")
        }
    }

    /** Clear all profile data. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private fun eventsToJson(events: List<ListenEvent>): String {
        val arr = JSONArray()
        for (e in events) {
            arr.put(JSONObject()
                .put("songId", e.songId)
                .put("title", e.title)
                .put("artist", e.artist)
                .put("timestamp", e.timestamp)
                .put("completed", e.completed)
                .put("playDurationMs", e.playDurationMs)
            )
        }
        return arr.toString()
    }

    private fun jsonToEvents(json: String): List<ListenEvent> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ListenEvent(
                    songId = o.optString("songId"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    timestamp = o.optLong("timestamp"),
                    completed = o.optBoolean("completed"),
                    playDurationMs = o.optLong("playDurationMs"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "dj_user_profile"
        private const val KEY_EVENTS = "listen_events"
        private const val KEY_SAVES = "saved_ids"
        private const val MAX_EVENTS = 500
        private const val MAX_SAVES = 1000
    }
}
