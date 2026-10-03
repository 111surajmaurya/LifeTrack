package com.lifetrack.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Thin layer over the DAOs so screens never touch Room directly. */
class Repository(private val db: AppDatabase) {

    companion object {
        /** The highest daily limit a tracked app can have. Mirrored by ScreenTimeViewModel.MAX_LIMIT_MIN. */
        const val MAX_APP_LIMIT_MIN = 60
    }

    // ---------------------------------------------------------------- seeding

    suspend fun ensureSeeded() {
        if (db.habitDao().count() == 0) {
            db.habitDao().insertAll(
                listOf(
                    Habit(name = "Gym", kind = HabitKind.TIMED.name, dailyGoalMin = 45, emoji = "🏋", accent = "leaf", seeded = true, sortOrder = 0),
                    Habit(name = "Reading", kind = HabitKind.TIMED.name, dailyGoalMin = 30, emoji = "📖", accent = "sky", seeded = true, sortOrder = 1),
                    Habit(name = "Study", kind = HabitKind.TIMED.name, dailyGoalMin = 60, emoji = "🎓", accent = "plum", seeded = true, sortOrder = 2),
                    Habit(name = "Water", kind = HabitKind.COUNT.name, dailyTarget = 8, emoji = "💧", accent = "sky", seeded = true, sortOrder = 3),
                    Habit(name = "Meditate", kind = HabitKind.COUNT.name, dailyTarget = 1, emoji = "🧘", accent = "clay", seeded = true, sortOrder = 4)
                )
            )
        }
        if (db.trackedAppDao().count() == 0) {
            db.trackedAppDao().upsertAll(
                listOf(
                    TrackedApp("com.google.android.youtube", "YouTube", 30, seeded = true),
                    TrackedApp("com.instagram.android", "Instagram", 30, seeded = true),
                    TrackedApp("com.linkedin.android", "LinkedIn", 30, seeded = true),
                    TrackedApp("com.android.chrome", "Chrome", 30, seeded = true),
                    TrackedApp("com.whatsapp", "WhatsApp", 30, seeded = true)
                )
            )
        }
        // An app limit can be at most an hour; older installs may have saved more.
        db.trackedAppDao().capLimits(MAX_APP_LIMIT_MIN)

        // Keyed by `key`, so items added in a later release join an existing routine and an
        // edited time or title is never put back.
        val haveRoutine = db.routineDao().keys().toSet()
        db.routineDao().insertItems(Routine.SEED.filter { it.key !in haveRoutine })

        if (db.foodDao().count() == 0) {
            db.foodDao().insertAll(Catalogue.items)
            setSeedVersion(Catalogue.VERSION)
        } else if (settingsOnce().seedVersion < Catalogue.VERSION) {
            refreshCatalogue()
            setSeedVersion(Catalogue.VERSION)
        }
    }

    /**
     * Re-point the seeded foods at the current [FoodSeed] figures.
     *
     * Seeding only runs on an empty table, so without this a phone that installed an earlier
     * build would keep its old numbers forever - which is exactly what happened when the
     * catalogue moved from hand-written estimates to measured USDA data.
     *
     * Matching is by name, and `custom = 1` rows are skipped, so a food the user invented or
     * corrected is never overwritten by a namesake from the catalogue. Meals already logged
     * keep the numbers they were logged with; only what you log from here on changes.
     */
    private suspend fun refreshCatalogue() {
        db.foodDao().seededFoods().forEach { existing ->
            val fresh = Catalogue.byName[existing.name] ?: return@forEach
            db.foodDao().update(
                existing.copy(
                    kcal = fresh.kcal, protein = fresh.protein, fiber = fresh.fiber,
                    vitA = fresh.vitA, vitC = fresh.vitC, iron = fresh.iron,
                    calcium = fresh.calcium, serving = fresh.serving, category = fresh.category
                )
            )
        }
        // Foods added to the catalogue since the last release have no row to update.
        val known = db.foodDao().allNames().toSet()
        db.foodDao().insertAll(Catalogue.items.filter { it.name !in known })
    }

