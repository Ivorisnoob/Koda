package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.SongSource
import kotlinx.serialization.Serializable

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

data class GlobalStats(
    val totalPlays: Int = 0,
    val totalPlayTimeSeconds: Long = 0,
    val topSongs: List<SongStats> = emptyList(),
    val topArtists: List<ArtistStats> = emptyList(),
    val uniqueArtists: Int = 0,
    val uniqueSongs: Int = 0
)

/**
 * Platform-independent stats repository interface.
 * Implementation lives in androidMain (file-based JSON history).
 */
interface StatsRepository {
    suspend fun addPlayEvent(song: Song)
    suspend fun loadHistory(): List<PlayHistoryEntry>
    suspend fun getGlobalStats(): GlobalStats
    suspend fun getMonthlyPlays(): Map<String, Int>
    suspend fun getDailyPlays(daysLimit: Int = 7): Map<String, Int>
    suspend fun clearHistory()
}
