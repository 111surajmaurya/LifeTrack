package com.lifetrack.app.data

import androidx.room.Entity
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Steps in one clock hour of one day: what draws "when did I walk" on the Activity tab and
 * what decides whether "walk after lunch" happened.
 */
@Entity(tableName = "hourly_steps", primaryKeys = ["date", "hour"])
data class HourlySteps(
    val date: String,
    val hour: Int,
    val steps: Double,
    val source: String = "HEALTH_CONNECT"
)

object StepSplit {

    /**
     * Steps the hardware counter gained since the last reading. The counter is cumulative since
     * boot, so a drop means the phone restarted and everything on it now is new. With no
     * previous reading there is nothing to subtract from, and guessing would invent a walk.
     * [rebooted] covers a restart the numbers alone can't show (the new count already past the old).
     */
    fun delta(previous: Long, current: Long, rebooted: Boolean = false): Long = when {
        previous < 0 -> 0
        rebooted || current < previous -> current
        else -> current - previous
    }

    /**
     * Spread [steps] evenly over the time between two readings, cut at every clock hour, and
     * return `(date, hour) -> steps`. The background job reads every 15 minutes or so, so the
     * smear is at most a quarter of an hour; after a long gap (phone off, Doze) it is the
     * honest best guess rather than dumping the lot into the hour the phone woke up.
     */
    fun spread(
        steps: Long,
        fromMillis: Long,
        toMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<Pair<String, Int>, Double> {
        if (steps <= 0) return emptyMap()
        val end = Instant.ofEpochMilli(toMillis).atZone(zone)
        if (fromMillis <= 0 || toMillis <= fromMillis) {
            return mapOf((Dates.format(end.toLocalDate()) to end.hour) to steps.toDouble())
        }
        // Walked in real instants, not local clock times, so a DST change or a time-zone hop
        // neither stretches the gap (counting steps twice) nor runs it backwards (losing them).
        val total = (toMillis - fromMillis).toDouble()
        val out = LinkedHashMap<Pair<String, Int>, Double>()
        var cursor = Instant.ofEpochMilli(fromMillis).atZone(zone)
        while (cursor.toInstant().isBefore(end.toInstant())) {
            val nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            val sliceEnd = if (nextHour.toInstant().isBefore(end.toInstant())) nextHour else end
            val millis = Duration.between(cursor.toInstant(), sliceEnd.toInstant()).toMillis()
            val key = Dates.format(cursor.toLocalDate()) to cursor.hour
            out[key] = (out[key] ?: 0.0) + steps * (millis / total)
            cursor = sliceEnd
        }
        return out
    }
}
