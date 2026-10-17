package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Caches library metadata (liked songs, playlists) to disk so the Library
 * screen shows instantly on app open, even before the YT Music refresh
 * completes. The cache is updated in the background after each successful
 * refresh.
 */
class LibraryCache(private val context: Context) {
    private val cacheFile: File
        get() = File(context.cacheDir, "library_cache.json")

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadCachedLikedSongs(): List<Song> = withContext(Dispatchers.IO) {
        try {
            val file = cacheFile
            if (!file.exists()) return@withContext emptyList()
            val text = file.readText()
            json.decodeFromString<List<Song>>(text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveLikedSongs(songs: List<Song>) = withContext(Dispatchers.IO) {
        try {
            cacheFile.writeText(json.encodeToString(songs))
        } catch (e: Exception) {
            // Cache write failures are non-fatal
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            cacheFile.delete()
        } catch (e: Exception) {
        }
    }
}
