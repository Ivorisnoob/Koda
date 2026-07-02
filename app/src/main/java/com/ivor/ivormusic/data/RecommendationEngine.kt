package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Local recommendation engine.
 *
 * Builds a "taste profile" from what the user actually does in the app —
 * play history ([StatsRepository]), liked songs ([LikedSongsRepository]) and
 * search history ([SearchHistoryRepository]) — and uses it to personalize
 * both the home feed and the auto-queue, without requiring a YouTube login.
 *
 * Scoring: each play contributes a recency-decayed weight (half-life
 * [HALF_LIFE_DAYS]), so heavy rotation from months ago fades out while this
 * week's obsession dominates. Liked songs get a flat bonus on top.
 */
class RecommendationEngine(
    context: Context,
    private val youTubeRepository: YouTubeRepository
) {
    private val statsRepository = StatsRepository(context)
    private val searchHistoryRepository = SearchHistoryRepository(context)
    private val likedSongsRepository = LikedSongsRepository(context)

    data class TasteProfile(
        /** Artist names ranked by recency-weighted listening, best first. */
        val topArtists: List<String>,
        /** The user's highest-scoring YouTube songs (radio seed candidates). */
        val topSongs: List<PlayHistoryEntry>,
        /** Most recent search queries, newest first. */
        val recentSearches: List<String>
    ) {
        fun isEmpty() = topArtists.isEmpty() && topSongs.isEmpty() && recentSearches.isEmpty()
    }

    companion object {
        /** A play from two weeks ago counts half as much as one from today. */
        private const val HALF_LIFE_DAYS = 14.0

        /** Flat score bonus for songs the user explicitly liked. */
        private const val LIKED_BONUS = 3.0

        private const val MAX_ARTISTS = 8
        private const val MAX_TOP_SONGS = 10
        private const val MAX_RECENT_SEARCHES = 5
        private const val MS_PER_DAY = 86_400_000.0
    }

    suspend fun buildTasteProfile(): TasteProfile = withContext(Dispatchers.Default) {
        val history = statsRepository.loadHistory()
        val likedIds = likedSongsRepository.getAllLikedSongIds()
        val now = System.currentTimeMillis()

        // Recency-weighted score per song. History is newest-first, so the
        // first entry seen for an id carries the freshest metadata.
        val songScores = HashMap<String, Double>()
        val entryById = HashMap<String, PlayHistoryEntry>()
        for (entry in history) {
            val ageDays = (now - entry.timestamp).coerceAtLeast(0) / MS_PER_DAY
            val weight = 0.5.pow(ageDays / HALF_LIFE_DAYS)
            songScores.merge(entry.songId, weight, Double::plus)
            entryById.putIfAbsent(entry.songId, entry)
        }
        for (id in likedIds) {
            if (entryById.containsKey(id)) {
                songScores.merge(id, LIKED_BONUS, Double::plus)
            }
        }

        val rankedIds = songScores.entries.sortedByDescending { it.value }.map { it.key }

        val topSongs = rankedIds.asSequence()
            .mapNotNull { entryById[it] }
            .filter { it.source == SongSource.YOUTUBE }
            .take(MAX_TOP_SONGS)
            .toList()

        val artistScores = HashMap<String, Double>()
        for ((id, score) in songScores) {
            val artist = entryById[id]?.artist ?: continue
            if (artist.isBlank() || artist.startsWith("Unknown", ignoreCase = true)) continue
            artistScores.merge(artist, score, Double::plus)
        }
        val topArtists = artistScores.entries
            .sortedByDescending { it.value }
            .take(MAX_ARTISTS)
            .map { it.key }

        TasteProfile(
            topArtists = topArtists,
            topSongs = topSongs,
            recentSearches = searchHistoryRepository.getHistory().take(MAX_RECENT_SEARCHES)
        )
    }

    /**
     * Songs to append to the playing queue.
     *
     * Prefers YouTube's actual related-songs radio for the current track;
     * falls back to searches seeded by the user's top artists, then to a
     * generic similarity search.
     */
    suspend fun getQueueContinuation(
        currentSong: Song?,
        excludeIds: Set<String>,
        limit: Int = 10
    ): List<Song> {
        val results = mutableListOf<Song>()

        // 1. Real related-songs radio for the current track
        if (currentSong != null && currentSong.source == SongSource.YOUTUBE) {
            results += youTubeRepository.getRelatedSongs(currentSong.id)
        }

        // 2. Taste-profile fallback: songs by the user's top artists.
        // Shuffled so consecutive loads don't always seed from the same artist.
        if (results.count { it.id !in excludeIds } < limit) {
            val profile = buildTasteProfile()
            for (artist in profile.topArtists.shuffled().take(2)) {
                results += youTubeRepository.search("$artist songs", YouTubeRepository.FILTER_SONGS)
                if (results.count { it.id !in excludeIds } >= limit) break
            }
        }

        // 3. Last resort: similarity search on the current song
        if (results.isEmpty() && currentSong != null) {
            results += youTubeRepository.search(
                "songs like ${currentSong.title} ${currentSong.artist}",
                YouTubeRepository.FILTER_SONGS
            )
        }

        return results.asSequence()
            .filter { it.id !in excludeIds }
            .distinctBy { it.id }
            .take(limit)
            .toList()
    }

    /**
     * Personalized home feed for logged-out users: interleaved results from
     * the user's top artists, their recent searches, and a radio seeded by a
     * favourite song (for discovery beyond exact-artist matches).
     */
    suspend fun getHomeRecommendations(limit: Int = 30): List<Song> = coroutineScope {
        val profile = buildTasteProfile()
        if (profile.isEmpty()) {
            // Nothing local to personalize with yet — fall back to trending.
            return@coroutineScope youTubeRepository.search("trending music", YouTubeRepository.FILTER_SONGS)
        }

        val seedQueries = buildList {
            profile.topArtists.take(3).forEach { add("$it songs") }
            profile.recentSearches.take(2).forEach { add(it) }
        }

        val searchBuckets = seedQueries.map { query ->
            async {
                runCatching { youTubeRepository.search(query, YouTubeRepository.FILTER_SONGS) }
                    .getOrDefault(emptyList())
            }
        }
        val radioBucket = async {
            profile.topSongs.firstOrNull()?.let { seed ->
                runCatching { youTubeRepository.getRelatedSongs(seed.songId) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
        }

        val buckets = (searchBuckets.map { it.await() } + listOf(radioBucket.await()))
            .filter { it.isNotEmpty() }

        val mixed = interleave(buckets).distinctBy { it.id }.take(limit)
        if (mixed.isNotEmpty()) mixed
        else youTubeRepository.search("trending music", YouTubeRepository.FILTER_SONGS)
    }

    /** Round-robin merge so one seed doesn't dominate the top of the feed. */
    private fun interleave(buckets: List<List<Song>>): List<Song> {
        val out = mutableListOf<Song>()
        var i = 0
        var added = true
        while (added) {
            added = false
            for (bucket in buckets) {
                if (i < bucket.size) {
                    out.add(bucket[i])
                    added = true
                }
            }
            i++
        }
        return out
    }
}
