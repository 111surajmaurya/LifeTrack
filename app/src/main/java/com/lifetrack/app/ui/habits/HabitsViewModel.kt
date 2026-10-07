package com.lifetrack.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Habit
import com.lifetrack.app.data.HabitKind
import com.lifetrack.app.data.Repository
import com.lifetrack.app.ui.components.CalendarDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Days of history the list screen keeps around: enough for the week dots and a believable streak. */
private const val HISTORY_DAYS = 30

/** How long a confirmation stays on screen before it fades itself out. */
private const val NOTICE_MS = 3_000L

/** The stopwatch needs ~20fps to make the hundredths readable rather than a blur. */
private const val TICK_RUNNING_MS = 50L

/**
 * A habit's day, ready to draw: today's figure, the last seven days as dots, and the
 * goal arithmetic done once here instead of in three places in the UI.
 */
data class HabitRow(
    val habit: Habit,
    val millisToday: Long,
    val countToday: Int,
    val week: List<CalendarDay>
) {
    val timed: Boolean get() = habit.kindType == HabitKind.TIMED

    /** Today's figure in the habit's own unit: millis for timed, ticks for counted. */
    val value: Double get() = if (timed) millisToday.toDouble() else countToday.toDouble()

    val goal: Double
        get() = if (timed) habit.dailyGoalMillis.toDouble() else habit.dailyTarget.toDouble()

    val progress: Float get() = if (goal > 0) (value / goal).toFloat() else 0f
    val done: Boolean get() = goal > 0 && value >= goal
}

data class HabitsUiState(
    val rows: List<HabitRow> = emptyList(),
    val archived: List<Habit> = emptyList(),
    val runningHabitId: Long? = null,
    val runningSince: Long? = null,
    val trackedTodayMillis: Long = 0L,
    val doneToday: Int = 0,
    val streakDays: Int = 0,
    /** False until the first database emission, so a fresh install doesn't flash an empty state. */
    val ready: Boolean = false
)

/** Raw per-habit history keyed by ISO date, in the habit's own unit. */
private data class HabitHistory(val habit: Habit, val byDate: Map<String, Double>) {
    fun metOn(date: String): Boolean {
        val goal = if (habit.kindType == HabitKind.TIMED) habit.dailyGoalMillis.toDouble()
        else habit.dailyTarget.toDouble()
        return goal > 0 && (byDate[date] ?: 0.0) >= goal
    }

    fun progressOn(date: String): Float {
        val goal = if (habit.kindType == HabitKind.TIMED) habit.dailyGoalMillis.toDouble()
        else habit.dailyTarget.toDouble()
        return if (goal > 0) ((byDate[date] ?: 0.0) / goal).toFloat() else 0f
    }
}

class HabitsViewModel(private val repo: Repository) : ViewModel() {

    private val _notice = MutableStateFlow<String?>(null)

    /** Short-lived confirmation line ("Saved 4m 12s"), cleared by [noticeJob]. */
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private var noticeJob: Job? = null

