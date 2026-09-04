package com.ivor.ivormusic.data.scrobble

import android.content.Context
import com.ivor.ivormusic.data.IncognitoMode
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Unified repository managing Last.fm and ListenBrainz scrobbling,
 * now-playing announcements, credential states, and offline queue drainage.
 */
class ScrobbleRepository(context: Context) {

    private val appContext = context.applicationContext
    val credentialsStore = ScrobbleCredentialsStore(appContext)
    val queueRepository = ScrobbleQueueRepository(appContext)
    val lastFmClient = LastFmClient()
    val listenBrainzClient = ListenBrainzClient()
    private val themePreferences = ThemePreferences(appContext)

    private val drainMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pendingQueueCount = MutableStateFlow(0)
    val pendingQueueCount: StateFlow<Int> = _pendingQueueCount.asStateFlow()

    init {
        refreshPendingCount()
    }

    companion object {
        private const val TAG = "ScrobbleRepository"

        @Volatile
        private var instance: ScrobbleRepository? = null

        fun getInstance(context: Context): ScrobbleRepository {
            return instance ?: synchronized(this) {
                instance ?: ScrobbleRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    fun refreshPendingCount() {
        scope.launch {
            _pendingQueueCount.value = queueRepository.getPendingCount()
        }
    }

    /**
     * Announces "Now Playing" status to enabled services.
     *
     * Gated on [IncognitoMode.isEnabled]; if incognito is on, no announcement is sent.
     */
    suspend fun updateNowPlaying(track: ScrobbleTrack) = withContext(Dispatchers.IO) {
        if (IncognitoMode.isEnabled(appContext)) {
            KLog.d(TAG, "Incognito mode active; suppressing Now Playing update")
            return@withContext
        }

        val lastFmEnabled = themePreferences.getLastFmEnabled()
        val listenBrainzEnabled = themePreferences.getListenBrainzEnabled()

        if (lastFmEnabled && credentialsStore.isLastFmAuthenticated()) {
            val apiKey = credentialsStore.getLastFmApiKey().orEmpty()
            val apiSecret = credentialsStore.getLastFmApiSecret().orEmpty()
            val sessionKey = credentialsStore.getLastFmSessionKey().orEmpty()

            scope.launch {
                val result = lastFmClient.updateNowPlaying(track, sessionKey, apiKey, apiSecret)
                if (result is ScrobbleResult.AuthRequired) {
                    KLog.w(TAG, "Last.fm session expired or invalid; clearing session")
                    credentialsStore.clearLastFmSession()
                }
            }
        }

        if (listenBrainzEnabled && credentialsStore.isListenBrainzConfigured()) {
            val token = credentialsStore.getListenBrainzToken().orEmpty()
            val customUrl = themePreferences.getListenBrainzCustomUrl()

            scope.launch {
                val result = listenBrainzClient.updateNowPlaying(track, token, customUrl)
                if (result is ScrobbleResult.AuthRequired) {
                    KLog.w(TAG, "ListenBrainz token invalid; clearing token")
                    credentialsStore.clearListenBrainz()
                }
            }
        }

        // Whenever now playing succeeds, trigger an opportunistic queue drain
        triggerQueueDrain()
    }

    /**
     * Submits a scrobble for a completed track listening threshold.
     *
     * Gated on [IncognitoMode.isEnabled]; if incognito is on, no scrobble is recorded.
     */
    suspend fun scrobbleTrack(track: ScrobbleTrack) = withContext(Dispatchers.IO) {
        if (IncognitoMode.isEnabled(appContext)) {
            KLog.d(TAG, "Incognito mode active; suppressing scrobble")
            return@withContext
        }

        val lastFmEnabled = themePreferences.getLastFmEnabled()
        val listenBrainzEnabled = themePreferences.getListenBrainzEnabled()

        if (!lastFmEnabled && !listenBrainzEnabled) {
            return@withContext
        }

        var needQueueLastFm = false
        var needQueueListenBrainz = false

        if (lastFmEnabled) {
            if (credentialsStore.isLastFmAuthenticated()) {
                val apiKey = credentialsStore.getLastFmApiKey().orEmpty()
                val apiSecret = credentialsStore.getLastFmApiSecret().orEmpty()
                val sessionKey = credentialsStore.getLastFmSessionKey().orEmpty()

                val result = lastFmClient.scrobbleBatch(listOf(track), sessionKey, apiKey, apiSecret)
                when (result) {
                    is ScrobbleResult.Success -> {
                        KLog.i(TAG, "Last.fm scrobble successful: ${track.artist} - ${track.title}")
                    }
                    is ScrobbleResult.AuthRequired -> {
                        KLog.w(TAG, "Last.fm authentication required; clearing session")
                        credentialsStore.clearLastFmSession()
                        needQueueLastFm = true
                    }
                    is ScrobbleResult.Failure -> {
                        KLog.w(TAG, "Last.fm scrobble failed: ${result.error}; queuing offline")
                        if (result.canRetry) needQueueLastFm = true
                    }
                }
            } else {
                needQueueLastFm = true
            }
        }

        if (listenBrainzEnabled) {
            if (credentialsStore.isListenBrainzConfigured()) {
                val token = credentialsStore.getListenBrainzToken().orEmpty()
                val customUrl = themePreferences.getListenBrainzCustomUrl()

                val result = listenBrainzClient.scrobbleSingle(track, token, customUrl)
                when (result) {
                    is ScrobbleResult.Success -> {
                        KLog.i(TAG, "ListenBrainz scrobble successful: ${track.artist} - ${track.title}")
                    }
                    is ScrobbleResult.AuthRequired -> {
                        KLog.w(TAG, "ListenBrainz token invalid; clearing token")
                        credentialsStore.clearListenBrainz()
                        needQueueListenBrainz = true
                    }
                    is ScrobbleResult.Failure -> {
                        KLog.w(TAG, "ListenBrainz scrobble failed: ${result.error}; queuing offline")
                        if (result.canRetry) needQueueListenBrainz = true
                    }
                }
            } else {
                needQueueListenBrainz = true
            }
        }

        if (needQueueLastFm || needQueueListenBrainz) {
            queueRepository.enqueue(track, needQueueLastFm, needQueueListenBrainz)
            refreshPendingCount()
        }
    }

    /**
     * Opportunistically triggers offline queue draining in the background.
     */
    fun triggerQueueDrain() {
        scope.launch {
            drainQueue()
        }
    }

    /**
     * Drains the offline scrobble queue in batches up to 50.
     */
    suspend fun drainQueue() = withContext(Dispatchers.IO) {
        if (!drainMutex.tryLock()) {
            return@withContext // Drain already in flight
        }

        try {
            while (true) {
                val batch = queueRepository.peekBatch(50)
                if (batch.isEmpty()) break

                val lastFmItems = batch.filter { it.pendingLastFm }
                val listenBrainzItems = batch.filter { it.pendingListenBrainz }

                val completedLastFm = mutableSetOf<String>()
                val completedListenBrainz = mutableSetOf<String>()
                val unrecoverable = mutableSetOf<String>()

                // Process Last.fm batch
                if (lastFmItems.isNotEmpty() && themePreferences.getLastFmEnabled()) {
                    if (credentialsStore.isLastFmAuthenticated()) {
                        val apiKey = credentialsStore.getLastFmApiKey().orEmpty()
                        val apiSecret = credentialsStore.getLastFmApiSecret().orEmpty()
                        val sessionKey = credentialsStore.getLastFmSessionKey().orEmpty()

                        val tracks = lastFmItems.map { it.track }
                        val result = lastFmClient.scrobbleBatch(tracks, sessionKey, apiKey, apiSecret)
                        when (result) {
                            is ScrobbleResult.Success -> {
                                completedLastFm.addAll(lastFmItems.map { it.id })
                            }
                            is ScrobbleResult.AuthRequired -> {
                                credentialsStore.clearLastFmSession()
                                break // Stop draining until user re-authenticates
                            }
                            is ScrobbleResult.Failure -> {
                                if (!result.canRetry) {
                                    unrecoverable.addAll(lastFmItems.map { it.id })
                                } else {
                                    break // Temporary failure / offline; stop this drain cycle
                                }
                            }
                        }
                    }
                }

                // Process ListenBrainz batch
                if (listenBrainzItems.isNotEmpty() && themePreferences.getListenBrainzEnabled()) {
                    if (credentialsStore.isListenBrainzConfigured()) {
                        val token = credentialsStore.getListenBrainzToken().orEmpty()
                        val customUrl = themePreferences.getListenBrainzCustomUrl()

                        val tracks = listenBrainzItems.map { it.track }
                        val result = listenBrainzClient.scrobbleBatch(tracks, token, customUrl)
                        when (result) {
                            is ScrobbleResult.Success -> {
                                completedListenBrainz.addAll(listenBrainzItems.map { it.id })
                            }
                            is ScrobbleResult.AuthRequired -> {
                                credentialsStore.clearListenBrainz()
                                break
                            }
                            is ScrobbleResult.Failure -> {
                                if (!result.canRetry) {
                                    unrecoverable.addAll(listenBrainzItems.map { it.id })
                                } else {
                                    break
                                }
                            }
                        }
                    }
                }

                queueRepository.completeItems(
                    completedLastFmIds = completedLastFm,
                    completedListenBrainzIds = completedListenBrainz,
                    unrecoverableIds = unrecoverable
                )

                // If nothing could be completed in this round, stop to avoid infinite busy-loop
                if (completedLastFm.isEmpty() && completedListenBrainz.isEmpty() && unrecoverable.isEmpty()) {
                    break
                }
            }
        } finally {
            _pendingQueueCount.value = queueRepository.getPendingCount()
            drainMutex.unlock()
        }
    }
}
