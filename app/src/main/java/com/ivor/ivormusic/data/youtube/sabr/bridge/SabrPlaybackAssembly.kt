/*
 * Assembles verified SABR pieces into a playable Media3 source: fixed
 * selection, session over its own spool, prepared timelines, synthetic
 * manifest, segment DataSource behind the transient video cache, lifecycle
 * tied to source release. Rollout stays disabled here until live validation.
 */
package com.ivor.ivormusic.data.youtube.sabr.bridge

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.SabrIdentity
import com.ivor.ivormusic.data.youtube.sabr.session.OkHttpSabrTransport
import com.ivor.ivormusic.data.youtube.sabr.session.SabrSession
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSpool
import java.io.ByteArrayInputStream
import java.io.File

/** Rollout gate: stays false until the bridge is validated live. */
internal const val SABR_PLAYBACK_ENABLED = false

/** Assembled source; releasing it stops the bridge and deletes the spool. */
internal class SabrPlayback(val mediaSource: MediaSource)

/**
 * Builds one fixed-quality source. Audio-only emits an audio-only manifest so
 * music never fetches video. Throws before any work when the rollout gate is
 * closed or the descriptor cannot supply the requested tracks.
 */
@Throws(SabrProtocolException::class)
internal fun assembleSabrPlayback(
    context: Context,
    descriptor: SabrDescriptor,
    mediaItem: MediaItem,
    audioOnly: Boolean,
    maxVideoHeight: Int,
    positionMs: Long,
    playbackRate: () -> Float,
    http: okhttp3.Call.Factory,
    identityNow: () -> SabrIdentity,
    writeToCache: Boolean = true,
): SabrPlayback {
    require(SABR_PLAYBACK_ENABLED) { "SABR playback is not rolled out" }
    val audio = descriptor.selectAudio()
        ?: throw SabrProtocolException("SABR descriptor has no audio")
    val video = if (audioOnly) null else descriptor.selectVideo(maxVideoHeight)
    val spec = SabrPlaybackSpec(descriptor.videoId, audio, video)
    val spoolDir = File(File(context.cacheDir, "sabr-playback"),
        "${descriptor.videoId}-${System.nanoTime()}")
    val spool = SabrSpool(spoolDir)
    val session = SabrSession(descriptor, OkHttpSabrTransport(http), spool, identityNow)
    val bridge = SabrBridge(session, spec, playbackRate)
    try {
        bridge.prepareTimelines(positionMs)
        if (!bridge.hasTimelines()) {
            throw SabrProtocolException("SABR preparation produced no timelines")
        }
        val manifest = DashManifestParser().parse(
            Uri.parse("sabr://${descriptor.videoId}"),
            ByteArrayInputStream(buildSabrManifest(
                audio, bridge.timeline(audio),
                video, video?.let { bridge.timeline(it) },
                spec.durationMs(),
            ).toByteArray(Charsets.UTF_8)),
        )
        val upstream: DataSource.Factory =
            DataSource.Factory { SabrSegmentDataSource(descriptor.videoId, spec, bridge) }
        val chunkSource = cachedFactory(context, spec, upstream, writeToCache)
        val child = DashMediaSource.Factory(chunkSource).createMediaSource(manifest, mediaItem)
        val releaseAll = {
            bridge.stop()
            runCatching { session.close() }
            runCatching { spool.close() }
            runCatching { spoolDir.deleteRecursively() }
            Unit
        }
        val wrapped = object : WrappingMediaSource(child) {
            override fun releaseSourceInternal() {
                try {
                    super.releaseSourceInternal()
                } finally {
                    releaseAll()
                }
            }
        }
        return SabrPlayback(wrapped)
    } catch (error: Throwable) {
        bridge.stop()
        runCatching { session.close() }
        runCatching { spool.close() }
        runCatching { spoolDir.deleteRecursively() }
        throw error
    }
}

private fun cachedFactory(
    context: Context,
    spec: SabrPlaybackSpec,
    upstream: DataSource.Factory,
    writeToCache: Boolean,
): DataSource.Factory {
    val cache = CacheManager.videoCache(context) ?: return upstream
    return CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheKeyFactory { dataSpec ->
            spec.refFor(dataSpec.uri.toString()).cacheKey(spec.videoId)
        }
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .apply { if (!writeToCache) setCacheWriteDataSinkFactory(null) }
}
