package com.ivor.ivormusic.data.tv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import com.ivor.ivormusic.util.KLog
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Reads a torrent's chosen file as if it were an ordinary stream.
 *
 * **This is why torrent playback needed no changes anywhere else.** ExoPlayer
 * asks a `DataSource` for bytes at an offset; whether those bytes came from a
 * CDN or from forty strangers is not its concern. Every feature built on the
 * player - resume, next-episode, audio tracks, the gesture surface - works
 * untouched because none of them are below this line.
 *
 * The one thing this does that an HTTP source never has to: **block**. A byte
 * that has not arrived yet is not an error, it is a wait, so [read] tells the
 * engine to prioritise the pieces it needs and then polls until they land. That
 * is also why it is the only data source in the app allowed to sit in a loop -
 * ExoPlayer's loading thread is designed to be blocked, and returning zero
 * bytes instead would be read as end-of-stream.
 */
@UnstableApi
class TorrentDataSource(
    private val handle: TorrentHandle,
    private val info: TorrentInfo,
    private val file: TorrentFile,
    private val savePath: File,
) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var readPosition = 0L
    private var bytesRemaining = 0L
    private var opened = false
    private var raf: RandomAccessFile? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        readPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            file.sizeBytes - dataSpec.position
        }
        if (bytesRemaining < 0) throw EOFException()

        // A seek is the moment to re-aim the swarm. Without this the sequential
        // head keeps crawling from wherever it was and a jump to 1:20:00 waits
        // for the entire first eighty minutes.
        TorrentEngine.prioritiseFrom(handle, info, file, readPosition)

        // The file exists on disk from the moment the torrent is added, as a
        // sparse allocation - so it can be opened before a single piece has
        // arrived, and read() is what waits for the bytes.
        val target = File(savePath, file.path)
        raf = awaitFile(target)

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        if (!awaitBytes(readPosition)) {
            throw IOException("Timed out waiting for torrent data at " + readPosition)
        }

        val access = raf ?: throw IOException("Torrent file not open")
        val read = try {
            access.seek(readPosition)
            access.read(buffer, offset, toRead)
        } catch (e: IOException) {
            throw IOException("Torrent read failed", e)
        }
        if (read <= 0) {
            // The piece is present but the sparse file has not been flushed
            // yet. Treat it as a wait rather than an end, or playback stops a
            // few seconds in for no visible reason.
            return 0
        }

        readPosition += read
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        runCatching { raf?.close() }
        raf = null
    }

    /**
     * Wait for the piece holding [position], nudging the engine as it goes.
     *
     * The re-prioritise inside the loop is deliberate: on a cold swarm the
     * first wait can outlive the deadlines set at open, and re-asking is what
     * keeps a slow start from becoming a permanent one.
     */
    private fun awaitBytes(position: Long): Boolean {
        val deadline = System.currentTimeMillis() + PIECE_TIMEOUT_MS
        var nudges = 0
        while (System.currentTimeMillis() < deadline) {
            if (TorrentEngine.hasByte(handle, info, file, position)) {
                TorrentEngine.publishProgress(handle, ready = true)
                return true
            }
            if (nudges % NUDGE_EVERY == 0) {
                TorrentEngine.prioritiseFrom(handle, info, file, position)
                TorrentEngine.publishProgress(handle, ready = false)
            }
            nudges++
            try {
                Thread.sleep(POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        KLog.w(TAG, "Gave up waiting for data at " + position)
        return false
    }

    /** The sparse file appears shortly after the torrent is added, not instantly. */
    private fun awaitFile(target: File): RandomAccessFile {
        val deadline = System.currentTimeMillis() + FILE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (target.exists()) {
                return runCatching { RandomAccessFile(target, "r") }.getOrNull()
                    ?: throw IOException("Could not open " + target.name)
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted waiting for torrent file")
            }
        }
        throw IOException("Torrent file never appeared: " + target.name)
    }

    /**
     * Hands the player a fresh source per open.
     *
     * One handle and one file, fixed at construction - a factory that could
     * switch torrents mid-playback would be a way to play the wrong film.
     */
    class Factory(
        private val handle: TorrentHandle,
        private val info: TorrentInfo,
        private val file: TorrentFile,
        private val savePath: File,
    ) : DataSource.Factory {
        private val listeners = mutableListOf<TransferListener>()

        fun addTransferListener(listener: TransferListener) = apply { listeners.add(listener) }

        override fun createDataSource(): DataSource =
            TorrentDataSource(handle, info, file, savePath).also { source ->
                listeners.forEach { source.addTransferListener(it) }
            }
    }

    private companion object {
        const val TAG = "TorrentDataSource"
        const val POLL_MS = 120L

        /**
         * How long one piece may take before the read is called failed.
         *
         * Generous, because a cold magnet on a thin swarm legitimately takes
         * tens of seconds to produce its first bytes, and failing early there
         * would report a dead torrent for one that was merely slow.
         */
        const val PIECE_TIMEOUT_MS = 90_000L
        const val FILE_TIMEOUT_MS = 30_000L
        const val NUDGE_EVERY = 25
    }
}
