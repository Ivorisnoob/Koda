package com.ivor.ivormusic.data

import androidx.annotation.StringRes
import com.ivor.ivormusic.R
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject

/**
 * A release's kind.
 *
 * [wireLabel] is the English text WEB_REMIX puts on a card, matched when
 * parsing - it is a wire format, not a caption. Anything shown to someone
 * reads [labelRes] instead, or a Hindi user gets "Single" on their album
 * header where every other noun on the page is translated. The constant
 * `name` is what the download, saved-release and last-song stores persist,
 * so it is frozen like every other stored enum.
 */
@Serializable
enum class MusicReleaseType(val wireLabel: String, @StringRes val labelRes: Int) {
    ALBUM("Album", R.string.label_album),
    EP("EP", R.string.label_ep),
    SINGLE("Single", R.string.label_single)
}

/** WEB_REMIX metadata, verified September 2026 against search, browse and next.
 * A text run's position is presentation, not identity: collaborators occupy the
 * same positions that single-artist rows use for albums. Only album endpoints
 * establish an album relationship. Missing metadata stays missing.
 */
internal object MusicMetadata {
    private val yearPattern = Regex("(?:19|20)\\d{2}")
    private val durationPattern = Regex("\\d{1,3}:\\d{2}(?::\\d{2})?")

