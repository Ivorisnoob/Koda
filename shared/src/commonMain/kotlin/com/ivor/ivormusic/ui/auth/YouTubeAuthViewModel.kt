package com.ivor.ivormusic.ui.auth

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
     * Called when URL changes in the auth WebView.
     * Cookie extraction is handled platform-specifically; here we check if the URL indicates success.
     */
    fun onUrlChanged(url: String) {
        // No-op in commonMain; platform implementations handle cookie extraction via saveCookies()
    }

    /**
     * Logout and clear session.
     */
    fun logout() {
        sessionManager.clearSession()
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
