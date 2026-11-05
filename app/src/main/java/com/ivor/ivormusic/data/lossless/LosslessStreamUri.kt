package com.ivor.ivormusic.data.lossless

import android.net.Uri

/**
 * Marks a queue row's URI as coming from the lossless server.
 *
 * The mark is the URI's fragment. A fragment is never sent to a server (not by
 * `HttpURLConnection`, not by OkHttp) and is dropped when a DASH manifest's
 * relative segment addresses are resolved against it, so it costs the request
 * nothing - and it rides on the URI itself, so it survives every place a queue
 * row is rebuilt (`bind`, `replaceMusicSource`, recovery) without a field of
 * its own to forget.
 *
 * Everything that has to treat these rows differently reads it here:
 *  - the media source factory, which plays them past the playback cache (and
 *    a manifest as DASH, since the URL alone does not say so);
 *  - the cache warmer, AutoMix profiling and the waveform analysis, which all
 *    write through the music cache under the song id and must not put FLAC
 *    bytes where an AAC stream is expected;
 *  - recovery, which hands a failing lossless song back to YouTube.
 */
object LosslessStreamUri {
    private const val FILE = "koda-lossless"
    private const val DASH = "koda-lossless-dash"

    fun mark(source: LosslessSource): Uri =
        Uri.parse(source.url).buildUpon()
            .fragment(if (source is LosslessSource.Dash) DASH else FILE)
            .build()

    fun isLossless(uri: Uri?): Boolean = uri?.fragment.let { it == FILE || it == DASH }

    fun isDash(uri: Uri?): Boolean = uri?.fragment == DASH
}
