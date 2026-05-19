package com.ivor.ivormusic.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidSessionManager(context: Context) : SessionManager {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = try {
        EncryptedSharedPreferences.create(
            context, PREFS_FILE_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        android.util.Log.e("SessionManager", "EncryptedSharedPreferences corrupted, resetting", e)
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        val prefsFile = java.io.File(context.filesDir.parent, "shared_prefs/$PREFS_FILE_NAME.xml")
        prefsFile.delete()
        EncryptedSharedPreferences.create(
            context, PREFS_FILE_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun saveCookies(cookies: String) = prefs.edit().putString(KEY_COOKIES, cookies).apply()
    override fun getCookies(): String? = prefs.getString(KEY_COOKIES, null)
    override fun clearSession() = prefs.edit().clear().apply()
    override fun isLoggedIn(): Boolean = !getCookies().isNullOrBlank()
    override fun getVisitorData(): String? = prefs.getString(KEY_VISITOR_DATA, null)
    override fun saveVisitorData(data: String) = prefs.edit().putString(KEY_VISITOR_DATA, data).apply()

    override fun saveUserAvatar(url: String) = prefs.edit().putString(KEY_USER_AVATAR, url).apply()
    override fun getUserAvatar(): String? = prefs.getString(KEY_USER_AVATAR, null)
    override fun saveUserName(name: String) = prefs.edit().putString(KEY_USER_NAME, name).apply()
    override fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    companion object {
        private const val PREFS_FILE_NAME = "yt_music_session"
        private const val KEY_COOKIES = "session_cookies"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_VISITOR_DATA = "visitor_data"
    }
}
