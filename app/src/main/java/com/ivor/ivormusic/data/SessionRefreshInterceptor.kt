package com.ivor.ivormusic.data

import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Keeps the stored YouTube session in step with the cookies Google re-issues
 * while the app is using it.
 *
 * The session used to be a snapshot taken once in the login WebView and then
 * replayed forever. Google rotates the session cookies (notably the
 * `__Secure-1PSIDTS` / `__Secure-3PSIDTS` pair) as requests go out, so the
 * frozen copy went stale on its own after a while: YouTube kept answering
 * `logged_in: 0` with an empty subscriptions feed and no account name, while
 * the app still believed it was signed in because a cookie string existed. The
 * only way out was signing out and back in, repeatedly.
 *
 * This is a *network* interceptor rather than the `CookieJar` it used to be,
 * for two reasons. A jar's callbacks say nothing about which profile sent the
 * request, so a response landing after an account switch folded one account's
 * refreshed cookies into the other's row. And the refresh has to be read off
 * the network response: Google rotates the `*PSIDTS` pair on the 302s inside a
 * redirect chain, and an application interceptor only ever sees the headers of
 * the final hop.
 *
 * Nothing is ever *sent* from here. Every authenticated call in
 * [YouTubeRepository] builds its own `Cookie` header alongside the matching
 * per-origin SAPISIDHASH and tags the request with the session it used; a jar
 * that handed cookies back would have OkHttp's bridge interceptor replace the
 * header the caller carefully assembled.
 */
internal class SessionRefreshInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        // An untagged request was not sent for a login (public reads, streams),
        // and googlevideo and the other CDN hosts hand out cookies that have
        // nothing to do with the account session either way.
        val session = request.tag(YouTubeSession::class.java) ?: return response
        if (!isSessionCookieHost(request.url.host)) return response
        val updates = sessionCookieUpdates(
            Cookie.parseAll(request.url, response.headers),
            System.currentTimeMillis(),
        )
        if (updates.isNotEmpty()) sessionManager.mergeResponseCookies(session, updates)
        return response
    }
}
