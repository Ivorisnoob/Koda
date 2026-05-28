package com.ivor.ivormusic

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class IvorMusicApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // First-time init unpacks the Python runtime + yt-dlp source into
        // app-private storage and can block for 1-2 seconds, so run it off
        // the main thread. Stream resolution checks ytDlpReady before using it.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@IvorMusicApplication)
                ytDlpReady.set(true)
                Log.i(TAG, "yt-dlp ready")
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "yt-dlp init failed", e)
            } catch (e: Throwable) {
                Log.e(TAG, "yt-dlp init crashed", e)
            }
        }
    }

    companion object {
        private const val TAG = "IvorMusicApp"
        val ytDlpReady = AtomicBoolean(false)
    }
}
