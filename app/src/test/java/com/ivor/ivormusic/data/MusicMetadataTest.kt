package com.ivor.ivormusic.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Minimal, sanitized public WEB_REMIX responses captured September 2026.
 * Mutation tests below exercise field absence and renderer changes without
 * network access; fixtures contain no cookies, tracking or account data.
 */
class MusicMetadataTest {
    private fun fixture(name: String): JSONObject = javaClass.getResourceAsStream("/music_metadata/$name.json")!!
        .bufferedReader().use { JSONObject(it.readText()) }

    @Test fun `search keeps canonical album and duration from subtitle`() {
        val song = MusicMetadata.song(fixture("search_song"))!!
        assertEquals("BIRDS OF A FEATHER", song.title)
        assertEquals("Billie Eilish", song.artist)
        assertEquals("HIT ME HARD AND SOFT", song.album)
        assertEquals("MPREb_Wp9Aj8HpTsB", song.albumId)
        assertEquals(211000L, song.duration)
        assertNotNull(song.thumbnailUrl)
    }

    @Test fun `collaborator is never mistaken for album in search or next`() {
        for (name in listOf("collaboration_search", "collaboration_next")) {
            val song = MusicMetadata.song(fixture(name))!!
            assertEquals("Billie Eilish, Khalid", song.artist)
            assertEquals("lovely", song.album)
            assertEquals("MPREb_6HPBhVgaSYO", song.albumId)
            assertEquals(201000L, song.duration)
        }
        assertEquals(2018, MusicMetadata.song(fixture("collaboration_next"))!!.releaseYear)
    }

    @Test fun `playlist album in a separate flex column survives`() {
        val song = MusicMetadata.song(fixture("playlist_track"))!!
        assertEquals("SKINNY", song.title)
        assertEquals("HIT ME HARD AND SOFT", song.album)
        assertEquals("Billie Eilish", song.artist)
        assertTrue(song.duration > 0)
    }

    @Test fun `search continuation uses the same metadata contract`() {
        val song = MusicMetadata.song(fixture("search_next_song"))!!
        assertFalse(isUnknownAlbum(song.album))
        assertNotNull(song.albumId)
        assertTrue(song.duration > 0)
    }

    @Test fun `EP search is a release not a song or an album by default`() {
        val row = fixture("ep_search")
        assertNull(MusicMetadata.song(row))
        val release = MusicMetadata.release(row)!!
        assertEquals("the rest", release.name)
        assertEquals("boygenius", release.uploaderName)
        assertEquals(MusicReleaseType.EP, release.releaseType)
        assertEquals(2023, release.releaseYear)
        // The visible caption is built from these two in the UI layer, where
        // the type resolves to a translated string resource.
        assertEquals("EP", release.releaseType?.wireLabel)
    }

    @Test fun `release search preserves every artist separately from year and type`() {
        val release = MusicMetadata.release(fixture("ep_multi_artist_search"))!!
        assertEquals("boygenius, Julien Baker, Lucy Dacus, Phoebe Bridgers", release.uploaderName)
        assertEquals(2018, release.releaseYear)
        assertEquals(MusicReleaseType.EP, release.releaseType)
    }

    @Test fun `release cards retain EP single and album shelf classification`() {
        val ep = MusicMetadata.release(fixture("ep_card"), MusicReleaseType.ALBUM)!!
        assertEquals(MusicReleaseType.EP, ep.releaseType)
        assertEquals(MusicReleaseType.SINGLE, MusicMetadata.release(fixture("single_card"))!!.releaseType)
        val album = MusicMetadata.release(fixture("album_card"), MusicReleaseType.ALBUM, "boygenius")!!
        assertEquals(MusicReleaseType.ALBUM, album.releaseType)
        assertEquals("boygenius", album.uploaderName)
        assertEquals(2023, album.releaseYear)
    }

