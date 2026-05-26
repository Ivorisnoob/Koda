package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

class DesktopStatsRepository : StatsRepository {

    private val configDir = File(System.getProperty("user.home"), ".config/koda").also { it.mkdirs() }
    private val historyFile = File(configDir, "play_history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()
    private val MAX_HISTORY = 5000

    override suspend fun addPlayEvent(song: Song) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val history = loadHistoryEntries().toMutableList()
                val last = history.firstOrNull()
                if (last?.songId == song.id && (System.currentTimeMillis() - last.timestamp) < 10_000L) return@withLock
                history.add(0, PlayHistoryEntry(
                    songId = song.id, title = song.title, artist = song.artist,
                    album = song.album, timestamp = System.currentTimeMillis(),
                    duration = song.duration, thumbnailUrl = song.thumbnailUrl,
                    source = song.source
                ))
                val trimmed = if (history.size > MAX_HISTORY) history.take(MAX_HISTORY) else history
                historyFile.writeText(json.encodeToString(trimmed))
            } catch (_: Exception) {}
        }
    }

    override suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.Default) {
        val history = loadHistoryEntries()
        if (history.isEmpty()) return@withContext GlobalStats()
        val songStats = history.groupBy { it.songId }.map { (id, entries) ->
            val first = entries.first()
            SongStats(id, first.title, first.artist, first.thumbnailUrl, entries.size, entries.sumOf { it.duration })
        }.sortedByDescending { it.playCount }
        val artistStats = history.groupBy { it.artist }.map { (name, entries) ->
            ArtistStats(name, entries.size, entries.distinctBy { it.songId }.size)
        }.sortedByDescending { it.playCount }
        GlobalStats(
            totalPlays = history.size,
            totalPlayTimeSeconds = history.sumOf { it.duration / 1000 },
            topSongs = songStats.take(10),
            topArtists = artistStats.take(10),
            uniqueArtists = artistStats.size,
            uniqueSongs = songStats.size
        )
    }

    override suspend fun loadHistory(): List<PlayHistoryEntry> = loadHistoryEntries()

    override suspend fun getMonthlyPlays(): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistoryEntries()
        val cal = Calendar.getInstance()
        history.groupBy { entry ->
            cal.timeInMillis = entry.timestamp
            "${cal.get(Calendar.YEAR)}-${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}"
        }.mapValues { it.value.size }
    }

    override suspend fun getDailyPlays(daysLimit: Int): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistoryEntries()
        val cal = Calendar.getInstance()
        history.groupBy { entry ->
            cal.timeInMillis = entry.timestamp
            "${cal.get(Calendar.YEAR)}-${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}-${cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}"
        }.mapValues { it.value.size }
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyFile.delete()
        Unit
    }

    private suspend fun loadHistoryEntries(): List<PlayHistoryEntry> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        try { json.decodeFromString(historyFile.readText()) } catch (_: Exception) { emptyList() }
    }
}
