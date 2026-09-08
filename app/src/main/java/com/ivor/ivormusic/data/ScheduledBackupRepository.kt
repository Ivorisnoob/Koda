package com.ivor.ivormusic.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import com.ivor.ivormusic.R
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

enum class ScheduledBackupStatus {
    IDLE,
    SUCCESS,
    PERMISSION_REVOKED,
    FOLDER_UNAVAILABLE,
    FILE_CREATION_FAILED,
    WRITE_FAILED
}

enum class FolderStatus {
    NOT_CONFIGURED,
    VALID,
    PERMISSION_REVOKED,
    FOLDER_MISSING
}

data class BackupFrequency(
    val hours: Long,
    @StringRes val labelRes: Int
)

sealed class ScheduledBackupResult {
    data class Success(val manifest: BackupManifest, val prunedCount: Int) : ScheduledBackupResult()
    data class Failure(val status: ScheduledBackupStatus, val message: String) : ScheduledBackupResult()
}

/**
 * Manages configuration and execution for automated scheduled backups (#230).
 *
 * All state lives in [PREFS_NAME] which is intentionally excluded from
 * [BackupRepository.PREFERENCE_FILES], ensuring device-specific storage permissions
 * and schedules are not copied across devices during backup restoration.
 */
class ScheduledBackupRepository(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (sharedEnabled == null) {
                sharedEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
                val uriStr = prefs.getString(KEY_FOLDER_URI, null)
                sharedFolderUri = MutableStateFlow(uriStr?.let { runCatching { Uri.parse(it) }.getOrNull() })
                sharedFolderDisplayName = MutableStateFlow(prefs.getString(KEY_FOLDER_NAME, null))
                sharedFrequencyHours = MutableStateFlow(prefs.getLong(KEY_FREQUENCY_HOURS, DEFAULT_FREQUENCY_HOURS))
                sharedRetentionLimit = MutableStateFlow(prefs.getInt(KEY_RETENTION_LIMIT, DEFAULT_RETENTION_LIMIT))
                sharedRequiresCharging = MutableStateFlow(prefs.getBoolean(KEY_REQUIRE_CHARGING, true))
                sharedRequiresDeviceIdle = MutableStateFlow(prefs.getBoolean(KEY_REQUIRE_DEVICE_IDLE, true))
                sharedRequiresUnmetered = MutableStateFlow(prefs.getBoolean(KEY_REQUIRE_UNMETERED, false))
                sharedLastRunAt = MutableStateFlow(prefs.getLong(KEY_LAST_RUN_AT, 0L))
                val statusStr = prefs.getString(KEY_LAST_RUN_STATUS, ScheduledBackupStatus.IDLE.name)
                sharedLastRunStatus = MutableStateFlow(
                    runCatching { ScheduledBackupStatus.valueOf(statusStr ?: ScheduledBackupStatus.IDLE.name) }
                        .getOrDefault(ScheduledBackupStatus.IDLE)
                )
                sharedLastRunError = MutableStateFlow(prefs.getString(KEY_LAST_RUN_ERROR, null))
                sharedConsecutiveFailures = MutableStateFlow(prefs.getInt(KEY_CONSECUTIVE_FAILURES, 0))
            }
        }
    }

    val enabled: StateFlow<Boolean> get() = sharedEnabled!!.asStateFlow()
    val folderUri: StateFlow<Uri?> get() = sharedFolderUri!!.asStateFlow()
    val folderDisplayName: StateFlow<String?> get() = sharedFolderDisplayName!!.asStateFlow()
    val frequencyHours: StateFlow<Long> get() = sharedFrequencyHours!!.asStateFlow()
    val retentionLimit: StateFlow<Int> get() = sharedRetentionLimit!!.asStateFlow()
    val requiresCharging: StateFlow<Boolean> get() = sharedRequiresCharging!!.asStateFlow()
    val requiresDeviceIdle: StateFlow<Boolean> get() = sharedRequiresDeviceIdle!!.asStateFlow()
    val requiresUnmetered: StateFlow<Boolean> get() = sharedRequiresUnmetered!!.asStateFlow()
    val lastRunAt: StateFlow<Long> get() = sharedLastRunAt!!.asStateFlow()
    val lastRunStatus: StateFlow<ScheduledBackupStatus> get() = sharedLastRunStatus!!.asStateFlow()
    val lastRunError: StateFlow<String?> get() = sharedLastRunError!!.asStateFlow()
    val consecutiveFailures: StateFlow<Int> get() = sharedConsecutiveFailures!!.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        sharedEnabled!!.value = value
        rescheduleWorker()
    }

    fun setFolder(uri: Uri) {
        val oldUri = sharedFolderUri?.value
        if (oldUri != null && oldUri != uri) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                appContext.contentResolver.releasePersistableUriPermission(oldUri, flags)
            } catch (e: Exception) {
                KLog.w(TAG, "Could not release previous persistable permission", e)
            }
        }

        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            appContext.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            KLog.e(TAG, "Failed to take persistable URI permission for $uri", e)
        }

        val displayName = getFolderDisplayName(appContext, uri)

        prefs.edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .putString(KEY_FOLDER_NAME, displayName)
            .putString(KEY_LAST_RUN_STATUS, ScheduledBackupStatus.IDLE.name)
            .remove(KEY_LAST_RUN_ERROR)
            .apply()

        sharedFolderUri!!.value = uri
        sharedFolderDisplayName!!.value = displayName
        sharedLastRunStatus!!.value = ScheduledBackupStatus.IDLE
        sharedLastRunError!!.value = null

        if (enabled.value) {
            rescheduleWorker()
        }
    }

    fun clearFolder() {
        val oldUri = sharedFolderUri?.value
        if (oldUri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                appContext.contentResolver.releasePersistableUriPermission(oldUri, flags)
            } catch (e: Exception) {
                KLog.w(TAG, "Could not release persistable permission", e)
            }
        }
        prefs.edit()
            .remove(KEY_FOLDER_URI)
            .remove(KEY_FOLDER_NAME)
            .apply()

        sharedFolderUri!!.value = null
        sharedFolderDisplayName!!.value = null
        setEnabled(false)
    }

    fun setFrequencyHours(hours: Long) {
        prefs.edit().putLong(KEY_FREQUENCY_HOURS, hours).apply()
        sharedFrequencyHours!!.value = hours
        if (enabled.value) {
            rescheduleWorker()
        }
    }

    fun setRetentionLimit(limit: Int) {
        val bounded = limit.coerceIn(1, 100)
        prefs.edit().putInt(KEY_RETENTION_LIMIT, bounded).apply()
        sharedRetentionLimit!!.value = bounded
    }

    fun setRequiresCharging(value: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_CHARGING, value).apply()
        sharedRequiresCharging!!.value = value
        if (enabled.value) {
            rescheduleWorker()
        }
    }

    fun setRequiresDeviceIdle(value: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_DEVICE_IDLE, value).apply()
        sharedRequiresDeviceIdle!!.value = value
        if (enabled.value) {
            rescheduleWorker()
        }
    }

    fun setRequiresUnmetered(value: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_UNMETERED, value).apply()
        sharedRequiresUnmetered!!.value = value
        if (enabled.value) {
            rescheduleWorker()
        }
    }

    fun checkFolderStatus(): FolderStatus {
        val uri = folderUri.value ?: return FolderStatus.NOT_CONFIGURED
        val hasPermission = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        if (!hasPermission) return FolderStatus.PERMISSION_REVOKED
        val doc = DocumentFile.fromTreeUri(appContext, uri)
        if (doc == null || !doc.exists()) return FolderStatus.FOLDER_MISSING
        return FolderStatus.VALID
    }

    /**
     * Executes the backup write directly into the configured folder, then prunes
     * older archives beyond [retentionLimit].
     *
     * In accordance with safety rules:
     * 1. A new backup is written and closed first.
     * 2. Only after writing succeeds are older archives pruned.
     * 3. Failed writes never delete previous backups and clean up partial artifacts.
     */
    suspend fun executeBackup(): ScheduledBackupResult = withContext(Dispatchers.IO) {
        val uri = folderUri.value
        if (uri == null) {
            val msg = "No backup folder configured"
            recordFailure(ScheduledBackupStatus.FOLDER_UNAVAILABLE, msg)
            return@withContext ScheduledBackupResult.Failure(ScheduledBackupStatus.FOLDER_UNAVAILABLE, msg)
        }

        // 1. Verify persisted SAF permission
        val hasPermission = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        if (!hasPermission) {
            val msg = "Storage permission was revoked"
            recordFailure(ScheduledBackupStatus.PERMISSION_REVOKED, msg)
            return@withContext ScheduledBackupResult.Failure(ScheduledBackupStatus.PERMISSION_REVOKED, msg)
        }

        // 2. Verify target directory accessibility
        val root = DocumentFile.fromTreeUri(appContext, uri)
        if (root == null || !root.exists() || !root.canWrite()) {
            val msg = "Backup folder is inaccessible or has been deleted"
            recordFailure(ScheduledBackupStatus.FOLDER_UNAVAILABLE, msg)
            return@withContext ScheduledBackupResult.Failure(ScheduledBackupStatus.FOLDER_UNAVAILABLE, msg)
        }

        // 3. Create document file in the target directory
        val fileName = BackupRepository.suggestedFileName()
        val backupDoc = root.createFile(BackupTransfer.MIME_TYPE, fileName)
        if (backupDoc == null) {
            val msg = "Could not create backup file in destination folder"
            recordFailure(ScheduledBackupStatus.FILE_CREATION_FAILED, msg)
            return@withContext ScheduledBackupResult.Failure(ScheduledBackupStatus.FILE_CREATION_FAILED, msg)
        }

        // 4. Collect snapshot and write stream
        val backupRepo = BackupRepository(appContext)
        val manifest = try {
            val snapshot = backupRepo.collect()
            appContext.contentResolver.openOutputStream(backupDoc.uri)?.use { out ->
                BackupTransfer.write(snapshot, out)
            } ?: throw IOException("Could not open output stream for ${backupDoc.uri}")
            snapshot.manifest
        } catch (e: Exception) {
            KLog.e(TAG, "Scheduled backup write failed", e)
            try {
                backupDoc.delete()
            } catch (cleanupEx: Exception) {
                KLog.w(TAG, "Failed to clean up incomplete backup file", cleanupEx)
            }
            val msg = e.localizedMessage ?: "Unknown write error"
            recordFailure(ScheduledBackupStatus.WRITE_FAILED, msg)
            return@withContext ScheduledBackupResult.Failure(ScheduledBackupStatus.WRITE_FAILED, msg)
        }

        // 5. Update last backup timestamp in state prefs (shared with manual backups)
        appContext.getSharedPreferences("koda_backup_state", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_backup_at", manifest.createdAt)
            .apply()

        // 6. Prune older backups strictly AFTER the new file is written and closed
        var pruned = 0
        val limit = retentionLimit.value
        try {
            val existingBackups = root.listFiles()
                .filter { doc ->
                    val name = doc.name.orEmpty()
                    name.startsWith("koda-backup-") && name.endsWith(".zip") && doc.isFile
                }
                .sortedByDescending { it.lastModified() }

            if (existingBackups.size > limit) {
                val toDelete = existingBackups.drop(limit)
                for (oldDoc in toDelete) {
                    if (oldDoc.delete()) {
                        pruned++
                    }
                }
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Pruning old backups encountered an issue", e)
        }

        // 7. Record success and reset failure counters
        recordSuccess(manifest.createdAt)
        ScheduledBackupResult.Success(manifest, pruned)
    }

    private fun recordSuccess(createdAt: Long) {
        prefs.edit()
            .putLong(KEY_LAST_RUN_AT, createdAt)
            .putString(KEY_LAST_RUN_STATUS, ScheduledBackupStatus.SUCCESS.name)
            .remove(KEY_LAST_RUN_ERROR)
            .putInt(KEY_CONSECUTIVE_FAILURES, 0)
            .apply()

        sharedLastRunAt!!.value = createdAt
        sharedLastRunStatus!!.value = ScheduledBackupStatus.SUCCESS
        sharedLastRunError!!.value = null
        sharedConsecutiveFailures!!.value = 0
    }

    private fun recordFailure(status: ScheduledBackupStatus, error: String) {
        val currentFailures = (sharedConsecutiveFailures?.value ?: 0) + 1
        prefs.edit()
            .putString(KEY_LAST_RUN_STATUS, status.name)
            .putString(KEY_LAST_RUN_ERROR, error)
            .putInt(KEY_CONSECUTIVE_FAILURES, currentFailures)
            .apply()

        sharedLastRunStatus!!.value = status
        sharedLastRunError!!.value = error
        sharedConsecutiveFailures!!.value = currentFailures
    }

    private fun rescheduleWorker() {
        try {
            com.ivor.ivormusic.work.ScheduledBackupWorker.sync(appContext)
        } catch (e: Exception) {
            KLog.w(TAG, "Could not invoke ScheduledBackupWorker.sync", e)
        }
    }

    companion object {
        private const val TAG = "ScheduledBackupRepo"
        const val PREFS_NAME = "koda_backup_schedule"

        const val KEY_ENABLED = "scheduled_backup_enabled"
        const val KEY_FOLDER_URI = "scheduled_backup_folder_uri"
        const val KEY_FOLDER_NAME = "scheduled_backup_folder_name"
        const val KEY_FREQUENCY_HOURS = "scheduled_backup_frequency_hours"
        const val KEY_RETENTION_LIMIT = "scheduled_backup_retention_limit"
        const val KEY_REQUIRE_CHARGING = "scheduled_backup_require_charging"
        const val KEY_REQUIRE_DEVICE_IDLE = "scheduled_backup_require_idle"
        const val KEY_REQUIRE_UNMETERED = "scheduled_backup_require_unmetered"
        const val KEY_LAST_RUN_AT = "scheduled_backup_last_run_at"
        const val KEY_LAST_RUN_STATUS = "scheduled_backup_last_run_status"
        const val KEY_LAST_RUN_ERROR = "scheduled_backup_last_run_error"
        const val KEY_CONSECUTIVE_FAILURES = "scheduled_backup_consecutive_failures"

        const val DEFAULT_FREQUENCY_HOURS = 24L // Daily
        const val DEFAULT_RETENTION_LIMIT = 5

        val FREQUENCY_OPTIONS = listOf(
            BackupFrequency(24L, R.string.bk_freq_daily),
            BackupFrequency(72L, R.string.bk_freq_3days),
            BackupFrequency(168L, R.string.bk_freq_weekly),
            BackupFrequency(336L, R.string.bk_freq_2weeks),
            BackupFrequency(720L, R.string.bk_freq_monthly),
        )

        val RETENTION_OPTIONS = listOf(3, 5, 10, 20)

        private val LOCK = Any()
        private var sharedEnabled: MutableStateFlow<Boolean>? = null
        private var sharedFolderUri: MutableStateFlow<Uri?>? = null
        private var sharedFolderDisplayName: MutableStateFlow<String?>? = null
        private var sharedFrequencyHours: MutableStateFlow<Long>? = null
        private var sharedRetentionLimit: MutableStateFlow<Int>? = null
        private var sharedRequiresCharging: MutableStateFlow<Boolean>? = null
        private var sharedRequiresDeviceIdle: MutableStateFlow<Boolean>? = null
        private var sharedRequiresUnmetered: MutableStateFlow<Boolean>? = null
        private var sharedLastRunAt: MutableStateFlow<Long>? = null
        private var sharedLastRunStatus: MutableStateFlow<ScheduledBackupStatus>? = null
        private var sharedLastRunError: MutableStateFlow<String?>? = null
        private var sharedConsecutiveFailures: MutableStateFlow<Int>? = null

        fun getFolderDisplayName(context: Context, treeUri: Uri): String {
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            val docName = doc?.name

            val treeDocId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (e: Exception) {
                null
            }

            if (treeDocId != null) {
                if (treeDocId.startsWith("primary:")) {
                    val relativePath = treeDocId.removePrefix("primary:").trim('/')
                    if (relativePath.isNotEmpty()) {
                        return relativePath.replace("/", " \u203A ")
                    }
                    return "Internal storage"
                }
                if (treeDocId.contains(':')) {
                    val parts = treeDocId.split(':', limit = 2)
                    val sub = parts.getOrNull(1)?.trim('/')?.replace("/", " \u203A ")
                    if (!sub.isNullOrEmpty()) {
                        return "SD Card \u203A $sub"
                    }
                }
            }

            if (!docName.isNullOrBlank()) {
                return docName
            }

            return Uri.decode(treeUri.lastPathSegment ?: "Backup folder")
        }
    }
}
