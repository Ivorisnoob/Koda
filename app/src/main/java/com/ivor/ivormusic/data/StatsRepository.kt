package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.util.Calendar

@Serializable
data class PlayHistoryEntry(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val timestamp: Long,
    val duration: Long,
    val thumbnailUrl: String? = null,
    val source: SongSource = SongSource.YOUTUBE,
    // The streaming release (MPREb...) when the song knew it. Absent from
    // plays recorded before it existed; ignoreUnknownKeys and the default
    // keep old files and backups reading as they did.
    val albumId: String? = null,
    /**
     * How long this play was actually heard, in wall time while playing
     * (November 2026). Null for every play recorded before it was measured:
     * those only know the song's length, so anything summing time has to
     * fall back to [duration] for them and say the total is approximate.
     */
    val listenedMs: Long? = null,
) {
    /** The time this play is worth: measured when known, the song's length otherwise. */
    val heardMs: Long get() = listenedMs ?: duration
}

@Serializable
data class SongStats(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val playCount: Int,
    val totalPlayTime: Long,
    // What Stats needs to play the song from its row, taken from the newest
    // play the way the History page does.
    val album: String = "",
    val durationMs: Long = 0,
    val source: SongSource = SongSource.YOUTUBE
)

@Serializable
data class ArtistStats(
    val name: String,
    val playCount: Int,
    val songCount: Int
)

/**
 * The listening history: every qualifying play.
 *
 * **Scoped per profile** (September 2026), with the search history and liked
 * songs. It is what Stats, Recently played, Top artists, Recent albums, Spin,
 * Recap and the signed-out recommendations are built from, so while it was
 * device-wide every profile was shown, and recommended from, every other
 * profile's listening. The profile that was active when this shipped kept the
 * existing file (see [ProfileManager.historyOwnerProfileId]); everyone else
 * has their own `play_history_<id>.json`.
 *
 * **No cap, and appended rather than rewritten** (November 2026). It used to
 * be one JSON array trimmed to the newest 5,000 plays and rewritten whole on
 * every play, so a heavy listener's history reached back about two months and
 * a yearly recap could not exist. The file now holds one play per line,
 * oldest first: a play is one appended line whatever the history's size, and
 * the time actually heard is written into that same last line as the song goes
 * on ([updateListened]). The name did not change, so backups, profile copies
 * and the profile switch find it where they always did; [readHistory] still
 * reads the old array (an older install, a backup taken before this), and the
 * first write after that converts it. Backups are written as the array
 * ([exportForBackup]) so a backup taken here restores on an older build too.
 *
 * Reads are cached per file and invalidated by its size and modification time,
 * so the half-dozen screens that read it do not each parse the whole history.
 *
 * The file is resolved on every call rather than captured, because an
 * instance outlives a profile switch - and resolved once per locked operation,
 * so a read-modify-write cannot read one profile's file and write another's.
 */
class StatsRepository(private val context: Context) {

    private fun historyFile(): File =
        historyFileFor(context, ProfileManager.requireActiveProfileId(context))

    /**
     * Record a play. Returns the entry written, so the caller can keep its
     * measured time current with [updateListened]; null when nothing was
     * written (incognito, the debounce, a failed write).
     */
    suspend fun addPlayEvent(song: Song, listenedMs: Long? = null): PlayHistoryEntry? = withContext(Dispatchers.IO) {
        // Incognito leaves no listening record, which is what makes the stats
        // screen and "recently played" honest about the session it describes.
        if (IncognitoMode.isEnabled(context)) return@withContext null
        mutex.withLock {
            try {
                val historyFile = historyFile()
                val history = readHistory(historyFile)

                // DEBOUNCE: Don't add the same song if it was added less than 10 seconds ago
                // This prevents duplicate entries from media item resolution loops.
                val lastEntry = history.firstOrNull()
                if (lastEntry?.songId == song.id && (System.currentTimeMillis() - lastEntry.timestamp) < 10000L) {
                    KLog.d(TAG, "Stats: Debouncing duplicate play event for ${song.title}")
                    return@withLock null
                }

                val entry = PlayHistoryEntry(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    timestamp = System.currentTimeMillis(),
                    duration = song.duration,
                    thumbnailUrl = song.thumbnailUrl ?: song.albumArtUri?.toString(),
                    source = song.source,
                    albumId = song.albumId,
                    listenedMs = listenedMs,
                )
                appendLine(historyFile, entry, history)
                entry
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e(TAG, "Error saving play event", e)
                null
            }
        }
    }

