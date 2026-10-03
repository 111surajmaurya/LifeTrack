package com.lifetrack.app

import com.lifetrack.app.data.ActivityLevel
import com.lifetrack.app.data.BodyMath
import com.lifetrack.app.data.DailyValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.FoodItem
import com.lifetrack.app.data.FoodSeed
import com.lifetrack.app.data.Habit
import com.lifetrack.app.data.HabitKind
import com.lifetrack.app.data.Meal
import com.lifetrack.app.data.Metric
import com.lifetrack.app.data.Nutrients
import com.lifetrack.app.data.Portion
import com.lifetrack.app.data.Reminder
import com.lifetrack.app.data.ReminderMode
import com.lifetrack.app.data.Retention
import com.lifetrack.app.data.Serving
import com.lifetrack.app.data.Sex
import com.lifetrack.app.data.Settings
import com.lifetrack.app.data.Slot
import com.lifetrack.app.data.StepDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules that live on the entities themselves: portions, budgets, retention, seeding. */
class DomainTest {

    // ------------------------------------------------------------------ food maths

    private fun roti() = FoodItem(name = "Roti", kcal = 100, serving = Serving.Piece.name)
    private fun daal() = FoodItem(name = "Daal", kcal = 180, serving = Serving.Bowl.name)

    @Test fun `counted foods just multiply`() {
        assertEquals(100, roti().kcalFor(1f, Portion.Medium))
        assertEquals(300, roti().kcalFor(3f, Portion.Medium))
    }

    /** A roti is a roti; the bowl-size chips are hidden for pieces and must not leak in. */
    @Test fun `a piece ignores bowl size`() {
        assertEquals(200, roti().kcalFor(2f, Portion.Large))
        assertEquals(200, roti().kcalFor(2f, Portion.Small))
    }

    @Test fun `bowls scale with the size you picked`() {
        assertEquals(108, daal().kcalFor(1f, Portion.Small))   // 180 * 0.6
        assertEquals(180, daal().kcalFor(1f, Portion.Medium))
        assertEquals(270, daal().kcalFor(1f, Portion.Large))   // 180 * 1.5
        assertEquals(540, daal().kcalFor(2f, Portion.Large))
    }

    @Test fun `half servings round to the nearest calorie`() {
        assertEquals(50, roti().kcalFor(0.5f, Portion.Medium))
        assertEquals(90, daal().kcalFor(0.5f, Portion.Medium))
    }

    @Test fun `a zero-calorie food stays at zero however much you have`() {
        val water = FoodItem(name = "Water", kcal = 0, serving = Serving.Glass.name)
        assertEquals(0, water.kcalFor(5f, Portion.Large))
    }

    // ------------------------------------------------------------------ protein & fibre

    /** Every nutrient has to scale by exactly the same rule, or the panel disagrees with itself. */
    @Test fun `protein scales with count the way calories do`() {
        val egg = FoodItem(name = "Egg", kcal = 78, protein = 6f, serving = Serving.Piece.name)
        assertEquals(6f, egg.proteinFor(1f, Portion.Medium), 1e-4f)
        assertEquals(18f, egg.proteinFor(3f, Portion.Medium), 1e-4f)
        assertEquals(12f, egg.proteinFor(2f, Portion.Large), 1e-4f)   // a piece ignores the size
    }

    @Test fun `protein scales with bowl size too`() {
        val daal = FoodItem(name = "Daal", kcal = 180, protein = 10f, serving = Serving.Bowl.name)
        assertEquals(6f, daal.proteinFor(1f, Portion.Small), 1e-4f)    // 10 * 0.6
        assertEquals(10f, daal.proteinFor(1f, Portion.Medium), 1e-4f)
        assertEquals(15f, daal.proteinFor(1f, Portion.Large), 1e-4f)   // 10 * 1.5
        assertEquals(30f, daal.proteinFor(2f, Portion.Large), 1e-4f)
    }

    /** The whole panel moves together - this is what stops fibre being forgotten somewhere. */
    @Test fun `every nutrient scales by the same factor`() {
        val bowl = FoodItem(
            name = "Test", kcal = 200, protein = 10f, fiber = 5f, vitA = 40f,
            vitC = 8f, iron = 2f, calcium = 100f, serving = Serving.Bowl.name
        )
        val large = bowl.nutrientsFor(2f, Portion.Large)   // x3
        assertEquals(600, large.kcal)
        assertEquals(30f, large.protein, 1e-3f)
        assertEquals(15f, large.fiber, 1e-3f)
        assertEquals(120f, large.vitA, 1e-3f)
        assertEquals(24f, large.vitC, 1e-3f)
        assertEquals(6f, large.iron, 1e-3f)
        assertEquals(300f, large.calcium, 1e-3f)
    }

