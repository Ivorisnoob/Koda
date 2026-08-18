package com.ivor.ivormusic.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

/**
 * Serves one SABR segment per open, addressed by the URI Media3 asks for.
 *
 * The synthesised manifest gives every representation a base URL of
 * `sabrseg://<itag>/` with `initialization="init"` and `media="$Number$"`, so
 * the URIs arriving here are `sabrseg://137/init` and `sabrseg://137/41`. That
 * is the whole point of the DASH detour: Media3 maps a seek position to a
 * segment number itself and then asks for that segment, where the old
 * spool-file source could only ever be read forward from the beginning.
 *
 * No network happens here. [SabrMediaBridge] owns the session and the cache.
 */
@UnstableApi
class SabrDataSource(private val bridge: SabrMediaBridge) : DataSource {

    companion object {
        const val SCHEME = "sabrseg"

        fun baseUrlFor(itag: Int): String = "$SCHEME://$itag/"
    }

    /** Builds sources bound to one bridge. */
    @UnstableApi
    class Factory(private val bridge: SabrMediaBridge) : DataSource.Factory {
        override fun createDataSource(): DataSource = SabrDataSource(bridge)
    }

    private var uri: Uri? = null
    private var data: ByteArray? = null
    private var position = 0
    private var bytesRemaining = 0
    private val listeners = ArrayList<TransferListener>(2)

    override fun addTransferListener(transferListener: TransferListener) {
        // Transfer happens inside SabrSession, not through this DataSource.
        listeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val itag = dataSpec.uri.host?.toIntOrNull()
            ?: throw IOException("SABR segment URI has no itag: ${dataSpec.uri}")
        val last = dataSpec.uri.lastPathSegment
            ?: throw IOException("SABR segment URI has no segment: ${dataSpec.uri}")

        val bytes = if (last == "init") {
            bridge.initData(itag)
                ?: throw IOException("SABR initialization missing for itag $itag")
        } else {
            val sequence = last.toIntOrNull()
                ?: throw IOException("SABR segment URI is not a number: ${dataSpec.uri}")
            bridge.segment(itag, sequence)
        }

        data = bytes
        position = dataSpec.position.toInt().coerceIn(0, bytes.size)
        val available = bytes.size - position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            available
        } else {
            minOf(dataSpec.length.toInt(), available)
        }
        return bytesRemaining.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
        val source = data ?: return C.RESULT_END_OF_INPUT
        val count = minOf(length, bytesRemaining)
        System.arraycopy(source, position, buffer, offset, count)
        position += count
        bytesRemaining -= count
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        data = null
        uri = null
        bytesRemaining = 0
    }
}

/**
 * Retries a pending segment instead of failing the load.
 *
 * A [SabrSegmentPendingException] means another request is already fetching what
 * this load wants, which happens constantly while buffering and on every seek.
 * The normal retry budget is a network budget and far too small for it, so
 * pending errors get their own short delay and an effectively unlimited count;
 * genuine errors are still made fatal once past the normal budget.
 */
@UnstableApi
class SabrLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (isPending(loadErrorInfo.exception)) return PENDING_RETRY_DELAY_MS
        val normalRetryCount = super.getMinimumLoadableRetryCount(
            loadErrorInfo.mediaLoadData.dataType,
        )
        return if (loadErrorInfo.errorCount > normalRetryCount) {
            C.TIME_UNSET
        } else {
            super.getRetryDelayMsFor(loadErrorInfo)
        }
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE

    private fun isPending(error: Throwable?): Boolean {
        var cause = error
        var depth = 0
        while (cause != null && depth < 8) {
            if (cause is SabrSegmentPendingException) return true
            cause = cause.cause
            depth++
        }
        return false
    }

    private companion object {
        const val PENDING_RETRY_DELAY_MS = 100L
    }
}
