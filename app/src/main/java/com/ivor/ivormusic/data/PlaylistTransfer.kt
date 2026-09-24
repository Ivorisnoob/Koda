package com.ivor.ivormusic.data

import android.database.sqlite.SQLiteDatabase
import com.ivor.ivormusic.util.KLog
import java.io.File

data class ImportedTrack(
    val videoId: String?,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val path: String? = null,
)

data class ImportedPlaylist(val name: String, val tracks: List<ImportedTrack>)

data class ImportedRemotePlaylist(
    val playlistId: String,
    val name: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val itemCount: Int,
)

data class PlaylistImportFile(
    val playlists: List<ImportedPlaylist> = emptyList(),
    val remote: List<ImportedRemotePlaylist> = emptyList(),
    val foreignServiceEntries: Int = 0,
)

/**
 * Playlist interchange: m3u/m3u8 both ways, plus NewPipe/PipePipe backup
 * archives in. Sniffed by content like [SubscriptionTransfer]. The NewPipe
 * schema is read by column name (`playlists`, `playlist_stream_join`,
 * `streams`, `remote_playlists`) [judgement: not yet checked against a real
 * playlist export].
 */
object PlaylistTransfer {

    private const val SERVICE_ID_YOUTUBE = 0
    private const val MAX_TEXT_BYTES = 8L * 1024 * 1024
    private const val TAG = "PlaylistTransfer"

    fun read(sourceFile: File, scratchFile: File, fallbackName: String): PlaylistImportFile {
        val header = sourceFile.inputStream().use { input ->
            ByteArray(16).also { bytes ->
                var offset = 0
                while (offset < bytes.size) {
                    val n = input.read(bytes, offset, bytes.size - offset)
                    if (n < 0) break
                    offset += n
                }
            }
        }
        if (SubscriptionTransfer.looksLikeZip(header)) {
            return sourceFile.inputStream().use { input ->
                if (SubscriptionTransfer.unpackDatabase(input, scratchFile)) {
                    try { parseDatabase(scratchFile) } finally { scratchFile.delete() }
                } else PlaylistImportFile()
            }
        }
        if (SubscriptionTransfer.looksLikeSqlite(header)) return parseDatabase(sourceFile)
        if (sourceFile.length() > MAX_TEXT_BYTES) return PlaylistImportFile()
        val tracks = parseM3u(sourceFile.readText(Charsets.UTF_8))
        return if (tracks.isEmpty()) PlaylistImportFile()
        else PlaylistImportFile(playlists = listOf(ImportedPlaylist(fallbackName, tracks)))
    }

