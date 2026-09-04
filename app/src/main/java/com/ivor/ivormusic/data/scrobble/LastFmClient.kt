package com.ivor.ivormusic.data.scrobble

import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Client for the Last.fm Audioscrobbler 2.0 API.
 *
 * Implements the User API model where users supply their own API Key & Shared Secret.
 *
 * Protocol notes:
 * - Requests are sent as HTTP POST with form-urlencoded bodies to `https://ws.audioscrobbler.com/2.0/`.
 * - All authenticated requests require an `api_sig` calculated as the MD5 hex digest
 *   of alphabetically sorted parameter name+value pairs (excluding `format` and `callback`)
 *   followed by the API shared secret.
 * - JSON responses are requested by including `format=json` in the request body (never in the signature).
 */
class LastFmClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "LastFmClient"
        private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"

        /**
         * Computes the Last.fm method signature (`api_sig`).
         *
         * 1. Sort all parameters alphabetically by ASCII key name (excluding `format` and `callback`).
         * 2. Concatenate `<key><value>` pairs without delimiters.
         * 3. Append the application shared secret.
         * 4. Return lowercase 32-character MD5 hex digest.
         */
        fun generateSignature(params: Map<String, String>, apiSecret: String): String {
            val sortedKeys = params.keys
                .filter { it != "format" && it != "callback" && it != "api_sig" }
                .sorted()
            val buffer = StringBuilder()
            for (key in sortedKeys) {
                buffer.append(key).append(params[key] ?: "")
            }
            buffer.append(apiSecret)

            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(buffer.toString().toByteArray(Charsets.UTF_8))
            val hex = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val hexChar = Integer.toHexString(b.toInt() and 0xFF)
                if (hexChar.length == 1) hex.append('0')
                hex.append(hexChar)
            }
            return hex.toString()
        }

        fun getAuthorizationUrl(apiKey: String, token: String): String {
            return "https://www.last.fm/api/auth/?api_key=$apiKey&token=$token"
        }
    }

    /**
     * Obtains an unauthorized request token from Last.fm to begin web authorization.
     */
    suspend fun fetchRequestToken(apiKey: String, apiSecret: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key and Secret are required"))
        }

        val params = mutableMapOf(
            "method" to "auth.getToken",
            "api_key" to apiKey
        )
        params["api_sig"] = generateSignature(params, apiSecret)
        params["format"] = "json"

        try {
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }

            val request = Request.Builder()
                .url(BASE_URL)
                .post(formBuilder.build())
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext Result.failure(Exception("Empty response from Last.fm"))
                }
                val json = JSONObject(body)
                if (json.has("error")) {
                    val code = json.optInt("error")
                    val message = json.optString("message", "Unknown error")
                    KLog.w(TAG, "auth.getToken failed: $code - $message")
                    return@withContext Result.failure(Exception("Last.fm error ($code): $message"))
                }
                val token = json.optString("token")
                if (token.isNotBlank()) {
                    Result.success(token)
                } else {
                    Result.failure(Exception("Token not found in response"))
                }
            }
        } catch (e: Exception) {
            KLog.e(TAG, "Network error fetching Last.fm request token", e)
            Result.failure(e)
        }
    }

    /**
     * Exchanges an authorized token for a permanent session key (`sk`) and username.
     */
    suspend fun fetchSession(token: String, apiKey: String, apiSecret: String): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            if (token.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Token, API Key, and Secret are required"))
            }

            val params = mutableMapOf(
                "method" to "auth.getSession",
                "api_key" to apiKey,
                "token" to token
            )
            params["api_sig"] = generateSignature(params, apiSecret)
            params["format"] = "json"

            try {
                val formBuilder = FormBody.Builder()
                params.forEach { (k, v) -> formBuilder.add(k, v) }

                val request = Request.Builder()
                    .url(BASE_URL)
                    .post(formBuilder.build())
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext Result.failure(Exception("Empty response from Last.fm"))
                    }
                    val json = JSONObject(body)
                    if (json.has("error")) {
                        val code = json.optInt("error")
                        val message = json.optString("message", "Unknown error")
                        KLog.w(TAG, "auth.getSession failed: $code - $message")
                        return@withContext Result.failure(Exception("Last.fm error ($code): $message"))
                    }

                    val sessionObj = json.optJSONObject("session")
                    val sessionKey = sessionObj?.optString("key").orEmpty()
                    val username = sessionObj?.optString("name").orEmpty()

                    if (sessionKey.isNotBlank() && username.isNotBlank()) {
                        Result.success(Pair(sessionKey, username))
                    } else {
                        Result.failure(Exception("Incomplete session data in response"))
                    }
                }
            } catch (e: Exception) {
                KLog.e(TAG, "Network error fetching Last.fm session", e)
                Result.failure(e)
            }
        }

    /**
     * Direct mobile credential authentication using username and password.
     */
    suspend fun fetchMobileSession(
        username: String,
        password: String,
        apiKey: String,
        apiSecret: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("All credentials are required"))
        }

        val params = mutableMapOf(
            "method" to "auth.getMobileSession",
            "username" to username,
            "password" to password,
            "api_key" to apiKey
        )
        params["api_sig"] = generateSignature(params, apiSecret)
        params["format"] = "json"

        try {
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }

            val request = Request.Builder()
                .url(BASE_URL)
                .post(formBuilder.build())
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext Result.failure(Exception("Empty response from Last.fm"))
                }
                val json = JSONObject(body)
                if (json.has("error")) {
                    val code = json.optInt("error")
                    val message = json.optString("message", "Authentication failed")
                    KLog.w(TAG, "auth.getMobileSession failed: $code - $message")
                    return@withContext Result.failure(Exception("Last.fm error ($code): $message"))
                }

                val sessionObj = json.optJSONObject("session")
                val sessionKey = sessionObj?.optString("key").orEmpty()
                val retrievedUser = sessionObj?.optString("name").orEmpty().ifBlank { username }

                if (sessionKey.isNotBlank()) {
                    Result.success(Pair(sessionKey, retrievedUser))
                } else {
                    Result.failure(Exception("Session key missing from response"))
                }
            }
        } catch (e: Exception) {
            KLog.e(TAG, "Network error in Last.fm mobile auth", e)
            Result.failure(e)
        }
    }

    /**
     * Notifies Last.fm that the user has started listening to [track].
     */
    suspend fun updateNowPlaying(
        track: ScrobbleTrack,
        sessionKey: String,
        apiKey: String,
        apiSecret: String
    ): ScrobbleResult = withContext(Dispatchers.IO) {
        if (sessionKey.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext ScrobbleResult.AuthRequired
        }

        val params = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to track.artist,
            "track" to track.title,
            "api_key" to apiKey,
            "sk" to sessionKey
        )
        if (!track.album.isNullOrBlank()) {
            params["album"] = track.album
        }
        if (track.durationSeconds > 0) {
            params["duration"] = track.durationSeconds.toString()
        }

        params["api_sig"] = generateSignature(params, apiSecret)
        params["format"] = "json"

        try {
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }

            val request = Request.Builder()
                .url(BASE_URL)
                .post(formBuilder.build())
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext ScrobbleResult.Failure("Empty response", canRetry = true)
                }
                val json = JSONObject(body)
                if (json.has("error")) {
                    val code = json.optInt("error")
                    val message = json.optString("message", "Error")
                    KLog.w(TAG, "updateNowPlaying error $code: $message")
                    return@withContext when (code) {
                        9, 4, 14 -> ScrobbleResult.AuthRequired
                        16, 29 -> ScrobbleResult.Failure(message, canRetry = true)
                        else -> ScrobbleResult.Failure(message, canRetry = false)
                    }
                }
                ScrobbleResult.Success
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Network error during Last.fm updateNowPlaying: ${e.message}")
            ScrobbleResult.Failure(e.message ?: "Network error", canRetry = true)
        }
    }

    /**
     * Submits one or more tracks (up to 50) as permanent scrobbles to Last.fm.
     */
    suspend fun scrobbleBatch(
        tracks: List<ScrobbleTrack>,
        sessionKey: String,
        apiKey: String,
        apiSecret: String
    ): ScrobbleResult = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext ScrobbleResult.Success
        if (sessionKey.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            return@withContext ScrobbleResult.AuthRequired
        }

        // Last.fm supports up to 50 scrobbles per call
        val batch = tracks.take(50)
        val params = mutableMapOf(
            "method" to "track.scrobble",
            "api_key" to apiKey,
            "sk" to sessionKey
        )

        batch.forEachIndexed { index, track ->
            params["artist[$index]"] = track.artist
            params["track[$index]"] = track.title
            params["timestamp[$index]"] = track.timestampSeconds.toString()
            if (!track.album.isNullOrBlank()) {
                params["album[$index]"] = track.album
            }
            if (track.durationSeconds > 0) {
                params["duration[$index]"] = track.durationSeconds.toString()
            }
        }

        params["api_sig"] = generateSignature(params, apiSecret)
        params["format"] = "json"

        try {
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }

            val request = Request.Builder()
                .url(BASE_URL)
                .post(formBuilder.build())
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext ScrobbleResult.Failure("Empty response", canRetry = true)
                }
                val json = JSONObject(body)
                if (json.has("error")) {
                    val code = json.optInt("error")
                    val message = json.optString("message", "Error")
                    KLog.w(TAG, "track.scrobble error $code: $message")
                    return@withContext when (code) {
                        9, 4, 14 -> ScrobbleResult.AuthRequired
                        16, 29 -> ScrobbleResult.Failure(message, canRetry = true)
                        else -> ScrobbleResult.Failure(message, canRetry = false)
                    }
                }
                ScrobbleResult.Success
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Network error during Last.fm scrobble: ${e.message}")
            ScrobbleResult.Failure(e.message ?: "Network error", canRetry = true)
        }
    }
}
