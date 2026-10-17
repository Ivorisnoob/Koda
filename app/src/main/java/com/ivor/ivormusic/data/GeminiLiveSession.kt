package com.ivor.ivormusic.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A WebSocket session to the Gemini Live API.
 * Handles the setup handshake, sends text prompts, and receives
 * streamed audio (base64 PCM 24kHz) + text responses.
 *
 * Endpoint: wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=API_KEY
 */
class GeminiLiveSession(
    context: Context,
    private val aiDjRepository: AiDjRepository,
) {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for streaming
        .build()

    private var webSocket: WebSocket? = null
    private val setupComplete = CountDownLatch(1)
    private val isConnected = AtomicBoolean(false)

    // Accumulators for the current turn.
    private val audioBuffer = ByteArrayOutputStream()
    private val textBuffer = StringBuilder()
    private var turnCompleteLatch: CountDownLatch? = null
    private var lastError: String? = null

    /**
     * Open the WebSocket and perform the setup handshake.
     * @param voiceName The TTS voice (e.g. "Kore", "Puck").
     * @param systemInstruction The DJ personality prompt.
     * @return true if setup completed successfully.
     */
    suspend fun connect(
        voiceName: String,
        systemInstruction: String,
        userProfileSummary: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val apiKey = aiDjRepository.getApiKey()
        if (apiKey.isEmpty()) {
            lastError = "No API key"
            return@withContext false
        }

        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent" +
            "?key=$apiKey"

        val request = Request.Builder().url(url).build()
        setupComplete.countDown() // Reset (in case of reconnect)
        val latch = CountDownLatch(1)
        // Use a fresh latch for this connection attempt.
        val connectLatch = CountDownLatch(1)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send setup as the first message.
                val setup = JSONObject()
                    .put("setup", JSONObject()
                        .put("model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
                        .put("generationConfig", JSONObject()
                            .put("responseModalities", org.json.JSONArray().put("AUDIO").put("TEXT"))
                            .put("speechConfig", JSONObject()
                                .put("voiceConfig", JSONObject()
                                    .put("prebuiltVoiceConfig", JSONObject()
                                        .put("voiceName", voiceName)
                                    )
                                )
                            )
                        )
                        .put("systemInstruction", JSONObject()
                            .put("parts", org.json.JSONArray().put(
                                JSONObject().put("text",
                                    "$systemInstruction\n\nListener profile:\n$userProfileSummary"
                                )
                            ))
                        )
                        .put("outputAudioTranscription", JSONObject())
                    )
                webSocket.send(setup.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    when {
                        msg.has("setupComplete") -> {
                            isConnected.set(true)
                            connectLatch.countDown()
                        }
                        msg.has("serverContent") -> {
                            val sc = msg.getJSONObject("serverContent")
                            // Audio chunks
                            if (sc.has("modelTurn")) {
                                val parts = sc.getJSONObject("modelTurn").optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        if (part.has("inlineData")) {
                                            val inline = part.getJSONObject("inlineData")
                                            val b64 = inline.optString("data", "")
                                            if (b64.isNotEmpty()) {
                                                val pcm = Base64.decode(b64, Base64.DEFAULT)
                                                synchronized(audioBuffer) {
                                                    audioBuffer.write(pcm)
                                                }
                                            }
                                        }
                                        if (part.has("text")) {
                                            synchronized(textBuffer) {
                                                textBuffer.append(part.getString("text"))
                                            }
                                        }
                                    }
                                }
                            }
                            // Turn complete?
                            if (sc.optBoolean("turnComplete", false) ||
                                sc.optBoolean("generationComplete", false)
                            ) {
                                turnCompleteLatch?.countDown()
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore malformed messages.
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                lastError = t.message ?: "WebSocket failure"
                connectLatch.countDown()
                turnCompleteLatch?.countDown()
                isConnected.set(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                connectLatch.countDown()
                turnCompleteLatch?.countDown()
            }
        }

        webSocket = httpClient.newWebSocket(request, listener)
        // Wait for setupComplete (or failure).
        val ok = connectLatch.await(30, TimeUnit.SECONDS)
        ok && isConnected.get()
    }

    /**
     * Send a text prompt and wait for the turn to complete.
     * @return Pair of (audio PCM bytes at 24kHz 16-bit mono, transcript text).
     */
    suspend fun sendText(prompt: String): Pair<ByteArray, String>? =
        withContext(Dispatchers.IO) {
            val ws = webSocket
            if (ws == null || !isConnected.get()) {
                lastError = "Not connected"
                return@withContext null
            }

            // Reset accumulators.
            synchronized(audioBuffer) { audioBuffer.reset() }
            synchronized(textBuffer) { textBuffer.setLength(0) }
            val latch = CountDownLatch(1)
            turnCompleteLatch = latch

            val msg = JSONObject()
                .put("clientContent", JSONObject()
                    .put("turns", org.json.JSONArray().put(
                        JSONObject().put("parts", org.json.JSONArray().put(
                            JSONObject().put("text", prompt)
                        ))
                    ))
                    .put("turnComplete", true)
                )
            ws.send(msg.toString())

            // Wait for turn completion (up to 60s).
            val done = latch.await(60, TimeUnit.SECONDS)
            turnCompleteLatch = null

            if (!done) {
                lastError = "Turn timeout"
                return@withContext null
            }

            val audio = synchronized(audioBuffer) { audioBuffer.toByteArray() }
            val text = synchronized(textBuffer) { textBuffer.toString() }
            Pair(audio, text)
        }

    /** Close the WebSocket. */
    fun disconnect() {
        try {
            webSocket?.close(1000, "Done")
        } catch (_: Exception) {
        }
        webSocket = null
        isConnected.set(false)
    }

    fun isConnected(): Boolean = isConnected.get()
    fun lastError(): String? = lastError

    companion object {
        /**
         * Convert raw 24kHz 16-bit mono PCM bytes to a WAV file.
         */
        fun pcmToWav(pcm: ByteArray, sampleRate: Int = 24000): ByteArray {
            val out = ByteArrayOutputStream()
            // WAV header (44 bytes)
            val dataLen = pcm.size
            val totalLen = dataLen + 36
            out.write("RIFF".toByteArray())
            out.write(intToBytes(totalLen))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToBytes(16)) // fmt chunk size
            out.write(shortToBytes(1)) // PCM
            out.write(shortToBytes(1)) // mono
            out.write(intToBytes(sampleRate))
            out.write(intToBytes(sampleRate * 2)) // byte rate
            out.write(shortToBytes(2)) // block align
            out.write(shortToBytes(16)) // bits per sample
            out.write("data".toByteArray())
            out.write(intToBytes(dataLen))
            out.write(pcm)
            return out.toByteArray()
        }

        private fun intToBytes(v: Int): ByteArray = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )

        private fun shortToBytes(v: Int): ByteArray = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
        )
    }
}
