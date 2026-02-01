package com.ivor.ivormusic.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf

/**
 * Manages search history using SharedPreferences.
 */
class SearchHistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun getHistory(): List<String> {
        val historyString = prefs.getString(KEY_HISTORY, "") ?: ""
        return if (historyString.isEmpty()) emptyList() else historyString.split("|")
    }
    
    fun addQuery(query: String) {
        if (query.isBlank()) return
        val current = getHistory().toMutableList()
        current.remove(query) // Remove if already exists to move to top
        current.add(0, query)
        
        // Limit to 15 items
        val limited = current.take(15)
        prefs.edit().putString(KEY_HISTORY, limited.joinToString("|")).apply()
    }
    
    fun removeQuery(query: String) {
        val current = getHistory().toMutableList()
        current.remove(query)
        prefs.edit().putString(KEY_HISTORY, current.joinToString("|")).apply()
    }
    
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
    
    companion object {
        private const val PREFS_NAME = "search_history"
        private const val KEY_HISTORY = "history_list"
    }
}
