package com.lifetrack.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.math.roundToInt

// ---------------------------------------------------------------- food

/** Meal slots, in the order they happen through the day. */
enum class Slot(val label: String, val emoji: String) {
    Breakfast("Breakfast", "🌅"),
    Lunch("Lunch", "☀"),
    Snacks("Snacks", "🍪"),
    Dinner("Dinner", "🌙");

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: Snacks

        /** Best guess for "right now", so logging a meal costs one tap less. */
        fun forHour(hour: Int) = when (hour) {
            in 4..10 -> Breakfast
            in 11..15 -> Lunch
            in 16..18 -> Snacks
            else -> Dinner
        }

        /** Default share of the daily goal each meal gets: 25 / 35 / 10 / 30. */
        fun defaultShare(slot: Slot) = when (slot) {
            Breakfast -> 0.25f
            Lunch -> 0.35f
            Snacks -> 0.10f
            Dinner -> 0.30f
        }
    }
}

/** Bowl/plate/glass size. Foods counted in pieces ignore this. */
enum class Portion(val label: String, val factor: Float) {
    Small("Small", 0.6f),
    Medium("Medium", 1f),
    Large("Large", 1.5f);

    companion object { fun from(name: String?) = entries.firstOrNull { it.name == name } ?: Medium }
}

/**
 * How a food is served, which decides what the picker offers.
 * [sized] foods (rice, daal, sabji) get small/medium/large; the rest just get a count.
 */
enum class Serving(val unit: String, val sized: Boolean) {
    Piece("piece", false),
    Bowl("bowl", true),
    Plate("plate", true),
    Glass("glass", true),
    Cup("cup", true),
    Spoon("tsp", false),
    Handful("handful", false),
    Serve("serving", false);

    companion object { fun from(name: String?) = entries.firstOrNull { it.name == name } ?: Piece }
}

/**
 * What one helping of something is worth.
 *
 * Grouped into one type so a food, a logged meal and a day's total are all added up by the
 * same code: there is no second place where fibre could be summed but vitamin C forgotten.
 * Units are the ones the labels use - grams for protein and fibre, ug RAE for vitamin A,
 * mg for the rest.
 */
data class Nutrients(
    val kcal: Int = 0,
    val protein: Float = 0f,
    val fiber: Float = 0f,
    val vitA: Float = 0f,
    val vitC: Float = 0f,
    val iron: Float = 0f,
    val calcium: Float = 0f
) {
    operator fun plus(other: Nutrients) = Nutrients(
        kcal + other.kcal, protein + other.protein, fiber + other.fiber,
        vitA + other.vitA, vitC + other.vitC, iron + other.iron, calcium + other.calcium
    )

    operator fun times(factor: Float) = Nutrients(
        (kcal * factor).roundToInt(), protein * factor, fiber * factor,
        vitA * factor, vitC * factor, iron * factor, calcium * factor
    )

    companion object {
        val NONE = Nutrients()
        fun sum(parts: Iterable<Nutrients>): Nutrients = parts.fold(NONE, Nutrients::plus)
    }
}

/** Used by the BMR formula, which genuinely differs by sex. */
enum class Sex(val label: String) {
    Female("Female"),
    Male("Male"),
    Unspecified("Rather not say");

    companion object { fun from(name: String?) = entries.firstOrNull { it.name == name } ?: Unspecified }
}

/** How much you move, which is what turns a resting burn into a daily one. */
enum class ActivityLevel(val label: String, val factor: Float, val hint: String) {
    Sedentary("Sedentary", 1.2f, "desk job, little or no exercise"),
    Light("Light", 1.375f, "light exercise 1-3 days a week"),
    Moderate("Moderate", 1.55f, "exercise 3-5 days a week"),
    Active("Active", 1.725f, "hard exercise 6-7 days a week"),
    VeryActive("Very active", 1.9f, "physical job, or training twice a day");

    companion object { fun from(name: String?) = entries.firstOrNull { it.name == name } ?: Moderate }
}

