package com.lifetrack.app

import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Routine
import com.lifetrack.app.data.RoutineEvidence
import com.lifetrack.app.data.RoutineItem
import com.lifetrack.app.data.RoutineKind
import com.lifetrack.app.data.RoutineLog
import com.lifetrack.app.data.RoutinePlan
import com.lifetrack.app.data.RoutineStatus
import com.lifetrack.app.data.Slot
import com.lifetrack.app.data.StepSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** The daily routine's rules: when things fire, what counts as a hit, and steps by the hour. */
class RoutineTest {

    private val zone = ZoneId.systemDefault()
    private fun at(date: String, h: Int, m: Int = 0) = LocalDateTime.of(Dates.parse(date), java.time.LocalTime.of(h, m))
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    private val wake = Routine.SEED.first { it.key == "wake" }.copy(id = 1)
    private val lunch = Routine.SEED.first { it.key == "lunch" }.copy(id = 4)
    private val walk = Routine.SEED.first { it.key == "walk" }.copy(id = 5)
    private val reading = Routine.SEED.first { it.key == "reading" }.copy(id = 9)
    private val plan = Routine.SEED.first { it.key == "plan" }.copy(id = 10)

    // ---------------------------------------------------------------- the seed

    @Test fun `seed is the day that was asked for`() {
        val byKey = Routine.SEED.associateBy { it.key }
        assertEquals(10, Routine.SEED.size)
        assertEquals(10, byKey.size)
        fun check(key: String, h: Int, m: Int, alarm: Boolean) {
            val i = byKey.getValue(key)
            assertEquals(key, h to m, i.hour to i.minute)
            assertEquals(key, alarm, i.isAlarm)
        }
        check("wake", 7, 0, true)
        check("workout", 7, 30, true)
        check("breakfast", 9, 30, false)
        check("lunch", 13, 30, false)
        check("walk", 14, 0, false)
        check("evening", 19, 0, true)
        check("learning", 20, 0, false)
        check("reading", 21, 45, false)
        check("plan", 22, 0, false)
        assertEquals(Slot.Dinner.name, byKey.getValue("dinner").slot)
    }

    // ---------------------------------------------------------------- next fire

    @Test fun `fires later today when the time is still ahead`() {
        val (date, at) = Routine.nextFire(wake, emptyMap(), at("2026-10-03", 6, 30))!!
        assertEquals("2026-10-03", date)
        assertEquals(millis(at("2026-10-03", 7)), at)
    }

    @Test fun `rolls to tomorrow once today's time has passed`() {
        val (date, _) = Routine.nextFire(wake, emptyMap(), at("2026-10-03", 7, 1))!!
        assertEquals("2026-10-04", date)
    }

    @Test fun `tomorrow's plan moves tomorrow only`() {
        val plans = mapOf("2026-10-04" to RoutinePlan("2026-10-04", 1, 5, 45, true))
        val (_, t) = Routine.nextFire(wake, plans, at("2026-10-03", 22))!!
        assertEquals(millis(at("2026-10-04", 5, 45)), t)
    }

    @Test fun `a day switched off in the plan skips to the day after`() {
        val plans = mapOf("2026-10-04" to RoutinePlan("2026-10-04", 1, 7, 0, false))
        val (date, _) = Routine.nextFire(wake, plans, at("2026-10-03", 22))!!
        assertEquals("2026-10-05", date)
    }

    @Test fun `an item switched off never fires`() {
        assertNull(Routine.nextFire(wake.copy(enabled = false), emptyMap(), at("2026-10-03", 6)))
    }

    // ---------------------------------------------------------------- hit or miss

    private val none = RoutineEvidence()

    @Test fun `today stays pending until the grace period runs out`() {
        val now = at("2026-10-03", 22)
        assertEquals(RoutineStatus.PENDING, Routine.resolve(reading, "2026-10-03", null, null, none, now).status)
        val late = at("2026-10-04", 1)
        assertEquals(RoutineStatus.MISSED, Routine.resolve(reading, "2026-10-03", null, null, none, late).status)
    }

    @Test fun `a tap beats the evidence either way`() {
        val now = at("2026-10-03", 23)
        val done = RoutineLog("2026-10-03", 9, Routine.DONE)
        assertEquals(RoutineStatus.DONE, Routine.resolve(reading, "2026-10-03", null, done, none, now).status)
        val skipped = RoutineLog("2026-10-03", 4, Routine.MISSED)
        val ate = RoutineEvidence(mealSlots = setOf(Slot.Lunch.name))
        assertEquals(RoutineStatus.MISSED, Routine.resolve(lunch, "2026-10-03", null, skipped, ate, now).status)
    }

    @Test fun `lunch ticks itself once anything is logged under lunch`() {
        val now = at("2026-10-03", 16)
        val slot = Routine.resolve(lunch, "2026-10-03", null, null, RoutineEvidence(mealSlots = setOf("Lunch")), now)
        assertEquals(RoutineStatus.DONE, slot.status)
        assertTrue(slot.auto)
        // Not logged yet, but still possible today: pending, not missed.
        assertEquals(RoutineStatus.PENDING, Routine.resolve(lunch, "2026-10-03", null, null, none, now).status)
    }

