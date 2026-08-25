package com.ivor.ivormusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ivor.ivormusic.data.CrashReporter

class IvorMusicApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else can crash, so the bug reporter has a
        // file to offer on the next launch. It wraps - never replaces - the
        // platform handler.
        CrashReporter.install(this)
        // Idempotent: converges on one periodic job across installs and
        // updates. The worker itself no-ops when the setting is off, so this
        // can be unconditional rather than chasing every toggle.
        com.ivor.ivormusic.work.UploadCheckWorker.schedule(this)
    }

    /**
     * The one Coil loader for the whole process. AsyncImage and every direct
     * ImageRequest (artwork color extraction, notification artwork, downloads)
     * resolve to this instance through Context.imageLoader, so they share one
     * memory cache, one disk cache and one connection pool instead of each
     * call site building its own loader. Deliberately default-configured:
     * the previous ad-hoc loaders were defaults too, so nothing about how an
     * individual image loads changes - only that they now share.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).build()
}
