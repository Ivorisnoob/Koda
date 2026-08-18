package com.ivor.ivormusic.data

/**
 * Wire format for YouTube's SABR (server-adaptive bitrate) playback path.
 *
 * **Why this exists at all.** googlevideo now serves only a bounded prefix of
 * any progressively-fetched stream - measured August 2026 at 10-16 MiB per
 * resolved URL, regardless of range size - and answers 403 for everything past
 * it. Re-resolving does not reopen the budget: a freshly resolved URL refuses a
 * first request that starts at a far offset, so it cannot be used to resume
 * either. A PO token does not lift it. Every `/player` response now carries
 * `streamingData.serverAbrStreamingUrl`, and that is the path YouTube expects a
 * client to use; SABR reads the whole file where ranged GETs stop at ~16 MiB
 * (verified against a 335 MiB 1080p stream, August 2026).
 *
 * **Two shapes here are not what the published protobuf definitions say**, both
 * verified against live responses rather than taken from a schema:
 *
 * - `MediaHeader` defines `startMs` (11) and `durationMs` (12) and **never
 *   sends them**. Timing arrives only as [TimeRange] (15), in ticks over a
 *   timescale. Building [BufferedRange]s from the documented fields yields
 *   zero-length ranges, the server reads that as "the client holds nothing",
 *   and it re-sends segment 1 forever - which looks like a working request loop
 *   that never advances.
 * - Init segments carry `isInitSeg` (8) and no sequence number, so they must be
 *   excluded from buffered ranges rather than counted as segment 0.
 *
 * Field numbers throughout were read off live traffic and cross-checked against
 * the generated encoders in the reference implementation. Note the wire uses
 * *two different* varint encodings: protobuf's LEB128 for message fields, and
 * UMP's own length-prefixed form for part framing ([readUmpVarInt]).
 */
object SabrProtocol {

