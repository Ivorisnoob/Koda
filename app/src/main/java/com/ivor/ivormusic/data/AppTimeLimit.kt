package com.ivor.ivormusic.data

import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration

/**
 * The enforcement half of the daily time limit; the user's choices live in
 * [ThemePreferences] (`timeLimitEnabled`, `timeLimitBudgets`).
 *
 * How it stays honest within what an app can do:
 *
 * - Usage accrues only while the activity is STARTED, in deltas charged by a
 *   30-second ticker plus an on-stop flush, so force-closing loses at most
 *   one tick - never enough to matter against a budget measured in hours.
 * - The total is persisted on every charge with today's date key, so it
 *   survives process death and reboots. It resets only when the local date
 *   changes (past midnight), which is also the only way out of a lock.
 * - While the lock overlay is up the activity keeps charging nothing, so
 *   sitting on the lock screen does not eat tomorrow's budget - and because
 *   the overlay covers every surface including Settings, there is no way to
 *   reach the toggle until the day rolls over.
 *
 * What it cannot do is survive the app being uninstalled or its data cleared;
 * Android gives no in-app defence against that. Stated plainly rather than
 * pretended away.
 */
object AppTimeLimit {

    private const val TAG = "AppTimeLimit"

    private const val PREFS_NAME = "app_time_limit"
    private const val KEY_USED_DATE = "used_date"
    private const val KEY_USED_SECONDS = "used_seconds"

    /** Seed budget when the limiter is first switched on: 5 hours a day. */
    const val DEFAULT_DAILY_MINUTES = 300

    /** Slider granularity, so the editor's steps land on clean values. */
    const val BUDGET_STEP_MINUTES = 15

    fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Parse stored "day=minutes" entries into a day(0=Mon..6=Sun) -> minutes
     * map. Malformed entries are skipped, not fatal - a bad value must never
     * take down startup.
     */
    fun parseBudgets(stored: Set<String>): Map<Int, Int> = buildMap {
        stored.forEach { entry ->
            val day = entry.substringBefore('=').toIntOrNull()
            val minutes = entry.substringAfter('=').toIntOrNull()
            if (day != null && minutes != null && day in 0..6 && minutes >= 0) {
                put(day, minutes)
            } else {
                Log.w(TAG, "Skipping malformed budget entry: $entry")
            }
        }
    }

    /** Today's budget in minutes; 0 means unlimited. Monday-based indexing. */
    fun budgetMinutesForToday(budgets: Map<Int, Int>): Int =
        budgets[(LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)] ?: 0

    /**
     * Charge foreground time to today. Rolls over automatically when the
     * local date has changed since the last charge.
     */
    fun addForegroundMillis(context: Context, millis: Long) {
        if (millis <= 0L) return
        try {
            val prefs = prefs(context)
            val today = LocalDate.now().toString()
            val usedToday =
                if (prefs.getString(KEY_USED_DATE, null) == today) {
                    prefs.getLong(KEY_USED_SECONDS, 0L)
                } else {
                    0L
                }
            // apply(), not commit(): this runs on the main thread twice a
            // minute, and losing the last tick to a force-stop costs nothing -
            // a lock that was already earned shows anyway.
            prefs.edit()
                .putString(KEY_USED_DATE, today)
                .putLong(KEY_USED_SECONDS, usedToday + millis / 1000L)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record usage", e)
        }
    }

    fun usedSecondsToday(context: Context): Long {
        val prefs = prefs(context)
        return if (prefs.getString(KEY_USED_DATE, null) == LocalDate.now().toString()) {
            prefs.getLong(KEY_USED_SECONDS, 0L)
        } else {
            0L
        }
    }

    /**
     * The single lock decision. Everything that shows or hides the overlay
     * goes through here, fresh-reading preferences each time so a budget
     * change takes effect on the next tick without any flow plumbing.
     */
    fun isLocked(
        context: Context,
        enabled: Boolean,
        budgetsStored: Set<String>
    ): Boolean {
        if (!enabled) return false
        val budgetMinutes = budgetMinutesForToday(parseBudgets(budgetsStored))
        if (budgetMinutes <= 0) return false
        return usedSecondsToday(context) >= budgetMinutes * 60L
    }

    /** Progress fraction for the wavy ring: used/budget, clamped to 0..1. */
    fun progressFraction(usedSeconds: Long, budgetMinutes: Int): Float {
        if (budgetMinutes <= 0) return 0f
        return (usedSeconds.toFloat() / (budgetMinutes * 60f)).coerceIn(0f, 1f)
    }

    fun millisUntilMidnight(now: LocalDateTime = LocalDateTime.now()): Long {
        val nextMidnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT)
        return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(0L)
    }

    /** "2h 30m", "45m" - shared by the editor, the overlay and onboarding. */
    fun formatBudget(minutes: Int): String {
        if (minutes <= 0) return "Unlimited"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}
