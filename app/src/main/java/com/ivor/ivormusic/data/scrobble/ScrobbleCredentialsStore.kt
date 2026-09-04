package com.ivor.ivormusic.data.scrobble

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore

/**
 * Secure storage for sensitive scrobble tokens and API credentials.
 *
 * Uses [EncryptedSharedPreferences] backed by the Android KeyStore (`AES256_GCM`).
 * Plaintext credentials and session keys are never stored in unencrypted preferences,
 * and this file (`scrobble_secure.xml`) is excluded from backups.
 */
class ScrobbleCredentialsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = getEncryptedPrefs(appContext)

    private val _lastFmUsername = MutableStateFlow(getLastFmUsername())
    val lastFmUsername: StateFlow<String?> = _lastFmUsername.asStateFlow()

    private val _listenBrainzUsername = MutableStateFlow(getListenBrainzUsername())
    val listenBrainzUsername: StateFlow<String?> = _listenBrainzUsername.asStateFlow()

    companion object {
        private const val TAG = "ScrobbleCredsStore"
        const val PREFS_FILE_NAME = "scrobble_secure"

        private const val KEY_LASTFM_API_KEY = "lastfm_api_key"
        private const val KEY_LASTFM_API_SECRET = "lastfm_api_secret"
        private const val KEY_LASTFM_SESSION_KEY = "lastfm_session_key"
        private const val KEY_LASTFM_USERNAME = "lastfm_username"

        private const val KEY_LISTENBRAINZ_TOKEN = "listenbrainz_token"
        private const val KEY_LISTENBRAINZ_USERNAME = "listenbrainz_username"

        private val LOCK = Any()
        @Volatile
        private var instance: SharedPreferences? = null

        private fun buildPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        private fun getEncryptedPrefs(context: Context): SharedPreferences {
            instance?.let { return it }
            return synchronized(LOCK) {
                instance ?: run {
                    val created = try {
                        buildPrefs(context)
                    } catch (e: Exception) {
                        KLog.e(TAG, "EncryptedSharedPreferences corrupted, resetting scrobble credentials", e)
                        try {
                            context.deleteSharedPreferences(PREFS_FILE_NAME)
                            runCatching {
                                KeyStore.getInstance("AndroidKeyStore").apply {
                                    load(null)
                                    deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                                }
                            }
                        } catch (resetEx: Exception) {
                            KLog.e(TAG, "Failed to delete corrupted scrobble keystore entry", resetEx)
                        }
                        buildPrefs(context)
                    }
                    instance = created
                    created
                }
            }
        }
    }

    // --- Last.fm Credentials ---

    fun getLastFmApiKey(): String? = prefs.getString(KEY_LASTFM_API_KEY, null)?.takeIf { it.isNotBlank() }
    fun getLastFmApiSecret(): String? = prefs.getString(KEY_LASTFM_API_SECRET, null)?.takeIf { it.isNotBlank() }
    fun getLastFmSessionKey(): String? = prefs.getString(KEY_LASTFM_SESSION_KEY, null)?.takeIf { it.isNotBlank() }
    fun getLastFmUsername(): String? = prefs.getString(KEY_LASTFM_USERNAME, null)?.takeIf { it.isNotBlank() }

    fun hasLastFmApiCredentials(): Boolean {
        return !getLastFmApiKey().isNullOrBlank() && !getLastFmApiSecret().isNullOrBlank()
    }

    fun isLastFmAuthenticated(): Boolean {
        return hasLastFmApiCredentials() && !getLastFmSessionKey().isNullOrBlank()
    }

    fun saveLastFmApiCredentials(apiKey: String, apiSecret: String) {
        prefs.edit()
            .putString(KEY_LASTFM_API_KEY, apiKey.trim())
            .putString(KEY_LASTFM_API_SECRET, apiSecret.trim())
            .apply()
    }

    fun saveLastFmSession(sessionKey: String, username: String) {
        prefs.edit()
            .putString(KEY_LASTFM_SESSION_KEY, sessionKey.trim())
            .putString(KEY_LASTFM_USERNAME, username.trim())
            .apply()
        _lastFmUsername.value = username.trim()
    }

    fun clearLastFmSession() {
        prefs.edit()
            .remove(KEY_LASTFM_SESSION_KEY)
            .remove(KEY_LASTFM_USERNAME)
            .apply()
        _lastFmUsername.value = null
    }

    fun clearLastFmAll() {
        prefs.edit()
            .remove(KEY_LASTFM_API_KEY)
            .remove(KEY_LASTFM_API_SECRET)
            .remove(KEY_LASTFM_SESSION_KEY)
            .remove(KEY_LASTFM_USERNAME)
            .apply()
        _lastFmUsername.value = null
    }

    // --- ListenBrainz Credentials ---

    fun getListenBrainzToken(): String? = prefs.getString(KEY_LISTENBRAINZ_TOKEN, null)?.takeIf { it.isNotBlank() }
    fun getListenBrainzUsername(): String? = prefs.getString(KEY_LISTENBRAINZ_USERNAME, null)?.takeIf { it.isNotBlank() }

    fun isListenBrainzConfigured(): Boolean {
        return !getListenBrainzToken().isNullOrBlank()
    }

    fun saveListenBrainzToken(token: String, username: String) {
        prefs.edit()
            .putString(KEY_LISTENBRAINZ_TOKEN, token.trim())
            .putString(KEY_LISTENBRAINZ_USERNAME, username.trim())
            .apply()
        _listenBrainzUsername.value = username.trim()
    }

    fun clearListenBrainz() {
        prefs.edit()
            .remove(KEY_LISTENBRAINZ_TOKEN)
            .remove(KEY_LISTENBRAINZ_USERNAME)
            .apply()
        _listenBrainzUsername.value = null
    }
}
