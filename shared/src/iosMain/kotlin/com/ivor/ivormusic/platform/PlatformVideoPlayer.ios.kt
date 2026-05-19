package com.ivor.ivormusic.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformVideoPlayerView(
    modifier: Modifier,
    videoUrl: String?,
    audioUrl: String?,
    isPlaying: Boolean,
    onPlayerReady: () -> Unit,
    onError: (String) -> Unit
) {
    // TODO: Implement with AVPlayer via UIKitView
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface))
}

actual fun enterPictureInPicture() {
    // TODO: Implement with AVPictureInPictureController
}