    /**
     * Wall clock for the live stopwatch.
     *
     * While a timer is running we emit every [TICK_RUNNING_MS] so the hundredths animate.
     * When nothing is running there is nothing to animate, so the flow emits once and then
     * suspends forever - no wake-ups, no battery burnt on a screen that isn't counting.
     * `WhileSubscribed` stops it entirely once the screen goes away.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val now: StateFlow<Long> = repo.settings
        .map { it.runningSince }
        .distinctUntilChanged()
        .flatMapLatest { since ->
            flow {
                emit(System.currentTimeMillis())
                while (since != null) {
                    delay(TICK_RUNNING_MS)
                    emit(System.currentTimeMillis())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    /**
     * One history query per active habit rather than one per day: five habits means five
     * queries instead of thirty, and it gives the per-habit resolution the dots need.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val histories: Flow<List<HabitHistory>> = repo.allHabits.flatMapLatest { all ->
        val active = all.filter { !it.archived }
        if (active.isEmpty()) {
            flowOf(emptyList())
        } else {
            val to = Dates.today()
            val from = Dates.shift(to, -(HISTORY_DAYS - 1).toLong())
            combine(active.map { historyFor(it, from, to) }) { it.toList() }
        }
    }

    private fun historyFor(habit: Habit, from: String, to: String): Flow<HabitHistory> =
        when (habit.kindType) {
            HabitKind.TIMED -> repo.habitMillisPerDay(habit.id, from, to)
                .map { days -> HabitHistory(habit, days.associate { it.date to it.value }) }

            HabitKind.COUNT -> repo.checksBetween(habit.id, from, to)
                .map { checks -> HabitHistory(habit, checks.associate { it.date to it.count.toDouble() }) }
        }

    val state: StateFlow<HabitsUiState> =
        combine(histories, repo.allHabits, repo.settings) { history, all, settings ->
            val today = Dates.today()
            val week = Dates.lastDays(7)
            // Cards are built from `history`, not `all`, so a habit added a frame ago never
            // renders before its history query has answered.
            val rows = history.map { h ->
                HabitRow(
                    habit = h.habit,
                    millisToday = if (h.habit.kindType == HabitKind.TIMED)
                        (h.byDate[today] ?: 0.0).toLong() else 0L,
                    countToday = if (h.habit.kindType == HabitKind.COUNT)
                        (h.byDate[today] ?: 0.0).toInt() else 0,
                    week = week.map { d -> CalendarDay(d, h.progressOn(d), h.metOn(d)) }
                )
            }
            HabitsUiState(
                rows = rows,
                archived = all.filter { it.archived },
                runningHabitId = settings.runningHabitId,
                runningSince = settings.runningSince,
                trackedTodayMillis = rows.sumOf { it.millisToday },
                doneToday = rows.count { it.done },
                streakDays = streakOf(history),
                ready = true
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    /**
     * Days in a row you kept something up - at least one habit's goal met. Today only breaks
     * the streak once it is over, so a morning with nothing logged yet doesn't read as a miss.
     */
    private fun streakOf(history: List<HabitHistory>): Int {
        if (history.isEmpty()) return 0
        fun anyDone(date: String) = history.any { it.metOn(date) }
        var day = Dates.today()
        if (!anyDone(day)) day = Dates.shift(day, -1)
        var count = 0
        while (count < HISTORY_DAYS && anyDone(day)) {
            count++
            day = Dates.shift(day, -1)
        }
        return count
    }

    // ---------------------------------------------------------------- timer

    fun startTimer(habitId: Long) = viewModelScope.launch {
        val current = state.value
        val previous = current.rows.firstOrNull { it.habit.id == current.runningHabitId }
        val since = current.runningSince
        repo.startTimer(habitId)     // banks whatever was running first
        if (previous != null && since != null) {
            val banked = (System.currentTimeMillis() - since).coerceAtLeast(0)
            say("Banked ${Dates.formatDuration(banked)} on ${previous.habit.name}")
        }
    }

    fun stopTimer() = viewModelScope.launch {
        val saved = repo.stopTimer()
        say("Saved ${Dates.formatDuration(saved)}")
    }

    /** Restart the stopwatch at zero without leaving the habit. */
    fun resetTimer() = viewModelScope.launch {
        val dropped = repo.resetTimer()
        say("Back to zero  ·  dropped ${Dates.formatDuration(dropped)}")
    }

    fun discardTimer() = viewModelScope.launch {
        val since = state.value.runningSince
        repo.discardTimer()
        val dropped = if (since != null) (System.currentTimeMillis() - since).coerceAtLeast(0) else 0L
        say("Discarded ${Dates.formatDuration(dropped)}")
    }

    // ---------------------------------------------------------------- habits

    fun bumpCheck(habitId: Long, delta: Int) = viewModelScope.launch {
        repo.bumpCheck(habitId, Dates.today(), delta)
    }

    /** Time done away from the stopwatch - "read for 20 minutes" - banked on [date]. */
    fun addMinutes(habitId: Long, minutes: Int, date: String = Dates.today()) = viewModelScope.launch {
        if (minutes <= 0) return@launch
        repo.addMillis(habitId, date, minutes * 60_000L)
        say("Added ${Dates.formatMinutes(minutes)}" + if (date != Dates.today()) " to ${Dates.label(date).lowercase()}" else "")
    }

