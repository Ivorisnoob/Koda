package com.ivor.ivormusic.data.youtube

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicResponseParsersTest {
    @Test
    fun likedSongsOnlyReadsThePlaylistShelf() {
        val likedSong = responsiveSong("liked-song")
        val suggestedSong = responsiveSong("suggested-song")
        val shelves = JSONArray()
            .put(
                JSONObject().put(
                    "musicPlaylistShelfRenderer",
                    JSONObject().put("contents", JSONArray().put(likedSong)),
                ),
            )
            .put(
                JSONObject().put(
                    "musicCarouselShelfRenderer",
                    JSONObject().put("contents", JSONArray().put(suggestedSong)),
                ),
            )
        val response = JSONObject().put(
            "contents",
            JSONObject().put(
                "singleColumnBrowseResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray().put(
                        JSONObject().put(
                            "tabRenderer",
                            JSONObject().put(
                                "content",
                                JSONObject().put(
                                    "sectionListRenderer",
                                    JSONObject().put("contents", shelves),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val songs = parseSongsFromInternalJson(response.toString(), listItemsOnly = true)

        assertEquals(listOf("liked-song"), songs.map { it.id })
    }

    @Test
    fun likedSongsAcceptDirectRowsFromPlaylistContinuations() {
        val response = JSONObject().put(
            "onResponseReceivedActions",
            JSONArray().put(
                JSONObject().put(
                    "appendContinuationItemsAction",
                    JSONObject().put(
                        "continuationItems",
                        JSONArray()
                            .put(responsiveSong("liked-next-page"))
                            .put(
                                JSONObject().put(
                                    "musicCarouselShelfRenderer",
                                    JSONObject().put(
                                        "contents",
                                        JSONArray().put(responsiveSong("suggested-next-page")),
                                    ),
                                ),
                            ),
                    ),
                ),
            ),
        )

        val songs = parseSongsFromInternalJson(response.toString(), listItemsOnly = true)

        assertEquals(listOf("liked-next-page"), songs.map { it.id })
    }

    private fun responsiveSong(videoId: String): JSONObject =
        JSONObject().put(
            "musicResponsiveListItemRenderer",
            JSONObject()
                .put(
                    "flexColumns",
                    JSONArray().put(
                        JSONObject().put(
                            "musicResponsiveListItemFlexColumnRenderer",
                            JSONObject().put(
                                "text",
                                JSONObject().put(
                                    "runs",
                                    JSONArray().put(JSONObject().put("text", "Episode")),
                                ),
                            ),
                        ),
                    ),
                )
                .put("playlistItemData", JSONObject().put("videoId", videoId)),
        )
}
