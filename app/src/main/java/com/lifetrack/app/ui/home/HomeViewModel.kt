package com.lifetrack.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.HabitKind
import com.lifetrack.app.data.Metric
import com.lifetrack.app.data.Repository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

// ---------------------------------------------------------------- helpers

/** A sparse `date -> value` series, read at one date. Missing days are simply zero. */
private fun List<DayValue>.on(date: String): Double = firstOrNull { it.date == date }?.value ?: 0.0

/**
 * Percent change against yesterday, or null when the comparison would be noise:
 * a day with nothing on either side has no story to tell, and "-100%" at 7am is a lie.
 */
private fun percentDelta(today: Double, yesterday: Double): Double? =
    if (today <= 0.0 || yesterday <= 0.0) null else (today - yesterday) / yesterday * 100.0

// ---------------------------------------------------------------- day score

/**
 * One line of the day score ring. [budget] marks the goals you want to stay *under*
 * (calories, screen time) - those go amber near the line and red past it, while the
 * "more is better" goals just turn green once they are met.
 */
data class DayGoal(
    val label: String,
    val emoji: String,
    val accentKey: String,
    val progress: Float,
    val met: Boolean,
    val budget: Boolean = false,
    val over: Boolean = false
)

// ---------------------------------------------------------------- per-section summaries

data class CaloriesCard(
    val kcal: Int = 0,
    val goal: Int = 0,
    val yesterday: Int = 0,
    val protein: Int = 0,
    val proteinGoal: Int = 0,
    val fiber: Int = 0,
    val fiberGoal: Int = 0,
    val series: List<DayValue> = emptyList()
) {
    val logged: Boolean get() = kcal > 0
    val over: Boolean get() = goal > 0 && kcal > goal
    val progress: Float get() = if (goal > 0) kcal / goal.toFloat() else 0f

    /** What you have eaten. The card is a readout of intake, not of headroom. */
    val headline: String get() = "%,d".format(kcal)

    val headlineCaption: String get() = "kcal eaten"

    /** The goal and the headroom, both demoted to the small line under the headline. */
    val detail: String get() = when {
        goal <= 0 -> "no goal set"
        over -> "of %,d  ·  %,d over".format(goal, kcal - goal)
        else -> "of %,d  ·  %,d left".format(goal, goal - kcal)
    }

    val proteinMet: Boolean get() = proteinGoal > 0 && protein >= proteinGoal
    val proteinProgress: Float get() = if (proteinGoal > 0) protein / proteinGoal.toFloat() else 0f

    val fiberMet: Boolean get() = fiberGoal > 0 && fiber >= fiberGoal
    val fiberProgress: Float get() = if (fiberGoal > 0) fiber / fiberGoal.toFloat() else 0f

    /** "48 / 60 g protein · 22 / 30 g fibre" — the two targets that are not a budget. */
    val macroDetail: String get() =
        "%,d/%,d g protein  ·  %,d/%,d g fibre".format(protein, proteinGoal, fiber, fiberGoal)

    val delta: Double? get() = percentDelta(kcal.toDouble(), yesterday.toDouble())

    /** Eating nothing is not a win, so an empty day never counts toward the score. */
    val met: Boolean get() = logged && !over
}

data class ActivityCard(
    val steps: Int = 0,
    val goal: Int = 0,
    val yesterday: Int = 0,
    val series: List<DayValue> = emptyList(),
    val distanceKm: Float = 0f,
    val burnKcal: Int = 0,
    val burnGoal: Int = 0,
    val activeMin: Int = 0
) {
    val hasData: Boolean get() = steps > 0 || burnKcal > 0 || activeMin > 0
    val progress: Float get() = if (goal > 0) steps / goal.toFloat() else 0f
    val met: Boolean get() = goal > 0 && steps >= goal
    val delta: Double? get() = percentDelta(steps.toDouble(), yesterday.toDouble())

    val headline: String get() = "%,d".format(steps)

    val detail: String get() {
        val lead = when {
            goal <= 0 -> "%,d steps".format(steps)
            met -> "Goal reached"
            else -> "%,d to go".format(goal - steps)
        }
        val extras = buildList {
            if (distanceKm >= 0.1f) add("%.1f km".format(distanceKm))
            if (burnKcal > 0) add("$burnKcal kcal burned")
            if (distanceKm < 0.1f && burnKcal == 0 && activeMin > 0) add(Dates.formatMinutes(activeMin) + " active")
        }
        return (listOf(lead) + extras).joinToString("  ·  ")
    }
}

