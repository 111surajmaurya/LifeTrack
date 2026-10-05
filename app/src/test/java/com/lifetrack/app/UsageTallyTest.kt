package com.lifetrack.app

import com.lifetrack.app.data.StepSplit
import com.lifetrack.app.screentime.UsageReader
import com.lifetrack.app.screentime.UsageReader.Ev
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class UsageTallyTest {

    private val app = "com.example.feed"
    private fun ev(cls: String, type: Int, at: Long) = Ev(app, cls, type, at)

    @Test fun `moving between screens of one app keeps the clock running`() {
        // A resumed, A paused, B resumed, then A's late STOPPED: B must keep counting.
        val events = listOf(
            ev("A", 1, 0), ev("A", 2, 10_000), ev("B", 1, 10_050), ev("A", 23, 10_400), ev("B", 2, 70_000)
        )
        val total = UsageReader.tally(events, 0, 100_000)[app]!!
        assertEquals(69_950L, total)
    }

    @Test fun `a session opened before the window counts from the window start`() {
        val events = listOf(ev("A", 1, -50_000), ev("A", 2, 40_000))
        assertEquals(40_000L, UsageReader.tally(events, 0, 100_000)[app])
    }

    @Test fun `an app still open is counted up to the cap`() {
        val events = listOf(ev("A", 1, 20_000))
        assertEquals(30_000L, UsageReader.tally(events, 0, 50_000)[app])
    }

    @Test fun `a shutdown ends whatever was open`() {
        val events = listOf(ev("A", 1, 0), Ev("android", null, 26, 5_000), ev("A", 2, 90_000))
        assertEquals(5_000L, UsageReader.tally(events, 0, 100_000)[app])
    }

    @Test fun `nothing before the window and closed before it counts as nothing`() {
        val events = listOf(ev("A", 1, -50_000), ev("A", 2, -10_000))
        assertNull(UsageReader.tally(events, 0, 100_000)[app])
    }

    @Test fun `a reboot the counter can't show still counts every step as new`() {
        assertEquals(1_200, StepSplit.delta(300, 1_200, rebooted = true))
    }

    @Test fun `spreading steps across a spring-forward gap counts them once`() {
        val zone = ZoneId.of("America/New_York")
        // 01:30 EST to 03:30 EDT on 2026-03-08 is one real hour.
        val from = LocalDateTime.of(2026, 3, 8, 1, 30).atZone(zone).toInstant().toEpochMilli()
        val to = LocalDateTime.of(2026, 3, 8, 3, 30).atZone(zone).toInstant().toEpochMilli()
        val parts = StepSplit.spread(600, from, to, zone)
        assertEquals(600.0, parts.values.sum(), 0.001)
    }

    @Test fun `spreading steps across a fall-back hour doesn't lose them`() {
        val zone = ZoneId.of("America/New_York")
        // 01:50 EDT to 01:10 EST on 2026-11-01 is twenty real minutes.
        val from = LocalDateTime.of(2026, 11, 1, 1, 50).atZone(zone).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
        val to = LocalDateTime.of(2026, 11, 1, 1, 10).atZone(zone).withLaterOffsetAtOverlap().toInstant().toEpochMilli()
        val parts = StepSplit.spread(200, from, to, zone)
        assertEquals(200.0, parts.values.sum(), 0.001)
    }
}
