package com.ivor.ivormusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What one run through a track scored.
 *
 * Kept as a whole rather than as a bare number so the result card can say
 * *why* a run was good, and so a later best can be compared on accuracy when
 * the scores tie.
 */
@Serializable
data class TileRunResult(
    val songId: String,
    val difficulty: String,
    val score: Int = 0,
    val maxCombo: Int = 0,
    val perfect: Int = 0,
    val great: Int = 0,
    val good: Int = 0,
    val missed: Int = 0,
    /** Tiles the run actually judged. Seeking past tiles does not count them. */
    val judged: Int = 0,
    val updatedAt: Long = 0L
) {
    /**
     * Weighted accuracy, zero to one.
     *
     * A near-miss is worth something and a miss is worth nothing, which is what
     * separates a sloppy clear from a clean one; a flat hit rate cannot.
     */
    val accuracy: Float
        get() = if (judged <= 0) 0f else {
            ((perfect * 1f) + (great * 0.7f) + (good * 0.35f)) / judged
        }

    /** Full-combo: every tile judged, none missed, and at least one tile judged. */
    val isFullCombo: Boolean get() = judged > 0 && missed == 0

    val grade: String
        get() = when {
            judged <= 0 -> "-"
            accuracy >= 0.95f && isFullCombo -> "S"
            accuracy >= 0.9f -> "A"
            accuracy >= 0.78f -> "B"
            accuracy >= 0.6f -> "C"
            else -> "D"
        }

    /** Score first, accuracy as the tiebreak. */
    fun beats(other: TileRunResult?): Boolean {
        if (other == null) return true
        if (score != other.score) return score > other.score
        return accuracy > other.accuracy
    }
}

/**
 * Best run per track and difficulty.
 *
 * Device-wide rather than per profile, alongside stats and downloads: a score
 * belongs to the person holding the phone, not to whichever YouTube account is
 * signed in at the time.
 *
 * One preference entry per track so a write touches one key instead of
 * rewriting every score ever set, which matters because this is written on
 * every track change.
 */
class TileScoreStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun keyOf(songId: String, difficulty: TileDifficulty) = "$songId|${difficulty.key}"

    suspend fun best(songId: String, difficulty: TileDifficulty): TileRunResult? =
        withContext(Dispatchers.IO) {
            val raw = prefs.getString(keyOf(songId, difficulty), null) ?: return@withContext null
            runCatching { json.decodeFromString<TileRunResult>(raw) }.getOrNull()
        }

    /**
     * Record [result] when it beats what is stored.
     *
     * @return the run's own result when it became the new best, null otherwise,
     *   so the caller can say so without a second read.
     */
    suspend fun submit(result: TileRunResult): TileRunResult? = withContext(Dispatchers.IO) {
        if (result.judged <= 0) return@withContext null
        val difficulty = TileDifficulty.from(result.difficulty)
        val key = keyOf(result.songId, difficulty)
        val existing = prefs.getString(key, null)
            ?.let { raw -> runCatching { json.decodeFromString<TileRunResult>(raw) }.getOrNull() }
        if (!result.beats(existing)) return@withContext null

        runCatching {
            prune()
            prefs.edit()
                .putString(key, json.encodeToString(TileRunResult.serializer(), result))
                .apply()
        }.onFailure { Log.w(TAG, "Could not persist tile score", it) }
        result
    }

    /**
     * Drop the older half once the file grows past the cap.
     *
     * Scores are tiny, so this is a runaway guard rather than a policy - the
     * same trade [AudioProfileStore] makes, and for the same reason: there is
     * no access ordering to prune by, only the time each was set.
     */
    private fun prune() {
        val all = prefs.all
        if (all.size <= MAX_ENTRIES) return
        val ordered = all.entries
            .mapNotNull { entry ->
                val raw = entry.value as? String ?: return@mapNotNull null
                val stamp = runCatching {
                    json.decodeFromString<TileRunResult>(raw).updatedAt
                }.getOrDefault(0L)
                entry.key to stamp
            }
            .sortedBy { it.second }
        val editor = prefs.edit()
        ordered.take(ordered.size / 2).forEach { editor.remove(it.first) }
        editor.apply()
    }

    private companion object {
        const val TAG = "TileScoreStore"
        const val PREFS_NAME = "ivor_tile_scores"
        const val MAX_ENTRIES = 800
    }
}
