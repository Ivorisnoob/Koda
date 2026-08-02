package com.ivor.ivormusic.ui.share

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.YouTubeLinkParser
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.video.VideoPlayerViewModel

/**
 * A link handed to Koda from outside the app, waiting to be opened.
 *
 * [token] distinguishes two shares of the same URL, which are otherwise
 * indistinguishable to the effect that acts on them - sharing the same video
 * twice in a row must open it twice.
 */
data class PendingSharedLink(val text: String, val token: Long)

/**
 * The link carried by an incoming intent, if any: the shared text for a share
 * sheet send, the URL itself for an "open with". Returns null for anything
 * else, including the launcher intent.
 */
fun Intent.sharedLinkText(): String? = when (action) {
    Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
    Intent.ACTION_VIEW -> dataString
    else -> null
}?.takeIf { it.isNotBlank() }

/**
 * Opens a YouTube link shared into Koda, choosing the player from where the
 * link points: music.youtube.com goes to the music player, everything else to
 * the video player. Playlist links load the whole playlist; a watch link that
 * also carries a list opens the playlist positioned on that track in music
 * mode, and just the video in video mode (the video player has no queue, so
 * loading the list would only cost a round trip).
 *
 * Video links skip metadata resolution entirely: [VideoPlayerViewModel.playVideo]
 * only needs an id to start streaming, and its second phase fills in the title,
 * channel and related videos. Music links have no such phase, so they resolve
 * first and the player opens once there is something to show.
 */
@Composable
fun SharedLinkHandler(
    pendingLink: PendingSharedLink?,
    enabled: Boolean,
    localOnlyMode: Boolean,
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    videoPlayerViewModel: VideoPlayerViewModel,
    onNavigateHome: () -> Unit
) {
    val context = LocalContext.current

    // Handled tokens are tracked here rather than cleared at the source: the
    // effect suspends on a network call, and nulling the state it is keyed on
    // would cancel the resolution half way through.
    var lastHandledToken by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(pendingLink?.token, enabled) {
        val pending = pendingLink ?: return@LaunchedEffect
        if (!enabled || lastHandledToken == pending.token) return@LaunchedEffect
        lastHandledToken = pending.token

        fun toast(message: String) =
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        val link = YouTubeLinkParser.parseFromSharedText(pending.text)
        if (link == null) {
            toast("No YouTube link found in what you shared")
            return@LaunchedEffect
        }
        if (localOnlyMode) {
            toast("Local only mode is on. Turn it off in Settings to open YouTube links.")
            return@LaunchedEffect
        }

        // The music player lives inside the Home screen, so it has nothing to
        // draw on top of Settings or Downloads.
        onNavigateHome()

        val videoId = link.videoId
        val playlistId = link.playlistId

        when {
            // Music playlist, opening on the shared track when the link names one
            link.isMusicLink && playlistId != null -> {
                val songs = homeViewModel.resolvePlaylistSongsFromLink(playlistId)
                if (songs.isEmpty()) {
                    toast("Couldn't open that playlist")
                } else {
                    val start = songs.firstOrNull { it.id == videoId } ?: songs.first()
                    playerViewModel.playQueue(songs, start)
                }
            }

            link.isMusicLink && videoId != null -> {
                val video = homeViewModel.resolveVideoFromLink(videoId)
                if (video == null) {
                    toast("Couldn't open that link")
                } else {
                    playerViewModel.playSong(video.toSong())
                }
            }

            videoId != null -> videoPlayerViewModel.playVideo(placeholderVideo(videoId))

            playlistId != null -> {
                val videos = homeViewModel.resolvePlaylistVideosFromLink(playlistId)
                if (videos.isEmpty()) {
                    toast("Couldn't open that playlist")
                } else {
                    videoPlayerViewModel.playVideo(videos.first())
                }
            }
        }
    }
}

/**
 * The bare minimum [VideoItem] needed to start playback from an id alone. The
 * thumbnail is derived rather than fetched so the player has a poster to show
 * while the first frames buffer; the title and channel arrive with the
 * watch-next phase a moment later.
 */
private fun placeholderVideo(videoId: String) = VideoItem(
    videoId = videoId,
    title = "",
    channelName = "",
    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
    duration = 0L,
    viewCount = ""
)

/** Music-mode representation of a video resolved from a shared link. */
private fun VideoItem.toSong(): Song = Song.fromYouTube(
    videoId = videoId,
    title = title,
    artist = channelName,
    album = "",
    duration = duration * 1000,
    thumbnailUrl = thumbnailUrl
)
