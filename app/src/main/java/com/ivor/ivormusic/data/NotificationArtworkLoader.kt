package com.ivor.ivormusic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Fetches album/thumbnail artwork for notification large icons.
 *
 * Notifications are built synchronously - the foreground service has to be able
 * to produce one immediately - but artwork arrives over the network. So loading
 * is split: [cached] answers instantly with whatever is already in memory, and
 * [load] fetches then lets the caller rebuild. A notification therefore appears
 * at once with no icon and gains the artwork a moment later, rather than
 * blocking on the image.
 *
 * Bitmaps are held in a tiny LRU-ish map. Notification icons are small and a
 * download queue only touches a handful of items, so this is deliberately
 * simpler than a real cache; Coil's own disk cache does the heavy lifting on
 * repeat loads.
 */
object NotificationArtworkLoader {

    /**
     * Cap on the decoded icon. Android scales notification large icons down to
     * roughly this anyway, and holding full-size album art would be wasteful.
     */
    private const val ICON_SIZE_PX = 256

    private const val MAX_ENTRIES = 16

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) =
                size > MAX_ENTRIES
        }
    )

    /** Whatever is already decoded for [url], without touching the network. */
    fun cached(url: String?): Bitmap? = url?.let { cache[it] }

    /**
     * Fetch and decode [url], returning null on any failure - artwork is
     * decoration, so a miss must never disturb the download itself.
     */
    suspend fun load(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        cache[url]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(ICON_SIZE_PX, ICON_SIZE_PX)
                    .allowHardware(false) // Hardware bitmaps cannot cross into a notification
                    .build()

                // The shared singleton loader, so this hits the same memory and
                // disk cache the UI already filled rather than starting cold.
                val drawable = context.imageLoader.execute(request).drawable
                    ?: return@withContext null
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: drawable.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)

                cache[url] = bitmap
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }
}
