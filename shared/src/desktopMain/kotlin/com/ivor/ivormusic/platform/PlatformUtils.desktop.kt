package com.ivor.ivormusic.platform

import java.awt.Desktop
import java.net.URI

actual fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (_: Exception) {}
}

actual fun setScreenOrientation(landscape: Boolean) {
    // Not applicable on desktop
}

actual fun downloadAndInstallApk(url: String) {
    openUrl(url)
}
