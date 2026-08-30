package com.ivor.ivormusic.data.tv

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File

/** One file inside a torrent, as the picker and the data source see it. */
data class TorrentFile(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    /** Byte offset of this file within the torrent's flat piece space. */
    val offset: Long,
) {
    val name: String get() = path.substringAfterLast('/')

    /**
     * Whether this looks like the feature rather than a sample or an extra.
     *
     * Season packs and scene releases ship samples, subtitles and NFOs beside
     * the film, and the largest video file is the right answer far more often
     * than the first one is.
     */
    val isVideo: Boolean
        get() = VIDEO_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } &&
            !name.contains("sample", ignoreCase = true)

    private companion object {
        val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".m4v", ".mov", ".webm", ".ts")
    }
}

/** What the UI shows while a torrent is warming up. */
data class TorrentProgress(
    val hasMetadata: Boolean = false,
    val peers: Int = 0,
    val downloadRateBps: Int = 0,
    /** 0f..1f across the whole torrent, not the playing file. */
    val progress: Float = 0f,
    val readyToPlay: Boolean = false,
)

/**
 * A torrent, streamed rather than downloaded.
 *
 * **The engine exists to feed [TorrentDataSource], not to download files.**
 * Everything here is shaped by that: pieces are prioritised around wherever the
 * player is reading, an unread tail is worth nothing, and the session is torn
 * down when playback stops rather than kept seeding. A general-purpose torrent
 * client would make almost every opposite choice.
 *
 * **One session for the process**, because libtorrent binds ports and holds a
 * DHT routing table - two sessions is two DHT nodes and two port bindings from
 * one app. It is started lazily on the first torrent and stopped when the last
 * one is released, so a user who never plays a torrent never starts a DHT node.
 */
object TorrentEngine {

    private val lock = Any()
    private var session: SessionManager? = null
    private var active: MutableMap<String, TorrentHandle> = mutableMapOf()

    private val _progress = MutableStateFlow(TorrentProgress())
    val progress: StateFlow<TorrentProgress> = _progress.asStateFlow()

    /**
     * Where partial data lives.
     *
     * Internal because [TorrentDataSource] has to open the same file libtorrent
     * is writing into, and the two agreeing about that path is the whole
     * contract between them.
     */
    internal fun cacheDir(context: Context): File =
        File(context.cacheDir, "tv_torrents").apply { mkdirs() }

    private fun ensureSession(): SessionManager = synchronized(lock) {
        session?.let { return it }
        val settings = SettingsPack()
            // Modest by phone standards. A streaming session wants enough peers
            // to saturate a film's bitrate and no more; the default ceilings are
            // tuned for seeding a library, and on mobile they are simply a
            // battery and data bill.
            .connectionsLimit(MAX_CONNECTIONS)
            .activeDownloads(1)
            .activeSeeds(0)
            .activeLimit(2)
            // Never announce as a seed. Nothing here is a library and a cache
            // directory that is wiped on release has nothing to offer a swarm.
            .uploadRateLimit(UPLOAD_LIMIT_BPS)
        val manager = SessionManager()
        manager.start(SessionParams(settings))
        manager.startDht()
        session = manager
        manager
    }

    /**
     * Fetch a torrent's metadata from its magnet.
     *
     * A magnet names content, not a file list - the list itself has to be
     * fetched from the swarm (BEP 9) before anything can be played, and that is
     * the one unavoidable wait in torrent playback. Returns null on timeout,
     * which for a dead swarm is the normal outcome rather than an error.
     */
    suspend fun fetchMetadata(
        context: Context,
        magnet: String,
        timeoutSeconds: Int = METADATA_TIMEOUT_SECONDS,
    ): TorrentInfo? = withContext(Dispatchers.IO) {
        try {
            val manager = ensureSession()
            val data = manager.fetchMagnet(magnet, timeoutSeconds, cacheDir(context))
                ?: return@withContext null
            TorrentInfo.bdecode(data)
        } catch (e: Throwable) {
            // Throwable rather than Exception: this is JNI, and a missing or
            // mismatched native library surfaces as UnsatisfiedLinkError, which
            // must degrade to "torrents unavailable" rather than kill playback.
            KLog.w(TAG, "Metadata fetch failed: " + e.javaClass.simpleName)
            null
        }
    }

    /** Start (or rejoin) a torrent and return its handle once it has metadata. */
    suspend fun start(context: Context, magnet: String, infoHash: String): TorrentHandle? =
        withContext(Dispatchers.IO) {
            try {
                val manager = ensureSession()
                synchronized(lock) { active[infoHash.lowercase()] }?.takeIf { it.isValid() }
                    ?.let { return@withContext it }
                // The hash comes from parsing the magnet rather than from
                // hex-decoding the id: it costs nothing, and it is the one path
                // that gets a v2 or hybrid torrent's hash right.
                val hash = AddTorrentParams.parseMagnetUri(magnet).infoHashes.best

                manager.download(magnet, cacheDir(context), null)
                // download() is asynchronous; the handle appears once the
                // session has registered the torrent.
                val handle = withTimeoutOrNull(HANDLE_TIMEOUT_MS) {
                    var found: TorrentHandle? = null
                    while (found == null) {
                        found = manager.find(hash)?.takeIf { it.isValid() }
                        if (found == null) delay(200)
                    }
                    found
                } ?: return@withContext null

                synchronized(lock) { active[infoHash.lowercase()] = handle }
                handle
            } catch (e: Throwable) {
                KLog.w(TAG, "Torrent start failed: " + e.javaClass.simpleName)
                null
            }
        }