/**
 * Turning a body into daily targets.
 *
 * [bmr] is Mifflin-St Jeor, which is the formula most nutrition software uses and the one that
 * validates best against measured resting expenditure in adults. It is still a population
 * average: two people of the same height, weight and age can differ by a few hundred calories.
 * Treat the number it produces as a starting point to adjust from, not a prescription.
 */
object BodyMath {

    /** Resting burn in kcal/day. Returns 0 if the profile is not filled in. */
    fun bmr(sex: Sex, weightKg: Float, heightCm: Float, age: Int): Int {
        if (weightKg <= 0f || heightCm <= 0f || age <= 0) return 0
        val base = 10f * weightKg + 6.25f * heightCm - 5f * age
        // Mifflin-St Jeor adds +5 for men and -161 for women. With no answer given, sit in the
        // middle rather than silently assuming one - it is the least wrong option.
        val offset = when (sex) {
            Sex.Male -> 5f
            Sex.Female -> -161f
            Sex.Unspecified -> -78f
        }
        return (base + offset).roundToInt()
    }

    /** Resting burn scaled by how much you actually move: the calorie goal. */
    fun tdee(sex: Sex, weightKg: Float, heightCm: Float, age: Int, level: ActivityLevel): Int {
        val rest = bmr(sex, weightKg, heightCm, age)
        return if (rest == 0) 0 else (rest * level.factor).roundToInt()
    }

    /**
     * Grams of protein a day, scaled by body weight rather than by calories - protein need
     * tracks lean mass, not appetite. 1.0 g/kg sedentary up to 1.6 g/kg training hard, which
     * is the range the sports-nutrition literature converges on.
     */
    fun protein(weightKg: Float, level: ActivityLevel): Int {
        if (weightKg <= 0f) return 0
        val perKg = when (level) {
            ActivityLevel.Sedentary -> 1.0f
            ActivityLevel.Light -> 1.2f
            ActivityLevel.Moderate -> 1.4f
            ActivityLevel.Active, ActivityLevel.VeryActive -> 1.6f
        }
        return (weightKg * perKg).roundToInt()
    }

    /** 14 g of fibre per 1000 kcal, the standard guideline, so it scales with how much you eat. */
    fun fiber(kcalGoal: Int): Int =
        if (kcalGoal <= 0) 0 else (kcalGoal * 14f / 1000f).roundToInt()
}

/**
 * Reference daily intakes, used to turn a raw milligram into "38% of a day".
 *
 * Adult values from the ICMR-NIN Recommended Dietary Allowances for Indians (2020) where
 * they differ meaningfully from Western ones - iron especially, which is set higher for an
 * Indian diet because non-haem iron from plants is absorbed far less readily than the haem
 * iron in meat.
 */
object DailyValue {
    const val VIT_A_UG = 900f
    const val VIT_C_MG = 80f
    const val IRON_MG = 19f
    const val CALCIUM_MG = 1000f
}

/**
 * One food in the searchable catalogue. Every figure is for a single *medium* serving,
 * so a medium bowl of daal is 218 kcal and a large one is 218 x 1.5.
 * Anything the user types in themselves lands here too, with [custom] set.
 */
