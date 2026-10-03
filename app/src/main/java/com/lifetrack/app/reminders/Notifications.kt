package com.lifetrack.app.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifetrack.app.MainActivity
import com.lifetrack.app.R
import com.lifetrack.app.data.RoutineItem
import com.lifetrack.app.data.RoutineKind
import com.lifetrack.app.data.Slot

object Notifications {
    const val CHANNEL_REMINDERS = "reminders_v2"

    /** Alarms get their own channel: max importance, no channel sound, and it ignores DND. */
    const val CHANNEL_ALARMS = "alarms_v1"

    /** Request-code bases so the alarm notification's own intents never collide with each other. */
    private const val CODE_FULL_SCREEN = 6_000_000
    private const val CODE_SNOOZE = 7_000_000
    private const val CODE_DISMISS = 8_000_000

    /**
     * Channel settings are frozen the first time a channel id is created, so the id carries a
     * version suffix: the original channel was created before sound and vibration were set,
     * and Android would have ignored those changes on the old id.
     */
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Activity and daily reminders"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 250, 350)
            enableLights(true)
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(channel)

        val alarms = NotificationChannel(
            CHANNEL_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen ringing alarms"
            // AlarmService owns the audio via MediaPlayer on STREAM_ALARM. If the channel also
            // had a sound the user would hear two things at once, out of sync.
            setSound(null, null)
            enableVibration(false)
            enableLights(true)
            setShowBadge(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(alarms)

        // Tidy up the pre-1.0 channel so the app settings screen isn't cluttered.
        runCatching { nm.deleteNotificationChannel("reminders") }
    }

    fun enabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(context: Context, id: Int, title: String, body: String) {
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        // POST_NOTIFICATIONS can be revoked at any time, and notify() throws without it.
        if (!postAllowed(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    private const val ROUTINE_NOTIFICATION_BASE = 3_000_000
    private const val CODE_ROUTINE_ACTION = 4_000_000
    private const val CODE_ROUTINE_OPEN = 5_000_000

    fun routineNotificationId(itemId: Long): Int = ROUTINE_NOTIFICATION_BASE + itemId.toInt()

    /**
     * A routine nudge. Meals open the Calories tab already on that meal, the planning item opens
     * tomorrow's plan, and everything else can be answered without opening the app at all.
     * [checkIn] is the follow-up after an alarm: same buttons, worded as a question.
     */
    fun showRoutine(context: Context, item: RoutineItem, date: String, checkIn: Boolean = false) {
        ensureChannels(context)
        val code = item.id.toInt()
        val (route, slot) = when (item.kindType) {
            RoutineKind.MEAL -> MainActivity.ROUTE_CALORIES to Slot.from(item.slot).name
            RoutineKind.PLAN -> MainActivity.ROUTE_PLAN to null
            else -> MainActivity.ROUTE_ROUTINE to null
        }
        val open = PendingIntent.getActivity(
            context, CODE_ROUTINE_OPEN + code,
            MainActivity.deepLink(context, route, slot),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun action(action: String, n: Int) = PendingIntent.getBroadcast(
            context, CODE_ROUTINE_ACTION + code * 4 + n,
            RoutineActions.intent(context, action, item.id, date),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val body = when {
            checkIn -> "Did you do it? One tap keeps the streak honest."
            item.kindType == RoutineKind.MEAL -> "Log ${item.title.lowercase()} so today's calories add up."
            item.kindType == RoutineKind.WALK -> "A short walk now. Steps in the next two hours count it for you."
            item.kindType == RoutineKind.PLAN -> "Set tomorrow's times - or keep the usual ones - before bed."
            else -> "Time for it. Mark it done when you have."
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${item.emoji} ${item.title}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)

        when (item.kindType) {
            RoutineKind.MEAL -> builder.addAction(R.drawable.ic_notification, "Log ${item.title.lowercase()}", open)
            RoutineKind.PLAN -> builder.addAction(R.drawable.ic_notification, "Plan tomorrow", open)
            else -> builder.addAction(R.drawable.ic_notification, "Done ✓", action(RoutineActions.ACTION_DONE, 0))
        }
        if (item.kindType != RoutineKind.PLAN) {
            builder.addAction(R.drawable.ic_notification, "Skip", action(RoutineActions.ACTION_SKIP, 1))
        }

        if (!postAllowed(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(routineNotificationId(item.id), builder.build()) }
    }

    fun showReminder(context: Context, id: Long, label: String) =
        show(context, id.toInt(), label, "Tap to open LifeTrack")

    /**
     * The ongoing notification [AlarmService] runs in the foreground with. The full-screen intent
     * is what gets [AlarmActivity] over the lock screen; the two actions make the alarm
     * controllable from the shade when the system decides to show a heads-up instead.
     */
    fun buildAlarmNotification(
        context: Context,
        id: Long,
        label: String,
        body: String,
        snoozeMinutes: Int
    ): Notification {
        ensureChannels(context)
        val code = id.toInt()
        val fullScreen = PendingIntent.getActivity(
            context, CODE_FULL_SCREEN + code,
            AlarmActivity.intent(context, id, label, snoozeMinutes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snooze = PendingIntent.getService(
            context, CODE_SNOOZE + code, AlarmService.snoozeIntent(context, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val dismiss = PendingIntent.getService(
            context, CODE_DISMISS + code, AlarmService.stopIntent(context, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(label)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)                     // the service plays the ringtone
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(R.drawable.ic_alarm, "Snooze ${snoozeMinutes}m", snooze)
            .addAction(R.drawable.ic_notification, "Dismiss", dismiss)
            .build()
    }

    /** True when notify() will not throw. Foreground-service notifications post regardless. */
    fun postAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
