package com.ivor.ivormusic.data

import android.net.Uri
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Represents the source of the song.
 */
@Serializable
enum class SongSource {
    LOCAL,
    YOUTUBE
}

/**
 * A unified Song model that supports both local and YouTube Music sources.
 */
@Serializable
data class Song(
    val id: String, // Changed from Long to String for YouTube video IDs
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // Duration in milliseconds
    @Serializable(with = UriAsStringSerializer::class)
    val uri: Uri? = null, // Local content URI (null for YouTube songs until resolved)
    @Serializable(with = UriAsStringSerializer::class)
    val albumArtUri: Uri? = null, // Album art URI
    val thumbnailUrl: String? = null, // YouTube thumbnail URL
    val source: SongSource = SongSource.LOCAL,
    val filePath: String? = null, // Local file path for folder filtering and embedded lyrics
    @Serializable(with = UriAsStringSerializer::class)
    val lyricsUri: Uri? = null, // Downloaded LRC companion in shared storage
    // When this song entered the user's library, epoch millis. Each source
    // stamps its own notion of "added": MediaStore DATE_ADDED for device
    // files, download completion time for downloads, like time for likes.
    // Null means unknown (sorts last), never zero.
    val dateAdded: Long? = null,
    // Device-library album position. Null means the file/provider supplied no
    // usable tag; album views sort those deterministically after tagged tracks.
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    // Canonical music release identity. Null for legacy rows or when the
    // source supplies no relationship/year; dateAdded is not a release date.
    val albumId: String? = null,
    val releaseYear: Int? = null,
    val releaseType: MusicReleaseType? = null
) {
    val highResThumbnailUrl: String?
        get() = thumbnailUrl?.let { url ->
            when {
                url.contains("googleusercontent.com") -> {
                    // Size directives (w120-h120, s120) only live after the '='
                    // separator; the opaque image token before it can itself
                    // contain "s<digits>" runs, so an unanchored replace over
                    // the whole URL corrupts it into a permanent 404.
                    val sep = url.lastIndexOf('=')
                    if (sep >= 0) {
                        url.substring(0, sep) + url.substring(sep)
                            .replace(Regex("w\\d+-h\\d+"), "w1080-h1080")
                            .replace(Regex("s\\d+"), "s1080")
                    } else url
                }
                url.contains("ytimg.com") || url.contains("youtube.com") -> {
                    // Replace low res filenames with max res
                    url.replace("mqdefault", "maxresdefault")
                       .replace("hqdefault", "maxresdefault")
                       .replace("sddefault", "maxresdefault")
                }
                else -> url
            }
        }

    companion object {
        /**
         * Creates a Song from local MediaStore data.
         */
        fun fromLocal(
            id: Long,
            title: String,
            artist: String,
            album: String,
            duration: Long,
            uri: Uri,
            albumArtUri: Uri?,
            filePath: String? = null,
            dateAdded: Long? = null,
            trackNumber: Int? = null,
            discNumber: Int? = null
        ): Song = Song(
            id = id.toString(),
            title = title,
            artist = normalizeLocalArtist(artist),
            album = normalizeLocalAlbum(album),
            duration = duration,
            uri = uri,
            albumArtUri = albumArtUri,
            source = SongSource.LOCAL,
            filePath = filePath,
            dateAdded = dateAdded,
            trackNumber = trackNumber,
            discNumber = discNumber
        )

        /**
         * Creates a Song from YouTube Music data.
         */
        fun fromYouTube(
            videoId: String,
            title: String,
            artist: String,
            album: String,
            duration: Long,
            thumbnailUrl: String?
        ): Song = Song(
            id = videoId,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            source = SongSource.YOUTUBE
        )
    }
}

/**
 * Canonical sentinels for a missing local tag, stored on [Song] at the scan
 * boundary. MediaStore reports a missing artist/album as the literal
 * "<unknown>" (and can hand back null or blank for any of the three); the
 * manual filesystem scan has no provider at all. Everything is normalised to
 * these spellings by [normalizeLocalArtist] / [normalizeLocalAlbum] so the
 * rest of the app only ever compares against one.
 */
const val UNKNOWN_ARTIST = "Unknown Artist"
const val UNKNOWN_ALBUM = "Unknown Album"

