package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.data.youtube.BROWSER_USER_AGENT
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The `playbackContext.contentPlaybackContext.signatureTimestamp` that WEB and
 * WEB_REMIX `/player` must carry for the history reports.
 *
 * [verified October 2026] Without it both clients answer every video, signed in
 * or out, with `UNPLAYABLE` / "Video unavailable" (errorScreen subreason "The
 * page needs to be reloaded") and no `playbackTracking`, so neither history
 * ping has a URL to send to. The client version makes no difference. With any
 * value from 20515 upwards (the live player said 20725, and even 99999 passed)
 * both answer OK with tracking URLs.
 *
 * The value is the player script's build day counted from the Unix epoch
 * (20725 is 28 September 2026, the build that was live on 2 October), and
 * YouTube accepts roughly the last seven months of them. So it is resolved in
 * three layers, most exact first:
 *
 * 1. The live value, read from the current player script (`iframe_api` names
 *    the player id, 1 KB; `base.js` holds `signatureTimestamp:<n>`, ~3 MB but
 *    downloaded only when the player id changes, about weekly). Persisted, and
 *    the player id rechecked at most once a day.
 * 2. Otherwise an estimate from today's date, a fortnight back, and never below
 *    a stored live value. It needs no network, so history keeps working when
 *    YouTube reshapes the player script and the live read breaks.
 * 3. [refreshAfterRejection]: a caller whose `/player` came back without
 *    tracking asks for a fresh read and retries once if the value changed.
 *
 * State lives in SharedPreferences, so every [YouTubeRepository] instance sees
 * the same value without a new piece of process-wide repository state.
 */
internal class PlayerSignatureTimestamp(
    context: Context,
    private val client: OkHttpClient,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The value to send now. Refreshes the live read when it is due. */
    suspend fun current(): Int {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_CHECKED_AT, 0L) >= RECHECK_INTERVAL_MS &&
            now - prefs.getLong(KEY_FAILED_AT, 0L) >= FAILURE_BACKOFF_MS
        ) {
            refresh(now)
        }
        return best(now)
    }

    /**
     * After a `/player` came back without tracking URLs. Returns a value worth
     * retrying with, or null when it would only repeat [rejected] (a video that
     * is really unavailable must not cost a second `/player`). Rate-limited, so
     * a run of unavailable videos does not refetch the player script each time.
     */
    suspend fun refreshAfterRejection(rejected: Int): Int? {
        val now = System.currentTimeMillis()
        val lastAttempt = maxOf(prefs.getLong(KEY_CHECKED_AT, 0L), prefs.getLong(KEY_FAILED_AT, 0L))
        if (now - lastAttempt >= FORCED_REFRESH_MIN_MS) refresh(now)
        val next = best(now)
        if (next != rejected) return next
        // The live read is what was rejected: the date estimate is the one
        // independent answer left.
        val estimate = estimate(now)
        return estimate.takeIf { it != rejected }
    }

    private fun best(now: Long): Int {
        val stored = prefs.getInt(KEY_VALUE, 0)
        val fresh = now - prefs.getLong(KEY_CHECKED_AT, 0L) < STALE_AFTER_MS
        return if (stored > 0 && fresh) stored else maxOf(stored, estimate(now))
    }

    private fun estimate(now: Long): Int =
        (now / DAY_MS - ESTIMATE_LAG_DAYS).toInt()

    private suspend fun refresh(now: Long): Unit = refreshLock.withLock {
        // Another caller may have refreshed while this one waited.
        if (maxOf(prefs.getLong(KEY_CHECKED_AT, 0L), prefs.getLong(KEY_FAILED_AT, 0L)) > now) {
            return@withLock
        }
        withContext(Dispatchers.IO) {
            try {
                val playerId = fetch(IFRAME_API_URL)
                    ?.let { PLAYER_ID.find(it)?.groupValues?.get(1) }
                    ?: return@withContext failed("no player id in iframe_api")
                val stored = prefs.getInt(KEY_VALUE, 0)
                if (stored > 0 && playerId == prefs.getString(KEY_PLAYER_ID, null)) {
                    prefs.edit().putLong(KEY_CHECKED_AT, System.currentTimeMillis()).apply()
                    return@withContext
                }
                val value = fetch("https://www.youtube.com/s/player/$playerId/player_ias.vflset/en_US/base.js")
                    ?.let { SIGNATURE_TIMESTAMP.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                    ?: return@withContext failed("no signatureTimestamp in player $playerId")
                prefs.edit()
                    .putInt(KEY_VALUE, value)
                    .putString(KEY_PLAYER_ID, playerId)
                    .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                    .apply()
                KLog.i("YouTubeRepo", "Player signatureTimestamp $value from player $playerId")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun failed(why: String) {
        prefs.edit().putLong(KEY_FAILED_AT, System.currentTimeMillis()).apply()
        KLog.w("YouTubeRepo", "Player signatureTimestamp read failed ($why), using the date estimate")
    }

    private fun fetch(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }

    companion object {
        private const val PREFS = "youtube_player_sts"
        private const val KEY_VALUE = "value"
        private const val KEY_PLAYER_ID = "player_id"
        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_FAILED_AT = "failed_at"

        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val RECHECK_INTERVAL_MS = DAY_MS
        /** A live read older than this yields to the date estimate if that is newer. */
        private const val STALE_AFTER_MS = 14 * DAY_MS
        private const val FAILURE_BACKOFF_MS = 60L * 60 * 1000
        private const val FORCED_REFRESH_MIN_MS = 10L * 60 * 1000
        /** Player builds run a few days behind the date; two weeks stays well inside the accepted window. */
        private const val ESTIMATE_LAG_DAYS = 14

        private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
        /** iframe_api writes the script URL JS-escaped (`\/s\/player\/<id>\/`). */
        private val PLAYER_ID = Regex("""\\?/s\\?/player\\?/([A-Za-z0-9_-]{8})\\?/""")
        private val SIGNATURE_TIMESTAMP = Regex("""signatureTimestamp[=:]"?(\d{5,})""")

        /** One refresh at a time across repository instances; a lock, not repository state. */
        private val refreshLock = Mutex()
    }
}
