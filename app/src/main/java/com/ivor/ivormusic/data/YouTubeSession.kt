package com.ivor.ivormusic.data

import okhttp3.Cookie

/**
 * The login a request is being sent for.
 *
 * A session is a login incarnation, not a frozen cookie jar. Google rotates the
 * session cookies while the app is using them, and a rotation keeps the same
 * [generation] - only signing in or out bumps it. That distinction is the whole
 * point of this type: comparing cookie strings to ask "is this still the
 * session I started with" answers no every time Google rotates a cookie, which
 * is how video history reporting used to stop partway through a video on a
 * perfectly valid account.
 *
 * [cookies] is a snapshot and goes stale on its own. Re-read it through
 * [SessionManager.currentSession] before each use rather than replaying a held
 * copy.
 */
internal data class YouTubeSession(val profileId: String, val generation: Long, val cookies: String) {
    /** Never log a cookie string: KLog's release ring buffer ends up in bug reports. */
    override fun toString(): String = "YouTubeSession(profileId=$profileId, generation=$generation)"
}

/** Hosts whose Set-Cookie headers belong to the account session. */
internal fun isSessionCookieHost(host: String): Boolean =
    host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "google.com" || host.endsWith(".google.com")

/**
 * Refreshes only. A blank or already-expired cookie is a deletion, and applying
 * one of those is how a single unlucky response could sign the user out.
 */
internal fun sessionCookieUpdates(cookies: List<Cookie>, nowMs: Long): Map<String, String> =
    cookies.asSequence()
        .filter { it.value.isNotBlank() && it.expiresAt > nowMs }
        .associate { it.name to it.value }

/**
 * Fold [updates] into [current], skipping any cookie whose value has changed
 * since this request went out carrying [sent].
 *
 * Several authenticated calls are usually in flight together and come back in
 * an arbitrary order. Without this, the last response to land wins and an older
 * one quietly rolls a newer rotation back to the value it replaced.
 */
internal fun mergeSessionCookieUpdates(sent: String, current: String, updates: Map<String, String>): String =
    YouTubeAuthUtils.mergeCookies(current, updates.filterKeys { name ->
        YouTubeAuthUtils.getCookieValue(sent, name) == YouTubeAuthUtils.getCookieValue(current, name)
    })
