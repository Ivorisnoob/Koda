package com.ivor.ivormusic.ui.lyricsshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * What the lyrics share screen does with a finished card: send it to another
 * app, keep it in the gallery, or copy the words.
 *
 * The card arrives as the bitmap of its own graphics layer, laid out at the
 * export size ([CARD_EXPORT_WIDTH_PX]), so the text is rasterised at full
 * resolution rather than scaled up from the preview on screen.
 */
internal object LyricsShareExport {
    private const val TAG = "LyricsShare"
    private const val SHARE_DIR = "shared"

    /** A software copy, which PNG compression needs; a layer's bitmap may be a hardware one. */
    private fun ImageBitmap.toSoftware(): Bitmap {
        val source = asAndroidBitmap()
        return if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
    }

    private fun fileName(song: Song): String {
        val base = "${song.artist} ${song.title}".replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(60)
        return "Koda-lyrics-${base.ifEmpty { "card" }}-${System.currentTimeMillis()}.png"
    }

    private fun write(bitmap: ImageBitmap, out: OutputStream) {
        out.use { bitmap.toSoftware().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Opens the system share sheet with the card. False when the image could not be written. */
    suspend fun share(context: Context, bitmap: ImageBitmap, song: Song): Boolean {
        val uri = withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                // Only the newest card is kept: each share is a fresh file, so a
                // receiving app never reads a stale image under a reused name.
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, fileName(song))
                write(bitmap, file.outputStream())
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e(TAG, "Could not write the share image", e)
                null
            }
        } ?: return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // The ClipData is what gives the share sheet its preview thumbnail.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.lyrics_share_title))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        return true
    }

    /**
     * Saves the card to Pictures/Koda. No storage permission: from Android 10
     * an app may add its own images to MediaStore. Android 8 and 9 have no
     * RELATIVE_PATH and would need WRITE_EXTERNAL_STORAGE for Pictures, so
     * there the card goes to Koda's own Pictures folder on shared storage
     * (no permission from API 19) and is handed to the media scanner so
     * gallery apps list it.
     */
    suspend fun save(context: Context, bitmap: ImageBitmap, song: Song): Boolean = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return@withContext saveLegacy(context, bitmap, song)
        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName(song))
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Koda")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
            val stream = resolver.openOutputStream(uri) ?: error("No stream for $uri")
            write(bitmap, stream)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(TAG, "Could not save the card", e)
            // A half-written entry would show in the gallery as a broken image.
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            false
        }
    }

    private fun saveLegacy(context: Context, bitmap: ImageBitmap, song: Song): Boolean {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false
        val file = File(directory, fileName(song))
        return try {
            directory.mkdirs()
            write(bitmap, file.outputStream())
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/png"),
                null
            )
            true
        } catch (e: Exception) {
            KLog.e(TAG, "Could not save the card", e)
            file.delete()
            false
        }
    }

    /** The lines, then the song they are from, as plain text. */
    fun copy(context: Context, lines: List<String>, song: Song) {
        val credit = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" - ")
        val text = buildString {
            append(lines.joinToString("\n"))
            if (credit.isNotEmpty()) append("\n\n").append(credit)
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(song.title, text))
    }
}
