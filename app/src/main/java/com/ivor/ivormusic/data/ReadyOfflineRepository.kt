package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The songs that are already sitting in the stream cache in full, so they play
 * with no network at all.
 *
 * **This is a state, not a collection.** Nothing here was put here on purpose:
 * a song is in the cache because it was played, and it leaves because
 * [CacheManager]'s LRU evictor needed the room for something played since. So
 * the list changes without the user touching it, which is exactly why it must
 * never be dressed as a playlist they own - contents quietly disappearing from
 * something that looks like a playlist reads as data loss. It is presented as
 * "Ready offline", and the way to keep something is to download it, which
 * promotes it out of this list and into [DownloadRepository] where it is
 * permanent.
 *
 * Nothing new is written to make this work. Three things that already exist
 * line up: cache keys are song ids (playback sets the custom cache key in
 * `MusicService.buildMediaItemWithUri`), [CacheManager.fullyCachedEntries]
 * already separates a whole song from one of `warmStreamCache`'s 512 KB heads,
 * and [StatsRepository]'s play history carries the title, artist, album,
 * duration and artwork needed to rebuild a [Song]. There is deliberately no
 * fourth store: a metadata sidecar written at play time would be a second copy
 * of what the history already holds, and one more thing to keep in step.
 *
 * The cost of that reuse is stated in [Result.historyDisabled]: with "save
 * listening history" off there is nothing to resolve the ids against, so the
 * list is empty however full the cache is. The Library says so in place of the
 * entry point rather than hiding, because "you turned off the thing this needs"
 * and "you have nothing cached" are the same empty list but different problems
 * with different fixes.
 */
class ReadyOfflineRepository(context: Context) {

    private val appContext = context.applicationContext
    private val statsRepository = StatsRepository(appContext)
    private val themePreferences = ThemePreferences(appContext)

    /**
     * @param songs newest-played first, which is the order they are most
     *   likely to be wanted in and the reverse of the order they will be
     *   evicted in.
     * @param totalBytes what the listed songs occupy on disk. Not the whole
     *   cache: partial entries and video streams live there too, and quoting
     *   the cache's total next to a list that excludes them would not add up.
     * @param historyDisabled true when the list is empty only because the
     *   play history it resolves ids against is turned off.
     */
    data class Result(
        val songs: List<Song> = emptyList(),
        val totalBytes: Long = 0L,
        val historyDisabled: Boolean = false,
        /**
         * Cached songs that could not be named, because no play history entry
         * matched them. Carried so the empty case can tell "nothing is cached"
         * apart from "plenty is cached and this cannot say what" - which are
         * the same empty list but different problems with different fixes.
         */
        val unnamedCount: Int = 0
    )

    /**
     * @param downloadedIds ids that already have a permanent copy. Excluded
     *   because a downloaded song is offline for good and is listed on the
     *   Downloads screen; showing it here as well would present one song as
     *   two kinds of offline and make the temporary list look safer than it is.
     */
    suspend fun load(downloadedIds: Set<String>): Result = withContext(Dispatchers.IO) {
        val cached = CacheManager.fullyCachedEntries()
        // Nothing cached is nothing to explain: an empty cache is the honest
        // empty case whatever the history setting says.
        if (cached.isEmpty()) return@withContext Result()

        val candidates = cached.keys.count { it !in downloadedIds }
        val history = statsRepository.loadHistory()
        if (history.isEmpty()) {
            return@withContext Result(
                historyDisabled = !themePreferences.isSaveMusicHistoryEnabled(),
                unnamedCount = candidates
            )
        }

        // Newest play wins: a song's title or artwork can be re-fetched with
        // better metadata later, and the most recent entry is the closest to
        // what the rest of the app is currently showing for it.
        val newestById = HashMap<String, PlayHistoryEntry>(history.size)
        for (entry in history) {
            val existing = newestById[entry.songId]
            if (existing == null || entry.timestamp > existing.timestamp) {
                newestById[entry.songId] = entry
            }
        }

        val songs = cached.keys
            .asSequence()
            .filter { it !in downloadedIds }
            .mapNotNull { id -> newestById[id] }
            // A local file was never streamed, so an id of one appearing here
            // would be a collision rather than a cached song.
            .filter { it.source != SongSource.LOCAL }
            .sortedByDescending { it.timestamp }
            .map { entry ->
                Song(
                    id = entry.songId,
                    title = entry.title,
                    artist = entry.artist,
                    album = entry.album,
                    duration = entry.duration,
                    thumbnailUrl = entry.thumbnailUrl,
                    source = entry.source
                )
            }
            .toList()

        Result(
            songs = songs,
            totalBytes = songs.sumOf { cached[it.id] ?: 0L },
            // Only worth saying when it explains an empty list. With songs on
            // screen the setting is beside the point.
            historyDisabled = songs.isEmpty() && !themePreferences.isSaveMusicHistoryEnabled(),
            unnamedCount = candidates - songs.size
        )
    }
}
