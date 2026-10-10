package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.NewPipeDownloaderImpl
import com.ivor.ivormusic.data.SessionManager
import kotlinx.coroutines.Dispatchers
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeService

/**
 * NewPipe Extractor, set up once per process and pointed at this instance's
 * HTTP client and session.
 *
 * NewPipe is a global singleton: its downloader and its localization are
 * process-wide, which is why the flags that guard them live in the companion.
 */
internal class NewPipeGateway(
    private val http: YouTubeHttp,
    private val sessionManager: SessionManager,
) {
    init {
        initializeNewPipe()
    }

    private fun initializeNewPipe() {
        if (isInitialized) return
        synchronized(newPipeInitLock) {
            if (!isInitialized) {
                try {
                    NewPipe.init(NewPipeDownloaderImpl(http.newPipeClient, sessionManager))
                } catch (_: Exception) {
                    // NewPipe may already have been initialized by another
                    // process entry point. Its singleton is still usable.
                }
                isInitialized = true
            }
        }
    }

    /**
     * A NewPipe search extractor that ranks for [YouTubeHttp.contentRegion].
     *
     * NewPipe keeps one global localization, defaulting to en-GB, so without
     * this every NewPipe-backed search (videos, artists, albums, playlists)
     * was British whatever the device said. Re-applied only when the region
     * changed; the language stays English for the same reason as the InnerTube
     * context (see ThemePreferences.resolveContentRegion).
     */
    fun regionalSearchExtractor(
        query: String,
        filters: List<String>,
        sort: String,
    ): org.schabi.newpipe.extractor.search.SearchExtractor {
        val region = http.contentRegion()
        if (region != appliedNewPipeRegion) {
            NewPipe.setupLocalization(
                org.schabi.newpipe.extractor.localization.Localization("en", region),
                org.schabi.newpipe.extractor.localization.ContentCountry(region)
            )
            appliedNewPipeRegion = region
        }
        return youtubeService.getSearchExtractor(query, filters, sort)
    }

    // Blocking NewPipe extractions are started here rather than in the caller's
    // scope. A coroutine timeout can only free the *caller*: the extraction
    // itself is uninterruptible until newPipeClient's callTimeout fires, and a
    // child job would keep the parent's coroutineScope waiting for exactly the
    // work it is trying to abandon. Detaching it is what lets the InnerTube
    // fallback start on time. SupervisorJob so one failed extraction cannot
    // cancel the scope every later one needs.
    val newPipeScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO,
    )

    private companion object {
        @Volatile private var isInitialized = false

        @Volatile private var appliedNewPipeRegion: String? = null

        private val newPipeInitLock = Any()
    }
}

// ServiceList eagerly constructs every extractor NewPipe supports. Koda
// only uses YouTube, so keep the equivalent service instance directly
// and let R8 discard the SoundCloud, PeerTube, Bandcamp and MediaCCC
// implementations.
internal val youtubeService = YoutubeService(0)