    /** Drops anything past the retention window. Cheap, and safe to call on every launch. */
    suspend fun prune(cutoff: String = Retention.cutoff()): Retention.Pruned = Retention.Pruned(
        meals = db.mealDao().deleteBefore(cutoff),
        sessions = db.habitDao().deleteSessionsBefore(cutoff),
        checks = db.habitDao().deleteChecksBefore(cutoff),
        appUsage = db.trackedAppDao().deleteUsageBefore(cutoff),
        metrics = db.metricDao().deleteBefore(cutoff),
        stepDays = db.stepDao().deleteBefore(cutoff),
        routine = db.routineDao().deletePlansBefore(cutoff) + db.routineDao().deleteLogsBefore(cutoff),
        hourlySteps = db.stepDao().deleteHoursBefore(cutoff)
    )

    /**
     * Removes synced history from before this install started tracking ([TrackingStart]): steps,
     * activity metrics and screen time that came from Health Connect or Android's usage records.
     * Meals are left alone - those are only ever typed in here.
     */
    suspend fun pruneBeforeStart(start: String) {
        db.metricDao().deleteBefore(start)
        db.stepDao().deleteBefore(start)
        db.stepDao().deleteHoursBefore(start)
        db.trackedAppDao().deleteUsageBefore(start)
    }

    // ---------------------------------------------------------------- meals

    fun mealsOn(date: String): Flow<List<Meal>> = db.mealDao().mealsOn(date)
    fun mealsBetween(from: String, to: String): Flow<List<Meal>> = db.mealDao().mealsBetween(from, to)
    fun kcalPerDay(from: String, to: String): Flow<List<DayValue>> = db.mealDao().kcalPerDay(from, to)
    fun kcalPerSlot(from: String, to: String): Flow<List<DayValue>> = db.mealDao().kcalPerSlot(from, to)
    fun proteinPerDay(from: String, to: String): Flow<List<DayValue>> = db.mealDao().proteinPerDay(from, to)
    fun fiberPerDay(from: String, to: String): Flow<List<DayValue>> = db.mealDao().fiberPerDay(from, to)
    fun topProteinFoods(from: String, to: String, limit: Int = 8): Flow<List<DayValue>> =
        db.mealDao().topProteinFoods(from, to, limit)
    fun mealSlotsLogged(from: String, to: String): Flow<List<DateSlot>> = db.mealDao().slotsLogged(from, to)
    fun topFoods(from: String, to: String, limit: Int = 8): Flow<List<DayValue>> =
        db.mealDao().topFoods(from, to, limit)

    suspend fun deleteMeal(meal: Meal) = db.mealDao().delete(meal)
    suspend fun updateMeal(meal: Meal) = db.mealDao().update(meal)

    /** Log a catalogue item. Bumps its use count so it floats to the top of the suggestions. */
    suspend fun logFood(date: String, item: FoodItem, qty: Float, portion: Portion, slot: Slot) {
        if (qty <= 0f) return
        val n = item.nutrientsFor(qty, portion)
        db.mealDao().insert(
            Meal(
                date = date, name = item.name, kcal = n.kcal,
                protein = n.protein, fiber = n.fiber, vitA = n.vitA,
                vitC = n.vitC, iron = n.iron, calcium = n.calcium,
                slot = slot.name, qty = qty, portion = portion.name,
                serving = item.serving, foodItemId = item.id
            )
        )
        db.foodDao().markUsed(item.id, System.currentTimeMillis())
    }

    /**
     * Save a food the catalogue didn't have, then log it. The item stays in the catalogue,
     * so next time you start typing its name it shows up like anything else.
     * If a food with that name already exists we reuse it instead of making a duplicate.
     */
    suspend fun addCustomFoodAndLog(
        date: String, name: String, kcal: Int, protein: Float, fiber: Float, serving: Serving,
        qty: Float, portion: Portion, slot: Slot
    ): FoodItem {
        val clean = name.trim()
        val existing = db.foodDao().byName(clean)
        val item = existing ?: run {
            val fresh = FoodItem(
                name = clean, kcal = kcal, protein = protein, fiber = fiber,
                serving = serving.name, category = FoodSeed.OTHER, custom = true
            )
            fresh.copy(id = db.foodDao().insert(fresh))
        }
        logFood(date, item, qty, portion, slot)
        return item
    }

    // ---------------------------------------------------------------- food catalogue

    fun searchFoods(query: String): Flow<List<FoodItem>> = db.foodDao().search(query.trim())
    fun foodSuggestions(): Flow<List<FoodItem>> = db.foodDao().suggestions()
    fun foodsInCategory(category: String): Flow<List<FoodItem>> = db.foodDao().byCategory(category)
    suspend fun updateFood(item: FoodItem) = db.foodDao().update(item)
    suspend fun deleteFood(item: FoodItem) = db.foodDao().delete(item)

