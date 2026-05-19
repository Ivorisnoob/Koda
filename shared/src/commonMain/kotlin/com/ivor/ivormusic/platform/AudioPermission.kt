package com.ivor.ivormusic.platform

import androidx.compose.runtime.Composable

/**
 * Platform-specific audio/media permission handler.
 * Android: uses Accompanist Permissions for READ_MEDIA_AUDIO.
 * iOS: media library access is handled at the OS level.
 */
@Composable
expect fun AudioPermissionEffect(onPermissionResult: (granted: Boolean) -> Unit)
