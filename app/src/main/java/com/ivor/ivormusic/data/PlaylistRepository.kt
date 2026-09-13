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
    
    /**
     * Append every song in [songs] that the playlist does not already hold,
     * keeping the given order. One write for the whole batch, because each
     * save rewrites the entire playlist file - adding fifty songs through
     * [addSongToPlaylist] would rewrite it fifty times.
     *
     * Duplicates are dropped by id (within the batch as well as against the
     * playlist): [removeSongFromPlaylist] filters by id, so a carried
     * duplicate could never be removed singly.
     *
     * @return how many songs were actually added.
     */
    suspend fun addSongsToPlaylist(playlistId: String, songs: List<Song>): Int = withContext(Dispatchers.IO) {
        val playlist = _userPlaylists.value.find { it.id == playlistId } ?: return@withContext 0
        val seen = playlist.songs.mapTo(HashSet()) { it.id }
        val toAdd = songs.filter { seen.add(it.id) }
        if (toAdd.isEmpty()) return@withContext 0
        savePlaylist(playlist.copy(songs = playlist.songs + toAdd))
        toAdd.size
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
     * Generates a tonal cover with generative geometric motifs.
     *
     * No letters or text: a giant initial reads as an unfinished placeholder,
     * and two playlists starting with the same letter were near-identical
     * tiles. The artwork is bauhaus-style geometry - an arch, concentric
     * rings, ribbons, corner quarter-circles - over a **single-hue tonal
     * ramp** with one solid accent. The color system lives in
     * [PlaylistCoverArt], shared with the studio's live preview; see its KDoc
     * for why a two-hue gradient was deliberately retired.
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

        val (seedA, seedB) = coverSeeds ?: run {
            // Themeless fallback: one random hue and a distant accent hue,
            // still funnelled through the same tonal scheme rather than drawn
            // raw, so even this path cannot produce a neon melt.
            val hue = Random.nextFloat() * 360f
            Color.HSVToColor(floatArrayOf(hue, 0.6f, 0.8f)) to
                Color.HSVToColor(floatArrayOf((hue + 140f) % 360f, 0.6f, 0.8f))
        }
        val scheme = PlaylistCoverArt.scheme(seedA, seedB, id.hashCode())

        val paint = Paint()
        // Near-vertical: a tonal ramp falling with a slight lean reads like
        // studio lighting; the old corner-to-corner diagonal read like a
        // default gradient tool.
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, size * 0.22f, size.toFloat(),
            scheme.top, scheme.bottom,
            android.graphics.Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        drawCoverMotifs(canvas, id, scheme)
        
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
     * Geometric motifs over the tonal ramp: one seeded family - arch, rings,
     * ribbons, corner quarters - in the scheme's overlay color (translucent
     * white on rich/deep ramps, ink on the pale one) plus exactly one solid
     * accent disc. All positions and sizes come from a Random seeded by the
     * playlist id, so the composition is stable across regenerations and
     * distinct between playlists. Mirrored by the studio preview's DrawScope
     * twin; a family added here needs adding there.
     */
    private fun drawCoverMotifs(canvas: Canvas, id: String, scheme: PlaylistCoverArt.Scheme) {
        val size = COVER_SIZE.toFloat()
        val rng = Random(id.hashCode())
        val family = PlaylistCoverArt.family(id.hashCode())

        // Ink on a pale ground needs less alpha than white on a deep one to
        // read at the same weight.
        val fillAlpha = if (scheme.lightBase) 44 else 56
        val lineAlpha = if (scheme.lightBase) 150 else 175

        fun overlay(alpha: Int, strokeWidth: Float? = null) = Paint().apply {
            isAntiAlias = true
            color = scheme.overlay
            this.alpha = alpha
            if (strokeWidth != null) {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }
        }

        val accent = Paint().apply {
            isAntiAlias = true
            color = scheme.accent
        }

        when (family) {
            0 -> {
                // Arch: a huge disc rising past the bottom edge with a thin
                // halo ring, the accent floating above it like a small sun.
                val cx = size * (0.35f + rng.nextFloat() * 0.3f)
                val cy = size * (1.05f + rng.nextFloat() * 0.08f)
                val r = size * (0.55f + rng.nextFloat() * 0.12f)
                canvas.drawCircle(cx, cy, r, overlay(fillAlpha))
                canvas.drawCircle(
                    cx, cy, r * (1.16f + rng.nextFloat() * 0.08f),
                    overlay(lineAlpha, strokeWidth = size * 0.011f)
                )
                canvas.drawCircle(
                    cx + (rng.nextFloat() - 0.5f) * size * 0.3f,
                    (cy - r * (1.42f + rng.nextFloat() * 0.12f)).coerceAtLeast(size * 0.14f),
                    size * 0.07f,
                    accent
                )
            }
            1 -> {
                // Concentric rings drifting off a top corner, accent low on
                // the opposite side.
                val cx = size * (0.68f + rng.nextFloat() * 0.2f)
                val cy = size * (0.18f + rng.nextFloat() * 0.16f)
                val r0 = size * (0.11f + rng.nextFloat() * 0.04f)
                canvas.drawCircle(cx, cy, r0 * 0.55f, overlay(fillAlpha + 20))
                for (i in 0 until 3) {
                    canvas.drawCircle(
                        cx, cy, r0 * (1f + i * 0.62f),
                        overlay(lineAlpha - i * 42, strokeWidth = size * 0.013f)
                    )
                }
                canvas.drawCircle(
                    size * (0.18f + rng.nextFloat() * 0.12f),
                    size * (0.68f + rng.nextFloat() * 0.12f),
                    size * 0.075f,
                    accent
                )
            }
            2 -> {
                // Ribbons: three round-capped bands sweeping across the lower
                // half at a shared tilt, fading as they descend.
                val bandWidth = size * 0.105f
                val firstY = size * (0.46f + rng.nextFloat() * 0.12f)
                canvas.save()
                canvas.rotate(-14f + rng.nextFloat() * 7f, size / 2f, size / 2f)
                for (i in 0 until 3) {
                    val ribbon = overlay(
                        (fillAlpha + 26 - i * 16).coerceAtLeast(14),
                        strokeWidth = bandWidth
                    ).apply { strokeCap = Paint.Cap.ROUND }
                    val y = firstY + i * bandWidth * 1.65f
                    canvas.drawLine(
                        size * (0.10f + i * 0.07f + rng.nextFloat() * 0.05f), y,
                        size * 1.3f, y,
                        ribbon
                    )
                }
                canvas.restore()
                canvas.drawCircle(
                    size * (0.2f + rng.nextFloat() * 0.15f),
                    size * (0.16f + rng.nextFloat() * 0.12f),
                    size * 0.06f,
                    accent
                )
            }
            else -> {
                // Bauhaus corners: a filled quarter-disc in the bottom-left,
                // a stroked quarter answering from the top-right.
                canvas.drawCircle(
                    0f, size,
                    size * (0.52f + rng.nextFloat() * 0.16f),
                    overlay(fillAlpha)
                )
                canvas.drawCircle(
                    size, 0f,
                    size * (0.30f + rng.nextFloat() * 0.12f),
                    overlay(lineAlpha, strokeWidth = size * 0.013f)
                )
                canvas.drawCircle(
                    size * (0.60f + rng.nextFloat() * 0.16f),
                    size * (0.52f + rng.nextFloat() * 0.16f),
                    size * 0.065f,
                    accent
                )
            }
        }
    }

    companion object {
        private const val TAG = "PlaylistRepository"

        /** Edge of every stored cover, generated or chosen. */
        private const val COVER_SIZE = 1000
    }
}
