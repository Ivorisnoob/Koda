package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * The live DJ engine: per-track selection, real-time commentary via the
 * Gemini Live API, and a feedback loop that learns from skips and saves.
 *
 * Pipeline:
 *  1. Data Collection  -> DjUserProfile (history, skips, saves)
 *  2. Session Init     -> GeminiLiveSession (WebSocket, profile sent at setup)
 *  3. Track Selection  -> suitability scoring, pick next track
 *  4. Commentary Gen   -> Live API generates spoken intro + streams voice audio
 *  5. Audio Playback   -> voice WAV file played, then the music track
 *  6. Feedback Loop    -> skips/saves recorded, scoring adjusts
 */
class LiveDjEngine(context: Context) {

    private val appContext = context.applicationContext
    val aiDjRepository = AiDjRepository(appContext)
    val userProfile = DjUserProfile(appContext)
    private val liveSession = GeminiLiveSession(appContext, aiDjRepository)
    private val geminiTts = GeminiTtsRepository(aiDjRepository)

    private var sessionActive = false

    data class DjTurn(
        val song: Song,
        val commentaryWav: File?, // null if no commentary this turn
        val commentaryText: String,
    )

    /**
     * Start a live DJ session. Opens the WebSocket and sends the user profile.
     * @return true if the session is ready.
     */
    suspend fun startSession(): Boolean = withContext(Dispatchers.IO) {
        val voice = geminiTts.voice // user's selected voice
        val profileSummary = userProfile.buildProfileSummary()
        val systemInstruction =
            "You are Koda DJ, a charismatic radio DJ with impeccable taste. " +
                "For each track I name, respond with ONE vivid spoken sentence " +
                "introducing it — warm, specific, never generic. " +
                "Only speak when the moment deserves it; if the track flows " +
                "naturally from the last, reply with just \"...\" (no commentary)."
        val ok = liveSession.connect(voice, systemInstruction, profileSummary)
        sessionActive = ok
        ok
    }

    /** End the session and close the WebSocket. */
    fun endSession() {
        liveSession.disconnect()
        sessionActive = false
    }

    fun isSessionActive(): Boolean = sessionActive && liveSession.isConnected()

    /**
     * Select the next track using suitability scoring.
     * Scores each candidate on: liked-artist boost, skip penalty, recency
     * penalty (avoid repeats), save boost, and random jitter for variety.
     */
    suspend fun selectNextTrack(
        library: List<Song>,
        recentlyPlayedIds: Set<String>,
        lastSong: Song?,
    ): Song? = withContext(Dispatchers.Default) {
        if (library.isEmpty()) return@withContext null
        val events = userProfile.getEventsSync()
        val saves = userProfile.getSaves()

        // Skip counts per song and per artist (penalize frequent skips).
        val skipCountBySong = events.filter { !it.completed }
            .groupingBy { it.songId }.eachCount()
        val skipCountByArtist = events.filter { !it.completed }
            .groupingBy { it.artist }.eachCount()
        val playCountBySong = events.groupingBy { it.songId }.eachCount()

        // Artist affinity: completed plays per artist.
        val affinityByArtist = events.filter { it.completed }
            .groupingBy { it.artist }.eachCount()

        val scored = library.map { song ->
            var score = 50.0 // base

            // Liked artist boost (from history affinity).
            val affinity = affinityByArtist[song.artist] ?: 0
            score += (affinity * 8.0).coerceAtMost(40.0)

            // Saved track boost.
            if (saves.contains(song.id)) score += 25.0

            // Skip penalties.
            score -= (skipCountBySong[song.id] ?: 0) * 30.0
            score -= (skipCountByArtist[song.artist] ?: 0) * 10.0

            // Recency penalty: avoid playing the same song twice in a session.
            if (recentlyPlayedIds.contains(song.id)) score -= 100.0

            // Overplay penalty: played a lot recently.
            score -= (playCountBySong[song.id] ?: 0) * 5.0

            // Energy flow: if last song was high-energy, prefer a comedown
            // (and vice versa). We approximate via title keywords.
            if (lastSong != null) {
                val lastUpbeat = isUpbeat(lastSong)
                val thisUpbeat = isUpbeat(song)
                if (lastUpbeat != thisUpbeat) score += 10.0 // contrast is good
            }

            // Random jitter for variety (the 1:100000 seed idea, per-track).
            score += Random.nextDouble(0.0, 20.0)

            song to score
        }

        // Weighted random pick from the top candidates (not just argmax —
        // pure argmax would repeat the same "best" song).
        val top = scored.sortedByDescending { it.second }.take(15)
        if (top.isEmpty()) return@withContext null
        val totalWeight = top.sumOf { (it.second - top.last().second + 1.0).coerceAtLeast(1.0) }
        var r = Random.nextDouble() * totalWeight
        for ((song, s) in top) {
            val w = (s - top.last().second + 1.0).coerceAtLeast(1.0)
            r -= w
            if (r <= 0) return@withContext song
        }
        top.first().first
    }

