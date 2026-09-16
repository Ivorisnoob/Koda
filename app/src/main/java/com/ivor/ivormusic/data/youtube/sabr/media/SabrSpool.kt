package com.ivor.ivormusic.data.youtube.sabr.media

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/** One source's temporary files, separate from Media3's durable cache. Close on source release. */
internal class SabrSpool(private val directory: File, private val maxBytes: Long = 256L * 1024 * 1024) : Closeable {
    private val files = mutableSetOf<Lease>()
    private var closed = false
    var bytes: Long = 0
        private set

    init { require(maxBytes > 0) }

    @Synchronized fun create(): Lease {
        if (closed) throw IOException("SABR spool closed")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create SABR spool")
        return Lease(File.createTempFile("segment-", ".tmp", directory)).also { files += it }
    }

    @Synchronized override fun close() {
        closed = true
        var error: IOException? = null
        files.toList().forEach {
            try { it.close() } catch (e: IOException) { if (error == null) error = e else error!!.addSuppressed(e) }
        }
        error?.let { throw it }
    }

    /** File access is scoped to its source; release can close an in-flight writer. */
    inner class Lease internal constructor(private val file: File) : Closeable {
        private var output: FileOutputStream? = null
        private var finished = false
        private var released = false
        private val readers = mutableSetOf<InputStream>()
        var length: Long = 0
            private set

        fun write(buffer: ByteArray, count: Int, limit: Long) = synchronized(this@SabrSpool) {
            if (closed || released || finished) throw IOException("SABR spool unavailable")
            if (count < 0 || count > buffer.size) throw IndexOutOfBoundsException()
            if (count > limit - length || count > maxBytes - bytes) {
                throw SabrProtocolException("SABR spool size limit exceeded")
            }
            val stream = output ?: FileOutputStream(file).also { output = it }
            // Reserve before IO: a partial failed write must still count against
            // the budget until its file has been deleted.
            length += count
            bytes += count
            stream.write(buffer, 0, count)
        }

        fun finish() = synchronized(this@SabrSpool) {
            if (closed || released) throw IOException("SABR spool unavailable")
            output?.close()
            output = null
            finished = true
        }

        fun open(): InputStream = synchronized(this@SabrSpool) {
            if (closed || released || !finished) throw IOException("SABR segment unavailable")
            val reader = object : FilterInputStream(FileInputStream(file)) {
                override fun close() = synchronized(this@SabrSpool) {
                    try { super.close() } finally { readers.remove(this) }
                }
            }
            readers += reader
            reader
        }

        override fun close() = synchronized(this@SabrSpool) {
            if (!released) {
                try {
                    readers.toList().forEach { it.close() }
                } finally {
                    try {
                        output?.close()
                    } finally {
                        output = null
                        // Retain accounting on a deletion failure so the same source cannot
                        // evade its disk budget by repeatedly failing cleanup.
                        Files.deleteIfExists(file.toPath())
                        bytes -= length
                        released = true
                        files.remove(this)
                    }
                }
            }
        }

        override fun toString() = "SabrSpool.Lease(bytes=$length)"
    }
}

internal class SabrSegment(val header: SabrMediaHeader, private val lease: SabrSpool.Lease) : Closeable {
    val length: Long get() = lease.length
    fun open(): InputStream = lease.open()
    override fun close() = lease.close()
    override fun toString() = "SabrSegment(header=$header, bytes=$length)"
}
