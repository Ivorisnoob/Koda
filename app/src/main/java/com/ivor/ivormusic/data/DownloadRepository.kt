package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.ivor.ivormusic.service.DownloadService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    NOT_DOWNLOADED,

    /** Accepted and waiting for the worker; downloads run one at a time. */
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
    LOCAL_ORIGINAL
}

/**
 * One queued or in-flight download, of either media type.
 *
 * Music carries its originating [Song] so completion can reuse it verbatim;
 * video has no equivalent domain object to preserve, so the display fields on
 * the request itself are the whole story.
 *
 * [qualityLabel] is the video quality the user picked in the download sheet
 * (null = the stored default). It lives on the request so retries — which
 * re-resolve stream URLs from scratch — keep honoring the original choice.
 */
data class DownloadRequest(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: DownloadMediaType,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0,
    val song: Song? = null,
    val qualityLabel: String? = null
) {
    val isVideo: Boolean get() = type == DownloadMediaType.VIDEO
}

data class DownloadProgress(
    val songId: String,
    val request: DownloadRequest,
    val progress: Float, // 0.0 to 1.0
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0
)

/** A completed video download. */
data class DownloadedVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val uri: Uri,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0,
    val quality: String? = null
)

/**
 * Owns download state for the whole process.
 *
 * This is a singleton on purpose. Every consumer used to construct its own
 * instance, and since all download state lives in per-instance StateFlows, the
 * instances never agreed: starting a download from the home screen updated the
 * home ViewModel's copy while the Downloads screen watched the player
 * ViewModel's, so the transfer was invisible there and the finished song only
 * turned up after an app restart. One instance means one set of flows and one
 * source of truth.
 *
 * The constructor is private so that failure mode cannot be reintroduced by
 * calling `DownloadRepository(context)` again.
 */
