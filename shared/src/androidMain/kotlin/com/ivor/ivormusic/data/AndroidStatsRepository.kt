package com.ivor.ivormusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.withContext

class AndroidStatsRepository(private val context: Context) : StatsRepository {

    private val historyFile = File(context.filesDir, "play_history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val TAG = "StatsRepository"
    private val MAX_HISTORY_ENTRIES = 5000
    private val mutex = Mutex()

    override suspend fun addPlayEvent(song: com.ivor.ivormusic.domain.Song) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val history = loadHistoryEntries().toMutableList()
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
                    thumbnailUrl = song.thumbnailUrl ?: song.albumArtUri,
                    source = song.source
                )
                history.add(0, entry)
                val trimmed = if (history.size > MAX_HISTORY_ENTRIES) history.take(MAX_HISTORY_ENTRIES) else history
                historyFile.writeText(json.encodeToString(trimmed))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving play event", e)
            }
        }
    }

    override suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.Default) {
        val history = loadHistoryEntries()
        if (history.isEmpty()) return@withContext GlobalStats()
        val totalPlayTime = history.sumOf { it.duration / 1000 }
        val songStats = history.groupBy { it.songId }.map { (id, entries) ->
            val first = entries.first()
            SongStats(id, first.title, first.artist, first.thumbnailUrl, entries.size, entries.sumOf { it.duration })
        }.sortedByDescending { it.playCount }
        val artistStats = history.groupBy { it.artist }.map { (name, entries) ->
            ArtistStats(name, entries.size, entries.distinctBy { it.songId }.size)
        }.sortedByDescending { it.playCount }
        GlobalStats(
            totalPlays = history.size,
            totalPlayTimeSeconds = totalPlayTime,
            topSongs = songStats.take(10),
            topArtists = artistStats.take(10),
            uniqueArtists = artistStats.size,
            uniqueSongs = songStats.size
        )
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        if (historyFile.exists()) historyFile.delete()
    }

    override suspend fun loadHistory(): List<PlayHistoryEntry> = loadHistoryEntries()

    override suspend fun getMonthlyPlays(): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistoryEntries()
        val calendar = Calendar.getInstance()
        history.groupBy {
            calendar.timeInMillis = it.timestamp
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)
            "$year-${month.toString().padStart(2, '0')}"
        }.mapValues { it.value.size }
    }

    override suspend fun getDailyPlays(daysLimit: Int): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistoryEntries()
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        history.filter { now - it.timestamp < daysLimit * 24 * 60 * 60 * 1000L }
            .groupBy {
                calendar.timeInMillis = it.timestamp
                "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}"
            }.mapValues { it.value.size }
    }

    private suspend fun loadHistoryEntries(): List<PlayHistoryEntry> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<PlayHistoryEntry>>(historyFile.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history", e)
            emptyList()
        }
    }
}
