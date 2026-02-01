package com.ivor.ivormusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

@Serializable
data class PlayHistoryEntry(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val timestamp: Long,
    val duration: Long,
    val thumbnailUrl: String? = null,
    val source: SongSource = SongSource.YOUTUBE
)

@Serializable
data class SongStats(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val playCount: Int,
    val totalPlayTime: Long
)

@Serializable
data class ArtistStats(
    val name: String,
    val playCount: Int,
    val songCount: Int
)

class StatsRepository(private val context: Context) {
    private val historyFile = File(context.filesDir, "play_history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val TAG = "StatsRepository"
    private val MAX_HISTORY_ENTRIES = 5000
    private val mutex = Mutex()

    suspend fun addPlayEvent(song: Song) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val history = loadHistory().toMutableList()
                
                // DEBOUNCE: Don't add the same song if it was added less than 10 seconds ago
                // This prevents duplicate entries from media item resolution loops.
                val lastEntry = history.firstOrNull()
                if (lastEntry?.songId == song.id && (System.currentTimeMillis() - lastEntry.timestamp) < 10000L) {
                    Log.d(TAG, "Stats: Debouncing duplicate play event for ${song.title}")
                    return@withLock
                }

                val entry = PlayHistoryEntry(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    timestamp = System.currentTimeMillis(),
                    duration = song.duration,
                    thumbnailUrl = song.thumbnailUrl ?: song.albumArtUri?.toString(),
                    source = song.source
                )
                history.add(0, entry)
                val trimmedHistory = if (history.size > MAX_HISTORY_ENTRIES) {
                    history.take(MAX_HISTORY_ENTRIES)
                } else history
                historyFile.writeText(json.encodeToString(trimmedHistory))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving play event", e)
            }
        }
    }

    suspend fun loadHistory(): List<PlayHistoryEntry> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<PlayHistoryEntry>>(historyFile.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history", e)
            emptyList()
        }
    }

    suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.Default) {
        val history = loadHistory()
        if (history.isEmpty()) return@withContext GlobalStats()
        val totalPlayTime = history.sumOf { it.duration / 1000 }
        val totalPlays = history.size
        val songStats = history.groupBy { it.songId }.map { (id, entries) ->
            val first = entries.first()
            SongStats(
                songId = id,
                title = first.title,
                artist = first.artist,
                thumbnailUrl = first.thumbnailUrl,
                playCount = entries.size,
                totalPlayTime = entries.sumOf { it.duration }
            )
        }.sortedByDescending { it.playCount }
        val artistStats = history.groupBy { it.artist }.map { (name, entries) ->
            ArtistStats(
                name = name,
                playCount = entries.size,
                songCount = entries.distinctBy { it.songId }.size
            )
        }.sortedByDescending { it.playCount }
        GlobalStats(
            totalPlays = totalPlays,
            totalPlayTimeSeconds = totalPlayTime,
            topSongs = songStats.take(10),
            topArtists = artistStats.take(10),
            uniqueArtists = artistStats.size,
            uniqueSongs = songStats.size
        )
    }

    suspend fun getMonthlyPlays(): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistory()
        val calendar = Calendar.getInstance()
        history.groupBy {
            calendar.timeInMillis = it.timestamp
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)
            "$year-${month.toString().padStart(2, '0')}"
        }.mapValues { it.value.size }
    }
    
    suspend fun getDailyPlays(daysLimit: Int = 7): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistory()
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        history.filter { now - it.timestamp < daysLimit * 24 * 60 * 60 * 1000L }
            .groupBy {
                calendar.timeInMillis = it.timestamp
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val month = calendar.get(Calendar.MONTH) + 1
                "$month/$day"
            }.mapValues { it.value.size }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        if (historyFile.exists()) historyFile.delete()
    }
}

data class GlobalStats(
    val totalPlays: Int = 0,
    val totalPlayTimeSeconds: Long = 0,
    val topSongs: List<SongStats> = emptyList(),
    val topArtists: List<ArtistStats> = emptyList(),
    val uniqueArtists: Int = 0,
    val uniqueSongs: Int = 0
)
