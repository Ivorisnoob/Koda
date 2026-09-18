package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.bridge.SabrBridge
import com.ivor.ivormusic.data.youtube.sabr.bridge.SabrPlaybackSpec
import com.ivor.ivormusic.data.youtube.sabr.bridge.SabrSegmentRef
import com.ivor.ivormusic.data.youtube.sabr.bridge.contiguousBuffered
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId
import com.ivor.ivormusic.data.youtube.sabr.model.SabrIdentity
import com.ivor.ivormusic.data.youtube.sabr.protocol.SabrProto
import com.ivor.ivormusic.data.youtube.sabr.session.SabrCall
import com.ivor.ivormusic.data.youtube.sabr.session.SabrHttpResponse
import com.ivor.ivormusic.data.youtube.sabr.session.SabrSession
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSpool
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Base64
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SabrBridgeTest {
    @get:Rule val temporary = TemporaryFolder()

    private val audio = SabrFormat(SabrFormatId(140, 7), "audio/mp4", 128_000, 3000)
    private val identity = SabrIdentity("profile", 1, 1)

    @Test fun `spec keys init slots and durations`() {
        val spec = SabrPlaybackSpec("video", audio)
        val initKey = spec.refFor("sabrseg://a0/init").cacheKey("video")
        val mediaKey = spec.refFor("sabrseg://a0/3").cacheKey("video")
        assertTrue(initKey.endsWith(":init") && ":140:" in initKey)
        assertTrue(mediaKey.endsWith(":3") && mediaKey != initKey)
        assertEquals(audio, spec.refFor("sabrseg://a0/1").format)
        assertEquals(3000L, spec.durationMs())
        assertTrue(spec.putInitData(audio, byteArrayOf(1)))
        assertFalse(spec.putInitData(audio, byteArrayOf(2)))
        assertArrayEquals(byteArrayOf(1), spec.initData(audio))
        assertThrows(IllegalArgumentException::class.java) { spec.refFor("https://a0/1") }
        assertThrows(IllegalArgumentException::class.java) { spec.refFor("sabrseg://b0/1") }
        assertThrows(IllegalArgumentException::class.java) { spec.refFor("sabrseg://a0/0") }
        assertThrows(IllegalArgumentException::class.java) { spec.refFor("sabrseg://a0/init/extra") }
    }

    @Test fun `buffered range is the honest contiguous run`() {
        assertNull(contiguousBuffered(emptySet(), 2))
        assertNull(contiguousBuffered(setOf(1, 2), 0))
        assertNull(contiguousBuffered(setOf(2, 3), 1))
        assertEquals(1..3, contiguousBuffered(setOf(1, 2, 3, 5), 3))
        assertEquals(3..3, contiguousBuffered(setOf(1, 3), 3))
    }

    @Test fun `prepare then demand serves spool files with one request each`() {
        val transport = FakeTransport(
            { ump(initPart(140, sidx(intArrayOf(1000, 1000, 1000)))) },
            { ump(mediaPart(140, 1, byteArrayOf(7, 8))) },
            { ump(mediaPart(140, 1, byteArrayOf(9))) },
        )
        val bridge = SabrBridge(session(transport), SabrPlaybackSpec("video", audio))
        assertFalse(bridge.hasTimelines())
        bridge.prepareTimelines(0)
        assertTrue(bridge.hasTimelines())
        assertEquals(3, bridge.timeline(audio).endSequence)

        val ref = SabrSegmentRef(audio, 1)
        assertArrayEquals(byteArrayOf(7, 8), bridge.awaitSegment(ref).open().use { it.readBytes() })
        assertEquals(2, transport.calls)
        // Ahead-cache hit: no new request.
        bridge.awaitSegment(ref).close()
        assertEquals(2, transport.calls)
        // Discard forces exactly one re-request.
        bridge.discard(ref)
        assertArrayEquals(byteArrayOf(9), bridge.awaitSegment(ref).open().use { it.readBytes() })
        assertEquals(3, transport.calls)
        bridge.stop()
    }

    @Test fun `demand past the timeline fails fast`() {
        val transport = FakeTransport(
            { ump(initPart(140, sidx(intArrayOf(1000)))) },
        )
        val bridge = SabrBridge(session(transport), SabrPlaybackSpec("video", audio))
        bridge.prepareTimelines(0)
        assertThrows(IllegalStateException::class.java) {
            bridge.awaitSegment(SabrSegmentRef(audio, 5))
        }
        bridge.stop()
    }

    @Test fun `undelivered media surfaces as retryable IO`() {
        val transport = FakeTransport(
            { ump(initPart(140, sidx(intArrayOf(1000, 1000)))) },
            // Init for an unknown itag: accepted by the session, dropped by the bridge.
            { ump(mediaPart(999, 1, byteArrayOf(1))) },
        )
        val bridge = SabrBridge(session(transport), SabrPlaybackSpec("video", audio))
        bridge.prepareTimelines(0)
        assertThrows(IOException::class.java) { bridge.awaitSegment(SabrSegmentRef(audio, 1)) }
        bridge.stop()
    }

    private fun descriptor() = SabrDescriptor(
        "video", "cpn-1", "2.20260901", "visitor",
        "https://rr1.googlevideo.com/videoplayback?c=MWEB&id=1",
        Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(1, 2, 3)),
        identity, 0, Long.MAX_VALUE, listOf(audio), byteArrayOf(9))

    private fun session(transport: FakeTransport) =
        SabrSession(descriptor(), transport, SabrSpool(temporary.newFolder()), { identity })

    private inner class FakeTransport(vararg responses: () -> SabrHttpResponse) :
        com.ivor.ivormusic.data.youtube.sabr.session.SabrTransport {
        private val queue = ArrayDeque(responses.toList())
        var calls = 0
        override fun newCall(url: String, headers: Map<String, String>, body: ByteArray): SabrCall {
            calls++
            return object : SabrCall {
                override fun execute() = queue.removeFirst()()
                override fun cancel() = Unit
            }
        }
    }

    private fun ump(parts: ByteArray) =
        SabrHttpResponse(200, "application/vnd.yt-ump", ByteArrayInputStream(parts))

    private fun initPart(itag: Int, init: ByteArray) =
        part(20, SabrProto.Writer().apply {
            writeUInt64(1, 1); writeUInt64(3, itag.toLong()); writeUInt64(8, 1)
        }.toByteArray()) + part(21, byteArrayOf(1) + init) + part(22, byteArrayOf(1))

    private fun mediaPart(itag: Int, sequence: Int, media: ByteArray) =
        part(20, SabrProto.Writer().apply {
            writeUInt64(1, 1); writeUInt64(3, itag.toLong()); writeUInt64(9, sequence.toLong())
        }.toByteArray()) + part(21, byteArrayOf(1) + media) + part(22, byteArrayOf(1))

    private fun part(type: Int, data: ByteArray): ByteArray {
        val size = data.size
        val length = when {
            size < 128 -> byteArrayOf(size.toByte())
            size < 16384 -> byteArrayOf((128 or (size and 63)).toByte(), (size ushr 6).toByte())
            else -> byteArrayOf((192 or (size and 31)).toByte(), (size ushr 5).toByte(), (size ushr 13).toByte())
        }
        return byteArrayOf(type.toByte()) + length + data
    }

    private fun sidx(durations: IntArray): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).apply {
            writeInt(0); writeInt(1); writeInt(1000); writeInt(0); writeInt(0)
            writeShort(0); writeShort(durations.size)
            durations.forEach { writeInt(100); writeInt(it); writeInt(0) }
        }
        val body = payload.toByteArray()
        return ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(body.size + 8); writeBytes("sidx"); write(body)
            }
        }.toByteArray()
    }
}
