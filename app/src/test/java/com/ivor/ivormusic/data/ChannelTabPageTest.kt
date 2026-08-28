package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelTabPageTest {

    @Test
    fun withoutShorts_removesDirectShortsAndShortsOnlyShelves() {
        val page = ChannelTabPage(
            shorts = listOf(short("direct")),
            shelves = listOf(
                ChannelShelf(title = "Shorts", shorts = listOf(short("shelf"))),
                ChannelShelf(title = "Videos", videos = listOf(video("video")))
            ),
            continuation = "next"
        )

        val filtered = page.withoutShorts()

        assertTrue(filtered.shorts.isEmpty())
        assertEquals(listOf("Videos"), filtered.shelves.map { it.title })
        assertEquals("next", filtered.continuation)
        assertFalse(filtered.isEmpty)
    }

    @Test
    fun withoutShorts_preservesOtherItemsInMixedShelf() {
        val page = ChannelTabPage(
            shelves = listOf(
                ChannelShelf(
                    title = "Featured",
                    videos = listOf(video("video")),
                    shorts = listOf(short("short")),
                    playlists = listOf(
                        VideoPlaylist(
                            playlistId = "playlist",
                            title = "Playlist",
                            thumbnailUrl = "",
                            videoCountText = "1 video"
                        )
                    )
                )
            )
        )

        val shelf = page.withoutShorts().shelves.single()

        assertTrue(shelf.shorts.isEmpty())
        assertEquals(listOf("video"), shelf.videos.map { it.videoId })
        assertEquals(listOf("playlist"), shelf.playlists.map { it.playlistId })
    }

    @Test
    fun withoutShorts_makesShortsOnlyPageEmpty() {
        val filtered = ChannelTabPage(shorts = listOf(short("short"))).withoutShorts()

        assertTrue(filtered.isEmpty)
    }

    private fun short(id: String) = ShortsItem(
        videoId = id,
        title = id,
        thumbnailUrl = ""
    )

    private fun video(id: String) = VideoItem(
        videoId = id,
        title = id,
        channelName = "Channel",
        thumbnailUrl = "",
        duration = 60,
        viewCount = "1 view"
    )
}
