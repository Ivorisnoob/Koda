package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap

/** Reads lyrics that travel with a local audio file, without using the network. */
internal class LocalLyricsSource(
    private val embeddedLyricsReader: (File) -> String? = ::readEmbeddedLyrics
) {
    private class CachedSidecars(val stamp: Long, val files: List<File>)

    /**
     * Bounded because a shuffle across a library walks through folders without
     * ever coming back, and holding every one of them would be a leak with a
     * slow fuse. Access-ordered, so the folder being played out of stays.
     */
    private val sidecarCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedSidecars>(MAX_CACHED_DIRECTORIES, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CachedSidecars>?
            ): Boolean = size > MAX_CACHED_DIRECTORIES
        }
    )

    fun find(song: Song): LyricsResult.Success? {
        if (song.source != SongSource.LOCAL) return null
        val audioFile = song.filePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: return null

        findSidecar(audioFile)?.let { sidecar ->
            readSidecar(sidecar)?.let(LyricsParser::parse)?.let { parsed ->
                return parsed.toResult("Local ${sidecar.extension.uppercase()}")
            }
        }

        val embedded = runCatching { embeddedLyricsReader(audioFile) }
            .onFailure { KLog.w(TAG, "Could not read embedded lyrics from ${audioFile.name}", it) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= MAX_EMBEDDED_LYRICS_CHARS }

        return embedded
            ?.let(LyricsParser::parse)
            ?.toResult("Embedded lyrics")
    }

    private fun findSidecar(audioFile: File): File? {
        val parent = audioFile.parentFile ?: return null
        val stem = audioFile.nameWithoutExtension

        // Avoid listing the directory in the common case. Shared storage is
        // case-insensitive, so on a phone this probe is the whole answer and
        // costs one stat per extension.
        SIDECAR_EXTENSIONS.forEach { extension ->
            File(parent, "$stem.$extension").takeIf(File::isFile)?.let { return it }
        }

        // The fallback makes matching case-insensitive for files copied from
        // case-sensitive hosts, and has to honor the same extension preference
        // the probe above does: readdir order is arbitrary, so taking the first
        // candidate would let the filesystem decide whether Track.LRC or
        // Track.TTML wins.
        val candidates = sidecarsIn(parent)
        if (candidates.isEmpty()) return null
        return SIDECAR_EXTENSIONS.firstNotNullOfOrNull { extension ->
            candidates.firstOrNull { candidate ->
                candidate.extension.equals(extension, ignoreCase = true) &&
                    candidate.nameWithoutExtension.equals(stem, ignoreCase = true)
            }
        }
    }

    /**
     * The lyric files sitting in [directory], remembered until the directory
     * itself changes.
     *
     * Most libraries carry no sidecars at all, which is exactly the case that
     * reaches the listing: without this, every track change lists the whole
     * music folder again to find nothing, and these are the devices that keep
     * thousands of files on an SD card. Keyed on `lastModified` so a sidecar
     * dropped in while the app is running is still picked up - adding or
     * removing a file bumps its directory's timestamp.
     */
    private fun sidecarsIn(directory: File): List<File> {
        val stamp = directory.lastModified()
        sidecarCache[directory.path]?.takeIf { it.stamp == stamp }?.let { return it.files }

        val files = runCatching {
            directory.listFiles { candidate: File ->
                candidate.isFile && candidate.extension.lowercase() in SIDECAR_EXTENSIONS
            }?.toList()
        }.onFailure {
            KLog.w(TAG, "Could not list ${directory.name} for lyric sidecars", it)
        }.getOrNull().orEmpty()

        sidecarCache[directory.path] = CachedSidecars(stamp, files)
        return files
    }

    private fun readSidecar(file: File): String? = runCatching {
        if (file.length() !in 1..MAX_SIDECAR_BYTES) return@runCatching null
        decodeSidecar(file.readBytes())
    }.onFailure {
        KLog.w(TAG, "Could not read lyric sidecar ${file.name}", it)
    }.getOrNull()

    /**
     * Decode a lyric sidecar, honoring a byte order mark.
     *
     * UTF-8 is the right default and the only encoding [LyricsParser] strips a
     * BOM for, but Windows editors save "Unicode" .lrc files as UTF-16 with
     * one, and reading those as UTF-8 does not fail. It yields a string of
     * replacement characters that no longer looks like LRC, so it parses as
     * plain text: the player shows a screen of garbage *and*, because a local
     * hit stops the search, never falls through to the embedded tag or a
     * provider. Sniffing two bytes is the whole fix. A file without a mark is
     * UTF-8, which is what every lyric tool other than Notepad writes.
     */
    private fun decodeSidecar(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> String(bytes, Charsets.UTF_8)
    }

    private fun ParsedLyrics.toResult(provider: String) = LyricsResult.Success(
        lines = lines,
        provider = provider,
        syncType = syncType
    )

    private companion object {
        const val TAG = "LocalLyricsSource"
        const val MAX_SIDECAR_BYTES = 2L * 1024L * 1024L
        const val MAX_EMBEDDED_LYRICS_CHARS = 2 * 1024 * 1024
        const val MAX_CACHED_DIRECTORIES = 8
        val SIDECAR_EXTENSIONS = listOf("lrc", "ttml")

        fun readEmbeddedLyrics(audioFile: File): String? {
            if (!audioFile.isFile || !audioFile.canRead()) return null
            return AudioFileIO.read(audioFile)
                .tag
                ?.getFirst(FieldKey.LYRICS)
                ?.takeIf(String::isNotBlank)
        }
    }
}
