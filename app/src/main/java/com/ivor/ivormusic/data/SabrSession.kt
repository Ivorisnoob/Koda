package com.ivor.ivormusic.data

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One SABR playback session: the request half of YouTube's server-adaptive path.
 *
 * **This is driven by the consumer, not by a loop of its own.** The caller asks
 * for a player time and says which segments it already holds; the server replies
 * with whatever segments it chooses, which are handed back through a callback.
 * An earlier version ran its own download loop into a spool file, which could
 * only ever move forwards from segment 1 - fine for linear playback and
 * structurally unable to seek. Addressing segments individually is what the
 * reference client does and what [SabrMediaBridge] builds on.
 *
 * Several wire details here are not what the published protobuf definitions say,
 * and every one of them fails silently rather than with an error:
 *
 * - **`BufferedRange` timing lives in the `timeRange` submessage (field 6)**,
 *   not the flat `startTimeMs`/`durationMs` at 2 and 3. Omit it and the server
 *   sees a client holding nothing and answers every request identically.
 * - **The streaming URL is never posted to bare.** `alr`, `cpn` and an
 *   incrementing `rn` identify the playback session; without them the server
 *   cannot correlate requests and answers with a reload demand instead.
 * - **`MediaHeader` never sends `startMs`/`durationMs`** (11/12) even though it
 *   defines them; timing arrives only as `timeRange` (15).
 * - **Init segments carry `isInitSeg` and no sequence number.**
 * - **`SABR_CONTEXT_UPDATE` (57) blobs must be echoed back** on every later
 *   request, or the server treats the session as lost.
 * - **`NEXT_REQUEST_POLICY` field 4 is a backoff** the caller is expected to
 *   honour.
 */
