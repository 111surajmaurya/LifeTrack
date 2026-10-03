package com.lifetrack.app

import com.lifetrack.app.data.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DatesTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private fun local(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
    private fun millis(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()

    // ------------------------------------------------------------------ durations

    /**
     * The bug that started all of this: a timer stopped after three seconds was rounded up and
     * banked as a whole minute. Anything under a minute has to stay in seconds.
     */
    @Test fun `duration keeps seconds visible below a minute`() {
        assertEquals("0s", Dates.formatDuration(0))
        assertEquals("3s", Dates.formatDuration(3_000))
        assertEquals("40s", Dates.formatDuration(40_000))
        assertEquals("59s", Dates.formatDuration(59_999))
    }

    @Test fun `duration switches to minutes and hours as it grows`() {
        assertEquals("1m", Dates.formatDuration(60_000))
        assertEquals("1m 5s", Dates.formatDuration(65_000))
        assertEquals("59m 59s", Dates.formatDuration(3_599_000))
        assertEquals("1h", Dates.formatDuration(3_600_000))
        assertEquals("1h 1m", Dates.formatDuration(3_660_000))
        assertEquals("2h 30m", Dates.formatDuration(9_045_000))  // seconds drop off past an hour
    }

    @Test fun `negative durations read as zero rather than nonsense`() {
        assertEquals("0s", Dates.formatDuration(-5_000))
        assertEquals("00:00:00", Dates.stopwatch(-1))
        assertEquals("00:00:00.00", Dates.stopwatchMillis(-1))
    }

    @Test fun `stopwatch is zero padded`() {
        assertEquals("00:00:03", Dates.stopwatch(3_000))
        assertEquals("01:02:03", Dates.stopwatch(3_723_000))
        assertEquals("100:00:00", Dates.stopwatch(360_000_000))  // no wrap past 99 hours
    }

    @Test fun `stopwatch with hundredths tracks sub-second time`() {
        assertEquals("00:00:00.00", Dates.stopwatchMillis(0))
        assertEquals("00:00:00.05", Dates.stopwatchMillis(50))
        assertEquals("00:00:01.23", Dates.stopwatchMillis(1_234))
        assertEquals("01:02:03.45", Dates.stopwatchMillis(3_723_456))
    }

    @Test fun `hundredths tail matches the stopwatch it sits beside`() {
        assertEquals(".00", Dates.hundredths(0))
        assertEquals(".45", Dates.hundredths(3_723_456))
        assertEquals(".99", Dates.hundredths(999))
    }

    @Test fun `minutes format reads naturally`() {
        assertEquals("0m", Dates.formatMinutes(0))
        assertEquals("45m", Dates.formatMinutes(45))
        assertEquals("1h", Dates.formatMinutes(60))
        assertEquals("1h 5m", Dates.formatMinutes(65))
        assertEquals("2h", Dates.formatMinutes(120))
    }

    // ------------------------------------------------------------------ dates

    @Test fun `shifting crosses month and year boundaries`() {
        assertEquals("2026-10-01", Dates.shift("2026-09-30", 1))
        assertEquals("2026-08-31", Dates.shift("2026-09-01", -1))
        assertEquals("2027-01-01", Dates.shift("2026-12-31", 1))
        assertEquals("2024-02-29", Dates.shift("2024-02-28", 1))   // leap year
    }

    @Test fun `range is inclusive at both ends and ordered oldest first`() {
        val r = Dates.range("2026-09-08", "2026-09-11")
        assertEquals(listOf("2026-09-08", "2026-09-09", "2026-09-10", "2026-09-11"), r)
        assertEquals(listOf("2026-09-08"), Dates.range("2026-09-08", "2026-09-08"))
        assertTrue("a backwards range is empty, not an exception", Dates.range("2026-09-11", "2026-09-08").isEmpty())
    }

    @Test fun `lastDays ends today and has the length asked for`() {
        val days = Dates.lastDays(7)
        assertEquals(7, days.size)
        assertEquals(Dates.today(), days.last())
        assertEquals(Dates.shift(Dates.today(), -6), days.first())
    }

    @Test fun `relative labels only apply to the three days around today`() {
        assertEquals("Today", Dates.label(Dates.today()))
        assertEquals("Yesterday", Dates.label(Dates.shift(Dates.today(), -1)))
        assertEquals("Tomorrow", Dates.label(Dates.shift(Dates.today(), 1)))
        val far = Dates.label(Dates.shift(Dates.today(), -30))
        assertFalse(far in listOf("Today", "Yesterday", "Tomorrow"))
    }

    @Test fun `today is neither future nor past`() {
        assertTrue(Dates.isToday(Dates.today()))
        assertFalse(Dates.isFuture(Dates.today()))
        assertTrue(Dates.isFuture(Dates.shift(Dates.today(), 1)))
        assertFalse(Dates.isFuture(Dates.shift(Dates.today(), -1)))
    }

    @Test fun `week starts on Monday`() {
        // 2026-09-10 is a Thursday.
        assertEquals("2026-09-07", Dates.weekStart("2026-09-10"))
        assertEquals("2026-09-07", Dates.weekStart("2026-09-07"))   // Monday is its own start
        assertEquals("2026-09-07", Dates.weekStart("2026-09-13"))   // Sunday belongs to that week
    }

    @Test fun `month bounds cover the whole month including February`() {
        assertEquals("2026-09-01", Dates.monthStart("2026-09-10"))
        assertEquals("2026-09-30", Dates.monthEnd("2026-09-10"))
        assertEquals("2026-02-28", Dates.monthEnd("2026-02-15"))
        assertEquals("2024-02-29", Dates.monthEnd("2024-02-15"))
    }

    @Test fun `month shifting clamps to the first so day-31 cannot skip a month`() {
        assertEquals("2026-10-01", Dates.shiftMonth("2026-09-30", 1))
        assertEquals("2026-08-01", Dates.shiftMonth("2026-09-30", -1))
        // The classic off-by-one: 31 Jan + 1 month must land in February, not March.
        assertEquals("2026-02-01", Dates.shiftMonth("2026-01-31", 1))
    }

    // ------------------------------------------------------------------ calendar grid

    @Test fun `month grid is whole weeks with blanks padding both ends`() {
        val grid = Dates.monthGrid("2026-09-15")
        assertEquals(0, grid.size % 7)
        // September 2026 starts on a Tuesday, so exactly one leading blank.
        assertEquals(1, grid.takeWhile { it == null }.size)
        assertEquals("2026-09-01", grid[1])
        assertEquals(30, grid.filterNotNull().size)
        assertEquals("2026-09-30", grid.filterNotNull().last())
    }

    @Test fun `a month starting on Monday has no leading blanks`() {
        // 1 June 2026 is a Monday.
        val grid = Dates.monthGrid("2026-06-01")
        assertNotNull(grid.first())
        assertEquals("2026-06-01", grid.first())
    }

    @Test fun `grid days stay in order and inside the month`() {
        val days = Dates.monthGrid("2026-02-10").filterNotNull()
        assertEquals(28, days.size)
        assertEquals(days.sorted(), days)
        assertTrue(days.all { it.startsWith("2026-02") })
    }

    @Test fun `weekday initials are Monday first and line up with the grid`() {
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), Dates.weekdayInitials)
        assertEquals("M", Dates.weekdayInitial("2026-09-07"))   // Monday
        assertEquals("S", Dates.weekdayInitial("2026-09-13"))   // Sunday
    }

    @Test fun `day of month is read from the date not the index`() {
        assertEquals(1, Dates.dayOfMonth("2026-09-01"))
        assertEquals(30, Dates.dayOfMonth("2026-09-30"))
    }

    // ------------------------------------------------------------------ reminders

    @Test fun `reminder later today fires today`() {
        val now = LocalDateTime.of(2026, 9, 9, 10, 0)   // Wednesday
        assertEquals(LocalDateTime.of(2026, 9, 9, 18, 0), local(Dates.nextTrigger(18, 0, 0b1111111, now)!!))
    }

    @Test fun `reminder already passed today moves to tomorrow`() {
        val now = LocalDateTime.of(2026, 9, 9, 19, 0)
        assertEquals(LocalDateTime.of(2026, 9, 10, 18, 0), local(Dates.nextTrigger(18, 0, 0b1111111, now)!!))
    }

    @Test fun `a reminder set for this exact minute waits for the next occurrence`() {
        // isAfter, not isSameOrAfter: firing "now" would double-fire the alarm that just rang.
        val now = LocalDateTime.of(2026, 9, 9, 18, 0)
        assertEquals(LocalDateTime.of(2026, 9, 10, 18, 0), local(Dates.nextTrigger(18, 0, 0b1111111, now)!!))
    }

    @Test fun `weekday-only reminder on Friday evening skips to Monday`() {
        val now = LocalDateTime.of(2026, 9, 11, 20, 0)  // Friday
        assertEquals(LocalDateTime.of(2026, 9, 14, 7, 30), local(Dates.nextTrigger(7, 30, 0b0011111, now)!!))
    }

    @Test fun `weekend-only reminder on Monday waits for Saturday`() {
        val now = LocalDateTime.of(2026, 9, 7, 9, 0)    // Monday
        assertEquals(LocalDateTime.of(2026, 9, 12, 9, 0), local(Dates.nextTrigger(9, 0, 0b1100000, now)!!))
    }

    @Test fun `a single-day reminder repeats a week later`() {
        val now = LocalDateTime.of(2026, 9, 7, 12, 0)   // Monday, after the 9am slot
        assertEquals(LocalDateTime.of(2026, 9, 14, 9, 0), local(Dates.nextTrigger(9, 0, 0b0000001, now)!!))
    }

    @Test fun `no days selected gives no trigger`() {
        assertNull(Dates.nextTrigger(9, 0, 0))
        assertNotNull(Dates.nextTrigger(9, 0, 0b1111111))
    }

    @Test fun `midnight reminders resolve like any other time`() {
        val now = LocalDateTime.of(2026, 9, 9, 23, 30)
        assertEquals(LocalDateTime.of(2026, 9, 10, 0, 0), local(Dates.nextTrigger(0, 0, 0b1111111, now)!!))
    }

    @Test fun `until label counts down in the right unit`() {
        val now = System.currentTimeMillis()
        assertEquals("in under a minute", Dates.untilLabel(now + 30_000, now))
        assertEquals("in 45m", Dates.untilLabel(now + 45 * 60_000L, now))
        assertEquals("in 3h 12m", Dates.untilLabel(now + (3 * 60 + 12) * 60_000L, now))
        assertEquals("in 2d 1h", Dates.untilLabel(now + (49 * 60) * 60_000L, now))
    }

    @Test fun `a time already past does not count backwards`() {
        val now = System.currentTimeMillis()
        assertEquals("in under a minute", Dates.untilLabel(now - 60_000, now))
    }

    @Test fun `when label names the day and the time`() {
        val at = millis(LocalDate.now().atTime(18, 0))
        assertTrue(Dates.whenLabel(at).startsWith("Today"))
        val tomorrow = millis(LocalDate.now().plusDays(1).atTime(7, 30))
        assertTrue(Dates.whenLabel(tomorrow).startsWith("Tomorrow"))
    }

    @Test fun `start of day is midnight local`() {
        val start = Dates.startOfDayMillis(Dates.today())
        assertEquals(Dates.startOfTodayMillis(), start)
        assertEquals(0, local(start).hour)
        assertEquals(0, local(start).minute)
    }
}
