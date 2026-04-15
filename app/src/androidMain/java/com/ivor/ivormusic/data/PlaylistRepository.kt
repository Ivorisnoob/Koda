package com.ivor.ivormusic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID
import kotlin.random.Random

class PlaylistRepository(private val context: Context) {

    private val playlistDir = File(context.filesDir, "playlists")
    private val coversDir = File(context.filesDir, "playlist_covers")
    
    // Cache of playlists
    private val _userPlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val userPlaylists: StateFlow<List<UserPlaylist>> = _userPlaylists.asStateFlow()

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
        isLenient = true
    }

    init {
        if (!playlistDir.exists()) playlistDir.mkdirs()
        if (!coversDir.exists()) coversDir.mkdirs()
        loadPlaylists()
    }

    private fun loadPlaylists() {
        // Load on background thread logic should be called from coroutine, but for init we do minimal
        // We'll expose suspend functions for ops
        val files = playlistDir.listFiles { _, name -> name.endsWith(".json") }
        val loaded = files?.mapNotNull { file ->
            try {
                json.decodeFromString<UserPlaylist>(file.readText())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }?.sortedByDescending { it.createdAt } ?: emptyList()
        
        _userPlaylists.value = loaded
    }
    
    suspend fun refreshPlaylists() = withContext(Dispatchers.IO) {
        loadPlaylists()
    }

    suspend fun createPlaylist(name: String, description: String?): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val coverPath = generateCoverArt(name, id)
        
        val newPlaylist = UserPlaylist(
            id = id,
            name = name,
            description = description,
            coverUri = "file://$coverPath",
            songs = emptyList()
        )
        
        savePlaylist(newPlaylist)
        return@withContext id
    }
    
    suspend fun addSongToPlaylist(playlistId: String, song: Song) = withContext(Dispatchers.IO) {
        val currentList = _userPlaylists.value
        val playlist = currentList.find { it.id == playlistId } ?: return@withContext
        
        // Check if song already exists
        if (playlist.songs.any { it.id == song.id }) return@withContext
        
        val updatedPlaylist = playlist.copy(
            songs = playlist.songs + song
        )
        savePlaylist(updatedPlaylist)
    }
    
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        val currentList = _userPlaylists.value
        val playlist = currentList.find { it.id == playlistId } ?: return@withContext
        
        val updatedPlaylist = playlist.copy(
            songs = playlist.songs.filter { it.id != songId }
        )
        savePlaylist(updatedPlaylist)
    }
    
    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        val file = File(playlistDir, "$playlistId.json")
        if (file.exists()) file.delete()
        
        // Also delete cover
        val cover = File(coversDir, "cover_$playlistId.png")
        if (cover.exists()) cover.delete()
        
        loadPlaylists()
    }
    
    private suspend fun savePlaylist(playlist: UserPlaylist) {
        val file = File(playlistDir, "${playlist.id}.json")
        val jsonString = json.encodeToString(playlist)
        file.writeText(jsonString)
        loadPlaylists() // Update cache
    }
    
    /**
     * Generates a gradient cover art with the first letter of the playlist name.
     */
    private fun generateCoverArt(name: String, id: String): String {
        val size = 1000 // High res
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Apple Music style gradients (mesh-like or simple linear)
        // We'll do a nice linear gradient with random vivid colors
        val hue1 = Random.nextFloat() * 360f
        val hue2 = (hue1 + 40 + Random.nextFloat() * 100) % 360
        
        val color1 = Color.HSVToColor(floatArrayOf(hue1, 0.8f, 0.9f))
        val color2 = Color.HSVToColor(floatArrayOf(hue2, 0.8f, 0.8f))
        
        val paint = Paint()
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            color1, color2,
            android.graphics.Shader.TileMode.CLAMP
        )
        
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        
        // Draw text (First Letter)
        val letter = name.take(1).uppercase()
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size * 0.5f // Large text
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            
            // Add shadow
            setShadowLayer(20f, 0f, 10f, Color.argb(100, 0, 0, 0))
        }
        
        // Center text
        val xPos = size / 2f
        val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)
        
        canvas.drawText(letter, xPos, yPos, textPaint)
        
        // Save to file
        val file = File(coversDir, "cover_$id.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        return file.absolutePath
    }
}
