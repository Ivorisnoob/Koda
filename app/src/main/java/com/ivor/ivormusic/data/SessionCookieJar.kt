package com.ivor.ivormusic.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Keeps the stored YouTube session in step with the cookies Google re-issues
 * while the app is using it.
 *
 * The session used to be a snapshot taken once in the login WebView and then
 * replayed forever. Google rotates the session cookies (notably the
 * `__Secure-1PSIDTS` / `__Secure-3PSIDTS` pair) as requests go out, so the
 * frozen copy went stale on its own after a while: YouTube kept answering
 * `logged_in: 0` with an empty subscriptions feed and no account name, while
 * the app still believed it was signed in because a cookie string existed.
 * The only way out was signing out and back in, repeatedly.
 *
 * This jar is deliberately write-only. [loadForRequest] returns nothing
 * because every authenticated call in [YouTubeRepository] builds its own
 * `Cookie` header alongside the matching per-origin SAPISIDHASH - OkHttp's
 * bridge interceptor *replaces* that header when a jar hands back cookies, so
 * serving them here would clobber the header the caller carefully assembled.
 */
class SessionCookieJar(private val sessionManager: SessionManager) : CookieJar {

    private val lock = Any()

    override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        // googlevideo and other CDN hosts hand out cookies that have nothing to
        // do with the account session.
        val host = url.host
        if (!host.endsWith("youtube.com") && !host.endsWith("google.com")) return

        val now = System.currentTimeMillis()
        val updates = cookies.asSequence()
            // An expired or emptied cookie is a deletion. Applying those is how
            // a single unlucky response could sign the user out, so they are
            // ignored - refreshes only.
            .filter { it.value.isNotBlank() && it.expiresAt > now }
            .associate { it.name to it.value }
        if (updates.isEmpty()) return

        synchronized(lock) {
            val existing = sessionManager.getCookies()
            if (existing.isNullOrBlank()) return
            val merged = YouTubeAuthUtils.mergeCookies(existing, updates)
            if (merged != existing) sessionManager.saveCookies(merged)
        }
    }
}
