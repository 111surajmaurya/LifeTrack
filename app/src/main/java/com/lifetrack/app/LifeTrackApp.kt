package com.lifetrack.app

import android.app.Application
import com.lifetrack.app.data.AppDatabase
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.TrackingStart
import com.lifetrack.app.reminders.Notifications
import com.lifetrack.app.reminders.ReminderScheduler
import com.lifetrack.app.screentime.LimitWatchService
import com.lifetrack.app.screentime.UsageSync
import com.lifetrack.app.reminders.RoutineScheduler
import com.lifetrack.app.steps.StepRecorder
import com.lifetrack.app.steps.StepSyncJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LifeTrackApp : Application() {
    val repository: Repository by lazy { Repository(AppDatabase.get(this)) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)

        scope.launch {
            repository.ensureSeeded()
            // Nothing from before this install counts; drop anything that was imported from earlier.
            val start = TrackingStart.date(this@LifeTrackApp)
            repository.pruneBeforeStart(start)

            // Six months is the window; anything older goes. See Retention and the README.
            repository.prune()

            // Alarms can be lost after a force-stop or an app update, and the boot receiver
            // only covers reboots, so re-arm everything enabled on every cold start.
            runCatching {
                ReminderScheduler.rescheduleAll(this@LifeTrackApp, repository.enabledReminders())
            }
            runCatching { RoutineScheduler.rescheduleAll(this@LifeTrackApp, repository) }

            // Steps and screen time keep updating every 15 minutes while the app is closed.
            StepSyncJob.schedule(this@LifeTrackApp)

            // Snapshot today's numbers so Home has something to show before any tab is opened.
            // Both are no-ops without their permission and neither throws.
            runCatching { UsageSync.snapshotToday(this@LifeTrackApp, repository) }
            runCatching { StepRecorder.syncNow(this@LifeTrackApp, repository) }
        }

        // Setting or clearing a limit starts or stops the limit watcher.
        scope.launch {
            repository.trackedApps
                .map { apps -> apps.any { it.dailyLimitMin > 0 } }
                .distinctUntilChanged()
                .collect { runCatching { LimitWatchService.sync(this@LifeTrackApp) } }
        }
    }
}