class DownloadRepository private constructor(private val context: Context) {
    companion object {
        private const val TAG = "DownloadRepository"

        /**
         * Attempts per download. Each one re-resolves the stream URL, which is
         * what actually rescues an expired googlevideo link.
         */
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BACKOFF_MS = 2_000L
        private const val BUFFER_SIZE = 8192
        private const val DOWNLOAD_ARTWORK_SIZE_PX = 1024
        private const val DOWNLOAD_ARTWORK_JPEG_QUALITY = 92

        // Ranged-request chunk size. Bounded ranges are served at full CDN
        // speed where an open-ended request is paced to the media bitrate.
        private const val DOWNLOAD_CHUNK_BYTES = 10L * 1024 * 1024

        /** How long a finished download stays visible in the progress list. */
        private const val COMPLETION_LINGER_MS = 1_500L

        @Volatile
        private var INSTANCE: DownloadRepository? = null

        /**
         * The process-wide instance. Always built against the application
         * context so holding it from a Service or ViewModel cannot leak an
         * Activity.
         */
        fun getInstance(context: Context): DownloadRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            if (ThemePreferences.isLocalOnly(context)) {
                throw java.io.IOException("Local only mode is on: network disabled")
            }
            chain.proceed(chain.request())
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val youtubeRepository = YouTubeRepository(context)
    private val lyricsRepository = LyricsRepository()
    private val notificationHelper = DownloadNotificationHelper(context)
    private val storage = DownloadStorage(context)

    private val downloadsFile = File(context.filesDir, "downloaded_songs_metadata.json")
    private val videosFile = File(context.filesDir, "downloaded_videos_metadata.json")

    /**
     * Scope for work that outlives a single call, currently only the one-time
     * storage migration. Repository instances are never explicitly disposed, so
     * this deliberately has no cancellation hook.
     */
    private val repositoryScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    // Real-time progress tracking for each active download
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val _downloadedVideos = MutableStateFlow<List<DownloadedVideo>>(emptyList())
    val downloadedVideos: StateFlow<List<DownloadedVideo>> = _downloadedVideos.asStateFlow()

    // Requests accepted but not yet started. Downloads run serially, so this is
    // the real backlog rather than a snapshot of a batch.
    private val _downloadQueue = MutableStateFlow<List<DownloadRequest>>(emptyList())
    val downloadQueue: StateFlow<List<DownloadRequest>> = _downloadQueue.asStateFlow()

    /** Guards queue mutations and [activeId] so enqueue and drain agree. */
    private val queueMutex = kotlinx.coroutines.sync.Mutex()

    private val workerLock = Any()

    @Volatile
    private var workerRunning = false

    /** The job running the current transfer, cancelled per-download. */
    @Volatile
    private var activeJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var activeId: String? = null

    // Track active download calls for cancellation. Concurrent because
    // downloads run on Dispatchers.IO while cancellation arrives from the main
    // thread; a plain LinkedHashMap here was a data race.
    private val activeDownloadCalls = java.util.concurrent.ConcurrentHashMap<String, okhttp3.Call>()

    init {
        // Read whatever is on disk right away so consumers that touch
        // downloadedSongs.value immediately (MusicService does) are not racing
        // an empty list, then migrate in the background and reload.
        loadDownloadedSongs()
        loadDownloadedVideos()
        repositoryScope.launch {
            if (DownloadMigration.migrateIfNeeded(context)) {
                loadDownloadedSongs()
            }
        }
    }

    /**
     * Whether this exact media type is already downloaded.
     *
     * Music and video share an id namespace (both are YouTube video ids), so
     * the check has to be per-type: having the audio does not mean the user
     * already has the video. The queue's own id check still prevents the two
     * running at once, so they simply happen in sequence.
     */
    private fun isDownloadedOfType(id: String, type: DownloadMediaType): Boolean =
        when (type) {
            DownloadMediaType.MUSIC -> _downloadedSongs.value.any { it.id == id }
            DownloadMediaType.VIDEO -> _downloadedVideos.value.any { it.id == id }
        }

    /**
     * Hydrate from the metadata sidecar, dropping anything whose file is gone.
     *
     * Two entry shapes are accepted on purpose: `mediaUri` for downloads in
     * shared storage, and the legacy `localPath` for entries that have not been
     * migrated yet. Keeping both readable means the list works before, during
     * and after [DownloadMigration] runs.
     *
     * Existence is reconciled against MediaStore in a single query rather than
     * trusted, because these files are now user-visible and can be deleted from
     * the Files app behind our back.
     */
    private fun loadDownloadedSongs() {
        if (!downloadsFile.exists()) {
            _downloadedSongs.value = emptyList()
            return
        }

        try {
            val jsonArray = JSONArray(downloadsFile.readText())
            val liveUris = storage.listExisting(DownloadMediaType.MUSIC).keys
            val songs = mutableListOf<Song>()
            var prunedAny = false
            var backfilledAny = false
            var companionStateChanged = false
            // Entries written before downloads carried a timestamp get one
            // synthesized from their position: the array is in completion
            // order, so walking backwards from the metadata file's mtime keeps
            // their relative order while placing them all before any new
            // download. saveMetadata() below persists it, so this runs once.
            val backfillAnchor = downloadsFile.lastModified().takeIf { it > 0 }
                ?: System.currentTimeMillis()
            val entryCount = jsonArray.length()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue

                val mediaUri = obj.optString("mediaUri").takeIf { it.isNotBlank() }
                val legacyPath = obj.optString("localPath").takeIf { it.isNotBlank() }
                val storedArtUri = obj.optString("albumArtUri")
                    .takeIf(String::isNotBlank)
                    ?.let(Uri::parse)
                val storedLyricsUri = obj.optString("lyricsUri")
                    .takeIf(String::isNotBlank)
                    ?.let(Uri::parse)

                val uri: Uri? = when {
                    mediaUri != null -> Uri.parse(mediaUri).takeIf { it in liveUris }
                    legacyPath != null -> File(legacyPath).takeIf { it.exists() }?.let(Uri::fromFile)
                    else -> null
                }

                if (uri == null) {
                    listOfNotNull(storedArtUri, storedLyricsUri)
                        .filter(storage::isMusicCompanion)
                        .forEach(storage::delete)
                    prunedAny = true
                    continue
                }

                val artUrl = obj.optString("albumArtUrl").takeIf {
                    it.isNotBlank() && !obj.isNull("albumArtUrl")
                }?.takeIf { Uri.parse(it).scheme in setOf("http", "https") }
                val localArtUri = storedArtUri?.takeIf { it in liveUris }
                val lyricsUri = storedLyricsUri?.takeIf { it in liveUris }
                if (storedArtUri != null && localArtUri == null) companionStateChanged = true
                if (storedLyricsUri != null && lyricsUri == null) companionStateChanged = true
                val addedAt = obj.optLong("addedAt").takeIf { it > 0 } ?: run {
                    backfilledAny = true
                    backfillAnchor - (entryCount - i) * 1000L
                }
                songs.add(
                    Song(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        album = obj.optString("album", ""),
                        duration = obj.optLong("duration"),
                        uri = uri,
                        albumArtUri = localArtUri ?: artUrl?.let(Uri::parse),
                        // A local companion must win before any network URL or
                        // artwork disappears exactly when the user goes offline.
                        thumbnailUrl = localArtUri?.toString() ?: artUrl,
                        source = SongSource.LOCAL,
                        lyricsUri = lyricsUri,
                        dateAdded = addedAt
                    )
                )
            }

            _downloadedSongs.value = songs
            // Persist the pruned list so externally deleted files do not get
            // re-checked on every launch, and so backfilled timestamps stick.
            if (prunedAny || backfilledAny || companionStateChanged) saveMetadata()
        } catch (e: Exception) {
            KLog.e(TAG, "Error loading downloads", e)
            _downloadedSongs.value = emptyList()
        }
    }

