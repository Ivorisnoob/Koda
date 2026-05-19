package com.ivor.ivormusic.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific video player surface.
 * Android: AndroidView wrapping ExoPlayer's PlayerView.
 * iOS: AVPlayerViewController embedded via UIViewControllerRepresentable.
 */
@Composable
expect fun PlatformVideoPlayerView(
    modifier: Modifier,
    videoUrl: String?,
    audioUrl: String?,
    isPlaying: Boolean,
    onPlayerReady: () -> Unit,
    onError: (String) -> Unit
)

/**
 * Trigger Picture-in-Picture mode (platform-specific).
 * Android: Activity.enterPictureInPictureMode().
 * iOS: AVPictureInPictureController.
 */
expect fun enterPictureInPicture()
