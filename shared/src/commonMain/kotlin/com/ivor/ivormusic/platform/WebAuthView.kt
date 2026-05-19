package com.ivor.ivormusic.platform

import androidx.compose.runtime.Composable

/**
 * Platform-specific web view for YouTube OAuth login.
 * Android: uses AndroidView(::WebView) with CookieManager.
 * iOS: uses SFSafariViewController / WKWebView.
 */
@Composable
expect fun PlatformWebAuthView(
    url: String,
    onCookiesReady: (String) -> Unit
)
