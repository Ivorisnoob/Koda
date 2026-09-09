package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the expanded-player music visualizer draws itself.
 *
 * The constants are persisted by [storageId], never the ordinal: renaming a
 * constant resets every existing user's choice, the same freeze that applies
 * to [PlayerStyle].
 */
enum class VisualizerStyle(val storageId: String) {
    BARS("bars"),
    WAVE("wave"),
    DOTS("dots");

    companion object {
        fun fromStorageId(value: String?): VisualizerStyle =
            entries.firstOrNull { it.storageId == value } ?: BARS
    }
}

/**
 * The visualizer's settings.
 *
 * There is no "read the real audio" switch, because there is nothing to opt
 * into: the levels come from Koda's own decoded PCM through
 * [com.ivor.ivormusic.service.VisualizerBus], which costs no permission. The
 * earlier design tapped `android.media.audiofx.Visualizer`, an audio *capture*
 * API the platform gates on RECORD_AUDIO whatever session it is aimed at, and
 * had to fall back to a simulated dance whenever consent was missing.
 *
 * A separate store from [ThemePreferences] on purpose: the player reads these
 * on every frame while the settings screen writes through its own instance,
 * so cross-instance propagation goes through the shared-preferences listener
 * below rather than a StateFlow that never crosses. The keys live in the same
 * `ivor_music_theme_prefs` file, which is what [BackupRepository] already
 * copies key by key - a new file would have needed an allowlist entry and the
 * setting would silently miss every backup until someone remembered it.
 */
class VisualizerPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /**
     * Whether the strip is drawn at all. Off by default: it adds 88dp under
     * the progress row of every expanded style, and a decoration that changes
     * the shape of eight players is something someone opts into rather than
     * something they find already there.
     */
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _style = MutableStateFlow(
        VisualizerStyle.fromStorageId(prefs.getString(KEY_STYLE, null))
    )
    val style: StateFlow<VisualizerStyle> = _style.asStateFlow()

    private val _barCount = MutableStateFlow(prefs.getInt(KEY_BAR_COUNT, DEFAULT_BAR_COUNT))
    val barCount: StateFlow<Int> = _barCount.asStateFlow()

    private val _sensitivity = MutableStateFlow(prefs.getFloat(KEY_SENSITIVITY, 1f))
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    // SharedPreferences only holds listeners weakly; a local would be
    // collected and propagation across instances would silently stop.
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_ENABLED -> _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
            KEY_STYLE -> _style.value =
                VisualizerStyle.fromStorageId(prefs.getString(KEY_STYLE, null))
            KEY_BAR_COUNT -> _barCount.value = prefs.getInt(KEY_BAR_COUNT, DEFAULT_BAR_COUNT)
            KEY_SENSITIVITY -> _sensitivity.value = prefs.getFloat(KEY_SENSITIVITY, 1f)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    fun setStyle(style: VisualizerStyle) {
        prefs.edit().putString(KEY_STYLE, style.storageId).apply()
        _style.value = style
    }

    fun setBarCount(count: Int) {
        prefs.edit().putInt(KEY_BAR_COUNT, count.coerceIn(BAR_COUNT_OPTIONS.min(), BAR_COUNT_OPTIONS.max())).apply()
        _barCount.value = prefs.getInt(KEY_BAR_COUNT, DEFAULT_BAR_COUNT)
    }

    fun setSensitivity(value: Float) {
        prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)).apply()
        _sensitivity.value = prefs.getFloat(KEY_SENSITIVITY, 1f)
    }

    companion object {
        private const val PREFS_NAME = "ivor_music_theme_prefs"
        private const val KEY_ENABLED = "visualizer_enabled"
        private const val KEY_STYLE = "visualizer_style"
        private const val KEY_BAR_COUNT = "visualizer_bars"
        private const val KEY_SENSITIVITY = "visualizer_sensitivity"

        const val DEFAULT_BAR_COUNT = 32
        val BAR_COUNT_OPTIONS = listOf(24, 32, 48)

        const val MIN_SENSITIVITY = 0.5f
        const val MAX_SENSITIVITY = 2f
    }
}
