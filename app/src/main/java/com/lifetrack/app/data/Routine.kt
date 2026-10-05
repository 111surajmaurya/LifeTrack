package com.lifetrack.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// ---------------------------------------------------------------- daily routine

/**
 * How an item proves it was done. Most are a tap ("Done" on the notification or in the
 * Routine tab), but some have evidence the app already holds, and those tick themselves:
 *
 *  - [WAKE]: holding to dismiss the wake-up alarm is proof enough of being up.
 *  - [MEAL]: anything logged under that meal slot that day.
 *  - [WALK]: [Routine.WALK_STEPS] steps in the two clock hours starting at the item's time.
 *  - [PLAN]: tomorrow's plan saved.
 */
enum class RoutineKind { MANUAL, WAKE, MEAL, WALK, PLAN;
    companion object { fun from(n: String?) = entries.firstOrNull { it.name == n } ?: MANUAL }
}

/**
 * One fixed point in the day. [hour]/[minute] are the default every day starts from; the
 * nightly plan can move or switch off a single day in [RoutinePlan] without touching this.
 * [key] is the stable seed identity, so a re-seed never duplicates and a rename never orphans.
 */
@Entity(tableName = "routine_items", indices = [Index(value = ["key"], unique = true)])
data class RoutineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val title: String,
    val emoji: String,
    val mode: String = ReminderMode.NOTIFICATION.name,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val kind: String = RoutineKind.MANUAL.name,
    /** For [RoutineKind.MEAL]: which [Slot] counts. Empty otherwise. */
    val slot: String = "",
    val sortOrder: Int = 0
) {
    val modeType: ReminderMode get() = ReminderMode.from(mode)
    val isAlarm: Boolean get() = modeType == ReminderMode.ALARM
    val kindType: RoutineKind get() = RoutineKind.from(kind)
    val minuteOfDay: Int get() = hour * 60 + minute
}

/** Tomorrow's (or any day's) override for one item: a different time, or the day off. */
@Entity(tableName = "routine_plans", primaryKeys = ["date", "itemId"])
data class RoutinePlan(
    val date: String,
    val itemId: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean
)

/** What the user said about one item on one day. Absence plus a past day reads as missed. */
@Entity(tableName = "routine_logs", primaryKeys = ["date", "itemId"])
data class RoutineLog(
    val date: String,
    val itemId: Long,
    val status: String,
    val at: Long = System.currentTimeMillis()
)

enum class RoutineStatus { DONE, MISSED, PENDING, OFF }

/** One item on one day after the plan, the log and the evidence have all been weighed. */
data class RoutineSlot(
    val item: RoutineItem,
    val date: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val status: RoutineStatus,
    /** True when the status came from evidence (a meal, steps, a plan) rather than a tap. */
    val auto: Boolean = false
) {
    val minuteOfDay: Int get() = hour * 60 + minute
}

/** What the app already knows about a day, for the items that tick themselves. */
data class RoutineEvidence(
    val mealSlots: Set<String> = emptySet(),
    /** steps per clock hour, 0..23 */
    val hourlySteps: Map<Int, Double> = emptyMap(),
    val planSavedForNextDay: Boolean = false
)

object Routine {
    const val DONE = "DONE"
    const val MISSED = "MISSED"

    /** Steps in the two hours after "walk after lunch" that count as having walked. */
    const val WALK_STEPS = 1_000

    /** Alarm behaviour for routine alarms, which have no per-item editor for these. */
    const val RING_SECONDS = 60
    const val SNOOZE_MINUTES = 5

    /**
     * The fixed day, as asked for. Dinner had no time given, so it sits at 9:00 PM, between
     * learning at 8 and reading at 9:45; it is a default like the rest and moves in the plan.
     */
    val SEED: List<RoutineItem> = listOf(
        RoutineItem(key = "wake", title = "Wake up", emoji = "⏰", mode = ReminderMode.ALARM.name, hour = 7, minute = 0, kind = RoutineKind.WAKE.name, sortOrder = 0),
        RoutineItem(key = "workout", title = "Meditation or gym", emoji = "🧘", mode = ReminderMode.ALARM.name, hour = 7, minute = 30, sortOrder = 1),
        RoutineItem(key = "breakfast", title = "Breakfast", emoji = "🌅", hour = 9, minute = 30, kind = RoutineKind.MEAL.name, slot = Slot.Breakfast.name, sortOrder = 2),
        RoutineItem(key = "lunch", title = "Lunch", emoji = "☀", hour = 13, minute = 30, kind = RoutineKind.MEAL.name, slot = Slot.Lunch.name, sortOrder = 3),
        RoutineItem(key = "walk", title = "Walk after lunch", emoji = "🚶", hour = 14, minute = 0, kind = RoutineKind.WALK.name, sortOrder = 4),
        RoutineItem(key = "evening", title = "Evening activity", emoji = "🏃", mode = ReminderMode.ALARM.name, hour = 19, minute = 0, sortOrder = 5),
        RoutineItem(key = "learning", title = "Learning / personal project", emoji = "🎓", hour = 20, minute = 0, sortOrder = 6),
        RoutineItem(key = "dinner", title = "Dinner", emoji = "🌙", hour = 21, minute = 0, kind = RoutineKind.MEAL.name, slot = Slot.Dinner.name, sortOrder = 7),
        RoutineItem(key = "reading", title = "Book reading", emoji = "📖", hour = 21, minute = 45, sortOrder = 8),
        RoutineItem(key = "plan", title = "Plan tomorrow", emoji = "🗓", hour = 22, minute = 0, kind = RoutineKind.PLAN.name, sortOrder = 9)
    )

