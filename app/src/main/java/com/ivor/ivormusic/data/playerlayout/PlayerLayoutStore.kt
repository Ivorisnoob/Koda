package com.ivor.ivormusic.data.playerlayout

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The custom player layouts on this device, and which one the Custom style
 * draws.
 *
 * Device-wide, like the rest of the player's look, and in the backup (its file
 * is on `BackupRepository`'s list - rename it there if it is ever renamed
 * here). Not process-wide state: every instance reads the file and follows it
 * through the preference listener, the way `ThemePreferences` does, so the
 * editor's save reaches the player that holds its own instance.
 */
class PlayerLayoutStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _layouts = MutableStateFlow(readLayouts())
    val layouts: StateFlow<List<PlayerLayout>> = _layouts.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    /** The layout the Custom style draws; null until one is chosen. */
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    // Held here: SharedPreferences keeps its listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_LAYOUTS -> _layouts.value = readLayouts()
            KEY_ACTIVE -> _activeId.value = prefs.getString(KEY_ACTIVE, null)
            // A cleared file (a restore) reports no key.
            null -> {
                _layouts.value = readLayouts()
                _activeId.value = prefs.getString(KEY_ACTIVE, null)
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readLayouts(): List<PlayerLayout> =
        PlayerLayoutCodec.decodeAll(prefs.getString(KEY_LAYOUTS, null))

    /** Adds the layout, or replaces the one with its id where it stands in the list. */
    fun save(layout: PlayerLayout) {
        val current = _layouts.value
        val index = current.indexOfFirst { it.id == layout.id }
        val updated = if (index >= 0) {
            current.toMutableList().also { it[index] = layout }
        } else {
            current + layout
        }
        write(updated)
    }

    /** Removes a layout. The active one falls back to the first that is left. */
    fun delete(id: String) {
        val updated = _layouts.value.filterNot { it.id == id }
        write(updated)
        if (_activeId.value == id) setActive(updated.firstOrNull()?.id)
    }

    fun setActive(id: String?) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
        _activeId.value = id
    }

    private fun write(layouts: List<PlayerLayout>) {
        prefs.edit().putString(KEY_LAYOUTS, PlayerLayoutCodec.encodeAll(layouts)).apply()
        _layouts.value = layouts
    }

    companion object {
        /** Named in `BackupRepository`'s preference list; rename both together. */
        const val PREFS_NAME = "koda_player_layouts"
        private const val KEY_LAYOUTS = "layouts"
        private const val KEY_ACTIVE = "active_layout"

        /** The layout the Custom style draws, or null when none has been made. */
        fun active(layouts: List<PlayerLayout>, activeId: String?): PlayerLayout? =
            layouts.firstOrNull { it.id == activeId } ?: layouts.firstOrNull()
    }
}
