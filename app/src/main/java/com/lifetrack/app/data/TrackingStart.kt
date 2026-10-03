package com.lifetrack.app.data

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/**
 * When this install started tracking - the first time the app was opened. Nothing before it is
 * shown or scored:
 *  - the routine doesn't count days (or first-day items) from before it as misses,
 *  - Health Connect and Android's own usage records are not imported from before it, even though
 *    both hold months of history; a fresh install starts empty, as a fresh install should,
 *  - anything already stored from before it is pruned on launch ([Repository.pruneBeforeStart]).
 *
 * Kept in preferences rather than a table: it is written once and never changes. The file and
 * key are the ones the routine used first, so installs that already had a start keep it.
 */
object TrackingStart {
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

    /** The start as an ISO date - the first day that counts. */
    fun date(context: Context): String = dateOf(ensure(context))

    fun dateOf(millis: Long): String =
        Dates.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())

    /** [from] moved forward to the start date if it is earlier. ISO dates compare as strings. */
    fun clamp(context: Context, from: String): String = maxOf(from, date(context))
}