@Entity(tableName = "food_items", indices = [Index("name")])
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcal: Int,
    val serving: String = Serving.Piece.name,
    val category: String = "Other",
    val custom: Boolean = false,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0,
    val protein: Float = 0f,
    val fiber: Float = 0f,
    val vitA: Float = 0f,
    val vitC: Float = 0f,
    val iron: Float = 0f,
    val calcium: Float = 0f
) {
    val servingType: Serving get() = Serving.from(serving)

    /** Everything this food is worth at one medium serving. */
    val nutrients: Nutrients get() = Nutrients(kcal, protein, fiber, vitA, vitC, iron, calcium)

    /** How much a [qty]/[portion] helping multiplies a per-medium-serving figure by. */
    private fun scale(qty: Float, portion: Portion): Float =
        qty * (if (servingType.sized) portion.factor else 1f)

    /** The whole panel for [qty] servings at [portion]; portion is ignored for unsized foods. */
    fun nutrientsFor(qty: Float, portion: Portion): Nutrients = nutrients * scale(qty, portion)

    /** Calories for [qty] servings at [portion]. */
    fun kcalFor(qty: Float, portion: Portion): Int = nutrientsFor(qty, portion).kcal

    /** Grams of protein for the same helping. */
    fun proteinFor(qty: Float, portion: Portion): Float = nutrientsFor(qty, portion).protein

    /** "1 roti", "2 medium bowls", "1.5 glasses" */
    fun describe(qty: Float, portion: Portion): String {
        val n = if (qty % 1f == 0f) qty.toInt().toString() else qty.toString()
        val size = if (servingType.sized) portion.label.lowercase() + " " else ""
        val unit = servingType.unit + if (qty > 1f) "s" else ""
        return "$n $size$unit"
    }
}

/** A logged meal. `date` is ISO yyyy-MM-dd so it sorts and groups cleanly. */
@Entity(tableName = "meals", indices = [Index("date")])
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val name: String,
    val kcal: Int,
    val slot: String = Slot.Snacks.name,
    val qty: Float = 1f,
    val portion: String = Portion.Medium.name,
    val serving: String = Serving.Serve.name,
    val foodItemId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Frozen at log time, so correcting a catalogue figure - or the whole catalogue moving to
     * measured USDA data, as it did in v7 - never silently rewrites what you ate last month.
     */
    val protein: Float = 0f,
    val fiber: Float = 0f,
    val vitA: Float = 0f,
    val vitC: Float = 0f,
    val iron: Float = 0f,
    val calcium: Float = 0f
) {
    val slotType: Slot get() = Slot.from(slot)

    val nutrients: Nutrients get() = Nutrients(kcal, protein, fiber, vitA, vitC, iron, calcium)

    /** "2 rotis", "1 large bowl" - the line under the food name. */
    val detail: String
        get() {
            val s = Serving.from(serving)
            val n = if (qty % 1f == 0f) qty.toInt().toString() else qty.toString()
            val size = if (s.sized) Portion.from(portion).label.lowercase() + " " else ""
            return "$n $size${s.unit}${if (qty > 1f) "s" else ""}"
        }
}

// ---------------------------------------------------------------- settings

