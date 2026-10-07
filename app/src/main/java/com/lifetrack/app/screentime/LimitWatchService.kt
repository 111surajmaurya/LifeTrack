package com.lifetrack.app.screentime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.MainActivity
import com.lifetrack.app.R
import com.lifetrack.app.data.TrackedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Makes a screen-time limit an actual limit, with no more access than the screen-time numbers
 * already need: Usage access says which app is in front, and "Display over other apps" is what
 * lets LifeTrack put [LimitReachedActivity] up from the background. Nothing on screen is read.
 *
 * Android can't tell an ordinary app when another app opens, so the service has to look. It
 * looks only when a limit could matter ([LimitSchedule]): if the app with the least time left
 * has 20 minutes to go, nothing can run out for 20 minutes, so it sleeps that long. Once an app
 * is over its limit it looks every few seconds, to catch it being opened again. When it finds a
 * tracked app past its limit in front, the phone goes to the home screen and the limit screen
 * explains why. With the screen off nothing runs at all: the loop is cancelled on SCREEN_OFF
 * and started again on SCREEN_ON, and the first read after that catches up on what it missed.
 *
 * Android only lets a foreground service do this, hence the quiet, permanent notification
 * while any limit is set. Only the user can hide a foreground
 * service's notification, so it carries a "Hide" action that opens its switch in Android
 * settings ([hideNotificationIntent]); turning it off there leaves the blocking running.
 */
class LimitWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** The polling loop; only alive while the screen is on. */
    private var loop: Job? = null
    private var started = false
    private var limits: Map<String, TrackedApp> = emptyMap()

    /** The app in front, as of the last event read; [readUpTo] is where the next read starts. */
    private var front: String? = null
    private var frontActivity: String? = null
    private var readUpTo = 0L
    private var lastBlock = 0L

    /** Today's usage per limited app at the last full read, and when that was; null before the first. */
    private var lastUsed: Map<String, Long>? = null
    private var lastUsedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (e: Exception) {
            // A restart from the background after "Display over other apps" was switched off is
            // refused; give up quietly - the 15-minute job and the next app open call sync() again.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!UsageReader.hasPermission(this) || !canShowOverApps(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            val repo = (application as? LifeTrackApp)?.repository
            if (repo == null) { stopSelf(); return START_NOT_STICKY }
            readUpTo = System.currentTimeMillis() - LOOKBACK_MS
            scope.launch {
                repo.trackedApps.collect { apps ->
                    val next = apps.filter { it.dailyLimitMin > 0 }.associateBy { it.packageName }
                    val changed = next != limits
                    limits = next
                    if (!canRun(this@LimitWatchService, limits.isNotEmpty())) stopSelf()
                    // A new or lower limit can make the current long sleep too long: look again now.
                    else if (changed && loop?.isActive == true) { pause(); resume() }
                }
            }
            ContextCompat.registerReceiver(
                this, screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        if (getSystemService(PowerManager::class.java)?.isInteractive != false) resume()
        return START_STICKY
    }

    /** Screen on: watch. Screen off: nothing at all - no timer, no reads - until it comes back on. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> resume()
                Intent.ACTION_SCREEN_OFF -> pause()
            }
        }
    }

    private fun resume() {
        if (loop?.isActive == true) return
        loop = scope.launch { watch() }
    }

    private fun pause() {
        loop?.cancel()
        loop = null
    }

    private suspend fun watch() {
        val repo = (application as? LifeTrackApp)?.repository ?: return stopSelf()
        // Limits may have changed while the screen was off; the collector keeps them fresh after this.
        limits = repo.trackedApps.first().filter { it.dailyLimitMin > 0 }.associateBy { it.packageName }
        lastUsed = null   // the screen was off: usage moved on, read it fresh
        while (true) {
            if (!UsageReader.hasPermission(this) || !canShowOverApps(this)) { stopSelf(); return }
            val pkg = withContext(Dispatchers.IO) { advanceFront() }
            val app = pkg?.let { limits[it] }
            val before = lastUsed
            // Today's usage is read when a limited app is in front (it might be over), and
            // otherwise only as often as the schedule needs - not on every few-second look while
            // some other app is already over its limit.
            val stale = before == null || System.currentTimeMillis() - lastUsedAt >= LimitSchedule.MAX_MS
            val used = if (app != null || stale) {
                withContext(Dispatchers.IO) {
                    UsageReader.foregroundTodayMillis(this@LimitWatchService, limits.keys)
                }.also { lastUsed = it; lastUsedAt = System.currentTimeMillis() }
            } else before!!
            if (app != null) {
                val ms = used[app.packageName] ?: 0L
                if (ms >= app.dailyLimitMin * 60_000L) {
                    // Already over at the last look means it was just opened again; otherwise
                    // it ran out while in use.
                    val wasOver = before == null || (before[app.packageName] ?: 0L) >= app.dailyLimitMin * 60_000L
                    block(app, ms, opening = wasOver)
                }
            }
            val remaining = LimitSchedule.remaining(limits.mapValues { it.value.dailyLimitMin }, used)
            delay(LimitSchedule.nextDelay(remaining.values))
        }
    }

    /** Reads the events since the last poll and returns whichever app is now in front. */
    private fun advanceFront(): String? {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return front
        val now = System.currentTimeMillis()
        try {
            val events = usm.queryEvents(readUpTo, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    RESUMED -> { front = e.packageName; frontActivity = e.className }
                    // Only the screen in front leaving clears it: the screen an app just moved
                    // away from reports STOPPED after the next one has already resumed.
                    PAUSED -> if (e.packageName == front && e.className == frontActivity) front = null
                }
            }
            // Events are stamped when they happen but stored a moment later, so re-read a short
            // overlap; replaying a RESUMED or PAUSED twice changes nothing.
            readUpTo = now - OVERLAP_MS
        } catch (t: Throwable) {
            // Some OEM builds throw out of queryEvents now and then; the next poll retries.
        }
        return front
    }

    private fun block(app: TrackedApp, usedMs: Long, opening: Boolean) {
        val now = System.currentTimeMillis()
        // The limit screen takes a moment to come up; don't stack a second one on top of it.
        if (now - lastBlock < BLOCK_COOLDOWN_MS) return
        lastBlock = now
        // front is left alone: if going home didn't take, the next poll after the cooldown
        // tries again, and the launcher's own RESUMED replaces it when it did.
        runCatching { startActivity(homeIntent()) }
        Toast.makeText(
            this,
            if (opening) "LifeTrack stopped ${app.label} - daily time limit reached"
            else "LifeTrack is closing ${app.label} - daily time limit reached",
            Toast.LENGTH_LONG
        ).show()
        runCatching {
            startActivity(LimitReachedActivity.intent(this, app.label, usedMs / 60_000L, app.dailyLimitMin, opening))
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Screen time limits are on")
            .setContentText("LifeTrack closes a tracked app once its daily limit is used up.")
            .setContentIntent(open)
            .addAction(
                0, "Hide this notification",
                PendingIntent.getActivity(
                    this, 1, hideNotificationIntent(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        if (started) runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "limits_v1"
        private const val NOTIFICATION_ID = 7_100
        // An app opened up to this long before the service started is still caught.
        private const val LOOKBACK_MS = 6 * 60 * 60_000L
        private const val OVERLAP_MS = 5_000L
        private const val BLOCK_COOLDOWN_MS = 3_000L

        // Same raw event values as UsageReader: stable across versions.
        private const val RESUMED = 1
        private const val PAUSED = 2
        private const val STOPPED = 23

        /** "Display over other apps" - what lets the limit screen open while LifeTrack is closed. */
        fun canShowOverApps(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun overlaySettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /**
         * Whether the "limits are on" notification can appear at all: false once the user has
         * switched its channel (or every LifeTrack notification) off in Android settings.
         */
        fun notificationVisible(context: Context): Boolean {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return false
            if (!nm.areNotificationsEnabled()) return false
            val channel = nm.getNotificationChannel(CHANNEL) ?: return true   // created on first start
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }

        /** Android's page for just this notification, where the user can turn it off. */
        fun hideNotificationIntent(context: Context): Intent {
            ensureChannel(context)   // the page needs the channel to exist
            return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        private fun canRun(context: Context, anyLimit: Boolean) =
            anyLimit && UsageReader.hasPermission(context) && canShowOverApps(context)

        /**
         * Starts the watcher when there is something to enforce and both accesses are on, and
         * stops it otherwise. Safe to call from anywhere, as often as you like.
         */
        suspend fun sync(context: Context) {
            val app = context.applicationContext
            val repo = (app as? LifeTrackApp)?.repository ?: return
            val anyLimit = repo.trackedApps.first().any { it.dailyLimitMin > 0 }
            val intent = Intent(app, LimitWatchService::class.java)
            if (canRun(app, anyLimit)) {
                runCatching { ContextCompat.startForegroundService(app, intent) }
            } else {
                app.stopService(intent)
            }
        }

        private fun homeIntent() = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Screen time limits", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown while LifeTrack is enforcing your app limits"
                    setShowBadge(false)
                }
            )
        }
    }
}
