package com.ivor.ivormusic.data

import com.russhwolf.settings.Settings

class SearchHistoryRepository(private val settings: Settings) {

    fun getHistory(): List<String> {
        val raw = settings.getStringOrNull(KEY_HISTORY) ?: return emptyList()
        return if (raw.isBlank()) emptyList() else raw.split("|")
    }

    fun addQuery(query: String) {
        if (query.isBlank()) return
        val current = getHistory().toMutableList()
        current.remove(query)
        current.add(0, query)
        settings.putString(KEY_HISTORY, current.take(15).joinToString("|"))
    }

    fun removeQuery(query: String) {
        val current = getHistory().toMutableList()
        current.remove(query)
        settings.putString(KEY_HISTORY, current.joinToString("|"))
    }

    fun clearHistory() {
        settings.remove(KEY_HISTORY)
    }

    companion object {
        private const val KEY_HISTORY = "search_history_list"
    }
}
