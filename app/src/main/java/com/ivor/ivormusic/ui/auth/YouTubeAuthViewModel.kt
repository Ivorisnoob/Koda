package com.ivor.ivormusic.ui.auth

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.YouTubeAuthUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for handling YouTube Music authentication logic.
 * Primarily used to intercept cookies from a WebView.
 */
class YouTubeAuthViewModel(private val sessionManager: SessionManager) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(sessionManager.isLoggedIn())
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _authUrl = MutableStateFlow("https://music.youtube.com/login")
    val authUrl = _authUrl.asStateFlow()

    /**
     * Intercept cookies from WebView and check for login success.
     */
    fun onUrlChanged(url: String) {
        val cookies = CookieManager.getInstance().getCookie(url)
        if (cookies != null && isLoginSuccessful(cookies)) {
            sessionManager.startSession(cookies)
            CookieManager.getInstance().flush()
            _isLoggedIn.value = true
        }
    }

    /**
     * Helper to determine if the captured cookies indicate a successful login.
     *
     * Exact cookie names: `contains("SID=")` is also satisfied by SAPISID=,
     * APISID= and __Secure-3PSID=, so a substring check accepted jars carrying
     * no SID session and produced a login that authenticated nothing.
     */
    private fun isLoginSuccessful(cookies: String): Boolean {
        return YouTubeAuthUtils.missingRequiredCookies(cookies).isEmpty() &&
            YouTubeAuthUtils.getCookieValue(cookies, "HSID") != null &&
            YouTubeAuthUtils.getCookieValue(cookies, "SSID") != null
    }

    /**
     * Logout and clear session.
     */
    fun logout() {
        sessionManager.clearSession()
        CookieManager.getInstance().removeAllCookies(null)
        _isLoggedIn.value = false
    }

    /**
     * Save cookies directly and update login state.
     */
    fun saveCookies(cookies: String) {
        sessionManager.startSession(cookies)
        _isLoggedIn.value = true
    }
}
