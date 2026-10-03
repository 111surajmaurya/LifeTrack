package com.lifetrack.app.data

import android.content.Context

/**
 * When routine tracking began. Without it every day before the routine existed - and every
 * item earlier on the first day - would score as a miss, which is a lie and a discouraging one.
 * Kept in preferences rather than a table: it is written once and never changes.
 */
object RoutineStart {
    private const val PREFS = "routine"
    private const val KEY = "started_at"

    /** Records now as the start, the first time only. Returns the stored start. */
    fun ensure(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getLong(KEY, 0L)
        if (existing > 0) return existing
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY, now).apply()
        return now
    }
}
