package com.lifetrack.app.export

import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Habit
import com.lifetrack.app.data.HabitDays
import com.lifetrack.app.data.HabitKind
import com.lifetrack.app.data.Meal
import com.lifetrack.app.data.Metric
import com.lifetrack.app.data.Portion
import com.lifetrack.app.data.Retention
import com.lifetrack.app.data.Slot
import kotlin.math.roundToLong

/** What can be downloaded. */
enum class ExportKind(val label: String, val fileStem: String) {
    Meals("Meals", "meals"),
    Habits("Habits", "habits"),
    Steps("Steps", "steps")
}

/** How far back. The longest is the whole [Retention] window - nothing older is kept. */
enum class ExportRange(val label: String, val short: String, val days: Int) {
    Week("Last 7 days", "7 days", 7),
    Month("Last 30 days", "30 days", 30),
    Year("Last 12 months", "12 months", Retention.DAYS.toInt());

    fun from(today: String = Dates.today()): String = Dates.shift(today, -(days - 1).toLong())
}

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    Csv("CSV", "csv", "text/csv"),
    Excel("Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    Pdf("PDF", "pdf", "application/pdf");

    companion object {
        fun forFileName(name: String): ExportFormat =
            entries.firstOrNull { name.endsWith(".${it.extension}") } ?: Csv
    }
}

/**
 * One table in an export: a sheet in Excel, a block in CSV, a section in the PDF.
 * Cells are `String` or `Number`; numbers stay numbers so a spreadsheet can add them up.
 */
data class ExportTable(val title: String, val headers: List<String>, val rows: List<List<Any>>)

/** A whole download: a title line, the period it covers, and its tables. */
data class ExportDoc(val title: String, val subtitle: String, val tables: List<ExportTable>) {
    val empty: Boolean get() = tables.all { it.rows.isEmpty() }
}

/**
 * Turns stored rows into [ExportTable]s. Pure: the caller loads the data, so the shape of every
 * file can be checked by a unit test without a database.
 */
object ExportTables {

    fun fileName(kind: ExportKind, range: ExportRange, format: ExportFormat, today: String = Dates.today()): String =
        "lifetrack-${kind.fileStem}-${range.from(today)}-to-$today.${format.extension}"

    fun subtitle(range: ExportRange, today: String = Dates.today()): String =
        "${range.label}: ${range.from(today)} to $today"

    // ------------------------------------------------------------ meals

    /** Every item eaten, then one line per day with each meal's calories. */
    fun meals(meals: List<Meal>): List<ExportTable> {
        val items = meals.sortedWith(compareBy({ it.date }, { it.slotType.ordinal }, { it.createdAt })).map { m ->
            listOf<Any>(
                m.date, m.slotType.label, m.name, round1(m.qty), Portion.from(m.portion).label,
                m.kcal, round1(m.protein), round1(m.fiber),
                round1(m.vitA), round1(m.vitC), round1(m.iron), round1(m.calcium)
            )
        }
        val days = meals.groupBy { it.date }.toSortedMap().map { (date, list) ->
            val perSlot = Slot.entries.map { slot -> list.filter { it.slotType == slot }.sumOf { it.kcal } }
            listOf<Any>(date) + perSlot + listOf<Any>(
                list.sumOf { it.kcal }, round1(list.sumOf { it.protein.toDouble() }),
                round1(list.sumOf { it.fiber.toDouble() }), list.size
            )
        }
        return listOf(
            ExportTable(
                "Meals",
                listOf(
                    "Date", "Meal", "Food", "Quantity", "Portion", "Calories (kcal)", "Protein (g)",
                    "Fibre (g)", "Vitamin A (µg)", "Vitamin C (mg)", "Iron (mg)", "Calcium (mg)"
                ),
                items
            ),
            ExportTable(
                "Daily totals",
                listOf("Date") + Slot.entries.map { "${it.label} (kcal)" } +
                    listOf("Total (kcal)", "Protein (g)", "Fibre (g)", "Items"),
                days
            )
        )
    }

    // ------------------------------------------------------------ habits

