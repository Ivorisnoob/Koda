package com.ivor.ivormusic.data

import org.json.JSONObject

/** WEB FEhistory lockup shape, verified against signed-in responses September 2026. */
internal fun parseVideoWatchProgress(lockup: JSONObject): Float? {
    val overlays = lockup.optJSONObject("contentImage")
        ?.optJSONObject("thumbnailViewModel")?.optJSONArray("overlays") ?: return null
    for (index in 0 until overlays.length()) {
        val percent = overlays.optJSONObject(index)
            ?.optJSONObject("thumbnailBottomOverlayViewModel")
            ?.optJSONObject("progressBar")
            ?.optJSONObject("thumbnailOverlayProgressBarViewModel")
            ?.optDouble("startPercent", Double.NaN) ?: continue
        if (percent.isFinite()) return (percent / 100.0).toFloat().coerceIn(0f, 1f)
    }
    return null
}

internal data class VideoHistorySession(
    val videoId: String,
    /**
     * The login this reporting session belongs to. Held as a session rather
     * than as the cookie string it was started with: Google rotates those
     * mid-video, and treating a rotation as a different login is what used to
     * stop history reporting partway through.
     */
    val login: YouTubeSession,
    val cpn: String,
    val playbackUrl: String,
    val watchtimeUrl: String,
)

/** What became of a history ping. Only [SESSION_ENDED] invalidates the caller's session. */
internal enum class HistoryPingResult { SENT, FAILED, SESSION_ENDED }

/** Only playing wall time qualifies a watch; seeks never manufacture watched seconds. */
internal class VideoWatchClock(private val thresholdMs: Long) {
    var playedMs: Long = 0L
        private set
    private var lastAtMs: Long? = null
    private var wasPlaying = false

    fun sample(nowMs: Long, isPlaying: Boolean, enabled: Boolean): Boolean {
        val previous = lastAtMs
        if (enabled && wasPlaying && previous != null) {
            playedMs += (nowMs - previous).coerceIn(0L, 2_000L)
        }
        lastAtMs = nowMs
        wasPlaying = isPlaying && enabled
        if (!enabled) playedMs = 0L
        return playedMs >= thresholdMs
    }
}
