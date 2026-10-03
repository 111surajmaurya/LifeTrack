package com.lifetrack.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.data.Routine
import com.lifetrack.app.data.RoutineKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Done" and "Skip" on a routine notification. Answering from the shade is the whole point:
 * the tracker only works if marking an item costs one tap at the moment it happens.
 */
class RoutineActions : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(RoutineScheduler.EXTRA_ROUTINE_ID, -1)
        val date = intent.getStringExtra(RoutineScheduler.EXTRA_ROUTINE_DATE) ?: return
        if (itemId < 0) return
        val status = when (intent.action) {
            ACTION_DONE -> Routine.DONE
            ACTION_SKIP -> Routine.MISSED
            else -> return
        }
        NotificationManagerCompat.from(context).cancel(Notifications.routineNotificationId(itemId))
        val repo = (context.applicationContext as? LifeTrackApp)?.repository ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { repo.setRoutineStatus(date, itemId, status) } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_DONE = "com.lifetrack.app.routine.DONE"
        const val ACTION_SKIP = "com.lifetrack.app.routine.SKIP"

        fun intent(context: Context, action: String, itemId: Long, date: String): Intent =
            Intent(context, RoutineActions::class.java)
                .setAction(action)
                .putExtra(RoutineScheduler.EXTRA_ROUTINE_ID, itemId)
                .putExtra(RoutineScheduler.EXTRA_ROUTINE_DATE, date)

        /**
         * Holding to dismiss the wake-up alarm proves you are up, so it counts by itself. Any
         * other alarm only proves you heard it, so it asks with a quiet notification instead.
         */
        fun alarmDismissed(context: Context, itemId: Long, date: String) {
            val repo = (context.applicationContext as? LifeTrackApp)?.repository ?: return
            CoroutineScope(Dispatchers.IO).launch {
                val item = repo.routineItem(itemId) ?: return@launch
                if (item.kindType == RoutineKind.WAKE) repo.setRoutineStatus(date, itemId, Routine.DONE)
                else Notifications.showRoutine(context, item, date, checkIn = true)
            }
        }

        fun alarmRangOut(context: Context, itemId: Long, date: String) {
            val repo = (context.applicationContext as? LifeTrackApp)?.repository ?: return
            CoroutineScope(Dispatchers.IO).launch {
                val item = repo.routineItem(itemId) ?: return@launch
                Notifications.showRoutine(context, item, date, checkIn = true)
            }
        }
    }
}
