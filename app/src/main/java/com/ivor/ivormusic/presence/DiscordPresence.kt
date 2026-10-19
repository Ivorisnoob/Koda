package com.ivor.ivormusic.presence

import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.widget.PlayerWidgetSnapshot
import java.net.URLEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Discord Rich Presence payload builder: what the card says, and the frames
 * that carry it. How the frames reach Discord is [DiscordIpcTransport]'s
 * business, and when they are sent is [DiscordPresenceManager]'s.
 *
 * The card one [buildActivity] call produces:
 * ```
 *   Listening to Song title           <- type 2 + status_display_type details
 *   Song title                        <- details, opens the song
 *   Artist                            <- state, opens a search for the artist
 *   1:04 / 3:47                       <- from the timestamps, while playing
 *   [ Listen on YouTube Music ]       <- streams only
 *   [ Get Koda ]
 * ```
 *
 * Field names and limits verified October 2026 against Social SDK 1.10.19337:
 * the limits are the ones `discordpp.h` documents, and `status_display_type`,
 * `details_url`, `state_url`, `large_url` are the keys its library writes.
 * Discord drops a whole activity over one field outside its limits, so every
 * string goes through [text] or [link] on the way in.
 *
 * Quality is omitted on purpose. Koda plays YouTube streams with no measured
 * lossless chain to report, and a guessed badge would be a lie on the card.
 */
object DiscordPresence {

    /**
     * Application ID from the Discord Developer Portal (General Information).
     * Public by design - safe to ship in source (it is not a secret; only the
     * Client Secret / bot token must stay private).
     */
    const val APPLICATION_ID = "1556607739231346708"

    const val PROJECT_URL = "https://github.com/ivorisnoob/Koda"
    const val RELEASES_URL = "$PROJECT_URL/releases"

    /** Discord's bounds for the card's text fields and tooltips. */
    const val MIN_TEXT = 2
    const val MAX_TEXT = 128
    const val MAX_URL = 256
    const val MAX_IMAGE = 300

    /** Discord recommends artwork of at least this many pixels a side. */
    const val ARTWORK_PX = 1024

    /**
     * The least time between two frames for the same card. Playback publishes
     * several times a second and Discord rate-limits updates, so a progress
     * bar being dragged about costs at most one frame per interval. A change
     * of track or play state is not held back by it.
     */
    const val PUSH_INTERVAL_MS = 5_000L

    /**
     * How far the progress bar's end may move before it is worth a frame.
     * Discord counts the bar on its own clock, so steady playback never
     * drifts; a seek or a long buffer does.
     */
    const val SEEK_DRIFT_MS = 3_000L

    /** How long a paused card stays on the profile before it is taken down. */
    const val PAUSE_LINGER_MS = 60_000L

    /**
     * Reconnect throttle. While music plays a missing connection is retried at
     * most this often; a user without the Discord app costs one cheap lookup
     * per attempt and must never be retried harder.
     */
    const val RETRY_INTERVAL_MS = 15_000L

    /** 2 = "Listening to". */
    const val TYPE_LISTENING = 2

    /** 2 = the member list reads "Listening to <details>", not the app name. */
    const val STATUS_DISPLAY_DETAILS = 2

    /** Not whitespace, so Discord does not trim it away again. */
    private const val PAD = '⠀'

    fun isConfigured(): Boolean = APPLICATION_ID.toLongOrNull() != null

