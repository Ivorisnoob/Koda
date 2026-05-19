package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.FolderInfo
import com.ivor.ivormusic.domain.Song

/**
 * Platform-independent local song repository.
 * Android: backed by MediaStore.
 * iOS: backed by MPMediaLibrary.
 */
interface LocalSongRepository {
    suspend fun getSongs(excludedFolders: Set<String> = emptySet(), manualScan: Boolean = false): List<Song>
    suspend fun getAvailableFolders(): List<FolderInfo>
}
