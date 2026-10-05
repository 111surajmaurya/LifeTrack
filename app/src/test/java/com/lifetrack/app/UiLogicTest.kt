package com.lifetrack.app

import com.lifetrack.app.data.ActivityLevel
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Sex
import com.lifetrack.app.data.Settings
import com.lifetrack.app.ui.calories.CalorieWindow
import com.lifetrack.app.ui.calories.CaloriesDetailState
import com.lifetrack.app.ui.calories.CaloriesViewModel
import com.lifetrack.app.ui.calories.decimalText
import com.lifetrack.app.ui.habits.runningToday
import com.lifetrack.app.ui.screentime.AppDetailState
import com.lifetrack.app.ui.screentime.DetailWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The small pure rules the screens lean on: input filters, deltas, timers crossing midnight. */
class UiLogicTest {

    // ---- decimal input

    @Test
    fun decimalKeepsOnlyTheFirstDot() {
        assertEquals("2.5", decimalText("2.5"))
        assertEquals("2.55", decimalText("2.5.5"))
        assertEquals("12", decimalText("1a2"))
        assertEquals(".5", decimalText(".5"))
    }

    @Test
    fun decimalIsCapped() {
        assertEquals("123.45", decimalText("123.4567"))
    }

    // ---- calculated goals

    @Test
    fun calculatedGoalsStayInsideTheDialogBounds() {
        val huge = Settings(
            heightCm = 250f, weightKg = 400f, age = 20,
            sex = Sex.Male.name, activityLevel = ActivityLevel.VeryActive.name
        ).withCalculatedGoals()
        assertEquals(CaloriesViewModel.GOAL_MAX, huge.calorieGoal)
        assertEquals(CaloriesViewModel.PROTEIN_MAX, huge.proteinGoal)
        assertTrue(huge.fiberGoal <= CaloriesViewModel.FIBER_MAX)

        val tiny = Settings(
            heightCm = 100f, weightKg = 20f, age = 100,
            sex = Sex.Female.name, activityLevel = ActivityLevel.Sedentary.name
        ).withCalculatedGoals()
        assertEquals(CaloriesViewModel.GOAL_MIN, tiny.calorieGoal)
        assertTrue(tiny.proteinGoal >= CaloriesViewModel.PROTEIN_MIN)
        assertTrue(tiny.fiberGoal >= CaloriesViewModel.FIBER_MIN)
    }

    @Test
    fun ordinaryProfileIsNotClamped() {
        val s = Settings(heightCm = 175f, weightKg = 70f, age = 30, sex = Sex.Male.name)
        assertEquals(s.tdee, s.withCalculatedGoals().calorieGoal)
    }

    // ---- deltas

    @Test
    fun calorieDeltaIsHiddenForToday() {
        val today = CaloriesDetailState(
            window = CalorieWindow.Today,
            perDay = listOf(DayValue("2026-10-05", 400.0)),
            previousAverage = 2000
        )
        assertFalse(today.hasComparison)
        assertTrue(today.copy(window = CalorieWindow.Week).hasComparison)
        assertFalse(today.copy(window = CalorieWindow.Week, previousAverage = 0).hasComparison)
    }

    @Test
    fun appDeltaIsHiddenForToday() {
        val state = AppDetailState(window = DetailWindow.Today, previousTotal = 120, hasPrevious = true)
        assertFalse(state.hasDelta)
        assertTrue(state.copy(window = DetailWindow.Week).hasDelta)
        assertFalse(state.copy(window = DetailWindow.Week, hasPrevious = false).hasDelta)
    }

    // ---- timers across midnight

    private fun at(date: String, hour: Int, minute: Int): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun runningTimeCountsTowardTodayWhenStartedToday() {
        val since = at("2026-10-05", 9, 0)
        assertEquals(30 * 60_000L, runningToday(since, at("2026-10-05", 9, 30)))
    }

    @Test
    fun timerStartedYesterdayAddsNothingToToday() {
        val since = at("2026-10-04", 23, 30)
        assertEquals(0L, runningToday(since, at("2026-10-05", 0, 10)))
    }
}
