package com.lifetrack.app.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.lifetrack.app.data.Dates

/**
 * Reads how long each app was in the foreground today.
 *
 * Uses the event stream (RESUMED/PAUSED pairs) rather than queryUsageStats(),
 * which is bucketed and often over-counts. Requires the "Usage access" special permission.
 */
object UsageReader {
    // Event type values are stable across versions; the named constants were renamed in API 29.
    private const val RESUMED = 1   // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val PAUSED = 2    // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val STOPPED = 23  // ACTIVITY_STOPPED (API 29+)

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun hasPermission(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Foreground milliseconds per package for today, for the given packages only. */
    fun foregroundTodayMillis(context: Context, packages: Set<String>): Map<String, Long> =
        foregroundMillisBetween(context, packages, Dates.startOfTodayMillis(), System.currentTimeMillis())

    /**
     * Foreground milliseconds per package inside an arbitrary window, from the same
     * RESUMED/PAUSED pairing as [foregroundTodayMillis].
     *
     * Only useful while the window still sits inside the event stream's retention — the OS
     * drops raw events after a few days. Past that, use [dailyBuckets].
     */
    fun foregroundMillisBetween(
        context: Context,
        packages: Set<String>,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        if (!hasPermission(context) || packages.isEmpty() || endMillis <= startMillis) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        // A session still open when the window closes counts only up to the window edge.
        val cap = minOf(endMillis, System.currentTimeMillis())

        val totals = HashMap<String, Long>()
        val openSince = HashMap<String, Long>()
        try {
            val events = usm.queryEvents(startMillis, endMillis)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                if (pkg !in packages) continue
                when (e.eventType) {
                    RESUMED -> openSince.putIfAbsent(pkg, e.timeStamp)
                    PAUSED, STOPPED -> {
                        openSince.remove(pkg)?.let { since ->
                            totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - since).coerceAtLeast(0)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            // Some OEM builds throw out of queryEvents even with the op allowed.
            return emptyMap()
        }
        openSince.forEach { (pkg, since) ->
            totals[pkg] = (totals[pkg] ?: 0L) + (cap - since).coerceAtLeast(0)
        }
        return totals
    }

    /**
     * True when [pkg]'s last event in the past few hours is a RESUMED with no PAUSED after it,
     * i.e. it is on screen right now. Used to re-check a limit only while the app is still open.
     */
    fun isForeground(context: Context, pkg: String): Boolean {
        if (!hasPermission(context)) return false
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return false
        val now = System.currentTimeMillis()
        var open = false
        try {
            val events = usm.queryEvents(now - 6 * 60 * 60 * 1000L, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.packageName != pkg) continue
                when (e.eventType) {
                    RESUMED -> open = true
                    PAUSED, STOPPED -> open = false
                }
            }
        } catch (t: Throwable) {
            return false
        }
        return open
    }

    /**
     * Per-day foreground milliseconds — `date -> package -> millis` — for the [days] days
     * *before* today, oldest first.
     *
     * Source is `queryUsageStats(INTERVAL_DAILY)`, which the OS keeps for weeks after the raw
     * event stream is gone. It is deliberately coarser: buckets can overlap the day boundary and
     * `totalTimeInForeground` tends to run high, so today is never taken from here — the event
     * stream in [foregroundTodayMillis] owns today, and this only fills history behind it.
     */
    fun dailyBuckets(context: Context, packages: Set<String>, days: Int): Map<String, Map<String, Long>> {
        if (!hasPermission(context) || packages.isEmpty() || days <= 0) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val today = Dates.today()

        val out = LinkedHashMap<String, Map<String, Long>>()
        for (back in days downTo 1) {
            val date = Dates.shift(today, -back.toLong())
            val start = Dates.startOfDayMillis(date)
            val end = start + DAY_MILLIS - 1
            val perPackage = HashMap<String, Long>()
            try {
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                stats?.forEach { s ->
                    val pkg = s.packageName ?: return@forEach
                    if (pkg !in packages) return@forEach
                    val fg = s.totalTimeInForeground
                    if (fg > 0) perPackage[pkg] = (perPackage[pkg] ?: 0L) + fg
                }
            } catch (t: Throwable) {
                continue
            }
            if (perPackage.isNotEmpty()) out[date] = perPackage
        }
        return out
    }
}