    /**
     * Bring a recorded play's measured time up to date as the song goes on.
     *
     * The play being heard is the last line of the file almost always, so the
     * line is replaced in place and nothing else is touched. Anything else -
     * the line is not the last one because the history was edited meanwhile,
     * the file is still the old array - goes through a full rewrite.
     */
    suspend fun updateListened(songId: String, timestamp: Long, listenedMs: Long) = withContext(Dispatchers.IO) {
        if (IncognitoMode.isEnabled(context)) return@withContext
        mutex.withLock {
            try {
                val historyFile = historyFile()
                if (!historyFile.exists()) return@withLock
                if (!replaceLastLine(historyFile, songId, timestamp, listenedMs)) {
                    val history = readHistory(historyFile)
                    val index = history.indexOfFirst { it.songId == songId && it.timestamp == timestamp }
                    if (index < 0) return@withLock
                    val updated = history.toMutableList()
                    updated[index] = updated[index].copy(listenedMs = listenedMs)
                    writeHistory(historyFile, updated)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e(TAG, "Error updating listened time", e)
            }
        }
    }

    /** Every play, newest first. */
    suspend fun loadHistory(): List<PlayHistoryEntry> = withContext(Dispatchers.IO) {
        readHistory(historyFile())
    }

    suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.Default) {
        val history = loadHistory()
        if (history.isEmpty()) return@withContext GlobalStats()
        // Measured time where it was measured, the song's length before that.
        val totalPlayTime = history.sumOf { it.heardMs / 1000 }
        val totalPlays = history.size
        val songStats = history.groupBy { it.songId }.map { (id, entries) ->
            val first = entries.first()
            SongStats(
                songId = id,
                title = first.title,
                artist = first.artist,
                thumbnailUrl = first.thumbnailUrl,
                playCount = entries.size,
                totalPlayTime = entries.sumOf { it.heardMs },
                album = first.album,
                durationMs = first.duration,
                source = first.source
            )
        }.sortedByDescending { it.playCount }
        val artistStats = history.groupBy { it.artist }.map { (name, entries) ->
            ArtistStats(
                name = name,
                playCount = entries.size,
                songCount = entries.distinctBy { it.songId }.size
            )
        }.sortedByDescending { it.playCount }
        // Listening streaks: consecutive local-time days with at least one play.
        val playDays = history.map { localDayOf(it.timestamp) }.toSortedSet()
        var longestStreak = 0
        var run = 0
        var prevDay: Long? = null
        for (day in playDays) {
            run = if (prevDay != null && day == prevDay + 1) run + 1 else 1
            if (run > longestStreak) longestStreak = run
            prevDay = day
        }
        // Current streak stays alive if the last play was today OR yesterday
        val today = localDayOf(System.currentTimeMillis())
        var currentStreak = 0
        var cursor = if (today in playDays) today else today - 1
        while (cursor in playDays) {
            currentStreak++
            cursor--
        }

        GlobalStats(
            totalPlays = totalPlays,
            totalPlayTimeSeconds = totalPlayTime,
            topSongs = songStats.take(10),
            topArtists = artistStats.take(10),
            uniqueArtists = artistStats.size,
            uniqueSongs = songStats.size,
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak
        )
    }

    /** Epoch day in the device's local timezone. */
    private fun localDayOf(timestamp: Long): Long =
        (timestamp + java.util.TimeZone.getDefault().getOffset(timestamp)) / 86_400_000L

