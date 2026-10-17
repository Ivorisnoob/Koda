package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Playback progress for podcast episodes, keyed by [PodcastEpisode.id].
 *
 * Lets episode rows show a progress bar and a played indicator, and lets
 * playback resume where the user left off instead of restarting from the
 * beginning. State is process-wide (companion flows, like
 * [PodcastRepository]) so the player service, the player ViewModel, and the
 * podcast UI all see the same progress without re-reading disk.
 *
 * Persistence is a JSON map in filesDir; the in-memory flow is updated
 * synchronously and the file write happens on IO, so UI stays reactive.
 */
@Serializable
data class EpisodeProgress(
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)

class EpisodeProgressRepository(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "EpisodeProgress"
        private const val FILE_NAME = "episode_progress.json"

        /**
         * Prefix of the player-facing [Song.id] for podcast episodes; see
         * [PodcastRepository.episodeToSong]. Stripped to recover the
         * [PodcastEpisode.id] this progress is keyed by.
         */
        const val PODCAST_SONG_ID_PREFIX = "podcast:"

        /**
         * Remaining time under which an episode counts as played. Short
         * enough that credits/outro don't matter, long enough that an
         * accidental skip to the end doesn't mark it.
         */
        const val COMPLETED_REMAINING_MS = 60_000L

        /** Don't offer resume for positions this short; it's just the intro. */
        const val RESUME_THRESHOLD_MS = 10_000L

        private const val MAX_ENTRIES = 1_000

        private val json = Json { ignoreUnknownKeys = true }

        // Process-wide state (see class doc).
        private val _progress = MutableStateFlow<Map<String, EpisodeProgress>>(emptyMap())
        private var loaded = false
        private val loadMutex = Mutex()
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val persistMutex = Mutex()
    }

    val progress: StateFlow<Map<String, EpisodeProgress>> = _progress.asStateFlow()

    init {
        ioScope.launch {
            loadMutex.withLock {
                if (loaded) return@withLock
                try {
                    val file = storeFile()
                    if (file.exists()) {
                        val stored: Map<String, EpisodeProgress> =
                            json.decodeFromString(file.readText())
                        _progress.value = stored
                    }
                } catch (e: Exception) {
                    KLog.w(TAG, "Failed to load episode progress", e)
                } finally {
                    loaded = true
                }
            }
        }
    }

    fun getProgress(episodeId: String): EpisodeProgress? = _progress.value[episodeId]

    /**
     * Record playback progress. Marks the episode completed when less than
     * [COMPLETED_REMAINING_MS] remains. Re-listening an episode (a fresh,
     * non-completed position) naturally clears a stale completed flag.
     */
    fun saveProgress(episodeId: String, positionMs: Long, durationMs: Long) {
        if (episodeId.isBlank() || durationMs <= 0) return
        val pos = positionMs.coerceAtLeast(0L)
        val completed = pos >= durationMs - COMPLETED_REMAINING_MS
        val entry = EpisodeProgress(
            episodeId = episodeId,
            positionMs = pos,
            durationMs = durationMs,
            completed = completed,
            updatedAt = System.currentTimeMillis(),
        )
        var next = _progress.value + (episodeId to entry)
        if (next.size > MAX_ENTRIES) {
            next = next.entries
                .sortedBy { it.value.updatedAt }
                .takeLast(MAX_ENTRIES)
                .associate { it.key to it.value }
        }
        _progress.value = next
        ioScope.launch {
            persistMutex.withLock {
                try {
                    storeFile().writeText(json.encodeToString(_progress.value))
                } catch (e: Exception) {
                    KLog.w(TAG, "Failed to persist episode progress", e)
                }
            }
        }
    }

    private fun storeFile(): File = File(appContext.filesDir, FILE_NAME)
}
