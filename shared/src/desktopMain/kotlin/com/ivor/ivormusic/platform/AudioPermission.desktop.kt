package com.ivor.ivormusic.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun AudioPermissionEffect(onPermissionResult: (granted: Boolean) -> Unit) {
    LaunchedEffect(Unit) { onPermissionResult(true) }
}
