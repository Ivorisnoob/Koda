package com.ivor.ivormusic.data

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.Locale

/**
 * Writes portable metadata into a completed M4A before it is published.
 *
 * Koda's private downloads JSON is an index, not a substitute for file tags:
 * files in public storage must retain their identity when opened in another
 * player or after Koda is uninstalled. Artwork and lyrics therefore live in
 * the M4A itself as well as in the optional companion files Koda keeps for
 * fast offline display.
 */
internal object DownloadedAudioMetadata {

    /**
     * Add portable tags without ever putting the downloaded media at risk.
     *
     * Some YouTube M4A layouts leave no free atom large enough for embedded
     * artwork. jaudiotagger then shifts the MP4 data and can reject its own
     * rewritten offsets. Performing that operation on the only downloaded
     * copy made an otherwise playable song fail. The caller can now publish
     * [sourceAudio] whenever this best-effort result fails.
     */
    fun writeCopy(
        sourceAudio: File,
        tempDirectory: File,
        song: Song,
        artworkFile: File?,
        lyrics: String?
    ): Result<File> = createMetadataCopy(sourceAudio, tempDirectory) { taggedCopy ->
        write(
            audioFile = taggedCopy,
            song = song,
            artworkFile = artworkFile,
            lyrics = lyrics
        )
    }

    private fun write(
        audioFile: File,
        song: Song,
        artworkFile: File?,
        lyrics: String?
    ) {
        val taggedAudio = AudioFileIO.read(audioFile)
        val tag = taggedAudio.tagOrCreateAndSetDefault

        tag.setField(FieldKey.TITLE, song.title.ifBlank { audioFile.nameWithoutExtension })
        song.artist.takeIf(String::isNotBlank)?.let { artist ->
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM_ARTIST, artist)
        }
        song.album.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.ALBUM, it) }
        lyrics?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.LYRICS, it) }

        artworkFile?.takeIf { it.isFile && it.length() > 0L }?.let { cover ->
            tag.deleteArtworkField()
            tag.setField(
                ArtworkFactory.createArtworkFromFile(cover).apply {
                    description = "Cover"
                    pictureType = FRONT_COVER_PICTURE_TYPE
                }
            )
        }

        taggedAudio.commit()
    }

    private const val FRONT_COVER_PICTURE_TYPE = 3
}

/**
 * Copy first, enrich second, and remove a rejected copy. Kept independent of
 * jaudiotagger so the non-destructive fallback contract has a JVM unit test.
 */
internal fun createMetadataCopy(
    sourceAudio: File,
    tempDirectory: File,
    writer: (File) -> Unit
): Result<File> {
    var taggedCopy: File? = null
    return runCatching {
        File.createTempFile("koda_tagged_", ".m4a", tempDirectory).also { copy ->
            taggedCopy = copy
            sourceAudio.copyTo(copy, overwrite = true)
            writer(copy)
        }
    }.onFailure {
        taggedCopy?.delete()
    }
}

/**
 * Serialize the exact lyric fidelity the player chose into a portable LRC
 * payload. Word timings use Enhanced LRC tags, line timings use ordinary LRC,
 * and unsynchronised lyrics remain plain text. [LyricsParser] reads all three,
 * so a downloaded track does not lose word highlighting when played offline.
 */
internal fun LyricsResult.Success.toDownloadLyrics(): String = when (syncType) {
    LyricsSyncType.PLAIN -> lines
        .asSequence()
        .map { it.text.singleLine() }
        .filter(String::isNotBlank)
        .joinToString("\n")

    LyricsSyncType.LINE -> lines
        .asSequence()
        .filter { it.timeMs >= 0L && it.text.isNotBlank() }
        .joinToString("\n") { line ->
            "[${line.timeMs.toLrcTimestamp()}]${line.text.singleLine()}"
        }

    LyricsSyncType.WORD -> lines
        .asSequence()
        .filter { it.timeMs >= 0L && it.text.isNotBlank() }
        .joinToString("\n") { line ->
            buildString {
                append('[')
                append(line.timeMs.toLrcTimestamp())
                append(']')
                if (line.contentSpans.isEmpty()) {
                    append(line.text.singleLine())
                } else {
                    line.contentSpans.forEach { span ->
                        append('<')
                        append(span.timeMs.coerceAtLeast(0L).toLrcTimestamp())
                        append('>')
                        append(span.text.singleLine(preserveEdgeSpaces = true))
                    }
                }
            }
        }
}

private fun Long.toLrcTimestamp(): String {
    val safe = coerceAtLeast(0L)
    val minutes = safe / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    val centiseconds = (safe % 1_000L) / 10L
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds)
}

private fun String.singleLine(preserveEdgeSpaces: Boolean = false): String {
    val collapsed = replace(Regex("[\\r\\n]+"), " ")
    return if (preserveEdgeSpaces) collapsed else collapsed.trim()
}
