package com.lifetrack.app.ui.activity

import androidx.compose.ui.graphics.Color
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Metric
import com.lifetrack.app.data.Settings
import com.lifetrack.app.ui.theme.Accents
import kotlin.math.roundToInt

/**
 * How each [Metric] is written down and judged. Both Activity screens go through here so a
 * distance never reads "4213 m" on one screen and "4.21 km" on the other.
 *
 * Values are always stored in the unit the `Metric` enum declares (metres, kcal, minutes, bpm);
 * only the display converts.
 */

/** The number on its own - for a hero, where the unit sits underneath it. */
internal fun Metric.bare(value: Double): String = when (this) {
    Metric.Steps -> "%,.0f".format(value)
    Metric.Distance -> "%.2f".format(value / 1000.0)
    Metric.Burn -> "%,.0f".format(value)
    Metric.HeartRate -> "%.0f".format(value)
    Metric.ActiveMinutes, Metric.Sleep -> Dates.formatMinutes(value.roundToInt())
}

/** Tile-width label. `Metric.label` says "Resting heart rate", which clips at this size. */
internal val Metric.shortLabel: String
    get() = when (this) {
        Metric.Steps -> "Steps"
        Metric.Distance -> "Distance"
        Metric.Burn -> "Burn"
        Metric.ActiveMinutes -> "Active"
        Metric.HeartRate -> "Heart rate"
        Metric.Sleep -> "Sleep"
    }

/** The unit to print beside [bare]. Durations already carry theirs ("7h 20m"), so they get none. */
internal fun Metric.displayUnit(): String = when (this) {
    Metric.Steps -> "steps"
    Metric.Distance -> "km"
    Metric.Burn -> "kcal"
    Metric.HeartRate -> "bpm"
    Metric.ActiveMinutes, Metric.Sleep -> ""
}

/** Number and unit together - tiles, list rows, chart captions. */
internal fun Metric.pretty(value: Double): String {
    val unit = displayUnit()
    return if (unit.isEmpty()) bare(value) else "${bare(value)} $unit"
}

/** Charts plot kilometres, not metres, so the axis and the goal line stay readable. */
internal fun Metric.chartValue(value: Double): Double =
    if (this == Metric.Distance) value / 1000.0 else value

/** Labels a value that has already been through [chartValue] - bar captions, axis ticks. */
internal fun Metric.chartLabel(value: Double): String =
    if (this == Metric.Distance) "%.2f".format(value) else bare(value)

/** The goal in the same unit as [chartValue], or null for the metrics nobody sets a target for. */
internal fun Metric.chartGoal(settings: Settings): Double? = when (this) {
    Metric.Steps -> settings.stepGoal.toDouble()
    Metric.Distance -> settings.distanceGoalKm.toDouble()
    Metric.Burn -> settings.burnGoalKcal.toDouble()
    Metric.ActiveMinutes, Metric.HeartRate, Metric.Sleep -> null
}

/** The goal in stored units, for "did this day count" arithmetic. */
internal fun Metric.storedGoal(settings: Settings): Double? = when (this) {
    Metric.Distance -> settings.distanceGoalKm.toDouble() * 1000.0
    else -> chartGoal(settings)
}

/**
 * Which direction of change is the good one, for [com.lifetrack.app.ui.components.DeltaChip].
 *
 * Heart rate is the inversion: a resting rate that drifts *down* is the fitter one, so a fall
 * is green. Sleep counts up as better because almost nobody's problem is too much of it - the
 * chip deliberately does not scold you for a long night, it only flags losing sleep.
 */
internal val Metric.higherIsBetter: Boolean
    get() = this != Metric.HeartRate

/**
 * One accent per tab, so colour still means state: the tab's own blue normally, green once a
 * goal is met. Metrics without a goal never turn green - there is nothing to have reached.
 */
internal fun Metric.tint(accents: Accents, value: Double, settings: Settings): Color {
    val goal = storedGoal(settings) ?: return accents.activity
    return if (goal > 0 && value >= goal) accents.positive else accents.activity
}
