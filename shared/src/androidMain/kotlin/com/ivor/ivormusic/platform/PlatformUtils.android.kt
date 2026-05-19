package com.ivor.ivormusic.platform

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    applicationContext.startActivity(intent)
}

actual fun setScreenOrientation(landscape: Boolean) {
    currentActivity?.requestedOrientation = if (landscape)
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    else
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

actual fun downloadAndInstallApk(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    applicationContext.startActivity(intent)
}

/** Weak reference to the current Activity, set in MainActivity. */
var currentActivity: android.app.Activity? = null