    /**
     * Decode base64 that may be standard or URL-safe, padded or not.
     *
     * YouTube mixes both alphabets in the same response - `ustreamerConfig`
     * arrives standard, the PO token is minted URL-safe - and Android's
     * decoder rejects the wrong one rather than coping, so normalise first.
     */
    fun decodeBase64(value: String): ByteArray {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> normalized
        }
        return android.util.Base64.decode(padded, android.util.Base64.NO_WRAP)
    }

    // ---------------------------------------------------------------------
    // protobuf writing (only the wire types the SABR request needs)
    // ---------------------------------------------------------------------

    /** Minimal protobuf writer. Fields must be appended in ascending order. */
    class Writer {
        private val out = java.io.ByteArrayOutputStream(256)

        fun varint(field: Int, value: Long): Writer {
            if (value != 0L) {
                writeTag(field, 0)
                writeVarint(value)
            }
            return this
        }

        fun varint(field: Int, value: Int): Writer = varint(field, value.toLong())

        /**
         * A varint written even when it is zero.
         *
         * Proto3 omits zero-valued scalars and [varint] follows that, which is
         * right for optional state but wrong inside [BufferedRange]: the server
         * reads a missing `startTimeMs` as a missing range rather than as a
         * range starting at zero, and the first range of every session starts
         * at zero. The reference client writes these unconditionally.
         */
        fun varintAlways(field: Int, value: Long): Writer {
            writeTag(field, 0)
            writeVarint(value)
            return this
        }

        fun varintAlways(field: Int, value: Int): Writer = varintAlways(field, value.toLong())

        fun bool(field: Int, value: Boolean): Writer {
            if (value) {
                writeTag(field, 0)
                writeVarint(1L)
            }
            return this
        }

        /** Wire type 5: 32-bit little-endian, used for `playbackRate`. */
        fun float(field: Int, value: Float): Writer {
            if (value != 0f) {
                writeTag(field, 5)
                val bits = java.lang.Float.floatToIntBits(value)
                out.write(bits and 0xFF)
                out.write((bits ushr 8) and 0xFF)
                out.write((bits ushr 16) and 0xFF)
                out.write((bits ushr 24) and 0xFF)
            }
            return this
        }

        fun bytes(field: Int, value: ByteArray?): Writer {
            if (value != null && value.isNotEmpty()) {
                writeTag(field, 2)
                writeVarint(value.size.toLong())
                out.write(value, 0, value.size)
            }
            return this
        }

        fun string(field: Int, value: String?): Writer =
            bytes(field, value?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8))

        /** Embedded message. Empty bodies are omitted, matching proto3. */
        fun message(field: Int, body: ByteArray): Writer = bytes(field, body)

        fun toByteArray(): ByteArray = out.toByteArray()

        private fun writeTag(field: Int, wire: Int) = writeVarint(((field shl 3) or wire).toLong())

        private fun writeVarint(value: Long) {
            var v = value
            while (true) {
                val b = (v and 0x7F).toInt()
                v = v ushr 7
                if (v == 0L) {
                    out.write(b)
                    return
                }
                out.write(b or 0x80)
            }
        }
    }

    // ---------------------------------------------------------------------
    // protobuf reading
    // ---------------------------------------------------------------------

    /**
     * A shallow decode of one protobuf message: field number to raw values.
     *
     * Deliberately untyped. The parts this client reads are a handful of fields
     * out of messages with dozens, most of them undocumented, and a full model
     * per part would be far more code to keep honest than it is worth. Callers
     * pull the two or three fields they need through the accessors below.
     */
    class Fields(private val map: Map<Int, List<Any>>) {
        fun long(field: Int): Long? = map[field]?.firstOrNull() as? Long
        fun int(field: Int): Int? = long(field)?.toInt()
        fun bytes(field: Int): ByteArray? = map[field]?.firstOrNull() as? ByteArray
        fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)
        fun message(field: Int): Fields? = bytes(field)?.let { decode(it) }
        fun has(field: Int): Boolean = map.containsKey(field)

        /**
         * Every varint carried by [field], whether it arrived as repeated
         * entries or packed into one length-delimited blob. The context
         * sending policy uses both forms for the same field.
         */
        fun longs(field: Int): List<Long> {
            val raw = map[field] ?: return emptyList()
            val out = ArrayList<Long>(raw.size)
            for (value in raw) {
                when (value) {
                    is Long -> out.add(value)
                    is ByteArray -> out.addAll(readPackedVarints(value))
                }
            }
            return out
        }
        override fun toString(): String = map.keys.sorted().toString()
    }

    /** Shallow-decode a protobuf message. Unknown wire types stop the scan. */
    /** Reads a packed repeated varint field body. Malformed tails are dropped. */
    fun readPackedVarints(data: ByteArray): List<Long> {
        val out = ArrayList<Long>()
        var off = 0
        while (off < data.size) {
            var v = 0L
            var shift = 0
            var done = false
            while (off < data.size) {
                val b = data[off].toInt() and 0xFF
                off++
                v = v or ((b and 0x7F).toLong() shl shift)
                shift += 7
                if (b and 0x80 == 0) {
                    done = true
                    break
                }
                if (shift > 63) break
            }
            if (!done) break
            out.add(v)
        }
        return out
    }

    fun decode(data: ByteArray): Fields {
        val map = HashMap<Int, MutableList<Any>>()
        var off = 0
        while (off < data.size) {
            var key = 0L
            var shift = 0
            var ok = false
            while (off < data.size) {
                val b = data[off].toInt() and 0xFF
                off++
                key = key or ((b and 0x7F).toLong() shl shift)
                shift += 7
                if (b and 0x80 == 0) {
                    ok = true
                    break
                }
                if (shift > 63) break
            }
            if (!ok) break
            val field = (key ushr 3).toInt()
            when ((key and 7L).toInt()) {
                0 -> {
                    var v = 0L
                    var s = 0
                    var done = false
                    while (off < data.size) {
                        val b = data[off].toInt() and 0xFF
                        off++
                        v = v or ((b and 0x7F).toLong() shl s)
                        s += 7
                        if (b and 0x80 == 0) {
                            done = true
                            break
                        }
                        if (s > 63) break
                    }
                    if (!done) return Fields(map)
                    map.getOrPut(field) { mutableListOf() }.add(v)
                }
                2 -> {
                    var len = 0L
                    var s = 0
                    var done = false
                    while (off < data.size) {
                        val b = data[off].toInt() and 0xFF
                        off++
                        len = len or ((b and 0x7F).toLong() shl s)
                        s += 7
                        if (b and 0x80 == 0) {
                            done = true
                            break
                        }
                        if (s > 63) break
                    }
                    if (!done || len < 0 || off + len > data.size) return Fields(map)
                    map.getOrPut(field) { mutableListOf() }
                        .add(data.copyOfRange(off, off + len.toInt()))
                    off += len.toInt()
                }
                5 -> {
                    if (off + 4 > data.size) return Fields(map)
                    off += 4
                }
                1 -> {
                    if (off + 8 > data.size) return Fields(map)
                    off += 8
                }
                // Groups (3/4) and anything unrecognised: nothing downstream
                // needs them and skipping blind would desynchronise the scan.
                else -> return Fields(map)
            }
        }
        return Fields(map)
    }

    // ---------------------------------------------------------------------
    // UMP framing
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // MP4 segment index
    // ---------------------------------------------------------------------

    /** One segment's place on the timeline, in milliseconds. */
    data class SegmentEntry(val sequence: Int, val startMs: Long, val endMs: Long)

    /**
     * The complete segment timeline for one track.
     *
     * **This is what makes seeking possible.** It comes out of the `sidx` box in
     * the track's initialization segment, which YouTube populates for the whole
     * file, so one init segment describes every segment's start and duration up
     * front. That is what lets the DASH manifest be written before any media is
     * fetched, and therefore what lets the player ask for segment N directly
     * instead of reading forward from segment 1.
     */
    class SegmentIndex(val entries: List<SegmentEntry>) {
        val size: Int get() = entries.size
        fun entry(sequence: Int): SegmentEntry? = entries.getOrNull(sequence - 1)
        fun startMs(sequence: Int): Long = entry(sequence)?.startMs ?: -1
        fun endMs(sequence: Int): Long = entry(sequence)?.endMs ?: -1
        val durationMs: Long get() = entries.lastOrNull()?.endMs ?: 0
    }

    /**
     * Parse the `sidx` box out of an initialization segment.
     *
     * Returns null rather than throwing when the box is absent or malformed, so
     * a track that cannot be indexed falls back rather than failing playback.
     */
    fun parseSegmentIndex(initData: ByteArray): SegmentIndex? = try {
        val offset = findSidx(initData)
        if (offset < 0) null else parseSidx(initData, offset)
    } catch (e: Exception) {
        android.util.Log.w("SabrProtocol", "sidx parse failed: ${e.message}")
        null
    }

    /** Walks the top-level MP4 box list looking for `sidx`. */
    private fun findSidx(data: ByteArray): Int {
        var cursor = 0
        while (cursor + 8 <= data.size) {
            val size = readUint32(data, cursor)
            val type = String(data, cursor + 4, 4, Charsets.US_ASCII)
            if (type == "sidx") return cursor
            // A zero or absurd size means the walk has lost the box structure;
            // scanning on from there would find "sidx" inside media bytes.
            if (size < 8 || cursor + size > data.size) return -1
            cursor += size.toInt()
        }
        return -1
    }

    private fun parseSidx(data: ByteArray, offset: Int): SegmentIndex? {
        var cursor = offset + 8
        val version = data[cursor].toInt() and 0xFF
        cursor += 4                                  // version + flags
        cursor += 4                                  // reference_ID
        val timescale = readUint32(data, cursor)
        cursor += 4
        if (timescale <= 0) return null
        val earliest: Long
        when (version) {
            0 -> {
                earliest = readUint32(data, cursor)
                cursor += 8                          // earliest_presentation_time + first_offset
            }
            1 -> {
                earliest = readUint64(data, cursor)
                cursor += 16
            }
            else -> return null
        }
        cursor += 2                                  // reserved
        val referenceCount = readUint16(data, cursor)
        cursor += 2

        val entries = ArrayList<SegmentEntry>(referenceCount)
        var unscaledStart = earliest
        for (i in 0 until referenceCount) {
            if (cursor + 12 > data.size) break
            val reference = readUint32(data, cursor)
            cursor += 4
            // A nested sidx points at another index instead of media; the
            // reference layout below would be meaningless for it.
            if (reference and 0x80000000L != 0L) return null
            val duration = readUint32(data, cursor)
            cursor += 8                              // subsegment_duration + SAP flags
            val start = unscaledStart * 1000L / timescale
            val end = (unscaledStart + duration) * 1000L / timescale
            entries.add(SegmentEntry(i + 1, start, end))
            unscaledStart += duration
        }
        return if (entries.isEmpty()) null else SegmentIndex(entries)
    }

    private fun readUint16(d: ByteArray, o: Int): Int =
        ((d[o].toInt() and 0xFF) shl 8) or (d[o + 1].toInt() and 0xFF)

    private fun readUint32(d: ByteArray, o: Int): Long =
        ((d[o].toLong() and 0xFF) shl 24) or ((d[o + 1].toLong() and 0xFF) shl 16) or
            ((d[o + 2].toLong() and 0xFF) shl 8) or (d[o + 3].toLong() and 0xFF)

    private fun readUint64(d: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (d[o + i].toLong() and 0xFF)
        return v
    }

    /** UMP part ids. Only the ones this client acts on are named. */
    object Part {
        const val MEDIA_HEADER = 20
        const val MEDIA = 21
        const val MEDIA_END = 22
        const val NEXT_REQUEST_POLICY = 35
        const val FORMAT_INITIALIZATION_METADATA = 42
        const val SABR_REDIRECT = 43
        const val SABR_ERROR = 44
        const val SABR_SEEK = 45
        const val RELOAD_PLAYER_RESPONSE = 46
        const val SABR_CONTEXT_UPDATE = 57
        const val STREAM_PROTECTION_STATUS = 58
        const val SABR_CONTEXT_SENDING_POLICY = 59
    }

    /**
     * UMP's own varint: the high bits of the first byte give the *total* byte
     * length, and the remaining low bits of that byte are the value's most
     * significant part. This is not protobuf's LEB128 - mixing the two silently
     * misframes the whole response.
     *
     * Returns the value and the offset just past it, or `null` if [data] does
     * not hold a complete varint at [off].
     */
    fun readUmpVarInt(data: ByteArray, off: Int): Pair<Long, Int>? {
        if (off >= data.size) return null
        val first = data[off].toInt() and 0xFF
        val size = when {
            first < 128 -> 1
            first < 192 -> 2
            first < 224 -> 3
            first < 240 -> 4
            else -> 5
        }
        if (off + size > data.size) return null
        fun b(i: Int) = data[off + i].toInt() and 0xFF
        val value: Long = when (size) {
            1 -> first.toLong()
            2 -> ((first and 0x3F) + 64 * b(1)).toLong()
            3 -> ((first and 0x1F) + 32 * (b(1) + 256 * b(2))).toLong()
            4 -> ((first and 0x0F) + 16 * (b(1) + 256 * (b(2) + 256 * b(3)))).toLong()
            else -> (b(1).toLong() or (b(2).toLong() shl 8) or
                (b(3).toLong() shl 16) or (b(4).toLong() shl 24))
        }
        return value to (off + size)
    }

    /** One decoded UMP part. [payload] is a view's copy, safe to retain. */
    data class UmpPart(val type: Int, val payload: ByteArray) {
        override fun equals(other: Any?) =
            other is UmpPart && type == other.type && payload.contentEquals(other.payload)

        override fun hashCode() = 31 * type + payload.contentHashCode()
    }

    /**
     * Split a complete UMP body into parts.
     *
     * A truncated trailing part is dropped rather than throwing: responses are
     * read whole here, so a partial tail means a cut connection, and the caller
     * treats a short read as a failed request.
     */
    fun parseUmp(data: ByteArray): List<UmpPart> {
        val parts = ArrayList<UmpPart>(32)
        var off = 0
        while (off < data.size) {
            val (type, afterType) = readUmpVarInt(data, off) ?: break
            val (size, afterSize) = readUmpVarInt(data, afterType) ?: break
            if (size < 0 || afterSize + size > data.size) break
            val end = afterSize + size.toInt()
            parts.add(UmpPart(type.toInt(), data.copyOfRange(afterSize, end)))
            off = end
        }
        return parts
    }

    // ---------------------------------------------------------------------
    // messages
    // ---------------------------------------------------------------------

    /** `FormatId`: 1 itag, 2 lastModified, 3 xtags. */
    data class FormatId(
        val itag: Int,
        val lastModified: Long,
        val xtags: String? = null,
    ) {
        fun encode(): ByteArray = Writer()
            .varint(1, itag)
            .varint(2, lastModified)
            .string(3, xtags)
            .toByteArray()

        /** Key used to bucket media headers by track. */
        val key: String get() = "$itag|$lastModified|${xtags.orEmpty()}"
    }

    /**
     * `timeRange`: ticks over a timescale rather than milliseconds. This is the
     * only timing the server actually sends on a media header.
     */
    data class TimeRange(val startTicks: Long, val durationTicks: Long, val timescale: Long) {
        val startMs: Long get() = if (timescale > 0) startTicks * 1000 / timescale else 0
        val durationMs: Long get() = if (timescale > 0) durationTicks * 1000 / timescale else 0

        companion object {
            fun parse(f: Fields) = TimeRange(
                startTicks = f.long(1) ?: 0,
                durationTicks = f.long(2) ?: 0,
                timescale = f.long(3) ?: 1000,
            )
        }
    }

    /**
     * `MEDIA_HEADER`. [durationMs] comes from [TimeRange]; the nominal
     * `startMs`/`durationMs` fields are not populated by the server.
     */
    data class MediaHeader(
        val headerId: Int,
        val itag: Int,
        val lastModified: Long,
        val isInitSegment: Boolean,
        val sequenceNumber: Int,
        val startRange: Long,
        val contentLength: Long,
        val startMs: Long,
        val durationMs: Long,
    ) {
        companion object {
            fun parse(payload: ByteArray): MediaHeader {
                val f = decode(payload)
                // itag is normally field 3, but fall back to the formatId
                // submessage (13) so a response that only carries the latter
                // still routes to the right track.
                val formatId = f.message(13)
                val itag = f.int(3) ?: formatId?.int(1) ?: 0
                val lmt = f.long(4) ?: formatId?.long(2) ?: 0
                val range = f.message(15)?.let { TimeRange.parse(it) }
                return MediaHeader(
                    headerId = f.int(1) ?: 0,
                    itag = itag,
                    lastModified = lmt,
                    isInitSegment = (f.long(8) ?: 0L) != 0L,
                    sequenceNumber = f.int(9) ?: 0,
                    startRange = f.long(6) ?: 0,
                    contentLength = f.long(14) ?: 0,
                    startMs = range?.startMs ?: 0,
                    durationMs = range?.durationMs ?: 0,
                )
            }
        }
    }

    /** `FORMAT_INITIALIZATION_METADATA`: which track the next segments belong to. */
    data class FormatInit(
        val videoId: String?,
        val itag: Int,
        val lastModified: Long,
        val durationMs: Long,
        val totalSegments: Int,
        val mimeType: String?,
    ) {
        companion object {
            fun parse(payload: ByteArray): FormatInit {
                val f = decode(payload)
                val formatId = f.message(2)
                return FormatInit(
                    videoId = f.string(1),
                    itag = formatId?.int(1) ?: 0,
                    lastModified = formatId?.long(2) ?: 0,
                    durationMs = f.long(3) ?: 0,
                    totalSegments = f.int(4) ?: 0,
                    mimeType = f.string(5),
                )
            }
        }
    }

    /**
     * A contiguous run of segments the client already holds, per track.
     *
     * [durationMs] must be real. A zero-duration range is how the server
     * decides the client has nothing and restarts from segment 1.
     */
    data class BufferedRange(
        val formatId: FormatId,
        val startTimeMs: Long,
        val durationMs: Long,
        val startSegmentIndex: Int,
        val endSegmentIndex: Int,
    ) {
        /**
         * **Field 6 is the one that counts.** The flat [startTimeMs] and
         * [durationMs] at 2 and 3 are the documented shape and the server does
         * not page from them; it reads the `timeRange` submessage, exactly as
         * `MediaHeader` reports timing only through its own `timeRange` and
         * never through the `startMs`/`durationMs` it defines. Sending 1-5 and
         * omitting 6 is what makes the server answer a byte-identical response
         * to every request in a session: it can see which segments are claimed
         * but not how much media that is, so it treats the client as empty.
         *
         * Milliseconds are used directly as ticks, with the timescale declared
         * as 1000, which is what the reference client does.
         */
        fun encode(): ByteArray {
            val timeRange = Writer()
                .varintAlways(1, startTimeMs)
                .varintAlways(2, durationMs)
                .varintAlways(3, TIMESCALE_MS)
                .toByteArray()
            return Writer()
                .message(1, formatId.encode())
                .varintAlways(2, startTimeMs)
                .varintAlways(3, durationMs)
                .varintAlways(4, startSegmentIndex)
                .varintAlways(5, endSegmentIndex)
                .message(6, timeRange)
                .toByteArray()
        }
    }

    /** Ticks per second when timings are carried as plain milliseconds. */
    const val TIMESCALE_MS = 1000

    /** Identifies the app to the streaming server. Mirrors the `/player` client. */
    data class ClientInfo(
        val clientNameId: Int,
        val clientVersion: String,
        val hl: String = "en-US",
        val gl: String = "US",
    ) {
        /**
         * Locale at 21/22 rather than an OS name and version at 18/19. Those
         * describe a native client; MWEB is a browser client and the reference
         * implementation sends the locale pair instead. The declared client
         * must match the one whose `/player` response produced the streaming
         * URL, or the server will not serve the session.
         */
        fun encode(): ByteArray = Writer()
            .varint(16, clientNameId)
            .string(17, clientVersion)
            .string(21, hl)
            .string(22, gl)
            .toByteArray()
    }

    /**
     * Build a `VideoPlaybackAbrRequest`.
     *
     * @param playerTimeMs how far playback has reached; the server pages the
     *   stream against this plus [bufferedRanges], so it must advance or the
     *   same segments come back.
     * @param playbackCookie the opaque blob from the previous response's
     *   `NEXT_REQUEST_POLICY` (field 7), echoed back to keep session continuity.
     */
    fun buildAbrRequest(
        playerTimeMs: Long,
        stickyResolution: Int,
        videoFormat: FormatId?,
        audioFormat: FormatId?,
        ustreamerConfig: ByteArray,
        poToken: ByteArray?,
        clientInfo: ClientInfo,
        bufferedRanges: List<BufferedRange>,
        playbackCookie: ByteArray?,
        sabrContexts: Map<Int, ByteArray> = emptyMap(),
        unsentContextTypes: Collection<Int> = emptyList(),
    ): ByteArray {
        // The playback half of the state is sent once there is any playback to
        // describe. A first request carries none of it, which is how the server
        // tells a cold start from a client that has fallen behind.
        val describesPlayback = playerTimeMs > 0 || bufferedRanges.isNotEmpty()

        val height = maxOf(stickyResolution, 360)
        val abrState = Writer()
            // 18/19 are the last known viewport the server sizes against, and
            // the reference client sends them only once playback is under way.
            // Width is derived 16:9 rather than tracked: the sticky resolution
            // below is what actually pins the ladder, and these are a hint.
            .varint(18, if (describesPlayback) maxOf(height * 16 / 9, 640) else 0)
            .varint(19, if (describesPlayback) height else 0)
            .varint(21, height)
            .varint(28, playerTimeMs)
            .varint(34, 1)          // visibility
            .float(35, 1.0f)        // playbackRate
            .toByteArray()

        val streamerContext = Writer()
            .message(1, clientInfo.encode())
            .bytes(2, poToken)
            .bytes(3, playbackCookie)
        // **Fields 5 and 6 are the session's memory.** The server hands out
        // opaque context blobs mid-session and expects the active ones back on
        // every subsequent request; a client that drops them looks like a
        // client that lost its session, and the server answers by demanding a
        // player reload (part 46) rather than by erroring. 6 declares the types
        // held but not yet echoed, so the server knows they are not lost.
        for ((type, value) in sabrContexts) {
            streamerContext.message(
                5,
                Writer().varintAlways(1, type).bytes(2, value).toByteArray(),
            )
        }
        for (type in unsentContextTypes) streamerContext.varintAlways(6, type)

        val w = Writer().message(1, abrState)
        if (describesPlayback) {
            audioFormat?.let { w.message(2, it.encode()) }
            videoFormat?.let { w.message(2, it.encode()) }
            for (range in bufferedRanges) w.message(3, range.encode())
            w.varintAlways(4, playerTimeMs)
        }
        w.bytes(5, ustreamerConfig)
        audioFormat?.let { w.message(16, it.encode()) }
        videoFormat?.let { w.message(17, it.encode()) }
        w.message(19, streamerContext.toByteArray())
        return w.toByteArray()
    }
}
