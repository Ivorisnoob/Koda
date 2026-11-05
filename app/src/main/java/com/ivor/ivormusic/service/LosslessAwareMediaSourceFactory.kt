package com.ivor.ivormusic.service

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.ivor.ivormusic.data.lossless.LosslessClient
import com.ivor.ivormusic.data.lossless.LosslessStreamUri

/**
 * The music player's media source factory: everything goes to [fallback] as
 * before, except rows the lossless server supplied ([LosslessStreamUri]).
 *
 * Those are played straight from the network, never through the music cache:
 * the cache is keyed by song id and sized for AAC, a hi-res FLAC would evict a
 * dozen songs to hold one, and a cached copy under the song id would later be
 * read back as if it were the YouTube stream. A manifest is played as DASH,
 * because nothing in its address says so and the default factory would try it
 * as a progressive file.
 */
@UnstableApi
internal class LosslessAwareMediaSourceFactory(
    context: Context,
    private val fallback: MediaSource.Factory,
) : MediaSource.Factory {

    private val direct = DefaultDataSource.Factory(
        context,
        DefaultHttpDataSource.Factory()
            .setUserAgent(LosslessClient.USER_AGENT)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
    )
    private val dash = DashMediaSource.Factory(direct)
    private val progressive = ProgressiveMediaSource.Factory(direct)

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        fallback.setDrmSessionManagerProvider(provider)
        dash.setDrmSessionManagerProvider(provider)
        progressive.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        fallback.setLoadErrorHandlingPolicy(policy)
        dash.setLoadErrorHandlingPolicy(policy)
        progressive.setLoadErrorHandlingPolicy(policy)
        return this
    }

    override fun getSupportedTypes(): IntArray =
        (fallback.supportedTypes.toSet() + C.CONTENT_TYPE_DASH + C.CONTENT_TYPE_OTHER).toIntArray()

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        return when {
            LosslessStreamUri.isDash(uri) -> dash.createMediaSource(mediaItem)
            LosslessStreamUri.isLossless(uri) -> progressive.createMediaSource(mediaItem)
            else -> fallback.createMediaSource(mediaItem)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
