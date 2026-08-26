package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.util.KLog

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.cast.CastPlayer
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A Chromecast discovered on the network. [id] is the MediaRouter route id
 * handed back to [connect]; [name] is what the device sheet shows.
 */
data class CastRoute(val id: String, val name: String)

/**
 * Everything about being *connected* to a Chromecast, deliberately kept out of
 * [VideoPlayerViewModel]: route discovery through MediaRouter, session start
 * and teardown through the Cast framework's SessionManager, and the two
 * receiver-side events CastPlayer cannot express - a stream finishing and a
 * load failing.
 *
 * **Why discovery is MediaRouter rather than the framework's chooser dialog.**
 * The stock `CastButtonFactory` + dialog path requires the hosting activity to
 * be a FragmentActivity; MainActivity is a ComponentActivity for Compose, and
 * Google's chooser is Material 2 besides. Selecting a MediaRouter route
 * directly triggers the same session machinery the button would, so the app
 * gets its own M3 Expressive sheet over identical plumbing.
 *
 * **The callbacks exist because CastPlayer never fires
 * Player.Listener.onPlayerError** (getPlayerError is hardcoded null) and maps a
 * finished broadcast to STATE_IDLE rather than STATE_ENDED. The ViewModel
 * therefore learns about end-of-media and load failures from here - off the
 * RemoteMediaClient's idle reason - and feeds them into the same queue-advance
 * and re-resolve logic the local player already has.
 */