    /**
     * Generate commentary for the selected track via the Live API.
     * Returns the WAV file (24kHz PCM) and transcript.
     * Falls back to the REST TTS if the Live session is down.
     */
    suspend fun generateCommentary(
        song: Song,
        previousSong: Song?,
    ): Pair<File?, String> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("Next track: \"${song.title}\" by ${song.artist}")
            if (previousSong != null) {
                append(" (coming from \"${previousSong.title}\" by ${previousSong.artist})")
            }
            append(".")
        }

        if (isSessionActive()) {
            val result = liveSession.sendText(prompt)
            if (result != null) {
                val (pcm, text) = result
                val transcript = text.trim()
                // "..." means the DJ chose silence for this transition.
                if (transcript == "..." || pcm.isEmpty()) {
                    return@withContext Pair(null, "")
                }
                val wav = GeminiLiveSession.pcmToWav(pcm)
                val file = File(appContext.cacheDir, "dj_live_${System.currentTimeMillis()}.wav")
                file.writeBytes(wav)
                return@withContext Pair(file, transcript)
            }
        }

        // Fallback: REST TTS for a short intro line.
        val line = "Up next, ${song.title} by ${song.artist}."
        val file = File(appContext.cacheDir, "dj_fallback_${System.currentTimeMillis()}.wav")
        val ok = geminiTts.synthesize(line, file)
        if (ok) Pair(file, line) else Pair(null, "")
    }

    /**
     * Run one full DJ turn: pick a track, generate its commentary.
     */
    suspend fun nextTurn(
        library: List<Song>,
        recentlyPlayedIds: Set<String>,
        lastSong: Song?,
    ): DjTurn? = withContext(Dispatchers.IO) {
        val song = selectNextTrack(library, recentlyPlayedIds, lastSong)
            ?: return@withContext null
        val (wav, text) = generateCommentary(song, lastSong)
        DjTurn(song, wav, text)
    }

    /**
     * Feedback: the user skipped this track. Record it so scoring penalizes it.
     * @param playDurationMs how long it played before the skip.
     */
    suspend fun onTrackSkipped(song: Song, playDurationMs: Long) {
        userProfile.recordListen(song, completed = false, playDurationMs = playDurationMs)
    }

    /** Feedback: the track played through. */
    suspend fun onTrackCompleted(song: Song, playDurationMs: Long) {
        userProfile.recordListen(song, completed = true, playDurationMs = playDurationMs)
    }

    /** Feedback: the user saved (liked) this track. */
    suspend fun onTrackSaved(song: Song) {
        userProfile.recordSave(song)
    }

    /** Feedback: the user unsaved (unliked) this track. */
    suspend fun onTrackUnsaved(songId: String) {
        userProfile.recordUnsave(songId)
    }

    fun lastError(): String? = liveSession.lastError()

    private fun isUpbeat(song: Song): Boolean {
        val t = "${song.title} ${song.artist}".lowercase()
        val upbeatWords = listOf(
            "dance", "party", "up", "fire", "hot", "wild", "crazy",
            "energy", "pump", "jump", "run", "fast", "loud",
        )
        return upbeatWords.any { t.contains(it) }
    }
}
