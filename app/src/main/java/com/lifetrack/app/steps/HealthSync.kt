package com.lifetrack.app.steps

import android.content.Context
import android.os.RemoteException
import com.lifetrack.app.data.BodyMath
import com.lifetrack.app.data.DailyMetric
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.HourlySteps
import com.lifetrack.app.data.Metric
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Retention
import com.lifetrack.app.data.TrackingStart
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * Moves Health Connect's numbers into `daily_metrics`, which is what the charts actually read.
 * Copying rather than querying live means the history survives the provider being uninstalled,
 * the watch being unpaired, or permission being withdrawn.
 *
 * Nothing here throws: every call reports failure as `false` / `0` and leaves the plain-words
 * cause in [lastError] so the screen can say *why* instead of showing a convincing zero.
 */
class HealthSync(private val context: Context, private val repo: Repository) {

    private val health = HealthConnectSteps(context)

    /** Why the last call did nothing, in words a user can act on. Null when it worked. */
    var lastError: String? = null
        private set

    /** Distinct days written by the last [importHistory] - "imported 172 days" needs this, not rows. */
    var lastImportedDays: Int = 0
        private set

    suspend fun status(): HealthConnectSteps.Status = health.status()

    suspend fun hasPermission(): Boolean = try {
        health.hasPermission()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        lastError = reason(e)
        false
    }

    /** Metrics the user hasn't shared yet, so the screen can name them rather than just omit them. */
    suspend fun missingMetrics(): List<Metric> = try {
        health.missingMetrics()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        lastError = reason(e)
        emptyList()
    }

    /** Pull today's numbers into daily_metrics. Returns true if anything was written. */
    suspend fun syncToday(): Boolean {
        lastError = null
        if (!ready()) return false
        return try {
            // One date for both the query and the key, so a read that crosses midnight
            // can't file yesterday's totals under today.
            val date = LocalDate.now()
            val totals = withBurn(health.totalsFor(date), weightKg(), workouts(date, date)[Dates.format(date)])
            val today = Dates.format(date)
            totals.forEach { (metric, value) -> repo.putMetric(today, metric, value) }
            totals.isNotEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            lastError = reason(e)
            false
        }
    }

    /** True when Health Connect supports background reads and LifeTrack has been allowed them. */
    suspend fun backgroundAllowed(): Boolean = try {
        health.backgroundReadSupported() &&
            HealthConnectSteps.BACKGROUND_PERMISSION in health.grantedPermissions()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        false
    }

    fun backgroundSupported(): Boolean = runCatching { health.backgroundReadSupported() }.getOrDefault(false)

    /**
     * Pull steps per hour for the last [days] days into `hourly_steps`. Today and yesterday are
     * always re-read (yesterday's late evening may have synced from a watch after midnight);
     * older days are only fetched when nothing is stored for them, so opening the app after a
     * week away fills the week in once and then costs nothing.
     */
    suspend fun syncHours(days: Int = 2): Boolean {
        lastError = null
        if (days <= 0 || !ready()) return false
        return try {
            val today = LocalDate.now()
            val first = startClamp(today.minusDays((days - 1).toLong()))
            val have = repo.datesWithHourlySteps(Dates.format(first), Dates.format(today)).toSet()
            val fresh = setOf(Dates.format(today), Dates.format(today.minusDays(1)))
            var start: LocalDate? = null
            // Contiguous run from the first day that needs reading; the rest is cheap to re-read.
            var d = first
            while (!d.isAfter(today)) {
                val iso = Dates.format(d)
                if (iso in fresh || iso !in have) { start = d; break }
                d = d.plusDays(1)
            }
            val from = start ?: return true
            val rows = health.stepsByHour(from, today).map { (key, steps) ->
                HourlySteps(key.first, key.second, steps, source = "HEALTH_CONNECT")
            }
            if (rows.isNotEmpty()) repo.putHourlySteps(rows)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            lastError = reason(e)
            false
        }
    }

    /**
     * Re-read the daily totals for the last [days] days and overwrite what is stored. A day that
     * was only synced at lunchtime - because the app was last opened then - otherwise keeps its
     * lunchtime number for good; the watch's evening walk lands in Health Connect but never here.
     */
    suspend fun refreshRecent(days: Int): Boolean {
        lastError = null
        if (days <= 0 || !ready()) return false
        return try {
            val today = LocalDate.now()
            val weight = weightKg()
            val first = startClamp(today.minusDays((days - 1).toLong()))
            val gym = workouts(first, today)
            val rows = health.totalsByDay(first, today).flatMap { (date, totals) ->
                withBurn(totals, weight, gym[date]).map { (metric, value) -> DailyMetric(date, metric.key, value) }
            }
            if (rows.isNotEmpty()) repo.putMetrics(rows)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            lastError = reason(e)
            false
        }
    }