    fun resetToday(habit: Habit) = viewModelScope.launch {
        repo.resetToday(habit.id)
        say("${habit.name} cleared for today")
    }

    /** Seeded habits archive and keep their history; habits the user added are deleted. */
    fun remove(habit: Habit) = viewModelScope.launch {
        repo.removeHabit(habit)
        say(if (habit.seeded) "${habit.name} archived" else "${habit.name} removed")
    }

    fun restore(habit: Habit) = viewModelScope.launch {
        repo.restoreHabit(habit)
        say("${habit.name} is back")
    }

    fun save(existing: Habit?, name: String, kind: HabitKind, goal: Int, emoji: String, accent: String) =
        viewModelScope.launch {
            val clean = name.trim().ifBlank { return@launch }
            val minutes = if (kind == HabitKind.TIMED) goal.coerceIn(1, 24 * 60) else 30
            val target = if (kind == HabitKind.COUNT) goal.coerceIn(1, 99) else 1
            if (existing == null) {
                repo.addHabit(
                    Habit(
                        name = clean, kind = kind.name, dailyGoalMin = minutes, dailyTarget = target,
                        emoji = emoji, accent = accent,
                        sortOrder = (state.value.rows.maxOfOrNull { it.habit.sortOrder } ?: -1) + 1
                    )
                )
                say("$clean added")
            } else {
                // A counted habit has no stop button, so a timer left running on it could never
                // be stopped. Bank it while the habit is still timed.
                if (kind != HabitKind.TIMED && state.value.runningHabitId == existing.id) {
                    repo.stopTimer()
                }
                repo.updateHabit(
                    existing.copy(
                        name = clean, kind = kind.name, dailyGoalMin = minutes,
                        dailyTarget = target, emoji = emoji, accent = accent
                    )
                )
                say("$clean updated")
            }
        }

    fun dismissNotice() {
        noticeJob?.cancel()
        _notice.value = null
    }

    private fun say(message: String) {
        noticeJob?.cancel()
        _notice.value = message
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MS)
            _notice.value = null
        }
    }
}

// ---------------------------------------------------------------- shared labels

/** "45m a day" / "8× a day" - one phrasing for both screens. */
internal fun goalLabel(habit: Habit): String = when (habit.kindType) {
    HabitKind.TIMED -> "${Dates.formatMinutes(habit.dailyGoalMin)} a day"
    HabitKind.COUNT -> "${habit.dailyTarget}× a day"
}

/**
 * How much of a running timer belongs to today's figures. A stopped session is banked to the
 * day it started, so a timer begun before midnight adds nothing to today - the stopwatch
 * still shows the whole run, but today's total must match what stopping will actually save.
 */
internal fun runningToday(since: Long, now: Long): Long =
    if (Dates.ofMillis(since) == Dates.ofMillis(now)) (now - since).coerceAtLeast(0L) else 0L

/** Formats a raw figure in the habit's own unit: "12m 30s" for timed, "5×" for counted. */
internal fun amountLabel(habit: Habit, value: Double): String = when (habit.kindType) {
    HabitKind.TIMED -> Dates.formatDuration(value.toLong())
    HabitKind.COUNT -> "${value.toInt()}×"
}

/** The 24 emoji the picker offers - broad enough that most habits find themselves one. */
internal val HabitEmoji: List<String> = listOf(
    "⏱", "🏋", "🏃", "🚴", "🧘", "🚶", "📖", "🎓",
    "✍", "💻", "🎧", "🎹", "🎨", "💧", "🥗", "💊",
    "😴", "🧹", "🌱", "🙏", "🧠", "📵", "☀", "🛠"
)

/** Accent keys `Accents.byKey` understands, with a label for the picker. */
internal val HabitAccents: List<Pair<String, String>> = listOf(
    "leaf" to "Leaf", "clay" to "Clay", "sky" to "Sky", "plum" to "Plum", "rose" to "Rose"
)
