package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide audio effects (equalizer + bass boost) for the music pipeline.
 *
 * A singleton object (same pattern as CacheManager) because the effect
 * instances belong to MusicService's ExoPlayer audio session while the UI that
 * controls them lives in the settings navigation graph, and there is no DI to
 * share an instance. MusicService calls [attach] with its audio session id at
 * startup and [release] on teardown; EqualizerScreen observes the StateFlows
 * and calls the setters. Settings persist in their own SharedPreferences and
 * are re-applied on every attach, so they survive service restarts.
 */
object AudioEffects {

    private const val TAG = "AudioEffects"
    private const val PREFS_NAME = "audio_effects_prefs"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_BAND_LEVELS = "eq_band_levels"
    private const val KEY_PRESET = "eq_preset"
    private const val KEY_BASS_BOOST = "eq_bass_boost"

    /** Sentinel preset index for manually adjusted band levels. */
    const val PRESET_CUSTOM = -1

    /** BassBoost strength is defined by the platform as 0..1000 permille. */
    const val BASS_BOOST_MAX = 1000

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var prefs: SharedPreferences? = null

    private val _isAvailable = MutableStateFlow(false)
    /** True while effects are attached to a live audio session. */
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Short>>(emptyList())
    /** Current gain per band in millibels, size == number of bands. */
    val bandLevels: StateFlow<List<Short>> = _bandLevels.asStateFlow()

    private val _currentPreset = MutableStateFlow(PRESET_CUSTOM)
    /** Active system preset index, or [PRESET_CUSTOM] for manual levels. */
    val currentPreset: StateFlow<Int> = _currentPreset.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    /** Bass boost strength 0..[BASS_BOOST_MAX]; 0 keeps the effect off. */
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    // Static capabilities of the attached equalizer, valid while isAvailable
    var minBandLevel: Short = -1500
        private set
    var maxBandLevel: Short = 1500
        private set
    var centerFreqsHz: List<Int> = emptyList()
        private set
    var presetNames: List<String> = emptyList()
        private set

    /**
     * Create the effects on [audioSessionId] and apply the persisted settings.
     * Safe to call again with a new session id (releases the old instances).
     */
    fun attach(context: Context, audioSessionId: Int) {
        release()
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            // Bass boost isn't supported on every device; the equalizer alone
            // is still worth attaching.
            bassBoost = try {
                BassBoost(0, audioSessionId).takeIf { it.strengthSupported }
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost unavailable", e)
                null
            }

            val bands = eq.numberOfBands.toInt()
            minBandLevel = eq.bandLevelRange[0]
            maxBandLevel = eq.bandLevelRange[1]
            // getCenterFreq returns milliHertz
            centerFreqsHz = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 }
            presetNames = (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }

            applySavedState(eq)
            _isAvailable.value = true
            Log.i(TAG, "Attached to session $audioSessionId: $bands bands, ${presetNames.size} presets")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio effects", e)
            release()
        }
    }

    private fun applySavedState(eq: Equalizer) {
        val p = prefs ?: return
        val enabled = p.getBoolean(KEY_ENABLED, false)
        val preset = p.getInt(KEY_PRESET, PRESET_CUSTOM)
        val savedLevels = p.getString(KEY_BAND_LEVELS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toShortOrNull() }
        val boost = p.getInt(KEY_BASS_BOOST, 0).coerceIn(0, BASS_BOOST_MAX)

        try {
            if (preset in presetNames.indices) {
                eq.usePreset(preset.toShort())
            } else if (savedLevels != null && savedLevels.size == eq.numberOfBands.toInt()) {
                savedLevels.forEachIndexed { band, level ->
                    eq.setBandLevel(band.toShort(), level.coerceIn(minBandLevel, maxBandLevel))
                }
            }
            eq.enabled = enabled
            bassBoost?.let {
                it.setStrength(boost.toShort())
                it.enabled = enabled && boost > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply saved effect state", e)
        }

        _enabled.value = enabled
        _currentPreset.value = if (preset in presetNames.indices) preset else PRESET_CUSTOM
        _bandLevels.value = readBandLevels(eq)
        _bassBoostStrength.value = boost
    }

    private fun readBandLevels(eq: Equalizer): List<Short> =
        (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()) }

    fun setEnabled(value: Boolean) {
        val eq = equalizer ?: return
        try {
            eq.enabled = value
            bassBoost?.enabled = value && _bassBoostStrength.value > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle equalizer", e)
            return
        }
        _enabled.value = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    /** Set one band's gain in millibels; switches the preset to custom. */
    fun setBandLevel(band: Int, level: Short) {
        val eq = equalizer ?: return
        if (band !in 0 until eq.numberOfBands.toInt()) return
        try {
            eq.setBandLevel(band.toShort(), level.coerceIn(minBandLevel, maxBandLevel))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set band $band", e)
            return
        }
        _currentPreset.value = PRESET_CUSTOM
        _bandLevels.value = readBandLevels(eq)
        persistLevels()
    }

    fun applyPreset(preset: Int) {
        val eq = equalizer ?: return
        if (preset !in presetNames.indices) return
        try {
            eq.usePreset(preset.toShort())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply preset $preset", e)
            return
        }
        _currentPreset.value = preset
        _bandLevels.value = readBandLevels(eq)
        persistLevels()
    }

    fun setBassBoostStrength(strength: Int) {
        val boost = strength.coerceIn(0, BASS_BOOST_MAX)
        val bb = bassBoost ?: return
        try {
            bb.setStrength(boost.toShort())
            bb.enabled = _enabled.value && boost > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set bass boost", e)
            return
        }
        _bassBoostStrength.value = boost
        prefs?.edit()?.putInt(KEY_BASS_BOOST, boost)?.apply()
    }

    val isBassBoostSupported: Boolean
        get() = bassBoost != null

    private fun persistLevels() {
        prefs?.edit()
            ?.putInt(KEY_PRESET, _currentPreset.value)
            ?.putString(KEY_BAND_LEVELS, _bandLevels.value.joinToString(","))
            ?.apply()
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio effects", e)
        }
        equalizer = null
        bassBoost = null
        _isAvailable.value = false
    }
}
