package com.lifetrack.app.ui.routine

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.DateSlot
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.HourlySteps
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Routine
import com.lifetrack.app.data.RoutineEvidence
import com.lifetrack.app.data.RoutineItem
import com.lifetrack.app.data.RoutineLog
import com.lifetrack.app.data.RoutinePlan
import com.lifetrack.app.data.RoutineSlot
import com.lifetrack.app.data.RoutineStatus
import com.lifetrack.app.reminders.RoutineScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** How far back the tracker looks: a month of dots and a 30-day hit rate. */
const val HISTORY_DAYS = 30

/** One routine item's record: today, the last [HISTORY_DAYS] days oldest first, and the streak. */
data class ItemTrack(
    val item: RoutineItem,
    val today: RoutineSlot,
    val days: List<RoutineSlot>,
    val streak: Int,
    val hitRate: Float?
)

data class RoutineUi(
    val loaded: Boolean = false,
    val today: String = Dates.today(),
    val items: List<RoutineItem> = emptyList(),
    val tracks: List<ItemTrack> = emptyList(),
    /** Tomorrow as it stands - the plan if one was saved, the defaults otherwise. */
    val tomorrow: List<RoutineSlot> = emptyList(),
    val tomorrowPlanned: Boolean = false,
    /** Hit rate per day, oldest first, for the trend strip. */
    val dailyRates: List<Pair<String, Float?>> = emptyList()
) {
    val todaySlots: List<RoutineSlot> get() = tracks.map { it.today }.sortedBy { it.minuteOfDay }
    val doneToday: Int get() = todaySlots.count { it.status == RoutineStatus.DONE }
    val countedToday: Int get() = todaySlots.count { it.status != RoutineStatus.OFF }
    val week: Float? get() = Routine.hitRate(tracks.flatMap { it.days.takeLast(7) })
    val month: Float? get() = Routine.hitRate(tracks.flatMap { it.days })
}

private data class Inputs(
    val items: List<RoutineItem>,
    val plans: List<RoutinePlan>,
    val logs: List<RoutineLog>,
    val meals: List<DateSlot>,
    val hours: List<HourlySteps>
)

/**
 * [startedAt] is when tracking began ([com.lifetrack.app.data.RoutineStart]): days before it are
 * left out, and on the first day anything scheduled before it is "off" rather than missed.
 */
class RoutineViewModel(private val repo: Repository, private val startedAt: Long) : ViewModel() {

    /** Ticks once a minute so "pending" turns into "missed" on time without a resume. */
    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = clock.flatMapLatest { now ->
        val today = Dates.format(now.toLocalDate())
        val from = Dates.shift(today, -(HISTORY_DAYS - 1L))
        val tomorrow = Dates.shift(today, 1)
        combine(
            repo.routineItems,
            repo.routinePlans(from, tomorrow),
            repo.routineLogs(from, today),
            repo.mealSlotsLogged(from, today),
            repo.hourlyStepsBetween(from, today)
        ) { items, plans, logs, meals, hours ->
            build(Inputs(items, plans, logs, meals, hours), now, from, today, tomorrow)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineUi())

    private fun build(i: Inputs, now: LocalDateTime, from: String, today: String, tomorrow: String): RoutineUi {
        val start = java.time.Instant.ofEpochMilli(startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        val startDay = Dates.format(start.toLocalDate())
        val dates = Dates.range(maxOf(from, minOf(startDay, today)), today)
        val plans = i.plans.associateBy { it.date to it.itemId }
        val logs = i.logs.associateBy { it.date to it.itemId }
        val meals = i.meals.groupBy({ it.date }, { it.value })
        val hours = i.hours.groupBy { it.date }
        val plannedDates = i.plans.map { it.date }.toSet()

        fun evidence(date: String) = RoutineEvidence(
            mealSlots = meals[date].orEmpty().toSet(),
            hourlySteps = hours[date].orEmpty().associate { it.hour to it.steps },
            planSavedForNextDay = Dates.shift(date, 1) in plannedDates
        )
        val evidenceByDate = dates.associateWith(::evidence)

        val tracks = i.items.map { item ->
            val days = dates.map { d ->
                val slot = Routine.resolve(item, d, plans[d to item.id], logs[d to item.id], evidenceByDate.getValue(d), now)
                val beforeStart = d == startDay && logs[d to item.id] == null &&
                    Dates.parse(d).atTime(slot.hour, slot.minute).isBefore(start)
                if (beforeStart) slot.copy(status = RoutineStatus.OFF) else slot
            }
            ItemTrack(
                item = item,
                today = days.last(),
                days = days,
                streak = Routine.streak(days.associate { it.date to it.status }, today),
                hitRate = Routine.hitRate(days)
            )
        }
        val tomorrowSlots = i.items.map { item ->
            Routine.resolve(item, tomorrow, plans[tomorrow to item.id], null, RoutineEvidence(), now)
        }.sortedBy { it.minuteOfDay }

        return RoutineUi(
            loaded = true,
            today = today,
            items = i.items,
            tracks = tracks,
            tomorrow = tomorrowSlots,
            tomorrowPlanned = tomorrow in plannedDates,
            dailyRates = dates.mapIndexed { index, d -> d to Routine.hitRate(tracks.map { it.days[index] }) }
        )
    }

    /** Tap on a row: pending → done → missed → back to "not answered". */
    fun cycle(slot: RoutineSlot) {
        val next = when (slot.status) {
            RoutineStatus.PENDING -> Routine.DONE
            RoutineStatus.DONE -> Routine.MISSED
            RoutineStatus.MISSED -> null
            RoutineStatus.OFF -> return
        }
        viewModelScope.launch { repo.setRoutineStatus(slot.date, slot.item.id, next) }
    }

    fun mark(slot: RoutineSlot, status: String?) {
        viewModelScope.launch { repo.setRoutineStatus(slot.date, slot.item.id, status) }
    }

    /** Saves one row per item for [date], then re-arms so tonight's change is what rings. */
    fun savePlan(context: Context, date: String, rows: List<RoutinePlan>) {
        val app = context.applicationContext
        viewModelScope.launch {
            repo.saveRoutinePlan(rows.map { it.copy(date = date) })
            RoutineScheduler.rescheduleAll(app, repo)
        }
    }

    fun updateItem(context: Context, item: RoutineItem) {
        val app = context.applicationContext
        viewModelScope.launch {
            repo.updateRoutineItem(item)
            RoutineScheduler.reschedule(app, repo, item.id)
        }
    }
}