data class HabitsCard(
    val total: Int = 0,
    val done: Int = 0,
    val trackedMillis: Long = 0,
    val yesterdayMillis: Long = 0
) {
    val progress: Float get() = if (total > 0) done / total.toFloat() else 0f
    val met: Boolean get() = total > 0 && done == total
    val hasAny: Boolean get() = total > 0
    val delta: Double? get() = percentDelta(trackedMillis.toDouble(), yesterdayMillis.toDouble())

    val detail: String get() =
        if (trackedMillis > 0) "${Dates.formatDuration(trackedMillis)} tracked today"
        else "Nothing tracked yet today"
}

/** The habit stopwatch that survived the app being closed, if one is running. */
data class RunningTimer(
    val habitId: Long,
    val name: String,
    val emoji: String,
    val accentKey: String,
    val since: Long
)

data class ScreenCard(
    val minutes: Int = 0,
    val yesterdayMinutes: Int = 0,
    val limitMinutes: Int = 0,
    val overApps: List<String> = emptyList(),
    val topApp: String? = null,
    val topAppMinutes: Int = 0,
    val trackedCount: Int = 0,
    val series: List<DayValue> = emptyList()
) {
    val hasData: Boolean get() = minutes > 0
    val over: Boolean get() = overApps.isNotEmpty()
    val progress: Float get() = if (limitMinutes > 0) minutes / limitMinutes.toFloat() else 0f
    val met: Boolean get() = hasData && !over
    val delta: Double? get() = percentDelta(minutes.toDouble(), yesterdayMinutes.toDouble())

    val headline: String get() = Dates.formatMinutes(minutes)

    val detail: String get() = when {
        overApps.size > 1 -> "${overApps.first()} +${overApps.size - 1} more over limit"
        overApps.size == 1 -> "${overApps.first()} over its limit"
        topApp != null -> "$topApp  ·  ${Dates.formatMinutes(topAppMinutes)}"
        else -> "$trackedCount apps tracked"
    }
}

data class AlarmsCard(
    val nextLabel: String? = null,
    val nextClock: String? = null,
    val nextAt: Long? = null,
    val enabledCount: Int = 0,
    val total: Int = 0
) {
    val hasNext: Boolean get() = nextAt != null

    val detail: String get() = when {
        nextAt != null -> "${nextLabel.orEmpty()}  ·  ${Dates.whenLabel(nextAt)}"
        total > 0 -> "$total set, none scheduled"
        else -> "No alarms set"
    }
}

// ---------------------------------------------------------------- screen state

data class HomeState(
    val today: String = Dates.today(),
    val from: String = Dates.shift(Dates.today(), -6),
    val calories: CaloriesCard = CaloriesCard(),
    val activity: ActivityCard = ActivityCard(),
    val habits: HabitsCard = HabitsCard(),
    val running: RunningTimer? = null,
    val screen: ScreenCard = ScreenCard(),
    val alarms: AlarmsCard = AlarmsCard()
) {
    /**
     * The goals behind "3 of 5". Burn only joins the list once the phone is actually
     * reporting it, so the denominator never counts something the user cannot influence.
     */
    val goals: List<DayGoal> get() = buildList {
        add(
            DayGoal(
                "Fuel", "🍽", "calories", calories.progress, calories.met,
                budget = true, over = calories.over
            )
        )
        add(DayGoal("Steps", "👣", "activity", activity.progress, activity.met))
        if (activity.burnGoal > 0 && activity.burnKcal > 0) {
            add(
                DayGoal(
                    "Burn", "🔥", "activity",
                    activity.burnKcal / activity.burnGoal.toFloat(),
                    activity.burnKcal >= activity.burnGoal
                )
            )
        }
        add(DayGoal("Habits", "🌱", "habits", habits.progress, habits.met))
        add(
            DayGoal(
                "Focus", "📱", "screen", screen.progress, screen.met,
                budget = true, over = screen.over
            )
        )
    }

    val metGoals: Int get() = goals.count { it.met }
    val score: Float get() = if (goals.isEmpty()) 0f else metGoals / goals.size.toFloat()

    /** The one encouraging line under the greeting. */
    val scoreLine: String get() = when {
        metGoals == goals.size -> "Every goal met. Nice day."
        metGoals == 0 -> "Nothing hit yet - pick one and start."
        goals.size - metGoals == 1 -> "One goal to go."
        else -> "${goals.size - metGoals} goals to go."
    }
}

/**
 * Home reads the repository directly rather than borrowing the feature ViewModels, so the
 * summary stays cheap and cannot drag a whole tab's state into the first screen you see.
 */
class HomeViewModel(repo: Repository) : ViewModel() {

    // Snapshotted once: the flows below are built at construction, and a home screen left
    // open across midnight is refreshed by the process restart that follows soon after.
    private val today = Dates.today()
    private val yesterday = Dates.shift(today, -1)
    private val from = Dates.shift(today, -6)

    private data class HabitsState(val card: HabitsCard, val running: RunningTimer?)