    /**
     * [value] fitted to a text field: trimmed, cut with an ellipsis when it is
     * too long, and padded when it is too short - a song called "7" is a real
     * title, and without the padding it takes the whole card down with it.
     * Null when there is nothing to show.
     */
    fun text(value: String?, limit: Int = MAX_TEXT): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val cut = if (trimmed.length > limit) trimmed.take(limit - 1).trimEnd() + "…" else trimmed
        return cut.padEnd(MIN_TEXT, PAD)
    }

    /** [value] when it is an http(s) URL Discord will accept, else null. */
    fun link(value: String?, limit: Int = MAX_URL): String? {
        val trimmed = value?.trim().orEmpty()
        val isWeb = trimmed.startsWith("https://") || trimmed.startsWith("http://")
        return trimmed.takeIf { isWeb && it.length <= limit }
    }

    /** A YouTube video id (11 base64-url chars); anything else is local. */
    internal fun isYouTubeId(value: String): Boolean =
        value.length == 11 && value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }

    /** Where the playing song can be opened by someone else, or null for a device file. */
    internal fun songUrl(snapshot: PlayerWidgetSnapshot): String? {
        val mediaId = snapshot.mediaId?.trim().orEmpty()
        return if (isYouTubeId(mediaId)) "https://music.youtube.com/watch?v=$mediaId" else null
    }

    /** Everything the card renders that is not a position tick. */
    fun signature(snapshot: PlayerWidgetSnapshot): String {
        return buildString {
            append(snapshot.mediaId.orEmpty()).append('|')
            append(snapshot.title.orEmpty()).append('|')
            append(snapshot.artist.orEmpty()).append('|')
            append(snapshot.album.orEmpty()).append('|')
            append("playing=").append(snapshot.isPlaying)
            append("|dur=").append(snapshot.durationMs / 1000L)
            append("|art=").append(snapshot.artworkUri?.toString().orEmpty())
        }
    }

    /**
     * Wall-clock time the progress bar runs out, or 0 when the card has no
     * bar (paused, or a duration that is not known yet).
     */
    fun endTimeMs(snapshot: PlayerWidgetSnapshot, nowMs: Long): Long {
        val duration = snapshot.durationMs
        if (!snapshot.isPlaying || duration <= 0L) return 0L
        // A position past the end (a stale tick on the last tick before the
        // next track) must not push the bar negative.
        return nowMs + (duration - snapshot.positionMs.coerceIn(0L, duration))
    }

    /**
     * The `SET_ACTIVITY` activity object. [nowMs] is wall clock and only feeds
     * the timestamps.
     *
     * Timestamps are a start/end pair rather than a position value so Discord
     * counts the bar on its own clock: a presence pushed once stays correct for
     * the rest of the track. A paused card carries none, so it shows no bar.
     */
    fun buildActivity(snapshot: PlayerWidgetSnapshot, nowMs: Long): JsonObject {
        val title = requireNotNull(text(snapshot.title)) { "no track title" }
        val artist = text(snapshot.artist)
        val album = text(snapshot.album)
        val endMs = endTimeMs(snapshot, nowMs)
        val songUrl = link(songUrl(snapshot))
        // Only a stream has an artist anyone else can look up.
        val artistUrl = if (songUrl != null && artist != null) {
            link("https://music.youtube.com/search?q=" + URLEncoder.encode(snapshot.artist!!.trim(), "UTF-8"))
        } else {
            null
        }
        // Discord fetches an external image itself, so only a web URL will do;
        // a device file's cover cannot be shown.
        val artwork = link(googleImageAtSize(snapshot.artworkUri?.toString(), ARTWORK_PX), MAX_IMAGE)

        return buildJsonObject {
            put("type", TYPE_LISTENING)
            put("status_display_type", STATUS_DISPLAY_DETAILS)
            put("name", "Koda")
            put("details", title)
            songUrl?.let { put("details_url", it) }
            artist?.let { put("state", it) }
            artistUrl?.let { put("state_url", it) }
            if (endMs != 0L) {
                put("timestamps", buildJsonObject {
                    put("start", (endMs - snapshot.durationMs) / 1000L)
                    put("end", endMs / 1000L)
                })
            }
            // Web images only. Asset keys uploaded in the Discord portal do not
            // resolve over the Android route (seen October 2026: a day-old
            // `play` key drew a "?" beside a cover that loaded), so there is no
            // play/pause badge, and a device file with no web cover sends no
            // image and gets the application's own icon from Discord.
            if (artwork != null) {
                put("assets", buildJsonObject {
                    put("large_image", artwork)
                    // The album, or nothing: the title is already on the card.
                    album?.let { put("large_text", it) }
                    songUrl?.let { put("large_url", it) }
                })
            }
            putJsonArray("buttons") {
                // Buttons are plain URLs and cannot open Koda itself, so the
                // label says where the tap really goes.
                songUrl?.let { url ->
                    add(buildJsonObject {
                        put("label", "Listen on YouTube Music")
                        put("url", url)
                    })
                }
                add(buildJsonObject {
                    put("label", "Get Koda")
                    put("url", RELEASES_URL)
                })
            }
        }
    }

    /** `{"cmd":"SET_ACTIVITY", ...}` - the frame the transport hands to Discord. */
    fun setActivityFrame(activity: JsonObject, pid: Int, nonce: String): String =
        activityFrame(activity, pid, nonce)

    /**
     * The clear frame. Discord treats a null activity as "hide the card", so
     * the same command does both jobs - there is no separate teardown.
     */
    fun clearActivityFrame(pid: Int, nonce: String): String =
        activityFrame(null, pid, nonce)

    private fun activityFrame(activity: JsonObject?, pid: Int, nonce: String): String =
        buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("args", buildJsonObject {
                put("pid", pid)
                put("activity", activity ?: JsonNull)
            })
            put("nonce", nonce)
        }.toString()
}