    @Test fun `album header supplies omitted artist album type and year to EP tracks`() {
        val songs = MusicMetadata.albumSongs(fixture("ep_album"), "MPREb_moznxmUWnGr")
        assertEquals(4, songs.size)
        assertEquals(listOf(1, 2, 3, 4), songs.map { it.trackNumber })
        for (song in songs) {
            assertEquals("the rest", song.album)
            assertEquals("boygenius", song.artist)
            assertEquals(2023, song.releaseYear)
            assertEquals(MusicReleaseType.EP, song.releaseType)
            assertEquals("MPREb_moznxmUWnGr", song.albumId)
            assertNotNull(song.thumbnailUrl)
        }
    }

    @Test fun `single track receives release name even when row has empty artist column`() {
        val songs = MusicMetadata.albumSongs(fixture("single_album"), "MPREb_Y4KWN0glLx2")
        assertEquals(1, songs.size)
        assertEquals("BILLIE EILISH.", songs.single().album)
        assertEquals("Armani White", songs.single().artist)
        assertEquals(MusicReleaseType.SINGLE, songs.single().releaseType)
        assertEquals(2022, songs.single().releaseYear)
    }

    @Test fun `album track order and duplicate occurrences survive parsing`() {
        val root = fixture("ep_album")
        val rows = root.getJSONArray("contents").getJSONObject(0).getJSONObject("musicShelfRenderer").getJSONArray("contents")
        rows.put(rows.getJSONObject(0))
        val songs = MusicMetadata.albumSongs(root, "album")
        assertEquals(5, songs.size)
        assertEquals(songs.first().id, songs.last().id)
    }

    @Test fun `recommendation rows outside the album track shelf are excluded`() {
        val root = fixture("ep_album")
        root.put("recommendations", JSONObject().put("musicResponsiveListItemRenderer", fixture("search_song")))
        assertEquals(4, MusicMetadata.albumSongs(root, "album").size)
    }

    @Test fun `missing album link never promotes a collaborator year duration or view count`() {
        val row = fixture("collaboration_search")
        val runs = row.getJSONArray("flexColumns").getJSONObject(1)
            .getJSONObject("musicResponsiveListItemFlexColumnRenderer").getJSONObject("text").getJSONArray("runs")
        // Remove the album link but retain every display string and artist.
        runs.getJSONObject(4).remove("navigationEndpoint")
        runs.put(JSONObject().put("text", "2025"))
        val song = MusicMetadata.song(row)!!
        assertEquals("Billie Eilish, Khalid", song.artist)
        assertEquals(UNKNOWN_ALBUM, song.album)
        assertNull(song.albumId)
    }

    @Test fun `browse page type overrides misleading id prefixes`() {
        val row = fixture("search_song")
        val run = row.getJSONArray("flexColumns").getJSONObject(1)
            .getJSONObject("musicResponsiveListItemFlexColumnRenderer").getJSONObject("text")
            .getJSONArray("runs").getJSONObject(2)
        run.getJSONObject("navigationEndpoint").getJSONObject("browseEndpoint")
            .getJSONObject("browseEndpointContextSupportedConfigs").getJSONObject("browseEndpointContextMusicConfig")
            .put("pageType", "MUSIC_PAGE_TYPE_PLAYLIST")
        assertNull(MusicMetadata.song(row)!!.albumId)
    }

    @Test fun `legacy album endpoint without pageType still identifies MPRE`() {
        val row = fixture("search_song")
        val run = row.getJSONArray("flexColumns").getJSONObject(1)
            .getJSONObject("musicResponsiveListItemFlexColumnRenderer").getJSONObject("text")
            .getJSONArray("runs").getJSONObject(2)
        run.getJSONObject("navigationEndpoint").getJSONObject("browseEndpoint")
            .remove("browseEndpointContextSupportedConfigs")
        assertEquals("HIT ME HARD AND SOFT", MusicMetadata.song(row)!!.album)
    }

