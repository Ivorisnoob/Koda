package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * One icon look the user mixed and kept.
 *
 * Colours are stored as ARGB longs rather than as a Compose `Color`, so the
 * store carries no UI types and the format is readable by anything that opens
 * the preference file. `shape` is an [com.ivor.ivormusic.ui.settings.IconShape]
 * id, and those ids are frozen for the same reason every other persisted enum
 * constant here is.
 */
@Serializable
data class SavedIconStyle(
    val id: String,
    val name: String,
    val blob: Long,
    val note: Long,
    val flag: Long,
    val dot: Long,
    val dark1: Long,
    val dark2: Long,
    val dark3: Long,
    val background: Long,
    val shape: String,
)

/**
 * The user's own icon styles, kept on the device.
 *
 * **Its own preference file, and it is in backups.** A style is a handful of
 * colours somebody sat and mixed, which makes it exactly the kind of thing that
 * should survive a new phone - and because `BackupRepository` copies whole
 * preference files key by key, being one file is all it takes. It is
 * deliberately not a key inside `ivor_music_theme_prefs`: this is a list that
 * grows, and one JSON string in its own file keeps a write atomic, the same
 * reasoning `PlayerWidgetStore` uses.
 *
 * **A saved style cannot become a launcher icon, and the store does not pretend
 * otherwise.** Android draws the home screen from a static resource behind an
 * `<activity-alias>`; there is no runtime path from arbitrary colours to that.
 * So the store keeps the look, the settings page applies the nearest real alias
 * to the launcher, and the page says which one out loud. Anything here that
 * claimed to set a custom launcher icon would be a lie the user finds out about
 * on their home screen.
 *
 * Instantiated where it is needed rather than held process-wide: only the App
 * icon page and the About dialog read it, and neither writes behind the other's
 * back, so there is no cross-surface staleness to solve.
 */
class AppIconStyleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _styles = MutableStateFlow(read())
    val styles: StateFlow<List<SavedIconStyle>> = _styles.asStateFlow()

    private val _activeStyleId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))

    /** The saved style currently standing in for Koda's mark, if any. */
    val activeStyleId: StateFlow<String?> = _activeStyleId.asStateFlow()

    private fun read(): List<SavedIconStyle> {
        val raw = prefs.getString(KEY_STYLES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SavedIconStyle>>(raw)
        } catch (error: Exception) {
            // A style list that will not parse is worth losing quietly: it is a
            // preference, not user content, and refusing to open the page over
            // it would be the worse failure.
            KLog.w("AppIconStyles", "Saved icon styles unreadable: ${error.message}")
            emptyList()
        }
    }

    private fun write(styles: List<SavedIconStyle>) {
        prefs.edit().putString(KEY_STYLES, json.encodeToString(styles)).apply()
        _styles.value = styles
    }

    /** Store [style] under a fresh id, newest first. Returns the stored copy. */
    fun save(style: SavedIconStyle): SavedIconStyle {
        val stored = style.copy(id = UUID.randomUUID().toString())
        write(listOf(stored) + _styles.value)
        return stored
    }

    /** Replace a style in place, keeping its id and its position in the list. */
    fun update(style: SavedIconStyle) {
        write(_styles.value.map { if (it.id == style.id) style else it })
    }

    fun delete(id: String) {
        write(_styles.value.filterNot { it.id == id })
        // Deleting the active style leaves the mark on whatever preset the
        // launcher is already showing, rather than on a style that is gone.
        if (_activeStyleId.value == id) setActive(null)
    }

    fun setActive(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id)
        }.apply()
        _activeStyleId.value = id
    }

    /** The active style itself, or null when a plain preset is in use. */
    fun activeStyle(): SavedIconStyle? {
        val id = _activeStyleId.value ?: return null
        return _styles.value.firstOrNull { it.id == id }
    }

    companion object {
        /**
         * Named here and restated in `BackupRepository.PREFERENCE_FILES`, which
         * no compiler checks. Rename this and the styles silently stop being
         * backed up.
         */
        const val PREFS = "koda_icon_styles"
        private const val KEY_STYLES = "styles"
        private const val KEY_ACTIVE = "active"
    }
}
