package com.ivor.ivormusic.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages session cookies for YouTube Music authentication.
 * Uses EncryptedSharedPreferences for secure storage.
 */
class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = try {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Keystore corruption - delete corrupted prefs and recreate
        android.util.Log.e("SessionManager", "EncryptedSharedPreferences corrupted, resetting", e)
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        // Also delete the backing file
        val prefsFile = java.io.File(context.filesDir.parent, "shared_prefs/$PREFS_FILE_NAME.xml")
        prefsFile.delete()
        // Retry creation
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveUserAvatar(url: String) {
        prefs.edit().putString(KEY_USER_AVATAR, url).apply()
    }

    fun getUserAvatar(): String? {
        return prefs.getString(KEY_USER_AVATAR, null)
    }

    /**
     * Save session cookies obtained from WebView.
     *
     * Also the refresh path for [SessionCookieJar], so it does not touch the
     * expired flag - only a deliberate sign-in clears that. Use [startSession]
     * when the user has just logged in.
     */
    fun saveCookies(cookies: String) {
        prefs.edit().putString(KEY_COOKIES, cookies).apply()
    }

    /**
     * Begin a session the user has just signed into: store the cookies and
     * drop any "YouTube rejected us" verdict left over from the dead one, so
     * the account screens come back without waiting for a response to prove it.
     */
    fun startSession(cookies: String) {
        saveCookies(cookies)
        setSessionExpired(false)
    }

    /**
     * Record that YouTube answered an authenticated request as anonymous, or
     * that it accepted one. Cookies are left alone either way - they are the
     * only thing a later refresh has to work with, and clearing them on a
     * single bad response would sign people out over a hiccup.
     */
    fun setSessionExpired(expired: Boolean) {
        if (_sessionExpired.value != expired) {
            if (expired) android.util.Log.w("SessionManager", "YouTube rejected the session as signed out")
            _sessionExpired.value = expired
        }
    }

    /**
     * Get stored session cookies.
     */
    fun getCookies(): String? {
        return prefs.getString(KEY_COOKIES, null)
    }

    /**
     * Clear session data (Logout).
     */
    fun clearSession() {
        prefs.edit().clear().apply()
        _sessionExpired.value = false
    }

    /**
     * Check if user is logged in.
     *
     * Deliberately still just "cookies exist", so requests keep going out and
     * a rotation can revive a session that looked dead. [sessionExpired] is
     * what the UI should read before showing account-only content.
     */
    fun isLoggedIn(): Boolean {
        return !getCookies().isNullOrBlank()
    }
    
    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }
    
    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    companion object {
        /**
         * True once YouTube has answered an authenticated call as anonymous.
         *
         * Companion-scoped on purpose: with no DI every ViewModel news up its
         * own SessionManager, so an instance flow would never reach the screens
         * that need to react - the same reason visitorData is cached up here.
         */
        private val _sessionExpired = MutableStateFlow(false)
        val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

        private const val PREFS_FILE_NAME = "yt_music_session"
        private const val KEY_COOKIES = "session_cookies"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR = "user_avatar"
    }
}
