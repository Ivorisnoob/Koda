package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject

/** One YouTube Music browse shelf (home, explore, charts, new releases, a mood page). */
data class MusicShelf(val title: String, val items: List<MusicShelfItem>)

data class MusicShelfPage(val shelves: List<MusicShelf>, val continuation: String?)

sealed interface MusicShelfItem {
    val key: String

    data class Collection(val playlist: PlaylistDisplayItem, val isAlbum: Boolean) : MusicShelfItem {
        override val key get() = "c_${playlist.id}"
    }

    data class Artist(val artist: ArtistItem) : MusicShelfItem {
        override val key get() = "a_${artist.id}"
    }

    data class Track(val song: Song) : MusicShelfItem {
        override val key get() = "t_${song.id}"
    }

    data class Mood(val title: String, val browseId: String, val params: String?) : MusicShelfItem {
        override val key get() = "m_$browseId$params"
    }
}

/** WEB_REMIX browse and continuation responses, verified September 2026. */
internal fun parseMusicShelves(root: JSONObject): MusicShelfPage {
    val shelves = mutableListOf<MusicShelf>()
    for (shelf in MusicMetadata.objects(root, "musicCarouselShelfRenderer")) {
        val title = runsText(
            shelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title")
        )
        val items = parseShelfItems(shelf.optJSONArray("contents"))
        if (items.isNotEmpty()) shelves += MusicShelf(title, items)
    }
    for (grid in MusicMetadata.objects(root, "gridRenderer")) {
        val title = runsText(
            grid.optJSONObject("header")?.optJSONObject("gridHeaderRenderer")?.optJSONObject("title")
        )
        val items = parseShelfItems(grid.optJSONArray("items"))
        if (items.isNotEmpty()) shelves += MusicShelf(title, items)
    }
    val continuation = MusicMetadata.objects(root, "nextContinuationData").firstOrNull()
        ?.optString("continuation")?.takeIf { it.isNotBlank() }
    return MusicShelfPage(shelves, continuation)
}

private fun parseShelfItems(array: JSONArray?): List<MusicShelfItem> {
    array ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val entry = array.optJSONObject(i) ?: return@mapNotNull null
        entry.optJSONObject("musicTwoRowItemRenderer")?.let { return@mapNotNull twoRow(it) }
        entry.optJSONObject("musicResponsiveListItemRenderer")?.let { return@mapNotNull listItem(it) }
        entry.optJSONObject("musicNavigationButtonRenderer")?.let { return@mapNotNull mood(it) }
        null
    }.distinctBy { it.key }
}

private fun twoRow(r: JSONObject): MusicShelfItem? {
    val title = runsText(r.optJSONObject("title")).takeIf { it.isNotBlank() } ?: return null
    val subtitle = runsText(r.optJSONObject("subtitle"))
    val thumb = thumbnail(r)
    val nav = r.optJSONObject("navigationEndpoint") ?: return null
    nav.optJSONObject("watchEndpoint")?.let { watch ->
        val videoId = watch.optString("videoId").takeIf { it.length == 11 } ?: return null
        return MusicShelfItem.Track(Song.fromYouTube(videoId, title, subtitle.substringBefore(" • "), "", 0L, thumb))
    }
    val browse = nav.optJSONObject("browseEndpoint") ?: return null
    val browseId = browse.optString("browseId").takeIf { it.isNotBlank() } ?: return null
    val pageType = browse.optJSONObject("browseEndpointContextSupportedConfigs")
        ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
    return when {
        pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") ->
            MusicShelfItem.Artist(ArtistItem(id = browseId, name = title, thumbnailUrl = thumb, subscriberCount = subtitle))
        pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE") ->
            MusicShelfItem.Collection(
                PlaylistDisplayItem(title, "https://music.youtube.com/browse/$browseId", subtitle, thumbnailUrl = thumb),
                isAlbum = true
            )
        pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL") -> {
            val id = browseId.removePrefix("VL")
            MusicShelfItem.Collection(
                PlaylistDisplayItem(title, "https://music.youtube.com/playlist?list=$id", subtitle, thumbnailUrl = thumb),
                isAlbum = false
            )
        }
        else -> null
    }
}

private fun listItem(r: JSONObject): MusicShelfItem? {
    val columns = r.optJSONArray("flexColumns") ?: return null
    fun column(i: Int) = runsText(
        columns.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
    )
    val title = column(0).takeIf { it.isNotBlank() } ?: return null
    val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.length == 11 }
        ?: MusicMetadata.objects(r, "watchEndpoint").firstOrNull()?.optString("videoId")?.takeIf { it.length == 11 }
    if (videoId != null) {
        return MusicShelfItem.Track(Song.fromYouTube(videoId, title, column(1).substringBefore(" • "), "", 0L, thumbnail(r)))
    }
    val browseId = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
    if (browseId != null && browseId.startsWith("UC")) {
        return MusicShelfItem.Artist(ArtistItem(id = browseId, name = title, thumbnailUrl = thumbnail(r), subscriberCount = column(1)))
    }
    return null
}

private fun mood(r: JSONObject): MusicShelfItem? {
    val title = runsText(r.optJSONObject("buttonText")).takeIf { it.isNotBlank() } ?: return null
    val browse = r.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint") ?: return null
    val browseId = browse.optString("browseId").takeIf { it.isNotBlank() } ?: return null
    return MusicShelfItem.Mood(title, browseId, browse.optString("params").takeIf { it.isNotBlank() })
}

private fun thumbnail(r: JSONObject): String? {
    val thumbs = MusicMetadata.objects(r, "musicThumbnailRenderer").firstOrNull()
        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails") ?: return null
    return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
}

private fun runsText(text: JSONObject?): String {
    text ?: return ""
    text.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
    val runs = text.optJSONArray("runs") ?: return ""
    return buildString { for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty()) }
}
