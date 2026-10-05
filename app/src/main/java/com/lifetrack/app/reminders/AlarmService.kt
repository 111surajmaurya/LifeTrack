package com.lifetrack.app.reminders

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps an alarm ringing. A BroadcastReceiver would be reclaimed after about ten seconds, which
 * is not enough for a 20s ring, so the ringtone, the vibration and the wake lock all live here.
 *
 * Everything is driven by intent actions ([ACTION_START] / [ACTION_STOP] / [ACTION_SNOOZE]) so
 * the shade actions, the ringing screen and the receiver all talk to it the same way.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringJob: Job? = null
    private var foregroundStarted = false

    private var currentId = -1L
    private var currentLabel = DEFAULT_LABEL
    private var currentSnooze = 5
    /** Set when the ringing alarm is a routine item: the day it belongs to. */
    private var currentRoutineDate: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(ReminderScheduler.EXTRA_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_SNOOZE -> {
                guaranteeForeground()
                // A late tap for an alarm that was already rung out by a newer one must not
                // silence the newer one.
                if (!isStale(id)) snoozeThenStop(if (id >= 0) id else currentId)
            }
            ACTION_STOP -> {
                guaranteeForeground()
                if (!isStale(id)) {
                    val target = if (id >= 0) id else currentId
                    if (RoutineScheduler.isRoutineRing(target)) {
                        RoutineActions.alarmDismissed(
                            this, RoutineScheduler.itemIdOf(target), currentRoutineDate ?: Dates.today()
                        )
                    }
                    stop()
                }
            }
            else -> if (id >= 0) ring(intent!!, id) else stop()
        }
        // Nothing useful to restore if we are killed mid-ring: the alarm has already passed.
        return START_NOT_STICKY
    }

    /** True for a STOP/SNOOZE aimed at an alarm other than the one ringing now. */
    private fun isStale(id: Long) = id >= 0 && ringJob != null && id != currentId

    /**
     * Starts (or restarts) ringing. A second START for the same alarm replaces the first rather
     * than stacking; a START for a different one first rings the current one out, so it still
     * leaves its missed notification (or routine check-in) behind instead of vanishing.
     */
    private fun ring(intent: Intent, id: Long) {
        if (ringJob != null && currentId >= 0 && currentId != id) {
            ringOut("Missed - another alarm went off while this one was ringing.")
        }
        silence()

        currentId = id
        currentLabel = intent.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() } ?: DEFAULT_LABEL
        currentSnooze = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5).coerceIn(1, 120)
        val ringSeconds = intent.getIntExtra(EXTRA_RING_SECONDS, 20).coerceIn(5, 300)
        val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
        currentRoutineDate = intent.getStringExtra(EXTRA_ROUTINE_DATE)

        AlarmState.ringing.value = AlarmState.Ringing(id, currentLabel, currentSnooze)

        // The user's own alarm volume is theirs. If they have muted the stream we say so in the
        // notification instead of overriding it - a silent alarm they chose beats a shock.
        val muted = alarmStreamMuted()
        val body = if (muted) {
            "Alarm volume is at zero, so this one only vibrates. Tap to snooze or dismiss."
        } else {
            "Tap to snooze or dismiss."
        }

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID,
            Notifications.buildAlarmNotification(this, id, currentLabel, body, currentSnooze),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        foregroundStarted = true

        acquireWakeLock(ringSeconds)
        if (!muted) startRingtone()
        if (vibrate) startVibration()

        ringJob = scope.launch {
            delay(ringSeconds * 1000L)
            ringOut("Missed - the alarm rang for ${ringSeconds}s with no answer.")
            stop()
        }
    }

    /** What an unanswered alarm leaves behind. [missedBody] is for ordinary reminders. */
    private fun ringOut(missedBody: String) {
        if (RoutineScheduler.isRoutineRing(currentId)) {
            // A routine alarm that rang out still asks the question, so the tracker gets an answer.
            RoutineActions.alarmRangOut(
                this, RoutineScheduler.itemIdOf(currentId), currentRoutineDate ?: Dates.today()
            )
        } else {
            // Rang itself out: leave a plain notification behind so the miss is visible later.
            Notifications.show(this, currentId.toInt(), currentLabel, missedBody)
        }
    }

    private fun snoozeThenStop(id: Long) {
        silence()   // quiet first; the reminder lookup below can take a moment
        if (id < 0) {
            stop()
            return
        }
        if (RoutineScheduler.isRoutineRing(id)) {
            RoutineScheduler.snooze(this, RoutineScheduler.itemIdOf(id), currentRoutineDate ?: Dates.today())
            stop()
            return
        }
        val repo = (applicationContext as? LifeTrackApp)?.repository
        scope.launch {
            val reminder: Reminder? = withContext(Dispatchers.IO) { repo?.reminder(id) }
            if (reminder != null) ReminderScheduler.snooze(this@AlarmService, reminder)
            // Another alarm may have started ringing during the lookup; leave that one alone.
            if (ringJob == null) stop()
        }
    }

    /**
     * A START always calls startForeground, but STOP/SNOOZE can arrive at a service that was
     * only just created by startForegroundService (shade action after a process death). Android
     * kills us if that happens without a startForeground within a few seconds.
     */
    private fun guaranteeForeground() {
        if (foregroundStarted) return
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID,
            Notifications.buildAlarmNotification(
                this, currentId, currentLabel, "Stopping…", currentSnooze
            ),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        foregroundStarted = true
    }

    private fun stop() {
        silence()
        AlarmState.ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    /** Everything that makes noise or holds power, torn down. Safe to call repeatedly. */
    private fun silence() {
        ringJob?.cancel()
        ringJob = null
        player?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        player = null
        runCatching { vibrator()?.cancel() }
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    // ------------------------------------------------------------------ audio

    private fun alarmStreamMuted(): Boolean {
        val am = getSystemService(AudioManager::class.java) ?: return false
        return am.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }

    private fun startRingtone() {
        val uri = ringtoneUri() ?: return
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                // Player-relative volume only - the system alarm stream level stays untouched.
                setVolume(1f, 1f)
                prepare()
                start()
                player = this
            }
        }.onFailure {
            player?.let { p -> runCatching { p.release() } }
            player = null
        }
    }

    /** Alarm ringtone, falling back to the notification sound on devices with none set. */
    private fun ringtoneUri(): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    // -------------------------------------------------------------- vibration

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    @Suppress("DEPRECATION") // the VibrationAttributes overload is 33+; this one still works
    private fun startVibration() {
        val vib = vibrator() ?: return
        if (!vib.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)   // repeat from index 0
        runCatching {
            vib.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
    }

    // -------------------------------------------------------------- wake lock

    private fun acquireWakeLock(ringSeconds: Int) {
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = runCatching {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                // Timeout is a backstop: onDestroy releases it, but a crash must not leave the
                // CPU pinned awake forever.
                acquire(ringSeconds * 1000L + WAKE_LOCK_SLACK_MS)
            }
        }.getOrNull()
    }

    override fun onDestroy() {
        silence()
        AlarmState.ringing.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.lifetrack.app.alarm.START"
        const val ACTION_STOP = "com.lifetrack.app.alarm.STOP"
        const val ACTION_SNOOZE = "com.lifetrack.app.alarm.SNOOZE"

        const val EXTRA_LABEL = "alarm_label"
        const val EXTRA_RING_SECONDS = "alarm_ring_seconds"
        const val EXTRA_SNOOZE_MINUTES = "alarm_snooze_minutes"
        const val EXTRA_VIBRATE = "alarm_vibrate"
        const val EXTRA_ROUTINE_DATE = "alarm_routine_date"

        private const val DEFAULT_LABEL = "Alarm"
        private const val NOTIFICATION_ID = 424_242
        private const val WAKE_LOCK_TAG = "LifeTrack:alarm"
        private const val WAKE_LOCK_SLACK_MS = 10_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 700, 600)

        fun startIntent(context: Context, r: Reminder): Intent =
            startIntent(context, r.id, r.label, r.ringSeconds, r.snoozeMinutes, r.vibrate)

        fun startIntent(
            context: Context,
            id: Long,
            label: String,
            ringSeconds: Int,
            snoozeMinutes: Int,
            vibrate: Boolean,
            routineDate: String? = null
        ): Intent =
            Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(ReminderScheduler.EXTRA_ID, id)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_RING_SECONDS, ringSeconds)
                .putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
                .putExtra(EXTRA_VIBRATE, vibrate)
                .putExtra(EXTRA_ROUTINE_DATE, routineDate)

        fun stopIntent(context: Context, id: Long): Intent =
            Intent(context, AlarmService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(ReminderScheduler.EXTRA_ID, id)

        fun snoozeIntent(context: Context, id: Long): Intent =
            Intent(context, AlarmService::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(ReminderScheduler.EXTRA_ID, id)
    }
}
