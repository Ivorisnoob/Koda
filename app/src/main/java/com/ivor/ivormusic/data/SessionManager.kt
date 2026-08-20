package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The active profile's YouTube session.
 *
 * **This class deliberately kept its entire public API when profiles arrived.**
 * Twenty-seven call sites in [YouTubeRepository] alone ask it for cookies or
 * for [isLoggedIn], and NewPipe's downloader holds one instance for the life of
 * the process. All of them resolve the session fresh on every call, so
 * redirecting what "the session" means - which is all [ProfileManager.setActive]
 * does - moves the whole app onto another account without touching any of them.
 * That is why switching accounts needs no re-authentication and works offline.
 *
 * A [ProfileKind.LOCAL] profile simply has no cookies, so [isLoggedIn] is false
 * and every signed-out path in the app runs exactly as it always has.
 */
class SessionManager(context: Context) {

    private val profileManager = ProfileManager(context)

    fun saveUserAvatar(url: String) {
        profileManager.updateIdentity(activeId(), avatarUrl = url)
    }

    fun getUserAvatar(): String? = profileManager.active().avatarUrl

    /**
     * Save session cookies obtained from WebView.
     *
     * Also the refresh path for [SessionCookieJar], so it does not touch the
     * expired flag - only a deliberate sign-in clears that. Use [startSession]
     * when the user has just logged in.
     *
     * A rotation arriving while a local profile is active is dropped: there is
     * no account to refresh, and writing it would quietly turn a device-only
     * profile into a signed-in one.
     */
    fun saveCookies(cookies: String) {
        val active = profileManager.active()
        if (active.isLocal) return
        profileManager.saveCookiesFor(active.id, cookies)
    }

    /**
     * Begin a session the user has just signed into.
     *
     * When a local profile is active this promotes the sign-in into a new
     * YouTube profile and switches to it, so the existing login flow keeps
     * working unchanged and simply produces a profile as a side effect.
     */
    fun startSession(cookies: String) {
        val active = profileManager.active()
        if (active.isLocal) {
            val profile = profileManager.addYouTubeProfile(cookies)
            profileManager.setActive(profile.id)
        } else {
            profileManager.saveCookiesFor(active.id, cookies)
        }
        setSessionExpired(false)
    }

    /**
     * Record that YouTube answered an authenticated request as anonymous, or
     * that it accepted one. Cookies are left alone either way - they are the
     * only thing a later refresh has to work with, and clearing them on a
     * single bad response would sign people out over a hiccup.
     *
     * The verdict is stored against the profile it came from, so with several
     * accounts in the roster the badge lands on the right row.
     */
    fun setSessionExpired(expired: Boolean) {
        val active = profileManager.active()
        if (active.isLocal) return
        profileManager.setExpired(active.id, expired)
        if (_sessionExpired.value != expired) {
            if (expired) android.util.Log.w(TAG, "YouTube rejected the session as signed out")
            _sessionExpired.value = expired
        }
    }

    /** Get the active profile's stored session cookies. */
    fun getCookies(): String? {
        val active = profileManager.active()
        if (active.isLocal) return null
        return profileManager.cookiesFor(active.id)
    }

    /**
     * Sign the active profile out.
     *
     * With a roster this means removing that profile and falling back to
     * another, rather than wiping the store: signing out of one account must
     * not take the others with it. When it is the only profile left, it is
     * emptied into a device-only profile instead, so the app always has an
     * identity to run as.
     */
    fun clearSession() {
        val active = profileManager.active()
        if (!profileManager.remove(active.id)) {
            profileManager.replaceWithFreshLocal(active.id)
        }
        _sessionExpired.value = false
    }

    /**
     * Check if user is logged in.
     *
     * Deliberately still just "cookies exist", so requests keep going out and
     * a rotation can revive a session that looked dead. [sessionExpired] is
     * what the UI should read before showing account-only content.
     */
    fun isLoggedIn(): Boolean = !getCookies().isNullOrBlank()

    fun saveUserName(name: String) {
        profileManager.updateIdentity(activeId(), name = name)
    }

    /**
     * Record YouTube's own identifier for the account behind the active
     * profile.
     *
     * Nothing wrote this until backups needed it, which meant
     * [ProfileManager.addYouTubeProfile]'s dedupe by [Profile.datasyncId] had
     * never once fired: signing back into an account already in the roster
     * added a second identical row instead of repairing the first, and a
     * restored backup could not tell that an account on the file was the one
     * this device is already signed into.
     */
    fun saveDatasyncId(datasyncId: String) {
        if (datasyncId.isBlank()) return
        profileManager.updateIdentity(activeId(), datasyncId = datasyncId)
    }

    fun getUserName(): String? = profileManager.active().name.takeIf { !profileManager.active().isLocal }

    private fun activeId(): String = profileManager.active().id

    /** Re-read the active profile's expired verdict, after a switch. */
    fun refreshExpiredFromProfile() {
        _sessionExpired.value = profileManager.active().expired
    }

    companion object {
        private const val TAG = "SessionManager"

        /**
         * True once YouTube has answered an authenticated call as anonymous.
         *
         * Companion-scoped on purpose: with no DI every ViewModel news up its
         * own SessionManager, so an instance flow would never reach the screens
         * that need to react - the same reason visitorData is cached up here.
         * It mirrors the *active* profile's flag; the durable per-profile value
         * lives on [Profile.expired].
         */
        private val _sessionExpired = MutableStateFlow(false)
        val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()
    }
}
