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

    /**
     * The same page with another video's owner, Subscribe button and like
     * status planted outside the video's own column, where a related card or
     * a panel would put them. A whole-response search can return either copy.
     */
    private fun watchNextWithDecoys(): JSONObject = fixture("watch_next").apply {
        put(
            "aRelatedCard",
            JSONObject()
                .put(
                    "videoOwnerRenderer",
                    JSONObject()
                        .put("title", JSONObject().put("simpleText", "Someone else"))
                        .put("subscriberCountText", JSONObject().put("simpleText", "1 subscriber"))
                        .put(
                            "navigationEndpoint",
                            JSONObject().put("browseEndpoint", JSONObject().put("browseId", "UCdecoydecoydecoydecoy00")),
                        ),
                )
                .put(
                    "subscribeButtonRenderer",
                    JSONObject().put("subscribed", true).put("channelId", "UCdecoydecoydecoydecoy00"),
                )
                .put(
                    "videoPrimaryInfoRenderer",
                    JSONObject().put("title", JSONObject().put("simpleText", "Another video")),
                ),
        )
        getJSONObject("frameworkUpdates").getJSONObject("entityBatchUpdate").getJSONArray("mutations").put(
            JSONObject().put(
                "payload",
                JSONObject().put(
                    "likeStatusEntity",
                    JSONObject().put("key", entityKeyFor("AnotherVid0")).put("likeStatus", "LIKE"),
                ),
            ),
        )
    }

    private fun entityKeyFor(id: String): String =
        java.util.Base64.getUrlEncoder().encodeToString("\u0012\u000b$id \u0003(\u0001".toByteArray(Charsets.ISO_8859_1))

    @Test
    fun watchNextReadsTheVideosOwnColumnNotTheFirstMatchAnywhere() {
        val root = watchNextWithDecoys()

        val video = parseVideoMetadataFromWatchNext("jNQXAC9IVRw", root, baseVideo = null)!!
        assertEquals("Me at the zoo", video.title)
        assertEquals("jawed", video.channelName)

        val engagement = parseEngagementFromWatchNext("jNQXAC9IVRw", root)
        assertEquals("UC4QobU6STFB0P71PMvOGN5A", engagement.channelId)
        assertFalse(engagement.isSubscribed)
        assertEquals("6.67M subscribers", engagement.subscriberCountText)
        assertEquals(com.ivor.ivormusic.data.LikeStatus.INDIFFERENT, engagement.likeStatus)
    }

    @Test
    fun anEntityKeyNamesWhatItBelongsTo() {
        // Both keys are from the watch page fixture's frameworkUpdates.
        assertTrue(entityKeyNames("EhhVQzRRb2JVNlNURkIwUDcxUE12T0dONUEgMygB", "UC4QobU6STFB0P71PMvOGN5A"))
        assertTrue(entityKeyNames("EgtqTlFYQUM5SVZSdyA-KAE%3D", "jNQXAC9IVRw"))
        assertFalse(entityKeyNames("EgtqTlFYQUM5SVZSdyA-KAE%3D", "UC4QobU6STFB0P71PMvOGN5A"))
        assertFalse(entityKeyNames("not base64 !", "jNQXAC9IVRw"))
        assertFalse(entityKeyNames(null, "jNQXAC9IVRw"))
    }

    // --- /browse: a channel's Videos tab ---

    private fun channelWithSubscriptionStates(vararg states: Pair<String, Boolean>): JSONObject =
        fixture("channel_videos").apply {
            val mutations = org.json.JSONArray()
            states.forEach { (channelId, subscribed) ->
                mutations.put(
                    JSONObject().put(
                        "payload",
                        JSONObject().put(
                            "subscriptionStateEntity",
                            JSONObject().put("key", entityKeyFor(channelId)).put("subscribed", subscribed),
                        ),
                    ),
                )
            }
            put("frameworkUpdates", JSONObject().put("entityBatchUpdate", JSONObject().put("mutations", mutations)))
        }

    @Test
    fun subscribedStateIsTheOneNamingThisChannel() {
        val own = "UC4QobU6STFB0P71PMvOGN5A"
        val featured = "UCfeaturedfeaturedfeatur"

        // A featured channel the account follows, on a channel it does not.
        val notFollowed = parseChannelHeader(
            channelWithSubscriptionStates(featured to true, own to false), fallbackChannelId = "fallback",
        )
        assertEquals(false, notFollowed!!.accountSubscribed)

        // And the other way round.
        val followed = parseChannelHeader(
            channelWithSubscriptionStates(featured to false, own to true), fallbackChannelId = "fallback",
        )
        assertEquals(true, followed!!.accountSubscribed)
    }

    @Test
    fun aPageNamingOnlyOtherChannelsDoesNotAnswerForThisOne() {
        val header = parseChannelHeader(
            channelWithSubscriptionStates("UCfeaturedfeaturedfeatur" to true), fallbackChannelId = "fallback",
        )

        // Unknown, so the caller asks, rather than "subscribed" off a
        // featured channel's button.
        assertEquals(null, header!!.accountSubscribed)
    }

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

    @Test
    fun aLockupWithNoCreatorRowNamesNoChannel() {
        val lockups = mutableListOf<JSONObject>()
        findObjectsByKey(fixture("channel_videos"), "lockupViewModel", lockups)

        // Read on its own, before the page's header is stitched in: the row
        // has views and a date and no creator, so the name must stay blank
        // rather than be invented. Screens test for blank.
        val video = parseLockupViewModel(lockups.single())!!
        assertEquals("", video.channelName)
        assertEquals(null, video.channelId)
        assertEquals("Me at the zoo", video.title)
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
