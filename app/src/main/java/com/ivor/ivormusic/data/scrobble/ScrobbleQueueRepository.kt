package com.ivor.ivormusic.data.scrobble

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Thread-safe offline queue for scrobbles that failed due to lack of network or server errors.
 *
 * Persisted in `filesDir/scrobble_queue.json` using atomic file replacement.
 * Bounded to [MAX_QUEUE_ENTRIES] to prevent unbounded disk growth.
 */
class ScrobbleQueueRepository(context: Context) {

    private val queueFile = File(context.filesDir, FILE_NAME)
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    companion object {
        private const val TAG = "ScrobbleQueueRepo"
        private const val FILE_NAME = "scrobble_queue.json"
        const val MAX_QUEUE_ENTRIES = 500
    }

    /**
     * Enqueue a track that needs to be submitted to one or both services.
     */
    suspend fun enqueue(
        track: ScrobbleTrack,
        pendingLastFm: Boolean,
        pendingListenBrainz: Boolean
    ) = withContext(Dispatchers.IO) {
        if (!pendingLastFm && !pendingListenBrainz) return@withContext

        synchronized(lock) {
            val list = loadQueueInternal().toMutableList()

            // Deduplicate if already queued for this track and timestamp
            val existingIndex = list.indexOfFirst {
                it.track.mediaId == track.mediaId && it.track.timestampSeconds == track.timestampSeconds
            }

            if (existingIndex != -1) {
                val existing = list[existingIndex]
                list[existingIndex] = existing.copy(
                    pendingLastFm = existing.pendingLastFm || pendingLastFm,
                    pendingListenBrainz = existing.pendingListenBrainz || pendingListenBrainz
                )
            } else {
                val item = QueuedScrobble(
                    id = UUID.randomUUID().toString(),
                    track = track,
                    pendingLastFm = pendingLastFm,
                    pendingListenBrainz = pendingListenBrainz
                )
                list.add(item)
            }

            // Cap to maximum entries (drop oldest)
            val trimmed = if (list.size > MAX_QUEUE_ENTRIES) {
                list.takeLast(MAX_QUEUE_ENTRIES)
            } else {
                list
            }

            saveQueueInternal(trimmed)
        }
    }

    /**
     * Retrieve the count of pending queued scrobbles.
     */
    suspend fun getPendingCount(): Int = withContext(Dispatchers.IO) {
        synchronized(lock) {
            loadQueueInternal().size
        }
    }

    /**
     * Peek up to [limit] items without removing them.
     */
    suspend fun peekBatch(limit: Int = 50): List<QueuedScrobble> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            loadQueueInternal().take(limit)
        }
    }

    /**
     * Update or remove items after a submission attempt.
     */
    suspend fun completeItems(
        completedLastFmIds: Set<String>,
        completedListenBrainzIds: Set<String>,
        unrecoverableIds: Set<String> = emptySet()
    ) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = loadQueueInternal()
            val updated = mutableListOf<QueuedScrobble>()

            for (item in current) {
                if (unrecoverableIds.contains(item.id)) {
                    continue // Drop permanently failing item
                }

                val stillPendingLastFm = item.pendingLastFm && !completedLastFmIds.contains(item.id)
                val stillPendingListenBrainz = item.pendingListenBrainz && !completedListenBrainzIds.contains(item.id)

                if (stillPendingLastFm || stillPendingListenBrainz) {
                    updated.add(
                        item.copy(
                            pendingLastFm = stillPendingLastFm,
                            pendingListenBrainz = stillPendingListenBrainz,
                            attempts = item.attempts + 1
                        )
                    )
                }
            }

            saveQueueInternal(updated)
        }
    }

    /**
     * Clear all queued items.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (queueFile.exists()) {
                queueFile.delete()
            }
        }
    }

    private fun loadQueueInternal(): List<QueuedScrobble> {
        if (!queueFile.exists()) return emptyList()
        return try {
            val content = queueFile.readText(Charsets.UTF_8)
            if (content.isBlank()) emptyList() else json.decodeFromString(content)
        } catch (e: Exception) {
            KLog.e(TAG, "Error reading scrobble queue file, resetting", e)
            emptyList()
        }
    }

    private fun saveQueueInternal(items: List<QueuedScrobble>) {
        try {
            val content = json.encodeToString(items)
            val tempFile = File(queueFile.parentFile, "${queueFile.name}.tmp")
            tempFile.writeText(content, Charsets.UTF_8)
            if (!tempFile.renameTo(queueFile)) {
                tempFile.copyTo(queueFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            KLog.e(TAG, "Error saving scrobble queue file", e)
        }
    }
}