    fun parseM3u(text: String): List<ImportedTrack> {
        val out = mutableListOf<ImportedTrack>()
        var pendingTitle: String? = null
        var pendingArtist = ""
        var pendingDuration = 0L
        text.trimStart('﻿').lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val body = line.substringAfter(':', "")
                    val seconds = body.substringBefore(',').substringBefore(' ').trim().toLongOrNull()
                    pendingDuration = if (seconds != null && seconds > 0) seconds * 1000 else 0L
                    val display = body.substringAfter(',', "").trim()
                    val split = display.indexOf(" - ")
                    if (split > 0) {
                        pendingArtist = display.substring(0, split).trim()
                        pendingTitle = display.substring(split + 3).trim()
                    } else {
                        pendingArtist = ""
                        pendingTitle = display.ifBlank { null }
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val videoId = videoIdFrom(line)
                    val fileName = line.substringAfterLast('/').substringAfterLast('\\')
                    out += ImportedTrack(
                        videoId = videoId,
                        title = pendingTitle ?: fileName.substringBeforeLast('.').ifBlank { line },
                        artist = pendingArtist,
                        durationMs = pendingDuration,
                        path = if (videoId == null) line else null,
                    )
                    pendingTitle = null
                    pendingArtist = ""
                    pendingDuration = 0L
                }
            }
        }
        return out
    }

    fun buildM3u(songs: List<Song>): String = buildString {
        append("#EXTM3U\n")
        songs.forEach { song ->
            val location = when {
                song.source == SongSource.YOUTUBE -> "https://music.youtube.com/watch?v=${song.id}"
                !song.filePath.isNullOrBlank() -> song.filePath
                else -> song.uri?.toString()
            } ?: return@forEach
            val seconds = if (song.duration > 0) song.duration / 1000 else -1
            val artist = song.artist.takeUnless { isUnknownArtist(it) }
            val label = if (artist != null) "$artist - ${song.title}" else song.title
            append("#EXTINF:").append(seconds).append(',').append(label.replace('\n', ' ')).append('\n')
            append(location).append('\n')
        }
    }

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun videoIdFrom(url: String): String? {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return null
        val candidate = when {
            "youtu.be/" in lower -> url.substringAfter("youtu.be/").substringBefore('?').substringBefore('&')
            "/shorts/" in lower -> url.substringAfter("/shorts/").substringBefore('?')
            "v=" in url -> url.substringAfter("v=").substringBefore('&').substringBefore('#')
            else -> return null
        }
        return candidate.takeIf { VIDEO_ID.matches(it) }
    }

    fun playlistIdFrom(url: String): String? =
        url.substringAfter("list=", "").substringBefore('&').substringBefore('#').ifBlank { null }

    private fun parseDatabase(file: File): PlaylistImportFile = try {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            var foreign = 0
            val byPlaylist = linkedMapOf<Long, Pair<String, MutableList<ImportedTrack>>>()
            runCatching {
                db.rawQuery(
                    """
                    SELECT p.uid AS pid, p.name AS pname, s.service_id AS service, s.url AS url,
                           s.title AS title, s.uploader AS uploader, s.duration AS duration
                    FROM playlists p
                    JOIN playlist_stream_join j ON j.playlist_id = p.uid
                    JOIN streams s ON s.uid = j.stream_id
                    ORDER BY p.uid, j.join_index
                    """.trimIndent(), null
                ).use { c ->
                    while (c.moveToNext()) {
                        val pid = c.getLong(c.getColumnIndexOrThrow("pid"))
                        val entry = byPlaylist.getOrPut(pid) {
                            (c.getString(c.getColumnIndexOrThrow("pname")) ?: "Playlist") to mutableListOf()
                        }
                        if (c.getInt(c.getColumnIndexOrThrow("service")) != SERVICE_ID_YOUTUBE) {
                            foreign++
                            continue
                        }
                        val videoId = videoIdFrom(c.getString(c.getColumnIndexOrThrow("url")) ?: "") ?: continue
                        entry.second += ImportedTrack(
                            videoId = videoId,
                            title = c.getString(c.getColumnIndexOrThrow("title")) ?: videoId,
                            artist = c.getString(c.getColumnIndexOrThrow("uploader")).orEmpty(),
                            durationMs = c.getLong(c.getColumnIndexOrThrow("duration")) * 1000,
                        )
                    }
                }
            }.onFailure { KLog.w(TAG, "No local playlists in that database", it) }

            val remote = mutableListOf<ImportedRemotePlaylist>()
            runCatching {
                db.rawQuery(
                    "SELECT service_id, name, url, thumbnail_url, uploader, stream_count FROM remote_playlists",
                    null
                ).use { c ->
                    while (c.moveToNext()) {
                        if (c.getInt(0) != SERVICE_ID_YOUTUBE) {
                            foreign++
                            continue
                        }
                        val id = playlistIdFrom(c.getString(2) ?: "") ?: continue
                        remote += ImportedRemotePlaylist(
                            playlistId = id,
                            name = c.getString(1) ?: id,
                            uploader = c.getString(4).orEmpty(),
                            thumbnailUrl = c.getString(3),
                            itemCount = if (c.isNull(5)) -1 else c.getInt(5),
                        )
                    }
                }
            }.onFailure { KLog.w(TAG, "No saved playlists in that database", it) }

            PlaylistImportFile(
                playlists = byPlaylist.values.map { (name, tracks) -> ImportedPlaylist(name, tracks) }
                    .filter { it.tracks.isNotEmpty() },
                remote = remote.distinctBy { it.playlistId },
                foreignServiceEntries = foreign,
            )
        }
    } catch (e: Exception) {
        KLog.w(TAG, "Not a NewPipe-family database", e)
        PlaylistImportFile()
    }
}