    @Test fun `two row songs parse links and plain artist fallback without inventing albums`() {
        val queue = fixture("collaboration_next")
        val card = JSONObject().put("title", queue.getJSONObject("title"))
            .put("subtitle", queue.getJSONObject("longBylineText"))
            .put("navigationEndpoint", JSONObject().put("watchEndpoint", JSONObject().put("videoId", queue.getString("videoId"))))
        assertEquals("lovely", MusicMetadata.song(card)!!.album)
        card.put("subtitle", JSONObject().put("runs", JSONArray().put(JSONObject().put("text", "A creator"))
            .put(JSONObject().put("text", " • ")).put(JSONObject().put("text", "3M views"))))
        val song = MusicMetadata.song(card)!!
        assertEquals("A creator", song.artist)
        assertEquals(UNKNOWN_ALBUM, song.album)
        assertNull(song.releaseYear)
    }

    @Test fun `missing identifiers or titles cannot fabricate songs`() {
        assertNull(MusicMetadata.song(JSONObject()))
        assertNull(MusicMetadata.song(JSONObject().put("videoId", "id")))
        assertNull(MusicMetadata.release(JSONObject()))
        val row = fixture("search_song")
        row.getJSONArray("flexColumns").getJSONObject(0)
            .getJSONObject("musicResponsiveListItemFlexColumnRenderer").remove("text")
        assertNull(MusicMetadata.song(row))
        row.getJSONArray("flexColumns").put(0, JSONObject.NULL)
        assertNull(MusicMetadata.song(row))
    }

    @Test fun `year is not parsed out of a release title artist name or count`() {
        val row = fixture("ep_card")
        row.put("title", JSONObject().put("simpleText", "1989"))
        row.put("subtitle", JSONObject().put("runs", JSONArray()
            .put(JSONObject().put("text", "2024 plays"))
            .put(JSONObject().put("text", "2017").put("navigationEndpoint", JSONObject()
                .put("browseEndpoint", JSONObject().put("browseId", "UCartist"))))))
        val release = MusicMetadata.release(row)!!
        assertNull(release.releaseYear)
        assertNull(release.releaseType)
        assertEquals("2017", release.uploaderName)
    }

    @Test fun `newest releases cross shelf boundaries and missing years stay last`() {
        fun release(id: String, year: Int?) = PlaylistDisplayItem("Same title", "https://music.youtube.com/browse/$id", "Artist", releaseYear = year)
        val sorted = listOf(release("old", 2017), release("unknown", null), release("new-a", 2026),
            release("middle", 2023), release("new-b", 2026)).newestReleasesFirst()
        assertEquals(listOf("new-a", "new-b", "middle", "old", "unknown"), sorted.map { it.id })
    }

    @Test fun `same titled editions have distinct release identities`() {
        val first = fixture("ep_card")
        val second = JSONObject(first.toString())
        second.getJSONObject("navigationEndpoint").getJSONObject("browseEndpoint").put("browseId", "MPREother")
        val root = JSONObject().put("items", JSONArray()
            .put(JSONObject().put("musicTwoRowItemRenderer", first))
            .put(JSONObject().put("musicTwoRowItemRenderer", second)))
        assertEquals(2, MusicMetadata.releaseRows(root).size)
    }

    @Test fun `release identity survives song persistence and old songs still decode`() {
        val song = MusicMetadata.albumSongs(fixture("ep_album"), "MPREep").first()
        assertEquals(song, Json.decodeFromString<Song>(Json.encodeToString(song)))
        val legacy = Json.decodeFromString<Song>("""{"id":"old","title":"Song","artist":"Artist","album":"Album","duration":1}""")
        assertNull(legacy.albumId)
        assertNull(legacy.releaseYear)
        assertNull(legacy.releaseType)
    }

    @Test fun `saved releases preserve EP classification and year when reopening`() {
        val release = SavedPlaylist("MPREep", "https://music.youtube.com/browse/MPREep", "the rest", "boygenius",
            isAlbum = true, releaseType = MusicReleaseType.EP, releaseYear = 2023).toDisplayItem()
        assertEquals(MusicReleaseType.EP, release.releaseType)
        assertEquals(2023, release.releaseYear)
        assertEquals("boygenius", release.uploaderName)
    }

