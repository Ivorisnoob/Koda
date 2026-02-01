package com.ivor.ivormusic.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
    LOCAL_ORIGINAL
}

data class DownloadProgress(
    val songId: String,
    val song: Song,
    val progress: Float, // 0.0 to 1.0
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0
)

class DownloadRepository(private val context: Context) {
    companion object {
        private const val TAG = "DownloadRepository"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val youtubeRepository = YouTubeRepository(context)
    private val notificationHelper = DownloadNotificationHelper(context)

    private val downloadsFile = File(context.filesDir, "downloaded_songs_metadata.json")
    private val musicDir = File(context.filesDir, "music")

    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    // Real-time progress tracking for each active download
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    // Download queue for batch downloads
    private val _downloadQueue = MutableStateFlow<List<Song>>(emptyList())
    val downloadQueue: StateFlow<List<Song>> = _downloadQueue.asStateFlow()

    // Track active download calls for cancellation
    private val activeDownloadCalls = mutableMapOf<String, okhttp3.Call>()
    private val downloadJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    init {
        if (!musicDir.exists()) musicDir.mkdirs()
        loadDownloadedSongs()
    }

    private fun loadDownloadedSongs() {
        if (!downloadsFile.exists()) {
            _downloadedSongs.value = emptyList()
            return
        }

        try {
            val jsonStr = downloadsFile.readText()
            val jsonArray = JSONArray(jsonStr)
            val songs = mutableListOf<Song>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val filePath = obj.getString("localPath")
                val file = File(filePath)
                if (file.exists()) {
                    songs.add(Song(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.optString("album", ""),
                        duration = obj.getLong("duration"),
                        uri = Uri.fromFile(file),
                        albumArtUri = if (obj.has("albumArtUrl")) Uri.parse(obj.getString("albumArtUrl")) else null,
                        thumbnailUrl = if (obj.has("albumArtUrl") && !obj.isNull("albumArtUrl")) obj.getString("albumArtUrl") else null,
                        source = SongSource.LOCAL
                    ))
                }
            }
            _downloadedSongs.value = songs
        } catch (e: Exception) {
            Log.e(TAG, "Error loading downloads", e)
            _downloadedSongs.value = emptyList()
        }
    }

    private fun saveMetadata() {
        try {
            val jsonArray = JSONArray()
            _downloadedSongs.value.forEach { song ->
                if (song.source == SongSource.LOCAL && song.uri?.path?.startsWith(context.filesDir.path) == true) {
                    val obj = JSONObject().apply {
                        put("id", song.id)
                        put("title", song.title)
                        put("artist", song.artist)
                        put("album", song.album)
                        put("duration", song.duration)
                        put("localPath", song.uri.path)
                        put("albumArtUrl", song.thumbnailUrl ?: song.albumArtUri?.toString())
                    }
                    jsonArray.put(obj)
                }
            }
            downloadsFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metadata", e)
        }
    }

    private fun updateProgress(songId: String, song: Song, progress: Float, status: DownloadStatus, bytesDownloaded: Long = 0, totalBytes: Long = 0) {
        val current = _downloadProgress.value.toMutableMap()
        current[songId] = DownloadProgress(songId, song, progress, status, bytesDownloaded, totalBytes)
        _downloadProgress.value = current
        
        // Update notification
        when (status) {
            DownloadStatus.DOWNLOADING -> {
                notificationHelper.showDownloadProgress(
                    songId = songId,
                    songTitle = song.title,
                    artistName = song.artist,
                    progress = progress,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes
                )
            }
            DownloadStatus.DOWNLOADED -> {
                notificationHelper.showDownloadComplete(
                    songId = songId,
                    songTitle = song.title,
                    artistName = song.artist
                )
            }
            DownloadStatus.FAILED -> {
                notificationHelper.showDownloadFailed(
                    songId = songId,
                    songTitle = song.title
                )
            }
            else -> { /* No notification for other statuses */ }
        }
    }

    private fun removeProgress(songId: String) {
        val current = _downloadProgress.value.toMutableMap()
        current.remove(songId)
        _downloadProgress.value = current
        
        // Dismiss notification when progress is removed
        notificationHelper.dismissNotification(songId)
    }

