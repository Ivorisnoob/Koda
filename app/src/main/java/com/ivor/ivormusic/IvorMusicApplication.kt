package com.ivor.ivormusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ivor.ivormusic.data.CrashReporter
import com.ivor.ivormusic.data.LocalVideoThumbnail
import com.ivor.ivormusic.data.LocalVideoThumbnailFetcher

class IvorMusicApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else can crash, so the bug reporter has a
        // file to offer on the next launch. It wraps - never replaces - the
        // platform handler.
        CrashReporter.install(this)
        // Reconcile the persisted periodic job with the user's current opt-in.
        // The setting handler does the same immediately when the value changes.
        com.ivor.ivormusic.work.UploadCheckWorker.sync(this)
    }

    /**
     * The one Coil loader for the whole process. AsyncImage and every direct
     * ImageRequest (artwork color extraction, notification artwork, downloads)
     * resolve to this instance through Context.imageLoader, so they share one
     * memory cache, one disk cache and one connection pool instead of each
     * call site building its own loader.
     *
     * The only component added is the device-video frame fetcher, which is
     * keyed on its own [LocalVideoThumbnail] model and so cannot affect any
     * other image: every existing request still resolves exactly as it did.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(LocalVideoThumbnailFetcher.Factory(this@IvorMusicApplication))
                add(LocalVideoThumbnailFetcher.ThumbnailKeyer())
            }
            .build()
}