    // ---------------------------------------------------------------- settings

    val settings: Flow<Settings> = db.settingsDao().settings().map { it ?: Settings() }
    private suspend fun settingsOnce() = db.settingsDao().settingsOnce() ?: Settings()
    private suspend fun edit(block: (Settings) -> Settings) = db.settingsDao().upsert(block(settingsOnce()))

    /**
     * Typing a goal by hand takes the profile out of the driving seat. Anything else would let
     * the next profile edit silently throw the typed number away.
     */
    suspend fun setCalorieGoal(goal: Int) = edit { it.copy(calorieGoal = goal, autoGoals = false) }
    suspend fun setActiveSlot(slot: Slot) = edit { it.copy(activeSlot = slot.name) }
    suspend fun setSlotGoal(slot: Slot, kcal: Int) = edit { it.withGoalFor(slot, kcal) }
    suspend fun setItemWarnKcal(kcal: Int) = edit { it.copy(itemWarnKcal = kcal) }
    suspend fun setProteinGoal(grams: Int) = edit { it.copy(proteinGoal = grams, autoGoals = false) }
    suspend fun setFiberGoal(grams: Int) = edit { it.copy(fiberGoal = grams, autoGoals = false) }

    /**
     * Saves the body profile, and recalculates the goals from it when [Settings.autoGoals] is on.
     *
     * That recalculation is the whole point of the profile: change your weight or your activity
     * level and the targets move with you, without a second trip to another screen.
     */
    suspend fun setBodyProfile(
        heightCm: Float,
        weightKg: Float,
        age: Int,
        sex: Sex,
        level: ActivityLevel
    ) = edit {
        val next = it.copy(
            heightCm = heightCm.coerceIn(0f, 250f),
            weightKg = weightKg.coerceIn(0f, 400f),
            age = age.coerceIn(0, 120),
            sex = sex.name,
            activityLevel = level.name
        )
        if (next.autoGoals) next.withCalculatedGoals() else next
    }

    /** Turning this on recalculates immediately; turning it off leaves the goals where they are. */
    suspend fun setAutoGoals(on: Boolean) = edit {
        val next = it.copy(autoGoals = on)
        if (on) next.withCalculatedGoals() else next
    }
    private suspend fun setSeedVersion(version: Int) = edit { it.copy(seedVersion = version) }
    suspend fun setStepGoal(goal: Int) = edit { it.copy(stepGoal = goal) }
    suspend fun setDistanceGoal(km: Float) = edit { it.copy(distanceGoalKm = km) }
    suspend fun setBurnGoal(kcal: Int) = edit { it.copy(burnGoalKcal = kcal) }

    // ---------------------------------------------------------------- habits

    val habits: Flow<List<Habit>> = db.habitDao().active()
    val allHabits: Flow<List<Habit>> = db.habitDao().all()
    fun habit(id: Long): Flow<Habit?> = db.habitDao().byId(id)

    fun habitTotalsOn(date: String) = db.habitDao().totalsOn(date)
    fun habitTotalsBetween(from: String, to: String) = db.habitDao().totalsBetween(from, to)
    fun habitMillisPerDay(habitId: Long, from: String, to: String) = db.habitDao().millisPerDay(habitId, from, to)
    fun allHabitMillisPerDay(from: String, to: String) = db.habitDao().allMillisPerDay(from, to)
    fun sessionsOn(habitId: Long, date: String) = db.habitDao().sessionsOn(habitId, date)
    fun recentSessions(habitId: Long, limit: Int = 20) = db.habitDao().recentSessions(habitId, limit)

    suspend fun addHabit(habit: Habit): Long = db.habitDao().insert(habit.copy(seeded = false))
    suspend fun updateHabit(habit: Habit) = db.habitDao().update(habit)

    /**
     * Seeded habits are archived rather than deleted, so their history stays intact and the
     * tab can never end up empty. Habits the user added are removed outright.
     */
    suspend fun removeHabit(habit: Habit) {
        if (settingsOnce().runningHabitId == habit.id) discardTimer()
        if (habit.seeded) {
            db.habitDao().update(habit.copy(archived = true))
        } else {
            db.habitDao().deleteSessionsFor(habit.id)
            db.habitDao().deleteChecksFor(habit.id)
            db.habitDao().delete(habit)
        }
    }

