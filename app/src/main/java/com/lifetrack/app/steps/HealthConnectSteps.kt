package com.lifetrack.app.steps

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.lifetrack.app.data.Metric
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.Period
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads daily totals from Health Connect (Samsung Health, Google Fit and watches all write there).
 * Health Connect de-duplicates overlapping sources, so a phone + watch pair isn't counted twice.
 *
 * Everything comes back through [totalsFor] / [totalsByDay] keyed by [Metric], already converted
 * into the unit each `Metric` declares, so callers never touch `Length` / `Energy` / `Duration`.
 * Reads **throw** on failure on purpose: a denied permission must not look like a day of zero steps.
 */
class HealthConnectSteps(private val context: Context) {

    /** Why steps aren't showing, in the terms the user can act on. */
    enum class Status { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

    val readPermission: String = HealthPermission.getReadPermission(StepsRecord::class)

    fun status(): Status = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UPDATE_REQUIRED
        else -> Status.NOT_INSTALLED
    }

    fun isAvailable(): Boolean = status() == Status.AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /** True once steps specifically are shared - the one metric the Activity tab can't do without. */
    suspend fun hasPermission(): Boolean =
        isAvailable() && readPermission in client.permissionController.getGrantedPermissions()

    suspend fun grantedPermissions(): Set<String> =
        if (isAvailable()) client.permissionController.getGrantedPermissions() else emptySet()

