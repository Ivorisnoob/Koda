package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.MusicMetadata
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchParsersTest {
    private fun text(name: String): String =
        checkNotNull(javaClass.getResource("/search/$name.json")) { name }.readText()

    private fun json(name: String) = JSONObject(text(name))

    @Test
    fun `a nothing-found answer never asks the fallback`() {
        assertFalse(searchFallbackAllowed(noResults = true, isRateLimited = false, isBotChecked = false))
    }

    @Test
    fun `a failed search asks the fallback`() {
        assertTrue(searchFallbackAllowed(noResults = false, isRateLimited = false, isBotChecked = false))
    }

    @Test
    fun `a rate limit or bot check keeps the fallback from running`() {
        assertFalse(searchFallbackAllowed(noResults = false, isRateLimited = true, isBotChecked = false))
        assertFalse(searchFallbackAllowed(noResults = false, isRateLimited = false, isBotChecked = true))
    }

    @Test
    fun `no results are told apart from a list`() {
        assertTrue(isEmptyVideoSearch(json("no_results_web")))
        assertFalse(isEmptyVideoSearch(json("channels")))
        assertTrue(isEmptyMusicSearch(json("no_results_music")))
        assertFalse(isEmptyMusicSearch(json("music_artists")))
    }

    @Test
    fun `a video row keeps its short count and is not live`() {
        val video = parseVideoSearchPage(text("videos")).videos.first()
        assertFalse(video.isLive)
        assertTrue(video.viewCount, video.viewCount.endsWith("views"))
        assertTrue(video.duration > 0)
    }

    @Test
    fun `a live row is live by its badge and counts who is watching`() {
        val video = parseVideoSearchPage(text("videos")).videos.last()
        assertTrue(video.isLive)
        assertTrue(video.viewCount, video.viewCount.endsWith("watching"))
    }

    @Test
    fun `channels carry the id, the subscribers and the handle`() {
        val channels = parseChannelSearchResults(json("channels"))
        val first = channels.first()
        assertTrue(first.channelId.startsWith("UC"))
        assertEquals("Lofi Girl", first.name)
        assertTrue(first.subscriberCountText.orEmpty(), first.subscriberCountText.orEmpty().endsWith("subscribers"))
        assertEquals("@LofiGirl", first.handle)
        assertTrue(first.avatarUrl.orEmpty().startsWith("https://"))
    }

    @Test
    fun `a channel with no counts leaves them blank`() {
        val second = parseChannelSearchResults(json("channels"))[1]
        assertNull(second.subscriberCountText)
        assertNull(second.handle)
    }

    @Test
    fun `playlists show their owner, not the word Playlist`() {
        val playlists = parsePlaylistSearchResults(json("playlists"))
        assertTrue(playlists.isNotEmpty())
        val first = playlists.first()
        assertEquals("Lorenzo", first.subtitle)
        assertTrue(first.videoCountText.orEmpty(), first.videoCountText.orEmpty().endsWith("videos"))
    }

    @Test
    fun `artists keep their id and audience`() {
        val artists = MusicMetadata.artistRows(json("music_artists"))
        assertEquals("Daft Punk", artists.first().name)
        assertTrue(artists.first().id.startsWith("UC"))
        assertTrue(artists.first().subscriberCount.orEmpty(), artists.first().subscriberCount.orEmpty().endsWith("monthly audience"))
        assertTrue(artists.any { it.subscriberCount.orEmpty().endsWith("subscribers") })
    }

    @Test
    fun `music playlists lose the VL prefix and leave the count unknown`() {
        val playlist = MusicMetadata.playlistRows(json("music_playlists")).first()
        assertTrue(playlist.id, playlist.id.startsWith("PL"))
        assertEquals(-1, playlist.itemCount)
        assertEquals("A W", playlist.uploaderName)
    }
}
