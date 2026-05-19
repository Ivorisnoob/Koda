package com.ivor.ivormusic.data

/**
 * Platform-independent session/cookie management.
 * Android: backed by EncryptedSharedPreferences (AES-256).
 * iOS: backed by Keychain.
 */
interface SessionManager {
    fun saveCookies(cookies: String)
    fun getCookies(): String?
    fun clearSession()
    fun isLoggedIn(): Boolean
    fun getVisitorData(): String?
    fun saveVisitorData(data: String)
    fun saveUserAvatar(url: String)
    fun getUserAvatar(): String?
    fun saveUserName(name: String)
    fun getUserName(): String?
}
