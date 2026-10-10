package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.ChannelTabKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The InnerTube parsers against real responses.
 *
 * Every fixture under `resources/youtube` is a signed-out WEB response
 * captured in October 2026 by `.probe/parser_fixtures.py` and reduced: the
 * tracking keys are dropped, long lists keep their first entries, and
 * continuation tokens are short stand-ins. Each expected value here was read
 * out of the fixture itself, not taken from what the parser returned, so a
 * test fails when a parser stops finding what the response plainly says.
 */
class ParserFixturesTest {
    private fun text(name: String): String =
        javaClass.getResourceAsStream("/youtube/$name.json")!!.bufferedReader().use { it.readText() }

    private fun fixture(name: String) = JSONObject(text(name))

    // --- /next: the watch page ---

    @Test
    fun watchNextNamesTheVideoAndItsChannel() {
        val video = parseVideoMetadataFromWatchNext("jNQXAC9IVRw", fixture("watch_next"), baseVideo = null)

        assertNotNull(video)
        assertEquals("jNQXAC9IVRw", video!!.videoId)
        assertEquals("Me at the zoo", video.title)
        assertEquals("jawed", video.channelName)
        assertEquals("UC4QobU6STFB0P71PMvOGN5A", video.channelId)
    }

    @Test
    fun watchNextCarriesLikesAndTheChannelToSubscribeTo() {
        val engagement = parseEngagementFromWatchNext("jNQXAC9IVRw", fixture("watch_next"))

        assertEquals("20M", engagement.likeCount)
        assertEquals("UC4QobU6STFB0P71PMvOGN5A", engagement.channelId)
        assertEquals("6.67M subscribers", engagement.subscriberCountText)
        assertFalse(engagement.isSubscribed)
        assertNotNull(engagement.commentsToken)
        assertTrue(engagement.collaborators.isEmpty())
    }

    @Test
    fun watchNextChaptersComeBackInOrder() {
        val chapters = parseChaptersFromWatchNext(fixture("watch_next"))

        assertEquals(listOf("Intro", "The cool thing", "End"), chapters.map { it.title })
        assertEquals(listOf(0L, 5_000L, 17_000L), chapters.map { it.startMs })
    }

    @Test
    fun watchNextRelatedVideosEachNameAChannel() {
        val related = parseRelatedFromWatchNext(fixture("watch_next"))

        assertEquals(listOf("MgBZpKV3AMM", "PkU8FTafNQU", "4TUTHzh1WUw"), related.map { it.videoId })
        assertEquals(listOf("Related video 1", "Related video 2", "Related video 3"), related.map { it.title })
        related.forEach { video ->
            assertTrue("no channel for ${video.videoId}", video.channelName.isNotBlank())
            assertFalse("placeholder channel for ${video.videoId}", video.channelName.startsWith("Unknown"))
        }
    }

    // --- /browse: a channel's Videos tab ---

    @Test
    fun channelHeaderReadsIdentityFromThePage() {
        val header = parseChannelHeader(fixture("channel_videos"), fallbackChannelId = "fallback")

        assertNotNull(header)
        assertEquals("UC4QobU6STFB0P71PMvOGN5A", header!!.channelId)
        assertEquals("jawed", header.name)
        assertEquals("@jawed", header.handle)
        assertEquals("6.67M subscribers", header.subscriberCountText)
        assertEquals("1 video", header.videoCountText)
    }

    @Test
    fun channelTabsAreTheOnesTheResponseLists() {
        val root = fixture("channel_videos")
        val tabs = parseChannelTabs(root)

        // Three tabRenderers and the expandableTabRenderer that is the
        // channel's own search box.
        assertEquals(listOf("Home", "Videos", "Playlists", "Search"), tabs.map { it.title })
        assertEquals(
            listOf(ChannelTabKind.HOME, ChannelTabKind.VIDEOS, ChannelTabKind.PLAYLISTS, ChannelTabKind.SEARCH),
            tabs.map { it.kind },
        )
        assertEquals("EgZ2aWRlb3PyBgQKAjoA", tabs.single { it.kind == ChannelTabKind.VIDEOS }.params)
        assertEquals(ChannelTabKind.VIDEOS, parseSelectedTab(root)?.first)
    }

    @Test
    fun channelTabVideosTakeTheirChannelFromTheHeader() {
        val root = fixture("channel_videos")
        val header = parseChannelHeader(root, fallbackChannelId = "fallback")
        val page = parseChannelTabPage(parseSelectedTab(root)!!.second, header)

        // A channel tab's lockup has no creator row: the page already names
        // the channel, so the row parser must take it from the header.
        val video = page.videos.single()
        assertEquals("jNQXAC9IVRw", video.videoId)
        assertEquals("Me at the zoo", video.title)
        assertEquals("jawed", video.channelName)
        assertEquals("UC4QobU6STFB0P71PMvOGN5A", video.channelId)
        assertTrue(video.viewCount, video.viewCount.startsWith("442M"))
        assertEquals("21 years ago", video.uploadedDate)
    }

    // --- /browse: a playlist, signed out ---

    @Test
    fun signedOutPlaylistRowsAreLockups() {
        val root = fixture("playlist_signed_out")
        val rows = parseVideoPlaylistRows(root)

        assertEquals(listOf("17WoOqgXsRM", "LG8ZK-rRkXo", "AaGK-fj-BAM"), rows.map { it.videoId })
        assertEquals("Coding Challenge 1: Starfield Simulation", rows.first().title)
        assertTrue(rows.all { it.channelName == "The Coding Train" })
        assertNotNull(extractVideoPlaylistContinuationToken(root))
    }

    @Test
    fun playlistHeaderStatesItsTitleAndSize() {
        val info = parseModernPlaylistHeader(fixture("playlist_signed_out"), "PLRqwX-V7Uu6ZiZxtDDRCi6uhfTH4FilpH")

        assertNotNull(info)
        assertEquals("Coding Challenges", info!!.title)
        assertEquals(246, info.itemCount)
    }

    // --- /search, sorted by upload date ---

    @Test
    fun sortedSearchReturnsItsVideosAndACursor() {
        val page = parseVideoSearchPage(text("search_by_date"))

        assertEquals(
            listOf("shPpgcgZPgc", "b9iGJe-PwcQ", "M2ufnTybmH4"),
            page.videos.map { it.videoId }.take(3),
        )
        val first = page.videos.first()
        assertEquals("TechReviewBD", first.channelName)
        assertEquals(322L, first.duration)
        assertNotNull(page.continuation)
    }

    @Test
    fun aPlaylistAmongSearchResultsIsNotReadAsAVideo() {
        val page = parseVideoSearchPage(text("search_by_date"))

        // The response carries one LOCKUP_CONTENT_TYPE_PLAYLIST lockup.
        assertFalse(page.videos.any { it.videoId == "PL85u7MKw0VpGhdv_1t3QNi4Ik84v6Yt8f" })
    }
}
