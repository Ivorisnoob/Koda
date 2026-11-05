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
import com.ivor.ivormusic.data.lossless.LosslessCache
import com.ivor.ivormusic.data.lossless.LosslessClient
import com.ivor.ivormusic.data.lossless.LosslessStreamUri

/**
 * The music player's media source factory: everything goes to [fallback] as
 * before, except rows the lossless server supplied ([LosslessStreamUri]).
 *
 * Those never go through the music cache: it is keyed by song id and sized
 * for AAC, a hi-res FLAC would evict a dozen songs to hold one, and a cached
 * copy under the song id would later be read back as if it were the YouTube
 * stream. They play through [LosslessCache] instead, keyed by song and quality,
 * so a song played once is whole on disk for the next time. A manifest is played as DASH,
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
    private val appContext = context.applicationContext
    private var drmProvider: DrmSessionManagerProvider? = null
    private var errorPolicy: LoadErrorHandlingPolicy? = null

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        fallback.setDrmSessionManagerProvider(provider)
        drmProvider = provider
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        fallback.setLoadErrorHandlingPolicy(policy)
        errorPolicy = policy
        return this
    }

    override fun getSupportedTypes(): IntArray =
        (fallback.supportedTypes.toSet() + C.CONTENT_TYPE_DASH + C.CONTENT_TYPE_OTHER).toIntArray()

    /**
     * A lossless row gets a source of its own, because its cache keys are its
     * own: everything it asks for is written to [LosslessCache] under its song
     * and quality (see there), which is what lets the next play come entirely
     * off the disk. With the cache off it plays straight from the network.
     */
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        val quality = LosslessStreamUri.quality(uri) ?: return fallback.createMediaSource(mediaItem)
        val dash = LosslessStreamUri.isDash(uri)
        val dataSource = LosslessCache.dataSourceFactory(
            appContext,
            LosslessCache.prefix(quality, mediaItem.mediaId),
            dash,
            direct
        ) ?: direct
        val factory: MediaSource.Factory =
            if (dash) DashMediaSource.Factory(dataSource) else ProgressiveMediaSource.Factory(dataSource)
        drmProvider?.let(factory::setDrmSessionManagerProvider)
        errorPolicy?.let(factory::setLoadErrorHandlingPolicy)
        return factory.createMediaSource(mediaItem)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
