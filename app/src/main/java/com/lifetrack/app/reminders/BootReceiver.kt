package com.lifetrack.app.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.screentime.LimitWatchService
import com.lifetrack.app.steps.StepSyncJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms are cleared on reboot; put them back. Also re-armed when the clock or time zone changes
 * (each alarm is an absolute instant worked out in local time) and when exact alarms are granted,
 * so the inexact fallbacks armed without the permission become exact ones.
 *
 * There is deliberately no LOCKED_BOOT_COMPLETED: the database lives in credential-encrypted
 * storage, so alarms are restored at first unlock, when BOOT_COMPLETED arrives.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val repo = (context.applicationContext as? LifeTrackApp)?.repository ?: return
        Notifications.ensureChannels(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.rescheduleAll(context, repo.enabledReminders())
                runCatching { RoutineScheduler.rescheduleAll(context, repo) }
                StepSyncJob.schedule(context)
                runCatching { LimitWatchService.sync(context) }
            } finally { pending.finish() }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,   // "android.intent.action.TIME_SET"
            // API 31+; a plain string constant, so naming it is safe on older releases.
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