    /** Wait until the file list is known. Magnets arrive without one. */
    suspend fun awaitMetadata(handle: TorrentHandle): TorrentInfo? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(METADATA_TIMEOUT_SECONDS * 1000L) {
            while (!handle.status().hasMetadata()) delay(250)
            handle.torrentFile()
        }
    }

    fun filesOf(info: TorrentInfo): List<TorrentFile> {
        val storage = info.files()
        return (0 until storage.numFiles()).map { i ->
            TorrentFile(
                index = i,
                path = storage.filePath(i),
                sizeBytes = storage.fileSize(i),
                offset = storage.fileOffset(i),
            )
        }
    }

    /**
     * The file to play out of a torrent that may hold a season.
     *
     * [preferredIndex] is the addon's own `fileIdx`, which is authoritative when
     * present - a season pack returned for one episode names which file that
     * episode is. Falling back to the largest video is what every torrent
     * player does, and it is right far more often than taking the first file.
     */
    fun pickPlayableFile(files: List<TorrentFile>, preferredIndex: Int?): TorrentFile? {
        preferredIndex?.let { idx -> files.firstOrNull { it.index == idx } }?.let { return it }
        return files.filter { it.isVideo }.maxByOrNull { it.sizeBytes }
            ?: files.maxByOrNull { it.sizeBytes }
    }

    /**
     * Download only the chosen file, in playing order.
     *
     * Everything else is set to [Priority.IGNORE] because an unread file is
     * pure cost - bandwidth, battery and disk for bytes nobody will watch. The
     * sequential range is what turns a torrent into something streamable at
     * all: the default rarest-first policy is optimal for swarm health and
     * useless for playing from the start.
     */
    fun prepareForStreaming(handle: TorrentHandle, info: TorrentInfo, file: TorrentFile) {
        runCatching {
            for (i in 0 until info.numFiles()) {
                handle.filePriority(i, if (i == file.index) Priority.TOP_PRIORITY else Priority.IGNORE)
            }
            val (first, last) = pieceRangeOf(info, file)
            handle.setSequentialRange(first, last)
            handle.resume()
        }.onFailure { KLog.w(TAG, "Could not prepare stream: " + it.javaClass.simpleName) }
    }

    /**
     * Ask for the pieces around a read position first.
     *
     * Called on every seek and as playback advances. Deadlines are how
     * libtorrent is told "this piece is needed at a wall-clock moment"; without
     * them a seek waits for the sequential head to crawl to the new position.
     */
    fun prioritiseFrom(handle: TorrentHandle, info: TorrentInfo, file: TorrentFile, offset: Long) {
        runCatching {
            val (first, last) = pieceRangeOf(info, file)
            val target = pieceAt(info, file, offset).coerceIn(first, last)
            handle.clearPieceDeadlines()
            var deadlineMs = 0
            for (piece in target..minOf(last, target + READ_AHEAD_PIECES)) {
                handle.piecePriority(piece, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(piece, deadlineMs)
                deadlineMs += DEADLINE_STEP_MS
            }
            handle.setSequentialRange(target, last)
        }.onFailure { KLog.w(TAG, "Could not prioritise: " + it.javaClass.simpleName) }
    }

    /** Whether the byte at [offset] within [file] is on disk yet. */
    fun hasByte(handle: TorrentHandle, info: TorrentInfo, file: TorrentFile, offset: Long): Boolean =
        runCatching { handle.havePiece(pieceAt(info, file, offset)) }.getOrDefault(false)

    fun publishProgress(handle: TorrentHandle, ready: Boolean) {
        runCatching {
            val status = handle.status()
            _progress.value = TorrentProgress(
                hasMetadata = status.hasMetadata(),
                peers = status.numPeers(),
                downloadRateBps = status.downloadRate(),
                progress = status.progress(),
                readyToPlay = ready,
            )
        }
    }

    /**
     * Stop a torrent and forget it.
     *
     * The session itself is stopped once nothing is active, so a device is not
     * left running a DHT node after a film ends. Partial data is deliberately
     * left on disk for the cache directory to reclaim rather than deleted here
     * - closing and reopening the same film within a session should not start
     * from zero.
     */
    fun release(infoHash: String) {
        synchronized(lock) {
            val key = infoHash.lowercase()
            active.remove(key)?.let { handle ->
                runCatching { session?.remove(handle, null) }
            }
            if (active.isEmpty()) {
                runCatching { session?.stop() }
                session = null
                _progress.value = TorrentProgress()
            }
        }
    }

    fun releaseAll() {
        synchronized(lock) {
            active.keys.toList().forEach { release(it) }
        }
    }

    /** Whether the native library loaded at all. False on an unsupported ABI. */
    val isAvailable: Boolean by lazy {
        runCatching {
            // Touching a swig-backed class is what forces the .so to load, and
            // an unsupported ABI fails here as UnsatisfiedLinkError rather than
            // later in the middle of playback.
            org.libtorrent4j.swig.libtorrent.version()
            true
        }.getOrElse {
            KLog.w(TAG, "Torrent engine unavailable: " + it.javaClass.simpleName)
            false
        }
    }

    // --- Piece arithmetic ---------------------------------------------------

    /**
     * The pieces a file occupies.
     *
     * Torrent pieces span file boundaries, so a file's first and last piece are
     * usually shared with its neighbours - which is why the last piece of a
     * season pack's episode one is also the first piece of episode two, and why
     * "download only this file" still fetches a little either side.
     */
    internal fun pieceRangeOf(info: TorrentInfo, file: TorrentFile): Pair<Int, Int> =
        pieceRangeOf(info.pieceLength(), info.numPieces(), file)

    internal fun pieceAt(info: TorrentInfo, file: TorrentFile, offsetInFile: Long): Int =
        pieceAt(info.pieceLength(), info.numPieces(), file, offsetInFile)

    /**
     * Split out from the [TorrentInfo] overloads so it can be tested.
     *
     * `TorrentInfo` is a JNI object and cannot exist in a JVM unit test, so
     * without this the piece maths - the one place here where an off-by-one is
     * silent rather than loud - would be untestable. A range that is one piece
     * short reads as a file that never finishes buffering.
     */
    internal fun pieceRangeOf(
        pieceLength: Int,
        numPieces: Int,
        file: TorrentFile,
    ): Pair<Int, Int> {
        val length = pieceLength.toLong().coerceAtLeast(1)
        val first = (file.offset / length).toInt()
        val last = ((file.offset + file.sizeBytes - 1).coerceAtLeast(file.offset) / length).toInt()
        return first to last.coerceAtLeast(first).coerceAtMost(numPieces - 1)
    }

    internal fun pieceAt(
        pieceLength: Int,
        numPieces: Int,
        file: TorrentFile,
        offsetInFile: Long,
    ): Int {
        val length = pieceLength.toLong().coerceAtLeast(1)
        return ((file.offset + offsetInFile.coerceAtLeast(0)) / length).toInt()
            .coerceIn(0, numPieces - 1)
    }

    private const val TAG = "TorrentEngine"

    /** Enough peers to saturate a film's bitrate, not enough to melt a phone. */
    private const val MAX_CONNECTIONS = 80

    /**
     * Uploading is capped rather than disabled outright: most swarms and many
     * trackers throttle or drop a peer that never gives anything back, so a
     * hard zero makes downloads slower rather than politer.
     */
    private const val UPLOAD_LIMIT_BPS = 32 * 1024

    private const val METADATA_TIMEOUT_SECONDS = 45
    private const val HANDLE_TIMEOUT_MS = 20_000L

    /** How far ahead of the read head to demand pieces. */
    private const val READ_AHEAD_PIECES = 12
    private const val DEADLINE_STEP_MS = 900
}

