package com.ivor.ivormusic.data

import android.content.Context
import android.os.Build
import com.ivor.ivormusic.BuildConfig
import com.ivor.ivormusic.util.KLog
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Uncaught-exception capture for the in-app bug reporter.
 *
 * Wraps the platform's default handler rather than replacing it: the system
 * crash dialog and process death must still happen exactly as before, the
 * file is a by-product, not a rescue. The write itself is guarded end to end -
 * an exception thrown inside the handler would replace the app's crash with
 * this file's, which helps nobody.
 *
 * The crash lands in `filesDir/logs/last_crash.txt` and survives process
 * death; the next launch offers to report it (see MainActivity) and either
 * successful reporting deletes it. "Not now" only suppresses that particular
 * prompt; the report remains available from Settings so diagnostics are not
 * lost before the user is ready. Nothing here uploads anything on its own.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"

    /** One crash is kept. A newer one overwrites an unreported older one. */
    private const val CRASH_FILE_NAME = "last_crash.txt"
    private const val LOG_DIR = "logs"
    private const val PROMPT_PREFS = "crash_reporter"
    private const val KEY_DISMISSED_FINGERPRINT = "dismissed_fingerprint"

    /** Log lines snapshotted into the crash file alongside the trace. */
    private const val CRASH_LOG_LINES = 100

    fun install(appContext: Context) {
        val context = appContext.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(context, previous))
    }

    fun pendingCrashFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        return File(dir, CRASH_FILE_NAME)
    }

    /** Non-null when the last run ended in a crash that has not been dismissed or reported. */
    fun readPendingCrash(context: Context): String? = try {
        pendingCrashFile(context).takeIf { it.exists() && it.length() > 0 }?.readText()
    } catch (_: Throwable) {
        null
    }

    /** True only for a crash whose prompt has not already been deferred. */
    fun shouldPromptForPendingCrash(context: Context): Boolean {
        val file = pendingCrashFile(context)
        if (!file.exists() || file.length() <= 0L) return false
        val dismissed = context.getSharedPreferences(PROMPT_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED_FINGERPRINT, null)
        return dismissed != fingerprint(file)
    }

    /** Hide this crash's automatic prompt while retaining the report itself. */
    fun dismissPendingCrashPrompt(context: Context) {
        val file = pendingCrashFile(context)
        if (!file.exists() || file.length() <= 0L) return
        context.getSharedPreferences(PROMPT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_FINGERPRINT, fingerprint(file))
            .apply()
    }

    fun clearPendingCrash(context: Context) {
        try {
            pendingCrashFile(context).delete()
            context.getSharedPreferences(PROMPT_PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_DISMISSED_FINGERPRINT).apply()
        } catch (_: Throwable) {
        }
    }

    private fun fingerprint(file: File): String = "${file.lastModified()}:${file.length()}"

    private class Handler(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                write(thread, throwable)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }

        private fun write(thread: Thread, throwable: Throwable) {
            // Stamp the crash into the buffer first so its own log snapshot
            // ends with the crash itself.
            KLog.addRaw(KLog.Level.ERROR, TAG, "Uncaught exception on ${thread.name}")
            KLog.flushToFile(context)

            val recentLogs = KLog.render(
                KLog.snapshot().takeLast(CRASH_LOG_LINES),
                header = "Last $CRASH_LOG_LINES log lines before the crash"
            )

            val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
                .toString()

            val text = buildString {
                appendLine("Koda crashed")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Build: ${if (BuildConfig.DEBUG) "debug" else "release"}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Thread: ${thread.name}")
                appendLine("Captured: ${System.currentTimeMillis()}")
                appendLine()
                appendLine("Stack trace:")
                appendLine(stack)
                appendLine()
                appendLine(recentLogs)
            }

            val target = pendingCrashFile(context)
            val tmp = File(target.parentFile, "$CRASH_FILE_NAME.tmp")
            tmp.writeText(text)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }
}
