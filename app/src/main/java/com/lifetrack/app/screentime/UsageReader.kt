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
    private const val DEVICE_SHUTDOWN = 26
    private const val DEVICE_STARTUP = 27

    private const val LOOKBACK_MILLIS = 6L * 60 * 60 * 1000

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
        val events = ArrayList<Ev>()
        try {
            // Read from a few hours earlier so a session opened before the window (an app
            // left open over midnight) is known to be running when the window starts.
            val stream = usm.queryEvents(startMillis - LOOKBACK_MILLIS, endMillis)
            val e = UsageEvents.Event()
            while (stream.hasNextEvent()) {
                stream.getNextEvent(e)
                val pkg = e.packageName ?: continue
                val kind = e.eventType
                val device = kind == DEVICE_SHUTDOWN || kind == DEVICE_STARTUP
                if (!device && pkg !in packages) continue
                events += Ev(pkg, e.className, kind, e.timeStamp)
            }
        } catch (t: Throwable) {
            // Some OEM builds throw out of queryEvents even with the op allowed.
            return emptyMap()
        }
        return tally(events, startMillis, minOf(endMillis, System.currentTimeMillis()))
    }

    /** One raw usage event, kept apart from the framework type so [tally] can be tested. */
    data class Ev(val pkg: String, val cls: String?, val type: Int, val at: Long)

    /**
     * Foreground time per package inside [startMillis]..[capMillis] from time-ordered events.
     *
     * Activities are tracked one by one, not per package: moving from screen A to screen B in
     * the same app goes A paused, B resumed, then A *stopped* a moment later, and pairing by
     * package let that late stop end B's session after a few hundred milliseconds. A package
     * counts as in front while any of its activities is resumed.
     */
    fun tally(events: List<Ev>, startMillis: Long, capMillis: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val resumed = HashMap<String, MutableSet<String?>>()
        val since = HashMap<String, Long>()

        fun close(pkg: String, at: Long) {
            val from = since.remove(pkg) ?: return
            val ms = minOf(at, capMillis) - maxOf(from, startMillis)
            if (ms > 0) totals[pkg] = (totals[pkg] ?: 0L) + ms
        }

        for (e in events) {
            when (e.type) {
                RESUMED -> {
                    val open = resumed.getOrPut(e.pkg) { HashSet() }
                    if (open.isEmpty()) since[e.pkg] = e.at
                    open += e.cls
                }
                PAUSED, STOPPED -> {
                    val open = resumed[e.pkg] ?: continue
                    if (open.remove(e.cls) && open.isEmpty()) close(e.pkg, e.at)
                }
                DEVICE_SHUTDOWN, DEVICE_STARTUP -> {
                    // Nothing survives a restart: whatever was open ended when the phone went down.
                    since.keys.toList().forEach { close(it, e.at) }
                    resumed.clear()
                }
            }
        }
        since.keys.toList().forEach { close(it, capMillis) }
        return totals
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
            val end = Dates.startOfDayMillis(Dates.shift(date, 1))   // 23 or 25 h on a DST day
            // A calendar day overlaps two of the OS's daily buckets, which don't start at local
            // midnight. Adding both nearly doubled the day, so each package takes the one bucket
            // that covers most of it.
            val best = HashMap<String, Pair<Long, Long>>()   // package -> (overlap, foreground)
            try {
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end - 1)
                stats?.forEach { s ->
                    val pkg = s.packageName ?: return@forEach
                    if (pkg !in packages) return@forEach
                    val overlap = minOf(end, s.lastTimeStamp) - maxOf(start, s.firstTimeStamp)
                    val fg = s.totalTimeInForeground
                    if (fg <= 0 || overlap <= 0) return@forEach
                    if (overlap > (best[pkg]?.first ?: 0L)) best[pkg] = overlap to fg
                }
            } catch (t: Throwable) {
                continue
            }
            if (best.isNotEmpty()) out[date] = best.mapValues { it.value.second }
        }
        return out
    }
}
