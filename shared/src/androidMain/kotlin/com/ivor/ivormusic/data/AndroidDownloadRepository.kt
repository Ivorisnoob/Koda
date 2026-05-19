package com.ivor.ivormusic.data

import android.content.Context
import android.util.Log
import com.ivor.ivormusic.domain.Song

internal enum class DownloadStatus { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED, LOCAL_ORIGINAL }

internal data class DownloadProgress(
    val songId: String,
    val song: Song,
    val progress: Float,
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0
)
import com.ivor.ivormusic.domain.SongSource
import com.ivor.ivormusic.network.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

class AndroidDownloadRepository(
    private val context: Context,
    private val youtubeRepository: YouTubeRepository
) : DownloadRepository {

    companion object {
        private const val TAG = "DownloadRepository"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val notificationHelper = DownloadNotificationHelper(context)
    private val downloadsFile = File(context.filesDir, "downloaded_songs_metadata.json")
    private val musicDir = File(context.filesDir, "music")

    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    override val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    override val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    private val _downloadProgressPct = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgressPct.asStateFlow()

    private val activeDownloadCalls = mutableMapOf<String, okhttp3.Call>()

    init {
        if (!musicDir.exists()) musicDir.mkdirs()
        loadDownloadedSongs()
    }

    private fun loadDownloadedSongs() {
        if (!downloadsFile.exists()) { _downloadedSongs.value = emptyList(); return }
        try {
            val arr = JSONArray(downloadsFile.readText())
            val songs = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val filePath = obj.getString("localPath")
                if (File(filePath).exists()) {
                    songs.add(Song(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.optString("album", ""),
                        duration = obj.getLong("duration"),
                        uri = filePath,
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
            val arr = JSONArray()
            _downloadedSongs.value.forEach { song ->
                if (song.source == SongSource.LOCAL && song.uri?.startsWith(context.filesDir.path) == true) {
                    arr.put(JSONObject().apply {
                        put("id", song.id); put("title", song.title); put("artist", song.artist)
                        put("album", song.album); put("duration", song.duration)
                        put("localPath", song.uri); put("albumArtUrl", song.thumbnailUrl ?: song.albumArtUri)
                    })
                }
            }
            downloadsFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metadata", e)
        }
    }

    private fun updateProgress(songId: String, song: Song, progress: Float, status: DownloadStatus, bytesDownloaded: Long = 0, totalBytes: Long = 0) {
        val dp = DownloadProgress(songId, song, progress, status, bytesDownloaded, totalBytes)
        _downloadProgress.value = _downloadProgress.value.toMutableMap().also { it[songId] = dp }
        _downloadProgressPct.value = _downloadProgressPct.value.toMutableMap().also { it[songId] = (progress * 100).toInt() }
        when (status) {
            DownloadStatus.DOWNLOADING -> notificationHelper.showDownloadProgress(songId, song.title, song.artist, progress, bytesDownloaded, totalBytes)
            DownloadStatus.DOWNLOADED -> notificationHelper.showDownloadComplete(songId, song.title, song.artist)
            DownloadStatus.FAILED -> notificationHelper.showDownloadFailed(songId, song.title)
            else -> {}
        }
    }

    private fun removeProgress(songId: String) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().also { it.remove(songId) }
        _downloadProgressPct.value = _downloadProgressPct.value.toMutableMap().also { it.remove(songId) }
        notificationHelper.dismissNotification(songId)
    }

    override suspend fun downloadSong(song: Song) = withContext(Dispatchers.IO) {
        if (isDownloaded(song.id) || _downloadingIds.value.contains(song.id)) return@withContext
        _downloadingIds.value = _downloadingIds.value + song.id
        updateProgress(song.id, song, 0f, DownloadStatus.DOWNLOADING)
        try {
            updateProgress(song.id, song, 0.05f, DownloadStatus.DOWNLOADING)
            val streamResult = (youtubeRepository as? com.ivor.ivormusic.network.AndroidYouTubeRepository)?.getStreamUrl(song.id)
            val streamUrl = streamResult?.getOrNull()
            if (streamUrl == null) {
                updateProgress(song.id, song, 0f, DownloadStatus.FAILED); return@withContext
            }
            if (!_downloadingIds.value.contains(song.id)) { removeProgress(song.id); return@withContext }
            updateProgress(song.id, song, 0.1f, DownloadStatus.DOWNLOADING)
            val call = client.newCall(Request.Builder().url(streamUrl).build())
            activeDownloadCalls[song.id] = call
            val response = call.execute()
            if (!response.isSuccessful) { updateProgress(song.id, song, 0f, DownloadStatus.FAILED); return@withContext }
            val body = response.body ?: run { updateProgress(song.id, song, 0f, DownloadStatus.FAILED); return@withContext }
            val totalBytes = body.contentLength()
            val file = File(musicDir, "${song.id}.m4a")
            var bytesDownloaded = 0L
            val buffer = ByteArray(8192)
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        if (!_downloadingIds.value.contains(song.id)) { file.delete(); removeProgress(song.id); return@withContext }
                        output.write(buffer, 0, n)
                        bytesDownloaded += n
                        val p = if (totalBytes > 0) 0.1f + bytesDownloaded.toFloat() / totalBytes * 0.9f else 0.5f
                        updateProgress(song.id, song, p, DownloadStatus.DOWNLOADING, bytesDownloaded, totalBytes)
                    }
                }
            }
            val downloaded = song.copy(uri = file.absolutePath, source = SongSource.LOCAL)
            _downloadedSongs.value = _downloadedSongs.value + downloaded
            saveMetadata()
            updateProgress(song.id, song, 1f, DownloadStatus.DOWNLOADED)
            delay(1000)
            removeProgress(song.id)
        } catch (e: Exception) {
            if (!_downloadingIds.value.contains(song.id)) removeProgress(song.id)
            else updateProgress(song.id, song, 0f, DownloadStatus.FAILED)
        } finally {
            _downloadingIds.value = _downloadingIds.value - song.id
            activeDownloadCalls.remove(song.id)
        }
    }

    override suspend fun downloadPlaylist(songs: List<Song>) {
        songs.filter { !isDownloaded(it.id) }.forEach { downloadSong(it) }
    }

    override fun cancelDownload(songId: String) {
        activeDownloadCalls[songId]?.cancel()
        activeDownloadCalls.remove(songId)
        _downloadingIds.value = _downloadingIds.value - songId
        removeProgress(songId)
        File(musicDir, "$songId.m4a").takeIf { it.exists() }?.delete()
    }

    override fun deleteDownload(songId: String) {
        val song = _downloadedSongs.value.find { it.id == songId } ?: return
        song.uri?.let { File(it).delete() }
        _downloadedSongs.value = _downloadedSongs.value.filter { it.id != songId }
        saveMetadata()
    }

    override fun isDownloaded(songId: String): Boolean = _downloadedSongs.value.any { it.id == songId }

    override fun isLocalOriginal(song: Song): Boolean =
        song.source == SongSource.LOCAL && song.uri?.startsWith(context.filesDir.path) == false
}
