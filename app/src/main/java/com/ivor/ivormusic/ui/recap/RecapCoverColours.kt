package com.ivor.ivormusic.ui.recap

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Each song's cover colour, the one thing a recap needs from the network.
 *
 * A year can hold a thousand songs, and the pressing, the calendar and the
 * spines colour every one of them, so a colour is worked out once per song -
 * from a 64-pixel cover through the shared image loader, which often already
 * has it - and kept in a small file. A recap opens on what is known and fills
 * in as the rest arrive, the most visible songs first.
 *
 * Derived data, not history: kept out of backups (it rebuilds itself) and
 * cleared with the app's files. It names songs only by id.
 */
internal class RecapCoverColours(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)

    // Per instance, not process-wide: the one activity-scoped RecapViewModel
    // owns the only instance, so nothing on another surface needs to see it.
    private val lock = Mutex()
    @Volatile private var memory: Map<String, Int>? = null

    /** What is already known, read once. */
    suspend fun known(): Map<String, Color> = withContext(Dispatchers.IO) {
        lock.withLock { loadLocked().mapValues { Color(it.value) } }
    }

    /**
     * Work out the colours of [songs] (id to thumbnail) not yet known, in the
     * order given, reporting each as it lands. Saved every few, so a recap
     * left early keeps what it found.
     */
    suspend fun fill(songs: List<Pair<String, String?>>, onColour: (String, Color) -> Unit) = withContext(Dispatchers.IO) {
        val known = lock.withLock { loadLocked().toMutableMap() }
        var unsaved = 0
        for ((id, thumbnail) in songs) {
            if (id in known || thumbnail.isNullOrBlank()) continue
            val colour = extract(thumbnail) ?: continue
            known[id] = colour.toArgb()
            onColour(id, colour)
            if (++unsaved >= 12) {
                save(known)
                unsaved = 0
            }
        }
        if (unsaved > 0) save(known)
    }

    private suspend fun extract(thumbnail: String): Color? {
        return try {
            val request = ImageRequest.Builder(appContext)
                // Ask Google's image service for a small one where it can scale.
                .data(googleImageAtSize(thumbnail, 96) ?: thumbnail)
                .allowHardware(false) // Palette needs a software bitmap
                .size(64)
                .build()
            val result = appContext.imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.let { (it.drawable as? BitmapDrawable)?.bitmap } ?: return null
            val palette = Palette.from(bitmap).maximumColorCount(12).generate()
            val dominant = palette.dominantSwatch ?: return null
            // The vivid colour when there is a real amount of it; otherwise
            // what the cover mostly is - a black sleeve is black.
            val vivid = palette.vibrantSwatch?.takeIf { it.population >= dominant.population * 0.2f }
            Color((vivid ?: dominant).rgb)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun loadLocked(): Map<String, Int> {
        memory?.let { return it }
        val loaded = try {
            if (!file.exists()) emptyMap() else {
                val json = JSONObject(file.readText())
                json.keys().asSequence().associateWith { json.getInt(it) }
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Cover colours unreadable, starting over")
            emptyMap()
        }
        memory = loaded
        return loaded
    }

    private suspend fun save(colours: Map<String, Int>) {
        lock.withLock {
            try {
                val json = JSONObject()
                colours.forEach { (k, v) -> json.put(k, v) }
                file.writeText(json.toString())
                memory = HashMap(colours)
            } catch (e: Exception) {
                KLog.w(TAG, "Could not save cover colours")
            }
        }
    }

    private companion object {
        const val TAG = "RecapColours"
        const val FILE_NAME = "recap_cover_colours.json"
    }
}
