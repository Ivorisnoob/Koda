package com.ivor.ivormusic.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages session cookies for YouTube Music authentication.
 * Uses EncryptedSharedPreferences for secure storage.
 */
class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "yt_music_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveUserAvatar(url: String) {
        prefs.edit().putString(KEY_USER_AVATAR, url).apply()
    }

    fun getUserAvatar(): String? {
        return prefs.getString(KEY_USER_AVATAR, null)
    }

    /**
     * Save session cookies obtained from WebView.
     */
    fun saveCookies(cookies: String) {
        prefs.edit().putString(KEY_COOKIES, cookies).apply()
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
    }

    /**
     * Check if user is logged in.
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
        private const val KEY_COOKIES = "session_cookies"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR = "user_avatar"
    }
}
