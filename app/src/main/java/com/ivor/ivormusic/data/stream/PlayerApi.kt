package com.ivor.ivormusic.data.stream

import com.ivor.ivormusic.util.KLog
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * What one InnerTube `/player` call said.
 *
 * Four answers that need four different reactions, which a nullable
 * `streamingData` used to fold into one: play it, replace the identity, give
 * up on this video, or try again later.
 */
internal sealed interface PlayerAnswer {
    /** Status OK with streams. [root] is the whole response: captions, loudness and storyboard ride on it. */
    class Playable(val root: JSONObject, val streamingData: JSONObject) : PlayerAnswer

    /**
     * The bot check refused the visitorData the call carried. Two signatures:
     * playability `LOGIN_REQUIRED` ("Sign in to confirm you're not a bot"),
     * and status OK with no `streamingData`, where [root] is the response
     * that still carries the video's captions.
     */
    class IdentityRefused(val reason: String?, val root: JSONObject? = null) : PlayerAnswer

    /** Any other playability status: a verdict on the video, which no other identity changes. */
    class Unplayable(val status: String, val reason: String?) : PlayerAnswer

    /** Nothing to read: an HTTP error, an empty or malformed body, or no connection. */
    class Failed(val httpCode: Int? = null) : PlayerAnswer
}

/**
 * Read a `/player` response into a [PlayerAnswer]. Pure, so the shapes it
 * depends on are pinned by fixtures in `PlayerAnswerTest`.
 */
internal fun readPlayerAnswer(httpCode: Int, body: String): PlayerAnswer {
    if (httpCode !in 200..299 || body.isEmpty()) return PlayerAnswer.Failed(httpCode)
    val root = try {
        JSONObject(body)
    } catch (_: org.json.JSONException) {
        return PlayerAnswer.Failed(httpCode)
    }
    val playability = root.optJSONObject("playabilityStatus")
    val status = playability?.optString("status").orEmpty()
    if (status.isNotEmpty() && status != "OK") {
        val reason = playability?.optString("reason")?.takeIf { it.isNotBlank() }
        return if (status == "LOGIN_REQUIRED") {
            PlayerAnswer.IdentityRefused(reason)
        } else {
            PlayerAnswer.Unplayable(status, reason)
        }
    }
    val streamingData = root.optJSONObject("streamingData")
        ?: return PlayerAnswer.IdentityRefused(reason = null, root = root)
    return PlayerAnswer.Playable(root, streamingData)
}

/**
 * The `/player` endpoint, called as one [PlayerClient] under one visitorData.
 *
 * It decides nothing: which client, which identity and what to do with a
 * refusal belong to [PlayerSession].
 *
 * @param http must carry a hard `callTimeout`. The call blocks inside OkHttp,
 * so a coroutine timeout above it cannot end it early; the client's own
 * wall-clock cap is the only thing that bounds one request.
 * @param apiKey the public InnerTube key every web client embeds.
 */
internal class PlayerApi(
    private val http: OkHttpClient,
    private val apiKey: String,
) {
    suspend fun player(
        videoId: String,
        client: PlayerClient,
        visitorData: String,
    ): PlayerAnswer = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(videoId, client, visitorData)
            val (code, body) = http.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
            readPlayerAnswer(code, body).also { log(it, videoId, client) }
        } catch (e: CancellationException) {
            // Swallowing this would report a cancelled call as a client with
            // no streams, sending the chain on to the next client inside a
            // coroutine that is already dead.
            throw e
        } catch (e: Exception) {
            KLog.e(STREAM_TAG, "Player[${client.name}] exception videoId=$videoId", e)
            PlayerAnswer.Failed()
        }
    }

    private fun buildRequest(videoId: String, client: PlayerClient, visitorData: String): Request {
        val clientContext = JSONObject().apply {
            put("clientName", client.name)
            put("clientVersion", client.version)
            put("hl", "en")
            put("gl", "US")
            put("utcOffsetMinutes", 0)
            if (visitorData.isNotBlank()) put("visitorData", visitorData)
            client.fields.forEach { (key, value) -> put(key, value) }
        }
        // No playbackContext.signatureTimestamp: the native clients here
        // return unciphered URLs and play without it. WEB and WEB_REMIX are
        // different - their /player refuses every video without one.
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().put("client", clientContext))
            if (client.sendsPlaybackNonce) {
                put("contentPlaybackNonce", nonce(16))
                put("t", nonce(12))
            }
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        return Request.Builder()
            .url("https://youtubei.googleapis.com/youtubei/v1/player?key=$apiKey&prettyPrint=false")
            .post(body.toRequestBody(JSON))
            .addHeader("User-Agent", client.userAgent)
            .addHeader("X-Goog-Api-Format-Version", "2")
            .addHeader("X-YouTube-Client-Name", client.id.toString())
            .addHeader("X-YouTube-Client-Version", client.version)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("Accept", "application/json")
            .apply { if (visitorData.isNotBlank()) addHeader("X-Goog-Visitor-Id", visitorData) }
            .build()
    }

    private fun log(answer: PlayerAnswer, videoId: String, client: PlayerClient) {
        val what = when (answer) {
            is PlayerAnswer.Playable -> return
            is PlayerAnswer.IdentityRefused ->
                if (answer.root == null) "bot check: ${answer.reason}" else "OK without streamingData"
            is PlayerAnswer.Unplayable -> "playability=${answer.status} reason=${answer.reason}"
            is PlayerAnswer.Failed -> "HTTP ${answer.httpCode ?: "none"} or unreadable body"
        }
        KLog.w(STREAM_TAG, "Player[${client.name}] $what videoId=$videoId")
    }

    private fun nonce(length: Int): String =
        buildString(length) { repeat(length) { append(NONCE_ALPHABET[random.nextInt(NONCE_ALPHABET.length)]) } }

    private val random = SecureRandom()

    private companion object {
        val JSON = "application/json".toMediaType()
        const val NONCE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    }
}