/**
 * Build a magnet from what an addon gave us.
 *
 * `sources` is where the protocol puts trackers, as `tracker:udp://...` and
 * `dht:<hash>` entries. Including them matters: DHT alone finds a popular
 * torrent eventually, but the tracker list is what makes it seconds rather
 * than minutes, and some private-ish swarms are tracker-only.
 */
internal fun magnetFor(infoHash: String, displayName: String?, sources: List<String>): String {
    val builder = StringBuilder("magnet:?xt=urn:btih:").append(infoHash.lowercase())
    displayName?.takeIf { it.isNotBlank() }?.let {
        builder.append("&dn=").append(StremioUrls.encodeSegment(it.take(120)))
    }
    val trackers = sources
        .mapNotNull { it.removePrefix("tracker:").takeIf { t -> t != it || t.startsWith("http") || t.startsWith("udp") } }
        .filter { it.startsWith("http") || it.startsWith("udp") }
        .distinct()
        .take(MAX_TRACKERS)
    for (tracker in trackers) {
        builder.append("&tr=").append(StremioUrls.encodeSegment(tracker))
    }
    for (fallback in DEFAULT_TRACKERS) {
        if (trackers.none { it == fallback }) {
            builder.append("&tr=").append(StremioUrls.encodeSegment(fallback))
        }
    }
    return builder.toString()
}

private const val MAX_TRACKERS = 12

/**
 * Public trackers appended when an addon supplied none.
 *
 * Torrentio sends its own list and these are then redundant; other addons send
 * a bare infoHash, and a magnet with no trackers relies entirely on DHT, which
 * on a cold routing table can take minutes before a single peer appears.
 */
private val DEFAULT_TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://exodus.desync.com:6969/announce",
)
