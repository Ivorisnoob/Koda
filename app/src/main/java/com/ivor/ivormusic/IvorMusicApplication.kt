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
        // Reconcile the persisted periodic job with the user's current opt-in.
        // The setter does the same immediately when the preference changes.
        com.ivor.ivormusic.work.UploadCheckJobService.sync(this)
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
