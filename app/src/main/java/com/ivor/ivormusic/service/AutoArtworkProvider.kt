package com.ivor.ivormusic.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ivor.ivormusic.data.PlaylistCoverArt
import java.io.File
import java.io.FileNotFoundException

/**
 * Category artwork for the Android Auto browse tree, served as plain PNGs.
 *
 * The Library tiles (Downloaded, Liked Songs, Recently Played, ...) carry no
 * song art of their own, and head units render artless grid tiles as a broken
 * image glyph - the warning triangles in the field photo. Every browsable
 * category therefore gets a deterministic tile here.
 *
 * Served through an exported provider with no read permission on purpose,
 * rather than as `android.resource://` vector URIs: the car-side image loader
 * cannot decode a vector XML stream, so vectors fail exactly where this art
 * has to work, while a PNG behind a content URI loads everywhere the phone's
 * own ContentResolver can read - which needs no grant for an exported
 * provider serving static art with no user data behind it. Offline by
 * construction, unlike an https fallback.
 *
 * The tiles reuse [PlaylistCoverArt]'s color system (single-hue tonal ramp
 * plus one accent disc) with a centered initial, so they read as the app's
 * own generated covers. Fixed per-category hues are identity colors - the
 * documented exception to the color rule, the way SponsorCategory swatches
 * are - because a tile minted on the head unit cannot see the phone's
 * palette, and seven random hues would be seven random tiles.
 */
class AutoArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? =
        if (AutoArtwork.keyFromUri(uri) != null) "image/png" else null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("read-only: $uri")
        val key = AutoArtwork.keyFromUri(uri) ?: throw FileNotFoundException(uri.toString())
        val context = context ?: throw FileNotFoundException(uri.toString())
        val file = AutoArtwork.fileFor(context, key)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    // The car only ever opens art files. Query/insert/update/delete exist
    // because ContentProvider declares them, not because anyone calls them.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}

/** Rendering and addressing for [AutoArtworkProvider]. Pure except [fileFor]. */
object AutoArtwork {

    /** Browse mediaId -> tile key. Null means "no category tile for this id". */
    fun categoryKey(mediaId: String?): String? = when (mediaId) {
        "LIBRARY" -> "library"
        "RECOMMENDED" -> "recommended"
        "PLAYLISTS" -> "playlists"
        "DOWNLOADS" -> "downloads"
        "LIKED" -> "liked"
        "RECENT" -> "recent"
        "LOCAL_SONGS" -> "device"
        else -> null
    }

    /**
     * Authority for these URIs. Built from the runtime package name, never a
     * literal: the debug build carries an applicationIdSuffix and a literal
     * would address a provider that is not installed there.
     */
    fun authority(context: android.content.Context): String =
        "${context.packageName}.autoartwork"

    fun uriFor(context: android.content.Context, key: String): Uri =
        Uri.Builder()
            .scheme("content")
            .authority(authority(context))
            .appendPath("category")
            .appendPath("$key.png")
            .build()

    /** Pure path half, so unit tests can pin the shape without android.net.Uri. */
    fun pathFor(key: String): String = "category/$key.png"

    fun keyFromUri(uri: Uri): String? {
        if (uri.scheme != "content") return null
        if (uri.pathSegments.size != 2 || uri.pathSegments[0] != "category") return null
        return uri.pathSegments[1].removeSuffix(".png").takeIf { it in TILE_HUES }
    }

    /**
     * The rendered tile, cached under cacheDir. Re-rendered when missing
     * (first browse, cache eviction) - a few Canvas ops, no network, no
     * theme read, so it is also safe to call from a binder thread.
     */
    @Synchronized
    fun fileFor(context: android.content.Context, key: String): File {
        require(key in TILE_HUES) { "unknown auto artwork key: $key" }
        val dir = File(context.cacheDir, "auto-artwork").also { it.mkdirs() }
        val file = File(dir, "$key.png")
        if (!file.isFile) {
            renderTile(key).compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
        }
        return file
    }

    /** Fixed hues per category: identity, not theme. See the file KDoc. */
    private val TILE_HUES: Map<String, Pair<Float, Float>> = mapOf(
        "library" to (260f to 295f),
        "recommended" to (20f to 45f),
        "playlists" to (210f to 240f),
        "downloads" to (175f to 210f),
        "liked" to (350f to 320f),
        "recent" to (35f to 15f),
        "device" to (120f to 90f)
    )

    private val TILE_LETTERS: Map<String, Char> = mapOf(
        "library" to 'L',
        "recommended" to 'R',
        "playlists" to 'P',
        "downloads" to 'D',
        "liked" to 'L',
        "recent" to 'R',
        "device" to 'O'
    )

    private const val TILE_PX = 512

    private fun renderTile(key: String): Bitmap {
        val size = TILE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (hueA, hueB) = TILE_HUES.getValue(key)
        val scheme = PlaylistCoverArt.scheme(
            seedA = Color.HSVToColor(floatArrayOf(hueA, 0.6f, 0.8f)),
            seedB = Color.HSVToColor(floatArrayOf(hueB, 0.6f, 0.8f)),
            key = key.hashCode()
        )

        // Near-vertical tonal ramp, the same lean as generated covers.
        Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                0f, 0f, size * 0.22f, size.toFloat(),
                scheme.top, scheme.bottom,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), this)
        }

        // The scheme's one accent pop, top-right.
        Paint().apply {
            isAntiAlias = true
            color = scheme.accent
            canvas.drawCircle(size * 0.76f, size * 0.24f, size * 0.10f, this)
        }

        // Centered initial in the scheme's overlay color.
        Paint().apply {
            isAntiAlias = true
            color = scheme.overlay
            alpha = 255
            textSize = size * 0.44f
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.BOLD
            )
            textAlign = Paint.Align.CENTER
            val letter = TILE_LETTERS.getValue(key).toString()
            val centerY = size / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(letter, size / 2f, centerY, this)
        }
        return bitmap
    }
}
