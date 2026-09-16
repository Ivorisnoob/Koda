package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrAttestationException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrCancelledException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrHttpException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrIncompleteMediaException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrReloadException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrServerErrorException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrStaleDescriptorException
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSpool
import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId
import com.ivor.ivormusic.data.youtube.sabr.model.SabrIdentity
import com.ivor.ivormusic.data.youtube.sabr.protocol.SabrProto
import com.ivor.ivormusic.data.youtube.sabr.session.SabrCall
import com.ivor.ivormusic.data.youtube.sabr.session.SabrExchange
import com.ivor.ivormusic.data.youtube.sabr.session.SabrHttpResponse
import com.ivor.ivormusic.data.youtube.sabr.session.SabrRequest
import com.ivor.ivormusic.data.youtube.sabr.session.SabrRequestEncoder
import com.ivor.ivormusic.data.youtube.sabr.session.SabrSession
import com.ivor.ivormusic.data.youtube.sabr.session.SabrSessionLimits
import com.ivor.ivormusic.data.youtube.sabr.session.SabrTrack
import com.ivor.ivormusic.data.youtube.sabr.session.SabrTransport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SabrSessionTest {
    @get:Rule val temporary = TemporaryFolder()

    private val audio = SabrFormat(SabrFormatId(140, 7, "xt", "en.4"), "audio/mp4", 128_000, 3000, isDrc = true)
    private val video = SabrFormat(SabrFormatId(248, 9), "video/webm", 2_000_000, 3000, 1920, 1080)
    private val identity = SabrIdentity("profile", 1, 1)
    private val token = byteArrayOf(9, 8, 7)
    private val config = byteArrayOf(1, 2, 3, 4)

    private fun descriptor(url: String = "https://rr1.googlevideo.com/videoplayback?c=MWEB&id=1", expires: Long = Long.MAX_VALUE) =
        SabrDescriptor("video", "cpn-1", "2.20260901", "visitor", url,
            Base64.getUrlEncoder().withoutPadding().encodeToString(config), identity, 0, expires, listOf(audio, video), token)

    private inner class FakeTransport(vararg responses: () -> SabrHttpResponse) : SabrTransport {
        val queue = ArrayDeque(responses.toList())
        val urls = mutableListOf<String>()
        val bodies = mutableListOf<ByteArray>()
        val headers = mutableListOf<Map<String, String>>()
        var cancelled = 0

        override fun newCall(url: String, headers: Map<String, String>, body: ByteArray): SabrCall {
            urls += url; bodies += body; this.headers += headers
            return object : SabrCall {
                override fun execute() = queue.removeFirst()()
                override fun cancel() { cancelled++ }
            }
        }
    }

    private fun session(transport: SabrTransport, descriptor: SabrDescriptor = descriptor(),
                        currentIdentity: () -> SabrIdentity = { identity },
                        limits: SabrSessionLimits = SabrSessionLimits(emptyResponseRetryMs = 5, maxContinuousWaitMs = 2_000),
                        now: () -> Long = { 1 }) =
        SabrSession(descriptor, transport, SabrSpool(temporary.newFolder()), currentIdentity, limits, now)

    private val audioOnly = SabrRequest.playback(0, 1f, listOf(SabrTrack(audio)))

    @Test fun `audio only body carries real speed, token, cookie and server contexts`() {
        val transport = FakeTransport(
            { ump(policy(cookie = bytes(42, 43)) + contextUpdate(5, bytes(6), sendByDefault = true) +
                contextUpdate(8, bytes(9), sendByDefault = false) + segment(1)) },
            { ump(segment(1)) },
        )
        val session = session(transport)
        val delivered = mutableListOf<Int>()
        assertEquals(1, (session.execute(audioOnly) { it.use { s -> delivered += s.header.itag } } as SabrExchange.Completed).segments)
        val second = SabrRequest.playback(4_000, 1.5f, listOf(SabrTrack(audio)))
        session.execute(second) { it.use { s -> delivered += s.header.itag } }
        assertEquals(listOf(140, 140), delivered)

        val first = fields(transport.bodies[0])
        assertArrayEquals(config, first.getValue(5).single().bytes)
        assertFalse("initial request at zero sends no playback state", 4 in first)
        val body = fields(transport.bodies[1])
        val state = fields(body.getValue(1).single().bytes)
        assertEquals(1L, state.getValue(40).single().varint)
        assertEquals(1.5f, java.lang.Float.intBitsToFloat(SabrProto.asFixed32LittleEndian(state.getValue(35).single().bytes)))
        assertEquals(1L, state.getValue(46).single().varint)
        assertEquals("en.4", state.getValue(69).single().string)
        assertFalse("audio-only has no viewport", 18 in state || 21 in state)
        assertEquals(4_000L, body.getValue(4).single().varint)
        assertTrue(16 in body && 17 !in body)
        val selected = fields(body.getValue(2).single().bytes)
        assertEquals(140L, selected.getValue(1).single().varint)
        assertEquals("xt", selected.getValue(3).single().string)

        val context = fields(body.getValue(19).single().bytes)
        assertArrayEquals(token, context.getValue(2).single().bytes)
        assertArrayEquals(bytes(42, 43), context.getValue(3).single().bytes)
        val active = fields(context.getValue(5).single().bytes)
        assertEquals(5L, active.getValue(1).single().varint)
        assertEquals(listOf(8L), context.getValue(6).map { it.varint })
        val client = fields(context.getValue(1).single().bytes)
        assertEquals(2L, client.getValue(16).single().varint)
        assertEquals("2.20260901", client.getValue(17).single().string)

        assertTrue(transport.urls[0].endsWith("&alr=yes&cpn=cpn-1&rn=0"))
        assertTrue(transport.urls[1].endsWith("&rn=1"))
        assertEquals(SabrRequestEncoder.MWEB_USER_AGENT, transport.headers[0]["User-Agent"])
        assertEquals(2, session.requestNumber)
    }

    @Test fun `buffered range is the honest contiguous run in timeline time`() {
        val timeline = SabrFormatTimeline.parse(audio, sidx(intArrayOf(1000, 1000, 1000)))
        val transport = FakeTransport({ ump(segment(3)) })
        val request = SabrRequest.playback(1_500, 1f, listOf(SabrTrack(audio, timeline, 2..3), SabrTrack(video)))
        session(transport).execute(request) { it.close() }
        val body = fields(transport.bodies[0])
        val range = fields(body.getValue(3).single().bytes)
        assertEquals(1000L, range.getValue(2).single().varint)
        assertEquals(2000L, range.getValue(3).single().varint)
        assertEquals(2L, range.getValue(4).single().varint)
        assertEquals(3L, range.getValue(5).single().varint)
        assertEquals(2, body.getValue(2).size)
        assertFalse("both tracks enabled", 40 in fields(body.getValue(1).single().bytes))
        assertThrows(IllegalArgumentException::class.java) { SabrTrack(audio, timeline, 0..1) }
        assertThrows(IllegalArgumentException::class.java) { SabrTrack(audio, null, 1..1) }
        assertThrows(IllegalArgumentException::class.java) { SabrRequest.playback(0, 0f, listOf(SabrTrack(audio))) }
        assertThrows(IllegalArgumentException::class.java) { SabrRequest.playback(0, 1f, listOf(SabrTrack(audio), SabrTrack(audio))) }
    }

    @Test fun `streaming URLs must be googlevideo https minted for MWEB, including redirects`() {
        listOf("http://rr1.googlevideo.com/v?c=MWEB", "https://googlevideo.com.evil.test/v",
            "https://rr1.googlevideo.com/v?c=ANDROID").forEach {
            assertThrows(SabrProtocolException::class.java) { session(FakeTransport(), descriptor(it)) }
        }
        val good = "https://rr9.googlevideo.com/videoplayback?c=MWEB&rn=4&alr=no"
        val transport = FakeTransport({ ump(redirect(good)) }, { ump(segment(1)) }, { ump(redirect("https://example.com/")) })
        val session = session(transport)
        assertTrue((session.execute(audioOnly) { it.close() } as SabrExchange.Completed).redirected)
        session.execute(audioOnly) { it.close() }
        assertEquals("https://rr9.googlevideo.com/videoplayback?c=MWEB&alr=no&cpn=cpn-1&rn=1", transport.urls[1])
        assertThrows(SabrProtocolException::class.java) { session.execute(audioOnly) { it.close() } }
    }

    @Test fun `redirects without media are bounded`() {
        val url = "https://rr2.googlevideo.com/videoplayback"
        val transport = FakeTransport(*Array(4) { { ump(redirect(url)) } })
        val session = session(transport)
        repeat(3) { session.execute(audioOnly) { it.close() } }
        assertThrows(SabrProtocolException::class.java) { session.execute(audioOnly) { it.close() } }
    }

    @Test fun `incomplete responses retry within a bound and never reuse a request number`() {
        val truncated = { ump(header(1, 4) + part(21, bytes(1, 1, 2))) }
        val transport = FakeTransport(truncated, { ump(segment(1)) }, truncated, truncated, truncated)
        val session = session(transport)
        assertEquals(SabrExchange.Incomplete(1), session.execute(audioOnly) { it.close() })
        assertTrue(session.execute(audioOnly) { it.close() } is SabrExchange.Completed)
        assertEquals(SabrExchange.Incomplete(1), session.execute(audioOnly) { it.close() })
        assertEquals(SabrExchange.Incomplete(2), session.execute(audioOnly) { it.close() })
        assertThrows(SabrIncompleteMediaException::class.java) { session.execute(audioOnly) { it.close() } }
        assertEquals((0..4).map { "rn=$it" }, transport.urls.map { it.substringAfterLast('&') })
    }

    @Test fun `request loop recovers from truncation and waits out server backoff without extra calls`() {
        val transport = FakeTransport(
            { ump(header(1, 4) + part(21, bytes(1, 1))) },
            { ump(policy(backoffMs = 60)) },
            { ump(segment(1)) },
        )
        val session = session(transport)
        val started = System.nanoTime()
        val result = session.request(audioOnly, { it.close() })
        assertEquals(1, result.segments)
        assertEquals(3, transport.urls.size)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 60)

        val deferred = FakeTransport({ ump(policy(backoffMs = 5_000)) })
        val waiting = session(deferred)
        waiting.execute(audioOnly) { it.close() }
        assertTrue(waiting.execute(audioOnly) { it.close() } is SabrExchange.Deferred)
        assertEquals(1, deferred.urls.size)
        assertThrows(SabrProtocolException::class.java) {
            session(FakeTransport({ ump(policy(backoffMs = 30_001)) })).execute(audioOnly) { it.close() }
        }
    }

    @Test fun `continuous no-progress is bounded`() {
        val transport = FakeTransport(*Array(200) { { ump(policy()) } })
        val limits = SabrSessionLimits(emptyResponseRetryMs = 20, maxContinuousWaitMs = 100)
        assertThrows(IOException::class.java) { session(transport, limits = limits).request(audioOnly, { it.close() }) }
        assertTrue(transport.urls.size in 2..10)
    }

    @Test fun `server controls map to typed failures`() {
        assertEquals(403, assertThrows(SabrHttpException::class.java) {
            session(FakeTransport({ SabrHttpResponse(403, "text/html", ByteArrayInputStream(ByteArray(0))) })).execute(audioOnly) { it.close() }
        }.status)
        assertThrows(SabrProtocolException::class.java) {
            session(FakeTransport({ SabrHttpResponse(200, "text/html", ByteArrayInputStream(ByteArray(0))) })).execute(audioOnly) { it.close() }
        }
        val error = assertThrows(SabrServerErrorException::class.java) {
            session(FakeTransport({ ump(part(44, SabrProto.Writer().apply { writeStringIfNotEmpty(1, "sabr.config"); writeUInt64(2, 3) }.toByteArray())) }))
                .execute(audioOnly) { it.close() }
        }
        assertEquals("sabr.config" to 3, error.type to error.code)
        assertThrows(SabrReloadException::class.java) { session(FakeTransport({ ump(part(46, ByteArray(0))) })).execute(audioOnly) { it.close() } }
        assertThrows(SabrAttestationException::class.java) { session(FakeTransport({ ump(protection(3)) })).execute(audioOnly) { it.close() } }
        assertThrows(SabrProtocolException::class.java) { session(FakeTransport({ ump(part(31, ByteArray(0)) + segment(1)) })).execute(audioOnly) { it.close() } }

        val pending = FakeTransport({ ump(protection(2)) }, { ump(protection(2) + segment(1)) }, { ump(protection(2)) }, { ump(protection(2)) }, { ump(protection(2)) })
        val session = session(pending)
        repeat(4) { session.execute(audioOnly) { it.close() } }
        assertThrows(SabrAttestationException::class.java) { session.execute(audioOnly) { it.close() } }
    }

    @Test fun `malformed optional control is skipped while media and later policy still apply`() {
        val invalidWireType = bytes(0x3e, 1)
        val transport = FakeTransport(
            { ump(part(35, invalidWireType) + segment(1) + contextUpdate(4, bytes(1), true) + contextUpdate(6, bytes(2), true)) },
            { ump(part(59, SabrProto.Writer().apply { writeUInt64(2, 4); writeUInt64(3, 6) }.toByteArray()) + segment(1)) },
            { ump(segment(1)) },
        )
        val session = session(transport)
        assertEquals(1, (session.execute(audioOnly) { it.close() } as SabrExchange.Completed).segments)
        session.execute(audioOnly) { it.close() }
        session.execute(audioOnly) { it.close() }
        val second = fields(fields(transport.bodies[1]).getValue(19).single().bytes)
        assertEquals(2, second.getValue(5).size)
        val third = fields(fields(transport.bodies[2]).getValue(19).single().bytes)
        assertFalse("stopped and discarded contexts are not sent", 5 in third)
        assertEquals(listOf(4L), third.getValue(6).map { it.varint })
    }

    @Test fun `stale identity or expired descriptor sends nothing`() {
        val transport = FakeTransport()
        var current = identity
        val session = session(transport, currentIdentity = { current })
        current = identity.copy(loginGeneration = 2)
        assertThrows(SabrStaleDescriptorException::class.java) { session.execute(audioOnly) { it.close() } }
        assertThrows(SabrStaleDescriptorException::class.java) {
            session(transport, descriptor(expires = 10), now = { 10 }).execute(audioOnly) { it.close() }
        }
        assertTrue(transport.urls.isEmpty())
    }

    @Test fun `close aborts a blocked body read and cleans partial media`() {
        val reading = CountDownLatch(1)
        val released = CountDownLatch(1)
        val blocking = object : InputStream() {
            val prefix = ByteArrayInputStream(header(1, 1_000) + bytes(21, 0x80 or (1001 and 63), 1001 ushr 6, 1, 5))
            override fun read(): Int {
                val next = prefix.read()
                if (next >= 0) return next
                reading.countDown()
                if (!released.await(5, TimeUnit.SECONDS)) error("close did not cancel the call")
                throw IOException("Canceled")
            }
        }
        val folder = temporary.newFolder()
        var cancelled = false
        val transport = object : SabrTransport {
            override fun newCall(url: String, headers: Map<String, String>, body: ByteArray) = object : SabrCall {
                override fun execute() = SabrHttpResponse(200, "application/vnd.yt-ump", blocking)
                override fun cancel() { cancelled = true; released.countDown() }
            }
        }
        val spool = SabrSpool(folder)
        val session = SabrSession(descriptor(), transport, spool, { identity })
        var failure: Throwable? = null
        val worker = Thread { try { session.execute(audioOnly) { it.close() } } catch (e: Throwable) { failure = e } }
        worker.start()
        assertTrue(reading.await(5, TimeUnit.SECONDS))
        session.close()
        worker.join(5_000)
        assertTrue(cancelled)
        assertTrue(failure is SabrCancelledException)
        assertEquals(0, spool.bytes)
        assertEquals(0, folder.listFiles()!!.size)
        assertThrows(SabrCancelledException::class.java) { session.execute(audioOnly) { it.close() } }
    }

    @Test fun `close wakes a backoff wait promptly`() {
        val transport = FakeTransport({ ump(policy(backoffMs = 20_000)) })
        val session = session(transport, limits = SabrSessionLimits())
        session.execute(audioOnly) { it.close() }
        var failure: Throwable? = null
        val worker = Thread { try { session.request(audioOnly, { it.close() }) } catch (e: Throwable) { failure = e } }
        val started = System.nanoTime()
        worker.start()
        Thread.sleep(50)
        session.close()
        worker.join(2_000)
        assertFalse(worker.isAlive)
        assertTrue(failure is SabrCancelledException)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000)
        assertEquals(1, transport.urls.size)
    }

    // --- wire helpers ---

    private fun ump(parts: ByteArray) = SabrHttpResponse(200, "application/vnd.yt-ump", ByteArrayInputStream(parts))
    private fun segment(sequence: Int, id: Int = 1) = header(id, 2, sequence) + part(21, bytes(id, 7, 8)) + part(22, bytes(id))
    private fun header(id: Int, length: Int, sequence: Int = 1) = part(20, SabrProto.Writer().apply {
        writeUInt64(1, id.toLong()); writeUInt64(3, 140); writeUInt64(4, 7); writeUInt64(9, sequence.toLong()); writeUInt64(14, length.toLong())
    }.toByteArray())
    private fun policy(backoffMs: Int? = null, cookie: ByteArray? = null) = part(35, SabrProto.Writer().apply {
        backoffMs?.let { writeUInt64(4, it.toLong()) }; cookie?.let { writeBytes(7, it) }
    }.toByteArray())
    private fun contextUpdate(type: Int, value: ByteArray, sendByDefault: Boolean) = part(57, SabrProto.Writer().apply {
        writeUInt64(1, type.toLong()); writeBytes(3, value); writeBool(4, sendByDefault)
    }.toByteArray())
    private fun redirect(url: String) = part(43, SabrProto.Writer().apply { writeStringIfNotEmpty(1, url) }.toByteArray())
    private fun protection(status: Int) = part(58, SabrProto.Writer().apply { writeUInt64(1, status.toLong()) }.toByteArray())
    private fun part(type: Int, data: ByteArray): ByteArray {
        val size = data.size
        val length = when {
            size < 128 -> bytes(size)
            size < 16384 -> bytes(128 or (size and 63), size ushr 6)
            else -> bytes(192 or (size and 31), size ushr 5, size ushr 13)
        }
        return bytes(type) + length + data
    }
    private fun fields(data: ByteArray) = SabrProto.readFields(data).groupBy { it.number }
    private fun sidx(durations: IntArray): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).apply {
            writeInt(0); writeInt(1); writeInt(1000); writeInt(0); writeInt(0)
            writeShort(0); writeShort(durations.size)
            durations.forEach { writeInt(100); writeInt(it); writeInt(0) }
        }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply { writeInt(payload.size() + 8); writeBytes("sidx"); write(payload.toByteArray()) }
        return out.toByteArray()
    }
    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
}
