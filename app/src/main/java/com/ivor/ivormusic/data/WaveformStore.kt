package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Measured waveforms, keyed by song id.
 *
 * Process-wide, and deliberately not an eleventh entry on invariant 6's list, for the
 * reason [com.ivor.ivormusic.service.VisualizerBus] is not one: it holds no user data and
 * nothing a person chose. It is a derived cache of what songs sound like, rebuilt by
 * playing them, so losing it costs one listen rather than anything a backup should carry.
 * It still has to be shared, because [com.ivor.ivormusic.service.MusicService] writes it
 * from its own process-level holder while the player UI reads it from another.
 *
 * Its own preference file rather than a row in `ivor_music_theme_prefs`: a few hundred
 * envelopes would otherwise ride along in every backup that copies that file key by key,
 * which is exactly the weight a cache should not add. That also means it is intentionally
 * absent from [BackupRepository]'s allowlists - do not add it.
 *
 * Keyed by song id and not by queue occurrence: the same song in two playlists is the same
 * audio, and its shape should be there the second time it is reached.
 */
object WaveformStore {

    private const val PREFS_NAME = "ivor_music_waveforms"

    /** A bounded LRU: each envelope is a quarter of a kilobyte, so this is well under 150 KB. */
    private const val MAX_SONGS = 400

    /** Persisting on every sample would write several times a second for no benefit. */
    private const val FLUSH_INTERVAL_MS = 15_000L

    private val lock = Any()
    private var prefs: SharedPreferences? = null

    private val cache = object : LinkedHashMap<String, WaveformEnvelope>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WaveformEnvelope>?): Boolean {
            if (size <= MAX_SONGS) return false
            eldest?.key?.let { evicted += it }
            return true
        }
    }
    private val evicted = mutableSetOf<String>()
    private val dirty = mutableSetOf<String>()
    private var lastFlushMs = 0L

    private val _version = MutableStateFlow(0)

    /** Bumped whenever a stored envelope actually grew, so the bar can redraw. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun prefs(context: Context): SharedPreferences = synchronized(lock) {
        prefs ?: context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { prefs = it }
    }

    /** The stored envelope for [songId], loading it from disk on first ask. Never null once asked. */
    fun envelope(context: Context, songId: String): WaveformEnvelope {
        synchronized(lock) { cache[songId] }?.let { return it }
        val stored = WaveformEnvelope.decodeFromString(prefs(context).getString(songId, null))
        val envelope = stored ?: WaveformEnvelope.empty()
        return synchronized(lock) { cache.getOrPut(songId) { envelope } }
    }

    /** Fold one measured peak into a song's envelope. Cheap enough for a 200ms sampler. */
    fun record(context: Context, songId: String, fraction: Float, peak: Float) {
        if (songId.isBlank()) return
        if (!envelope(context, songId).record(fraction, peak)) return
        synchronized(lock) { dirty += songId }
        _version.value++
    }

    /** Write out at most every [FLUSH_INTERVAL_MS]; safe to call from the sampler each tick. */
    fun flushIfDue(context: Context, nowMs: Long = SystemClock.uptimeMillis(), force: Boolean = false) {
        synchronized(lock) {
            if (dirty.isEmpty() && evicted.isEmpty()) return
            if (!force && nowMs - lastFlushMs < FLUSH_INTERVAL_MS) return
            lastFlushMs = nowMs
        }
        try {
            val editor = prefs(context).edit()
            synchronized(lock) {
                evicted.forEach { editor.remove(it) }
                evicted.clear()
                dirty.forEach { id -> cache[id]?.let { editor.putString(id, it.encodeToString()) } }
                dirty.clear()
            }
            editor.apply()
        } catch (e: RuntimeException) {
            // A waveform is decoration; failing to store one must never disturb playback.
            KLog.w(TAG, "Could not persist waveforms", e)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            cache.clear()
            dirty.clear()
            evicted.clear()
        }
        try {
            prefs(context).edit().clear().apply()
        } catch (e: RuntimeException) {
            KLog.w(TAG, "Could not clear waveforms", e)
        }
        _version.value++
    }

    private const val TAG = "Waveform"
}