/**
 * Single-row table for user settings (id fixed at 1).
 * Also holds the currently running habit timer so it survives the app being killed.
 */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val calorieGoal: Int = 2000,
    val runningHabitId: Long? = null,
    val runningSince: Long? = null,
    val stepGoal: Int = 8000,
    /** Which meal the Calories tab is currently filing food under. */
    val activeSlot: String = Slot.Snacks.name,
    /** A single item above this many kcal gets flagged red in the log. */
    val itemWarnKcal: Int = 300,
    val breakfastGoal: Int = 500,
    val lunchGoal: Int = 700,
    val snacksGoal: Int = 200,
    val dinnerGoal: Int = 600,
    val distanceGoalKm: Float = 5f,
    val burnGoalKcal: Int = 400,
    /** Grams per day. One daily target, not four - protein is a whole-day number. */
    @ColumnInfo(defaultValue = "60") val proteinGoal: Int = 60,
    /** Grams per day. 30 g is the ICMR-NIN adult recommendation. */
    @ColumnInfo(defaultValue = "30") val fiberGoal: Int = 30,
    /**
     * Which build of [FoodSeed] the catalogue was last refreshed from. Bumping the constant
     * in FoodSeed is what makes an existing install pick up corrected numbers; without this
     * the catalogue would be frozen at whatever shipped the day it was first launched.
     */
    @ColumnInfo(defaultValue = "0") val seedVersion: Int = 0,
    // ---- body profile, optional: everything still works with all of this left at zero ----
    @ColumnInfo(defaultValue = "0") val heightCm: Float = 0f,
    @ColumnInfo(defaultValue = "0") val weightKg: Float = 0f,
    @ColumnInfo(defaultValue = "0") val age: Int = 0,
    @ColumnInfo(defaultValue = "Unspecified") val sex: String = Sex.Unspecified.name,
    @ColumnInfo(defaultValue = "Moderate") val activityLevel: String = ActivityLevel.Moderate.name,
    /**
     * Keep the goals in step with the profile. On by default, so filling in the profile is all
     * it takes; typing a goal by hand turns it off, because at that point you have said what
     * you want more clearly than the formula can guess it.
     */
    @ColumnInfo(defaultValue = "1") val autoGoals: Boolean = true
) {
    val sexType: Sex get() = Sex.from(sex)
    val activity: ActivityLevel get() = ActivityLevel.from(activityLevel)

    /** Enough filled in for the formula to mean anything. */
    val hasBodyProfile: Boolean get() = heightCm > 0f && weightKg > 0f && age > 0

    val bmr: Int get() = BodyMath.bmr(sexType, weightKg, heightCm, age)
    val tdee: Int get() = BodyMath.tdee(sexType, weightKg, heightCm, age, activity)
    val suggestedProtein: Int get() = BodyMath.protein(weightKg, activity)
    val suggestedFiber: Int get() = BodyMath.fiber(tdee)

    /** True when the goals in use are the ones the profile would suggest. */
    val goalsMatchProfile: Boolean
        get() = hasBodyProfile && calorieGoal == tdee && proteinGoal == suggestedProtein

    /** Whether the goals are actually being driven by the profile right now. */
    val goalsAreCalculated: Boolean get() = autoGoals && hasBodyProfile

    /**
     * The goals this profile implies: the calorie target from [tdee], protein from body weight,
     * fibre from the calories, and the four meal budgets split 25/35/10/30 as usual.
     *
     * Returns the settings untouched when the profile is incomplete - a half-filled profile
     * must never quietly rewrite a goal you set yourself.
     */
    fun withCalculatedGoals(): Settings {
        if (!hasBodyProfile) return this
        val kcal = tdee
        var next = copy(
            calorieGoal = kcal,
            proteinGoal = suggestedProtein,
            fiberGoal = suggestedFiber
        )
        Slot.entries.forEach { slot ->
            next = next.withGoalFor(slot, (kcal * Slot.defaultShare(slot)).roundToInt())
        }
        return next
    }
    fun goalFor(slot: Slot): Int = when (slot) {
        Slot.Breakfast -> breakfastGoal
        Slot.Lunch -> lunchGoal
        Slot.Snacks -> snacksGoal
        Slot.Dinner -> dinnerGoal
    }

    fun withGoalFor(slot: Slot, value: Int): Settings = when (slot) {
        Slot.Breakfast -> copy(breakfastGoal = value)
        Slot.Lunch -> copy(lunchGoal = value)
        Slot.Snacks -> copy(snacksGoal = value)
        Slot.Dinner -> copy(dinnerGoal = value)
    }

    val activeSlotType: Slot get() = Slot.from(activeSlot)
}

// ---------------------------------------------------------------- habits

/** A timed habit runs a stopwatch; a counted one is just tapped off. */
enum class HabitKind { TIMED, COUNT;
    companion object { fun from(n: String?) = entries.firstOrNull { it.name == n } ?: TIMED }
}

/**
 * Something you want to do every day. [seeded] habits ship with the app and can be
 * archived but not deleted, so the tab is never empty and history is never orphaned.
 */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = HabitKind.TIMED.name,
    /** TIMED: minutes per day. */
    val dailyGoalMin: Int = 30,
    /** COUNT: how many times a day counts as done. */
    val dailyTarget: Int = 1,
    val emoji: String = "⏱",
    val accent: String = "leaf",
    val seeded: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0
) {
    val kindType: HabitKind get() = HabitKind.from(kind)
    val dailyGoalMillis: Long get() = dailyGoalMin * 60_000L
}

