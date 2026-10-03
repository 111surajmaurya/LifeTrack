package com.lifetrack.app.ui.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.DailyMetric
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.HourlySteps
import com.lifetrack.app.data.Metric
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Settings
import com.lifetrack.app.steps.HealthConnectSteps
import com.lifetrack.app.steps.HealthSync
import com.lifetrack.app.steps.StepRecorder
import com.lifetrack.app.steps.StepSensor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The window the Activity tab's hero chart covers. */
private const val WEEK = 7

/** What the tab reads out of the database. */
data class ActivityData(
    val today: String = Dates.today(),
    val weekFrom: String = Dates.shift(Dates.today(), -(WEEK - 1L)),
    val settings: Settings = Settings(),
    val todayMetrics: List<DailyMetric> = emptyList(),
    val weekSteps: List<DayValue> = emptyList(),
    /** The day the "when did I walk" chart is showing, and its 24 hours. */
    val hourDay: String = Dates.today(),
    val hours: List<HourlySteps> = emptyList()
)

/** What the tab knows about the Health Connect link, which lives outside the database. */
data class ActivityLink(
    val checked: Boolean = false,
    val status: HealthConnectSteps.Status = HealthConnectSteps.Status.NOT_INSTALLED,
    val connected: Boolean = false,
    /** Metrics Health Connect is installed for but that the user hasn't shared. */
    val missing: List<Metric> = emptyList(),
    val sensorPresent: Boolean = false,
    val sensorGranted: Boolean = false,
    val importing: Boolean = false,
    val importMessage: String? = null,
    /** Health Connect can read in the background on this phone, but LifeTrack isn't allowed yet. */
    val backgroundMissing: Boolean = false,
    val storedFrom: String? = null,
    val storedDays: Int = 0,
    /** The real reason a read failed - never collapsed into "0 steps". */
    val error: String? = null
)

data class ActivityUi(
    val data: ActivityData = ActivityData(),
    val link: ActivityLink = ActivityLink()
) {
    fun valueOf(metric: Metric): Double? =
        data.todayMetrics.firstOrNull { it.metric == metric.key }?.value

    val steps: Double get() = valueOf(Metric.Steps) ?: 0.0
    val stepGoal: Int get() = data.settings.stepGoal.coerceAtLeast(1)
    val progress: Float get() = (steps / stepGoal).toFloat()
    val stepsLeft: Int get() = (stepGoal - steps).toInt().coerceAtLeast(0)

    /** "HEALTH_CONNECT" / "SENSOR", or null when today has no step reading at all. */
    val stepSource: String?
        get() = data.todayMetrics.firstOrNull { it.metric == Metric.Steps.key }?.source

    /** Metrics other than steps that actually returned a number today - one tile each. */
    val tiles: List<Metric>
        get() = Metric.entries.filter { it != Metric.Steps && valueOf(it) != null }

    val hasAnyData: Boolean
        get() = data.todayMetrics.isNotEmpty() || data.weekSteps.any { it.value > 0 } || link.storedDays > 0
}

/**
 * Drives the Activity tab. Health Connect permissions are granted in *another app's* UI, so
 * nothing here can rely on a result callback - the screen re-runs [refresh] on every resume.
 */
class ActivityViewModel(private val repo: Repository) : ViewModel() {

    /** Re-read on every refresh so the tab rolls over at midnight without being recreated. */
    private val day = MutableStateFlow(Dates.today())
    private val link = MutableStateFlow(ActivityLink())
    private val hourDay = MutableStateFlow(Dates.today())

    private var refreshing = false
    private var triedFirstImport = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val data: Flow<ActivityData> = combine(day, hourDay) { d, h -> d to h }.flatMapLatest { (today, shown) ->
        val from = Dates.shift(today, -(WEEK - 1L))
        combine(
            repo.settings,
            repo.metricsOn(today),
            repo.metricSeries(Metric.Steps, from, today),
            repo.hourlyStepsOn(shown)
        ) { settings, metrics, week, hours ->
            ActivityData(today, from, settings, metrics, week, shown, hours)
        }
    }

    /** Step the hour chart a day back or forward; never past today. */
    fun shiftHourDay(days: Long) {
        val next = Dates.shift(hourDay.value, days)
        if (!Dates.isFuture(next)) hourDay.value = next
    }

    val ui: StateFlow<ActivityUi> = combine(data, link) { d, l -> ActivityUi(d, l) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUi())

    /**
     * Checks the link, pulls today's numbers, and falls back to the phone's own step counter
     * when Health Connect can't or won't answer.
     */
    fun refresh(context: Context) {
        day.value = Dates.today()
        if (refreshing) return
        refreshing = true
        val app = context.applicationContext
        viewModelScope.launch {
            try {
                val sensor = StepSensor(app)
                val granted = hasActivityRecognition(app)
                link.update { it.copy(sensorPresent = sensor.isAvailable, sensorGranted = granted) }

                val sync = HealthSync(app, repo)
                val state = sync.status()
                val connected = state == HealthConnectSteps.Status.AVAILABLE && sync.hasPermission()
                link.update { it.copy(checked = true, status = state, connected = connected) }

                if (connected) {
                    sync.syncToday()
                    val dayError = sync.lastError
                    // A week of hours: covers days the phone sat in a drawer and the job couldn't read.
                    sync.refreshRecent(WEEK)
                    sync.syncHours(WEEK)
                    val backgroundMissing = sync.backgroundSupported() && !sync.backgroundAllowed()
                    link.update {
                        it.copy(missing = sync.missingMetrics(), error = dayError, backgroundMissing = backgroundMissing)
                    }
                    // First connect: the six months already on the phone are worth more than
                    // starting today's chart from scratch.
                    if (!triedFirstImport && repo.metricCount(Metric.Steps) == 0) {
                        triedFirstImport = true
                        runImport(sync)
                    }
                } else {
                    link.update { it.copy(error = null) }
                    if (granted) StepRecorder.sampleSensor(app, repo)
                }
                refreshStored()
            } finally {
                refreshing = false
            }
        }
    }

    /** Pulls the last six months out of Health Connect into daily_metrics. */
    fun importHistory(context: Context) {
        if (link.value.importing) return
        val app = context.applicationContext
        viewModelScope.launch { runImport(HealthSync(app, repo)) }
    }

    fun dismissMessage() = link.update { it.copy(importMessage = null, error = null) }

    private suspend fun runImport(sync: HealthSync) {
        link.update { it.copy(importing = true, importMessage = null, error = null) }
        val rows = sync.importHistory()
        val days = sync.lastImportedDays
        val message = when {
            rows > 0 -> "Imported ${days.plural("day")} - ${rows.plural("reading")}"
            sync.lastError != null -> null
            else -> "Already up to date - nothing new to import"
        }
        link.update { it.copy(importing = false, importMessage = message, error = sync.lastError) }
        refreshStored()
    }

    private suspend fun refreshStored() {
        val from = repo.earliestMetricDate(Metric.Steps)
        val days = repo.metricCount(Metric.Steps)
        link.update { it.copy(storedFrom = from, storedDays = days) }
    }

    private fun hasActivityRecognition(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}

private fun Int.plural(noun: String): String = "%,d %s".format(this, if (this == 1) noun else "${noun}s")
