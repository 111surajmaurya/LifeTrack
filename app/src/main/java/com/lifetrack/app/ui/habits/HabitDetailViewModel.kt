package com.lifetrack.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Habit
import com.lifetrack.app.data.HabitCheck
import com.lifetrack.app.data.HabitKind
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Retention
import com.lifetrack.app.data.Session
import com.lifetrack.app.ui.components.CalendarDay
import com.lifetrack.app.ui.components.ChartPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How far back the streak arithmetic can see - the same window the database keeps. */
private val WINDOW_DAYS = Retention.DAYS

data class HabitDetailUiState(
    val habit: Habit? = null,
    /** First day of the month the calendar is showing. */
    val anchor: String = Dates.monthStart(Dates.today()),
    val selected: String? = null,
    /** Day -> figure in the habit's own unit (millis for timed, ticks for counted). */
    val byDate: Map<String, Double> = emptyMap(),
    val calendar: Map<String, CalendarDay> = emptyMap(),
    val weekPoints: List<ChartPoint> = emptyList(),
    val monthPoints: List<ChartPoint> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val goal: Double = 0.0,
    val todayValue: Double = 0.0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val monthTotal: Double = 0.0,
    val dailyAverage: Double = 0.0,
    val ready: Boolean = false
) {
    val selectedValue: Double get() = selected?.let { byDate[it] } ?: 0.0

    /** True once the month on screen is the current one, so the forward arrow can stop. */
    val atCurrentMonth: Boolean get() = anchor == Dates.monthStart(Dates.today())
}

/** What the four queries hand back before any of it is turned into chart shapes. */
private data class DetailData(
    val habitId: Long = 0L,
    val anchor: String = Dates.monthStart(Dates.today()),
    val habit: Habit? = null,
    val millis: List<DayValue> = emptyList(),
    val checks: List<HabitCheck> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val ready: Boolean = false
)

class HabitDetailViewModel(private val repo: Repository) : ViewModel() {

    private val habitId = MutableStateFlow(0L)
    private val anchor = MutableStateFlow(Dates.monthStart(Dates.today()))
    private val selected = MutableStateFlow<String?>(null)

    fun load(habitId: Long) {
        if (this.habitId.value != habitId) {
            this.habitId.value = habitId
            selected.value = null
        }
    }

    /** Month navigation never runs past the current month - there is nothing there to see. */
    fun shiftMonth(months: Long) {
        val next = Dates.shiftMonth(anchor.value, months)
        if (months > 0 && next > Dates.monthStart(Dates.today())) return
        anchor.value = next
        selected.value = null
    }

    fun selectDay(date: String) {
        selected.value = if (selected.value == date) null else date
    }

    fun deleteSession(session: Session) = viewModelScope.launch { repo.deleteSession(session) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val data: Flow<DetailData> =
        combine(habitId, anchor) { id, month -> id to month }
            .distinctUntilChanged()
            .flatMapLatest { (id, month) ->
                if (id <= 0L) {
                    flowOf(DetailData())
                } else {
                    // One window wide enough for both jobs: the month on screen and the
                    // whole retention period the streaks are counted over. ISO dates sort
                    // lexicographically, so min/max on the strings is the real min/max.
                    val today = Dates.today()
                    val from = minOf(Dates.monthStart(month), Dates.shift(today, -(WINDOW_DAYS - 1)))
                    val to = maxOf(Dates.monthEnd(month), today)
                    combine(
                        repo.habit(id),
                        repo.habitMillisPerDay(id, from, to),
                        repo.checksBetween(id, from, to),
                        repo.recentSessions(id, 20)
                    ) { habit, millis, checks, sessions ->
                        DetailData(id, month, habit, millis, checks, sessions, ready = true)
                    }
                }
            }

    val state: StateFlow<HabitDetailUiState> = combine(data, selected) { d, pick ->
        val habit = d.habit ?: return@combine HabitDetailUiState(anchor = d.anchor, ready = d.ready)
        val timed = habit.kindType == HabitKind.TIMED
        val byDate: Map<String, Double> =
            if (timed) d.millis.associate { it.date to it.value }
            else d.checks.associate { it.date to it.count.toDouble() }
        val goal = if (timed) habit.dailyGoalMillis.toDouble() else habit.dailyTarget.toDouble()
        val today = Dates.today()

        val monthDays = Dates.range(Dates.monthStart(d.anchor), Dates.monthEnd(d.anchor))
        val calendar = monthDays.associateWith { date ->
            val value = byDate[date] ?: 0.0
            CalendarDay(
                date = date,
                intensity = if (goal > 0) (value / goal).toFloat() else 0f,
                done = goal > 0 && value >= goal,
                future = Dates.isFuture(date)
            )
        }

        // Charted days stop at today: a month of empty bars ahead of you reads as a collapse.
        val chartedMonth = monthDays.filterNot { Dates.isFuture(it) }
        val monthTotal = chartedMonth.sumOf { byDate[it] ?: 0.0 }
        val elapsed = chartedMonth.size.coerceAtLeast(1)

        HabitDetailUiState(
            habit = habit,
            anchor = d.anchor,
            selected = pick,
            byDate = byDate,
            calendar = calendar,
            weekPoints = Dates.lastDays(7).map { date ->
                ChartPoint(date, Dates.weekdayInitial(date), byDate[date] ?: 0.0, Dates.isToday(date))
            },
            monthPoints = chartedMonth.map { date ->
                ChartPoint(date, Dates.dayOfMonth(date).toString(), byDate[date] ?: 0.0, Dates.isToday(date))
            },
            sessions = d.sessions,
            goal = goal,
            todayValue = byDate[today] ?: 0.0,
            streak = currentStreak(byDate, goal),
            bestStreak = bestStreak(byDate, goal),
            monthTotal = monthTotal,
            dailyAverage = monthTotal / elapsed,
            ready = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailUiState())

    /**
     * Days in a row ending today. Today is only counted against you once it is over, so an
     * unlogged morning doesn't wipe out a month of work at 9am.
     */
    private fun currentStreak(byDate: Map<String, Double>, goal: Double): Int {
        if (goal <= 0) return 0
        fun met(date: String) = (byDate[date] ?: 0.0) >= goal
        var day = Dates.today()
        if (!met(day)) day = Dates.shift(day, -1)
        var count = 0
        while (count < WINDOW_DAYS && met(day)) {
            count++
            day = Dates.shift(day, -1)
        }
        return count
    }

    /** Longest run of goal-met days anywhere in the window we hold. */
    private fun bestStreak(byDate: Map<String, Double>, goal: Double): Int {
        if (goal <= 0 || byDate.isEmpty()) return 0
        val met = byDate.filterValues { it >= goal }.keys.sorted()
        var best = 0
        var run = 0
        var previous: String? = null
        met.forEach { date ->
            run = if (previous != null && Dates.shift(previous!!, 1) == date) run + 1 else 1
            if (run > best) best = run
            previous = date
        }
        return best
    }
}
