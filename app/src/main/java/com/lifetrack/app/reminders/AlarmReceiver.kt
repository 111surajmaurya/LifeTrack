package com.lifetrack.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Reminder
import com.lifetrack.app.data.Routine
import com.lifetrack.app.data.RoutineItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? LifeTrackApp ?: return
        val fromSnooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_SNOOZE, false)

        val routineId = intent.getLongExtra(RoutineScheduler.EXTRA_ROUTINE_ID, -1)
        if (routineId >= 0) {
            val date = intent.getStringExtra(RoutineScheduler.EXTRA_ROUTINE_DATE) ?: Dates.today()
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.repository.routineItem(routineId)?.let { item ->
                        // The plan may have switched this day off after the alarm was armed.
                        val plan = app.repository.routinePlan(date, item.id)
                        val (_, _, on) = Routine.slotFor(item, date, plan)
                        if (on) fireRoutine(context, item, date)
                    }
                    if (!fromSnooze) RoutineScheduler.reschedule(context, app.repository, routineId)
                } finally {
                    pending.finish()
                }
            }
            return
        }

        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1)
        if (id < 0) return
        // A receiver gets about ten seconds; the row read is off the main thread either way.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.reminder(id)?.let { r ->
                    if (!r.enabled) return@let
                    if (r.isAlarm) startAlarm(context, r) else Notifications.showReminder(context, r.id, r.label)
                    // A snooze fire must not shift the recurring slot, and schedule() is
                    // idempotent, so re-queueing here is safe on both paths.
                    if (!fromSnooze) ReminderScheduler.schedule(context, r)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun fireRoutine(context: Context, item: RoutineItem, date: String) {
        if (!item.isAlarm) {
            Notifications.showRoutine(context, item, date)
            return
        }
        val start = AlarmService.startIntent(
            context,
            id = RoutineScheduler.ringId(item.id),
            label = "${item.emoji} ${item.title}",
            ringSeconds = Routine.RING_SECONDS,
            snoozeMinutes = Routine.SNOOZE_MINUTES,
            vibrate = true,
            routineDate = date
        )
        runCatching { ContextCompat.startForegroundService(context, start) }
            .onFailure { Notifications.showRoutine(context, item, date) }
    }

    /**
     * The exact alarm that woke us carries a short foreground-service exemption, which is what
     * makes starting the service legal from the background here.
     */
    private fun startAlarm(context: Context, r: Reminder) {
        runCatching {
            ContextCompat.startForegroundService(context, AlarmService.startIntent(context, r))
        }.onFailure {
            // Better a passive notification than a silently dropped alarm.
            Notifications.showReminder(context, r.id, r.label)
        }
    }
}