    @Test fun `nutrients add up across a plate`() {
        val roti = FoodItem(name = "Roti", kcal = 119, protein = 4.5f, fiber = 2f, serving = Serving.Piece.name)
        val daal = FoodItem(name = "Daal", kcal = 218, protein = 12.9f, fiber = 11.2f, serving = Serving.Bowl.name)
        val meal = Nutrients.sum(
            listOf(roti.nutrientsFor(2f, Portion.Medium), daal.nutrientsFor(1f, Portion.Medium))
        )
        assertEquals(119 * 2 + 218, meal.kcal)
        assertEquals(21.9f, meal.protein, 1e-3f)
        assertEquals(15.2f, meal.fiber, 1e-3f)
    }

    @Test fun `an empty day sums to nothing rather than throwing`() {
        assertEquals(Nutrients.NONE, Nutrients.sum(emptyList()))
        assertEquals(0, Nutrients.NONE.kcal)
    }

    /** Most fruit really is ~0 g protein; that must stay 0 rather than rounding its way up. */
    @Test fun `a food with no protein reports none however much you eat`() {
        val apple = FoodItem(name = "Apple", kcal = 95, protein = 0f, serving = Serving.Piece.name)
        assertEquals(0f, apple.proteinFor(6f, Portion.Large), 1e-4f)
    }

    @Test fun `a food added before the nutrient columns existed reads as zero, not as broken`() {
        val legacy = FoodItem(name = "Old entry", kcal = 200, serving = Serving.Bowl.name)
        assertEquals(0f, legacy.protein, 1e-4f)
        assertEquals(0f, legacy.fiber, 1e-4f)
        assertEquals(400, legacy.kcalFor(2f, Portion.Medium))
    }

    @Test fun `a logged meal keeps the numbers it was logged at`() {
        val meal = Meal(date = "2026-09-10", name = "Daal", kcal = 270, protein = 15f, fiber = 11f)
        assertEquals(15f, meal.protein, 1e-4f)
        assertEquals(11f, meal.nutrients.fiber, 1e-4f)
        assertEquals(270, meal.nutrients.kcal)
    }

    // ------------------------------------------------------------------ body profile

    /**
     * Mifflin-St Jeor, worked by hand:
     *   male   10(80) + 6.25(180) - 5(30) + 5   = 800 + 1125 - 150 + 5   = 1780
     *   female 10(60) + 6.25(165) - 5(30) - 161 = 600 + 1031.25 - 150 - 161 = 1320.25
     */
    @Test fun `BMR follows Mifflin-St Jeor`() {
        assertEquals(1780, BodyMath.bmr(Sex.Male, 80f, 180f, 30))
        assertEquals(1320, BodyMath.bmr(Sex.Female, 60f, 165f, 30))
    }

    /** With no answer given we sit between the two constants rather than assuming one. */
    @Test fun `an unspecified sex lands between the male and female figures`() {
        val male = BodyMath.bmr(Sex.Male, 70f, 170f, 30)
        val female = BodyMath.bmr(Sex.Female, 70f, 170f, 30)
        val unspecified = BodyMath.bmr(Sex.Unspecified, 70f, 170f, 30)
        assertTrue(unspecified in (female + 1)..(male - 1))
        assertEquals((male + female) / 2, unspecified)
    }

    @Test fun `an empty profile produces no numbers rather than nonsense`() {
        assertEquals(0, BodyMath.bmr(Sex.Male, 0f, 180f, 30))
        assertEquals(0, BodyMath.bmr(Sex.Male, 80f, 0f, 30))
        assertEquals(0, BodyMath.bmr(Sex.Male, 80f, 180f, 0))
        assertEquals(0, BodyMath.tdee(Sex.Male, 0f, 0f, 0, ActivityLevel.Moderate))
        assertEquals(0, BodyMath.protein(0f, ActivityLevel.Active))
        assertEquals(0, BodyMath.fiber(0))
    }

    @Test fun `activity level scales the resting burn`() {
        val rest = BodyMath.bmr(Sex.Male, 80f, 180f, 30)
        assertEquals(rest, 1780)
        assertEquals(Math.round(1780 * 1.2f), BodyMath.tdee(Sex.Male, 80f, 180f, 30, ActivityLevel.Sedentary))
        assertEquals(Math.round(1780 * 1.55f), BodyMath.tdee(Sex.Male, 80f, 180f, 30, ActivityLevel.Moderate))
        assertEquals(Math.round(1780 * 1.9f), BodyMath.tdee(Sex.Male, 80f, 180f, 30, ActivityLevel.VeryActive))
    }