    @Test fun `the walk counts from the steps in the two hours after it`() {
        val now = at("2026-10-03", 18)
        val walked = RoutineEvidence(hourlySteps = mapOf(14 to 600.0, 15 to 500.0))
        assertEquals(RoutineStatus.DONE, Routine.resolve(walk, "2026-10-03", null, null, walked, now).status)
        val barely = RoutineEvidence(hourlySteps = mapOf(14 to 300.0, 16 to 5_000.0))
        assertEquals(RoutineStatus.PENDING, Routine.resolve(walk, "2026-10-03", null, null, barely, now).status)
        // A moved walk is judged at its new hour.
        val moved = RoutinePlan("2026-10-03", 5, 16, 0, true)
        assertEquals(RoutineStatus.DONE, Routine.resolve(walk, "2026-10-03", moved, null, barely, now).status)
    }

    @Test fun `planning counts when tomorrow has a saved plan`() {
        val now = at("2026-10-03", 22, 30)
        val s = Routine.resolve(plan, "2026-10-03", null, null, RoutineEvidence(planSavedForNextDay = true), now)
        assertEquals(RoutineStatus.DONE, s.status)
    }

    @Test fun `a day off is neither hit nor miss`() {
        val off = RoutinePlan("2026-10-02", 9, 21, 45, false)
        val s = Routine.resolve(reading, "2026-10-02", off, null, none, at("2026-10-03", 9))
        assertEquals(RoutineStatus.OFF, s.status)
        assertNull(Routine.hitRate(listOf(s)))
    }

    @Test fun `wake up is missed an hour after the alarm with no dismiss`() {
        assertEquals(RoutineStatus.PENDING, Routine.resolve(wake, "2026-10-03", null, null, none, at("2026-10-03", 7, 50)).status)
        assertEquals(RoutineStatus.MISSED, Routine.resolve(wake, "2026-10-03", null, null, none, at("2026-10-03", 8, 5)).status)
    }

    // ---------------------------------------------------------------- streaks and rates

    @Test fun `streak counts back from today, skipping days off`() {
        val days = mapOf(
            "2026-10-03" to RoutineStatus.DONE,
            "2026-10-02" to RoutineStatus.OFF,
            "2026-10-01" to RoutineStatus.DONE,
            "2026-09-30" to RoutineStatus.MISSED,
            "2026-09-29" to RoutineStatus.DONE
        )
        assertEquals(2, Routine.streak(days, "2026-10-03"))
    }

    @Test fun `a still-pending today doesn't break yesterday's streak`() {
        val days = mapOf("2026-10-03" to RoutineStatus.PENDING, "2026-10-02" to RoutineStatus.DONE)
        assertEquals(1, Routine.streak(days, "2026-10-03"))
    }

    @Test fun `hit rate ignores pending and off`() {
        val base = Routine.resolve(reading, "2026-10-01", null, RoutineLog("2026-10-01", 9, Routine.DONE), none, at("2026-10-03", 9))
        val miss = base.copy(status = RoutineStatus.MISSED)
        val pending = base.copy(status = RoutineStatus.PENDING)
        assertEquals(0.5f, Routine.hitRate(listOf(base, miss, pending))!!, 1e-6f)
    }

    @Test fun `every seeded kind round-trips through its name`() {
        Routine.SEED.forEach { assertEquals(RoutineKind.valueOf(it.kind), it.kindType) }
    }

    // ---------------------------------------------------------------- steps by the hour

    @Test fun `counter delta handles first reading and reboots`() {
        assertEquals(0, StepSplit.delta(-1, 5_000))
        assertEquals(250, StepSplit.delta(5_000, 5_250))
        assertEquals(40, StepSplit.delta(5_000, 40))   // rebooted: everything on it is new
    }

    @Test fun `steps are spread over the hours between two readings`() {
        val from = millis(at("2026-10-03", 13, 45))
        val to = millis(at("2026-10-03", 14, 15))
        val parts = StepSplit.spread(600, from, to, zone)
        assertEquals(300.0, parts.getValue("2026-10-03" to 13), 1e-6)
        assertEquals(300.0, parts.getValue("2026-10-03" to 14), 1e-6)
    }

    @Test fun `a reading across midnight lands on both days`() {
        val parts = StepSplit.spread(
            100, millis(at("2026-10-03", 23, 30)), millis(at("2026-10-04", 0, 30)), zone
        )
        assertEquals(50.0, parts.getValue("2026-10-03" to 23), 1e-6)
        assertEquals(50.0, parts.getValue("2026-10-04" to 0), 1e-6)
        assertEquals(100.0, parts.values.sum(), 1e-6)
    }

    @Test fun `no previous time puts everything in the current hour`() {
        val parts = StepSplit.spread(80, 0, millis(at("2026-10-03", 9, 10)), zone)
        assertEquals(mapOf(("2026-10-03" to 9) to 80.0), parts)
    }
}