class SabrSession(
    private val videoId: String,
    private var streamingUrl: String,
    private var ustreamerConfig: ByteArray,
    private val poToken: ByteArray?,
    private val clientInfo: SabrProtocol.ClientInfo,
    private val userAgent: String,
    /**
     * Client playback nonce. googlevideo identifies a playback session by this,
     * so it is generated once and rides on every request URL.
     */
    private var cpn: String = generateCpn(),
    /**
     * Re-resolves the video's `/player` response when the server asks for one.
     * Null means the reload cannot be satisfied and the request fails.
     */
    private val reloadProvider: (suspend () -> Reload?)? = null,
) {

    /** Fresh streaming parameters for a session the server asked to reload. */
    data class Reload(val streamingUrl: String, val ustreamerConfig: ByteArray)

    /** What the caller holds for one track, so the server knows where to resume. */
    data class TrackRequest(
        val format: SabrProtocol.FormatId,
        val index: SabrProtocol.SegmentIndex?,
        val bufferedThrough: Int,
    )

    /** One complete segment as delivered by the server. */
    class Segment(
        val itag: Int,
        val sequence: Int,
        val isInit: Boolean,
        val data: ByteArray,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Contexts the server issued and expects echoed. Insertion-ordered. */
    private val sabrContexts = LinkedHashMap<Int, ByteArray>()
    private val activeContextTypes = LinkedHashSet<Int>()

    private var playbackCookie: ByteArray? = null
    private var requestNumber = 0
    private var reloads = 0

    /**
     * Serialises requests. The protocol carries per-session state - `rn`, the
     * playback cookie, the contexts - so two in flight would interleave it and
     * desynchronise the session.
     */
    private val requestMutex = Mutex()

    /** Media duration reported by FORMAT_INITIALIZATION_METADATA, once seen. */
    @Volatile
    var totalDurationMs: Long = 0L
        private set

    /** Backoff the server last asked for, in milliseconds. */
    @Volatile
    var backoffMs: Long = 0L
        private set

    @Volatile
    private var released = false

    fun release() {
        released = true
    }

    /**
     * Issue one SABR request and hand every complete segment to [onSegment].
     *
     * Serialised: the protocol is a conversation with per-session state
     * (`rn`, the playback cookie, the contexts), so two requests in flight would
     * interleave that state and desynchronise the session.
     */
    suspend fun requestOnce(
        playerTimeMs: Long,
        tracks: List<TrackRequest>,
        onSegment: (Segment) -> Unit,
    ) = requestMutex.withLock {
        if (released) throw IOException("SABR session released")

        var attempt = 0
        while (true) {
            val audio = tracks.firstOrNull()
            val video = tracks.getOrNull(1)
            val body = SabrProtocol.buildAbrRequest(
                playerTimeMs = playerTimeMs,
                stickyResolution = 0,
                videoFormat = video?.format,
                audioFormat = audio?.format,
                ustreamerConfig = ustreamerConfig,
                poToken = poToken,
                clientInfo = clientInfo,
                bufferedRanges = tracks.mapNotNull { bufferedRange(it) },
                playbackCookie = playbackCookie,
                sabrContexts = activeContexts(),
                unsentContextTypes = unsentContextTypes(),
            )

            val response = request(body)
            requestNumber++
            val outcome = consume(response, onSegment)

            if (outcome.fatal != null) {
                throw IOException("SABR: ${outcome.fatal}")
            }
            if (!outcome.reloadRequired) return

            // A reload is the server saying this video's `/player` response has
            // aged out, not that playback failed. The segments already handed
            // out stay valid; only the streaming parameters are stale.
            if (++reloads > MAX_RELOADS || ++attempt > MAX_RELOAD_ATTEMPTS_PER_REQUEST) {
                throw IOException("SABR: reload not satisfied")
            }
            val fresh = reloadProvider?.invoke()
                ?: throw IOException("SABR: reload required but unavailable")
            // **A reload starts a new playback session, not a patched one.**
            // The `cpn` identifies the session to googlevideo and belongs to the
            // player response that produced these streaming parameters; carrying
            // the old one over asks the server to continue a session it has just
            // said is finished. `rn` restarts with it, since it numbers requests
            // within a session, and the cookie and contexts describe the session
            // that ended.
            Log.i(TAG, "SABR reloaded $videoId (#$reloads), new session")
            streamingUrl = fresh.streamingUrl
            ustreamerConfig = fresh.ustreamerConfig
            cpn = generateCpn()
            requestNumber = 0
            playbackCookie = null
            sabrContexts.clear()
            activeContextTypes.clear()
        }
    }

    private fun bufferedRange(track: TrackRequest): SabrProtocol.BufferedRange? {
        val index = track.index ?: return null
        val end = minOf(track.bufferedThrough, index.size)
        if (end <= 0) return null
        return SabrProtocol.BufferedRange(
            formatId = track.format,
            startTimeMs = 0,
            durationMs = maxOf(0, index.endMs(end)),
            startSegmentIndex = 1,
            endSegmentIndex = end,
        )
    }

    // ------------------------------------------------------------------

    /**
     * **The streaming URL is not posted to bare.** googlevideo tracks a playback
     * session through query parameters: `alr=yes`, the `cpn` identifying the
     * session, and `rn`, a request number that increments. Without them the
     * server cannot correlate one request with the last, which it answers by
     * demanding a player reload (part 46) - on the first request for some
     * videos and after about a minute for the rest, both of which read as
     * playback simply stopping.
     */
    private fun sessionUrl(): String {
        var url = streamingUrl
        if (!Regex("[?&]alr=").containsMatchIn(url)) {
            url += if (url.contains('?')) "&alr=yes" else "?alr=yes"
        }
        if (!Regex("[?&]cpn=").containsMatchIn(url)) {
            url += if (url.contains('?')) "&cpn=$cpn" else "?cpn=$cpn"
        }
        val fragmentIndex = url.indexOf('#')
        val base = if (fragmentIndex < 0) url else url.substring(0, fragmentIndex)
        val fragment = if (fragmentIndex < 0) "" else url.substring(fragmentIndex)
        val queryIndex = base.indexOf('?')
        val path = if (queryIndex < 0) base else base.substring(0, queryIndex)
        val query = if (queryIndex < 0) "" else base.substring(queryIndex + 1)
        val kept = query.split("&").filter { it.isNotEmpty() && it.substringBefore('=') != "rn" }
        return buildString {
            append(path).append('?')
            for (parameter in kept) append(parameter).append('&')
            append("rn=").append(requestNumber)
            append(fragment)
        }
    }

    private fun request(body: ByteArray): ByteArray {
        val request = Request.Builder()
            .url(sessionUrl())
            .post(body.toRequestBody("application/x-protobuf".toMediaType()))
            .addHeader("User-Agent", userAgent)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("SABR HTTP ${response.code}")
            return response.body?.bytes() ?: throw IOException("empty SABR body")
        }
    }

    private class Outcome(val fatal: String?, val reloadRequired: Boolean)

    /**
     * Walk the UMP parts of one response, assembling segments.
     *
     * A segment is a header, then one or more `MEDIA` parts carrying its bytes,
     * then `MEDIA_END`. Emitting only on `MEDIA_END` is what keeps a segment
     * split across parts from being handed over half-written.
     */
    private fun consume(raw: ByteArray, onSegment: (Segment) -> Unit): Outcome {
        val headers = HashMap<Int, SabrProtocol.MediaHeader>()
        val buffers = HashMap<Int, ByteArrayOutputStream>()
        var fatal: String? = null
        var reloadRequired = false

        for (part in SabrProtocol.parseUmp(raw)) {
            when (part.type) {
                SabrProtocol.Part.MEDIA_HEADER -> {
                    val header = SabrProtocol.MediaHeader.parse(part.payload)
                    headers[header.headerId] = header
                    buffers[header.headerId] = ByteArrayOutputStream()
                }

                SabrProtocol.Part.MEDIA -> {
                    val idAndOffset = SabrProtocol.readUmpVarInt(part.payload, 0) ?: continue
                    val buffer = buffers[idAndOffset.first.toInt()] ?: continue
                    val offset = idAndOffset.second
                    if (offset < part.payload.size) {
                        buffer.write(part.payload, offset, part.payload.size - offset)
                    }
                }

                SabrProtocol.Part.MEDIA_END -> {
                    val id = SabrProtocol.readUmpVarInt(part.payload, 0)?.first?.toInt() ?: continue
                    val header = headers[id] ?: continue
                    val buffer = buffers.remove(id) ?: continue
                    val bytes = buffer.toByteArray()
                    if (bytes.isNotEmpty()) {
                        onSegment(
                            Segment(
                                itag = header.itag,
                                sequence = header.sequenceNumber,
                                isInit = header.isInitSegment,
                                data = bytes,
                            )
                        )
                    }
                }

                SabrProtocol.Part.FORMAT_INITIALIZATION_METADATA -> {
                    val init = SabrProtocol.FormatInit.parse(part.payload)
                    if (init.durationMs > totalDurationMs) totalDurationMs = init.durationMs
                }

                SabrProtocol.Part.NEXT_REQUEST_POLICY -> {
                    val policy = SabrProtocol.decode(part.payload)
                    policy.bytes(7)?.let { playbackCookie = it }
                    backoffMs = policy.int(4)?.toLong()?.coerceAtLeast(0L) ?: 0L
                }

                SabrProtocol.Part.SABR_CONTEXT_UPDATE -> ingestContextUpdate(part.payload)

                SabrProtocol.Part.SABR_CONTEXT_SENDING_POLICY ->
                    ingestContextSendingPolicy(part.payload)

                SabrProtocol.Part.SABR_REDIRECT -> {
                    SabrProtocol.decode(part.payload).string(1)
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            streamingUrl = it
                            Log.i(TAG, "SABR redirected to a new edge")
                        }
                }

                SabrProtocol.Part.SABR_ERROR ->
                    fatal = "server error ${SabrProtocol.decode(part.payload)}"

                SabrProtocol.Part.RELOAD_PLAYER_RESPONSE -> reloadRequired = true

                SabrProtocol.Part.STREAM_PROTECTION_STATUS -> {
                    // 1 ok, 2 attestation pending, 3 attestation required. Only
                    // 3 is terminal, and it means the PO token was rejected.
                    if ((SabrProtocol.decode(part.payload).int(1) ?: 0) >= 3) {
                        fatal = "attestation required (PO token rejected)"
                    }
                }
            }
        }
        return Outcome(fatal, reloadRequired)
    }

    /**
     * Record a context blob. `writePolicy == 2` means write-once, so a repeat of
     * a type already held is ignored, and `sendByDefault` puts a new blob
     * straight into rotation.
     */
    private fun ingestContextUpdate(payload: ByteArray) {
        val fields = SabrProtocol.decode(payload)
        val type = fields.int(1) ?: return
        val value = fields.bytes(3)?.takeIf { it.isNotEmpty() } ?: return
        if ((fields.int(5) ?: -1) == 2 && sabrContexts.containsKey(type)) return
        sabrContexts[type] = value
        if ((fields.long(4) ?: 0L) != 0L) activeContextTypes.add(type)
    }

    /**
     * The server turning contexts on (1), off (2) or discarding them (3). Types
     * arrive as repeated varints or packed into one, hence `longs`.
     */
    private fun ingestContextSendingPolicy(payload: ByteArray) {
        val fields = SabrProtocol.decode(payload)
        for (type in fields.longs(1)) activeContextTypes.add(type.toInt())
        for (type in fields.longs(2)) activeContextTypes.remove(type.toInt())
        for (type in fields.longs(3)) {
            sabrContexts.remove(type.toInt())
            activeContextTypes.remove(type.toInt())
        }
    }

    private fun activeContexts(): Map<Int, ByteArray> {
        val out = LinkedHashMap<Int, ByteArray>(activeContextTypes.size)
        for (type in activeContextTypes) sabrContexts[type]?.let { out[type] = it }
        return out
    }

    private fun unsentContextTypes(): List<Int> =
        sabrContexts.keys.filter { it !in activeContextTypes }

    companion object {
        private const val TAG = "SabrSession"

        /** Player-response reloads one session will serve before giving up. */
        private const val MAX_RELOADS = 60

        /**
         * Reloads tolerated inside a single request. One is a legitimate race
         * (the response aged out exactly as the request was built); a second in
         * a row means re-resolving is not what the server is asking for.
         */
        private const val MAX_RELOAD_ATTEMPTS_PER_REQUEST = 2

        private const val CPN_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        /** 16 characters from the alphabet googlevideo expects. */
        fun generateCpn(): String {
            val random = java.security.SecureRandom()
            return buildString(16) {
                repeat(16) { append(CPN_ALPHABET[random.nextInt(CPN_ALPHABET.length)]) }
            }
        }
    }
}