    @Test fun `a more active person is asked for more of everything`() {
        val sedentary = BodyMath.tdee(Sex.Male, 80f, 180f, 30, ActivityLevel.Sedentary)
        val active = BodyMath.tdee(Sex.Male, 80f, 180f, 30, ActivityLevel.Active)
        assertTrue(active > sedentary)
        assertTrue(
            BodyMath.protein(80f, ActivityLevel.Active) >
                BodyMath.protein(80f, ActivityLevel.Sedentary)
        )
    }

    /** Protein tracks body weight, not appetite: 1.0 g/kg sedentary up to 1.6 g/kg training hard. */
    @Test fun `protein target is grams per kilo`() {
        assertEquals(80, BodyMath.protein(80f, ActivityLevel.Sedentary))
        assertEquals(112, BodyMath.protein(80f, ActivityLevel.Moderate))
        assertEquals(128, BodyMath.protein(80f, ActivityLevel.Active))
    }

    /** 14 g per 1000 kcal, so eating more asks for more fibre. */
    @Test fun `fibre target scales with the calorie goal`() {
        assertEquals(28, BodyMath.fiber(2000))
        assertEquals(42, BodyMath.fiber(3000))
    }

    @Test fun `settings know whether the profile is filled in`() {
        assertFalse(Settings().hasBodyProfile)
        val filled = Settings(heightCm = 180f, weightKg = 80f, age = 30, sex = Sex.Male.name)
        assertTrue(filled.hasBodyProfile)
        assertEquals(1780, filled.bmr)
        assertEquals(ActivityLevel.Moderate, filled.activity)   // the default
    }

    @Test fun `unknown body enum names fall back instead of throwing`() {
        assertEquals(Sex.Unspecified, Sex.from("Nonsense"))
        assertEquals(Sex.Unspecified, Sex.from(null))
        assertEquals(ActivityLevel.Moderate, ActivityLevel.from("Nonsense"))
    }

    // ------------------------------------------------------------------ calculated goals

    private fun profile() = Settings(
        heightCm = 180f, weightKg = 80f, age = 30,
        sex = Sex.Male.name, activityLevel = ActivityLevel.Moderate.name
    )

    /** The whole point of the profile: fill it in and the targets follow. */
    @Test fun `a complete profile calculates every goal`() {
        val s = profile().withCalculatedGoals()
        assertEquals(Math.round(1780 * 1.55f), s.calorieGoal)
        assertEquals(112, s.proteinGoal)                       // 80 kg x 1.4
        assertEquals(BodyMath.fiber(s.calorieGoal), s.fiberGoal)
    }

    /** The four meal budgets have to move with the daily goal, or they stop adding up to it. */
    @Test fun `calculated goals split across the meals too`() {
        val s = profile().withCalculatedGoals()
        val sum = Slot.entries.sumOf { s.goalFor(it) }
        assertEquals(s.calorieGoal.toDouble(), sum.toDouble(), s.calorieGoal * 0.02)
        assertTrue(s.goalFor(Slot.Lunch) > s.goalFor(Slot.Snacks))
    }

    /** A half-filled profile must never quietly rewrite a goal you set yourself. */
    @Test fun `an incomplete profile leaves the goals exactly as they were`() {
        val typed = Settings(calorieGoal = 2400, proteinGoal = 90, fiberGoal = 25)
        assertEquals(typed, typed.withCalculatedGoals())

        val halfFilled = typed.copy(heightCm = 180f)           // no weight, no age
        assertEquals(halfFilled, halfFilled.withCalculatedGoals())
    }

    @Test fun `recalculating twice lands in the same place`() {
        val once = profile().withCalculatedGoals()
        assertEquals(once, once.withCalculatedGoals())
    }

    @Test fun `a heavier or more active profile asks for more`() {
        val base = profile().withCalculatedGoals()
        val heavier = profile().copy(weightKg = 95f).withCalculatedGoals()
        val fitter = profile().copy(activityLevel = ActivityLevel.VeryActive.name).withCalculatedGoals()
        assertTrue(heavier.calorieGoal > base.calorieGoal)
        assertTrue(heavier.proteinGoal > base.proteinGoal)
        assertTrue(fitter.calorieGoal > base.calorieGoal)
    }

    @Test fun `goals count as calculated only while the switch is on and a profile exists`() {
        assertTrue(profile().withCalculatedGoals().goalsAreCalculated)
        assertFalse(profile().copy(autoGoals = false).goalsAreCalculated)
        assertFalse(Settings().goalsAreCalculated)             // no profile at all
        assertTrue(Settings().autoGoals)                       // ...but the switch starts on
    }

