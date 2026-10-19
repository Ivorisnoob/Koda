package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog

/**
 * The short list of what a release is worth updating for.
 *
 * **One file per release is the source of both surfaces.**
 * `app/src/main/assets/release-notes/<versionName>.md` holds three to six
 * `- ` bullets. It ships inside that version's APK, where the "What's new"
 * sheet reads it on the first launch after updating - offline, and in Local
 * Only mode, because nothing is fetched. The same bullets are pasted into the
 * GitHub release under a `## Highlights` heading, which is what the update
 * dialog on an *older* install reads out of the release body: that install
 * cannot have the new version's file, so the release is the only place it can
 * learn them from. The release procedure is in `CLAUDE.md` (section 6).
 *
 * A release with no Highlights section is not an error. The dialog then says
 * so in one line and offers the full notes, rather than guessing which of
 * forty changelog bullets were the important ones.
 */
object ReleaseHighlights {

    private const val ASSET_DIR = "release-notes"

    /** A dialog is read in a glance; a seventh bullet is a changelog. */
    const val MAX_HIGHLIGHTS = 6

    private val HEADING = Regex("^#{1,6}\\s*\\S.*$")
    private val HIGHLIGHTS_HEADING =
        Regex("^(#{1,6}\\s*|\\*\\*)\\W*highlights\\W*$", RegexOption.IGNORE_CASE)
    private val BULLET = Regex("^[-*\\u2022]\\s+(.+)$")

    /** The bullets of [text], with list markers and Markdown bold removed. */
    fun parseBullets(text: String): List<String> = text.lineSequence()
        .mapNotNull { line -> BULLET.matchEntire(line.trim())?.groupValues?.get(1) }
        .map { it.replace("**", "").replace("__", "").trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_HIGHLIGHTS)
        .toList()

    /**
     * The bullets under a GitHub release body's Highlights heading, up to the
     * next heading. Empty when the release has no such section.
     */
    fun fromReleaseBody(body: String): List<String> {
        val lines = body.lines()
        val start = lines.indexOfFirst { HIGHLIGHTS_HEADING.matches(it.trim()) }
        if (start < 0) return emptyList()
        val section = lines.drop(start + 1).takeWhile { !HEADING.matches(it.trim()) }
        return parseBullets(section.joinToString("\n"))
    }

    /** "5.2-debug" and "5.2" are the same release. */
    fun releaseVersion(versionName: String): String = versionName.substringBefore('-').trim()

    /** The highlights bundled for [versionName], or empty when this build shipped none. */
    fun bundled(context: Context, versionName: String): List<String> = try {
        context.assets.open("$ASSET_DIR/${releaseVersion(versionName)}.md")
            .bufferedReader().use { parseBullets(it.readText()) }
    } catch (e: java.io.IOException) {
        emptyList()
    }

    /**
     * The newest bundled file, whatever version it is for. Debug builds only:
     * it lets the sheet be previewed while the next release's file is being
     * written, before `versionName` has been bumped to match it.
     */
    fun newestBundled(context: Context): Pair<String, List<String>>? = try {
        context.assets.list(ASSET_DIR).orEmpty()
            .filter { it.endsWith(".md") }
            .map { it.removeSuffix(".md") }
            .maxWithOrNull { a, b -> compareVersions(a, b) }
            ?.let { version -> version to bundled(context, version) }
            ?.takeIf { it.second.isNotEmpty() }
    } catch (e: java.io.IOException) {
        KLog.w("ReleaseHighlights", "Could not list bundled release notes", e)
        null
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
        val right = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
        for (index in 0 until maxOf(left.size, right.size)) {
            val difference = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }
}

/**
 * What this install has already been shown: the version whose "What's new" it
 * has seen, and the update it was last asked about.
 *
 * Its own preference file, and deliberately not in the backup allowlist: this
 * is a fact about the app installed on this device, and restoring it onto
 * another phone would hide a prompt that phone has never shown.
 */
class UpdatePromptStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The version to show "What's new" for on this launch, or null.
     *
     * An install with no record is either brand new or has just updated from a
     * version older than this feature. The package manager tells the two apart:
     * a fresh install has never been updated, and is not shown a list of
     * changes to an app it has not used.
     */
    fun whatsNewVersion(installedVersion: String): String? {
        val installed = ReleaseHighlights.releaseVersion(installedVersion)
        val seen = prefs.getString(KEY_SEEN_VERSION, null)
        if (seen == installed) return null
        if (seen == null && isFreshInstall()) {
            markWhatsNewSeen(installedVersion)
            return null
        }
        return installed
    }

    fun markWhatsNewSeen(installedVersion: String) {
        prefs.edit()
            .putString(KEY_SEEN_VERSION, ReleaseHighlights.releaseVersion(installedVersion))
            .apply()
    }

    fun shouldPromptFor(version: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        shouldPrompt(
            promptedVersion = prefs.getString(KEY_PROMPTED_VERSION, null),
            promptedAtMs = prefs.getLong(KEY_PROMPTED_AT, 0L),
            version = version,
            nowMs = nowMs
        )

    fun markPrompted(version: String, nowMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_PROMPTED_VERSION, version)
            .putLong(KEY_PROMPTED_AT, nowMs)
            .apply()
    }

    private fun isFreshInstall(): Boolean = try {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    } catch (e: Exception) {
        // Unknown reads as an update: showing the sheet once to a new user is
        // a smaller mistake than never showing it to someone who updated.
        false
    }

    companion object {
        private const val PREFS = "update_prompts"
        private const val KEY_SEEN_VERSION = "whats_new_seen_version"
        private const val KEY_PROMPTED_VERSION = "prompted_version"
        private const val KEY_PROMPTED_AT = "prompted_at"

        /** "Later" is asked again after two days on the same release. */
        const val REPROMPT_AFTER_MS = 2L * 24L * 60L * 60L * 1000L

        /**
         * Whether the update dialog should open by itself: for a release it
         * has not been shown for, or again once "Later" is two days old. A
         * clock set backwards reads as due rather than as never.
         */
        fun shouldPrompt(
            promptedVersion: String?,
            promptedAtMs: Long,
            version: String,
            nowMs: Long
        ): Boolean {
            if (promptedVersion != version) return true
            val elapsed = nowMs - promptedAtMs
            return elapsed < 0L || elapsed >= REPROMPT_AFTER_MS
        }
    }
}
