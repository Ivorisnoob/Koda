package com.ivor.ivormusic.presence

import android.content.Context
import android.os.SystemClock
import com.ivor.ivormusic.data.IncognitoMode
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.KLog
import com.ivor.ivormusic.widget.PlayerWidgetSnapshot
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Publishes what is playing in the music player to Discord as a Rich Presence
 * activity. Video playback never reaches this class.
 *
 * Koda has no DI framework, so this is a process singleton started from
 * `IvorMusicApplication`, and its playback feed is the widget snapshot
 * `MusicService` already publishes on every player event - the one place that
 * cannot be wrong about it - rather than a new MediaController bind per push.
 *
 * When a card is on the profile:
 * - Only while the switch is on (it is off by default) and Incognito is off.
 *   Incognito is read again at the push itself, so a card cannot slip out
 *   between the toggle and the flow that reports it.
 * - A playing track shows straight away. A frame is sent when something the
 *   card shows changes, or when the progress bar has drifted (a seek, a long
 *   buffer) - never on the position ticker and never on a timer.
 * - A pause swaps the card for a paused one, which is taken down after
 *   [DiscordPresence.PAUSE_LINGER_MS]. A pause never puts a card up on its own:
 *   a session restored paused at launch stays off the profile until it plays.
 * - Stopping the playback service takes the card down at once.
 *
 * Whenever no card is showing the connection is released too. A bound service
 * holds the Discord app's process up, and Koda must not be the reason Discord
 * stays alive while nothing is being shown.
 *
 * Connection attempts are throttled by [DiscordPresence.RETRY_INTERVAL_MS],
 * because the common case is a user who simply does not have Discord installed.
 *
 * Playback is never touched, delayed or gated by anything in here: every
 * failure is swallowed and every frame is fire-and-forget.
 */
object DiscordPresenceManager {

    private const val TAG = "DiscordPresence"

    private val lock = Mutex()

    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var started = false

    /** The switch is on and Incognito is off. False until the flows report. */
    @Volatile private var allowed = false
    @Volatile private var lastSnapshot: PlayerWidgetSnapshot? = null

    // Everything below is guarded by [lock].
    private var transport: DiscordTransport? = null
    /** Signature of the card on the profile; empty when none is showing. */
    private var lastKey = ""
    private var lastPushMs = 0L
    /** Wall-clock end of the progress bar last sent; 0 when it had none. */
    private var lastEndMs = 0L
    /** When the last connection attempt failed; 0 when the next may go now. */
    private var lastAttemptMs = 0L
    /** When the current pause began; 0 while playing. */
    private var pausedSinceMs = 0L
    private var pauseExpiry: Job? = null