    private val calories: Flow<CaloriesCard> =
        combine(
            repo.settings,
            repo.kcalPerDay(from, today),
            repo.proteinPerDay(from, today),
            repo.fiberPerDay(from, today)
        ) { settings, series, proteinSeries, fiberSeries ->
            CaloriesCard(
                kcal = series.on(today).roundToInt(),
                goal = settings.calorieGoal,
                yesterday = series.on(yesterday).roundToInt(),
                protein = proteinSeries.on(today).roundToInt(),
                proteinGoal = settings.proteinGoal,
                fiber = fiberSeries.on(today).roundToInt(),
                fiberGoal = settings.fiberGoal,
                series = series
            )
        }

    private val activity: Flow<ActivityCard> = combine(
        repo.settings,
        repo.metricSeries(Metric.Steps, from, today),
        repo.metricsOn(today)
    ) { settings, steps, metrics ->
        fun metric(m: Metric) = metrics.firstOrNull { it.metric == m.key }?.value ?: 0.0
        ActivityCard(
            steps = steps.on(today).roundToInt(),
            goal = settings.stepGoal,
            yesterday = steps.on(yesterday).roundToInt(),
            series = steps,
            distanceKm = (metric(Metric.Distance) / 1000.0).toFloat(),
            burnKcal = metric(Metric.Burn).roundToInt(),
            burnGoal = settings.burnGoalKcal,
            activeMin = metric(Metric.ActiveMinutes).roundToInt()
        )
    }

    private val habits: Flow<HabitsState> = combine(
        repo.habits,
        repo.habitTotalsOn(today),
        repo.checksOn(today),
        repo.allHabitMillisPerDay(from, today),
        repo.settings
    ) { list, totals, checks, series, settings ->
        val done = list.count { h ->
            when (h.kindType) {
                HabitKind.TIMED -> h.dailyGoalMillis > 0 &&
                    (totals.firstOrNull { it.habitId == h.id }?.millis ?: 0L) >= h.dailyGoalMillis
                HabitKind.COUNT -> h.dailyTarget > 0 &&
                    (checks.firstOrNull { it.habitId == h.id }?.count ?: 0) >= h.dailyTarget
            }
        }
        val tracked = totals.filter { t -> list.any { it.id == t.habitId } }.sumOf { it.millis }
        val runningId = settings.runningHabitId
        val runningSince = settings.runningSince
        val running = if (runningId != null && runningSince != null) {
            val habit = list.firstOrNull { it.id == runningId }
            RunningTimer(
                habitId = runningId,
                name = habit?.name ?: "Timer",
                emoji = habit?.emoji ?: "⏱",
                accentKey = habit?.accent ?: "habits",
                since = runningSince
            )
        } else null
        HabitsState(
            card = HabitsCard(
                total = list.size,
                done = done,
                trackedMillis = tracked,
                yesterdayMillis = series.on(yesterday).toLong()
            ),
            running = running
        )
    }

    private val screen: Flow<ScreenCard> = combine(
        repo.trackedApps,
        repo.usageTotalPerApp(today, today),
        repo.usageTotalPerDay(from, today)
    ) { apps, perApp, perDay ->
        // usageTotalPerApp puts the package name in DayValue.date.
        val minutesByPackage = perApp.associate { it.date to it.value.roundToInt() }
        val labels = apps.associate { it.packageName to it.label }
        val top = perApp.maxByOrNull { it.value }
        ScreenCard(
            minutes = perApp.sumOf { it.value }.roundToInt(),
            yesterdayMinutes = perDay.on(yesterday).roundToInt(),
            limitMinutes = apps.sumOf { it.dailyLimitMin },
            overApps = apps.filter {
                it.dailyLimitMin > 0 && (minutesByPackage[it.packageName] ?: 0) > it.dailyLimitMin
            }.map { it.label },
            topApp = top?.let { labels[it.date] ?: it.date.substringAfterLast('.') },
            topAppMinutes = top?.value?.roundToInt() ?: 0,
            trackedCount = apps.size,
            series = perDay
        )
    }

    private val alarms: Flow<AlarmsCard> = repo.reminders.map { list ->
        val next = list.filter { it.enabled }
            .mapNotNull { r -> Dates.nextTrigger(r.hour, r.minute, r.daysMask)?.let { r to it } }
            .minByOrNull { it.second }
        AlarmsCard(
            nextLabel = next?.first?.label,
            nextClock = next?.first?.let { Dates.clockLabel(it.hour, it.minute) },
            nextAt = next?.second,
            enabledCount = list.count { it.enabled },
            total = list.size
        )
    }

    val state: StateFlow<HomeState> =
        combine(calories, activity, habits, screen, alarms) { cal, act, hab, scr, alm ->
            HomeState(
                today = today,
                from = from,
                calories = cal,
                activity = act,
                habits = hab.card,
                running = hab.running,
                screen = scr,
                alarms = alm
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}