/**
 * A finished block of time on a habit, stored in **milliseconds**.
 * Seconds were already better than the original minutes; millis means the stopwatch
 * you see on screen and the number that gets banked are the same value.
 */
@Entity(
    tableName = "sessions",
    indices = [Index("habitId"), Index("date")]
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: String,
    val millis: Long,
    val endedAt: Long = System.currentTimeMillis()
)

/** How many times a COUNT habit was ticked off on a given day. */
@Entity(tableName = "habit_checks", primaryKeys = ["habitId", "date"], indices = [Index("date")])
data class HabitCheck(
    val habitId: Long,
    val date: String,
    val count: Int = 0
)

// ---------------------------------------------------------------- reminders

/** A reminder either drops a notification or takes over the screen and rings. */
enum class ReminderMode(val label: String) {
    NOTIFICATION("Notification"),
    ALARM("Alarm");

    companion object { fun from(n: String?) = entries.firstOrNull { it.name == n } ?: NOTIFICATION }
}

/**
 * A scheduled reminder. `daysMask` uses bit (dayOfWeek - 1): Mon = bit 0 ... Sun = bit 6.
 * `habitId` is optional so you can also have free-form reminders ("log dinner").
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val habitId: Long? = null,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = 0b1111111,
    val enabled: Boolean = true,
    val mode: String = ReminderMode.NOTIFICATION.name,
    /** How long an alarm rings before giving up. */
    val ringSeconds: Int = 20,
    val snoozeMinutes: Int = 5,
    val vibrate: Boolean = true
) {
    val modeType: ReminderMode get() = ReminderMode.from(mode)
    val isAlarm: Boolean get() = modeType == ReminderMode.ALARM
}

// ---------------------------------------------------------------- screen time

/**
 * An app whose foreground time we read from UsageStats, with a daily limit.
 * [seeded] apps come with the app; only ones the user added can be removed.
 */
@Entity(tableName = "tracked_apps")
data class TrackedApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val dailyLimitMin: Int,
    val seeded: Boolean = false
)

/** One day of foreground time for one app, kept so the charts have history. */
@Entity(tableName = "app_usage_days", primaryKeys = ["date", "packageName"], indices = [Index("date")])
data class AppUsageDay(
    val date: String,
    val packageName: String,
    val minutes: Int
)

// ---------------------------------------------------------------- activity metrics

/** Everything the Activity tab can chart. Stored generically so adding one is a one-liner. */
enum class Metric(val key: String, val label: String, val unit: String, val emoji: String) {
    Steps("STEPS", "Steps", "steps", "👣"),
    Distance("DISTANCE_M", "Distance", "m", "📍"),
    Burn("BURN_KCAL", "Calories burned", "kcal", "🔥"),
    ActiveMinutes("ACTIVE_MIN", "Active time", "min", "⚡"),
    HeartRate("HEART_BPM", "Resting heart rate", "bpm", "❤"),
    Sleep("SLEEP_MIN", "Sleep", "min", "😴");

    companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } }
}

/**
 * One value for one metric on one day, whatever the source. Health Connect history is
 * imported into this table on first connect, so charts survive the provider going away.
 */
@Entity(tableName = "daily_metrics", primaryKeys = ["date", "metric"], indices = [Index("date"), Index("metric")])
data class DailyMetric(
    val date: String,
    val metric: String,
    val value: Double,
    val source: String = "HEALTH_CONNECT",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Fallback step counting from the phone's hardware sensor, which reports a cumulative
 * count since boot. We store where the count stood at the start of the day and handle resets.
 *   steps today = accumulated + (latest - baseline)
 */
@Entity(tableName = "step_days")
data class StepDay(
    @PrimaryKey val date: String,
    val baseline: Long,
    val latest: Long,
    val accumulated: Long = 0
) {
    val steps: Long get() = accumulated + (latest - baseline).coerceAtLeast(0)
}
