package com.ivor.ivormusic.data

import android.content.Context
import android.util.AtomicFile
import com.ivor.ivormusic.util.KLog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The downloads that gave up, kept across process death.
 *
 * Progress lives in memory, which is right for a transfer and wrong for a
 * failure: a failed row is the only record that something the user asked for
 * never arrived, and the only place to retry it from. Losing the process -
 * a restart, a swipe from recents, the system reclaiming it - used to wipe
 * the list, so a playlist that failed halfway left nothing saying which half.
 *
 * The whole [DownloadRequest] is stored, not just its id, because a retry
 * needs everything the first attempt had: the song, the chosen video quality
 * and the caption tracks picked in the download sheet.
 *
 * Owned by the singleton download repository, which is the only writer.
 */
internal class FailedDownloadStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "failed_downloads.json"))
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): List<DownloadRequest> = try {
        file.openRead().bufferedReader().use { json.decodeFromString(it.readText()) }
    } catch (_: java.io.FileNotFoundException) {
        emptyList()
    } catch (error: Exception) {
        KLog.e(TAG, "Cannot read failed downloads", error)
        emptyList()
    }

    @Synchronized
    fun write(requests: List<DownloadRequest>) {
        if (requests.isEmpty()) {
            file.delete()
            return
        }
        val output = try {
            file.startWrite()
        } catch (error: Exception) {
            KLog.e(TAG, "Cannot save failed downloads", error)
            return
        }
        try {
            output.write(json.encodeToString(requests).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            KLog.e(TAG, "Cannot save failed downloads", error)
        }
    }

    private companion object {
        const val TAG = "FailedDownloads"
    }
}
