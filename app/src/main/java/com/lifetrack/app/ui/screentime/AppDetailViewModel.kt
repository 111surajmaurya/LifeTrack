package com.lifetrack.app.ui.screentime

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.TrackedApp
import com.lifetrack.app.screentime.UsageSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

enum class DetailWindow(val days: Int, val label: String) {
    Today(1, "Today"),
    Week(7, "This week"),
    Month(30, "This month"),
    Quarter(90, "3 months"),
    HalfYear(182, "6 months");

    val barred: Boolean get() = days <= 7
}

/**
 * The point of the detail screen: what the numbers say about the habit, not just the total.
 * All of it is computed over *recorded* days only — a day with no row is a day before tracking
 * started, and averaging it in as a zero would flatter every stat.
 */
data class PatternStats(
    val recordedDays: Int = 0,
    val busiestDay: String? = null,
    val busiestMin: Int = 0,
    val quietestDay: String? = null,
    val quietestMin: Int = 0,
    val dailyAverageMin: Int = 0,
    val daysOverLimit: Int = 0,
    val longestUnderStreak: Int = 0
)

data class AppDetailState(
    val loading: Boolean = true,
    val permission: Boolean = true,
    val packageName: String = "",
    val label: String = "",
    val app: TrackedApp? = null,
    val limitMin: Int = 0,
    val minutesToday: Int = 0,
    val window: DetailWindow = DetailWindow.Week,
    val from: String = Dates.today(),
    val to: String = Dates.today(),
    val series: List<DayValue> = emptyList(),
    val windowTotal: Int = 0,
    val previousTotal: Int = 0,
    val deltaPct: Double = 0.0,
    val hasPrevious: Boolean = false,
    val pattern: PatternStats = PatternStats()
) {
    val overToday: Boolean get() = limitMin > 0 && minutesToday > limitMin
    val progressToday: Float get() = if (limitMin > 0) minutesToday / limitMin.toFloat() else 0f
    val hasHistory: Boolean get() = series.any { it.value > 0.0 }

    /**
     * The percent chip, which needs more than [hasPrevious]: today is still under way, so
     * against all of yesterday it would show "-80%" every morning.
     */
    val hasDelta: Boolean get() = hasPrevious && window != DetailWindow.Today
}

/** The pair that decides which rows to read; kept as a type so [combine] stays typed. */
private data class Query(val packageName: String, val window: DetailWindow)

class AppDetailViewModel(private val repo: Repository) : ViewModel() {

    private val packageName = MutableStateFlow<String?>(null)
    private val window = MutableStateFlow(DetailWindow.Week)
    private val permission = MutableStateFlow(true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val detail = combine(packageName.filterNotNull(), window) { pkg, w -> Query(pkg, w) }
        .flatMapLatest { q ->
        val to = Dates.today()
        val from = Dates.shift(to, -(q.window.days - 1).toLong())
        val prevTo = Dates.shift(from, -1)
        val prevFrom = Dates.shift(prevTo, -(q.window.days - 1).toLong())
        combine(
            repo.trackedApps,
            repo.usagePerDayFor(q.packageName, from, to),
            repo.usagePerDayFor(q.packageName, prevFrom, prevTo)
        ) { apps, series, previous -> build(q, from, to, apps, series, previous) }
    }

    val state: StateFlow<AppDetailState> = combine(detail, permission) { s, granted ->
        s.copy(permission = granted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailState())

    fun load(packageName: String) {
        this.packageName.value = packageName
    }

    fun setWindow(w: DetailWindow) {
        window.value = w
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        permission.value = UsageSync.hasPermission(app)
        viewModelScope.launch { UsageSync.snapshotToday(app, repo) }
    }

    fun setLimit(minutes: Int) {
        val pkg = packageName.value ?: return
        viewModelScope.launch {
            val app = repo.trackedApps.first().firstOrNull { it.packageName == pkg } ?: return@launch
            repo.upsertTrackedApp(
                app.copy(
                    dailyLimitMin = minutes.coerceIn(
                        ScreenTimeViewModel.MIN_LIMIT_MIN,
                        ScreenTimeViewModel.MAX_LIMIT_MIN
                    )
                )
            )
        }
    }

    private fun build(
        q: Query,
        from: String,
        to: String,
        apps: List<TrackedApp>,
        series: List<DayValue>,
        previous: List<DayValue>
    ): AppDetailState {
        val app = apps.firstOrNull { it.packageName == q.packageName }
        val limit = app?.dailyLimitMin ?: 0
        val byDate = series.associate { it.date to it.value.toInt() }
        val windowTotal = byDate.values.sum()
        val previousTotal = previous.sumOf { it.value.toInt() }
        return AppDetailState(
            loading = false,
            packageName = q.packageName,
            label = app?.label ?: q.packageName.substringAfterLast('.'),
            app = app,
            limitMin = limit,
            minutesToday = byDate[Dates.today()] ?: 0,
            window = q.window,
            from = from,
            to = to,
            series = series,
            windowTotal = windowTotal,
            previousTotal = previousTotal,
            deltaPct = if (previousTotal > 0) (windowTotal - previousTotal) * 100.0 / previousTotal else 0.0,
            hasPrevious = previousTotal > 0,
            pattern = pattern(byDate, from, to, limit)
        )
    }

    private fun pattern(
        byDate: Map<String, Int>,
        from: String,
        to: String,
        limit: Int
    ): PatternStats {
        if (byDate.isEmpty()) return PatternStats()

        val perWeekday = byDate.entries
            .groupBy { Dates.parse(it.key).dayOfWeek }
            .mapValues { (_, days) -> days.sumOf { it.value } / days.size }
        val busiest = perWeekday.maxByOrNull { it.value }
        val quietest = perWeekday.minByOrNull { it.value }

        // A missing day breaks the streak: we cannot claim a day we never recorded.
        var streak = 0
        var best = 0
        Dates.range(from, to).forEach { date ->
            val minutes = byDate[date]
            if (minutes != null && limit > 0 && minutes <= limit) {
                streak++
                if (streak > best) best = streak
            } else {
                streak = 0
            }
        }

        return PatternStats(
            recordedDays = byDate.size,
            busiestDay = busiest?.key?.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            busiestMin = busiest?.value ?: 0,
            quietestDay = quietest?.key?.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            quietestMin = quietest?.value ?: 0,
            dailyAverageMin = byDate.values.sum() / byDate.size,
            daysOverLimit = if (limit > 0) byDate.values.count { it > limit } else 0,
            longestUnderStreak = best
        )
    }
}