    fun objects(node: Any?, key: String): List<JSONObject> = buildList {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let(::add)
                node.keys().forEach { if (it != key) addAll(objects(node.opt(it), key)) }
            }
            is JSONArray -> for (i in 0 until node.length()) addAll(objects(node.opt(i), key))
        }
    }

    fun text(node: JSONObject?): String = node?.optString("simpleText")?.takeIf(String::isNotBlank)
        ?: runs(node).joinToString("") { it.optString("text") }

    private fun runs(node: JSONObject?): List<JSONObject> = node?.optJSONArray("runs").rows()
    private fun JSONArray?.rows(): List<JSONObject> = if (this == null) emptyList()
        else (0 until length()).mapNotNull { optJSONObject(it) }

    fun endpoint(node: JSONObject?): JSONObject? = node?.optJSONObject("navigationEndpoint")
        ?.optJSONObject("browseEndpoint")

    private fun pageType(endpoint: JSONObject?): String = endpoint
        ?.optJSONObject("browseEndpointContextSupportedConfigs")
        ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()

    private fun isAlbum(endpoint: JSONObject?): Boolean = pageType(endpoint) == "MUSIC_PAGE_TYPE_ALBUM" ||
        (pageType(endpoint).isBlank() && endpoint?.optString("browseId")?.startsWith("MPRE") == true)

    private fun isArtist(endpoint: JSONObject?): Boolean = pageType(endpoint) in
        setOf("MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL") ||
        (pageType(endpoint).isBlank() && endpoint?.optString("browseId")?.startsWith("UC") == true)

    private fun columns(row: JSONObject): List<JSONObject?> {
        val columns = row.optJSONArray("flexColumns") ?: return emptyList()
        // Keep empty slots: dropping a missing title column would promote the
        // artist column to a title and recreate a positional metadata bug.
        return (0 until columns.length()).map { index ->
            columns.optJSONObject(index)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
        }
    }

    private fun metadataRuns(row: JSONObject): List<JSONObject> =
        columns(row).drop(1).flatMap(::runs) + runs(row.optJSONObject("subtitle")) +
            runs(row.optJSONObject("longBylineText"))

    private fun artists(runs: List<JSONObject>): String = runs
        .filter { isArtist(endpoint(it)) }.map { it.optString("text").trim() }
        .filter(String::isNotBlank).distinct().joinToString(", ")

    private fun year(runs: List<JSONObject>): Int? = runs.asSequence()
        .filter { endpoint(it) == null }
        .map { it.optString("text").trim() }
        .firstOrNull { it.matches(yearPattern) }?.toIntOrNull()

    private fun type(runs: List<JSONObject>): MusicReleaseType? = runs.asSequence()
        .filter { endpoint(it) == null }
        .mapNotNull { run -> MusicReleaseType.entries.firstOrNull { it.wireLabel.equals(run.optString("text").trim(), true) } }
        .firstOrNull()

    private fun thumbnail(row: JSONObject): String? =
        findThumbnail(row.optJSONObject("thumbnail") ?: row.optJSONObject("thumbnailRenderer"))

    private fun findThumbnail(node: Any?): String? = when (node) {
        is JSONObject -> node.optJSONArray("thumbnails")?.rows()?.asReversed()?.firstNotNullOfOrNull {
            it.optString("url").takeIf(String::isNotBlank)
        }
            ?: node.keys().asSequence().mapNotNull { findThumbnail(node.opt(it)) }.firstOrNull()
        else -> null
    }?.takeIf(String::isNotBlank)

    private fun duration(text: String): Long? {
        if (!text.matches(durationPattern)) return null
        return text.split(':').fold(0L) { total, part -> total * 60 + part.toLong() } * 1000
    }

    fun song(row: JSONObject): Song? {
        val titleText = columns(row).firstOrNull() ?: row.optJSONObject("title")
        val title = text(titleText).takeIf(String::isNotBlank) ?: return null
        // Ignore menu commands: a release card can have a play menu without
        // being a track. Responsive album search rows have a browse endpoint.
        if (isAlbum(endpoint(row))) return null
        val videoId = row.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf(String::isNotBlank)
            ?: row.optString("videoId").takeIf(String::isNotBlank)
            ?: row.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf(String::isNotBlank)
            ?: runs(titleText).firstNotNullOfOrNull {
                it.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf(String::isNotBlank)
            }
            ?: objects(row.optJSONObject("overlay"), "watchEndpoint").firstOrNull()?.optString("videoId")?.takeIf(String::isNotBlank)
            ?: return null
        val metadata = metadataRuns(row)
        val albumRun = metadata.firstOrNull { isAlbum(endpoint(it)) }
        // Plain creator text is supplied by /next shortBylineText, or by the
        // first subtitle segment on cards whose creator has no link. Never
        // use that fallback for an album, a year, or a count.
        val artist = artists(metadata).ifBlank {
            text(row.optJSONObject("shortBylineText")).ifBlank {
                val first = metadata.firstOrNull()
                first?.optString("text")?.trim()?.takeIf {
                    endpoint(first) == null && it.isNotBlank() &&
                        it !in listOf("Song", "Video", "Music video", "Album", "Single", "EP") &&
                        !it.matches(Regex("\\d.*")) && it != "•"
                }.orEmpty()
            }
        }.ifBlank { UNKNOWN_ARTIST }
        val length = text(row.optJSONObject("lengthText"))
        val fixed = row.optJSONArray("fixedColumns").rows().mapNotNull {
            it.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")
        }.map(::text)
        val duration = (listOf(length) + fixed + metadata.filter { endpoint(it) == null }.map { it.optString("text").trim() })
            .firstNotNullOfOrNull(::duration) ?: 0L
        return Song(
            id = videoId,
            title = title,
            artist = artist,
            album = albumRun?.optString("text")?.takeIf(String::isNotBlank) ?: UNKNOWN_ALBUM,
            duration = duration,
            thumbnailUrl = thumbnail(row),
            source = SongSource.YOUTUBE,
            albumId = endpoint(albumRun)?.optString("browseId")?.takeIf(String::isNotBlank),
            releaseYear = year(metadata),
        )
    }

    fun release(row: JSONObject, defaultType: MusicReleaseType? = null, artist: String = ""): PlaylistDisplayItem? {
        val endpoint = endpoint(row) ?: return null
        if (!isAlbum(endpoint)) return null
        val id = endpoint.optString("browseId").takeIf(String::isNotBlank) ?: return null
        val title = text(columns(row).firstOrNull() ?: row.optJSONObject("title")).takeIf(String::isNotBlank) ?: return null
        val metadata = metadataRuns(row)
        return PlaylistDisplayItem(
            name = title,
            url = "https://music.youtube.com/browse/$id",
            uploaderName = artists(metadata).ifBlank { artist },
            thumbnailUrl = thumbnail(row),
            releaseType = type(metadata) ?: defaultType,
            releaseYear = year(metadata),
        )
    }

    fun releaseRows(root: JSONObject, defaultType: MusicReleaseType? = null, artist: String = ""): List<PlaylistDisplayItem> =
        (objects(root, "musicTwoRowItemRenderer") + objects(root, "musicResponsiveListItemRenderer"))
            .mapNotNull { release(it, defaultType, artist) }.distinctBy { it.id }

    /**
     * The identity block of an InnerTube artist browse
     * (`musicImmersiveHeaderRenderer`): name, biography, monthly audience and
     * the widest banner thumbnail. Null when the response carries no immersive
     * header (e.g. a discography page). Verified September 2026.
     */
    fun artistHeader(root: JSONObject): ArtistHeader? {
        val header = objects(root, "musicImmersiveHeaderRenderer").firstOrNull() ?: return null
        val name = text(header.optJSONObject("title")).takeIf(String::isNotBlank) ?: return null
        return ArtistHeader(
            name = name,
            bio = text(header.optJSONObject("description")).takeIf(String::isNotBlank),
            monthlyAudience = text(header.optJSONObject("monthlyListenerCount")).takeIf(String::isNotBlank),
            bannerUrl = thumbnail(header),
        )
    }

    /** The carousel shelf with this exact header title, or null. Titles are
     * matched in English, the language every request pins (`hl=en`). */
    fun carousel(root: JSONObject, title: String): JSONObject? =
        objects(root, "musicCarouselShelfRenderer").firstOrNull { shelf ->
            text(
                shelf.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title")
            ) == title
        }

    /** "Fans might also like" shelf: artist id, name, audience, thumbnail. */
    fun similarArtists(root: JSONObject): List<SimilarArtist> {
        val shelf = carousel(root, "Fans might also like") ?: return emptyList()
        return objects(shelf, "musicTwoRowItemRenderer").mapNotNull { row ->
            val id = endpoint(row)?.optString("browseId")?.takeIf { it.startsWith("UC") } ?: return@mapNotNull null
            val name = text(row.optJSONObject("title")).takeIf(String::isNotBlank) ?: return@mapNotNull null
            SimilarArtist(
                id = id,
                name = name,
                audienceText = text(row.optJSONObject("subtitle")).takeIf(String::isNotBlank),
                thumbnailUrl = thumbnail(row),
            )
        }.distinctBy { it.id }
    }

    /** "Featured on" shelf: playlists carrying this artist. */
    fun featuredPlaylists(root: JSONObject): List<FeaturedPlaylist> {
        val shelf = carousel(root, "Featured on") ?: return emptyList()
        return objects(shelf, "musicTwoRowItemRenderer").mapNotNull { row ->
            val id = endpoint(row)?.optString("browseId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = text(row.optJSONObject("title")).takeIf(String::isNotBlank) ?: return@mapNotNull null
            FeaturedPlaylist(
                id = id,
                title = title,
                subtitle = text(row.optJSONObject("subtitle")).takeIf(String::isNotBlank),
                thumbnailUrl = thumbnail(row),
            )
        }.distinctBy { it.id }
    }

    /**
     * Where a song lives, from one music `/next` panel renderer (the first
     * `playlistPanelVideoRenderer` is the requested song itself). Its
     * `longBylineText` runs are exactly artist link, album link, year.
     * Verified September 2026.
     */
    fun songAlbumRef(panel: JSONObject): SongAlbumRef? {
        val runs = runs(panel.optJSONObject("longBylineText"))
        val albumRun = runs.firstOrNull { isAlbum(endpoint(it)) } ?: return null
        val albumId = endpoint(albumRun)?.optString("browseId")?.takeIf(String::isNotBlank) ?: return null
        val artistRun = runs.firstOrNull { isArtist(endpoint(it)) }
        return SongAlbumRef(
            albumId = albumId,
            albumTitle = albumRun.optString("text").trim().takeIf(String::isNotBlank),
            artistId = endpoint(artistRun)?.optString("browseId")?.takeIf(String::isNotBlank),
            year = year(runs),
        )
    }

    fun albumSongs(root: JSONObject, browseId: String): List<Song> {
        val header = (objects(root, "musicResponsiveHeaderRenderer") + objects(root, "musicDetailHeaderRenderer")).firstOrNull()
        val title = text(header?.optJSONObject("title"))
        val metadata = runs(header?.optJSONObject("subtitle"))
        val artist = artists(runs(header?.optJSONObject("straplineTextOne")) + metadata)
            .ifBlank { text(header?.optJSONObject("straplineTextOne")) }
        val thumbnail = header?.let { thumbnail(it) }
        // Scope to the album's track shelves; recommendations elsewhere in the
        // response must never become tracks in this album.
        val shelves = objects(root, "musicShelfRenderer") + objects(root, "musicPlaylistShelfRenderer")
        return shelves.flatMap { shelf -> objects(shelf.optJSONArray("contents"), "musicResponsiveListItemRenderer") }
            .mapNotNull { row ->
                val song = song(row) ?: return@mapNotNull null
                song.copy(
                    album = title.ifBlank { song.album },
                    albumId = browseId,
                    releaseYear = year(metadata) ?: song.releaseYear,
                    releaseType = type(metadata),
                    artist = song.artist.takeUnless(::isUnknownArtist) ?: artist.ifBlank { UNKNOWN_ARTIST },
                    thumbnailUrl = song.thumbnailUrl ?: thumbnail,
                    trackNumber = text(row.optJSONObject("index")).toIntOrNull()?.takeIf { it > 0 },
                )
            }
    }

    private fun strings(node: Any?, key: String): List<String> = buildList {
        when (node) {
            is JSONObject -> node.keys().forEach { name ->
                val value = node.opt(name)
                if (name == key && value is String) add(value) else addAll(strings(value, key))
            }
            is JSONArray -> for (i in 0 until node.length()) addAll(strings(node.opt(i), key))
        }
    }

    /**
     * Whether a row or panel is the song itself rather than a video of it:
     * its watch endpoints carry `musicVideoType`, and the song is
     * `MUSIC_VIDEO_TYPE_ATV` (the others are the official video, a lyric
     * video, a user upload). Null when the row does not say.
     * [verified October 2026 on album rows, playlist rows and `/next` panels]
     */
    fun isSongVersion(node: JSONObject): Boolean? {
        val types = strings(node, "musicVideoType")
        return if (types.isEmpty()) null else types.any { it == "MUSIC_VIDEO_TYPE_ATV" }
    }

    /**
     * Whether an album page listed videos in place of its songs.
     *
     * Signed out, YouTube Music's album page gives each track that has an
     * official video the *video's* id, under the song's title and the song's
     * length [verified October 2026: Short n' Sweet, all twelve tracks, in
     * US, IN and GB; signed in, the same page gives the songs]. Played as it
     * comes, that is the video's audio - a longer cut with its own intro and
     * ending - running past the length on screen, with lyrics timed for a
     * recording that is not the one playing.
     */
    fun albumHasVideoVersions(root: JSONObject): Boolean {
        val shelves = objects(root, "musicShelfRenderer") + objects(root, "musicPlaylistShelfRenderer")
        return shelves.flatMap { objects(it.optJSONArray("contents"), "musicResponsiveListItemRenderer") }
            .any { isSongVersion(it) == false }
    }

    /**
     * The album's own audio playlist (`OLAK5uy_...`), from the header's play
     * button. Browsed as a playlist it lists the songs themselves, signed in
     * or out [verified October 2026].
     */
    fun albumAudioPlaylistId(root: JSONObject): String? {
        val header = (objects(root, "musicResponsiveHeaderRenderer") + objects(root, "musicDetailHeaderRenderer"))
            .firstOrNull() ?: return null
        return strings(header, "playlistId").firstOrNull { it.startsWith("OLAK5uy_") }
    }

    /**
     * [album] with each track's id and length taken from the same track in
     * [audio], the album's audio playlist. Matched by position when the titles
     * agree and by title otherwise; a track with no match keeps what it had,
     * which is the video and still plays.
     */
    fun withSongVersions(album: List<Song>, audio: List<Song>): List<Song> {
        fun key(title: String) = title.trim().lowercase()
        val unused = audio.toMutableList()
        return album.mapIndexed { index, track ->
            val match = audio.getOrNull(index)?.takeIf { it in unused && key(it.title) == key(track.title) }
                ?: unused.firstOrNull { key(it.title) == key(track.title) }
                ?: return@mapIndexed track
            unused.remove(match)
            track.copy(id = match.id, duration = match.duration.takeIf { it > 0L } ?: track.duration)
        }
    }

    /**
     * The songs of a `/next` queue, one per entry. Signed in, an entry that
     * exists as both a video and a song arrives as a
     * `playlistPanelVideoWrapperRenderer` holding the two - `primaryRenderer`
     * and `counterpart[0].counterpartRenderer` - and reading every panel in
     * the response queued both. The song is taken when there is one.
     * [verified October 2026]
     */
    fun queueSongs(root: JSONObject): List<Song> {
        val entries = objects(root, "playlistPanelRenderer")
            .flatMap { it.optJSONArray("contents").rows() }
        if (entries.isEmpty()) return objects(root, "playlistPanelVideoRenderer").mapNotNull(::song)
        return entries.mapNotNull { entry ->
            val wrapper = entry.optJSONObject("playlistPanelVideoWrapperRenderer")
            val versions = if (wrapper != null) {
                objects(wrapper.optJSONObject("primaryRenderer"), "playlistPanelVideoRenderer") +
                    objects(wrapper.optJSONArray("counterpart"), "playlistPanelVideoRenderer")
            } else listOfNotNull(entry.optJSONObject("playlistPanelVideoRenderer"))
            (versions.firstOrNull { isSongVersion(it) == true } ?: versions.firstOrNull())?.let(::song)
        }
    }

    /** Search and discography continuations only; never call on an artist page
     * with unrelated shelves competing for the next token. */
    fun continuation(root: JSONObject): String? =
        objects(root, "nextContinuationData").firstNotNullOfOrNull { it.optString("continuation").takeIf(String::isNotBlank) }
            ?: objects(root, "continuationItemRenderer").firstNotNullOfOrNull {
                it.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")
                    ?.optString("token")?.takeIf(String::isNotBlank)
            }
}

/** Stable ties preserve source order; an absent year is never the current year. */
internal fun List<PlaylistDisplayItem>.newestReleasesFirst(): List<PlaylistDisplayItem> =
    sortedByDescending { it.releaseYear ?: Int.MIN_VALUE }
