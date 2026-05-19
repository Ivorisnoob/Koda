package com.ivor.ivormusic.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

actual fun setScreenOrientation(landscape: Boolean) {
    // TODO: Implement via UIDevice.currentDevice.setValue for iOS orientation lock
}

actual fun downloadAndInstallApk(url: String) {
    openUrl(url) // iOS directs to App Store or browser
}
