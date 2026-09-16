package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId
import com.ivor.ivormusic.data.youtube.sabr.protocol.SabrProto
import com.ivor.ivormusic.data.youtube.sabr.protocol.UmpReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.InterruptedIOException
import org.junit.Assert.*
import org.junit.Test

class SabrWireTest {
    @Test fun `format id matches independent protobuf golden bytes`() {
        assertArrayEquals(bytes(8, 251, 1, 16, 123, 26, 1, 120),
            SabrProto.formatId(SabrFormatId(251, 123, "x", "not-a-wire-field")))
    }

    @Test fun `protobuf decodes every supported wire type and unsigned bits`() {
        val writer = SabrProto.Writer().apply {
            writeUInt64(1, -1L)
            writeFixed64(2, 0x0102030405060708L)
            writeBytes(3, bytes(4, 5))
            writeFloat(4, 1.0f)
        }
        val fields = SabrProto.readFields(writer.toByteArray())
        assertEquals(-1L, fields[0].varint)
        assertArrayEquals(bytes(8, 7, 6, 5, 4, 3, 2, 1), fields[1].bytes)
        assertArrayEquals(bytes(4, 5), fields[2].bytes)
        assertEquals(1.0f.toRawBits(), SabrProto.asFixed32LittleEndian(fields[3].bytes))
    }

    @Test fun `protobuf rejects invalid tags and overflowing lengths before allocation`() {
        val invalid = listOf(
            bytes(0), bytes(11), // zero tag, unsupported group
            varint(1L shl 32) + bytes(0), // oversized field number
            bytes(10) + varint(1L shl 32), // length must not wrap to zero
            bytes(10) + varint(Int.MAX_VALUE.toLong()),
            bytes(10, 4, 1), bytes(9, 1), bytes(13, 1), bytes(8, 128),
            bytes(8) + ByteArray(9) { 255.toByte() } + bytes(2),
            ByteArray(SabrProto.MAX_MESSAGE_BYTES + 1),
        )
        invalid.forEach { data ->
            assertThrows(SabrProtocolException::class.java) { SabrProto.readFields(data) }
        }
    }

    @Test fun `protobuf caps metadata fanout and diagnostics omit values`() {
        val repeated = ByteArray(65537 * 2) { if (it % 2 == 0) 8 else 0 }
        assertThrows(SabrProtocolException::class.java) { SabrProto.readFields(repeated) }
        assertThrows(SabrProtocolException::class.java) { SabrProto.readPackedVarints(ByteArray(65537)) }
        val writer = SabrProto.Writer().apply { writeUInt64(1, 123456789); writeStringIfNotEmpty(2, "secret") }
        val summary = SabrProto.summarizeFields(writer.toByteArray())
        assertFalse(summary.contains("123456789"))
        assertFalse(summary.contains("secret"))
        assertThrows(IllegalArgumentException::class.java) { SabrProto.Writer().writeUInt64(0, 1) }
    }

    @Test fun `UMP integer widths and boundaries match buffered and streaming readers`() {
        val types = listOf(0, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456, Int.MAX_VALUE)
        val data = types.flatMap { (umpInt(it) + bytes(1, 42)).toList() }.toByteArray()
        assertEquals(types, UmpReader.readAll(data).map { it.type })
        val observed = mutableListOf<Int>()
        val fragmented = object : ByteArrayInputStream(data) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(1, len))
        }
        UmpReader.readStreaming(fragmented) { type, payload ->
            observed += type
            assertArrayEquals(bytes(42), payload)
        }
        assertEquals(types, observed)
    }

    @Test fun `UMP distinguishes clean EOF from truncated integer or payload`() {
        UmpReader.readPayloadsUntil(ByteArrayInputStream(byteArrayOf())) { _, _, _ -> fail(); true }
        listOf(bytes(128), bytes(1), bytes(1, 128), bytes(1, 3, 42)).forEach { data ->
            assertThrows(EOFException::class.java) {
                UmpReader.readPayloadsUntil(ByteArrayInputStream(data)) { _, _, _ -> true }
            }
        }
        assertThrows(SabrProtocolException::class.java) {
            UmpReader.readAll(bytes(1, 240, 255, 255, 255, 127))
        }
    }

    @Test fun `UMP drains unconsumed payload but stops at requested boundary`() {
        val source = ByteArrayInputStream(bytes(1, 3, 10, 11, 12, 2, 1, 20))
        UmpReader.readPayloadsUntil(source) { type, size, payload ->
            assertEquals(1, type); assertEquals(3, size)
            assertEquals(10, payload.read())
            false
        }
        assertEquals(2, source.read())
    }

    @Test fun `UMP refuses oversized declarations without reading body`() {
        val header = bytes(1) + umpInt(UmpReader.MAX_STREAMED_PART_BYTES + 1)
        assertThrows(SabrProtocolException::class.java) {
            UmpReader.readPayloadsUntil(ByteArrayInputStream(header)) { _, _, _ -> fail(); true }
        }
        assertThrows(SabrProtocolException::class.java) {
            UmpReader.readStreaming(ByteArrayInputStream(bytes(1) + umpInt(UmpReader.MAX_BUFFERED_PART_BYTES + 1))) { _, _ -> fail() }
        }
        assertThrows(SabrProtocolException::class.java) {
            UmpReader.readPayloadsUntil(ByteArrayInputStream(ByteArray(16385 * 2))) { _, _, _ -> true }
        }
    }

    @Test fun `UMP streams large generated payload without response sized allocation`() {
        val size = 8 * 1024 * 1024
        val header = ByteArrayInputStream(bytes(21) + umpInt(size))
        var remaining = size
        var maxRead = 0
        val source = object : InputStream() {
            override fun read(): Int = if (header.available() > 0) header.read() else if (remaining-- > 0) 42 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                maxRead = maxOf(maxRead, len)
                if (remaining <= 0) return -1
                val count = minOf(len, remaining)
                b.fill(42, off, off + count)
                remaining -= count
                return count
            }
        }
        var consumed = 0
        UmpReader.readPayloadsUntil(source) { _, length, payload ->
            assertEquals(size, length)
            val buffer = ByteArray(4096)
            while (true) {
                val count = payload.read(buffer)
                if (count < 0) break
                consumed += count
            }
            assertEquals(0, payload.read(buffer, 0, 0))
            true
        }
        assertEquals(size, consumed)
        assertTrue(maxRead <= 4096)
    }

    @Test fun `UMP responds to thread interruption`() {
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedIOException::class.java) {
                UmpReader.readPayloadsUntil(ByteArrayInputStream(bytes(1, 0))) { _, _, _ -> true }
            }
        } finally {
            Thread.interrupted()
        }
    }

    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
    private fun varint(value: Long): ByteArray {
        var remaining = value
        val out = ByteArrayOutputStream()
        while (remaining and -128L != 0L) {
            out.write((remaining.toInt() and 127) or 128)
            remaining = remaining ushr 7
        }
        out.write(remaining.toInt())
        return out.toByteArray()
    }
    private fun umpInt(value: Int): ByteArray = when {
        value < 128 -> bytes(value)
        value < 16384 -> bytes(128 or (value and 63), value ushr 6)
        value < 2097152 -> bytes(192 or (value and 31), value ushr 5, value ushr 13)
        value < 268435456 -> bytes(224 or (value and 15), value ushr 4, value ushr 12, value ushr 20)
        else -> bytes(240, value, value ushr 8, value ushr 16, value ushr 24)
    }
}