@UnstableApi
class VideoCastManager(
    private val context: Context,
    private val playbackKind: CastPlaybackKind = CastPlaybackKind.VIDEO
) {

    /** False when Play services are missing: everything degrades to "no cast". */
    val available: Boolean = try {
        GoogleApiAvailabilityLight.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (_: Exception) {
        false
    }

    private val castContext: CastContext? = if (!available) null else try {
        @Suppress("VisibleForTests")
        CastContext.getSharedInstance(context)
    } catch (e: Exception) {
        KLog.w(TAG, "CastContext unavailable", e)
        null
    }

    private val sessionManager: SessionManager?
        get() = castContext?.sessionManager

    private val _receivers = MutableStateFlow<List<CastRoute>>(emptyList())
    val receivers: StateFlow<List<CastRoute>> = _receivers.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    /** The current video finished on the receiver. Drives autoplay while casting. */
    var onRemoteFinished: (() -> Unit)? = null

    /** A load or playback failed on the receiver. Routed into re-resolve/skip logic. */
    var onRemoteFailed: (() -> Unit)? = null

    /**
     * The session ended without this app asking for it (receiver died, network
     * dropped, someone took over). Carries the last position the remote
     * reported so playback can resume locally where the TV left off.
     */
    var onSessionLost: ((positionMs: Long) -> Unit)? = null

    // ---------------- Discovery ----------------

    private val routeSelector: MediaRouteSelector? = try {
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            )
            .build()
    } catch (_: Exception) {
        null
    }

    private val mediaRouter: MediaRouter?
        get() = try { MediaRouter.getInstance(context) } catch (_: Exception) { null }

    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) =
            refreshRoutes()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) =
            refreshRoutes()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) =
            refreshRoutes()
    }

    /**
     * Begin scanning for receivers. Called when the device sheet opens rather
     * than at process start: continuous discovery keeps radios awake for a
     * feature most sessions never open.
     */
    fun startDiscovery() {
        val router = mediaRouter ?: return
        val selector = routeSelector ?: return
        refreshRoutes()
        router.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
    }

    fun stopDiscovery() {
        mediaRouter?.removeCallback(routeCallback)
    }

    private fun refreshRoutes() {
        val router = mediaRouter ?: return
        val selector = routeSelector ?: return
        _receivers.value = router.routes
            .filter {
                it.isEnabled && !it.isDefault && it.matchesSelector(selector)
            }
            .map { CastRoute(it.id, it.name.ifBlank { it.description ?: "Cast device" }) }
            .distinctBy { it.id }
    }

    /**
     * Whether a Cast session already exists - joined automatically after a
     * process restart, or started by another surface. The ViewModel adopts it
     * rather than ending someone's TV playback because they reopened the app.
     */
    fun hasLiveSession(): Boolean = try {
        sessionManager?.currentCastSession?.let { it.isConnected || it.isConnecting } == true
    } catch (_: Exception) {
        false
    }

    /** Last position the receiver reported, for a resume that lands where the TV was. */
    fun remotePositionMs(): Long = try {
        sessionManager?.currentCastSession?.remoteMediaClient
            ?.approximateStreamPosition?.coerceAtLeast(0L) ?: 0L
    } catch (_: Exception) {
        0L
    }

    /**
     * The receiver's current media status, or null when nothing is loaded.
     * Read when adopting a session that predates this process.
     */
    fun currentMediaStatus(): MediaStatus? = try {
        sessionManager?.currentCastSession?.remoteMediaClient?.mediaStatus
    } catch (_: Exception) {
        null
    }

    /**
     * Build a [CastPlayer] over the live session, with Koda's converter so
     * loads carry live stream type and caption tracks. Null when Play services
     * cannot produce a CastContext.
     */
    fun createPlayer(): CastPlayer? {
        val ctx = castContext ?: return null
        return try {
            @Suppress("VisibleForTests")
            CastPlayer(ctx, KodaCastMediaItemConverter(playbackKind))
        } catch (e: Exception) {
            KLog.w(TAG, "Could not create CastPlayer", e)
            null
        }
    }

    // ---------------- Connect / disconnect ----------------

    /**
     * Start a session by selecting the MediaRouter route. Suspends until the
     * framework reports the session connected, the budget runs out, or the
     * caller's coroutine is cancelled. True only means "the receiver accepted
     * us" - nothing has been loaded onto it yet.
     */
    suspend fun connect(routeId: String): Boolean {
        val manager = sessionManager ?: return false
        val router = mediaRouter ?: return false
        val route = router.routes.firstOrNull { it.id == routeId } ?: return false
        if (_isConnecting.value) return false

        synchronized(ownerLock) {
            val owner = activePlaybackKind
            if (owner != null && owner != playbackKind) return false
            activePlaybackKind = playbackKind
        }
        _isConnecting.value = true
        try {
            router.selectRoute(route)
            val deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (manager.currentCastSession?.isConnected == true) return true
                kotlinx.coroutines.delay(CONNECT_POLL_MS)
            }
            KLog.w(TAG, "Cast connect timed out for route $routeId")
            clearOwnerIfMine()
            return false
        } catch (e: Exception) {
            KLog.w(TAG, "Cast connect failed", e)
            clearOwnerIfMine()
            return false
        } finally {
            _isConnecting.value = false
        }
    }

    /**
     * End the session deliberately - a user disconnect or the player closing.
     * Marks the teardown as expected so the listener below does not double-fire
     * the loss path: the caller has usually captured state and resumed playback
     * itself before this event lands. [stopOnReceiver] decides whether the TV
     * also stops; leaving it running would sit on a paused poster playing
     * nothing but its own light.
     */
    fun endSession(stopOnReceiver: Boolean) {
        expectingOwnTeardown = true
        try {
            sessionManager?.endCurrentSession(stopOnReceiver)
        } catch (e: Exception) {
            KLog.w(TAG, "Ending cast session failed", e)
        }
    }

    @Volatile private var expectingOwnTeardown = false

    /** Last position the receiver reported, kept fresh for an honest local resume. */
    @Volatile private var lastKnownRemotePositionMs = 0L

    // ---------------- Session + remote listeners ----------------

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) =
            onSessionUp(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) =
            onSessionUp(session)

        override fun onSessionEnded(session: CastSession, errorCode: Int) = onSessionDown()

        override fun onSessionSuspended(session: CastSession, reason: Int) = onSessionDown()

        override fun onSessionStartFailed(session: CastSession, errorCode: Int) {}
        override fun onSessionResumeFailed(session: CastSession, errorCode: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

    private fun onSessionUp(session: CastSession) {
        val statusOwner = CastPlaybackKind.fromWireValue(
            session.remoteMediaClient?.mediaStatus?.mediaInfo?.customData
                ?.optString(CAST_PLAYBACK_KIND_KEY)
        )
        synchronized(ownerLock) {
            if (statusOwner != null) activePlaybackKind = statusOwner
        }
        if (activePlaybackKind != playbackKind) {
            _deviceName.value = null
            _isSessionActive.value = false
            detachRemoteListener()
            return
        }
        expectingOwnTeardown = false
        _deviceName.value = session.castDevice?.friendlyName ?: _deviceName.value
        _isSessionActive.value = true
        attachRemoteListener()
    }

    /**
     * An app-initiated end ([endSession]) cleans up silently; anything else -
     * receiver power-off, network loss, another sender taking over - reaches
     * the ViewModel so it can hand playback back to the phone instead of just
     * stopping.
     */
    private fun onSessionDown() {
        val wasActive = _isSessionActive.value
        val position = lastKnownRemotePositionMs
        _deviceName.value = null
        _isSessionActive.value = false
        detachRemoteListener()
        if (!expectingOwnTeardown && wasActive) {
            onSessionLost?.invoke(position)
        }
        expectingOwnTeardown = false
        clearOwnerIfMine()
    }

    private fun clearOwnerIfMine() {
        synchronized(ownerLock) {
            if (activePlaybackKind == playbackKind) activePlaybackKind = null
        }
    }

    /** Installed by [beginObservation]; the ViewModel owns the whole lifecycle. */
    private var observing = false

    /**
     * Register the session and remote-status listeners once, when the owning
     * ViewModel is created. Any session that already exists (a reconnect after
     * process death) fires immediately through the listener contract.
     */
    fun beginObservation() {
        if (observing) return
        observing = true
        sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        try {
            sessionManager?.currentCastSession?.let { if (it.isConnected) onSessionUp(it) }
        } catch (_: Exception) {
        }
    }

    fun endObservation() {
        if (!observing) return
        observing = false
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        detachRemoteListener()
    }

    // ---------------- Remote status ----------------

    private var remoteClient: RemoteMediaClient? = null

    // Callback, not the Listener interface: registerCallback takes a Callback
    // (the no-op-implementation abstract class over Listener), and an anonymous
    // Listener cannot be handed to it directly.
    private val remoteStatusListener = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = remoteClient?.mediaStatus ?: return
            if (status.playerState != MediaStatus.PLAYER_STATE_IDLE) return
            when (status.idleReason) {
                MediaStatus.IDLE_REASON_FINISHED -> onRemoteFinished?.invoke()
                // CANCELLED is what a stop()/load-replacement looks like, not a
                // failure; only ERROR means the receiver could not play it.
                MediaStatus.IDLE_REASON_ERROR -> onRemoteFailed?.invoke()
            }
        }
    }

    private val remoteProgressListener = RemoteMediaClient.ProgressListener { progressMs, _ ->
        if (progressMs > 0) lastKnownRemotePositionMs = progressMs
    }

    private fun attachRemoteListener() {
        detachRemoteListener()
        val client = try {
            sessionManager?.currentCastSession?.remoteMediaClient
        } catch (_: Exception) {
            null
        } ?: return
        remoteClient = client
        lastKnownRemotePositionMs =
            client.approximateStreamPosition.coerceAtLeast(0L)
        try {
            client.registerCallback(remoteStatusListener)
            client.addProgressListener(remoteProgressListener, PROGRESS_REFRESH_MS)
        } catch (_: Exception) {
        }
    }

    private fun detachRemoteListener() {
        remoteClient?.let { client ->
            try {
                client.unregisterCallback(remoteStatusListener)
                client.removeProgressListener(remoteProgressListener)
            } catch (_: Exception) {
            }
        }
        remoteClient = null
    }

    companion object {
        private const val TAG = "VideoCastManager"

        /** A receiver that has not answered in this long is not going to. */
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val CONNECT_POLL_MS = 200L

        /** How often the remote position is sampled while it plays. */
        private const val PROGRESS_REFRESH_MS = 1_000L

        private val ownerLock = Any()
        @Volatile private var activePlaybackKind: CastPlaybackKind? = null
    }
}
