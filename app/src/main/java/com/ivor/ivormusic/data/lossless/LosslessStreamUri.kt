package com.ivor.ivormusic.data.lossless

import android.net.Uri

/**
 * Marks a queue row's URI as coming from the lossless server.
 *
 * The mark is the URI's fragment: `koda-lossless.<quality>` for a single
 * file, `koda-lossless-dash.<quality>` for a manifest. A fragment is never sent
 * to a server (not by `HttpURLConnection`, not by OkHttp) and is dropped when a
 * DASH manifest's relative segment addresses are resolved against it, so it
 * costs the request nothing - and it rides on the URI itself, so it survives
 * every place a queue row is rebuilt (`bind`, `replaceMusicSource`, recovery)
 * without a field of its own to forget. The quality says which
 * [LosslessCache] keys the row plays under.
 *
 * Everything that has to treat these rows differently reads it here:
 *  - the media source factory, which plays them through [LosslessCache]
 *    rather than the music cache (and a manifest as DASH, since the URL alone
 *    does not say so);
 *  - the cache warmer, AutoMix profiling and the waveform analysis, which all
 *    write through the music cache under the song id and must not put FLAC
 *    bytes where an AAC stream is expected;
 *  - recovery, which hands a failing lossless song back to YouTube.
 */
object LosslessStreamUri {
    private const val FILE = "koda-lossless"
    private const val DASH = "koda-lossless-dash"

    fun mark(url: String, dash: Boolean, quality: LosslessQuality): Uri =
        Uri.parse(url).buildUpon()
            .fragment("${if (dash) DASH else FILE}.${quality.id}")
            .build()

    fun mark(source: LosslessSource, quality: LosslessQuality): Uri =
        mark(source.url, source is LosslessSource.Dash, quality)

    private fun kind(uri: Uri?): String? = uri?.fragment?.substringBefore('.')

    fun isLossless(uri: Uri?): Boolean = kind(uri).let { it == FILE || it == DASH }

    fun isDash(uri: Uri?): Boolean = kind(uri) == DASH

    /** The quality a marked row was asked at; null for any other row. */
    fun quality(uri: Uri?): LosslessQuality? =
        if (!isLossless(uri)) null
        else LosslessQuality.fromId(uri?.fragment?.substringAfter('.', ""))

    /** The address as the server gave it, mark removed. */
    fun unmarked(uri: Uri): String = uri.buildUpon().fragment(null).build().toString()
}
