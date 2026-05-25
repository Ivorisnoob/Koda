package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.SongSource
import com.ivor.ivormusic.network.YouTubeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class DesktopDownloadRepository(
    private val youtubeRepository: YouTubeRepository
) : DownloadRepository {

    private val downloadDir = File(System.getProperty("user.home"), ".config/koda/downloads").also { it.mkdirs() }
    private val indexFile = File(downloadDir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    override val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    override val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    init {
        loadIndex()
    }

    private fun loadIndex() {
        if (!indexFile.exists()) return
        try {
            val songs = json.decodeFromString<List<Song>>(indexFile.readText())
            _downloadedSongs.value = songs
        } catch (_: Exception) {}
    }

    private fun saveIndex() {
        indexFile.writeText(json.encodeToString(_downloadedSongs.value))
    }

    override suspend fun downloadSong(song: Song) {
        if (isDownloaded(song.id)) return
        _downloadingIds.value = _downloadingIds.value + song.id
        _downloadProgress.value = _downloadProgress.value + (song.id to 0)
        scope.launch {
            try {
                val streamUrl = youtubeRepository.getVideoStreamUrl(song.id) ?: return@launch
                val destFile = File(downloadDir, "${song.id}.m4a")
                val request = Request.Builder().url(streamUrl).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body ?: return@launch
                    val total = body.contentLength()
                    var received = 0L
                    destFile.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(8192)
                            var n: Int
                            while (input.read(buf).also { n = it } != -1) {
                                out.write(buf, 0, n)
                                received += n
                                if (total > 0) {
                                    _downloadProgress.value = _downloadProgress.value +
                                            (song.id to ((received * 100) / total).toInt())
                                }
                            }
                        }
                    }
                }
                val localSong = song.copy(
                    source = SongSource.LOCAL,
                    uri = destFile.toURI().toString()
                )
                _downloadedSongs.value = _downloadedSongs.value + localSong
                saveIndex()
            } catch (_: Exception) {
            } finally {
                _downloadingIds.value = _downloadingIds.value - song.id
                _downloadProgress.value = _downloadProgress.value - song.id
            }
        }
    }

    override suspend fun downloadPlaylist(songs: List<Song>) {
        songs.forEach { downloadSong(it) }
    }

    override fun cancelDownload(songId: String) {
        _downloadingIds.value = _downloadingIds.value - songId
    }

    override fun deleteDownload(songId: String) {
        File(downloadDir, "$songId.m4a").delete()
        _downloadedSongs.value = _downloadedSongs.value.filter { it.id != songId }
        saveIndex()
    }

    override fun isDownloaded(songId: String): Boolean =
        _downloadedSongs.value.any { it.id == songId }

    override fun isLocalOriginal(song: Song): Boolean = song.source == SongSource.LOCAL && song.uri != null
}