    /**
     * Whether this Health Connect can hand data to an app that isn't on screen. Without it every
     * read from the background job is refused, and steps only move when the app is opened.
     */
    fun backgroundReadSupported(): Boolean = isAvailable() && runCatching {
        @Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    /**
     * Steps per clock hour over [from]..[to] inclusive, keyed `(iso date, hour)`. Hours with no
     * records are absent. One request covers a few days of 24 buckets each, which is small.
     */
    suspend fun stepsByHour(from: LocalDate, to: LocalDate): Map<Pair<String, Int>, Double> {
        if (from.isAfter(to)) return emptyMap()
        val zone = ZoneId.systemDefault()
        val groups = client.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()
                ),
                timeRangeSlicer = Duration.ofHours(1)
            )
        )
        val out = LinkedHashMap<Pair<String, Int>, Double>()
        groups.forEach { bucket ->
            val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: return@forEach
            if (steps <= 0) return@forEach
            val at = bucket.startTime.atZone(zone)
            out[at.toLocalDate().toString() to at.hour] = steps.toDouble()
        }
        return out
    }

    /** Metrics with no granted source, so the UI can name them instead of showing an empty tile. */
    suspend fun missingMetrics(): List<Metric> {
        val granted = grantedPermissions()
        return Metric.entries.filter { m -> SOURCES.none { it.metric == m && it.permission in granted } }
    }

    /** Every metric that has data on [date]. Metrics with no records that day are simply absent. */
    suspend fun totalsFor(date: LocalDate): Map<Metric, Double> {
        val sources = grantedSources()
        if (sources.isEmpty()) return emptyMap()
        val filter = TimeRangeFilter.between(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
        val out = LinkedHashMap<Metric, Double>()

        try {
            collect(sources, client.aggregate(request(sources, filter)), out)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (whole: Exception) {
            if (sources.size == 1) throw whole
            var failure: Exception = whole
            // One record type the provider refuses must cost one tile, not the whole screen.
            for (s in sources) {
                try {
                    collect(listOf(s), client.aggregate(request(listOf(s), filter)), out)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    failure = e
                }
            }
            if (out.isEmpty()) throw failure
        }
        return out
    }

    /**
     * Day-by-day totals over [from]..[to] inclusive, keyed by ISO date. Days with no records at
     * all are absent rather than zero, so the importer never invents a day of no walking.
     */
    suspend fun totalsByDay(from: LocalDate, to: LocalDate): Map<String, Map<Metric, Double>> {
        val sources = grantedSources()
        if (sources.isEmpty() || from.isAfter(to)) return emptyMap()
        val filter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        val out = LinkedHashMap<String, LinkedHashMap<Metric, Double>>()

        try {
            collect(sources, buckets(sources, filter), out)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (whole: Exception) {
            if (sources.size == 1) throw whole
            var failure: Exception = whole
            for (s in sources) {
                try {
                    collect(listOf(s), buckets(listOf(s), filter), out)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    failure = e
                }
            }
            if (out.isEmpty()) throw failure
        }
        return out
    }

    private suspend fun buckets(
        sources: List<Source>,
        filter: TimeRangeFilter
    ): List<AggregationResultGroupedByPeriod> = client.aggregateGroupByPeriod(
        AggregateGroupByPeriodRequest(
            metrics = sources.map { it.aggregate }.toSet(),
            timeRangeFilter = filter,
            timeRangeSlicer = Period.ofDays(1)
        )
    )

    private fun request(sources: List<Source>, filter: TimeRangeFilter) =
        AggregateRequest(sources.map { it.aggregate }.toSet(), filter)

    private fun collect(
        sources: List<Source>,
        result: AggregationResult,
        into: MutableMap<Metric, Double>
    ) {
        // SOURCES is ordered by preference, so the first source to answer wins the metric.
        sources.forEach { s ->
            if (s.metric !in into) s.read(result)?.let { into[s.metric] = it }
        }
    }

    private fun collect(
        sources: List<Source>,
        groups: List<AggregationResultGroupedByPeriod>,
        into: MutableMap<String, LinkedHashMap<Metric, Double>>
    ) {
        groups.forEach { bucket ->
            val day = bucket.startTime.toLocalDate().toString()
            val slot = into.getOrPut(day) { LinkedHashMap() }
            collect(sources, bucket.result, slot)
            if (slot.isEmpty()) into.remove(day)
        }
    }

    private suspend fun grantedSources(): List<Source> {
        val granted = grantedPermissions()
        return SOURCES.filter { it.permission in granted }
    }

    /**
     * One way to pull one [Metric] out of Health Connect. [Metric.Burn] has two, tried in order,
     * because a phone that only logs workouts has active calories but no all-day total.
     */
    private class Source(
        val metric: Metric,
        val aggregate: AggregateMetric<*>,
        val permission: String,
        val read: (AggregationResult) -> Double?
    )

    companion object {
        const val PACKAGE = "com.google.android.apps.healthdata"

        /** Health Connect's own screen, where the user can check that Samsung Health is syncing. */
        fun openIntent(): Intent =
            Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun installIntent(): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private val SOURCES: List<Source> = listOf(
            Source(
                Metric.Steps, StepsRecord.COUNT_TOTAL,
                HealthPermission.getReadPermission(StepsRecord::class)
            ) { it[StepsRecord.COUNT_TOTAL]?.toDouble() },

            Source(
                Metric.Distance, DistanceRecord.DISTANCE_TOTAL,
                HealthPermission.getReadPermission(DistanceRecord::class)
            ) { it[DistanceRecord.DISTANCE_TOTAL]?.inMeters },

            Source(
                Metric.Burn, TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
            ) { it[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories },

            // Fallback for the same metric: preferred order means this only lands if the total is absent.
            Source(
                Metric.Burn, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
            ) { it[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories },

            Source(
                Metric.ActiveMinutes, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                HealthPermission.getReadPermission(ExerciseSessionRecord::class)
            ) { it[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes()?.toDouble() },

            // Health Connect 1.1.0 has no daily "resting" aggregate, and READ_RESTING_HEART_RATE
            // isn't in the manifest, so this is the day's average beat - the closest honest number.
            Source(
                Metric.HeartRate, HeartRateRecord.BPM_AVG,
                HealthPermission.getReadPermission(HeartRateRecord::class)
            ) { it[HeartRateRecord.BPM_AVG]?.toDouble() },

            Source(
                Metric.Sleep, SleepSessionRecord.SLEEP_DURATION_TOTAL,
                HealthPermission.getReadPermission(SleepSessionRecord::class)
            ) { it[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toDouble() }
        )

        /** Read permission for every metric the Activity tab charts. */
        val READ_PERMISSIONS: Set<String> = SOURCES.map { it.permission }.toSet()

        /** Lets the 15-minute background job read steps while LifeTrack is closed. */
        const val BACKGROUND_PERMISSION: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    }
}
