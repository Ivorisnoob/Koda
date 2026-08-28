package com.ivor.ivormusic.data

import android.content.SharedPreferences

/**
 * Process-only SharedPreferences used when Android Keystore is unavailable.
 *
 * Falling back to a normal on-disk preference file would silently store login
 * cookies in plaintext. This implementation keeps the app usable and signed
 * out without weakening storage; values disappear with the process and the
 * encrypted store is attempted again on the next launch.
 */
internal class VolatileSharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = synchronized(this) { values.toMap() }

    override fun getString(key: String?, defValue: String?): String? =
        synchronized(this) { values[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
        synchronized(this) { (values[key] as? Set<String>)?.toSet() ?: defValues }

    override fun getInt(key: String?, defValue: Int): Int =
        synchronized(this) { values[key] as? Int ?: defValue }

    override fun getLong(key: String?, defValue: Long): Long =
        synchronized(this) { values[key] as? Long ?: defValue }

    override fun getFloat(key: String?, defValue: Float): Float =
        synchronized(this) { values[key] as? Float ?: defValue }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        synchronized(this) { values[key] as? Boolean ?: defValue }

    override fun contains(key: String?): Boolean = synchronized(this) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener != null) synchronized(this) { listeners += listener }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener != null) synchronized(this) { listeners -= listener }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var clearFirst = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            put(key, value)

        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor =
            put(key, values?.toSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor = put(key, REMOVED)

        override fun clear(): SharedPreferences.Editor = apply {
            clearFirst = true
            changes.clear()
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() = applyChanges()

        private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply {
            if (key != null) changes[key] = value
        }

        private fun applyChanges() {
            val changedKeys: Set<String>
            val currentListeners: List<SharedPreferences.OnSharedPreferenceChangeListener>
            synchronized(this@VolatileSharedPreferences) {
                val changed = linkedSetOf<String>()
                if (clearFirst) {
                    changed += values.keys
                    values.clear()
                }
                changes.forEach { (key, value) ->
                    if (value === REMOVED || value == null) values.remove(key) else values[key] = value
                    changed += key
                }
                changedKeys = changed
                currentListeners = listeners.toList()
            }
            changedKeys.forEach { key ->
                currentListeners.forEach { listener ->
                    listener.onSharedPreferenceChanged(this@VolatileSharedPreferences, key)
                }
            }
        }
    }

    private companion object {
        private val REMOVED = Any()
    }
}
