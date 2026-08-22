package com.ivor.ivormusic

import android.app.Application
import com.ivor.ivormusic.data.CrashReporter

class IvorMusicApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else can crash, so the bug reporter has a
        // file to offer on the next launch. It wraps - never replaces - the
        // platform handler.
        CrashReporter.install(this)
    }
}