    suspend fun restoreHabit(habit: Habit) = db.habitDao().update(habit.copy(archived = false))

    suspend fun addMillis(habitId: Long, date: String, millis: Long) {
        if (millis > 0) db.habitDao().insertSession(Session(habitId = habitId, date = date, millis = millis))
    }

    suspend fun deleteSession(s: Session) = db.habitDao().deleteSession(s)

    // ---- the running timer ----

    /** Only one timer runs at a time; starting a new one banks the old one first. */
    suspend fun startTimer(habitId: Long) {
        stopTimer()
        edit { it.copy(runningHabitId = habitId, runningSince = System.currentTimeMillis()) }
    }

    /**
     * Bank the running timer at its real length in milliseconds and return what was saved.
     * Stopping after three seconds records three seconds, not a minute.
     */
    suspend fun stopTimer(): Long {
        val s = settingsOnce()
        val id = s.runningHabitId ?: return 0
        val since = s.runningSince ?: return 0
        val millis = (System.currentTimeMillis() - since).coerceAtLeast(0)
        addMillis(id, Dates.today(), millis)
        db.settingsDao().upsert(s.copy(runningHabitId = null, runningSince = null))
        return millis
    }

    /** Throws away the running timer without banking it. */
    suspend fun discardTimer() = edit { it.copy(runningHabitId = null, runningSince = null) }

    /** Restarts the stopwatch from zero without leaving the habit, and reports what was dropped. */
    suspend fun resetTimer(): Long {
        val s = settingsOnce()
        val since = s.runningSince ?: return 0
        val dropped = (System.currentTimeMillis() - since).coerceAtLeast(0)
        db.settingsDao().upsert(s.copy(runningSince = System.currentTimeMillis()))
        return dropped
    }

    /** Wipes everything banked for a habit today - the "reset today" action. */
    suspend fun resetToday(habitId: Long) {
        val today = Dates.today()
        db.habitDao().deleteSessionsForDate(habitId, today)
        db.habitDao().upsertCheck(HabitCheck(habitId, today, 0))
        if (settingsOnce().runningHabitId == habitId) discardTimer()
    }

    // ---- counted habits ----

    fun checksOn(date: String) = db.habitDao().checksOn(date)
    fun checksBetween(habitId: Long, from: String, to: String) = db.habitDao().checksBetween(habitId, from, to)

    suspend fun bumpCheck(habitId: Long, date: String, delta: Int) {
        val current = db.habitDao().checkOnce(habitId, date)?.count ?: 0
        db.habitDao().upsertCheck(HabitCheck(habitId, date, (current + delta).coerceAtLeast(0)))
    }

    suspend fun setCheck(habitId: Long, date: String, count: Int) =
        db.habitDao().upsertCheck(HabitCheck(habitId, date, count.coerceAtLeast(0)))

    // ---------------------------------------------------------------- reminders

    val reminders: Flow<List<Reminder>> = db.reminderDao().all()
    suspend fun enabledReminders() = db.reminderDao().enabledOnce()
    suspend fun reminder(id: Long) = db.reminderDao().byId(id)
    suspend fun addReminder(r: Reminder): Long = db.reminderDao().insert(r)
    suspend fun updateReminder(r: Reminder) = db.reminderDao().update(r)
    suspend fun deleteReminder(r: Reminder) = db.reminderDao().delete(r)

    // ---------------------------------------------------------------- activity metrics

    fun metricSeries(metric: Metric, from: String, to: String): Flow<List<DayValue>> =
        db.metricDao().series(metric.key, from, to)

    fun metricOn(metric: Metric, date: String): Flow<Double?> = db.metricDao().onDay(metric.key, date)
    fun metricsOn(date: String): Flow<List<DailyMetric>> = db.metricDao().allOn(date)
    suspend fun metricOnce(metric: Metric, date: String): Double? = db.metricDao().onDayOnce(metric.key, date)
    suspend fun metricCount(metric: Metric): Int = db.metricDao().countFor(metric.key)
    suspend fun earliestMetricDate(metric: Metric): String? = db.metricDao().earliest(metric.key)

    suspend fun putMetric(date: String, metric: Metric, value: Double, source: String = "HEALTH_CONNECT") =
        db.metricDao().upsert(DailyMetric(date, metric.key, value, source))

    suspend fun putMetrics(metrics: List<DailyMetric>) = db.metricDao().upsertAll(metrics)

    // ---------------------------------------------------------------- steps by the hour

