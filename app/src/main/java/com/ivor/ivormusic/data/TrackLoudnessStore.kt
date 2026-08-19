package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.pow

/**
 * YouTube's own loudness measurement per track, and the playback gain derived
 * from it.
 *
 * **This is metadata, not analysis.** Probed August 2026: every `/player`
 * response carries `playerConfig.audioConfig` with `trackAbsoluteLoudnessLkfs`
 * (the track's integrated loudness), `loudnessTargetLkfs` (-14, YouTube's
 * target) and `loudnessDb`, which is exactly `measured - target` in every
 * sample taken. So the correction is a gain of `-loudnessDb` dB and costs no
 * decoding, no analysis and no extra request - it arrives with the stream
 * resolution the app already performs. The measured spread across eight
 * ordinary tracks was 9.15 LU, which is the volume jump between songs that
 * people notice, and the reason a crossfade cannot ship without this.
 *
 * **It has to persist, and that is the whole reason this is a store rather
 * than a field.** A song that is fully cached never calls `/player` again -
 * `MusicService.getOrStartResolution` short-circuits to a `cached://` URI - so
 * a value held only in memory would be present the first time a song played
 * and absent every time after, which is the worst possible pattern for a
 * volume correction. Written once at resolution time, read forever.
 *
 * Deliberately not one of the process-wide `MutableStateFlow` stores: nothing
 * observes a loudness value, it is read once when a track starts. A plain
 * preference file with an in-memory mirror is the smaller thing that fits.
 */
object TrackLoudnessStore {

    private const val PREFS_NAME = "ivor_track_loudness"

    /**
     * Beyond this the file is cleared rather than pruned. Entries are eight
     * bytes of payload behind a video id, so the ceiling is generous and only
     * exists so a heavy listener's preference file cannot grow without bound.
     * Clearing costs one re-resolve per song, and only for songs played again
     * after the wipe.
     */
    private const val MAX_ENTRIES = 4000

    private val memory = HashMap<String, Float>()

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences =
        prefs ?: synchronized(this) {
            prefs ?: context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .also { prefs = it }
        }

    /**
     * Record the `loudnessDb` a `/player` response reported for [videoId].
     *
     * Zero is a legitimate measurement (a track already mastered exactly at
     * target), so it is stored rather than treated as "unknown".
     */
    fun put(context: Context, videoId: String, loudnessDb: Float) {
        if (videoId.isBlank()) return
        synchronized(memory) {
            if (memory[videoId] == loudnessDb) return
            memory[videoId] = loudnessDb
        }
        val store = prefs(context)
        if (store.all.size >= MAX_ENTRIES) {
            store.edit().clear().apply()
            synchronized(memory) {
                memory.clear()
                memory[videoId] = loudnessDb
            }
        }
        store.edit().putFloat(videoId, loudnessDb).apply()
    }

    /** The stored `loudnessDb`, or null for a track never resolved here. */
    fun loudnessDb(context: Context, videoId: String): Float? {
        synchronized(memory) { memory[videoId] }?.let { return it }
        val store = prefs(context)
        if (!store.contains(videoId)) return null
        val value = store.getFloat(videoId, 0f)
        synchronized(memory) { memory[videoId] = value }
        return value
    }

    /**
     * The linear volume scalar for [videoId], or 1.0 when nothing is known.
     *
     * `ExoPlayer.volume` is a linear amplitude scalar, so a dB correction is
     * `10^(dB/20)`.
     *
     * **Attenuate only.** A negative `loudnessDb` means the track is quieter
     * than target and the correction would be a boost, but the stream is
     * already scaled to 1.0 and multiplying past that clips. Quiet masters
     * therefore stay quiet, which is the conservative half of the trade: the
     * loud ones coming down is what removes the jump, and doing it without a
     * limiter is not something to smuggle into a volume field.
     */
    fun gainFor(context: Context, videoId: String): Float {
        val db = loudnessDb(context, videoId) ?: return 1f
        if (db <= 0f) return 1f
        return 10.0.pow(-db / 20.0).toFloat().coerceIn(MIN_GAIN, 1f)
    }

    /**
     * Floor on the attenuation, about -20 dB. A corrupt or absurd measurement
     * should not be able to mute playback outright - silence reads as a broken
     * app, where a track that is merely too loud reads as a bad master.
     */
    private const val MIN_GAIN = 0.1f
}
