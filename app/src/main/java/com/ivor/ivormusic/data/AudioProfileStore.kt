package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Measured [AudioProfile]s, kept so a track is analysed once and not once per
 * play.
 *
 * Persisted for the same reason [TrackLoudnessStore] is: the analysis reads
 * audio out of the playback cache, and the cache evicts, so a profile held only
 * in memory would be lost exactly when the audio behind it was too. Written
 * once, read forever, and cheap enough at a few dozen bytes a track that the
 * bound below is a runaway guard rather than a policy.
 *
 * Rows carry [AudioProfile.version] and are dropped when it moves, because a
 * change to how the envelope is measured makes old numbers wrong rather than
 * merely stale.
 */
class AudioProfileStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        // The schema version is a default-valued property. Without defaults in
        // the payload, an old row that predates `version` is decoded using the
        // *current* default and silently passes invalidation.
        encodeDefaults = true
    }
    private val mutex = Mutex()

    @Volatile
    private var cache: MutableMap<String, AudioProfile>? = null

    private suspend fun loaded(): MutableMap<String, AudioProfile> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: withContext(Dispatchers.IO) {
                val map = runCatching {
                    if (!file.exists()) return@runCatching mutableMapOf<String, AudioProfile>()
                    json.parseToJsonElement(file.readText()).jsonArray
                        .mapNotNull { element ->
                            // Missing is not current. Older payloads omitted
                            // this default-valued field, and decoding first
                            // would manufacture CURRENT_VERSION for them.
                            val storedVersion = element.jsonObject["version"]
                                ?.jsonPrimitive?.intOrNull
                            if (storedVersion != AudioProfile.CURRENT_VERSION) null
                            else json.decodeFromJsonElement<AudioProfile>(element)
                        }
                        .associateBy { it.songId }
                        .toMutableMap()
                }.getOrElse {
                    KLog.w(TAG, "Unreadable profile store, starting over", it)
                    mutableMapOf()
                }
                cache = map
                map
            }
        }
    }

    suspend fun get(songId: String): AudioProfile? = loaded()[songId]

    /** Non-suspending peek for the playback hot path; null until warmed. */
    fun peek(songId: String): AudioProfile? = cache?.get(songId)

    suspend fun put(profile: AudioProfile) {
        val map = loaded()
        mutex.withLock {
            map[profile.songId] = profile
            if (map.size > MAX_ENTRIES) {
                // No access ordering to prune by, and the value of any one row
                // is small, so clear rather than pretend to a policy. Costs a
                // re-analysis of tracks played again after the wipe.
                val keep = map.entries.take(MAX_ENTRIES / 2).associate { it.key to it.value }
                map.clear()
                map.putAll(keep)
            }
            val snapshot = map.values.toList()
            withContext(Dispatchers.IO) {
                runCatching {
                    file.writeText(
                        json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(AudioProfile.serializer()),
                            snapshot
                        )
                    )
                }
                    .onFailure { KLog.w(TAG, "Could not persist profiles", it) }
            }
        }
    }

    /** Warm the in-memory map so [peek] can answer during playback. */
    suspend fun warm() {
        loaded()
    }

    private companion object {
        const val TAG = "AudioProfileStore"
        const val FILE_NAME = "audio_profiles.json"
        const val MAX_ENTRIES = 2000
    }
}