    fun hourlyStepsOn(date: String): Flow<List<HourlySteps>> = db.stepDao().hoursOn(date)
    fun hourlyStepsBetween(from: String, to: String): Flow<List<HourlySteps>> = db.stepDao().hoursBetween(from, to)
    suspend fun putHourlySteps(rows: List<HourlySteps>) = db.stepDao().upsertHours(rows)
    suspend fun datesWithHourlySteps(from: String, to: String, source: String = "HEALTH_CONNECT") =
        db.stepDao().datesWithHours(from, to, source)

    /** One sensor write at a time: the background job and an open Activity tab can overlap. */
    private val sensorLock = Mutex()

    /**
     * Add steps the phone's own counter saw, already spread over `(date, hour)` by [StepSplit].
     * Each hour and each day's total grow by their share, so the day keeps whatever it had
     * before - including what an older build counted - and only the new steps are added.
     */
    suspend fun addSensorSteps(parts: Map<Pair<String, Int>, Double>) = sensorLock.withLock {
        if (parts.isEmpty()) return@withLock
        val rows = parts.map { (key, steps) ->
            val (date, hour) = key
            val had = db.stepDao().hour(date, hour)?.steps ?: 0.0
            HourlySteps(date, hour, had + steps, source = "SENSOR")
        }
        db.stepDao().upsertHours(rows)
        parts.entries.groupBy({ it.key.first }, { it.value }).forEach { (date, added) ->
            val had = metricOnce(Metric.Steps, date) ?: 0.0
            putMetric(date, Metric.Steps, had + added.sum(), source = "SENSOR")
        }
    }

    // ---------------------------------------------------------------- daily routine

    val routineItems: Flow<List<RoutineItem>> = db.routineDao().items()
    suspend fun routineItemsOnce(): List<RoutineItem> = db.routineDao().itemsOnce()
    suspend fun routineItem(id: Long): RoutineItem? = db.routineDao().item(id)
    suspend fun updateRoutineItem(item: RoutineItem) = db.routineDao().updateItem(item)

    fun routinePlans(from: String, to: String): Flow<List<RoutinePlan>> = db.routineDao().plansBetween(from, to)
    suspend fun routinePlansOnce(from: String, to: String): List<RoutinePlan> =
        db.routineDao().plansBetweenOnce(from, to)
    suspend fun routinePlan(date: String, itemId: Long): RoutinePlan? = db.routineDao().plan(date, itemId)

    /** The night-before plan: one row per item, so "saved" is visible even when nothing moved. */
    suspend fun saveRoutinePlan(plans: List<RoutinePlan>) = db.routineDao().upsertPlans(plans)

    fun routineLogs(from: String, to: String): Flow<List<RoutineLog>> = db.routineDao().logsBetween(from, to)

    /** [status] is [Routine.DONE] or [Routine.MISSED]; null clears the answer back to "not yet". */
    suspend fun setRoutineStatus(date: String, itemId: Long, status: String?) {
        if (status == null) db.routineDao().clearLog(date, itemId)
        else db.routineDao().upsertLog(RoutineLog(date, itemId, status))
    }

    // ---------------------------------------------------------------- screen time

    val trackedApps: Flow<List<TrackedApp>> = db.trackedAppDao().all()
    suspend fun upsertTrackedApp(app: TrackedApp) =
        db.trackedAppDao().upsert(app.copy(dailyLimitMin = app.dailyLimitMin.coerceAtMost(MAX_APP_LIMIT_MIN)))

    /** Only apps the user added themselves can be removed; the starter five stay put. */
    suspend fun removeTrackedApp(app: TrackedApp): Boolean {
        if (app.seeded) return false
        db.trackedAppDao().delete(app)
        return true
    }

    suspend fun recordUsage(date: String, minutesByPackage: Map<String, Int>) =
        db.trackedAppDao().upsertUsageAll(minutesByPackage.map { (pkg, min) -> AppUsageDay(date, pkg, min) })

    suspend fun usageMinutesToday(pkg: String): Int? = db.trackedAppDao().minutesOn(pkg, Dates.today())
    fun usageTotalPerDay(from: String, to: String) = db.trackedAppDao().totalPerDay(from, to)
    fun usagePerDayFor(pkg: String, from: String, to: String) = db.trackedAppDao().perDayFor(pkg, from, to)
    fun usageTotalPerApp(from: String, to: String) = db.trackedAppDao().totalPerApp(from, to)
}