    /** Starts the observer. Safe to call once from the Application. */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        appContext = app
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        // ThemePreferences fans every write out to all instances through its
        // SharedPreferences listener, so this flow stays current no matter
        // which screen's instance the toggle was flipped through.
        val prefs = ThemePreferences(app)
        s.launch {
            combine(prefs.discordPresence, IncognitoMode.enabled(app)) { on, incognito ->
                on && !incognito
            }.distinctUntilChanged().collect {
                allowed = it
                evaluate()
            }
        }
    }

    /**
     * Feeds one playback snapshot in. Called from `MusicService` beside the
     * widget publish; never blocks the caller.
     */
    fun onSnapshot(snapshot: PlayerWidgetSnapshot) {
        lastSnapshot = snapshot
        scope?.launch { evaluate() }
    }

    /** Takes the card down and lets go of Discord when the playback service goes away. */
    fun onServiceStopped() {
        // Forgotten, not just cleared: a snapshot left behind would be
        // republished by the next evaluation as a track still playing.
        lastSnapshot = null
        scope?.launch { evaluate() }
    }

    /**
     * Brings the profile in line with the newest snapshot. It reads
     * [lastSnapshot] rather than taking one, because evaluations are launched
     * per player event and are not guaranteed to run in the order they were
     * launched - an argument could be an older state than the one on screen.
     */
    private suspend fun evaluate() {
        lock.withLock {
            val now = SystemClock.elapsedRealtime()
            val context = appContext
            val snapshot = lastSnapshot
            if (
                !allowed || context == null || IncognitoMode.isEnabled(context) ||
                !DiscordPresence.isConfigured() ||
                snapshot == null || !snapshot.hasMedia || snapshot.title.isNullOrBlank()
            ) {
                endPause()
                hideAndRelease()
                return
            }

            if (snapshot.isPlaying) {
                endPause()
            } else {
                // Nothing on the profile: a pause has no card to replace.
                if (lastKey.isEmpty()) return
                if (pausedSinceMs == 0L) {
                    pausedSinceMs = now
                    // Paused players stop publishing, so the linger needs its
                    // own wake-up to take the card down.
                    pauseExpiry?.cancel()
                    pauseExpiry = scope?.launch {
                        delay(DiscordPresence.PAUSE_LINGER_MS)
                        evaluate()
                    }
                }
                if (now - pausedSinceMs >= DiscordPresence.PAUSE_LINGER_MS) {
                    hideAndRelease()
                    return
                }
            }

            val key = DiscordPresence.signature(snapshot)
            val wallNow = System.currentTimeMillis()
            val endMs = DiscordPresence.endTimeMs(snapshot, wallNow)
            if (key == lastKey && transport?.isOpen == true) {
                // Same card, live connection: only a moved progress bar is
                // worth a frame, and not more often than the push interval.
                val drifted = endMs != 0L && abs(endMs - lastEndMs) > DiscordPresence.SEEK_DRIFT_MS
                if (!drifted || now - lastPushMs < DiscordPresence.PUSH_INTERVAL_MS) return
            }

            val active = ensureConnected(context, now) ?: return
            val sent = runCatching {
                active.setActivity(DiscordPresence.buildActivity(snapshot, wallNow))
            }.onFailure {
                KLog.w(TAG, "Discord presence push failed: ${it.message}")
            }.getOrDefault(false)
            if (!sent) {
                // Transport died mid-push: drop it so the next evaluation
                // reconnects instead of writing into a dead connection.
                KLog.w(TAG, "Discord push rejected; dropping connection")
                runCatching { active.close() }
                transport = null
                lastAttemptMs = SystemClock.elapsedRealtime()
                return
            }
            lastKey = key
            lastPushMs = now
            lastEndMs = endMs
        }
    }

    /** Existing connection, or a new one if the retry throttle allows it. */
    private suspend fun ensureConnected(context: Context, now: Long): DiscordTransport? {
        val existing = transport
        if (existing != null) {
            if (existing.isOpen) return existing
            KLog.d(TAG, "Discord transport dead; reconnecting")
            runCatching { existing.close() }
            transport = null
        }
        if (lastAttemptMs != 0L && now - lastAttemptMs < DiscordPresence.RETRY_INTERVAL_MS) return null
        val fresh = DiscordIpcTransport.connectOrNull(context)
        if (fresh == null) {
            lastAttemptMs = SystemClock.elapsedRealtime()
            KLog.w(TAG, "Discord connect failed (app missing/signed-out/refusing?); retrying later")
            return null
        }
        lastAttemptMs = 0L
        transport = fresh
        return fresh
    }

    private fun endPause() {
        pausedSinceMs = 0L
        pauseExpiry?.cancel()
        pauseExpiry = null
    }

    /**
     * Takes the card down and releases the connection, and forgets the last
     * signature so the next real track is never deduped away as "unchanged".
     */
    private fun hideAndRelease() {
        lastKey = ""
        lastEndMs = 0L
        val active = transport ?: return
        transport = null
        runCatching { active.clearActivity() }
            .onFailure { KLog.d(TAG, "Discord presence clear ignored: ${it.message}") }
        runCatching { active.close() }
    }
}
