package com.ivor.ivormusic.platform

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
actual fun PlatformVideoPlayerView(
    modifier: Modifier,
    videoUrl: String?,
    audioUrl: String?,
    isPlaying: Boolean,
    onPlayerReady: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().also { player ->
            if (videoUrl != null) {
                player.setMediaItem(MediaItem.fromUri(videoUrl))
                player.prepare()
            }
            onPlayerReady()
        }
    }

    DisposableEffect(videoUrl) {
        if (videoUrl != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
            exoPlayer.prepare()
            if (isPlaying) exoPlayer.play()
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
            }
        },
        modifier = modifier
    )
}

actual fun enterPictureInPicture() {
    // Called from the Activity context — the app module wires this up via MainActivity
}
