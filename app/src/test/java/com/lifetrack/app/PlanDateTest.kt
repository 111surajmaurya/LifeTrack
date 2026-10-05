package com.lifetrack.app

import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Routine
import com.lifetrack.app.data.RoutineEvidence
import com.lifetrack.app.data.RoutineItem
import com.lifetrack.app.data.RoutineKind
import com.lifetrack.app.data.RoutineStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Which day "Plan tomorrow" plans, so the 10 PM nudge answered after midnight saves the right day. */
class PlanDateTest {
    private fun at(date: String, h: Int, m: Int = 0): LocalDateTime = LocalDate.parse(date).atTime(h, m)

    @Test fun `in the evening the plan is for the next day`() {
        assertEquals("2026-10-04", Routine.planDate(at("2026-10-03", 22)))
        assertEquals("2026-10-04", Routine.planDate(at("2026-10-03", 23, 59)))
        assertEquals("2026-10-04", Routine.planDate(at("2026-10-03", 4)))
    }

    @Test fun `after midnight the plan is still for the day about to start`() {
        assertEquals("2026-10-04", Routine.planDate(at("2026-10-04", 0, 30)))
        assertEquals("2026-10-04", Routine.planDate(at("2026-10-04", 3, 59)))
    }

    @Test fun `the rollover crosses month and year ends`() {
        assertEquals("2027-01-01", Routine.planDate(at("2026-12-31", 22)))
        assertEquals("2027-01-01", Routine.planDate(at("2027-01-01", 1)))
    }

    /** The credit rule the view model uses: a plan for D ticks D-1's "Plan tomorrow". */
    @Test fun `a plan saved after midnight credits the night it was asked for`() {
        val plan = RoutineItem(id = 10, key = "plan", title = "Plan tomorrow", emoji = "", hour = 22, minute = 0, kind = RoutineKind.PLAN.name)
        val now = at("2026-10-04", 0, 30)
        val planned = setOf(Routine.planDate(now))
        fun credited(date: String) = RoutineEvidence(planSavedForNextDay = Dates.shift(date, 1) in planned)
        assertEquals(RoutineStatus.DONE, Routine.resolve(plan, "2026-10-03", null, null, credited("2026-10-03"), now).status)
        // Tonight's nudge is still open.
        assertEquals(RoutineStatus.PENDING, Routine.resolve(plan, "2026-10-04", null, null, credited("2026-10-04"), now).status)
    }
}
