package com.ivor.ivormusic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.UserPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.random.Random

class AndroidPlaylistRepository(private val context: Context) : PlaylistRepository {

    private val playlistDir = File(context.filesDir, "playlists")
    private val coversDir = File(context.filesDir, "playlist_covers")

    private val _userPlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    override val userPlaylists: StateFlow<List<UserPlaylist>> = _userPlaylists.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; isLenient = true }

    init {
        if (!playlistDir.exists()) playlistDir.mkdirs()
        if (!coversDir.exists()) coversDir.mkdirs()
        loadPlaylists()
    }

    private fun loadPlaylists() {
        val files = playlistDir.listFiles { _, name -> name.endsWith(".json") }
        val loaded = files?.mapNotNull { file ->
            try { json.decodeFromString<UserPlaylist>(file.readText()) } catch (_: Exception) { null }
        }?.sortedByDescending { it.createdAt } ?: emptyList()
        _userPlaylists.value = loaded
    }

    override suspend fun refreshPlaylists() = withContext(Dispatchers.IO) { loadPlaylists() }

    override suspend fun createPlaylist(name: String, description: String?): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val coverPath = generateCoverArt(name, id)
        val playlist = UserPlaylist(id = id, name = name, description = description, coverUri = "file://$coverPath", songs = emptyList())
        savePlaylist(playlist)
        id
    }

    override suspend fun addSongToPlaylist(playlistId: String, song: Song) = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext
        if (playlist.songs.any { it.id == song.id }) return@withContext
        savePlaylist(playlist.copy(songs = playlist.songs + song))
    }

    override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext
        savePlaylist(playlist.copy(songs = playlist.songs.filter { it.id != songId }))
    }

    override suspend fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext
        if (fromIndex == toIndex) return@withContext
        if (fromIndex !in playlist.songs.indices || toIndex !in playlist.songs.indices) return@withContext
        val songs = playlist.songs.toMutableList().apply {
            val moved = removeAt(fromIndex)
            add(toIndex, moved)
        }
        savePlaylist(playlist.copy(songs = songs))
    }

    override suspend fun replacePlaylistSongs(playlistId: String, songs: List<Song>) = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext
        savePlaylist(playlist.copy(songs = songs))
    }

    override suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        File(playlistDir, "$playlistId.json").takeIf { it.exists() }?.delete()
        File(coversDir, "cover_$playlistId.png").takeIf { it.exists() }?.delete()
        loadPlaylists()
    }

    override suspend fun updatePlaylist(playlistId: String, name: String, description: String?) = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return@withContext
        val shouldRegen = playlist.coverUri?.contains("cover_${playlist.id}.png") == true &&
            playlist.name.firstOrNull()?.uppercaseChar() != trimmedName.firstOrNull()?.uppercaseChar()
        val updatedCover = if (shouldRegen) "file://${generateCoverArt(trimmedName, playlist.id)}" else playlist.coverUri
        savePlaylist(playlist.copy(name = trimmedName, description = description?.trim().takeUnless { it.isNullOrBlank() }, coverUri = updatedCover))
    }

    private suspend fun savePlaylist(playlist: UserPlaylist) {
        File(playlistDir, "${playlist.id}.json").writeText(json.encodeToString(playlist))
        loadPlaylists()
    }

    private fun generateCoverArt(name: String, id: String): String {
        val size = 1000
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue1 = Random.nextFloat() * 360f
        val hue2 = (hue1 + 40 + Random.nextFloat() * 100) % 360
        val paint = Paint()
        paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
            Color.HSVToColor(floatArrayOf(hue1, 0.8f, 0.9f)), Color.HSVToColor(floatArrayOf(hue2, 0.8f, 0.8f)),
            Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val letter = name.take(1).uppercase()
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size * 0.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(20f, 0f, 10f, Color.argb(100, 0, 0, 0))
        }
        canvas.drawText(letter, size / 2f, (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2), textPaint)
        val file = File(coversDir, "cover_$id.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }
}
