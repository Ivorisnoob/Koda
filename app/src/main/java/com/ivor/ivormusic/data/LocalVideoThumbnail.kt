package com.ivor.ivormusic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coil model for a frame of a device video. Wrapping the Uri rather than
 * passing it directly keeps this off the path of every other content:// image
 * the app loads - album art among them - which Coil's own fetchers already
 * handle correctly.
 */
data class LocalVideoThumbnail(val uri: Uri)

/**
 * Draws a device video's poster frame.
 *
 * Written rather than taken from coil-video because that decoder works from an
 * ImageSource: Coil hands it the fetched bytes, so a content:// video is first
 * buffered to a cache file before a single frame can be read - copying a
 * multi-gigabyte movie to make a 200dp card. MediaStore has already generated a
 * thumbnail for every file it indexed, and [android.content.ContentResolver.loadThumbnail]
 * serves that directly.
 *
 * The retriever fallback covers files MediaStore indexed without producing a
 * thumbnail, which is normal for anything the system scanner could not open
 * with its own extractors - the same exotic containers this feature exists to
 * play. It decodes one scaled frame and no more.
 *
 * Registering this also puts device frames in the same memory and disk cache as
 * every other image in the app, so scrolling a folder re-reads nothing.
 */
class LocalVideoThumbnailFetcher(
    private val context: Context,
    private val data: LocalVideoThumbnail,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val width = options.size.width.pxOr(DEFAULT_THUMBNAIL_PX)
        val height = options.size.height.pxOr(DEFAULT_THUMBNAIL_PX)
        val bitmap = loadFromProvider(width, height) ?: decodeFirstFrame(width, height)
        bitmap?.let {
            DrawableResult(
                drawable = BitmapDrawable(context.resources, it),
                // The frame is scaled to the request rather than being the
                // file's full resolution, which is exactly what isSampled means.
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        }
    }

    private fun loadFromProvider(width: Int, height: Int): Bitmap? = try {
        context.contentResolver.loadThumbnail(data.uri, Size(width, height), null)
    } catch (e: Exception) {
        // Missing thumbnail, revoked access, or a provider that does not
        // implement it. All three fall through to decoding a frame.
        KLog.d("LocalVideoThumb", "No provider thumbnail for ${data.uri}: ${e.message}")
        null
    }

    private fun decodeFirstFrame(width: Int, height: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, data.uri)
            // A frame one second in rather than at zero: plenty of recordings
            // and encodes open on a black or single-color frame, which reads as
            // a broken thumbnail. OPTION_CLOSEST_SYNC keeps it to one keyframe
            // decode. Falls back to the first frame for videos shorter than
            // that, where the request returns null.
            retriever.getScaledFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                width,
                height,
            ) ?: retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                width,
                height,
            )
        } catch (e: Exception) {
            KLog.d("LocalVideoThumb", "Could not decode a frame of ${data.uri}: ${e.message}")
            null
        } finally {
            // release() rather than close(): close() is API 29+ AutoCloseable
            // and release() is what every API level accepts.
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun Dimension.pxOr(fallback: Int): Int =
        (this as? Dimension.Pixels)?.px?.takeIf { it > 0 } ?: fallback

    class Factory(private val context: Context) : Fetcher.Factory<LocalVideoThumbnail> {
        override fun create(
            data: LocalVideoThumbnail,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = LocalVideoThumbnailFetcher(context, data, options)
    }

    /**
     * Without a keyer Coil cannot name this model, and a request whose key is
     * null skips the memory cache entirely - every card would re-decode on
     * every scroll pass.
     */
    class ThumbnailKeyer : Keyer<LocalVideoThumbnail> {
        override fun key(data: LocalVideoThumbnail, options: Options): String =
            "local-video-frame:${data.uri}"
    }

    private companion object {
        // Used only when the request did not constrain that axis, which happens
        // for a wrap-content image. Large enough for a full-width card.
        const val DEFAULT_THUMBNAIL_PX = 512
    }
}
