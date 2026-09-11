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

    /** Enough of a song measured to draw it as a whole one. See [readySnapshot]. */
    private const val READY_COVERAGE = 0.9f

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

    /**
     * Fold one measured peak into a song's envelope. Cheap enough for a 100ms sampler.
     *
     * A song [WaveformAnalyzer] has already measured end to end is left alone. The two paths
     * measure different signals - the file's own PCM against what the sink was handed after
     * gain and normalisation - so letting a listening sample raise a bucket of an analysed
     * envelope would slowly pull one song's shape out of shape across replays, and there is
     * nothing it could add to an envelope that is already whole.
     */
    fun record(context: Context, songId: String, fraction: Float, peak: Float) {
        if (songId.isBlank()) return
        val envelope = envelope(context, songId)
        if (envelope.isComplete) return
        if (!envelope.record(fraction, peak)) return
        synchronized(lock) { dirty += songId }
        _version.value++
    }

    /** Whether this song has been measured end to end, so nothing needs to measure it again. */
    fun isComplete(context: Context, songId: String): Boolean =
        songId.isNotBlank() && envelope(context, songId).isComplete

    /**
     * A frozen copy of [songId]'s envelope once it covers enough of the song to draw as a whole
     * one, or null while it does not.
     *
     * The threshold is not 100%: [WaveformEnvelope.bars] interpolates gaps, so a song the live
     * sampler measured across a couple of pauses reads as one continuous waveform well before
     * every bucket has been visited, and holding out for the last few would leave a plain bar
     * on a song whose shape is plainly known. An analysed envelope is complete and passes on
     * its first ask.
     */
    fun readySnapshot(context: Context, songId: String): WaveformEnvelope? =
        envelope(context, songId).takeIf { it.coverage >= READY_COVERAGE }?.snapshot()

    /**
     * The same, from memory alone.
     *
     * The seek bar asks this first, during composition, because reaching disk there would be a
     * blocking read on the frame that opens the player - and because a song whose envelope is
     * only found one frame later makes the whole progress row shrink and grow again as the
     * waveform layout arrives late. [MusicService][com.ivor.ivormusic.service.MusicService]
     * loads the playing song's envelope when it starts sampling it, so by the time anybody can
     * see the bar the answer is normally already here.
     */
    fun cachedReadySnapshot(songId: String): WaveformEnvelope? =
        synchronized(lock) { cache[songId] }
            ?.takeIf { it.coverage >= READY_COVERAGE }
            ?.snapshot()

    /**
     * Replace a song's envelope with one measured in a single pass, and persist it now.
     *
     * Written through rather than left to [flushIfDue] because this is the expensive
     * measurement: it cost a decode of the whole track, and losing it to a process death
     * minutes later means paying for it again.
     */
    fun publish(context: Context, songId: String, envelope: WaveformEnvelope) {
        if (songId.isBlank()) return
        synchronized(lock) {
            cache[songId] = envelope
            dirty += songId
        }
        flushIfDue(context, force = true)
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