    @Test fun `the default goals are the ICMR-NIN daily targets`() {
        assertEquals(60, Settings().proteinGoal)
        assertEquals(30, Settings().fiberGoal)
        assertEquals(19f, DailyValue.IRON_MG, 1e-4f)
        assertEquals(1000f, DailyValue.CALCIUM_MG, 1e-4f)
    }
 @Test fun `descriptions read like a person wrote them`() {
        assertEquals("1 piece", roti().describe(1f, Portion.Medium))
        assertEquals("2 pieces", roti().describe(2f, Portion.Medium))
        assertEquals("1 large bowl", daal().describe(1f, Portion.Large))
        assertEquals("2 small bowls", daal().describe(2f, Portion.Small))
    }

    @Test fun `logged meal detail keeps the portion it was logged at`() {
        val m = Meal(
            date = "2026-09-09", name = "Daal", kcal = 270,
            qty = 1f, portion = Portion.Large.name, serving = Serving.Bowl.name
        )
        assertEquals("1 large bowl", m.detail)
        assertEquals(Slot.Snacks, m.slotType)
    }

    @Test fun `unknown enum names fall back instead of throwing`() {
        assertEquals(Slot.Snacks, Slot.from("Elevenses"))
        assertEquals(Slot.Snacks, Slot.from(null))
        assertEquals(Portion.Medium, Portion.from("Enormous"))
        assertEquals(Serving.Piece, Serving.from(null))
        assertEquals(HabitKind.TIMED, HabitKind.from("WHATEVER"))
        assertEquals(ReminderMode.NOTIFICATION, ReminderMode.from(null))
    }

    // ------------------------------------------------------------------ catalogue

    /** "da" has to find daal, daliya and dahi — the search the user asked for by name. */
    @Test fun `catalogue covers the everyday Indian staples`() {
        val names = FoodSeed.items.map { it.name.lowercase() }
        listOf("roti", "daal", "daliya", "dahi", "rice", "sabji", "egg (boiled)", "idli", "poha", "chapati")
            .forEach { assertTrue("missing $it", it in names) }
        val da = FoodSeed.items.filter { it.name.lowercase().startsWith("da") }
        assertTrue("daal/daliya/dahi should all match 'da', got ${da.map { it.name }}", da.size >= 3)
    }

    @Test fun `rice daal and sabji are bowls so they get size options`() {
        listOf("Rice", "Daal", "Sabji", "Poha", "Curd").forEach { name ->
            assertTrue("$name should be sized", FoodSeed.items.first { it.name == name }.servingType.sized)
        }
    }

    @Test fun `roti and egg are counted, not sized`() {
        listOf("Roti", "Egg (boiled)", "Banana", "Samosa").forEach { name ->
            assertFalse("$name should be counted", FoodSeed.items.first { it.name == name }.servingType.sized)
        }
    }

    @Test fun `catalogue has no duplicate names and no nonsense values`() {
        val names = FoodSeed.items.map { it.name.lowercase() }
        assertEquals("duplicate food names", names.size, names.toSet().size)
        assertTrue(FoodSeed.items.all { it.kcal >= 0 })
        assertTrue(FoodSeed.items.all { it.name.isNotBlank() })
        assertTrue("seeded foods must not be marked custom", FoodSeed.items.none { it.custom })
    }

    @Test fun `every catalogue category has an emoji and is a known category`() {
        FoodSeed.items.forEach { item ->
            assertTrue("unknown category ${item.category}", item.category in FoodSeed.categories)
            assertTrue("no emoji for ${item.category}", FoodSeed.emojiFor(item.category).isNotBlank())
        }
    }

    // ------------------------------------------------------------------ meal slots

    @Test fun `slot is guessed from the clock`() {
        assertEquals(Slot.Breakfast, Slot.forHour(8))
        assertEquals(Slot.Lunch, Slot.forHour(13))
        assertEquals(Slot.Snacks, Slot.forHour(17))
        assertEquals(Slot.Dinner, Slot.forHour(21))
        assertEquals(Slot.Dinner, Slot.forHour(2))   // a 2am snack is still "dinner"
    }

    @Test fun `every hour of the day maps to some slot`() {
        (0..23).forEach { assertNotNull(Slot.forHour(it)) }
    }

    @Test fun `default meal shares add up to the whole day`() {
        val total = Slot.entries.sumOf { Slot.defaultShare(it).toDouble() }
        assertEquals(1.0, total, 1e-6)
    }