    private fun saveMetadata() {
        try {
            val jsonArray = JSONArray()
            _downloadedSongs.value.forEach { song ->
                val uri = song.uri ?: return@forEach
                val obj = JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                    put("duration", song.duration)
                    // Downloads live in MediaStore now; a file:// uri here only
                    // happens for entries awaiting migration.
                    if (uri.scheme == "file") {
                        put("localPath", uri.path)
                    } else {
                        put("mediaUri", uri.toString())
                    }
                    song.thumbnailUrl?.takeIf { url ->
                        Uri.parse(url).scheme in setOf("http", "https")
                    }?.let { put("albumArtUrl", it) }
                    song.albumArtUri?.takeIf { it.scheme == "content" }?.let {
                        put("albumArtUri", it.toString())
                    }
                    song.lyricsUri?.let { put("lyricsUri", it.toString()) }
                    song.dateAdded?.let { put("addedAt", it) }
                }
                jsonArray.put(obj)
            }
            downloadsFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            KLog.e(TAG, "Error saving metadata", e)
        }
    }

    /** Load completed video downloads, reconciled against MediaStore. */
    private fun loadDownloadedVideos() {
        if (!videosFile.exists()) {
            _downloadedVideos.value = emptyList()
            return
        }

        try {
            val jsonArray = JSONArray(videosFile.readText())
            val liveUris = storage.listExisting(DownloadMediaType.VIDEO).keys
            val videos = mutableListOf<DownloadedVideo>()
            var prunedAny = false

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val uri = obj.optString("mediaUri").takeIf { it.isNotBlank() }
                    ?.let(Uri::parse)
                    ?.takeIf { it in liveUris }

                if (uri == null) {
                    prunedAny = true
                    continue
                }

                videos.add(
                    DownloadedVideo(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        channelName = obj.optString("channelName"),
                        uri = uri,
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        durationMs = obj.optLong("durationMs"),
                        quality = obj.optString("quality").takeIf { it.isNotBlank() }
                    )
                )
            }

            _downloadedVideos.value = videos
            if (prunedAny) saveVideoMetadata()
        } catch (e: Exception) {
            KLog.e(TAG, "Error loading video downloads", e)
            _downloadedVideos.value = emptyList()
        }
    }

    private fun saveVideoMetadata() {
        try {
            val jsonArray = JSONArray()
            _downloadedVideos.value.forEach { video ->
                jsonArray.put(
                    JSONObject().apply {
                        put("id", video.id)
                        put("title", video.title)
                        put("channelName", video.channelName)
                        put("mediaUri", video.uri.toString())
                        put("thumbnailUrl", video.thumbnailUrl)
                        put("durationMs", video.durationMs)
                        put("quality", video.quality)
                    }
                )
            }
            videosFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            KLog.e(TAG, "Error saving video metadata", e)
        }
    }

    /**
     * Publish progress for one download.
     *
     * The transfer loop calls this on every 8KB buffer, which is hundreds of
     * times a second. Emitting all of them would recompose the Downloads screen
     * just as often for changes too small to see, so an update is dropped
     * unless the whole-percent value or the status actually changed. That takes
     * a download from thousands of emissions to about a hundred, and the
     * terminal states always get through because their status differs.
     */
    private fun updateProgress(
        request: DownloadRequest,
        progress: Float,
        status: DownloadStatus,
        bytesDownloaded: Long = 0,
        totalBytes: Long = 0
    ) {
        val previous = _downloadProgress.value[request.id]
        if (previous != null &&
            previous.status == status &&
            (previous.progress * 100).toInt() == (progress * 100).toInt()
        ) {
            return
        }

        val current = _downloadProgress.value.toMutableMap()
        current[request.id] =
            DownloadProgress(request.id, request, progress, status, bytesDownloaded, totalBytes)
        _downloadProgress.value = current

        // Terminal states get their own one-shot notification. Progress itself
        // is not posted here: it belongs to DownloadService, which owns the
        // foreground notification and observes these flows.
        when (status) {
            DownloadStatus.DOWNLOADED -> notificationHelper.showDownloadComplete(
                songId = request.id,
                songTitle = request.title,
                artistName = request.subtitle,
                // Already in memory from the progress notification, which spent
                // the whole download displaying it.
                artwork = NotificationArtworkLoader.cached(request.thumbnailUrl)
            )
            DownloadStatus.FAILED -> notificationHelper.showDownloadFailed(
                songId = request.id,
                songTitle = request.title
            )
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

    // --- Queue and worker ---------------------------------------------------

    /**
     * Queue a song. Kept `suspend` so existing call sites are unchanged, but it
     * no longer performs the transfer: it enqueues and returns. Previously the
     * transfer ran in the caller's scope, so a download started from a screen
     * died when that screen's ViewModel was cleared, leaving a half-written
     * file behind.
     */
    suspend fun downloadSong(song: Song) {
        enqueue(listOf(song.toRequest()))
    }

    /**
     * Queue a whole playlist. Songs already downloaded or already queued are
     * skipped, so re-running this over a partially downloaded playlist only
     * fetches what is missing. Device-local originals are already offline and
     * must never be routed through YouTube stream resolution.
     */
    suspend fun downloadPlaylist(songs: List<Song>) {
        enqueue(
            songs.asSequence()
                .filter { it.source == SongSource.YOUTUBE }
                .distinctBy { it.id }
                .map { it.toRequest() }
                .toList()
        )
    }

    /**
     * Queue a video download. [qualityLabel] pins the quality picked in the
     * download sheet; null defers to the stored default at transfer time.
     */
    suspend fun downloadVideo(video: VideoItem, qualityLabel: String? = null) {
        enqueue(listOf(video.toRequest(qualityLabel)))
    }

    /**
     * Queue several videos at once with one quality cap for the batch.
     *
     * Live broadcasts only expose an HLS manifest and cannot be remuxed by the
     * offline MP4 path. They are skipped here as a final data-layer guard even
     * though the playlist sheet also explains the skip before confirmation.
     */
    suspend fun downloadVideos(videos: List<VideoItem>, qualityLabel: String? = null) {
        enqueue(
            videos.asSequence()
                .filterNot { it.isLive }
                .distinctBy { it.videoId }
                .map { it.toRequest(qualityLabel) }
                .toList()
        )
    }

    /**
     * Resolve and queue a complete video playlist.
     *
     * A local playlist already carries its whole copied list. A YouTube
     * playlist is normally loaded as one page for responsive browsing, so a
     * batch explicitly follows every continuation here. False means neither
     * independent resolver reached the real end; no partial batch is queued.
     */
    suspend fun downloadVideoPlaylist(
        playlistId: String,
        loadedVideos: List<VideoItem>,
        qualityLabel: String? = null
    ): Boolean {
        val completeVideos = if (LocalVideoPlaylistsRepository.isLocal(playlistId)) {
            loadedVideos
        } else {
            youtubeRepository.getCompletePlaylistVideos(playlistId) ?: return false
        }
        downloadVideos(completeVideos, qualityLabel)
        return true
    }

    private fun Song.toRequest() = DownloadRequest(
        id = id,
        title = title,
        subtitle = artist,
        type = DownloadMediaType.MUSIC,
        thumbnailUrl = highResThumbnailUrl ?: thumbnailUrl ?: albumArtUri?.toString(),
        durationMs = duration,
        song = this
    )

    private fun VideoItem.toRequest(qualityLabel: String? = null) = DownloadRequest(
        id = videoId,
        title = title,
        subtitle = channelName,
        type = DownloadMediaType.VIDEO,
        thumbnailUrl = thumbnailUrl,
        // VideoItem.duration is seconds; everything downstream works in millis.
        durationMs = duration * 1000,
        qualityLabel = qualityLabel
    )

    private suspend fun enqueue(requests: List<DownloadRequest>) {
        val accepted = queueMutex.withLock {
            val existing = _downloadQueue.value.map { it.id }.toSet()
            val additions = requests.filter { request ->
                request.id.isNotBlank() &&
                    !isDownloadedOfType(request.id, request.type) &&
                    request.id !in existing &&
                    request.id != activeId
            }.distinctBy { it.id }

            if (additions.isNotEmpty()) {
                _downloadQueue.value = _downloadQueue.value + additions
                _downloadingIds.value = _downloadingIds.value + additions.map { it.id }
                additions.forEach { updateProgress(it, 0f, DownloadStatus.QUEUED) }
            }
            additions
        }

        if (accepted.isNotEmpty()) ensureWorker()
    }

    /**
     * Start the drain loop if it is not already running.
     *
     * The re-check in the `finally` closes the race where a request is enqueued
     * just as the loop decides the queue is empty: rather than locking around
     * the whole drain, the loop re-arms itself if anything arrived on the way
     * out.
     */
    private fun ensureWorker() {
        synchronized(workerLock) {
            if (workerRunning) return
            workerRunning = true
        }

        repositoryScope.launch {
            try {
                drainQueue()
            } finally {
                synchronized(workerLock) { workerRunning = false }
                if (_downloadQueue.value.isNotEmpty()) ensureWorker()
            }
        }
    }

    private suspend fun drainQueue() {
        DownloadService.start(context)
        try {
            while (true) {
                val next = queueMutex.withLock {
                    _downloadQueue.value.firstOrNull()?.also {
                        _downloadQueue.value = _downloadQueue.value.drop(1)
                        activeId = it.id
                    }
                } ?: break

                // Each task is its own child job so cancelling one download
                // does not tear down the queue. join() returns normally when
                // the child is cancelled, so the loop simply moves on.
                val job = repositoryScope.launch { runTask(next) }
                activeJob = job
                job.join()

                activeJob = null
                queueMutex.withLock { activeId = null }
            }
        } finally {
            DownloadService.stop(context)
        }
    }

    /**
     * Run one download, retrying transient failures.
     *
     * Each attempt re-resolves the stream URL rather than reusing the previous
     * one. googlevideo URLs expire, so a retry against a stale URL would fail
     * exactly like the attempt before it.
     *
     * Re-resolving is not enough on its own when googlevideo answered 403: the
     * refusal is keyed on the `visitorData` the /player call carried, so every
     * attempt would rebuild an equally dead URL under the same flagged token and
     * the download would fail three times in a row for the whole 6h TTL. The
     * players re-mint on exactly this signal; without the same call here a
     * download is only ever repaired as a side effect of playing something else.
     */
    private suspend fun runTask(request: DownloadRequest) {
        var lastError: Throwable? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                val ok = if (request.isVideo) {
                    attemptVideoDownload(request)
                } else {
                    attemptMusicDownload(request)
                }
                if (ok) return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                KLog.w(TAG, "Attempt $attempt/$MAX_ATTEMPTS failed for ${request.title}: ${e.message}")
            }

            if (attempt < MAX_ATTEMPTS) {
                if (isMediaForbidden(lastError)) {
                    KLog.w(TAG, "googlevideo refused the media; re-minting visitorData before retry")
                    youtubeRepository.refreshVisitorDataAfterPlaybackFailure()
                }
                kotlinx.coroutines.delay(RETRY_BACKOFF_MS * attempt)
            }
        }

        KLog.e(TAG, "Giving up on ${request.title}", lastError)
        updateProgress(request, 0f, DownloadStatus.FAILED)
        _downloadingIds.value = _downloadingIds.value - request.id
    }

    /**
     * One music download attempt. Returns true on success.
     *
     * Cancellation is cooperative through the coroutine's own job rather than
     * through membership of a state set: the previous design used
     * `_downloadingIds` as both the "is downloading" state and the stop signal,
     * which meant any code touching that set could halt a transfer.
     */
    private suspend fun attemptMusicDownload(request: DownloadRequest): Boolean =
        withContext(Dispatchers.IO) {
            val song = request.song ?: return@withContext false
            val pendingTargets = mutableListOf<Uri>()
            var audioTemp: File? = null
            var artworkTemp: File? = null
            updateProgress(request, 0.02f, DownloadStatus.DOWNLOADING)

            try {
                val streamUrl = youtubeRepository.getDownloadAudioStreamUrl(request.id).getOrNull()
                    ?: throw java.io.IOException("No stream URL for ${request.id}")

                ensureActive()
                updateProgress(request, 0.1f, DownloadStatus.DOWNLOADING)

                coroutineScope {
                    // These are best-effort enrichments and run under the audio
                    // transfer rather than extending every playlist item by up
                    // to two provider timeouts after its bytes have arrived.
                    val lyricsDeferred = async {
                        (lyricsRepository.fetchLyrics(song) as? LyricsResult.Success)
                            ?.toDownloadLyrics()
                            ?.takeIf(String::isNotBlank)
                    }
                    val artworkDeferred = async { loadDownloadArtwork(song) }

                    audioTemp = File.createTempFile("koda_music_", ".m4a", context.cacheDir)
                    audioTemp!!.outputStream().use { out ->
                        downloadStream(request, streamUrl, out, 0.1f, 0.82f)
                    }

                    ensureActive()
                    updateProgress(request, 0.84f, DownloadStatus.DOWNLOADING)

                    val lyrics = runCatching { lyricsDeferred.await() }
                        .onFailure { KLog.w(TAG, "Lyrics unavailable for ${song.title}", it) }
                        .getOrNull()
                    val artwork = runCatching { artworkDeferred.await() }
                        .onFailure { KLog.w(TAG, "Artwork unavailable for ${song.title}", it) }
                        .getOrNull()
                    artworkTemp = artwork?.let(::writeArtworkTemp)

                    ensureActive()
                    updateProgress(request, 0.88f, DownloadStatus.DOWNLOADING)
                    DownloadedAudioMetadata.write(
                        audioFile = audioTemp!!,
                        song = song,
                        artworkFile = artworkTemp,
                        lyrics = lyrics
                    )

                    ensureActive()
                    updateProgress(request, 0.92f, DownloadStatus.DOWNLOADING)

                    val audioName = storage.buildFileName(
                        request.title,
                        request.subtitle,
                        DownloadMediaType.MUSIC
                    )
                    val audioTarget = storage.createPending(audioName, DownloadMediaType.MUSIC)
                        ?: throw java.io.IOException("Could not create audio storage entry")
                    pendingTargets += audioTarget
                    copyToPending(audioTemp!!, audioTarget)

                    val artworkTarget = artworkTemp?.let { cover ->
                        val name = storage.buildMusicCompanionFileName(
                            request.title,
                            request.subtitle,
                            "jpg"
                        )
                        storage.createPendingMusicCompanion(name, "image/jpeg")?.also { target ->
                            pendingTargets += target
                            copyToPending(cover, target)
                        } ?: throw java.io.IOException("Could not create artwork storage entry")
                    }

                    val lyricsTarget = lyrics?.let { body ->
                        val name = storage.buildMusicCompanionFileName(
                            request.title,
                            request.subtitle,
                            "lrc"
                        )
                        storage.createPendingMusicCompanion(name, "text/plain")?.also { target ->
                            pendingTargets += target
                            storage.openOutput(target)?.bufferedWriter(Charsets.UTF_8)?.use {
                                it.write(body)
                            } ?: throw java.io.IOException("Could not write lyric storage entry")
                        } ?: throw java.io.IOException("Could not create lyric storage entry")
                    }

                    ensureActive()
                    updateProgress(request, 0.98f, DownloadStatus.DOWNLOADING)

                    // Publish companions first and audio last. A media scanner
                    // can never observe a finished song before everything that
                    // was successfully fetched for it is visible beside it.
                    if (artworkTarget != null && !storage.publish(artworkTarget)) {
                        throw java.io.IOException("Could not publish artwork storage entry")
                    }
                    if (lyricsTarget != null && !storage.publish(lyricsTarget)) {
                        throw java.io.IOException("Could not publish lyric storage entry")
                    }
                    if (!storage.publish(audioTarget)) {
                        throw java.io.IOException("Could not publish audio storage entry")
                    }
                    pendingTargets.clear()

                    val localArtwork = artworkTarget ?: song.albumArtUri
                    val downloaded = song.copy(
                        uri = audioTarget,
                        albumArtUri = localArtwork,
                        thumbnailUrl = artworkTarget?.toString()
                            ?: song.thumbnailUrl
                            ?: song.albumArtUri?.toString(),
                        source = SongSource.LOCAL,
                        lyricsUri = lyricsTarget,
                        dateAdded = System.currentTimeMillis()
                    )
                    _downloadedSongs.value = _downloadedSongs.value + downloaded
                    saveMetadata()
                }

                finishSuccess(request)
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable) {
                    pendingTargets.forEach(storage::delete)
                    _downloadingIds.value = _downloadingIds.value - request.id
                    removeProgress(request.id)
                }
                throw e
            } catch (e: Exception) {
                pendingTargets.forEach(storage::delete)
                throw e
            } finally {
                audioTemp?.delete()
                artworkTemp?.delete()
                activeDownloadCalls.remove(request.id)
            }
        }

    /** Decode a bounded, software-backed cover through Coil's shared cache. */
    private suspend fun loadDownloadArtwork(song: Song): Bitmap? {
        val urls = listOfNotNull(song.highResThumbnailUrl, song.thumbnailUrl)
            .distinct()
        for (url in urls) {
            val drawable = runCatching {
                context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(DOWNLOAD_ARTWORK_SIZE_PX, DOWNLOAD_ARTWORK_SIZE_PX)
                        .allowHardware(false)
                        .build()
                ).drawable
            }.getOrNull() ?: continue

            return (drawable as? BitmapDrawable)?.bitmap
                ?: drawable.toBitmap(DOWNLOAD_ARTWORK_SIZE_PX, DOWNLOAD_ARTWORK_SIZE_PX)
        }
        return null
    }

    private fun writeArtworkTemp(bitmap: Bitmap): File {
        val file = File.createTempFile("koda_cover_", ".jpg", context.cacheDir)
        val written = file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, DOWNLOAD_ARTWORK_JPEG_QUALITY, out)
        }
        if (!written) {
            file.delete()
            throw java.io.IOException("Could not encode album artwork")
        }
        return file
    }

    private fun copyToPending(source: File, target: Uri) {
        val output = storage.openOutput(target)
            ?: throw java.io.IOException("Could not open storage entry")
        output.use { out -> source.inputStream().use { input -> input.copyTo(out) } }
    }

    /**
     * One video download attempt.
     *
     * YouTube serves a single ready-made file only at 360p; everything better
     * is video-only plus a separate audio stream, so the good path downloads
     * both to the cache and remuxes them into the final MP4 (see
     * [DownloadMuxer]). If no adaptive pair is usable - or the MP4 muxer
     * rejects the codec - it falls back to whatever progressive stream exists
     * so the user gets a file rather than an error.
     */
    private suspend fun attemptVideoDownload(request: DownloadRequest): Boolean =
        withContext(Dispatchers.IO) {
            var pendingTarget: Uri? = null
            var videoTemp: File? = null
            var audioTemp: File? = null
            updateProgress(request, 0.02f, DownloadStatus.DOWNLOADING)

            try {
                val qualities = youtubeRepository.getVideoStreamQualities(request.id)
                if (qualities.isEmpty()) throw java.io.IOException("No streams for ${request.id}")

                ensureActive()

                // MP4-container entries only: the muxer will not accept VP9 or
                // Opus, which is what the webm ladder carries.
                val mp4Candidates = qualities.filter {
                    !it.isDASH && it.audioUrl != null && it.isMp4DownloadCompatible
                }
                // The list is sorted highest-first, so "auto" is the head and a
                // requested label resolves to the best entry at or below its
                // height (a picked quality can vanish between the sheet's fetch
                // and this re-resolution), else the lowest available.
                val targetLabel = request.qualityLabel
                    ?: ThemePreferences.currentDownloadVideoQuality(context)
                fun height(label: String): Int =
                    label.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                val adaptive = if (targetLabel == ThemePreferences.VIDEO_QUALITY_AUTO) {
                    mp4Candidates.firstOrNull()
                } else {
                    mp4Candidates.firstOrNull { height(it.resolution) in 1..height(targetLabel) }
                        ?: mp4Candidates.lastOrNull { height(it.resolution) > 0 }
                        ?: mp4Candidates.firstOrNull()
                }
                val progressive = qualities.firstOrNull {
                    !it.isDASH && it.audioUrl == null && it.isMp4Container
                }

                val chosen = adaptive ?: progressive
                    ?: throw java.io.IOException("No downloadable stream for ${request.id}")

                val fileName =
                    storage.buildFileName(request.title, request.subtitle, DownloadMediaType.VIDEO)
                val target = storage.createPending(fileName, DownloadMediaType.VIDEO)
                    ?: throw java.io.IOException("Could not create storage entry")
                pendingTarget = target

                var muxed = false

                if (chosen.audioUrl != null) {
                    // Two transfers, then a remux. Progress is split so the bar
                    // reflects real work: video is the bulk, audio a slice, and
                    // the tail is the mux.
                    videoTemp = File.createTempFile("koda_v_", ".mp4", context.cacheDir)
                    audioTemp = File.createTempFile("koda_a_", ".m4a", context.cacheDir)

                    videoTemp.outputStream().use { out ->
                        downloadStream(request, chosen.url, out, 0.05f, 0.70f)
                    }
                    ensureActive()
                    audioTemp.outputStream().use { out ->
                        downloadStream(request, chosen.audioUrl, out, 0.70f, 0.88f)
                    }
                    ensureActive()

                    updateProgress(request, 0.9f, DownloadStatus.DOWNLOADING)

                    muxed = context.contentResolver.openFileDescriptor(target, "rw")?.use { pfd ->
                        DownloadMuxer.mux(videoTemp, audioTemp, pfd.fileDescriptor)
                    } ?: false

                    if (!muxed) {
                        KLog.w(TAG, "Mux failed for ${request.title}, falling back to progressive")
                    }
                }

                if (!muxed) {
                    val fallback = progressive
                        ?: throw java.io.IOException("Mux failed and no progressive stream")
                    // Re-create the row: a failed mux may have written a partial
                    // container into the existing one.
                    storage.delete(target)
                    val retryTarget = storage.createPending(fileName, DownloadMediaType.VIDEO)
                        ?: throw java.io.IOException("Could not recreate storage entry")
                    pendingTarget = retryTarget

                    storage.openOutput(retryTarget)?.use { out ->
                        downloadStream(request, fallback.url, out, 0.1f, 1f)
                    } ?: throw java.io.IOException("Could not open output")

                    storage.publish(retryTarget)
                    recordVideo(request, retryTarget, fallback.resolution)
                    pendingTarget = null
                } else {
                    storage.publish(target)
                    recordVideo(request, target, chosen.resolution)
                    pendingTarget = null
                }

                finishSuccess(request)
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                cleanUpCancelled(request, pendingTarget)
                throw e
            } catch (e: Exception) {
                pendingTarget?.let { storage.delete(it) }
                throw e
            } finally {
                videoTemp?.delete()
                audioTemp?.delete()
                activeDownloadCalls.remove(request.id)
            }
        }

    /**
     * Stream [url] into [out], reporting progress scaled into
     * [fromFraction]..[toFraction] of the overall download. Fetches in
     * bounded ranged chunks: googlevideo paces open-ended requests to
     * roughly the media bitrate, which made downloads crawl at playback
     * speed (same server behavior ChunkedStreamDataSource works around for
     * playback). The User-Agent must match the URL's issuing client
     * (`?c=` param) or googlevideo answers 403. Throws on a truncated
     * transfer so a short read is never published as a complete file.
     */
    private suspend fun downloadStream(
        request: DownloadRequest,
        url: String,
        out: java.io.OutputStream,
        fromFraction: Float,
        toFraction: Float
    ) {
        val userAgent = YouTubeRepository.uaForPlaybackUri(Uri.parse(url))
        val buffer = ByteArray(BUFFER_SIZE)
        val span = toFraction - fromFraction
        var position = 0L
        var totalBytes = -1L

        while (totalBytes < 0 || position < totalBytes) {
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Range", "bytes=$position-${position + DOWNLOAD_CHUNK_BYTES - 1}")
                    .build()
            )
            activeDownloadCalls[request.id] = call

            val response = call.execute()
            if (!response.isSuccessful) {
                response.close()
                throw MediaHttpException(response.code)
            }
            val body = response.body ?: throw java.io.IOException("Empty body")

            // A 206 carries the served extent and the file size in
            // Content-Range; a 200 means the server ignored the range and is
            // sending the whole file in this response.
            val ranged = response.code == 206
            if (totalBytes < 0) {
                totalBytes = if (ranged) {
                    parseContentRangeTotal(response.header("Content-Range")) ?: -1L
                } else {
                    body.contentLength()
                }
            }

            var chunkBytes = 0L
            body.byteStream().use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    // Throws CancellationException if this task was cancelled,
                    // unwinding into the caller's handler.
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    out.write(buffer, 0, read)
                    position += read
                    chunkBytes += read

                    val fraction = if (totalBytes > 0) {
                        fromFraction + (position.toFloat() / totalBytes * span)
                    } else {
                        fromFraction + span / 2f // Indeterminate
                    }
                    updateProgress(
                        request, fraction, DownloadStatus.DOWNLOADING,
                        position, totalBytes
                    )
                }
            }

            // An unranged response was the whole file. Without a known total,
            // an empty chunk means the server has nothing more to serve.
            if (!ranged || totalBytes < 0 || chunkBytes == 0L) break
        }

        if (totalBytes > 0 && position < totalBytes) {
            throw java.io.IOException("Truncated: $position/$totalBytes")
        }
    }

    /**
     * A googlevideo media fetch that came back with a non-2xx status, carrying
     * the code so [runTask] can tell a refusal apart from an ordinary transport
     * failure. Plain IOException would flatten that back into a message string.
     */
    private class MediaHttpException(val code: Int) : java.io.IOException("HTTP $code")

    /**
     * Whether [error] is googlevideo refusing to serve the media, which is the
     * flagged-token signature rather than anything wrong with this download.
     * Walks the cause chain: the muxing path wraps failures on its way out.
     */
    private fun isMediaForbidden(error: Throwable?): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is MediaHttpException && cause.code == 403) return true
            cause = cause.cause
        }
        return false
    }

    /** Total size out of a "bytes 0-1023/4096" Content-Range; null when absent. */
    private fun parseContentRangeTotal(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/')?.toLongOrNull()?.takeIf { it > 0 }

    private fun recordVideo(request: DownloadRequest, uri: Uri, quality: String?) {
        _downloadedVideos.value = _downloadedVideos.value + DownloadedVideo(
            id = request.id,
            title = request.title,
            channelName = request.subtitle,
            uri = uri,
            thumbnailUrl = request.thumbnailUrl,
            durationMs = request.durationMs,
            quality = quality
        )
        saveVideoMetadata()
    }

    private fun finishSuccess(request: DownloadRequest) {
        updateProgress(request, 1f, DownloadStatus.DOWNLOADED)
        _downloadingIds.value = _downloadingIds.value - request.id
        removeProgressAfterCompletion(request.id)
        KLog.d(TAG, "Downloaded ${request.title}")
    }

    /**
     * NonCancellable: the job is already cancelled, so a plain suspend cleanup
     * here would itself be cancelled and leak the pending row.
     */
    private suspend fun cleanUpCancelled(request: DownloadRequest, pendingTarget: Uri?) {
        withContext(kotlinx.coroutines.NonCancellable) {
            pendingTarget?.let { storage.delete(it) }
            _downloadingIds.value = _downloadingIds.value - request.id
            removeProgress(request.id)
        }
    }

    /**
     * Clear a finished download from the progress map after a beat, so the UI
     * shows the completed state briefly instead of the row vanishing.
     * Deliberately not tied to the task's job, which is about to end.
     */
    private fun removeProgressAfterCompletion(songId: String) {
        repositoryScope.launch {
            kotlinx.coroutines.delay(COMPLETION_LINGER_MS)
            val entry = _downloadProgress.value[songId]
            if (entry?.status == DownloadStatus.DOWNLOADED) {
                val current = _downloadProgress.value.toMutableMap()
                current.remove(songId)
                _downloadProgress.value = current
            }
        }
    }

    /**
     * Cancel a queued or in-flight download. Safe to call for an id that is
     * neither.
     */
    fun cancelDownload(songId: String) {
        KLog.d(TAG, "Cancelling download for $songId")

        repositoryScope.launch {
            val wasQueued = queueMutex.withLock {
                val before = _downloadQueue.value
                val after = before.filterNot { it.id == songId }
                _downloadQueue.value = after
                before.size != after.size
            }

            if (!wasQueued && songId == activeId) {
                // Cancel the HTTP call as well as the job: a blocking read on
                // the socket will not notice job cancellation on its own.
                activeDownloadCalls[songId]?.cancel()
                activeJob?.cancel()
            }

            _downloadingIds.value = _downloadingIds.value - songId
            removeProgress(songId)
        }
    }

    /** Cancel everything, queued and active. */
    fun cancelAll() {
        repositoryScope.launch {
            val cleared = queueMutex.withLock {
                val queued = _downloadQueue.value
                _downloadQueue.value = emptyList()
                queued
            }
            cleared.forEach { removeProgress(it.id) }
            activeId?.let { id ->
                activeDownloadCalls[id]?.cancel()
                activeJob?.cancel()
                removeProgress(id)
            }
            _downloadingIds.value = emptySet()
        }
    }

    /** Re-queue a download that previously failed. */
    fun retryDownload(request: DownloadRequest) {
        repositoryScope.launch {
            removeProgress(request.id)
            enqueue(listOf(request))
        }
    }

    /** Delete a completed video download. */
    fun deleteVideoDownload(videoId: String) {
        val current = _downloadedVideos.value
        val video = current.find { it.id == videoId } ?: return
        storage.delete(video.uri)
        _downloadedVideos.value = current - video
        saveVideoMetadata()
    }

    fun deleteDownload(songId: String) {
        val currentList = _downloadedSongs.value.toMutableList()
        val songToDelete = currentList.find { it.id == songId } ?: return

        songToDelete.uri?.let { uri ->
            if (uri.scheme == "file") {
                // Legacy entry that never made it through migration.
                uri.path?.let { File(it).delete() }
            } else {
                storage.delete(uri)
            }
        }
        listOfNotNull(songToDelete.albumArtUri, songToDelete.lyricsUri)
            .filter(storage::isMusicCompanion)
            .forEach(storage::delete)

        currentList.remove(songToDelete)
        _downloadedSongs.value = currentList
        saveMetadata()
    }

    fun isDownloaded(songId: String): Boolean {
        return _downloadedSongs.value.any { it.id == songId }
    }
    
    fun getDownloadStatus(songId: String): DownloadStatus {
        if (isDownloaded(songId)) return DownloadStatus.DOWNLOADED
        // The progress map distinguishes queued from in-flight from failed;
        // downloadingIds only says "somewhere in the pipeline".
        _downloadProgress.value[songId]?.let { return it.status }
        if (_downloadingIds.value.contains(songId)) return DownloadStatus.QUEUED
        return DownloadStatus.NOT_DOWNLOADED
    }
    
    /**
     * A song the user already had on the device, as opposed to one Koda
     * downloaded. Previously inferred from the file living outside filesDir,
     * which stopped working once downloads moved to shared storage - our own
     * downloads are outside filesDir now too. Membership in the downloaded set
     * is the direct question.
     */
    fun isLocalOriginal(song: Song): Boolean {
        return song.source == SongSource.LOCAL && !isDownloaded(song.id)
    }

    fun clearFailedDownloads() {
        val current = _downloadProgress.value.toMutableMap()
        current.entries.removeAll { it.value.status == DownloadStatus.FAILED }
        _downloadProgress.value = current
    }
}
