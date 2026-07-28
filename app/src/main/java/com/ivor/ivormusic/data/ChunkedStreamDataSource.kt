package com.ivor.ivormusic.data

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * HTTP data source for googlevideo media that downloads in bounded chunks.
 *
 * googlevideo paces open-ended requests (no Range end bound) to roughly the
 * media bitrate: measured 32 KB/s on an audio stream whose URL served bounded
 * ranges at 5-22 MB/s over the same connection (verified July 2026).
 * ExoPlayer's progressive pipeline issues exactly those open-ended requests,
 * so every stream trickled in at playback speed - slow starts, stalling
 * seeks, and a read-ahead buffer that never actually filled. This source
 * splits the logical stream into [CHUNK_SIZE_BYTES] ranged requests,
 * invisibly to ExoPlayer: [open] reports the full remaining resource length
 * and [read] rolls over to the next ranged request when the current one is
 * exhausted. The served extent and total size come from the response's
 * Content-Range header, so a chunk that overshoots the end of the file is
 * handled by the server's clamping rather than by guessing.
 *
 * Also picks the per-request User-Agent via [YouTubeRepository.uaForPlaybackUri]:
 * googlevideo URLs are bound to their issuing InnerTube client through the
 * `?c=` query param and answer 403 when the playback UA does not match.
 *
 * Non-googlevideo URIs pass straight through to the delegate unchunked.
 */
@UnstableApi
class ChunkedStreamDataSource private constructor(
    private val delegate: DefaultHttpDataSource,
) : DataSource {

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedStreamDataSource(
            // No factory-level setUserAgent: DefaultHttpDataSource applies the
            // userAgent field last and would overwrite the per-request
            // User-Agent set in open().
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true)
                .createDataSource()
        )
    }

    companion object {
        /**
         * 10 MB per ranged request: one request covers a whole typical song,
         * and for video it balances request-count overhead against how much
         * data is thrown away when the user seeks or switches quality.
         */
        private const val CHUNK_SIZE_BYTES = 10L * 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val UNSET = C.LENGTH_UNSET.toLong()

        private val CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

        private fun isGoogleVideo(uri: android.net.Uri): Boolean =
            uri.host?.endsWith(".googlevideo.com") == true
    }

    private var currentSpec: DataSpec? = null
    private var chunked = false

    /** Absolute read position within the resource. */
    private var position = 0L

    /** Absolute exclusive end of the caller's requested window, or [UNSET]. */
    private var requestedEnd = UNSET

    /** Total resource length learned from Content-Range, or [UNSET]. */
    private var totalLength = UNSET

    /** Bytes the server actually promised for the currently open chunk. */
    private var chunkRemaining = 0L

    /** Guards against reopen loops when the server serves an empty chunk. */
    private var chunkProgressed = false

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        delegate.setRequestProperty(
            "User-Agent",
            YouTubeRepository.uaForPlaybackUri(dataSpec.uri)
        )
        currentSpec = dataSpec
        position = dataSpec.position
        chunked = isGoogleVideo(dataSpec.uri)
        if (!chunked) {
            return delegate.open(dataSpec)
        }

        requestedEnd =
            if (dataSpec.length != UNSET) dataSpec.position + dataSpec.length else UNSET
        totalLength = UNSET
        openChunk()

        return when {
            dataSpec.length != UNSET ->
                if (totalLength != UNSET) {
                    minOf(dataSpec.length, totalLength - dataSpec.position)
                } else {
                    dataSpec.length
                }
            totalLength != UNSET -> totalLength - dataSpec.position
            else -> UNSET
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!chunked) return delegate.read(buffer, offset, length)
        if (length == 0) return 0

        if (chunkRemaining <= 0L) {
            val end = effectiveEnd()
            if (end != UNSET && position >= end) return C.RESULT_END_OF_INPUT
            delegate.close()
            openChunk()
            if (chunkRemaining <= 0L) return C.RESULT_END_OF_INPUT
        }

        val toRead = minOf(length.toLong(), chunkRemaining).toInt()
        val read = delegate.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            // The server delivered less than Content-Range promised. If this
            // chunk produced nothing at all, stop instead of re-requesting the
            // same empty range forever; otherwise retry from the current
            // position with a fresh ranged request.
            if (!chunkProgressed) return C.RESULT_END_OF_INPUT
            chunkRemaining = 0
            return read(buffer, offset, length)
        }
        chunkProgressed = true
        position += read
        chunkRemaining -= read
        return read
    }

    override fun getUri(): android.net.Uri? = delegate.uri ?: currentSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() {
        currentSpec = null
        chunked = false
        chunkRemaining = 0
        delegate.close()
    }

    /** Exclusive end of what can still be read: caller window and/or file end. */
    private fun effectiveEnd(): Long = when {
        requestedEnd != UNSET && totalLength != UNSET -> minOf(requestedEnd, totalLength)
        requestedEnd != UNSET -> requestedEnd
        else -> totalLength
    }

    /**
     * Issue the ranged request for the next chunk starting at [position] and
     * record the extent the server actually promised. Sets [chunkRemaining]
     * to 0 (instead of throwing) when the position is at or past the end of
     * the file, so [read] can report a clean end of input.
     */
    private fun openChunk() {
        val spec = checkNotNull(currentSpec)
        val end = effectiveEnd()
        val maxLen = if (end != UNSET) end - position else Long.MAX_VALUE
        val len = minOf(CHUNK_SIZE_BYTES, maxLen)
        chunkProgressed = false
        if (len <= 0L) {
            chunkRemaining = 0
            return
        }

        val chunkSpec = spec.buildUpon().setPosition(position).setLength(len).build()
        try {
            delegate.open(chunkSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // 416 means the requested start is past the end of the file (an
            // unknown-length window that guessed too far): clean end of input.
            if (e.responseCode == 416) {
                chunkRemaining = 0
                return
            }
            throw e
        }

        val contentRange = delegate.responseHeaders["Content-Range"]?.firstOrNull()
        val match = contentRange?.let { CONTENT_RANGE_REGEX.find(it) }
        if (match != null) {
            val (_, rangeEnd, total) = match.destructured
            chunkRemaining = rangeEnd.toLong() - position + 1
            total.toLongOrNull()?.let { totalLength = it }
        } else {
            // No Content-Range (a 200 response): the delegate skips to
            // `position` internally and will deliver up to `len` bytes.
            chunkRemaining = len
        }
    }
}