    /** The item as it stands on [date]: the plan's time and switch if there is one, else the default. */
    fun slotFor(item: RoutineItem, date: String, plan: RoutinePlan?): Triple<Int, Int, Boolean> =
        if (plan != null) Triple(plan.hour, plan.minute, plan.enabled && item.enabled)
        else Triple(item.hour, item.minute, item.enabled)

    /**
     * When [item] next goes off after [now], looking at today and the next two days so a day
     * switched off in the plan rolls on to the one after. Null when nothing is due.
     */
    fun nextFire(
        item: RoutineItem,
        plans: Map<String, RoutinePlan>,
        now: LocalDateTime = LocalDateTime.now()
    ): Pair<String, Long>? {
        if (!item.enabled) return null
        for (offset in 0L..2L) {
            val day = now.toLocalDate().plusDays(offset)
            val iso = Dates.format(day)
            val (h, m, on) = slotFor(item, iso, plans[iso])
            if (!on) continue
            val at = day.atTime(h, m)
            if (at.isAfter(now)) return iso to at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return null
    }

    /**
     * Where one item stands on one day. A tap always wins over evidence (a "missed" you chose
     * is honest), evidence wins over silence, and silence turns into a miss once the day is
     * over - or, for today, once the item's time has passed by [GRACE_MINUTES] with nothing.
     * Today stays PENDING until then, so the morning doesn't open as a wall of red.
     */
    fun resolve(
        item: RoutineItem,
        date: String,
        plan: RoutinePlan?,
        log: RoutineLog?,
        evidence: RoutineEvidence,
        now: LocalDateTime = LocalDateTime.now()
    ): RoutineSlot {
        val (h, m, on) = slotFor(item, date, plan)
        val base = RoutineSlot(item, date, h, m, on, RoutineStatus.PENDING)
        if (!on) return base.copy(status = RoutineStatus.OFF)
        when (log?.status) {
            DONE -> return base.copy(status = RoutineStatus.DONE)
            MISSED -> return base.copy(status = RoutineStatus.MISSED)
        }
        if (evidenced(item, h, evidence)) return base.copy(status = RoutineStatus.DONE, auto = true)

        val day = Dates.parse(date)
        val today = now.toLocalDate()
        val over = when {
            day.isBefore(today) -> true
            day.isAfter(today) -> false
            else -> now.isAfter(day.atTime(h, m).plusMinutes(graceFor(item)))
        }
        // An item that ticks itself can still do so until midnight - lunch logged at 4pm counts.
        val stillPossible = item.kindType != RoutineKind.MANUAL && item.kindType != RoutineKind.WAKE &&
            !day.isBefore(today)
        return base.copy(status = if (over && !stillPossible) RoutineStatus.MISSED else RoutineStatus.PENDING)
    }

    private fun graceFor(item: RoutineItem): Long = when (item.kindType) {
        RoutineKind.WAKE -> 60L
        else -> GRACE_MINUTES
    }

    /** How long after its time an untouched item turns into a miss. */
    const val GRACE_MINUTES = 180L

    private fun evidenced(item: RoutineItem, hour: Int, e: RoutineEvidence): Boolean = when (item.kindType) {
        RoutineKind.MEAL -> item.slot in e.mealSlots
        RoutineKind.WALK -> (e.hourlySteps[hour] ?: 0.0) + (e.hourlySteps[hour + 1] ?: 0.0) >= WALK_STEPS
        RoutineKind.PLAN -> e.planSavedForNextDay
        else -> false
    }

    /** Hit rate over the slots that counted: DONE / (DONE + MISSED). Null with nothing to judge. */
    fun hitRate(slots: List<RoutineSlot>): Float? {
        val done = slots.count { it.status == RoutineStatus.DONE }
        val missed = slots.count { it.status == RoutineStatus.MISSED }
        return if (done + missed == 0) null else done.toFloat() / (done + missed)
    }

    /**
     * Consecutive days, ending today or yesterday, on which [itemId] was done. Today only adds
     * to the streak once it is done; a still-pending today doesn't break it. Days off are skipped.
     */
    fun streak(byDate: Map<String, RoutineStatus>, today: String): Int {
        var count = 0
        var date = today
        var first = true
        while (true) {
            when (byDate[date]) {
                RoutineStatus.DONE -> count++
                RoutineStatus.OFF -> Unit
                RoutineStatus.PENDING -> if (!first) return count
                RoutineStatus.MISSED, null -> return count
            }
            first = false
            date = Dates.shift(date, -1)
            if (count > 400) return count
        }
    }

    fun tomorrow(today: LocalDate = LocalDate.now()): String = Dates.format(today.plusDays(1))

    /** Before this hour the night isn't over yet, so "tomorrow" still means the coming day. */
    const val PLAN_DAY_ROLLOVER_HOUR = 4

    /**
     * The day the plan screen plans. Answering the 10 PM nudge at half past midnight is still
     * planning the day about to start - which by the calendar is already today. Saving a plan
     * for that day credits the night before it, as always (see [RoutineEvidence.planSavedForNextDay]).
     */
    fun planDate(now: LocalDateTime = LocalDateTime.now()): String =
        if (now.hour < PLAN_DAY_ROLLOVER_HOUR) Dates.format(now.toLocalDate())
        else Dates.format(now.toLocalDate().plusDays(1))
}
