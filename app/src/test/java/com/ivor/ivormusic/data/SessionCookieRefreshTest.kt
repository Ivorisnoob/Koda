package com.ivor.ivormusic.data

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cookie-refresh rules behind [SessionRefreshInterceptor]. The storage side
 * (generations, profile ownership) lives in ProfileManager on
 * EncryptedSharedPreferences and is not reachable from a JVM test; these are the
 * decisions it depends on.
 */
class SessionCookieRefreshTest {

    private val url = "https://www.youtube.com/youtubei/v1/browse".toHttpUrl()

    private fun cookie(name: String, value: String, expiresAt: Long): Cookie =
        Cookie.Builder().name(name).value(value).domain("youtube.com").expiresAt(expiresAt).build()

    @Test
    fun `a rotated cookie is taken`() {
        val updates = sessionCookieUpdates(
            listOf(cookie("__Secure-1PSIDTS", "fresh", NOW + 86_400_000L)),
            NOW,
        )
        assertEquals(mapOf("__Secure-1PSIDTS" to "fresh"), updates)
    }

    @Test
    fun `a deletion is not a refresh`() {
        // Google clears a cookie by sending it back empty or already expired.
        // Applying either is how one unlucky response could sign the user out.
        val updates = sessionCookieUpdates(
            listOf(
                cookie("SID", "", NOW + 86_400_000L),
                cookie("HSID", "gone", NOW - 1L),
                cookie("SAPISID", "kept", NOW + 86_400_000L),
            ),
            NOW,
        )
        assertEquals(mapOf("SAPISID" to "kept"), updates)
    }

    @Test
    fun `set-cookie is read off the response that carried it`() {
        val headers = okhttp3.Headers.Builder()
            .add("Set-Cookie", "__Secure-3PSIDTS=rotated; Domain=.youtube.com; Path=/; Max-Age=86400; Secure")
            .add("Set-Cookie", "YSC=throwaway; Domain=.youtube.com; Path=/; Secure")
            .build()
        val updates = sessionCookieUpdates(Cookie.parseAll(url, headers), NOW)
        assertEquals("rotated", updates["__Secure-3PSIDTS"])
        // Every response also sets throwaway cookies. They survive the parse
        // and are dropped on the way into storage, which is where the list of
        // names that authenticate anything lives.
        val stored = "SID=a; __Secure-3PSIDTS=stale"
        assertEquals(
            "SID=a; __Secure-3PSIDTS=rotated",
            mergeSessionCookieUpdates(stored, stored, updates),
        )
    }

    @Test
    fun `a rotation lands on the stored session`() {
        val sent = "SID=a; SAPISID=b; __Secure-1PSIDTS=old"
        val merged = mergeSessionCookieUpdates(sent, sent, mapOf("__Secure-1PSIDTS" to "new"))
        assertEquals("SID=a; SAPISID=b; __Secure-1PSIDTS=new", merged)
    }

    @Test
    fun `an older response does not roll back a newer rotation`() {
        // Two calls go out together carrying `old`. The second response lands
        // first and stores `newer`; the first must not put `older` back.
        val sent = "SID=a; __Secure-1PSIDTS=old"
        val current = "SID=a; __Secure-1PSIDTS=newer"
        val merged = mergeSessionCookieUpdates(sent, current, mapOf("__Secure-1PSIDTS" to "older"))
        assertEquals(current, merged)
    }

    @Test
    fun `an untouched cookie in the same response still applies`() {
        val sent = "SID=a; __Secure-1PSIDTS=old; SIDCC=one"
        val current = "SID=a; __Secure-1PSIDTS=newer; SIDCC=one"
        val merged = mergeSessionCookieUpdates(
            sent,
            current,
            mapOf("__Secure-1PSIDTS" to "older", "SIDCC" to "two"),
        )
        assertEquals("SID=a; __Secure-1PSIDTS=newer; SIDCC=two", merged)
    }

    @Test
    fun `only account hosts refresh the session`() {
        assertTrue(isSessionCookieHost("www.youtube.com"))
        assertTrue(isSessionCookieHost("music.youtube.com"))
        assertTrue(isSessionCookieHost("youtube.com"))
        assertTrue(isSessionCookieHost("accounts.google.com"))
        // Playback CDNs hand out cookies that have nothing to do with the
        // account, and a suffix match would trust a lookalike domain.
        assertFalse(isSessionCookieHost("rr3---sn-4g5e6nez.googlevideo.com"))
        assertFalse(isSessionCookieHost("notyoutube.com"))
        assertFalse(isSessionCookieHost("youtube.com.evil.test"))
    }

    private companion object {
        const val NOW = 1_757_000_000_000L
    }
}
