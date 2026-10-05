package com.lifetrack.app.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.lifetrack.app.MainActivity
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Reminder

/**
 * One alarm per enabled reminder, for its next occurrence.
 * When it fires, [AlarmReceiver] either posts a notification or starts [AlarmService], and
 * queues the following one.
 *
 * Delivery on modern Android needs four things to line up, which is why [health] exists and the
 * Alarms screen shows all four:
 *   1. notifications allowed (POST_NOTIFICATIONS, Android 13+),
 *   2. exact alarms allowed - denied by default on Android 13+, and an inexact alarm can be
 *      deferred for hours by Doze,
 *   3. the app exempt from battery optimisation, or the OEM may drop the alarm entirely,
 *   4. full-screen intents allowed (Android 14+), or an ALARM reminder degrades to a heads-up
 *      notification instead of taking over the lock screen.
 *
 * Exact alarms go out via setAlarmClock(), which is the one alarm type Doze and App Standby
 * are not allowed to defer.
 */
object ReminderScheduler {
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_SNOOZE = "is_snooze"

    /**
     * Request-code base for snoozes. A snooze must not replace the recurring alarm's
     * PendingIntent (same receiver, same extras - only the request code separates them), so its
     * codes live in their own band, well clear of row ids.
     */
    const val SNOOZE_CODE_BASE = 1_000_000

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun ignoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Android 14 made this a per-app grant; below it, a full-screen intent always works. */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }

    /** Everything that has to be true for a reminder to actually arrive. */
    data class Health(
        val notificationsEnabled: Boolean,
        val exactAlarms: Boolean,
        val batteryUnrestricted: Boolean,
        val fullScreenIntents: Boolean
    ) {
        val allGood get() =
            notificationsEnabled && exactAlarms && batteryUnrestricted && fullScreenIntents
    }

    fun health(context: Context) = Health(
        notificationsEnabled = Notifications.enabled(context),
        exactAlarms = canScheduleExact(context),
        batteryUnrestricted = ignoringBatteryOptimizations(context),
        fullScreenIntents = canUseFullScreenIntent(context)
    )

    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        else null

    @Suppress("BatteryLife") // personal tracker: a missed reminder is the whole failure mode
    fun batterySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun fullScreenIntentSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")
            )
        else null

    fun schedule(context: Context, r: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, r.id)
        am.cancel(pi)
        if (!r.enabled) return
        val at = Dates.nextTrigger(r.hour, r.minute, r.daysMask) ?: return
        fire(context, am, at, pi)
    }

    /**
     * Re-fires [r] in `snoozeMinutes`. Uses its own request-code band so the recurring alarm
     * for the same reminder keeps its slot - snoozing 7am must not move tomorrow's 7am.
     */
    fun snooze(context: Context, r: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingSnooze(context, r.id)
        am.cancel(pi)
        val minutes = r.snoozeMinutes.coerceIn(1, 120)
        val at = System.currentTimeMillis() + minutes * 60_000L
        SnoozeStore.saveReminder(context, r.id, at)   // so a reboot mid-snooze can re-arm it
        fire(context, am, at, pi)
    }

    internal fun fire(context: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
        if (canScheduleExact(context)) {
            // setAlarmClock is the only kind Doze/App Standby cannot postpone.
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            // Best effort without the permission: still wakes the device, just not to the second.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context, id))
        am.cancel(pendingSnooze(context, id))   // a pending snooze outlives the row otherwise
        SnoozeStore.clearReminder(context, id)
    }

    /** Every recurring alarm, plus any snooze still due - reboots clear both. */
    fun rescheduleAll(context: Context, reminders: List<Reminder>) {
        reminders.forEach { schedule(context, it) }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val snoozes = SnoozeStore.reminders(context)
        reminders.forEach { r ->
            val at = snoozes[r.id] ?: return@forEach
            // Same PendingIntent as the original, so an alarm that survived is just replaced.
            fire(context, am, at, pendingSnooze(context, r.id))
        }
    }

    private fun pending(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun pendingSnooze(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, SNOOZE_CODE_BASE + id.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_SNOOZE, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
