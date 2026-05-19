package com.ivor.ivormusic.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformWebAuthView(
    url: String,
    onCookiesReady: (String) -> Unit
) {
    // TODO: Implement with WKWebView via UIKitView
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Sign in via browser")
    }
}
