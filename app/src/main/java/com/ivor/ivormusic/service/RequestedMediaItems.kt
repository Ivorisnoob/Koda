package com.ivor.ivormusic.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withTimeout

/**
 * Turns what a controller asks to play into rows the queue can hold.
 *
 * A voice request ("Hey Google, play X on Koda") arrives as an item carrying
 * only a search query - no media id, no uri - and needs a bounded search
 * first. Legacy browsers can hand back only an id, losing a device song's
 * local URI, which is looked up again in the browse tree. Every row leaves
 * with an occurrence id of its own.
 */
@UnstableApi
internal class RequestedMediaItems(
    private val scope: CoroutineScope,
    private val browseTree: MusicBrowseTree,
) {
    fun resolve(mediaItems: List<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
        val needsSearchResolution = mediaItems.any {
            it.mediaId.isBlank() && !it.requestMetadata.searchQuery.isNullOrBlank()
        }
        val needsBrowseLookup = mediaItems.any { it.localConfiguration == null }
        if (!needsSearchResolution && !needsBrowseLookup) {
            return Futures.immediateFuture(mediaItems.mapTo(mutableListOf(), ::prepare))
        }

        return scope.future {
            withTimeout(TIMEOUT_MS) {
                val generation = browseTree.generation
                val resolved = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    val query = item.requestMetadata.searchQuery?.trim()?.takeIf { it.isNotEmpty() }
                    if (item.mediaId.isNotBlank() || query == null) {
                        val song = if (item.localConfiguration == null) browseTree.findSong(item.mediaId) else null
                        resolved.add(prepare(song?.let(browseTree::toBrowseItem) ?: item))
                        continue
                    }
                    // Take the best song result for the query. A miss is
                    // dropped rather than invented: handing the player an
                    // empty id would surface as an unexplained playback error.
                    val match = try {
                        browseTree.searchSongs(query).firstOrNull()
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        KLog.w(TAG, "Voice search timed out", e)
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        KLog.e(TAG, "Voice resolution failed for '$query'", e)
                        null
                    }
                    if (match == null) {
                        KLog.w(TAG, "Voice request '$query' matched nothing")
                        continue
                    }
                    resolved.add(prepare(browseTree.toPlaceholderItem(match, MusicStreamResolver.PLACEHOLDER_PREFIX)))
                }
                check(generation == browseTree.generation) { "Profile changed during voice or browse playback request" }
                if (resolved.isEmpty()) {
                    throw IllegalStateException("No results for any requested media item")
                }
                resolved
            }
        }
    }

    /**
     * Give an incoming item a concrete shape the playback pipeline can carry:
     * local URIs preserved, YouTube ids turned into placeholders that
     * [MusicStreamResolver] swaps for a real stream.
     */
    fun prepare(item: MediaItem): MediaItem {
        val existingUri = item.localConfiguration?.uri

        // Local songs come with either content:// (MediaStore) or file://
        // (downloaded) URIs that ExoPlayer can play directly — we must NOT
        // overwrite them with a placeholder.
        val isLocalUri = existingUri != null &&
            !existingUri.toString().startsWith(MusicStreamResolver.PLACEHOLDER_PREFIX) &&
            (existingUri.scheme == "content" || existingUri.scheme == "file")

        // Fill in what the browse tree already knows, so the row draws at once.
        var meta = item.mediaMetadata
        if (meta.title == null) {
            val cached = browseTree.cachedSong(item.mediaId)
            if (cached != null) {
                meta = meta.buildUpon()
                    .setTitle(cached.title)
                    .setArtist(cached.artist)
                    .setAlbumTitle(cached.album)
                    .setArtworkUri(if (cached.thumbnailUrl != null) Uri.parse(cached.thumbnailUrl) else null)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            }
        }

        // External controllers do not supply MusicQueueItem.id. Give
        // every inserted row its own identity, preserving app-owned ids.
        if (meta.extras?.getString(EXTRA_QUEUE_ITEM_ID) == null) {
            val extras = meta.extras?.let(::Bundle) ?: Bundle()
            extras.putString(EXTRA_QUEUE_ITEM_ID, java.util.UUID.randomUUID().toString())
            meta = meta.buildUpon().setExtras(extras).build()
        }

        val builder = MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setMediaMetadata(meta)
        if (isLocalUri) {
            KLog.d(TAG, "onAddMediaItems: Preserving local URI for ${item.mediaId}: $existingUri")
            builder.setUri(existingUri)
        } else {
            // Resolution happens through the prefetch and validation paths.
            builder.setUri("${MusicStreamResolver.PLACEHOLDER_PREFIX}${item.mediaId}")
        }
        return builder.build()
    }

    private companion object {
        const val TAG = "MusicService"
        const val TIMEOUT_MS = 10_000L
    }
}
