package com.lifetrack.app.ui.calories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Retention
import com.lifetrack.app.data.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

/** How far back the detail screen looks. Six months is the whole retention window. */
enum class CalorieWindow(val label: String, val days: Int) {
    Today("Today", 1),
    Week("This week", 7),
    Month("This month", 30),
    Quarter("3 months", 90),
    HalfYear("6 months", Retention.DAYS.toInt());

    /** Short enough to draw as bars; anything longer becomes a line. */
    val barred: Boolean get() = days <= 7
}

/**
 * Everything the trends screen draws for one window.
 *
 * Averages are taken over the days that actually have food logged, not over the whole
 * window — otherwise a fresh install with two logged days would report an average of
 * "600 kcal a day" and look like a triumph.
 */
data class CaloriesDetailState(
    val window: CalorieWindow = CalorieWindow.Week,
    val from: String = Dates.today(),
    val to: String = Dates.today(),
    val perDay: List<DayValue> = emptyList(),
    val perSlot: List<DayValue> = emptyList(),
    val topFoods: List<DayValue> = emptyList(),
    val proteinPerDay: List<DayValue> = emptyList(),
    val fiberPerDay: List<DayValue> = emptyList(),
    val goal: Int = Settings().calorieGoal,
    val proteinGoal: Int = Settings().proteinGoal,
    val fiberGoal: Int = Settings().fiberGoal,
    val previousAverage: Int = 0,
    val loaded: Boolean = false
) {
    private val logged: List<DayValue> get() = perDay.filter { it.value > 0.0 }

    val daysLogged: Int get() = logged.size
    val average: Int get() = averageOf(perDay)
    val total: Int get() = logged.sumOf { it.value }.roundToInt()

    val daysOver: Int get() = logged.count { it.value > goal }
    val daysUnder: Int get() = logged.count { it.value <= goal }

    /** Under the goal is the win, so the lightest logged day is the best one. */
    val bestDay: DayValue? get() = logged.minByOrNull { it.value }
    val worstDay: DayValue? get() = logged.maxByOrNull { it.value }

    val hasComparison: Boolean get() = previousAverage > 0

    // ---- protein: averaged over the same logged days, and counted the other way round ----

    val proteinAverage: Int get() = averageOf(proteinPerDay)

    /** Days that reached the protein goal. Unlike calories, more is the win. */
    val proteinDaysMet: Int
        get() = if (proteinGoal <= 0) 0
        else proteinPerDay.count { it.value >= proteinGoal }

    val hasProtein: Boolean get() = proteinPerDay.any { it.value > 0.0 }

    val fiberAverage: Int get() = averageOf(fiberPerDay)
    val fiberDaysMet: Int
        get() = if (fiberGoal <= 0) 0 else fiberPerDay.count { it.value >= fiberGoal }
    val hasFiber: Boolean get() = fiberPerDay.any { it.value > 0.0 }

    /**
     * Percent change in daily average against the window immediately before this one.
     * The sign is the plain arithmetic one — positive means you ate *more* — and the chip
     * is drawn with `higherIsBetter = false` so eating more comes out red, less comes out
     * green. Flipping the sign here instead would make "+8%" mean "ate less", which nobody
     * reads correctly.
     */
    val delta: Double
        get() = if (previousAverage <= 0) 0.0
        else (average - previousAverage) * 100.0 / previousAverage

    companion object {
        fun averageOf(days: List<DayValue>): Int {
            val logged = days.filter { it.value > 0.0 }
            return if (logged.isEmpty()) 0 else (logged.sumOf { it.value } / logged.size).roundToInt()
        }
    }
}

/** Weekly / monthly / six-month calorie trends. */
class CaloriesDetailViewModel(private val repo: Repository) : ViewModel() {

    private val window = MutableStateFlow(CalorieWindow.Week)

    /** The six series for a window, carried together so they can never be mismatched. */
    private data class Slice(
        val window: CalorieWindow,
        val from: String,
        val to: String,
        val days: List<DayValue>,
        val previousDays: List<DayValue>,
        val slots: List<DayValue>,
        val foods: List<DayValue>,
        val protein: List<DayValue>,
        val fiber: List<DayValue>
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val slice: Flow<Slice> = window.flatMapLatest { w ->
        val to = Dates.today()
        val from = Dates.shift(to, -(w.days - 1).toLong())
        val previousTo = Dates.shift(from, -1)
        val previousFrom = Dates.shift(previousTo, -(w.days - 1).toLong())
        // combine() has no six-flow overload, so the two macro series travel together.
        val macros: Flow<Pair<List<DayValue>, List<DayValue>>> =
            combine(repo.proteinPerDay(from, to), repo.fiberPerDay(from, to)) { p, f -> p to f }
        combine(
            repo.kcalPerDay(from, to),
            repo.kcalPerDay(previousFrom, previousTo),
            repo.kcalPerSlot(from, to),
            repo.topFoods(from, to),
            macros
        ) { days, previous, slots, foods, (protein, fiber) ->
            Slice(w, from, to, days, previous, slots, foods, protein, fiber)
        }
    }

    val state: StateFlow<CaloriesDetailState> =
        combine(slice, repo.settings) { s, settings ->
            CaloriesDetailState(
                window = s.window,
                from = s.from,
                to = s.to,
                perDay = s.days,
                perSlot = s.slots,
                topFoods = s.foods,
                proteinPerDay = s.protein,
                fiberPerDay = s.fiber,
                goal = settings.calorieGoal,
                proteinGoal = settings.proteinGoal,
                fiberGoal = settings.fiberGoal,
                previousAverage = CaloriesDetailState.averageOf(s.previousDays),
                loaded = true
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaloriesDetailState())

    fun setWindow(w: CalorieWindow) { window.value = w }
}
