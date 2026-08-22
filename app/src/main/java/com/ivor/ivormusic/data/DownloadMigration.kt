package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * One-time move of downloads out of the app's private `filesDir/music` and into
 * shared storage under Downloads/Koda, so they are reachable from the Files app.
 *
 * Two properties matter more than speed here:
 *
 *  - **Resumable.** Files are migrated one at a time and the metadata is
 *    rewritten after each, so a kill mid-migration loses at most the file in
 *    flight. Entries already carrying a `mediaUri` are skipped on the next run.
 *  - **Single-flight.** The mutex and process-wide flag mean concurrent callers
 *    collapse into one migration rather than racing over the same files.
 *    DownloadRepository is a singleton so in practice this runs once anyway,
 *    but the guard is what makes that a property of this object rather than an
 *    assumption about its caller.
 *
 * Copy-then-delete per file means peak extra disk usage is one file, not a
 * duplicate of the whole library.
 */
object DownloadMigration {

    private const val TAG = "DownloadMigration"
    private const val PREFS_NAME = "koda_download_migration"
    private const val KEY_MIGRATED = "downloads_migrated_to_mediastore"

    private val mutex = Mutex()

    @Volatile
    private var completedThisProcess = false

    /**
     * Move any legacy downloads into MediaStore. Safe to call repeatedly and
     * from multiple repository instances; only the first does work.
     *
     * @return true when anything was actually migrated, so the caller knows to
     *         reload its metadata.
     */
    suspend fun migrateIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (completedThisProcess) return@withContext false

        mutex.withLock {
            if (completedThisProcess) return@withLock false

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_MIGRATED, false)) {
                completedThisProcess = true
                return@withLock false
            }

            val metadataFile = File(context.filesDir, "downloaded_songs_metadata.json")
            val legacyDir = File(context.filesDir, "music")

            if (!metadataFile.exists()) {
                // Nothing to move; mark done so we never look again.
                prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
                completedThisProcess = true
                return@withLock false
            }

            val storage = DownloadStorage(context)
            var migratedAny = false

            try {
                val entries = JSONArray(metadataFile.readText())

                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue

                    // Already migrated on a previous (interrupted) run.
                    if (entry.has("mediaUri")) continue

                    val legacyPath = entry.optString("localPath").takeIf { it.isNotBlank() }
                        ?: continue
                    val legacyFile = File(legacyPath)
                    if (!legacyFile.exists()) {
                        // File vanished; drop the stale path so it is not retried.
                        entry.remove("localPath")
                        continue
                    }

                    val fileName = storage.buildFileName(
                        title = entry.optString("title", "Koda download"),
                        artist = entry.optString("artist", ""),
                        type = DownloadMediaType.MUSIC
                    )

                    val target = storage.createPending(fileName, DownloadMediaType.MUSIC)
                    if (target == null) {
                        KLog.e(TAG, "Could not create MediaStore entry for $fileName")
                        continue
                    }

                    val copied = runCatching {
                        storage.openOutput(target)?.use { output ->
                            legacyFile.inputStream().use { input -> input.copyTo(output) }
                        } != null
                    }.getOrElse { e ->
                        KLog.e(TAG, "Copy failed for $fileName", e)
                        false
                    }

                    if (!copied) {
                        // Roll back the pending row so no empty file is left behind.
                        storage.delete(target)
                        continue
                    }

                    storage.publish(target)
                    legacyFile.delete()

                    entry.put("mediaUri", target.toString())
                    entry.remove("localPath")
                    migratedAny = true

                    // Persist after every file so an interrupted migration
                    // resumes rather than restarting.
                    metadataFile.writeText(entries.toString())
                }

                metadataFile.writeText(entries.toString())

                // Only clear the legacy directory once nothing is left in it, so
                // a partially failed migration keeps its remaining source files.
                if (legacyDir.exists() && legacyDir.listFiles()?.isEmpty() != false) {
                    legacyDir.delete()
                }

                prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
                completedThisProcess = true
                KLog.i(TAG, "Download migration finished (migratedAny=$migratedAny)")
            } catch (e: Exception) {
                // Leave the flag unset so the next launch retries. The metadata
                // file is still valid: migrated entries carry mediaUri, the rest
                // still carry localPath, and both are readable.
                KLog.e(TAG, "Download migration failed, will retry next launch", e)
            }

            migratedAny
        }
    }
}
