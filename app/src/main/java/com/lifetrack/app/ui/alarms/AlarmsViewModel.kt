package com.lifetrack.app.ui.alarms

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Habit
import com.lifetrack.app.data.Reminder
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Routine
import com.lifetrack.app.data.RoutineItem
import com.lifetrack.app.reminders.Notifications
import com.lifetrack.app.reminders.ReminderScheduler
import com.lifetrack.app.reminders.RoutineScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A reminder plus the moment it will next go off, or null when it never will. */
data class AlarmRow(val reminder: Reminder, val nextAt: Long?)

/** A daily-routine item plus when it next goes off, tomorrow's plan included. */
data class RoutineRow(val item: RoutineItem, val nextAt: Long?)

data class AlarmsUi(
    val rows: List<AlarmRow> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val routine: List<RoutineRow> = emptyList(),
    val health: ReminderScheduler.Health? = null,
    val loaded: Boolean = false
) {
    /** The alarm that will actually ring next - the only row that gets a countdown. */
    val soonest: AlarmRow? get() = rows.firstOrNull { it.reminder.enabled && it.nextAt != null }

    /** The next thing to go off at all, routine included - what the header counts down to. */
    val nextAny: Long?
        get() = (rows.mapNotNull { it.nextAt.takeIf { _ -> it.reminder.enabled } } + routine.mapNotNull { it.nextAt }).minOrNull()
}

class AlarmsViewModel(private val repo: Repository) : ViewModel() {

    private val health = MutableStateFlow<ReminderScheduler.Health?>(null)

    private val today = Dates.today()

    val state = combine(
        repo.reminders, repo.habits, health,
        repo.routineItems, repo.routinePlans(today, Dates.shift(today, 2))
    ) { reminders, habits, h, items, plans ->
        val routine = items.map { item ->
            val mine = plans.filter { it.itemId == item.id }.associateBy { it.date }
            RoutineRow(item, Routine.nextFire(item, mine)?.second)
        }
        AlarmsUi(
            rows = reminders.map { it.row() }.sorted(),
            habits = habits,
            routine = routine,
            health = h,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmsUi())

    /** Changes the *usual* time, mode or switch of a routine item - every day from now on. */
    fun saveRoutineItem(context: Context, item: RoutineItem) {
        val app = context.applicationContext
        viewModelScope.launch {
            repo.updateRoutineItem(item)
            RoutineScheduler.reschedule(app, repo, item.id)
        }
    }

    /** Permissions can change while the app is backgrounded, so the screen re-checks on resume. */
    fun refreshHealth(context: Context) {
        Notifications.ensureChannels(context)
        val app = context.applicationContext
        val before = health.value
        val now = ReminderScheduler.health(app)
        health.value = now
        // Just came back from granting exact alarms: everything armed so far is the inexact
        // fallback, which Doze can push back by hours, so re-arm it all as exact.
        if (before != null && !before.exactAlarms && now.exactAlarms) {
            viewModelScope.launch {
                runCatching { ReminderScheduler.rescheduleAll(app, repo.enabledReminders()) }
                runCatching { RoutineScheduler.rescheduleAll(app, repo) }
            }
        }
    }

    fun save(context: Context, reminder: Reminder) {
        val app = context.applicationContext
        viewModelScope.launch {
            val saved = if (reminder.id == 0L) reminder.copy(id = repo.addReminder(reminder))
            else reminder.also { repo.updateReminder(it) }
            ReminderScheduler.schedule(app, saved)
        }
    }

    fun setEnabled(context: Context, reminder: Reminder, enabled: Boolean) =
        save(context, reminder.copy(enabled = enabled))

    fun delete(context: Context, reminder: Reminder) {
        val app = context.applicationContext
        viewModelScope.launch {
            ReminderScheduler.cancel(app, reminder.id)   // drop the alarm before the row it points at
            repo.deleteReminder(reminder)
        }
    }

    private fun Reminder.row() =
        AlarmRow(this, if (enabled) Dates.nextTrigger(hour, minute, daysMask) else null)

    /** Next to fire reads first; anything switched off or with no days sinks to the bottom. */
    private fun List<AlarmRow>.sorted() = sortedWith(
        compareBy<AlarmRow> { it.nextAt == null }
            .thenBy { it.nextAt ?: Long.MAX_VALUE }
            .thenBy { it.reminder.hour * 60 + it.reminder.minute }
    )
}
