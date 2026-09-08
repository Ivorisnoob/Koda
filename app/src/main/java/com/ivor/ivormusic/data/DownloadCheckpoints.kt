package com.ivor.ivormusic.data

import java.io.File

/** Session-owned partial media. Never stores signed URLs; completed downloads live elsewhere. */
internal class DownloadCheckpoints(private val directory: File) {
    internal class Entry(val identity: String, val file: File) {
        var totalBytes: Long = -1L
        var complete: Boolean = false
    }

    private val entries = mutableMapOf<Pair<String, String>, Entry>()

    init {
        // The queue is session-scoped too. Orphans from a killed process cannot
        // be resumed without their request and must not consume storage forever.
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
    }

    @Synchronized
    fun get(id: String, role: String, identity: String): Entry {
        val key = id to role
        entries[key]?.let {
            if (it.identity == identity && it.file.exists()) return it
            it.file.delete()
        }
        return Entry(identity, File.createTempFile("media_", ".part", directory))
            .also { entries[key] = it }
    }

    @Synchronized
    fun remove(id: String) {
        val keys = entries.keys.filter { it.first == id }
        keys.forEach { entries.remove(it)?.file?.delete() }
    }
}
