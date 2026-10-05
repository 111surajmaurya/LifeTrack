package com.lifetrack.app.screentime

import android.content.Context
import android.content.Intent
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.TrackingStart
import kotlinx.coroutines.flow.first

/**
 * Turns what [UsageReader] can see right now into the day-by-day history the charts read.
 *
 * The OS keeps usage data for itself; nothing is stored until we snapshot it here, so
 * [snapshotToday] is called every time a screen-time screen resumes. Nothing in here throws:
 * usage access is a special permission the user can revoke at any moment, and a missing
 * snapshot must never take a screen down with it.
 */
object UsageSync {

    fun hasPermission(context: Context): Boolean = UsageReader.hasPermission(context)

    fun settingsIntent(): Intent = UsageReader.settingsIntent()

    /**
     * Snapshot today's foreground minutes for the tracked apps into `app_usage_days`.
     *
     * Apps with no time today are written as zero on purpose: a stored zero is what lets the
     * pattern stats tell "a day you did not open it" apart from "a day before we were tracking".
     */
    suspend fun snapshotToday(context: Context, repo: Repository): Boolean = try {
        val app = context.applicationContext
        val packages = repo.trackedApps.first().map { it.packageName }.toSet()
        if (packages.isEmpty() || !UsageReader.hasPermission(app)) {
            false
        } else {
            val today = Dates.today()
            val millis = UsageReader.foregroundTodayMillis(app, packages)
            val minutes = packages.associateWith { toMinutes(millis[it] ?: 0L) }
            repo.recordUsage(today, minutes)
            // Yesterday's last stretch - after its final snapshot, up to midnight - can only be
            // picked up from here, while the event stream still has it.
            val yesterday = Dates.shift(today, -1)
            if (yesterday >= TrackingStart.date(app)) {
                val late = UsageReader.foregroundMillisBetween(
                    app, packages, Dates.startOfDayMillis(yesterday), Dates.startOfDayMillis(today)
                )
                if (late.isNotEmpty()) {
                    repo.recordUsage(yesterday, packages.associateWith { toMinutes(late[it] ?: 0L) })
                }
            }
            true
        }
    } catch (t: Throwable) {
        false
    }

    /**
     * Backfill older days from the OS's own daily buckets, for days we have nothing stored for.
     * A day already in the database is never overwritten — the stored value came from the finer
     * event stream and is the better number. Returns the number of rows written.
     */
    suspend fun backfill(context: Context, repo: Repository, days: Int = 30): Int = try {
        val app = context.applicationContext
        val packages = repo.trackedApps.first().map { it.packageName }.toSet()
        if (packages.isEmpty() || !UsageReader.hasPermission(app)) {
            0
        } else {
            val today = Dates.today()
            val from = Dates.shift(today, -days.toLong())
            val stored = repo.usageTotalPerDay(from, today).first().map { it.date }.toSet()
            var written = 0
            UsageReader.dailyBuckets(app, packages, days).forEach { (date, byPackage) ->
                // Android keeps weeks of usage; a fresh install still starts on its own first day.
                if (date in stored || date < TrackingStart.date(app)) return@forEach
                val minutes = packages.associateWith { toMinutes(byPackage[it] ?: 0L) }
                repo.recordUsage(date, minutes)
                written += minutes.size
            }
            written
        }
    } catch (t: Throwable) {
        0
    }

    /** Rounds to the nearest minute so a 40-second glance reads as "1m" rather than vanishing. */
    private fun toMinutes(millis: Long): Int =
        ((millis.coerceAtLeast(0L) + 30_000L) / 60_000L).toInt()
}