    suspend fun downloadSong(song: Song) = withContext(Dispatchers.IO) {
        if (isDownloaded(song.id) || _downloadingIds.value.contains(song.id)) return@withContext

        _downloadingIds.value = _downloadingIds.value + song.id
        updateProgress(song.id, song, 0f, DownloadStatus.DOWNLOADING)

        try {
            Log.d(TAG, "Starting download for: ${song.title}")
            
            // Get stream URL
            updateProgress(song.id, song, 0.05f, DownloadStatus.DOWNLOADING)
            val result = youtubeRepository.getStreamUrl(song.id)
            val streamUrl = result.getOrNull()
            
            if (streamUrl == null) {
                val error = result.exceptionOrNull()
                Log.e(TAG, "Could not get stream URL for ${song.title}", error)
                updateProgress(song.id, song, 0f, DownloadStatus.FAILED)
                return@withContext
            }

            // Check if cancelled during URL fetch
            if (!_downloadingIds.value.contains(song.id)) {
                Log.d(TAG, "Download cancelled for ${song.title}")
                removeProgress(song.id)
                return@withContext
            }

            updateProgress(song.id, song, 0.1f, DownloadStatus.DOWNLOADING)
            Log.d(TAG, "Got stream URL, starting download...")

            val request = Request.Builder().url(streamUrl).build()
            val call = client.newCall(request)
            activeDownloadCalls[song.id] = call
            
            val response = call.execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed with code: ${response.code}")
                updateProgress(song.id, song, 0f, DownloadStatus.FAILED)
                return@withContext
            }

            val body = response.body
            if (body == null) {
                Log.e(TAG, "Response body is null")
                updateProgress(song.id, song, 0f, DownloadStatus.FAILED)
                return@withContext
            }

            val totalBytes = body.contentLength()
            val file = File(musicDir, "${song.id}.m4a")
            
            Log.d(TAG, "Downloading ${totalBytes} bytes to ${file.absolutePath}")

            var bytesDownloaded = 0L
            val buffer = ByteArray(8192)

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check for cancellation
                        if (!_downloadingIds.value.contains(song.id)) {
                            Log.d(TAG, "Download cancelled during transfer for ${song.title}")
                            file.delete()
                            removeProgress(song.id)
                            return@withContext
                        }
                        
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        // Update progress (10% is fetching URL, 90% is actual download)
                        val downloadProgress = if (totalBytes > 0) {
                            0.1f + (bytesDownloaded.toFloat() / totalBytes.toFloat() * 0.9f)
                        } else {
                            0.5f // Indeterminate
                        }
                        updateProgress(song.id, song, downloadProgress, DownloadStatus.DOWNLOADING, bytesDownloaded, totalBytes)
                    }
                }
            }

            Log.d(TAG, "Download complete for: ${song.title}")

            // Create new song object pointing to local file
            val downloadedSong = song.copy(
                uri = Uri.fromFile(file),
                source = SongSource.LOCAL
            )

            // Update list
            val currentList = _downloadedSongs.value.toMutableList()
            currentList.add(downloadedSong)
            _downloadedSongs.value = currentList
            saveMetadata()

            updateProgress(song.id, song, 1f, DownloadStatus.DOWNLOADED)
            
            // Remove from progress after a short delay
            kotlinx.coroutines.delay(1000)
            removeProgress(song.id)

        } catch (e: java.io.IOException) {
            // Check if this was a cancellation
            if (!_downloadingIds.value.contains(song.id)) {
                Log.d(TAG, "Download was cancelled for ${song.title}")
                removeProgress(song.id)
            } else {
                Log.e(TAG, "Failed to download song ${song.title}", e)
                updateProgress(song.id, song, 0f, DownloadStatus.FAILED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download song ${song.title}", e)
            updateProgress(song.id, song, 0f, DownloadStatus.FAILED)
        } finally {
            _downloadingIds.value = _downloadingIds.value - song.id
            activeDownloadCalls.remove(song.id)
        }
    }

    suspend fun downloadPlaylist(songs: List<Song>) {
        _downloadQueue.value = songs.filter { !isDownloaded(it.id) }
        
        for (song in _downloadQueue.value) {
            downloadSong(song)
        }
        
        _downloadQueue.value = emptyList()
    }

    fun cancelDownload(songId: String) {
        Log.d(TAG, "Cancelling download for $songId")
        
        // Cancel the OkHttp call if active
        activeDownloadCalls[songId]?.cancel()
        activeDownloadCalls.remove(songId)
        
        // Remove from downloading set (this triggers the checks in downloadSong)
        _downloadingIds.value = _downloadingIds.value - songId
        removeProgress(songId)
        
        // Delete partial file if exists
        val partialFile = File(musicDir, "${songId}.m4a")
        if (partialFile.exists()) {
            partialFile.delete()
        }
    }

    fun deleteDownload(songId: String) {
        val currentList = _downloadedSongs.value.toMutableList()
        val songToDelete = currentList.find { it.id == songId } ?: return

        songToDelete.uri?.path?.let { path ->
            File(path).delete()
        }

        currentList.remove(songToDelete)
        _downloadedSongs.value = currentList
        saveMetadata()
    }

    fun isDownloaded(songId: String): Boolean {
        return _downloadedSongs.value.any { it.id == songId }
    }
    
    fun getDownloadStatus(songId: String): DownloadStatus {
        if (_downloadingIds.value.contains(songId)) return DownloadStatus.DOWNLOADING
        if (isDownloaded(songId)) return DownloadStatus.DOWNLOADED
        return DownloadStatus.NOT_DOWNLOADED
    }
    
    fun isLocalOriginal(song: Song): Boolean {
        return song.source == SongSource.LOCAL && 
               (song.uri?.path?.startsWith(context.filesDir.path) == false)
    }

    fun clearFailedDownloads() {
        val current = _downloadProgress.value.toMutableMap()
        current.entries.removeAll { it.value.status == DownloadStatus.FAILED }
        _downloadProgress.value = current
    }
}
