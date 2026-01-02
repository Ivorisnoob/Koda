package com.ivor.ivormusic.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.SongSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import com.google.common.util.concurrent.ListenableFuture

class MusicService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: Player
    private lateinit var youtubeRepository: YouTubeRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    private fun initializeSessionAndPlayer() {
        player = ExoPlayer.Builder(this).build()
        youtubeRepository = YouTubeRepository(this)

        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                return serviceScope.future {
                    val resolvedItems = mutableListOf<MediaItem>()
                    for (item in mediaItems) {
                        val videoId = item.mediaId
                        // Check if it's a YouTube media item (we use videoId as mediaId)
                        if (item.requestMetadata.mediaUri == null || item.requestMetadata.mediaUri.toString().isEmpty()) {
                            val streamUrl = youtubeRepository.getStreamUrl(videoId)
                            if (streamUrl != null) {
                                resolvedItems.add(
                                    item.buildUpon()
                                        .setUri(streamUrl)
                                        .build()
                                )
                            } else {
                                resolvedItems.add(item)
                            }
                        } else {
                            resolvedItems.add(item)
                        }
                    }
                    resolvedItems
                }
            }

        })
            .setSessionActivity(sessionIntent)
            .build()
    }
}
