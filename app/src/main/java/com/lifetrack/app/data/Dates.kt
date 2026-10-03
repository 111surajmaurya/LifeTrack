package com.lifetrack.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val pretty: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val longDay = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun today(): String = LocalDate.now().format(iso)
    fun parse(date: String): LocalDate = LocalDate.parse(date, iso)
    fun format(date: LocalDate): String = date.format(iso)
    fun shift(date: String, days: Long): String = parse(date).plusDays(days).format(iso)
    fun isToday(date: String) = date == today()
    fun isFuture(date: String) = parse(date).isAfter(LocalDate.now())

    fun label(date: String): String = when (date) {
        today() -> "Today"
        shift(today(), -1) -> "Yesterday"
        shift(today(), 1) -> "Tomorrow"
        else -> parse(date).format(pretty)
    }

    /** "Monday, 8 September 2026" - the unambiguous form, for a detail sheet's subtitle. */
    fun longLabel(date: String): String = parse(date).format(longDay)

    /** Inclusive list of ISO dates, oldest first. */
    fun range(from: String, to: String): List<String> {
        val start = parse(from)
        val end = parse(to)
        val n = ChronoUnit.DAYS.between(start, end)
        if (n < 0) return emptyList()
        return (0..n).map { start.plusDays(it).format(iso) }
    }

    /** The last [days] days ending today, oldest first. */
    fun lastDays(days: Int): List<String> = range(shift(today(), -(days - 1).toLong()), today())

    /** Monday of the week containing [date]. */
    fun weekStart(date: String = today()): String =
        parse(date).with(DayOfWeek.MONDAY).format(iso)

    fun monthStart(date: String = today()): String = parse(date).withDayOfMonth(1).format(iso)

    fun monthEnd(date: String = today()): String {
        val d = parse(date)
        return YearMonth.of(d.year, d.month).atEndOfMonth().format(iso)
    }

    fun monthTitle(date: String): String = parse(date).format(monthTitle)

    fun shiftMonth(date: String, months: Long): String =
        parse(date).withDayOfMonth(1).plusMonths(months).format(iso)

    /**
     * Every cell of a Monday-first month grid, with nulls for the leading/trailing blanks.
     * Used by the habit calendar.
     */
    fun monthGrid(date: String): List<String?> {
        val first = parse(date).withDayOfMonth(1)
        val lead = first.dayOfWeek.value - 1              // Monday = 0
        val length = YearMonth.of(first.year, first.month).lengthOfMonth()
        val cells = ArrayList<String?>(42)
        repeat(lead) { cells.add(null) }
        (0 until length).forEach { cells.add(first.plusDays(it.toLong()).format(iso)) }
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    fun dayOfMonth(date: String): Int = parse(date).dayOfMonth

    /** Single-letter weekday initials, Monday first. */
    val weekdayInitials: List<String> = listOf("M", "T", "W", "T", "F", "S", "S")

    fun weekdayInitial(date: String): String = weekdayInitials[parse(date).dayOfWeek.value - 1]

    fun shortDay(date: String): String =
        parse(date).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    /** Epoch millis for local midnight at the start of today. */
    fun startOfTodayMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun startOfDayMillis(date: String): Long =
        parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Next time a reminder should fire, as epoch millis, or null if no days are enabled.
     * Looks up to 7 days ahead so weekly patterns always resolve.
     */
    fun nextTrigger(hour: Int, minute: Int, daysMask: Int, now: LocalDateTime = LocalDateTime.now()): Long? {
        if (daysMask == 0) return null
        for (offset in 0L..7L) {
            val candidate = now.toLocalDate().plusDays(offset).atTime(hour, minute)
            val bit = 1 shl (candidate.dayOfWeek.value - 1)
            if (daysMask and bit != 0 && candidate.isAfter(now)) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return null
    }

    /** "Today 6:00 PM" / "Tomorrow 7:30 AM" / "Mon, 14 Sep 7:30 AM" - shown under each reminder. */
    fun whenLabel(epochMillis: Long): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        return "${label(dt.toLocalDate().format(iso))} ${dt.format(clock)}"
    }

    /** "in 3h 12m" - how long until an alarm goes off. */
    fun untilLabel(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val delta = ((epochMillis - now) / 60_000L).coerceAtLeast(0)
        val days = delta / (60 * 24)
        val hours = (delta % (60 * 24)) / 60
        val mins = delta % 60
        return when {
            days > 0 -> "in ${days}d ${hours}h"
            hours > 0 -> "in ${hours}h ${mins}m"
            mins > 0 -> "in ${mins}m"
            else -> "in under a minute"
        }
    }

    fun clockLabel(hour: Int, minute: Int): String =
        LocalDateTime.of(2000, 1, 1, hour, minute).format(clock)

    fun formatMinutes(min: Int): String = when {
        min < 60 -> "${min}m"
        min % 60 == 0 -> "${min / 60}h"
        else -> "${min / 60}h ${min % 60}m"
    }

    /**
     * Human duration from milliseconds. Under a minute stays in seconds so a 40-second
     * timer reads "40s" instead of being rounded up to a whole minute.
     */
    fun formatDuration(millis: Long): String {
        val total = (millis / 1000L).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val sec = total % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            m > 0 && sec > 0 -> "${m}m ${sec}s"
            m > 0 -> "${m}m"
            else -> "${sec}s"
        }
    }

    /** Compact "01:23:45" for a running timer. */
    fun stopwatch(millis: Long): String {
        val s = (millis / 1000L).coerceAtLeast(0)
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /**
     * "01:23:45.67" - the same clock with hundredths, which is what the habit timer shows.
     * Hundredths rather than raw milliseconds: three digits flickering at 60fps is unreadable.
     */
    fun stopwatchMillis(millis: Long): String {
        val ms = millis.coerceAtLeast(0)
        val s = ms / 1000L
        val hundredths = (ms % 1000L) / 10L
        return "%02d:%02d:%02d.%02d".format(s / 3600, (s % 3600) / 60, s % 60, hundredths)
    }

    /** Just the ".67" tail, so the UI can render it smaller than the rest of the clock. */
    fun hundredths(millis: Long): String = ".%02d".format((millis.coerceAtLeast(0) % 1000L) / 10L)
}
