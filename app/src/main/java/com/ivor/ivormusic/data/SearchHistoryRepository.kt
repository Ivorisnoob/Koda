package com.ivor.ivormusic.data

import android.content.Context

/**
 * Manages search history using SharedPreferences.
 *
 * **Scoped per profile** (September 2026), with the listening history and
 * liked songs: recent searches are suggested back and feed the signed-out
 * recommendations, so a device-wide list offered one profile another's
 * searches. The key is resolved on every call, because an instance outlives a
 * profile switch; see [ProfileManager.historyOwnerProfileId] for which
 * profile kept the list that was already on the device.
 */
class SearchHistoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun historyKey(): String =
        keyFor(appContext, ProfileManager.requireActiveProfileId(appContext))

    fun getHistory(): List<String> = parse(prefs.getString(historyKey(), "") ?: "")

    fun addQuery(query: String) {
        // Incognito records nothing that would later be suggested back.
        if (IncognitoMode.isEnabled(appContext)) return
        if (query.isBlank()) return
        val key = historyKey()
        val current = parse(prefs.getString(key, "") ?: "").toMutableList()
        current.remove(query) // Remove if already exists to move to top
        current.add(0, query)

        // Limit to 15 items
        val limited = current.take(15)
        prefs.edit().putString(key, limited.joinToString("|")).apply()
    }

    fun removeQuery(query: String) {
        val key = historyKey()
        val current = parse(prefs.getString(key, "") ?: "").toMutableList()
        current.remove(query)
        prefs.edit().putString(key, current.joinToString("|")).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(historyKey()).apply()
    }

    private fun parse(historyString: String): List<String> =
        if (historyString.isEmpty()) emptyList() else historyString.split("|")

    companion object {
        internal const val PREFS_NAME = "search_history"
        internal const val KEY_HISTORY = "history_list"

        /** [profileId]'s key, for backups and a new profile's copy. */
        internal fun keyFor(context: Context, profileId: String): String =
            ProfileManager.historyScopedKey(KEY_HISTORY, profileId, context)

        /** Give [toProfileId] a copy of [fromProfileId]'s searches, if it has none. */
        internal fun copyProfileData(context: Context, fromProfileId: String, toProfileId: String) {
            ProfileManager.copyScopedPreferences(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                listOf(KEY_HISTORY), fromProfileId, toProfileId
            ) { _, profileId -> keyFor(context, profileId) }
        }
    }
}
