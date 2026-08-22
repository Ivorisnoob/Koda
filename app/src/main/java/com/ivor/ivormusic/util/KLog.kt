package com.ivor.ivormusic.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's logging front door, and the source of everything the bug reporter
 * shows.
 *
 * Every call writes through to [Log] so logcat keeps working for development,
 * and appends to a bounded in-memory ring buffer. The buffer is what the
 * report screen renders and what [CrashReporter] snapshots when a crash is
 * written - which is why it lives on the companion object: the crash handler
 * runs outside any ViewModel scope, and with no DI there is no other way for
 * it to reach the same entries the UI recorded.
 *
 * Deliberately records every level in both debug and release builds. Release
 * is exactly where a debugger cannot be attached, so that is where the buffer
 * earns its keep; what leaves the device is decided by the report screen's
 * level filter, not by what was recorded.
 */
object KLog {

    enum class Level { ERROR, WARN, INFO, DEBUG }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        /** Throwable class name + message + first stack frames, already flattened. */
        val error: String? = null
    )

    private const val MAX_ENTRIES = 500

    /**
     * Byte ceiling alongside the entry cap, so a run of unusually long messages
     * cannot turn the report payload into megabytes of text on its own.
     */
    private const val MAX_CHARS = 256 * 1024

    private const val MAX_MESSAGE_CHARS = 500
    private const val MAX_ERROR_CHARS = 2000

    private val lock = Any()
    private val buffer = ArrayDeque<Entry>()
    private var bufferedChars = 0

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        record(Level.ERROR, tag, message, error)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error)
        record(Level.WARN, tag, message, error)
    }

    fun i(tag: String, message: String, error: Throwable? = null) {
        Log.i(tag, message, error)
        record(Level.INFO, tag, message, error)
    }

    fun d(tag: String, message: String, error: Throwable? = null) {
        Log.d(tag, message, error)
        record(Level.DEBUG, tag, message, error)
    }

    /**
     * Insert a line that did not come through [e]/[w]/[d] - the crash handler
     * uses this to stamp the crash itself into the buffer before anything
     * reads it back.
     */
    fun addRaw(level: Level, tag: String, message: String) {
        record(level, tag, message, null)
    }

    fun snapshot(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) {
        buffer.clear()
        bufferedChars = 0
    }

    private fun record(level: Level, tag: String, message: String, error: Throwable?) {
        val entry = Entry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message.take(MAX_MESSAGE_CHARS),
            error = error?.let { flattenThrowable(it).take(MAX_ERROR_CHARS) }
        )
        synchronized(lock) {
            buffer.addLast(entry)
            bufferedChars += entry.message.length + (entry.error?.length ?: 0)
            while (buffer.size > MAX_ENTRIES ||
                (bufferedChars > MAX_CHARS && buffer.size > 1)
            ) {
                val dropped = buffer.removeFirst()
                bufferedChars -= dropped.message.length + (dropped.error?.length ?: 0)
            }
        }
    }

    private fun flattenThrowable(throwable: Throwable): String = buildString {
        append(throwable.javaClass.name)
        throwable.message?.let { append(": ").append(it) }
        append('\n')
        val frames = throwable.stackTrace
        frames.take(12).forEach { frame ->
            append("    at ").append(frame.className).append('.')
                .append(frame.methodName)
                .append('(').append(frame.fileName ?: "?").append(':')
                .append(frame.lineNumber).append(')')
            append('\n')
        }
        if (frames.size > 12) append("    ... ${frames.size - 12} more")
        throwable.cause?.let { cause ->
            if (cause !== throwable) {
                append("Caused by: ").append(flattenThrowable(cause))
            }
        }
    }

    /**
     * Write the current buffer to app-private storage so a report can be
     * rebuilt after the process dies (a crash kills the buffer with it).
     * Atomic temp-file-then-rename, matching PlaybackSessionRepository.
     */
    fun flushToFile(context: Context): File? = try {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        val target = File(dir, "session_log.txt")
        val tmp = File(dir, "session_log.txt.tmp")
        tmp.writeText(render(snapshot(), header = "Koda session log"))
        if (target.exists()) target.delete()
        tmp.renameTo(target)
        target
    } catch (_: Throwable) {
        null
    }

    fun render(entries: List<Entry>, header: String): String {
        val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        return buildString {
            appendLine(header)
            appendLine()
            entries.forEach { entry ->
                append(format.format(Date(entry.timestamp)))
                append(' ')
                append(
                    when (entry.level) {
                        Level.ERROR -> 'E'
                        Level.WARN -> 'W'
                        Level.INFO -> 'I'
                        Level.DEBUG -> 'D'
                    }
                )
                append('/')
                append(entry.tag)
                append(": ")
                appendLine(entry.message)
                entry.error?.let { trace ->
                    trace.lineSequence().forEach { line ->
                        append("    ")
                        appendLine(line)
                    }
                }
            }
        }
    }
}
