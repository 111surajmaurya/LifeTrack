package com.lifetrack.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** date -> value, the shape every chart in the app consumes. */
data class DayValue(val date: String, val value: Double)

data class DateSlot(val date: String, val value: String)

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE date = :date ORDER BY createdAt ASC")
    fun mealsOn(date: String): Flow<List<Meal>>

    @Query("SELECT * FROM meals WHERE date BETWEEN :from AND :to ORDER BY date, createdAt")
    fun mealsBetween(from: String, to: String): Flow<List<Meal>>

    @Query("SELECT date, CAST(SUM(kcal) AS REAL) AS value FROM meals WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    fun kcalPerDay(from: String, to: String): Flow<List<DayValue>>

    @Query("SELECT slot AS date, CAST(SUM(kcal) AS REAL) AS value FROM meals WHERE date BETWEEN :from AND :to GROUP BY slot")
    fun kcalPerSlot(from: String, to: String): Flow<List<DayValue>>

    @Query("SELECT name AS date, CAST(SUM(kcal) AS REAL) AS value FROM meals WHERE date BETWEEN :from AND :to GROUP BY name ORDER BY value DESC LIMIT :limit")
    fun topFoods(from: String, to: String, limit: Int): Flow<List<DayValue>>

    @Query("SELECT date, CAST(SUM(protein) AS REAL) AS value FROM meals WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    fun proteinPerDay(from: String, to: String): Flow<List<DayValue>>

    @Query("SELECT date, CAST(SUM(fiber) AS REAL) AS value FROM meals WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    fun fiberPerDay(from: String, to: String): Flow<List<DayValue>>

    @Query("SELECT name AS date, CAST(SUM(protein) AS REAL) AS value FROM meals WHERE date BETWEEN :from AND :to GROUP BY name ORDER BY value DESC LIMIT :limit")
    fun topProteinFoods(from: String, to: String, limit: Int): Flow<List<DayValue>>

    /** `date -> slot` pairs with anything logged, which is how meal routine items tick themselves. */
    @Query("SELECT DISTINCT date, slot AS value FROM meals WHERE date BETWEEN :from AND :to")
    fun slotsLogged(from: String, to: String): Flow<List<DateSlot>>

    @Query("DELETE FROM meals WHERE date < :cutoff")
    suspend fun deleteBefore(cutoff: String): Int

    @Insert suspend fun insert(meal: Meal): Long
    @Update suspend fun update(meal: Meal)
    @Delete suspend fun delete(meal: Meal)
}

@Dao
interface FoodDao {
    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun count(): Int

    /** The seeded rows, which `refreshCatalogue` re-points at the current FoodSeed figures. */
    @Query("SELECT * FROM food_items WHERE custom = 0")
    suspend fun seededFoods(): List<FoodItem>

    @Query("SELECT name FROM food_items")
    suspend fun allNames(): List<String>

    /**
     * Type-ahead search. Matches at the start of the name or at the start of any word inside it,
     * so "da" finds Daal, Daliya and Dahi, and "pan" finds Paneer and Palak Paneer.
     * Exact prefixes rank first, then whatever you eat most.
     */
    @Query(
        """
        SELECT * FROM food_items
        WHERE name LIKE :q || '%' OR name LIKE '% ' || :q || '%'
        ORDER BY (CASE WHEN name LIKE :q || '%' THEN 0 ELSE 1 END),
                 useCount DESC, lastUsedAt DESC, name ASC
        LIMIT 30
        """
    )
    fun search(q: String): Flow<List<FoodItem>>

    /** What to show before the user types: most-eaten first, then a stable alphabetical tail. */
    @Query("SELECT * FROM food_items ORDER BY useCount DESC, lastUsedAt DESC, name ASC LIMIT 20")
    fun suggestions(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE category = :category ORDER BY useCount DESC, name ASC")
    fun byCategory(category: String): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE lower(name) = lower(:name) LIMIT 1")
    suspend fun byName(name: String): FoodItem?

    @Query("UPDATE food_items SET useCount = useCount + 1, lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)

    @Insert suspend fun insert(item: FoodItem): Long
    @Insert suspend fun insertAll(items: List<FoodItem>)
    @Update suspend fun update(item: FoodItem)
    @Delete suspend fun delete(item: FoodItem)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun settings(): Flow<Settings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settingsOnce(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: Settings)
}

data class HabitTotal(val habitId: Long, val millis: Long)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder, id")
    fun active(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY archived, sortOrder, id")
    fun all(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun byId(id: Long): Flow<Habit?>

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun count(): Int

    @Insert suspend fun insert(habit: Habit): Long
    @Insert suspend fun insertAll(habits: List<Habit>)
    @Update suspend fun update(habit: Habit)
    @Delete suspend fun delete(habit: Habit)

    // ---- timed sessions ----
    @Query("SELECT habitId, SUM(millis) AS millis FROM sessions WHERE date = :date GROUP BY habitId")
    fun totalsOn(date: String): Flow<List<HabitTotal>>

    @Query("SELECT habitId, SUM(millis) AS millis FROM sessions WHERE date BETWEEN :from AND :to GROUP BY habitId")
    fun totalsBetween(from: String, to: String): Flow<List<HabitTotal>>

    @Query("SELECT date, CAST(SUM(millis) AS REAL) AS value FROM sessions WHERE habitId = :habitId AND date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    fun millisPerDay(habitId: Long, from: String, to: String): Flow<List<DayValue>>

    @Query("SELECT date, CAST(SUM(millis) AS REAL) AS value FROM sessions WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    fun allMillisPerDay(from: String, to: String): Flow<List<DayValue>>

    @Query("SELECT * FROM sessions WHERE habitId = :habitId AND date = :date ORDER BY endedAt DESC")
    fun sessionsOn(habitId: Long, date: String): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE habitId = :habitId ORDER BY endedAt DESC LIMIT :limit")
    fun recentSessions(habitId: Long, limit: Int): Flow<List<Session>>

    @Query("DELETE FROM sessions WHERE habitId = :habitId")
    suspend fun deleteSessionsFor(habitId: Long)

    @Query("DELETE FROM sessions WHERE habitId = :habitId AND date = :date")
    suspend fun deleteSessionsForDate(habitId: Long, date: String)

    @Query("DELETE FROM sessions WHERE date < :cutoff")
    suspend fun deleteSessionsBefore(cutoff: String): Int

    @Insert suspend fun insertSession(session: Session): Long
    @Delete suspend fun deleteSession(session: Session)

    // ---- counted checks ----
    @Query("SELECT * FROM habit_checks WHERE date = :date")
    fun checksOn(date: String): Flow<List<HabitCheck>>

    @Query("SELECT * FROM habit_checks WHERE habitId = :habitId AND date BETWEEN :from AND :to ORDER BY date")
    fun checksBetween(habitId: Long, from: String, to: String): Flow<List<HabitCheck>>

    @Query("SELECT * FROM habit_checks WHERE habitId = :habitId AND date = :date")
    suspend fun checkOnce(habitId: Long, date: String): HabitCheck?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheck(check: HabitCheck)

    @Query("DELETE FROM habit_checks WHERE habitId = :habitId")
    suspend fun deleteChecksFor(habitId: Long)

    @Query("DELETE FROM habit_checks WHERE date < :cutoff")
    suspend fun deleteChecksBefore(cutoff: String): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun all(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun enabledOnce(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: Long): Reminder?

    @Insert suspend fun insert(reminder: Reminder): Long
    @Update suspend fun update(reminder: Reminder)
    @Delete suspend fun delete(reminder: Reminder)
}

@Dao
interface TrackedAppDao {
    @Query("SELECT * FROM tracked_apps ORDER BY seeded DESC, label")
    fun all(): Flow<List<TrackedApp>>

    @Query("SELECT COUNT(*) FROM tracked_apps")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(app: TrackedApp)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(apps: List<TrackedApp>)
    @Delete suspend fun delete(app: TrackedApp)

    // ---- daily history, so screen time can be charted over weeks ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsage(day: AppUsageDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsageAll(days: List<AppUsageDay>)

    @Query("SELECT date, CAST(SUM(minutes) AS REAL) AS value FROM app_usage_days WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    fun totalPerDay(from: String, to: String): Flow<List<DayValue>>

    @Query("SELECT date, CAST(minutes AS REAL) AS value FROM app_usage_days WHERE packageName = :pkg AND date BETWEEN :from AND :to ORDER BY date")
    fun perDayFor(pkg: String, from: String, to: String): Flow<List<DayValue>>

    @Query("SELECT packageName AS date, CAST(SUM(minutes) AS REAL) AS value FROM app_usage_days WHERE date BETWEEN :from AND :to GROUP BY packageName ORDER BY value DESC")
    fun totalPerApp(from: String, to: String): Flow<List<DayValue>>

    /** Pulls any limit above [max] down to it - for limits saved before the ceiling existed. */
    @Query("UPDATE tracked_apps SET dailyLimitMin = :max WHERE dailyLimitMin > :max")
    suspend fun capLimits(max: Int): Int

    @Query("SELECT minutes FROM app_usage_days WHERE date = :date AND packageName = :pkg")
    suspend fun minutesOn(pkg: String, date: String): Int?

    @Query("DELETE FROM app_usage_days WHERE date < :cutoff")
    suspend fun deleteUsageBefore(cutoff: String): Int

    @Query("DELETE FROM app_usage_days WHERE packageName = :pkg")
    suspend fun deleteUsageFor(pkg: String): Int
}

@Dao
interface MetricDao {
    @Query("SELECT date, value FROM daily_metrics WHERE metric = :metric AND date BETWEEN :from AND :to ORDER BY date")
    fun series(metric: String, from: String, to: String): Flow<List<DayValue>>

    @Query("SELECT value FROM daily_metrics WHERE metric = :metric AND date = :date")
    fun onDay(metric: String, date: String): Flow<Double?>

    @Query("SELECT value FROM daily_metrics WHERE metric = :metric AND date = :date")
    suspend fun onDayOnce(metric: String, date: String): Double?

    @Query("SELECT * FROM daily_metrics WHERE date = :date")
    fun allOn(date: String): Flow<List<DailyMetric>>

    @Query("SELECT COUNT(*) FROM daily_metrics WHERE metric = :metric")
    suspend fun countFor(metric: String): Int

    @Query("SELECT MIN(date) FROM daily_metrics WHERE metric = :metric")
    suspend fun earliest(metric: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metric: DailyMetric)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metrics: List<DailyMetric>)

    @Query("DELETE FROM daily_metrics WHERE date < :cutoff")
    suspend fun deleteBefore(cutoff: String): Int
}

@Dao
interface StepDao {
    @Query("SELECT * FROM step_days WHERE date = :date")
    suspend fun day(date: String): StepDay?

    @Query("SELECT * FROM step_days WHERE date BETWEEN :from AND :to ORDER BY date")
    fun between(from: String, to: String): Flow<List<StepDay>>

    @Query("DELETE FROM step_days WHERE date < :cutoff")
    suspend fun deleteBefore(cutoff: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(day: StepDay)

    // ---- steps by clock hour ----
    @Query("SELECT * FROM hourly_steps WHERE date = :date ORDER BY hour")
    fun hoursOn(date: String): Flow<List<HourlySteps>>

    @Query("SELECT * FROM hourly_steps WHERE date = :date ORDER BY hour")
    suspend fun hoursOnce(date: String): List<HourlySteps>

    @Query("SELECT * FROM hourly_steps WHERE date BETWEEN :from AND :to ORDER BY date, hour")
    fun hoursBetween(from: String, to: String): Flow<List<HourlySteps>>

    @Query("SELECT * FROM hourly_steps WHERE date = :date AND hour = :hour")
    suspend fun hour(date: String, hour: Int): HourlySteps?

    @Query("SELECT DISTINCT date FROM hourly_steps WHERE date BETWEEN :from AND :to AND source = :source")
    suspend fun datesWithHours(from: String, to: String, source: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHours(rows: List<HourlySteps>)

    @Query("DELETE FROM hourly_steps WHERE date < :cutoff")
    suspend fun deleteHoursBefore(cutoff: String): Int
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_items ORDER BY sortOrder, id")
    fun items(): Flow<List<RoutineItem>>

    @Query("SELECT * FROM routine_items ORDER BY sortOrder, id")
    suspend fun itemsOnce(): List<RoutineItem>

    @Query("SELECT * FROM routine_items WHERE id = :id")
    suspend fun item(id: Long): RoutineItem?

    @Query("SELECT `key` FROM routine_items")
    suspend fun keys(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertItems(items: List<RoutineItem>)
    @Update suspend fun updateItem(item: RoutineItem)

    // ---- per-day plans ----
    @Query("SELECT * FROM routine_plans WHERE date BETWEEN :from AND :to")
    fun plansBetween(from: String, to: String): Flow<List<RoutinePlan>>

    @Query("SELECT * FROM routine_plans WHERE date BETWEEN :from AND :to")
    suspend fun plansBetweenOnce(from: String, to: String): List<RoutinePlan>

    @Query("SELECT * FROM routine_plans WHERE date = :date AND itemId = :itemId")
    suspend fun plan(date: String, itemId: Long): RoutinePlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlans(plans: List<RoutinePlan>)

    @Query("DELETE FROM routine_plans WHERE date < :cutoff")
    suspend fun deletePlansBefore(cutoff: String): Int

    // ---- done / missed ----
    @Query("SELECT * FROM routine_logs WHERE date BETWEEN :from AND :to")
    fun logsBetween(from: String, to: String): Flow<List<RoutineLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLog(log: RoutineLog)

    @Query("DELETE FROM routine_logs WHERE date = :date AND itemId = :itemId")
    suspend fun clearLog(date: String, itemId: Long)

    @Query("DELETE FROM routine_logs WHERE date < :cutoff")
    suspend fun deleteLogsBefore(cutoff: String): Int
}
