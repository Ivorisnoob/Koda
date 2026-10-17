package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background DJ cache manager.
 *
 * Workflow:
 *  1. On app launch (or idle), pre-compute the mix and synthesize the first
 *     voice intros, caching them to disk.
 *  2. On FAB press, play immediately from cache — no generation delay.
 *  3. During playback, keep synthesizing upcoming intros ahead of the queue.
 */
class DjCacheManager(context: Context) {

    private val appContext = context.applicationContext
    private val aiDjRepository = AiDjRepository(appContext)
    private val voiceRepository = DjVoiceRepository(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var precomputeJob: Job? = null
    private var aheadJob: Job? = null

    /** True once the initial mix + first intros are cached and ready. */
    @Volatile
    var isReady: Boolean = false
        private set

    private val positionPrefs = appContext.getSharedPreferences("dj_position", android.content.Context.MODE_PRIVATE)

    /**
     * Save the last played queue index for a mix. Called when the DJ mix
     * stops being the active queue.
     */
    fun saveDjPosition(mix: AiDjMix, queueIndex: Int) {
        val mixId = mixKeyFor(mix)
        positionPrefs.edit().putInt("pos_$mixId", queueIndex).apply()
    }

    /**
     * Get the saved queue index for a mix, or null if none.
     */
    fun getSavedDjPosition(mix: AiDjMix): Int? {
        val mixId = mixKeyFor(mix)
        val pos = positionPrefs.getInt("pos_$mixId", -1)
        return if (pos >= 0) pos else null
    }

    /**
     * Find the nearest DJ intro at or before the given queue index.
     * Returns the intro index, or 0 if none found.
     */
    fun findResumeIntroIndex(queue: List<Song>, fromIndex: Int): Int {
        val prefix = com.ivor.ivormusic.data.StatsRepository.DJ_INTRO_ID_PREFIX
        // Walk backwards from fromIndex to find the nearest intro
        for (i in fromIndex downTo 0) {
            if (i < queue.size && queue[i].id.startsWith(prefix)) {
                return i
            }
        }
        return 0
    }

    private fun mixKeyFor(mix: AiDjMix): String {
        // Use the same key as voice file caching for consistency
        return mix.title.hashCode().toString(36) + "_" + mix.tracks.size
    }

    /**
     * Start background pre-computation. Call on app launch.
     * Generates the mix (if stale/missing) and synthesizes the first
     * few voice intros so the FAB can play instantly.
     */
    fun startPrecompute(
        getRecent: suspend () -> List<Song>,
        getLiked: suspend () -> List<Song>,
    ) {
        if (precomputeJob?.isActive == true) return
        precomputeJob = scope.launch {
            try {
                // If we have a fresh cached mix, just ensure intros are ready.
                var mix = aiDjRepository.cachedMix.value
                if (mix == null || aiDjRepository.isCacheStale()) {
                    val recent = getRecent()
                    val liked = getLiked()
                    aiDjRepository.generateInstantMix(recent, liked).fold(
                        onSuccess = { m ->
                            aiDjRepository.cacheMix(m)
                            mix = m
                        },
                        onFailure = { return@launch },
                    )
                }
                val m = mix ?: return@launch
                // Pre-synthesize the first 3 intros (or fewer if the mix is short).
                // buildQueueWithIntros caches each file; we just need the first few.
                preSynthesizeIntros(m, count = 3)
                isReady = true
            } catch (_: Exception) {
                // Background precompute must never crash the app.
            }
        }
    }

    /**
     * Synthesize the first [count] voice intros for the mix, caching to disk.
     * This is what makes the FAB instant — the audio is already there.
     */
    private suspend fun preSynthesizeIntros(mix: AiDjMix, count: Int) {
        withContext(Dispatchers.IO) {
            val voiceDir = File(appContext.cacheDir, "dj_voice").apply { mkdirs() }
            val voiceKey = voiceRepository.currentVoiceKey()
            val mixKey = mix.title.hashCode().toString(36) + mix.tracks.size + voiceKey
            var done = 0
            for ((index, track) in mix.tracks.withIndex()) {
                if (done >= count) break
                val line = track.djLine?.takeIf { it.isNotBlank() } ?: continue
                val wavFile = File(voiceDir, "intro_${mixKey}_$index.wav")
                val mp3File = File(voiceDir, "intro_${mixKey}_$index.mp3")
                if ((wavFile.exists() && wavFile.length() > 0) ||
                    (mp3File.exists() && mp3File.length() > 0)
                ) {
                    done++
                    continue
                }
                // Synthesize and cache; failures are fine — playback falls back.
                try {
                    voiceRepository.synthesizeIntro(line, wavFile, mp3File)
                    done++
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * During playback, keep synthesizing upcoming intros ahead of the queue.
     * Call when playback starts; call [stopAheadCaching] when it stops.
     */
    fun startAheadCaching(mix: AiDjMix, startIndex: Int = 0) {
        aheadJob?.cancel()
        aheadJob = scope.launch {
            try {
                // Small delay so playback starts first.
                delay(2000)
                val voiceDir = File(appContext.cacheDir, "dj_voice").apply { mkdirs() }
                val voiceKey = voiceRepository.currentVoiceKey()
                val mixKey = mix.title.hashCode().toString(36) + mix.tracks.size + voiceKey
                for (index in startIndex until mix.tracks.size) {
                    val track = mix.tracks[index]
                    val line = track.djLine?.takeIf { it.isNotBlank() } ?: continue
                    val wavFile = File(voiceDir, "intro_${mixKey}_$index.wav")
                    val mp3File = File(voiceDir, "intro_${mixKey}_$index.mp3")
                    if ((wavFile.exists() && wavFile.length() > 0) ||
                        (mp3File.exists() && mp3File.length() > 0)
                    ) {
                        continue
                    }
                    try {
                        voiceRepository.synthesizeIntro(line, wavFile, mp3File)
                    } catch (_: Exception) {
                    }
                    // Yield between syntheses so we don't hog the CPU.
                    delay(500)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stopAheadCaching() {
        aheadJob?.cancel()
        aheadJob = null
    }

    /** Get the cached mix, or null if none. */
    fun getCachedMix(): AiDjMix? = aiDjRepository.cachedMix.value

    /** Build the full playback queue from cache (fast — files already exist). */
    suspend fun buildQueueFromCache(mix: AiDjMix): List<Song> =
        voiceRepository.buildQueueWithIntros(mix)

    fun shutdown() {
        scope.cancel()
    }
}
