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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One tracked app plus what it cost today. [app] is kept whole so edit/remove can act on it. */
data class AppRow(val app: TrackedApp, val minutes: Int) {
    val packageName: String get() = app.packageName
    val limitMin: Int get() = app.dailyLimitMin
    val progress: Float get() = if (limitMin > 0) minutes / limitMin.toFloat() else 0f
    val over: Boolean get() = limitMin > 0 && minutes > limitMin
    val remainingMin: Int get() = (limitMin - minutes).coerceAtLeast(0)
}

data class ScreenTimeState(
    val loading: Boolean = true,
    val permission: Boolean = true,
    val day: String = Dates.today(),
    val weekFrom: String = Dates.shift(Dates.today(), -6),
    val rows: List<AppRow> = emptyList(),
    val weekly: List<DayValue> = emptyList(),
    val totalToday: Int = 0,
    val totalYesterday: Int = 0,
    val totalLimit: Int = 0,
    val deltaPct: Double = 0.0,
    val hasYesterday: Boolean = false
) {
    val anyOver: Boolean get() = rows.any { it.over }
    val overCount: Int get() = rows.count { it.over }
    val hasHistory: Boolean get() = weekly.any { it.value > 0.0 }
}

/** Everything the day's three flows carry, grouped so [combine] stays typed. */
private data class Today(
    val day: String,
    val weekFrom: String,
    val apps: List<TrackedApp>,
    val perApp: List<DayValue>,
    val perDay: List<DayValue>
)

class ScreenTimeViewModel(private val repo: Repository) : ViewModel() {

    private val permission = MutableStateFlow(true)

    /** Re-read on every resume so a session left open overnight rolls to the new day. */
    private val day = MutableStateFlow(Dates.today())
    private var backfilled = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val today: Flow<Today> = day.flatMapLatest { d ->
        val weekFrom = Dates.shift(d, -6)
        combine(
            repo.trackedApps,
            repo.usageTotalPerApp(d, d),
            repo.usageTotalPerDay(weekFrom, d)
        ) { apps, perApp, perDay -> Today(d, weekFrom, apps, perApp, perDay) }
    }

    val state: StateFlow<ScreenTimeState> = combine(today, permission) { t, granted ->
        // usageTotalPerApp puts the package name in DayValue.date.
        val minutes = t.perApp.associate { it.date to it.value.toInt() }
        val rows = t.apps
            .map { AppRow(it, minutes[it.packageName] ?: 0) }
            .sortedWith(compareByDescending<AppRow> { it.minutes }.thenBy { it.app.label.lowercase() })
        val yesterday = t.perDay.firstOrNull { it.date == Dates.shift(t.day, -1) }?.value?.toInt() ?: 0
        val total = rows.sumOf { it.minutes }
        ScreenTimeState(
            loading = false,
            permission = granted,
            day = t.day,
            weekFrom = t.weekFrom,
            rows = rows,
            weekly = t.perDay,
            totalToday = total,
            totalYesterday = yesterday,
            totalLimit = rows.sumOf { it.limitMin },
            deltaPct = if (yesterday > 0) (total - yesterday) * 100.0 / yesterday else 0.0,
            hasYesterday = yesterday > 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenTimeState())

    /**
     * Called on every ON_RESUME. Nothing is stored until we snapshot, so this is what keeps
     * today's numbers live and what gives the charts a history at all.
     */
    fun refresh(context: Context) {
        val app = context.applicationContext
        permission.value = UsageSync.hasPermission(app)
        day.value = Dates.today()
        viewModelScope.launch {
            UsageSync.snapshotToday(app, repo)
            if (!backfilled) {
                backfilled = true
                UsageSync.backfill(app, repo)
            }
        }
    }

    fun setLimit(app: TrackedApp, minutes: Int) {
        viewModelScope.launch {
            repo.upsertTrackedApp(app.copy(dailyLimitMin = minutes.coerceIn(MIN_LIMIT_MIN, MAX_LIMIT_MIN)))
        }
    }

    /** Adds a user-chosen app and immediately snapshots, so its row is not stuck at zero. */
    fun addApp(context: Context, packageName: String, label: String, limitMin: Int = DEFAULT_LIMIT_MIN) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return
        val app = context.applicationContext
        viewModelScope.launch {
            repo.upsertTrackedApp(
                TrackedApp(
                    packageName = pkg,
                    label = label.trim().ifBlank { pkg.substringAfterLast('.') },
                    dailyLimitMin = limitMin.coerceIn(MIN_LIMIT_MIN, MAX_LIMIT_MIN),
                    seeded = false
                )
            )
            UsageSync.snapshotToday(app, repo)
        }
    }

    /** Seeded apps refuse to be removed; the UI never offers the action for them. */
    fun remove(app: TrackedApp) {
        viewModelScope.launch { repo.removeTrackedApp(app) }
    }

    companion object {
        const val DEFAULT_LIMIT_MIN = 30
        const val MIN_LIMIT_MIN = 5
        const val MAX_LIMIT_MIN = 12 * 60
    }
}
