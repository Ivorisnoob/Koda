package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import android.util.Base64
import com.ivor.ivormusic.util.KLog

/**
 * Natural-sounding DJ voice via Gemini's native text-to-speech.
 *
 * Unlike the on-device TextToSpeech (robotic), Gemini TTS produces
 * human-like speech with selectable voices. Uses the same Google AI API key
 * as the DJ's playlist generation — no extra setup.
 *
 * The API returns 24kHz 16-bit mono PCM; we wrap it in a WAV header so the
 * player can play the file directly.
 */
/**
 * @param aiDjRepository The single source of truth for the Google AI API key.
 * TTS uses the exact same key variable as the DJ generator — no separate storage.
 */
class GeminiTtsRepository(
    private val aiDjRepository: AiDjRepository,
) {
    private val appContext: Context = aiDjRepository.appContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /** The API key: directly from the DJ repository — the same variable. */
    private fun apiKey(): String = aiDjRepository.getApiKey()

    /**
     * Diagnostic: reports what the tester sees (without exposing the key value).
     * Returns "prefs=ivor_music_ai_dj key_len=39" or "prefs=... key=MISSING".
     */
    fun keyDiagnostic(): String {
        val key = aiDjRepository.getApiKey()
        return if (key.isEmpty()) {
            "key=MISSING (via AiDjRepository)"
        } else {
            "key_len=${key.length} (via AiDjRepository)"
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** The selected TTS voice. */
    var voice: String
        get() = prefs.getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
        set(value) { prefs.edit().putString(KEY_VOICE, value).apply() }

    /**
     * Synthesize [text] to [outputFile] (WAV). Returns true on success.
     */
    suspend fun synthesize(text: String, outputFile: File): Boolean =
        synthesize(text, voice, outputFile)

    suspend fun synthesize(text: String, voiceName: String, outputFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val apiKey = apiKey()
            if (apiKey.isEmpty()) {
                KLog.w(TAG, "Gemini TTS: no API key")
                return@withContext false
            }
            // A little direction helps the voice sound like a DJ, not a reader.
            val directed = "Say in a warm, engaging radio DJ voice: $text"
            val body = JSONObject()
                .put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", directed)
                    ))
                ))
                .put("generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject()
                        .put("voiceConfig", JSONObject()
                            .put("prebuiltVoiceConfig", JSONObject()
                                .put("voiceName", voiceName)
                            )
                        )
                    )
                )
                .toString()
            // Try TTS models in order; the 2.5 preview may be retired.
            for (model in orderedModels()) {
                try {
                    val request = Request.Builder()
                        .url("$TTS_URL_BASE/$model:generateContent")
                        .header("x-goog-api-key", apiKey)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            KLog.w(TAG, "Gemini TTS ($model) failed: HTTP ${response.code} ${responseBody.take(200)}")
                            // Try the next model on any failure, not just 404:
                            // a 400 on one model shouldn't kill the fallback chain.
                            continue
                        }
                        val pcmBase64 = runCatching {
                            JSONObject(responseBody)
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getJSONObject("inlineData")
                                .getString("data")
                        }.getOrNull() ?: continue
                        val pcm = Base64.decode(pcmBase64, Base64.DEFAULT)
                        writeWav(outputFile, pcm)
                        return@withContext true
                    }
                } catch (e: Exception) {
                    KLog.w(TAG, "Gemini TTS ($model) failed", e)
                    continue
                }
            }
            return@withContext false
        }

    /**
     * Result of testing a single TTS model. Used by the Settings voice tester
     * so the user can see exactly which model produces audio.
     */
    data class TtsTestResult(
        val model: String,
        val success: Boolean,
        val httpCode: Int,
        val detail: String,
        val audioBytes: Int = 0,
    )

    /**
     * Test [model] with a short phrase in [voiceName]. Returns full diagnostics;
     * never throws. Writes a WAV sample to [outputFile] on success.
     */
    suspend fun testModel(
        model: String,
        voiceName: String,
        outputFile: File,
    ): TtsTestResult = withContext(Dispatchers.IO) {
        val apiKey = apiKey()
        if (apiKey.isEmpty()) {
            return@withContext TtsTestResult(model, false, 0, "No API key saved")
        }
        val body = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", "This is a voice test for the Koda AI DJ.")
                ))
            ))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject()
                    .put("voiceConfig", JSONObject()
                        .put("prebuiltVoiceConfig", JSONObject()
                            .put("voiceName", voiceName)
                        )
                    )
                )
            )
            .toString()
        try {
            val request = Request.Builder()
                .url("$TTS_URL_BASE/$model:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching {
                        JSONObject(responseBody).getJSONObject("error").optString("message")
                    }.getOrNull().takeIf { !it.isNullOrBlank() }
                        ?: responseBody.take(200)
                    KLog.w(TAG, "TTS test ($model): HTTP ${response.code} $err")
                    return@withContext TtsTestResult(
                        model, false, response.code,
                        "HTTP ${response.code}: $err",
                    )
                }
                val pcmBase64 = runCatching {
                    JSONObject(responseBody)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getJSONObject("inlineData")
                        .getString("data")
                }.getOrNull()
                if (pcmBase64.isNullOrBlank()) {
                    KLog.w(TAG, "TTS test ($model): 200 but no audio in response")
                    return@withContext TtsTestResult(
                        model, false, 200,
                        "OK but no audio data in response",
                    )
                }
                val pcm = Base64.decode(pcmBase64, Base64.DEFAULT)
                writeWav(outputFile, pcm)
                // Remember the working model so synthesize() tries it first.
                prefs.edit().putString(KEY_WORKING_MODEL, model).apply()
                TtsTestResult(model, true, 200, "Voice ready", pcm.size)
            }
        } catch (e: Exception) {
            KLog.w(TAG, "TTS test ($model) failed", e)
            TtsTestResult(model, false, -1, "Error: ${e.message}")
        }
    }

    /** All TTS models available for testing, in default try-order. */
    fun allModels(): List<String> = TTS_MODELS

    /** Models to try, working model first when known. */
    fun orderedModels(): List<String> {
        val working = prefs.getString(KEY_WORKING_MODEL, null)
        return if (working != null && TTS_MODELS.contains(working)) {
            listOf(working) + TTS_MODELS.filter { it != working }
        } else TTS_MODELS
    }

    /**
     * Wrap 24kHz 16-bit mono PCM in a WAV header.
     */
    private fun writeWav(file: File, pcm: ByteArray) {
        val sampleRate = 24000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val dataSize = pcm.size
        val totalSize = 36 + dataSize

        file.outputStream().buffered().use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToBytes(totalSize))
            out.write("WAVE".toByteArray())
            // fmt chunk
            out.write("fmt ".toByteArray())
            out.write(intToBytes(16)) // chunk size
            out.write(shortToBytes(1)) // PCM
            out.write(shortToBytes(channels.toShort()))
            out.write(intToBytes(sampleRate))
            out.write(intToBytes(byteRate))
            out.write(shortToBytes(blockAlign))
            out.write(shortToBytes(bitsPerSample.toShort()))
            // data chunk
            out.write("data".toByteArray())
            out.write(intToBytes(dataSize))
            out.write(pcm)
        }
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun shortToBytes(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte(),
    )

    companion object {
        private const val TAG = "GeminiTts"
        private const val PREFS_NAME = "gemini_tts"
        private const val DJ_PREFS_NAME = "ivor_music_ai_dj"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_VOICE = "tts_voice"
        private const val KEY_WORKING_MODEL = "tts_working_model"
        private const val TTS_URL_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        /** TTS models to try in order; 3.8 is current, older previews as fallback. */
        private val TTS_MODELS = listOf(
            "gemini-3.8-flash-tts",
            "gemini-3.8-flash-lite-tts",
            "gemini-3.1-flash-tts-preview",
            "gemini-2.5-flash-preview-tts",
        )
        const val DEFAULT_VOICE = "Kore"

        /** Curated voices that suit a DJ. */
        val VOICES = listOf(
            "Kore" to "Warm and engaging",
            "Charon" to "Deep and smooth",
            "Puck" to "Playful and bright",
            "Zephyr" to "Bright and clear",
            "Fenrir" to "Smooth and relaxed",
        )
    }
}
