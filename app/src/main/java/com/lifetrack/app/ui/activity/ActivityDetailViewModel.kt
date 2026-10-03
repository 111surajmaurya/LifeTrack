package com.lifetrack.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Metric
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Retention
import com.lifetrack.app.data.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** How far back a detail screen looks. Six months is the retention limit, so it's the last stop. */
enum class DetailWindow(val label: String, val days: Int) {
    Today("Today", 1),
    Week("This week", 7),
    Month("This month", 30),
    Quarter("3 months", 90),
    HalfYear("6 months", Retention.DAYS.toInt());

    val barred: Boolean get() = days <= 7
}

/** Everything worth saying about a window of one metric. */
data class DetailStats(
    val average: Double = 0.0,
    val total: Double = 0.0,
    val high: DayValue? = null,
    val low: DayValue? = null,
    val daysWithData: Int = 0,
    val daysAtGoal: Int = 0
) {
    /** The day worth calling out: the highest, or the lowest when down is the good direction. */
    fun best(metric: Metric): DayValue? = if (metric.higherIsBetter) high else low
}

data class ActivityDetailUi(
    val metric: Metric = Metric.Steps,
    val window: DetailWindow = DetailWindow.Week,
    val from: String = Dates.shift(Dates.today(), -6),
    val to: String = Dates.today(),
    val settings: Settings = Settings(),
    /** Oldest first, only the days that actually have a reading. */
    val series: List<DayValue> = emptyList(),
    val today: Double? = null,
    /** Percent change against the window before this one, on a like-for-like daily average. */
    val delta: Double = 0.0,
    val stats: DetailStats = DetailStats()
) {
    val isEmpty: Boolean get() = series.isEmpty()
    val goal: Double? get() = metric.storedGoal(settings)
}

/** Backs [ActivityDetailScreen]: one metric, one window, plus the window before it for the delta. */
class ActivityDetailViewModel(private val repo: Repository) : ViewModel() {

    private data class Request(val metric: Metric, val window: DetailWindow)

    private val request = MutableStateFlow(Request(Metric.Steps, DetailWindow.Week))

    fun load(metric: Metric) = request.update { it.copy(metric = metric) }

    fun select(window: DetailWindow) = request.update { it.copy(window = window) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<ActivityDetailUi> = request.flatMapLatest { r ->
        val span = (r.window.days - 1).toLong()
        val to = Dates.today()
        val from = Dates.shift(to, -span)
        val previousTo = Dates.shift(from, -1)
        val previousFrom = Dates.shift(previousTo, -span)
        combine(
            repo.settings,
            repo.metricSeries(r.metric, from, to),
            repo.metricSeries(r.metric, previousFrom, previousTo)
        ) { settings, series, previous -> build(r, from, to, settings, series, previous) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityDetailUi())

    private fun build(
        request: Request,
        from: String,
        to: String,
        settings: Settings,
        series: List<DayValue>,
        previous: List<DayValue>
    ): ActivityDetailUi {
        val goal = request.metric.storedGoal(settings)
        val logged = series.filter { it.value > 0 }
        val average = logged.averageValue()
        // Compared on averages, not totals: a half-finished week is not a 50% collapse.
        val before = previous.filter { it.value > 0 }.averageValue()
        val delta = if (before <= 0.0) 0.0 else (average - before) / before * 100.0

        return ActivityDetailUi(
            metric = request.metric,
            window = request.window,
            from = from,
            to = to,
            settings = settings,
            series = series,
            today = series.firstOrNull { it.date == to }?.value,
            delta = delta,
            stats = DetailStats(
                average = average,
                total = logged.sumOf { it.value },
                high = logged.maxByOrNull { it.value },
                low = logged.minByOrNull { it.value },
                daysWithData = logged.size,
                daysAtGoal = if (goal == null || goal <= 0.0) 0 else series.count { it.value >= goal }
            )
        )
    }
}

private fun List<DayValue>.averageValue(): Double =
    if (isEmpty()) 0.0 else sumOf { it.value } / size