    /**
     * Backfill up to [days] of history on first connect. Returns rows written; [lastImportedDays]
     * carries how many days those rows covered.
     *
     * The range is walked in [CHUNK]-day requests because a six-month `aggregateGroupByPeriod` in
     * one call is a large IPC payload on a phone with a watch. Days already stored are left alone,
     * so re-running this is cheap and never overwrites the phone sensor's own history - except
     * today, which is still moving and is always refreshed.
     */
    suspend fun importHistory(days: Int = Retention.DAYS.toInt()): Int {
        lastError = null
        lastImportedDays = 0
        if (days <= 0 || !ready()) return 0
        return try {
            val today = LocalDate.now()
            val todayIso = Dates.today()
            val first = startClamp(today.minusDays((days - 1).toLong()))
            val stored = storedKeys(Dates.format(first), todayIso)
            val touched = HashSet<String>()
            val weight = weightKg()
            var rows = 0

            var start = first
            while (!start.isAfter(today)) {
                val end = minOf(start.plusDays(CHUNK - 1L), today)
                val batch = ArrayList<DailyMetric>()
                val gym = workouts(start, end)
                health.totalsByDay(start, end).forEach { (date, totals) ->
                    withBurn(totals, weight, gym[date]).forEach { (metric, value) ->
                        if (date == todayIso || (date to metric.key) !in stored) {
                            batch += DailyMetric(date, metric.key, value)
                        }
                    }
                }
                if (batch.isNotEmpty()) {
                    repo.putMetrics(batch)
                    rows += batch.size
                    batch.forEach { touched += it.date }
                }
                start = end.plusDays(1)
            }
            lastImportedDays = touched.size
            rows
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            lastError = reason(e)
            0
        }
    }

    private suspend fun weightKg(): Float = repo.settings.first().weightKg

    /** Workout calories by day; a refused read costs the workouts, not the whole sync. */
    private suspend fun workouts(from: LocalDate, to: LocalDate): Map<String, Double> = try {
        health.workoutKcalByDay(from, to)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        emptyMap()
    }

    /**
     * Calories burned by moving: the walking estimate from the day's steps, plus whatever logged
     * workouts other than walks and runs burned. Not the phone's all-day "active calories",
     * which on Samsung come out around three times what the walking costs.
     */
    private fun withBurn(totals: Map<Metric, Double>, weightKg: Float, workoutKcal: Double?): Map<Metric, Double> {
        val walked = BodyMath.walkingKcal(
            totals[Metric.Distance] ?: 0.0, totals[Metric.Steps] ?: 0.0, weightKg
        )
        val burn = walked + (workoutKcal ?: 0.0)
        if (burn <= 0) return totals
        return totals + (Metric.Burn to burn)
    }

    /** Never reach back past the day this install started tracking. */
    private fun startClamp(day: LocalDate): LocalDate =
        maxOf(day, Dates.parse(TrackingStart.date(context)))

    /** Sets [lastError] and returns false when Health Connect can't be read at all. */
    private suspend fun ready(): Boolean {
        val state = status()
        if (state != HealthConnectSteps.Status.AVAILABLE) {
            lastError = when (state) {
                HealthConnectSteps.Status.NOT_INSTALLED -> "Health Connect isn't installed on this phone."
                else -> "Health Connect needs an update before it will share data."
            }
            return false
        }
        if (!hasPermission()) {
            lastError = "LifeTrack hasn't been allowed to read your activity yet."
            return false
        }
        return true
    }

    /** `date to metric.key` for everything already in the table, so the import can skip it. */
    private suspend fun storedKeys(from: String, to: String): Set<Pair<String, String>> =
        Metric.entries.flatMapTo(HashSet()) { metric ->
            repo.metricSeries(metric, from, to).first().map { it.date to metric.key }
        }

    private fun reason(e: Throwable): String = when (e) {
        is SecurityException ->
            "Health Connect refused the read. Open it and allow LifeTrack to read your activity."
        is IllegalStateException -> "Health Connect isn't set up on this phone."
        is RemoteException -> "Health Connect didn't respond. Try again in a moment."
        is IOException -> "Couldn't read from Health Connect - it reported a storage error."
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    companion object {
        /** Read permissions for every metric the Activity tab shows. */
        val PERMISSIONS: Set<String> = HealthConnectSteps.READ_PERMISSIONS

        /** Days per aggregate request while backfilling. */
        private const val CHUNK = 30L
    }
}