    /**
     * One line per habit per day, from the day the habit started being scored, then a summary
     * per habit. [values] is each habit's figure by date in its own unit (millis or ticks).
     * A day is Done, Missed (over without the goal - the same rule the calendar colours red),
     * or In progress (today).
     */
    fun habits(
        habits: List<Habit>,
        values: Map<Long, Map<String, Double>>,
        from: String,
        to: String,
        trackingStart: String,
        today: String = Dates.today()
    ): List<ExportTable> {
        val days = Dates.range(from, minOf(to, today))
        val daily = ArrayList<List<Any>>()
        val summary = ArrayList<List<Any>>()
        habits.forEach { habit ->
            val timed = habit.kindType == HabitKind.TIMED
            val goal = if (timed) habit.dailyGoalMin.toDouble() else habit.dailyTarget.toDouble()
            val unit = if (timed) "min" else "times"
            val byDate = values[habit.id].orEmpty()
            val first = HabitDays.firstDay(habit, trackingStart)
            var done = 0
            var missed = 0
            var total = 0.0
            days.filter { it >= first }.forEach { date ->
                val raw = byDate[date] ?: 0.0
                val amount = if (timed) raw / 60_000.0 else raw
                val met = goal > 0 && amount >= goal
                val status = when {
                    met -> "Done"
                    HabitDays.missed(date, met, first, today) -> "Missed"
                    else -> "In progress"
                }
                if (met) done++
                if (status == "Missed") missed++
                total += amount
                daily += listOf<Any>(date, habit.name, unit, round1(amount), goal.toLong(), status)
            }
            val judged = done + missed
            if (judged > 0 || total > 0) {
                summary += listOf<Any>(
                    habit.name, "${goal.toLong()} $unit a day", done, missed,
                    if (judged > 0) (done * 100.0 / judged).roundToLong() else 0L, round1(total), unit
                )
            }
        }
        return listOf(
            ExportTable(
                "Habits by day",
                listOf("Date", "Habit", "Unit", "Done", "Daily goal", "Status"),
                daily.sortedWith(compareBy({ it[0] as String }, { it[1] as String }))
            ),
            ExportTable(
                "Habit summary",
                listOf("Habit", "Goal", "Days done", "Days missed", "Success (%)", "Total", "Unit"),
                summary
            )
        )
    }

    // ------------------------------------------------------------ steps

    /** One line per day that has any activity figure, then the period's totals. */
    fun steps(metrics: Map<Metric, Map<String, Double>>, from: String, to: String): List<ExportTable> {
        fun on(metric: Metric, date: String) = metrics[metric]?.get(date)
        val dates = metrics.values.flatMap { it.keys }.filter { it in from..to }.toSortedSet()
        val rows = dates.map { date ->
            listOf<Any>(
                date,
                (on(Metric.Steps, date) ?: 0.0).roundToLong(),
                round2((on(Metric.Distance, date) ?: 0.0) / 1000.0),
                (on(Metric.Burn, date) ?: 0.0).roundToLong(),
                (on(Metric.ActiveMinutes, date) ?: 0.0).roundToLong()
            )
        }
        val steps = rows.map { it[1] as Long }
        val walked = steps.filter { it > 0 }
        val best = rows.maxByOrNull { it[1] as Long }
        val summary = if (rows.isEmpty()) emptyList() else listOf(
            listOf<Any>("Total steps", steps.sum()),
            listOf<Any>("Daily average (days with steps)", if (walked.isEmpty()) 0L else walked.sum() / walked.size),
            listOf<Any>("Best day", best?.let { "${it[0]} (${it[1]} steps)" } ?: "-"),
            listOf<Any>("Total distance (km)", round2(rows.sumOf { it[2] as Double })),
            listOf<Any>("Days recorded", rows.size)
        )
        return listOf(
            ExportTable(
                "Steps by day",
                listOf("Date", "Steps", "Distance (km)", "Calories burned (kcal)", "Active time (min)"),
                rows
            ),
            ExportTable("Steps summary", listOf("Measure", "Value"), summary)
        )
    }

    private fun round1(v: Number): Double = Math.round(v.toDouble() * 10) / 10.0
    private fun round2(v: Number): Double = Math.round(v.toDouble() * 100) / 100.0
}