    suspend fun getMonthlyPlays(): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistory()
        val calendar = Calendar.getInstance()
        history.groupBy {
            calendar.timeInMillis = it.timestamp
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)
            "$year-${month.toString().padStart(2, '0')}"
        }.mapValues { it.value.size }
    }

    suspend fun getDailyPlays(daysLimit: Int = 7): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistory()
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        history.filter { now - it.timestamp < daysLimit * 24 * 60 * 60 * 1000L }
            .groupBy {
                calendar.timeInMillis = it.timestamp
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val month = calendar.get(Calendar.MONTH) + 1
                "$month/$day"
            }.mapValues { it.value.size }
    }

    /**
     * Drop a single play.
     *
     * Identity is the song plus the instant it was played: entries carry no id
     * of their own, and [addPlayEvent] already debounces repeats of the same
     * song inside ten seconds, so the pair cannot collide in practice.
     *
     * Returns the surviving history so the caller can publish it without a
     * second read - the screen doing the removing is showing the list.
     */
    suspend fun removeEntry(songId: String, timestamp: Long): List<PlayHistoryEntry> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val historyFile = historyFile()
                val remaining = readHistory(historyFile).filterNot {
                    it.songId == songId && it.timestamp == timestamp
                }
                writeHistory(historyFile, remaining)
                remaining
            }
        }

    /**
     * Drop every play of one song. The list-level version of [removeEntry], for
     * a song that has been on repeat and owns half the log.
     */
    suspend fun removeAllPlaysOf(songId: String): List<PlayHistoryEntry> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val historyFile = historyFile()
                val remaining = readHistory(historyFile).filterNot { it.songId == songId }
                writeHistory(historyFile, remaining)
                remaining
            }
        }

    /**
     * Put entries back exactly as they were, newest first.
     *
     * Undo restores the whole list rather than re-inserting one entry, because
     * a removal can take a run of plays with it and the order has to come back
     * unchanged. Callers hold the pre-removal list; this only writes it.
     */
    suspend fun restoreHistory(entries: List<PlayHistoryEntry>) = withContext(Dispatchers.IO) {
        mutex.withLock { writeHistory(historyFile(), entries) }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val historyFile = historyFile()
            if (historyFile.exists()) historyFile.delete()
            forget(historyFile)
        }
    }

    companion object {
        internal const val HISTORY_FILE_BASE = "play_history"
        private const val TAG = "StatsRepository"

        /**
         * One lock for every instance. Five classes each build their own
         * repository over the same file, and a lock per instance let the
         * player's write and the history screen's removal interleave.
         */
        private val mutex = Mutex()

        /** Compact, one entry per line. Never pretty-printed: a line is a play. */
        private val lineJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        /**
         * The last parse of each file, newest first, keyed by what would change
         * if anything wrote to it. Guarded by [cacheLock]; the lists are never
         * mutated once cached.
         */
        private data class Parsed(val length: Long, val modified: Long, val entries: List<PlayHistoryEntry>)
        private val cache = HashMap<String, Parsed>()
        private val cacheLock = Any()

        private fun cached(file: File): List<PlayHistoryEntry>? = synchronized(cacheLock) {
            val hit = cache[file.absolutePath] ?: return null
            if (hit.length == file.length() && hit.modified == file.lastModified()) hit.entries else null
        }

        private fun remember(file: File, entries: List<PlayHistoryEntry>) {
            synchronized(cacheLock) {
                cache[file.absolutePath] = Parsed(file.length(), file.lastModified(), entries)
            }
        }

        private fun forget(file: File) {
            synchronized(cacheLock) { cache.remove(file.absolutePath) }
        }

        /**
         * Every play in [file], newest first, whichever format it is in: the
         * old JSON array (newest first) or one play per line (oldest first). A
         * line that does not parse - a write cut off by a crash - is skipped
         * rather than costing the whole history.
         */
        internal fun readHistory(file: File): List<PlayHistoryEntry> {
            if (!file.exists()) return emptyList()
            cached(file)?.let { return it }
            return try {
                val text = file.readText()
                val entries = if (text.trimStart().startsWith("[")) {
                    lineJson.decodeFromString<List<PlayHistoryEntry>>(text)
                } else {
                    text.lineSequence()
                        .filter { it.isNotBlank() }
                        .mapNotNull { line ->
                            try {
                                lineJson.decodeFromString<PlayHistoryEntry>(line)
                            } catch (e: Exception) {
                                KLog.w(TAG, "Skipping an unreadable history line")
                                null
                            }
                        }
                        .toList()
                        .asReversed()
                        .toList()
                }
                remember(file, entries)
                entries
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e(TAG, "Error loading history", e)
                emptyList()
            }
        }

        /**
         * Append [entry] as one line. [current] is the history as just read
         * (newest first); an old array file is converted to lines first.
         * Caller must hold [mutex].
         */
        private fun appendLine(file: File, entry: PlayHistoryEntry, current: List<PlayHistoryEntry>) {
            val isArray = file.exists() && file.length() > 0 && file.inputStream().use { input ->
                var c: Int
                do { c = input.read() } while (c != -1 && Character.isWhitespace(c))
                c == '['.code
            }
            if (isArray) {
                writeHistory(file, listOf(entry) + current)
                return
            }
            file.appendText(lineJson.encodeToString(entry) + "\n")
            remember(file, listOf(entry) + current)
        }

        /**
         * Rewrite the file's last line in place when it is the play [songId] at
         * [timestamp]. False when it is not, so the caller falls back to a
         * rewrite. Caller must hold [mutex].
         */
        private fun replaceLastLine(file: File, songId: String, timestamp: Long, listenedMs: Long): Boolean {
            RandomAccessFile(file, "rw").use { raf ->
                val length = raf.length()
                if (length < 2) return false
                // Find the start of the last line: the byte after the newline
                // that precedes the final one.
                var pos = length - 2
                val chunk = ByteArray(4096)
                var lineStart = 0L
                search@ while (pos >= 0) {
                    val from = maxOf(0L, pos - chunk.size + 1)
                    val count = (pos - from + 1).toInt()
                    raf.seek(from)
                    raf.readFully(chunk, 0, count)
                    for (i in count - 1 downTo 0) {
                        if (chunk[i] == '\n'.code.toByte()) {
                            lineStart = from + i + 1
                            break@search
                        }
                    }
                    pos = from - 1
                }
                val lineBytes = ByteArray((length - lineStart).toInt())
                raf.seek(lineStart)
                raf.readFully(lineBytes)
                val line = String(lineBytes, Charsets.UTF_8).trim()
                if (line.startsWith("[") || line.endsWith("]")) return false
                val entry = try {
                    lineJson.decodeFromString<PlayHistoryEntry>(line)
                } catch (e: Exception) {
                    return false
                }
                if (entry.songId != songId || entry.timestamp != timestamp) return false
                val updated = entry.copy(listenedMs = listenedMs)
                raf.setLength(lineStart)
                raf.seek(lineStart)
                raf.write((lineJson.encodeToString(updated) + "\n").toByteArray(Charsets.UTF_8))
                val previous = cached(file)
                forget(file)
                // The cached list (newest first) has this play at its head.
                previous?.let { list ->
                    if (list.firstOrNull()?.let { it.songId == songId && it.timestamp == timestamp } == true) {
                        remember(file, listOf(updated) + list.drop(1))
                    }
                }
                return true
            }
        }

        /** Write [entries] (newest first) as lines, oldest first. Caller must hold [mutex]. */
        private fun writeHistory(historyFile: File, entries: List<PlayHistoryEntry>) {
            try {
                if (entries.isEmpty()) {
                    if (historyFile.exists()) historyFile.delete()
                    forget(historyFile)
                } else {
                    // Written beside and moved over, so a crash mid-write leaves
                    // the old history rather than half of the new one.
                    val partial = File(historyFile.parentFile, historyFile.name + ".partial")
                    partial.bufferedWriter().use { out ->
                        for (i in entries.indices.reversed()) {
                            out.write(lineJson.encodeToString(entries[i]))
                            out.write("\n")
                        }
                    }
                    if (!partial.renameTo(historyFile)) {
                        historyFile.delete()
                        partial.renameTo(historyFile)
                    }
                    remember(historyFile, entries)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e(TAG, "Error writing history", e)
                forget(historyFile)
            }
        }

        /**
         * The history in [file] as the JSON array (newest first) every build
         * has read, for a backup: one taken here then restores on an older
         * build too, which would not read the line format.
         */
        internal fun exportForBackup(file: File): String? {
            if (!file.exists()) return null
            return lineJson.encodeToString(readHistory(file))
        }

        /** [profileId]'s history file, for backups and a new profile's copy. */
        internal fun historyFileFor(context: Context, profileId: String): File = File(
            context.applicationContext.filesDir,
            ProfileManager.historyScopedFileName(HISTORY_FILE_BASE, "json", profileId, context)
        )

        /** Give [toProfileId] a copy of [fromProfileId]'s history, if it has none. */
        internal fun copyProfileData(context: Context, fromProfileId: String, toProfileId: String) {
            ProfileManager.copyScopedFile(
                historyFileFor(context, fromProfileId),
                historyFileFor(context, toProfileId)
            )
        }
    }
}

data class GlobalStats(
    val totalPlays: Int = 0,
    val totalPlayTimeSeconds: Long = 0,
    val topSongs: List<SongStats> = emptyList(),
    val topArtists: List<ArtistStats> = emptyList(),
    val uniqueArtists: Int = 0,
    val uniqueSongs: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0
)
