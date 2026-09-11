package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID
import kotlin.random.Random

class PlaylistRepository(private val context: Context) {

    private val playlistDir = File(context.filesDir, "playlists")
    private val coversDir = File(context.filesDir, "playlist_covers")
    
    // Cache of playlists
    private val _userPlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val userPlaylists: StateFlow<List<UserPlaylist>> = _userPlaylists.asStateFlow()

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
        isLenient = true
    }

    init {
        if (!playlistDir.exists()) playlistDir.mkdirs()
        if (!coversDir.exists()) coversDir.mkdirs()
        loadPlaylists()
    }

    private fun loadPlaylists() {
        // Load on background thread logic should be called from coroutine, but for init we do minimal
        // We'll expose suspend functions for ops
        val files = playlistDir.listFiles { _, name -> name.endsWith(".json") }
        val loaded = files?.mapNotNull { file ->
            try {
                json.decodeFromString<UserPlaylist>(file.readText())
            } catch (e: Exception) {
                KLog.w("PlaylistRepository", "Skipping unreadable playlist ${file.name}", e)
                null
            }
        }?.sortedByDescending { it.createdAt } ?: emptyList()
        
        _userPlaylists.value = loaded
    }
    
    suspend fun refreshPlaylists() = withContext(Dispatchers.IO) {
        loadPlaylists()
    }

    /**
     * @param coverSeeds two ARGB colors for the generated cover, taken from the
     * user's active palette by the caller. The repository cannot resolve those
     * itself without reaching into the theme layer, and a cover in colors the
     * app never uses is exactly what this used to produce.
     */
    suspend fun createPlaylist(
        name: String,
        description: String?,
        coverSeeds: Pair<Int, Int>? = null
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val coverPath = generateCoverArt(name, id, coverSeeds)

        val newPlaylist = UserPlaylist(
            id = id,
            name = name,
            description = description,
            coverUri = "file://$coverPath",
            songs = emptyList()
        )
        
        savePlaylist(newPlaylist)
        return@withContext id
    }
    
    suspend fun addSongToPlaylist(playlistId: String, song: Song) = withContext(Dispatchers.IO) {
        val currentList = _userPlaylists.value
        val playlist = currentList.find { it.id == playlistId } ?: return@withContext
        
        // Check if song already exists
        if (playlist.songs.any { it.id == song.id }) return@withContext
        
        val updatedPlaylist = playlist.copy(
            songs = playlist.songs + song
        )
        savePlaylist(updatedPlaylist)
    }
    
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        val currentList = _userPlaylists.value
        val playlist = currentList.find { it.id == playlistId } ?: return@withContext
        
        val updatedPlaylist = playlist.copy(
            songs = playlist.songs.filter { it.id != songId }
        )
        savePlaylist(updatedPlaylist)
    }

    suspend fun moveSongInPlaylist(
        playlistId: String,
        fromIndex: Int,
        toIndex: Int
    ) = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext
        if (fromIndex == toIndex) return@withContext
        if (fromIndex !in playlist.songs.indices || toIndex !in playlist.songs.indices) return@withContext

        val reorderedSongs = playlist.songs.toMutableList().apply {
            val movedSong = removeAt(fromIndex)
            add(toIndex, movedSong)
        }

        savePlaylist(playlist.copy(songs = reorderedSongs))
    }

    suspend fun replacePlaylistSongs(
        playlistId: String,
        songs: List<Song>
    ) = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext
        savePlaylist(playlist.copy(songs = songs))
    }

    suspend fun exportPlaylistToM3u(playlistId: String): String? = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext null
        PlaylistExport.toM3u(playlist)
    }

    suspend fun importPlaylistFromM3u(
        name: String,
        m3uContent: String,
        coverSeeds: Pair<Int, Int>? = null
    ): String = withContext(Dispatchers.IO) {
        val songs = PlaylistImport.parseM3u(m3uContent)
        val id = UUID.randomUUID().toString()
        val coverPath = generateCoverArt(name, id, coverSeeds)

        val newPlaylist = UserPlaylist(
            id = id,
            name = name,
            description = "Imported M3U playlist",
            coverUri = "file://$coverPath",
            songs = songs
        )

        savePlaylist(newPlaylist)
        id
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        val file = File(playlistDir, "$playlistId.json")
        if (file.exists()) file.delete()
        
        // Also delete cover art. Both kinds carry a timestamp in the name, so
        // this matches on the prefix rather than one fixed file.
        coversDir.listFiles { _, fileName ->
            fileName.startsWith("cover_$playlistId") || fileName.startsWith("custom_$playlistId")
        }?.forEach { it.delete() }

        loadPlaylists()
    }

    suspend fun updatePlaylist(
        playlistId: String,
        name: String,
        description: String?,
        coverSeeds: Pair<Int, Int>? = null
    ) = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return@withContext

        // Only a generated cover follows the name. A cover the user chose is
        // theirs and survives every rename.
        val shouldRegenerateCover = playlist.coverUri?.contains("/cover_${playlist.id}") == true &&
            playlist.name.firstOrNull()?.uppercaseChar() != trimmedName.firstOrNull()?.uppercaseChar()

        val updatedCover = if (shouldRegenerateCover) {
            "file://${generateCoverArt(trimmedName, playlist.id, coverSeeds)}"
        } else {
            playlist.coverUri
        }

        savePlaylist(
            playlist.copy(
                name = trimmedName,
                description = description?.trim().takeUnless { it.isNullOrBlank() },
                coverUri = updatedCover
            )
        )
    }
    
    /**
     * Replace [playlistId]'s artwork with the image at [source].
     *
     * Copied into the app's own storage rather than referenced: a content URI
     * from the photo picker is a one-shot grant, so a stored one is a broken
     * image the next time the app starts. The copy is decoded down and
     * centre-cropped square on the way in - the source is a phone camera photo
     * often enough that keeping it whole would put several megabytes per
     * playlist into internal storage for a tile drawn at 180dp.
     *
     * The file name carries a timestamp, and the previous one is deleted. Coil
     * caches by URL, so writing a new image to the path the old one used shows
     * the old artwork until the cache is evicted.
     *
     * @return true when the cover was replaced.
     */
    suspend fun setCustomCover(playlistId: String, source: android.net.Uri): Boolean =
        withContext(Dispatchers.IO) {
            val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext false
            val decoded = try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = false
                        // Cheap power-of-two downscale during decode, so a 48MP
                        // photo is never fully materialised in memory.
                        inSampleSize = sampleSizeFor(source)
                    }
                    android.graphics.BitmapFactory.decodeStream(input, null, options)
                }
            } catch (e: Exception) {
                KLog.w(TAG, "Could not read the chosen cover", e)
                null
            } ?: return@withContext false

            val square = cropToSquare(decoded, COVER_SIZE)
            val target = File(coversDir, "custom_${playlistId}_${System.currentTimeMillis()}.jpg")
            try {
                target.outputStream().use { out ->
                    square.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            } catch (e: Exception) {
                KLog.e(TAG, "Could not write the cover", e)
                target.delete()
                return@withContext false
            } finally {
                // Identity check, not equality: cropping an already-square
                // image hands back the source bitmap itself, and recycling
                // that twice would free the pixels out from under the write.
                if (square !== decoded) square.recycle()
                decoded.recycle()
            }

            val previous = playlist.coverUri
            savePlaylist(playlist.copy(coverUri = "file://${target.absolutePath}"))
            deleteCoverFile(previous)
            true
        }

    /**
     * Drop a chosen cover and go back to the generated one. Not a delete: a
     * playlist with no artwork at all falls back to a placeholder icon, which
     * reads as something having gone wrong rather than as a reset.
     */
    suspend fun resetCoverToGenerated(
        playlistId: String,
        coverSeeds: Pair<Int, Int>? = null
    ) = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext
        val previous = playlist.coverUri
        val regenerated = generateCoverArt(playlist.name, playlist.id, coverSeeds)
        savePlaylist(playlist.copy(coverUri = "file://$regenerated"))
        deleteCoverFile(previous)
    }

    /**
     * Deletes a cover file this repository wrote, once it has been replaced.
     * Scoped to the covers directory and to the two names generated here, so a
     * malformed or externally supplied coverUri can never delete anything else.
     */
    private fun deleteCoverFile(coverUri: String?) {
        val path = coverUri?.removePrefix("file://") ?: return
        val file = File(path)
        val isOurs = file.name.startsWith("custom_") || file.name.startsWith("cover_")
        if (isOurs && file.parentFile?.absolutePath == coversDir.absolutePath) {
            file.delete()
        }
    }

    /**
     * Power-of-two decode divisor that lands the shorter edge near
     * [COVER_SIZE]. Read from the bounds pass, which decodes no pixels.
     */
    private fun sampleSizeFor(source: android.net.Uri): Int {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, bounds)
            }
        } catch (e: Exception) {
            return 1
        }
        val shorterEdge = minOf(bounds.outWidth, bounds.outHeight)
        if (shorterEdge <= 0) return 1
        var sample = 1
        while (shorterEdge / (sample * 2) >= COVER_SIZE) sample *= 2
        return sample
    }

    /**
     * Centre crop to a square of [size], the shape every cover is drawn in.
     * Never recycles [source]; that is the caller's, and both helpers here can
     * hand it straight back when no work was needed.
     */
    private fun cropToSquare(source: Bitmap, size: Int): Bitmap {
        val edge = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(
            source,
            (source.width - edge) / 2,
            (source.height - edge) / 2,
            edge,
            edge
        )
        if (edge == size) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (scaled !== cropped && cropped !== source) cropped.recycle()
        return scaled
    }

    private suspend fun savePlaylist(playlist: UserPlaylist) {
        val file = File(playlistDir, "${playlist.id}.json")
        val jsonString = json.encodeToString(playlist)
        file.writeText(jsonString)
        loadPlaylists() // Update cache
    }
    
    /**
     * Generates a gradient cover with generative geometric motifs.
     *
     * No letters or text: a giant initial reads as an unfinished placeholder,
     * and two playlists starting with the same letter were near-identical
     * tiles. The artwork is bauhaus-style geometry instead - an orb, a ring, a
     * diagonal band, a dot field - composed from the palette gradient, so it
     * looks designed rather than stamped.
     *
     * Everything is seeded by the playlist id: re-running the generator on a
     * rename hands back the same cover, and two playlists on one palette still
     * do not look identical.
     *
     * [coverSeeds] are the user's own accent colors, resolved by the caller
     * from the active palette. Without them this fell back to two random vivid
     * hues, which is how a library themed Sage & Sand or Graphite ended up
     * full of neon covers the app's own colors never contained. The random path
     * survives only as the fallback for a caller with no theme to hand.
     */
    private fun generateCoverArt(name: String, id: String, coverSeeds: Pair<Int, Int>? = null): String {
        val size = COVER_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val (color1, color2) = if (coverSeeds != null) {
            // The playlist id, not Random: re-running the generator on a rename
            // must not hand the user a differently shaded cover, and two
            // playlists on one palette should still not look identical.
            val variation = (id.hashCode() and 0x7FFFFFFF) % 3
            shadePair(coverSeeds.first, coverSeeds.second, variation)
        } else {
            val hue1 = Random.nextFloat() * 360f
            val hue2 = (hue1 + 40 + Random.nextFloat() * 100) % 360
            Color.HSVToColor(floatArrayOf(hue1, 0.8f, 0.9f)) to
                Color.HSVToColor(floatArrayOf(hue2, 0.8f, 0.8f))
        }

        val paint = Paint()
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            color1, color2,
            android.graphics.Shader.TileMode.CLAMP
        )
        
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        drawCoverMotifs(canvas, id, color1, color2)
        
        // Timestamped, and the previous generation is deleted. Writing over
        // the same path would leave Coil - which caches by URL - serving the
        // cover from before the rename or the reset.
        val file = File(coversDir, "cover_${id}_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        coversDir.listFiles { _, fileName ->
            fileName.startsWith("cover_$id") && fileName != file.name
        }?.forEach { it.delete() }

        return file.absolutePath
    }

    /**
     * Geometric motifs over the cover gradient: one diagonal band for depth,
     * then a seeded family of orb / ring / dot-field shapes in translucent
     * white plus a small solid accent disc. All positions and sizes come from
     * a Random seeded by the playlist id, so the composition is stable across
     * regenerations and distinct between playlists.
     */
    private fun drawCoverMotifs(canvas: Canvas, id: String, color1: Int, color2: Int) {
        val size = COVER_SIZE.toFloat()
        val rng = Random(id.hashCode())
        val family = (id.hashCode() and 0x7FFFFFFF) % 3

        // Soft bottom depth so the tile never reads flat.
        val scrim = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, size * 0.5f, 0f, size,
                Color.TRANSPARENT, Color.argb(70, 0, 0, 0),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size, size, scrim)

        // Diagonal band in the gradient's own deep end, tilted for movement.
        val band = Paint().apply {
            isAntiAlias = true
            color = darken(color2, 0.7f)
            alpha = 150
        }
        val bandTop = size * (0.58f + rng.nextFloat() * 0.15f)
        canvas.save()
        canvas.rotate(-18f, size / 2f, size / 2f)
        canvas.drawRect(-size, bandTop, size * 2f, bandTop + size * 0.24f, band)
        canvas.restore()

        val softWhite = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            alpha = 45
        }
        val ringWhite = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            alpha = 150
            style = Paint.Style.STROKE
            strokeWidth = size * 0.035f
        }
        val accent = Paint().apply {
            isAntiAlias = true
            color = lighten(color1, 0.55f)
            alpha = 235
        }

        when (family) {
            0 -> {
                // Giant orb bleeding off the top-right, ring anchored low-left.
                canvas.drawCircle(
                    size * (0.78f + rng.nextFloat() * 0.15f),
                    size * (0.10f + rng.nextFloat() * 0.12f),
                    size * (0.38f + rng.nextFloat() * 0.12f),
                    softWhite
                )
                canvas.drawCircle(
                    size * (0.16f + rng.nextFloat() * 0.1f),
                    size * (0.68f + rng.nextFloat() * 0.1f),
                    size * (0.16f + rng.nextFloat() * 0.05f),
                    ringWhite
                )
                canvas.drawCircle(
                    size * (0.30f + rng.nextFloat() * 0.4f),
                    size * (0.30f + rng.nextFloat() * 0.2f),
                    size * 0.055f,
                    accent
                )
            }
            1 -> {
                // Ring high, dot field low, accent disc off to the side.
                canvas.drawCircle(
                    size * (0.24f + rng.nextFloat() * 0.12f),
                    size * (0.22f + rng.nextFloat() * 0.1f),
                    size * (0.20f + rng.nextFloat() * 0.06f),
                    ringWhite
                )
                drawDotField(
                    canvas,
                    originX = size * (0.52f + rng.nextFloat() * 0.12f),
                    originY = size * (0.60f + rng.nextFloat() * 0.1f),
                    step = size * 0.075f,
                    radius = size * 0.013f
                )
                canvas.drawCircle(
                    size * (0.68f + rng.nextFloat() * 0.14f),
                    size * (0.24f + rng.nextFloat() * 0.12f),
                    size * 0.075f,
                    accent
                )
            }
            else -> {
                // Twin orbs on a diagonal plus thin parallel lines.
                val orbCX = size * (0.7f + rng.nextFloat() * 0.15f)
                val orbCY = size * (0.24f + rng.nextFloat() * 0.12f)
                canvas.drawCircle(orbCX, orbCY, size * 0.30f, softWhite)
                canvas.drawCircle(
                    orbCX - size * 0.34f, orbCY + size * 0.36f,
                    size * 0.17f, softWhite
                )
                val lines = Paint().apply {
                    isAntiAlias = true
                    color = Color.WHITE
                    alpha = 110
                    strokeWidth = size * 0.012f
                }
                val lineY = size * (0.62f + rng.nextFloat() * 0.1f)
                canvas.save()
                canvas.rotate(-18f, size / 2f, size / 2f)
                canvas.drawLine(-size, lineY, size * 2f, lineY, lines)
                canvas.drawLine(-size, lineY + size * 0.05f, size * 2f, lineY + size * 0.05f, lines)
                canvas.restore()
                canvas.drawCircle(
                    size * (0.2f + rng.nextFloat() * 0.15f),
                    size * (0.2f + rng.nextFloat() * 0.12f),
                    size * 0.05f,
                    accent
                )
            }
        }
    }

    /** 4x4 dot field used by one motif family. */
    private fun drawDotField(
        canvas: Canvas,
        originX: Float,
        originY: Float,
        step: Float,
        radius: Float
    ) {
        val dot = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            alpha = 70
        }
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                canvas.drawCircle(originX + col * step, originY + row * step, radius, dot)
            }
        }
    }

    /** Scale the RGB channels toward black, keeping alpha. */
    private fun darken(color: Int, factor: Float): Int {
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }

    /** Mix toward white by [amount] 0..1, keeping alpha. */
    private fun lighten(color: Int, amount: Float): Int {
        val mix = amount.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) + (255 - Color.red(color)) * mix).toInt().coerceIn(0, 255),
            (Color.green(color) + (255 - Color.green(color)) * mix).toInt().coerceIn(0, 255),
            (Color.blue(color) + (255 - Color.blue(color)) * mix).toInt().coerceIn(0, 255)
        )
    }

    /**
     * Two ends of a gradient from the palette's own accents, nudged per
     * [variation] so a library of playlists on one palette still has some
     * range instead of twenty identical tiles.
     *
     * Value and saturation are pinned rather than taken from the seed: the
     * pastel and aesthetic palettes are pale enough that a cover made from
     * them raw would look washed out.
     */
    private fun shadePair(seedA: Int, seedB: Int, variation: Int): Pair<Int, Int> {
        val hsvA = FloatArray(3).also { Color.colorToHSV(seedA, it) }
        val hsvB = FloatArray(3).also { Color.colorToHSV(seedB, it) }
        val spread = when (variation) {
            0 -> 0f
            1 -> 18f
            else -> -18f
        }
        val first = Color.HSVToColor(
            floatArrayOf((hsvA[0] + spread + 360f) % 360f, 0.72f, 0.86f)
        )
        val second = Color.HSVToColor(
            floatArrayOf((hsvB[0] - spread + 360f) % 360f, 0.78f, 0.62f)
        )
        return first to second
    }

    companion object {
        private const val TAG = "PlaylistRepository"

        /** Edge of every stored cover, generated or chosen. */
        private const val COVER_SIZE = 1000
    }
}
