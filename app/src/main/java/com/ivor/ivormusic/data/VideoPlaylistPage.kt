package com.ivor.ivormusic.data

import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor

sealed interface VideoPlaylistCursor {
    data class Browse(val token: String) : VideoPlaylistCursor
    data class NewPipe(val extractor: PlaylistExtractor, val page: Page) : VideoPlaylistCursor
}

data class VideoPlaylistPage(
    val videos: List<VideoItem>,
    val continuation: VideoPlaylistCursor? = null
)
