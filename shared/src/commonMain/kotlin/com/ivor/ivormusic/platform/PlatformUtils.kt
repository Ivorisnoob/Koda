package com.ivor.ivormusic.platform

import androidx.compose.runtime.Composable

/** Open a URL in the system browser. */
expect fun openUrl(url: String)

/** Lock/unlock screen orientation (for video fullscreen). */
expect fun setScreenOrientation(landscape: Boolean)

/** Download APK or open install intent (for update screen). */
expect fun downloadAndInstallApk(url: String)
