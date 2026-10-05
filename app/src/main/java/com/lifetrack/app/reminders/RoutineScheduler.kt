package com.lifetrack.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Routine
import com.lifetrack.app.data.RoutineItem
import com.lifetrack.app.data.RoutinePlan

/**
 * Arms the daily routine: one alarm per item, for its next occurrence, honouring tomorrow's
 * plan. It shares [AlarmReceiver] and [AlarmService] with ordinary reminders, but every id it
 * uses lives in its own band so a routine item can never cancel or ring as a reminder row.
 *
 * Re-armed after each fire, on every cold start, on boot, and whenever an item or a plan is saved.
 * Pending snoozes are kept in [SnoozeStore] and re-armed along with everything else.
 */
object RoutineScheduler {
    const val EXTRA_ROUTINE_ID = "routine_id"
    const val EXTRA_ROUTINE_DATE = "routine_date"

    private const val FIRE_CODE_BASE = 2_000_000
    private const val SNOOZE_CODE_BASE = 2_100_000

    /**
     * What [AlarmService] and the notifications see as the id. Anything at or above this is a
     * routine item, which is how a snooze or dismiss knows which table to look in.
     */
    const val RING_ID_BASE = 3_000_000L

    fun ringId(itemId: Long): Long = RING_ID_BASE + itemId
    fun isRoutineRing(id: Long): Boolean = id >= RING_ID_BASE
    fun itemIdOf(ringId: Long): Long = ringId - RING_ID_BASE

    suspend fun rescheduleAll(context: Context, repo: Repository) {
        val today = Dates.today()
        val plans = repo.routinePlansOnce(today, Dates.shift(today, 2))
        val items = repo.routineItemsOnce()
        items.forEach { item ->
            schedule(context, item, plans.filter { it.itemId == item.id }.associateBy { it.date })
        }
        // Snoozes too, which a reboot would otherwise lose. AlarmReceiver re-checks the plan.
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val ids = items.map { it.id }.toSet()
        SnoozeStore.routines(context).filter { it.itemId in ids }.forEach { s ->
            ReminderScheduler.fire(context, am, s.at, pending(context, s.itemId, s.date, snooze = true))
        }
    }

    suspend fun reschedule(context: Context, repo: Repository, itemId: Long) {
        val item = repo.routineItem(itemId) ?: return
        val today = Dates.today()
        val plans = repo.routinePlansOnce(today, Dates.shift(today, 2))
            .filter { it.itemId == itemId }.associateBy { it.date }
        schedule(context, item, plans)
    }

    private fun schedule(context: Context, item: RoutineItem, plans: Map<String, RoutinePlan>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context, item.id, date = null, snooze = false))
        val (date, at) = Routine.nextFire(item, plans) ?: return
        ReminderScheduler.fire(context, am, at, pending(context, item.id, date, snooze = false))
    }

    fun snooze(context: Context, itemId: Long, date: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, itemId, date, snooze = true)
        am.cancel(pi)
        val at = System.currentTimeMillis() + Routine.SNOOZE_MINUTES * 60_000L
        SnoozeStore.saveRoutine(context, itemId, at, date)
        ReminderScheduler.fire(context, am, at, pi)
    }

    /**
     * The extras are part of the PendingIntent but not of its identity (request code + target),
     * so cancelling with any date cancels the armed one, and FLAG_UPDATE_CURRENT swaps the date in.
     */
    private fun pending(context: Context, itemId: Long, date: String?, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (if (snooze) SNOOZE_CODE_BASE else FIRE_CODE_BASE) + itemId.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_ROUTINE_ID, itemId)
                .putExtra(EXTRA_ROUTINE_DATE, date ?: Dates.today())
                .putExtra(ReminderScheduler.EXTRA_SNOOZE, snooze),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