    @Test fun `per-slot budgets are read and written by slot`() {
        val s = Settings(breakfastGoal = 500, lunchGoal = 700, snacksGoal = 200, dinnerGoal = 600)
        assertEquals(500, s.goalFor(Slot.Breakfast))
        assertEquals(700, s.goalFor(Slot.Lunch))
        assertEquals(200, s.goalFor(Slot.Snacks))
        assertEquals(600, s.goalFor(Slot.Dinner))

        val edited = s.withGoalFor(Slot.Lunch, 900)
        assertEquals(900, edited.goalFor(Slot.Lunch))
        // Editing one budget must not disturb the others.
        assertEquals(500, edited.goalFor(Slot.Breakfast))
        assertEquals(600, edited.goalFor(Slot.Dinner))
    }

    @Test fun `active slot survives as a typed value`() {
        assertEquals(Slot.Snacks, Settings().activeSlotType)
        assertEquals(Slot.Breakfast, Settings(activeSlot = Slot.Breakfast.name).activeSlotType)
    }

    @Test fun `the default heavy-item threshold is the 300 kcal the user asked for`() {
        assertEquals(300, Settings().itemWarnKcal)
    }

    // ------------------------------------------------------------------ habits

    @Test fun `a timed habit converts its goal to milliseconds`() {
        assertEquals(45 * 60_000L, Habit(name = "Gym", dailyGoalMin = 45).dailyGoalMillis)
        assertEquals(0L, Habit(name = "None", dailyGoalMin = 0).dailyGoalMillis)
    }

    @Test fun `habit kind round-trips through its stored name`() {
        assertEquals(HabitKind.TIMED, Habit(name = "Gym", kind = HabitKind.TIMED.name).kindType)
        assertEquals(HabitKind.COUNT, Habit(name = "Water", kind = HabitKind.COUNT.name).kindType)
    }

    // ------------------------------------------------------------------ reminders

    @Test fun `a reminder only counts as an alarm in alarm mode`() {
        val base = Reminder(label = "Gym", hour = 6, minute = 0)
        assertFalse(base.isAlarm)
        assertEquals(ReminderMode.NOTIFICATION, base.modeType)
        val alarm = base.copy(mode = ReminderMode.ALARM.name)
        assertTrue(alarm.isAlarm)
        assertEquals(ReminderMode.ALARM, alarm.modeType)
    }

    @Test fun `alarms default to the twenty second ring the user asked for`() {
        val r = Reminder(label = "Gym", hour = 6, minute = 0)
        assertEquals(20, r.ringSeconds)
        assertEquals(5, r.snoozeMinutes)
        assertTrue(r.vibrate)
    }

    // ------------------------------------------------------------------ steps

    @Test fun `steps are latest minus baseline`() {
        assertEquals(1500L, StepDay("2026-09-09", baseline = 10_000, latest = 11_500).steps)
    }

    @Test fun `after a reboot accumulated steps are kept`() {
        val d = StepDay("2026-09-09", baseline = 0, latest = 300, accumulated = 1500)
        assertEquals(1800L, d.steps)
    }

    @Test fun `negative differences never go below zero`() {
        assertEquals(0L, StepDay("2026-09-09", baseline = 500, latest = 100).steps)
    }

    // ------------------------------------------------------------------ metrics

    @Test fun `metric keys are unique and resolvable both ways`() {
        val keys = Metric.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        Metric.entries.forEach { assertEquals(it, Metric.from(it.key)) }
        assertNull(Metric.from("NOT_A_METRIC"))
        assertNull(Metric.from(null))
    }

    @Test fun `every metric has a label unit and emoji for the tiles`() {
        Metric.entries.forEach {
            assertTrue(it.label.isNotBlank())
            assertTrue(it.unit.isNotBlank())
            assertTrue(it.emoji.isNotBlank())
        }
    }

    // ------------------------------------------------------------------ retention

    @Test fun `retention keeps six months`() {
        assertEquals(180L, Retention.DAYS)
        assertEquals(Dates.shift("2026-09-10", -180), Retention.cutoff("2026-09-10"))
    }

    @Test fun `the cutoff is old enough to keep half a year of charts`() {
        val cutoff = Retention.cutoff("2026-09-10")
        assertTrue("six months back should be in early 2026", cutoff < "2026-04-01")
        assertTrue(cutoff > "2026-03-01")
    }

    @Test fun `pruned totals add up and report whether anything went`() {
        assertFalse(Retention.Pruned().any)
        assertEquals(0, Retention.Pruned().total)
        val p = Retention.Pruned(meals = 3, sessions = 2, checks = 1, appUsage = 4, metrics = 5, stepDays = 6)
        assertEquals(21, p.total)
        assertTrue(p.any)
    }
}
