package com.lifetrack.app.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.StepSplit
import com.lifetrack.app.steps.HealthConnectSteps.Status
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * The one place that brings steps up to date, called by the 15-minute [StepSyncJob], on app
 * start and from the Activity tab. Health Connect is preferred; the phone's own counter is used
 * when Health Connect isn't connected.
 *
 * The counter only knows "steps since boot". The old fallback counted from the first time the
 * app was opened each day, so a morning walk before opening it vanished. This keeps the last
 * reading instead (in its own preferences file, away from the settings row every screen
 * rewrites) and turns each new reading into "steps since the last one", spread over the hours
 * in between.
 */
object StepRecorder {

    private const val PREFS = "step_sensor"
    private const val KEY_COUNT = "last_count"
    private const val KEY_AT = "last_at"
    private const val KEY_BOOT = "boot_at"

    /** Wall clock minus uptime drifts a little with clock corrections; a reboot moves it far more. */
    private const val BOOT_TOLERANCE_MS = 60_000L

    private val lock = Mutex()

    /** Sync whatever source is available. Never throws; returns true if anything was written. */
    suspend fun syncNow(context: Context, repo: Repository, hourDays: Int = 2): Boolean {
        val app = context.applicationContext
        return runCatching {
            val sync = HealthSync(app, repo)
            val connected = sync.status() == Status.AVAILABLE && sync.hasPermission()
            if (connected) {
                val wrote = sync.syncToday()
                sync.refreshRecent(hourDays)
                sync.syncHours(hourDays)
                // Health Connect owns these hours. Forget the sensor's last reading so falling back
                // to it later counts from then, not from before Health Connect - which would add
                // every step since on top of the Health Connect numbers already stored.
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
                wrote
            } else {
                sampleSensor(app, repo)
            }
        }.getOrDefault(false)
    }

    /** One reading of the hardware counter, folded into today's hours. */
    suspend fun sampleSensor(context: Context, repo: Repository): Boolean = lock.withLock {
        if (!sensorGranted(context)) return@withLock false
        val sensor = StepSensor(context)
        if (!sensor.isAvailable) return@withLock false
        val count = sensor.readCumulative() ?: return@withLock false
        val now = System.currentTimeMillis()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastCount = prefs.getLong(KEY_COUNT, -1L)
        val lastAt = prefs.getLong(KEY_AT, 0L)
        // The counter restarts at boot. A drop shows that, but a long gap can hide it (300 before,
        // 1,200 after), so the boot time is compared too.
        val bootAt = now - SystemClock.elapsedRealtime()
        val lastBoot = prefs.getLong(KEY_BOOT, 0L)
        val rebooted = lastBoot > 0 && abs(bootAt - lastBoot) > BOOT_TOLERANCE_MS
        val parts = StepSplit.spread(StepSplit.delta(lastCount, count, rebooted), lastAt, now)
        repo.addSensorSteps(parts)
        // Saved after the write: a crash in between re-counts one window rather than losing it.
        prefs.edit().putLong(KEY_COUNT, count).putLong(KEY_AT, now).putLong(KEY_BOOT, bootAt).apply()
        parts.isNotEmpty()
    }

    fun sensorGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}
