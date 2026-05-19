package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-independent download repository.
 * Android: downloads to filesDir using OkHttp, shows system notifications.
 * iOS: TODO stub.
 */
interface DownloadRepository {
    val downloadedSongs: StateFlow<List<Song>>
    val downloadingIds: StateFlow<Set<String>>
    val downloadProgress: StateFlow<Map<String, Int>>

    suspend fun downloadSong(song: Song)
    suspend fun downloadPlaylist(songs: List<Song>)
    fun cancelDownload(songId: String)
    fun deleteDownload(songId: String)
    fun isDownloaded(songId: String): Boolean
    fun isLocalOriginal(song: Song): Boolean
}