/** MediaStore's literal for a missing tag, matched case-insensitively. */
private const val MEDIastore_UNKNOWN_SENTINEL = "<unknown>"

private fun isUnknownValue(value: String?, vararg canonical: String): Boolean {
    val trimmed = value?.trim() ?: return true
    if (trimmed.isEmpty()) return true
    if (trimmed.equals(MEDIastore_UNKNOWN_SENTINEL, ignoreCase = true)) return true
    return canonical.any { trimmed.equals(it, ignoreCase = true) }
}

/**
 * Whether [artist] carries no real artist: blank, MediaStore's "<unknown>",
 * or the [UNKNOWN_ARTIST] sentinel in any casing.
 *
 * Deliberately an exact match rather than a prefix: a band actually called
 * "Unknown Mortal Orchestra" is a real artist, not a missing tag.
 */
fun isUnknownArtist(artist: String?): Boolean = isUnknownValue(artist, UNKNOWN_ARTIST)

/**
 * Whether [album] carries no real album: blank, "<unknown>", or the
 * [UNKNOWN_ALBUM] sentinel in any casing.
 */
fun isUnknownAlbum(album: String?): Boolean = isUnknownValue(album, UNKNOWN_ALBUM)

/**
 * Whether [title] carries no real title: blank, "<unknown>", or the
 * "Unknown" placeholder written when a song is rebuilt from a media item
 * with no title metadata.
 */
fun isUnknownTitle(title: String?): Boolean = isUnknownValue(title, "Unknown")

/**
 * Normalise a raw MediaStore / manual-scan artist to either a name or
 * [UNKNOWN_ARTIST]. Null, blank and "<unknown>" all become the sentinel.
 */
fun normalizeLocalArtist(raw: String?): String =
    raw?.trim().takeUnless { isUnknownArtist(it) } ?: UNKNOWN_ARTIST

/**
 * Normalise a raw MediaStore / manual-scan album to either a name or
 * [UNKNOWN_ALBUM].
 */
fun normalizeLocalAlbum(raw: String?): String =
    raw?.trim().takeUnless { isUnknownAlbum(it) } ?: UNKNOWN_ALBUM

/**
 * Normalise a raw title, falling back to [fallback] (usually the file name)
 * when the tag is missing rather than to another metadata field.
 */
fun normalizeLocalTitle(raw: String?, fallback: String): String {
    val trimmed = raw?.trim()
    return if (trimmed.isNullOrEmpty() || isUnknownTitle(trimmed)) fallback else trimmed
}

/**
 * Group device songs into albums. An album is keyed by its normalised
 * (trimmed, case-folded) name so "My Album" and "my album" do not split, and
 * each group's songs come back in disc/track order ready for an album page.
 * The display name is the first row's spelling.
 */
fun List<Song>.groupSongsByAlbum(): List<Pair<String, List<Song>>> =
    groupBy { it.album.trim().lowercase(Locale.ROOT) }
        .map { (_, songs) -> songs.first().album.trim() to songs.sortedInAlbumOrder() }
        .sortedBy { (name, _) -> name.lowercase(Locale.ROOT) }

/**
 * Group device songs into artists, keyed case-insensitively like albums.
 * The display name is the first row's spelling.
 */
fun List<Song>.groupSongsByArtist(): List<Pair<String, List<Song>>> =
    groupBy { it.artist.trim().lowercase(Locale.ROOT) }
        .map { (_, songs) -> songs.first().artist.trim() to songs }
        .sortedBy { (name, _) -> name.lowercase(Locale.ROOT) }

/**
 * The artist line for an album card or header: the single artist when every
 * known track agrees, [variousArtists] for a compilation, [UNKNOWN_ARTIST]
 * when no track names one. Never the first track's artist, which is what
 * made compilations show one contributor as the album's artist.
 */
fun albumArtistLabel(songs: List<Song>, variousArtists: String): String {
    val artists = songs.map { it.artist.trim() }.filterNot { isUnknownArtist(it) }.distinct()
    return when {
        artists.isEmpty() -> UNKNOWN_ARTIST
        artists.size == 1 -> artists.first()
        else -> variousArtists
    }
}
