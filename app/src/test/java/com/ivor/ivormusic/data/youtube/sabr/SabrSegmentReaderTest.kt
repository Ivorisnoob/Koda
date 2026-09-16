package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.media.SabrMediaHeader
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSegment
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSegmentReader
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSpool
import com.ivor.ivormusic.data.youtube.sabr.protocol.SabrProto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SabrSegmentReaderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `interleaved segments and controls are streamed to independent files`() {
        val directory = temporary.newFolder()
        SabrSpool(directory).use { spool ->
            val wire = header(1, 3) + header(2, 2) + part(21, bytes(1, 10)) +
                part(35, bytes(8, 1)) + part(21, bytes(2, 20, 21)) + end(2) +
                part(21, bytes(1, 11, 12)) + end(1)
            val seen = mutableListOf<Int>()
            val controls = mutableListOf<Int>()
            SabrSegmentReader.read(ByteArrayInputStream(wire), spool, { type, _ -> controls += type }) {
                it.use { segment ->
                    seen += segment.header.headerId
                    val actual = segment.open().use { stream -> stream.readBytes() }
                    assertArrayEquals(if (segment.header.headerId == 1) bytes(10, 11, 12) else bytes(20, 21), actual)
                }
            }
            assertEquals(listOf(2, 1), seen)
            assertEquals(listOf(35), controls)
            assertEquals(0, spool.bytes)
            assertEquals(0, directory.listFiles()!!.size)
        }
    }

    @Test fun `gzip and Brotli decode into bounded file backed segments`() {
        val gzip = gzip("gzip-fixture".toByteArray())
        // Independent Brotli fixture produced by Python brotli.compress.
        val brotli = bytes(11, 9, 128, 115, 97, 98, 114, 45, 98, 114, 111, 116, 108, 105, 45, 102, 105, 120, 116, 117, 114, 101, 3)
        listOf(Triple(1, gzip, "gzip-fixture"), Triple(2, brotli, "sabr-brotli-fixture")).forEach { (compression, data, expected) ->
            SabrSpool(temporary.newFolder()).use { spool ->
                SabrSegmentReader.read(ByteArrayInputStream(header(1, data.size, compression) + part(21, bytes(1) + data) + end(1)), spool, { _, _ -> fail() }) {
                    it.use { segment -> assertEquals(expected, segment.open().use { stream -> stream.readBytes().toString(Charsets.UTF_8) }) }
                }
                assertEquals(0, spool.bytes)
            }
        }
    }

    @Test fun `malformed and incomplete segments clean all pending files`() {
        val cases = listOf(
            header(1, 2) + header(1, 2),
            part(21, bytes(1, 5)), end(1),
            header(1, 2) + part(21, bytes(1, 5)) + end(1),
            header(1, 1) + part(21, bytes(1, 5, 6)),
            header(1, 1) + part(21, bytes(1, 5)),
            header(1, 1) + part(22, bytes(1, 2)),
            header(1, 1, compression = 7),
            header(1, 2, compression = 1) + part(21, bytes(1, 5, 6)) + end(1),
        )
        cases.forEach { wire ->
            val directory = temporary.newFolder()
            SabrSpool(directory).use { spool ->
                assertThrows(IOException::class.java) {
                    SabrSegmentReader.read(ByteArrayInputStream(wire), spool, { _, _ -> }, { fail() })
                }
                assertEquals(0, spool.bytes)
                assertEquals(0, directory.listFiles()!!.size)
            }
        }
    }

    @Test fun `disk budget and decompression expansion are bounded`() {
        val directory = temporary.newFolder()
        SabrSpool(directory, 2).use { spool ->
            assertThrows(IOException::class.java) {
                SabrSegmentReader.read(ByteArrayInputStream(header(1, 3) + part(21, bytes(1, 1, 2, 3))), spool, { _, _ -> }, { fail() })
            }
            assertEquals(0, spool.bytes)
        }
        val expanded = gzip(ByteArray(4 * 1024 * 1024 + 1))
        SabrSpool(directory).use { spool ->
            assertThrows(IOException::class.java) {
                SabrSegmentReader.read(ByteArrayInputStream(header(1, expanded.size, 1, true) + part(21, bytes(1) + expanded) + end(1)), spool, { _, _ -> }, { fail() })
            }
            assertEquals(0, spool.bytes)
            assertEquals(0, directory.listFiles()!!.size)
        }
    }

    @Test fun `consumer failure releases completed segment and source release closes readers`() {
        val directory = temporary.newFolder()
        val wire = header(1, 1) + part(21, bytes(1, 42)) + end(1)
        SabrSpool(directory).use { spool ->
            assertThrows(IllegalStateException::class.java) {
                SabrSegmentReader.read(ByteArrayInputStream(wire), spool, { _, _ -> }, { error("consumer failed") })
            }
            assertEquals(0, spool.bytes)
            var segment: SabrSegment? = null
            SabrSegmentReader.read(ByteArrayInputStream(wire), spool, { _, _ -> }, { segment = it })
            val reader = segment!!.open()
            spool.close()
            assertThrows(IOException::class.java) { reader.read() }
            assertThrows(IOException::class.java) { segment!!.open() }
            assertThrows(IOException::class.java) { spool.create() }
            assertEquals(0, directory.listFiles()!!.size)
        }
    }

    @Test fun `headers reject narrowing overflow and wrong protobuf wire types`() {
        val writer = SabrProto.Writer().apply { writeUInt64(1, 1L shl 32); writeUInt64(3, 140); writeUInt64(9, 1) }
        assertThrows(IOException::class.java) { SabrMediaHeader.decode(writer.toByteArray()) }
        val wrongType = SabrProto.Writer().apply { writeBytes(1, bytes(1)); writeUInt64(3, 140); writeUInt64(9, 1) }
        assertThrows(IOException::class.java) { SabrMediaHeader.decode(wrongType.toByteArray()) }
    }

    private fun header(id: Int, length: Int, compression: Int = 0, init: Boolean = false): ByteArray =
        part(20, SabrProto.Writer().apply {
            writeUInt64(1, id.toLong()); writeUInt64(3, 140); writeUInt64(4, 1)
            writeUInt64(7, compression.toLong()); writeBool(8, init); writeUInt64(9, 1)
            writeUInt64(14, length.toLong())
        }.toByteArray())
    private fun end(id: Int) = part(22, bytes(id))
    private fun part(type: Int, data: ByteArray): ByteArray {
        val size = data.size
        val length = when {
            size < 128 -> bytes(size)
            size < 16384 -> bytes(128 or (size and 63), size ushr 6)
            else -> bytes(192 or (size and 31), size ushr 5, size ushr 13)
        }
        return bytes(type) + length + data
    }
    private fun gzip(data: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()
    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
}