    @Test fun `search and modern discography continuation shapes are supported`() {
        assertEquals("next", MusicMetadata.continuation(JSONObject("""{"continuationContents":{"musicShelfContinuation":{"continuations":[{"nextContinuationData":{"continuation":"next"}}]}}}""")))
        assertEquals("grid", MusicMetadata.continuation(JSONObject("""{"items":[{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"grid"}}}}]}""")))
        assertNull(MusicMetadata.continuation(JSONObject()))
    }

    @Test fun `artist header carries bio audience and banner`() {
        val root = JSONObject().put("musicImmersiveHeaderRenderer", fixture("artist_header"))
        val header = MusicMetadata.artistHeader(root)!!
        assertEquals("Billie Eilish", header.name)
        assertTrue(header.bio!!.startsWith("Billie Eilish Pirate Baird"))
        assertEquals("371M monthly audience", header.monthlyAudience)
        assertTrue(header.bannerUrl!!.contains("lh3.googleusercontent.com"))
        // A discography page has shelves but no immersive header.
        assertNull(MusicMetadata.artistHeader(JSONObject()))
    }

    @Test fun `top song row links artist and album by page type`() {
        val song = MusicMetadata.song(fixture("artist_top_song"))!!
        assertEquals("BIRDS OF A FEATHER", song.title)
        assertEquals("Billie Eilish", song.artist)
        assertEquals("HIT ME HARD AND SOFT", song.album)
        assertEquals("MPREb_Wp9Aj8HpTsB", song.albumId)
        assertEquals("WKZO-CWeOVA", song.id)
    }

    @Test fun `fans also like parses artists with audience`() {
        val root = JSONObject().put("musicCarouselShelfRenderer", fixture("similar_shelf"))
        val similar = MusicMetadata.similarArtists(root)
        assertEquals(2, similar.size)
        assertTrue(similar.all { it.id.startsWith("UC") })
        assertTrue(similar.all { it.name.isNotBlank() })
        assertTrue(similar.all { !it.audienceText.isNullOrBlank() })
        assertTrue(similar.all { !it.thumbnailUrl.isNullOrBlank() })
        assertTrue(MusicMetadata.similarArtists(JSONObject()).isEmpty())
    }

    @Test fun `featured on parses playlists`() {
        val root = JSONObject().put("musicCarouselShelfRenderer", fixture("featured_shelf"))
        val featured = MusicMetadata.featuredPlaylists(root)
        assertEquals(2, featured.size)
        assertTrue(featured.all { it.id.isNotBlank() && it.title.isNotBlank() })
        assertTrue(MusicMetadata.featuredPlaylists(JSONObject()).isEmpty())
    }

    @Test fun `next panel resolves the song album artist and year`() {
        val ref = MusicMetadata.songAlbumRef(fixture("next_panel"))!!
        assertEquals("MPREb_Wp9Aj8HpTsB", ref.albumId)
        assertEquals("HIT ME HARD AND SOFT", ref.albumTitle)
        assertEquals("UCERrDZ8oN0U_n9MphMKERcg", ref.artistId)
        assertEquals(2024, ref.year)
        // A panel without an album link resolves to nothing, never a guess.
        assertNull(MusicMetadata.songAlbumRef(JSONObject()))
    }

    @Test fun `album header names the artist link for track attribution`() {
        val header = MusicMetadata.artistHeader(fixture("album_header"))
        // Album pages use the responsive header, never the immersive one.
        assertNull(header)
        val songs = MusicMetadata.albumSongs(fixture("album_header"), "MPREb_Wp9Aj8HpTsB")
        assertTrue(songs.isNotEmpty())
        assertTrue(songs.all { it.albumId == "MPREb_Wp9Aj8HpTsB" })
        assertTrue(songs.all { it.artist == "Billie Eilish" })
    }
}
