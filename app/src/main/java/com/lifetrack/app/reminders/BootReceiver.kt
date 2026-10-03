package com.lifetrack.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.steps.StepSyncJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are cleared on reboot; put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val handled = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        if (!handled) return
        val repo = (context.applicationContext as? LifeTrackApp)?.repository ?: return
        Notifications.ensureChannels(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.rescheduleAll(context, repo.enabledReminders())
                runCatching { RoutineScheduler.rescheduleAll(context, repo) }
                StepSyncJob.schedule(context)
            } finally { pending.finish() }
        }
    }
}
