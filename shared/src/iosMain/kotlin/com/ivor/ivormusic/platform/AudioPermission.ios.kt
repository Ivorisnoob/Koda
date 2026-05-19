package com.ivor.ivormusic.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun AudioPermissionEffect(onPermissionResult: (granted: Boolean) -> Unit) {
    // iOS media library access is managed via Info.plist entitlements
    LaunchedEffect(Unit) { onPermissionResult(true) }
}
