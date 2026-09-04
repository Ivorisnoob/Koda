package com.ivor.ivormusic.data.scrobble

import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the ListenBrainz (and compatible GNU FM / Libre.fm / Maloja) REST API.
 *
 * Supported operations:
 * - Token validation (`GET /1/validate-token`)
 * - Now Playing (`POST /1/submit-listens`, listen_type = "playing_now")
 * - Single scrobble (`POST /1/submit-listens`, listen_type = "single")
 * - Batch import (`POST /1/submit-listens`, listen_type = "import")
 */
class ListenBrainzClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "ListenBrainzClient"
        const val DEFAULT_BASE_URL = "https://api.listenbrainz.org/1/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun normalizeBaseUrl(url: String): String {
            val trimmed = url.trim().removeSuffix("/")
            if (trimmed.isBlank()) return DEFAULT_BASE_URL
            val withVersion = if (trimmed.endsWith("/1")) trimmed else "$trimmed/1"
            return "$withVersion/"
        }
    }

    /**
     * Validates a user token and returns the associated username if valid.
     */
    suspend fun validateToken(token: String, baseUrl: String = DEFAULT_BASE_URL): Result<String> =
        withContext(Dispatchers.IO) {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Token is empty"))
            }

            val endpoint = "${normalizeBaseUrl(baseUrl)}validate-token"
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Token $cleanToken")
                .get()
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext Result.failure(Exception("Empty response from server"))
                    }

                    val json = JSONObject(body)
                    val isValid = json.optBoolean("valid", false)
                    val userName = json.optString("user_name").trim()

                    if (response.isSuccessful && isValid && userName.isNotBlank()) {
                        Result.success(userName)
                    } else {
                        val message = json.optString("message", "Token is invalid")
                        Result.failure(Exception(message))
                    }
                }
            } catch (e: Exception) {
                KLog.e(TAG, "Failed to validate ListenBrainz token", e)
                Result.failure(e)
            }
        }

    /**
     * Notifies ListenBrainz that the track is currently playing.
     */
    suspend fun updateNowPlaying(
        track: ScrobbleTrack,
        token: String,
        baseUrl: String = DEFAULT_BASE_URL
    ): ScrobbleResult = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return@withContext ScrobbleResult.AuthRequired

        val trackMetadata = JSONObject().apply {
            put("artist_name", track.artist)
            put("track_name", track.title)
            if (!track.album.isNullOrBlank()) {
                put("release_name", track.album)
            }
            if (track.durationSeconds > 0) {
                val additionalInfo = JSONObject().apply {
                    put("duration", track.durationSeconds)
                }
                put("additional_info", additionalInfo)
            }
        }

        val payloadItem = JSONObject().apply {
            put("track_metadata", trackMetadata)
        }

        val requestBody = JSONObject().apply {
            put("listen_type", "playing_now")
            put("payload", JSONArray().apply { put(payloadItem) })
        }

        sendSubmitListens(requestBody, cleanToken, baseUrl)
    }

    /**
     * Submits a single finished track play.
     */
    suspend fun scrobbleSingle(
        track: ScrobbleTrack,
        token: String,
        baseUrl: String = DEFAULT_BASE_URL
    ): ScrobbleResult = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return@withContext ScrobbleResult.AuthRequired

        val trackMetadata = JSONObject().apply {
            put("artist_name", track.artist)
            put("track_name", track.title)
            if (!track.album.isNullOrBlank()) {
                put("release_name", track.album)
            }
            if (track.durationSeconds > 0) {
                val additionalInfo = JSONObject().apply {
                    put("duration", track.durationSeconds)
                }
                put("additional_info", additionalInfo)
            }
        }

        val payloadItem = JSONObject().apply {
            put("listened_at", track.timestampSeconds)
            put("track_metadata", trackMetadata)
        }

        val requestBody = JSONObject().apply {
            put("listen_type", "single")
            put("payload", JSONArray().apply { put(payloadItem) })
        }

        sendSubmitListens(requestBody, cleanToken, baseUrl)
    }

    /**
     * Submits a batch of tracks (up to 50) using the "import" listen type.
     */
    suspend fun scrobbleBatch(
        tracks: List<ScrobbleTrack>,
        token: String,
        baseUrl: String = DEFAULT_BASE_URL
    ): ScrobbleResult = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext ScrobbleResult.Success
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return@withContext ScrobbleResult.AuthRequired

        val batch = tracks.take(50)
        val payloadArray = JSONArray()

        for (track in batch) {
            val trackMetadata = JSONObject().apply {
                put("artist_name", track.artist)
                put("track_name", track.title)
                if (!track.album.isNullOrBlank()) {
                    put("release_name", track.album)
                }
                if (track.durationSeconds > 0) {
                    val additionalInfo = JSONObject().apply {
                        put("duration", track.durationSeconds)
                    }
                    put("additional_info", additionalInfo)
                }
            }

            val payloadItem = JSONObject().apply {
                put("listened_at", track.timestampSeconds)
                put("track_metadata", trackMetadata)
            }
            payloadArray.put(payloadItem)
        }

        val requestBody = JSONObject().apply {
            put("listen_type", "import")
            put("payload", payloadArray)
        }

        sendSubmitListens(requestBody, cleanToken, baseUrl)
    }

    private fun sendSubmitListens(
        bodyJson: JSONObject,
        token: String,
        baseUrl: String
    ): ScrobbleResult {
        val endpoint = "${normalizeBaseUrl(baseUrl)}submit-listens"
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Token $token")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> ScrobbleResult.Success
                    401 -> ScrobbleResult.AuthRequired
                    429 -> ScrobbleResult.Failure("ListenBrainz rate limit reached", canRetry = true)
                    in 500..599 -> ScrobbleResult.Failure("ListenBrainz server error (${response.code})", canRetry = true)
                    else -> {
                        val errorMsg = try {
                            JSONObject(body).optString("error", "Error code ${response.code}")
                        } catch (_: Exception) {
                            "Error code ${response.code}"
                        }
                        KLog.w(TAG, "ListenBrainz submission failed: ${response.code} $errorMsg")
                        ScrobbleResult.Failure(errorMsg, canRetry = false)
                    }
                }
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Network error submitting to ListenBrainz: ${e.message}")
            ScrobbleResult.Failure(e.message ?: "Network error", canRetry = true)
        }
    }
}
