package com.ivor.ivormusic.ui.auth

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import com.ivor.ivormusic.data.SessionManager
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
            sessionManager.saveCookies(cookies)
            _isLoggedIn.value = true
        }
    }

    /**
     * Helper to determine if the captured cookies indicate a successful login.
     * Looks for key session cookies like "SID", "HSID", "SSID", or "SAPISID".
     */
    private fun isLoginSuccessful(cookies: String): Boolean {
        // These cookies usually indicate an active session
        return cookies.contains("SID=") && cookies.contains("HSID=") && cookies.contains("SSID=")
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
        sessionManager.saveCookies(cookies)
        _isLoggedIn.value = true
    }
}
