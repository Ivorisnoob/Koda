package com.ivor.ivormusic.data.lossless

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import com.ivor.ivormusic.util.KLog
import java.io.File
import java.io.OutputStream
import java.util.TreeSet

/** How much room played lossless songs may take. Ids are stored, so frozen. */
enum class LosslessCacheSize(val id: String, val bytes: Long) {
    GB_1("1", 1L shl 30),
    GB_2("2", 2L shl 30),
    GB_5("5", 5L shl 30),
    GB_10("10", 10L shl 30);

    companion object {
        val DEFAULT = GB_2
        fun fromId(id: String?): LosslessCacheSize = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Played lossless songs, kept so they are never fetched twice.
 *
 * A lossless song is 30-150 MB, so playing one twice used to cost twice that.
 * Every lossless row is now played through this cache: the stream is written
 * here as it plays (ExoPlayer reads five minutes ahead, so a song is normally
 * whole on disk before it ends), and a song found whole here plays from disk -
 * no server, no network, Wi-Fi or not. A download of a song already here is a
 * copy, not a transfer.
 *
 * It is its own `SimpleCache` in its own directory, never the music cache: that
 * one is keyed by song id, sized for AAC and read back as the YouTube stream
 * (see `LosslessStreamUri`). Its size is the listener's (1-10 GB), enforced by
 * a least-recently-used evictor that reads the limit live, so a smaller limit
 * applies without rebuilding the cache under a playing player.
 *
 * Keys are `L|<quality>|<song id>|...`: `mpd` for a manifest, `file` for a
 * single file, and the segment's address for a DASH segment. The address a
 * manifest or file came from is kept in its content metadata (`koda_url`), which
 * is what lets a manifest read back from disk resolve its segments to the very
 * keys they were written under. The server is not part of the key: the same
 * song at the same quality is the same audio whichever address it came from.
 *
 * Process-wide by necessity (one `SimpleCache` per directory per process), and
 * not repository state in invariant 6's sense: it holds audio bytes, like the
 * playback caches, and nothing a listener entered.
 */
@UnstableApi
object LosslessCache {
    private const val TAG = "LosslessCache"
    private const val DIR = "lossless_audio"
    private const val META_URL = "koda_url"

    @Volatile
    private var cache: SimpleCache? = null
    private var evictor: LiveLimitLruEvictor? = null

    /** The listener's switch and limit, as last read; set by [apply]. */
    @Volatile
    var enabled: Boolean = true
        private set

    @Volatile
    private var limitBytes: Long = LosslessCacheSize.DEFAULT.bytes

    /** Follow the settings. Cheap; called wherever a fresh read already happened. */
    fun apply(config: LosslessConfig) {
        enabled = config.cacheEnabled
        if (limitBytes != config.cacheSize.bytes) {
            limitBytes = config.cacheSize.bytes
            synchronized(this) { cache?.let { c -> evictor?.trim(c) } }
        }
    }

    @Synchronized
    fun get(context: Context): SimpleCache? {
        cache?.let { return it }
        val dir = File(context.applicationContext.cacheDir, DIR)
        val newEvictor = LiveLimitLruEvictor { limitBytes }
        val created = runCatching {
            SimpleCache(dir, newEvictor, StandaloneDatabaseProvider(context.applicationContext))
        }.recoverCatching {
            KLog.e(TAG, "Lossless cache unreadable; starting it again", it)
            dir.deleteRecursively()
            SimpleCache(dir, newEvictor, StandaloneDatabaseProvider(context.applicationContext))
        }.onFailure {
            KLog.e(TAG, "Lossless cache unavailable; lossless songs play direct", it)
        }.getOrNull()
        cache = created
        evictor = newEvictor.takeIf { created != null }
        return created
    }

    fun prefix(quality: LosslessQuality, songId: String): String = "L|${quality.id}|$songId"
    private fun manifestKey(prefix: String) = "$prefix|mpd"
    private fun fileKey(prefix: String) = "$prefix|file"
    private fun segmentKey(prefix: String, url: String) = "$prefix|$url"

    /**
     * The data source a lossless row plays through: [upstream] behind the
     * cache, every request keyed under [prefix]. Null when the cache is off or
     * unavailable, and the row then plays straight from [upstream].
     */
    fun dataSourceFactory(context: Context, prefix: String, dash: Boolean, upstream: DataSource.Factory): DataSource.Factory? {
        if (!enabled) return null
        val c = get(context) ?: return null
        return CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory { spec -> keyFor(prefix, dash, spec.uri) }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun keyFor(prefix: String, dash: Boolean, uri: Uri): String = when {
        // The row's own address carries the mark; only the manifest or the
        // single file is asked for under it.
        LosslessStreamUri.isLossless(uri) -> if (dash) manifestKey(prefix) else fileKey(prefix)
        else -> segmentKey(prefix, uri.toString())
    }

    /**
     * About to play [url] fresh from the server for [prefix]. A manifest or
     * file kept from an older address is dropped first - otherwise the cache
     * would answer the new request with the old manifest, whose segments may
     * have expired - and the new address is remembered.
     */
    fun prepareFresh(context: Context, prefix: String, url: String, dash: Boolean) {
        if (!enabled) return
        val c = get(context) ?: return
        val key = if (dash) manifestKey(prefix) else fileKey(prefix)
        val stale = if (dash) fileKey(prefix) else manifestKey(prefix)
        runCatching {
            if (c.getContentMetadata(key).get(META_URL, null as String?) != url) c.removeResource(key)
            c.removeResource(stale)
            c.applyContentMetadataMutations(key, ContentMetadataMutations().set(META_URL, url))
        }.onFailure { KLog.w(TAG, "Could not prepare the cache for $prefix", it) }
    }

    /** A song kept whole: what it was played from, so it can be played again from disk. */
    data class Kept(val url: String, val dash: Boolean)

    /** [prefix]'s song, if every byte of it is here; null otherwise. Blocking. */
    fun kept(context: Context, prefix: String): Kept? {
        if (!enabled) return null
        val c = get(context) ?: return null
        return runCatching {
            whole(c, fileKey(prefix))?.let { return@runCatching Kept(it, dash = false) }
            val manifestUrl = whole(c, manifestKey(prefix)) ?: return@runCatching null
            val segments = segmentsOf(c, prefix, manifestUrl) ?: return@runCatching null
            if (segments.all { isWhole(c, segmentKey(prefix, it)) }) {
                Kept(manifestUrl, dash = true)
            } else null
        }.getOrNull()
    }

    /**
     * Writes [prefix]'s song, kept whole, to [out]: the file, or the init and
     * every segment in order. False when it is not all here. Blocking.
     */
    fun copyKept(context: Context, prefix: String, out: OutputStream): Boolean {
        val kept = kept(context, prefix) ?: return false
        val c = get(context) ?: return false
        val keys = if (!kept.dash) listOf(fileKey(prefix))
        else segmentsOf(c, prefix, kept.url)?.map { segmentKey(prefix, it) } ?: return false
        return runCatching {
            keys.forEach { key -> out.write(readWhole(c, key)) }
            true
        }.getOrElse {
            KLog.w(TAG, "Could not copy $prefix out of the cache", it)
            false
        }
    }

    fun sizeBytes(context: Context): Long = get(context)?.cacheSpace ?: 0L

    fun clear(context: Context) {
        val c = get(context) ?: return
        runCatching { c.keys.toList().forEach(c::removeResource) }
            .onFailure { KLog.w(TAG, "Could not clear the lossless cache", it) }
    }

    /** The init and media addresses of a kept manifest, in order. */
    private fun segmentsOf(c: Cache, prefix: String, manifestUrl: String): List<String>? {
        val xml = String(readWhole(c, manifestKey(prefix)), Charsets.UTF_8)
        val segments = DashManifest.parse(xml, manifestUrl)
        return listOfNotNull(segments.initUrl) + segments.mediaUrls
    }

    /** The stored address of [key] when all of it is here. */
    private fun whole(c: Cache, key: String): String? {
        if (!isWhole(c, key)) return null
        return c.getContentMetadata(key).get(META_URL, null as String?)
    }

    private fun isWhole(c: Cache, key: String): Boolean {
        val length = ContentMetadata.getContentLength(c.getContentMetadata(key))
        return length > 0 && c.isCached(key, 0, length)
    }

    /** Every byte of [key], from disk only. */
    private fun readWhole(c: Cache, key: String): ByteArray {
        val source = CacheDataSource.Factory()
            .setCache(c)
            // No upstream: a byte that is not on disk is a failure, never a fetch.
            .setUpstreamDataSourceFactory(null)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory { key }
            .createDataSource()
        return try {
            source.open(DataSpec.Builder().setUri(Uri.parse("koda-lossless-cache://$key")).setKey(key).build())
            DataSourceUtil.readToEnd(source)
        } finally {
            source.close()
        }
    }

    /**
     * Least recently used first, against a limit read live. Media3's own LRU
     * evictor takes its limit once, at construction, and the cache cannot be
     * rebuilt under a player that holds it.
     */
    private class LiveLimitLruEvictor(private val maxBytes: () -> Long) : CacheEvictor {
        private val spans = TreeSet<CacheSpan> { a, b ->
            val byTouch = a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
            if (byTouch != 0) byTouch else a.compareTo(b)
        }
        private var size = 0L

        override fun requiresCacheSpanTouches(): Boolean = true
        override fun onCacheInitialized() = Unit

        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
            if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
        }

        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            spans.add(span)
            size += span.length
            evict(cache, 0)
        }

        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            spans.remove(span)
            size -= span.length
        }

        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
            onSpanRemoved(cache, oldSpan)
            onSpanAdded(cache, newSpan)
        }

        /** SimpleCache calls this evictor under its own monitor; so must a trim from outside. */
        fun trim(cache: Cache) = synchronized(cache) { evict(cache, 0) }

        private fun evict(cache: Cache, required: Long) {
            while (size + required > maxBytes() && spans.isNotEmpty()) {
                val oldest = spans.first()
                runCatching { cache.removeSpan(oldest) }
                // A span the cache no longer knew sends no removal back; drop
                // it here, or this loop would ask for it forever.
                if (spans.firstOrNull() === oldest) onSpanRemoved(cache, oldest)
            }
        }
    }
}
