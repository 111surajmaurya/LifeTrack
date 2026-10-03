package com.lifetrack.app.steps

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.screentime.UsageSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps steps (and screen time) moving while LifeTrack is closed. Before this, both only
 * updated when a screen was opened.
 *
 * Plain [JobScheduler], which is part of Android itself - no library. Fifteen minutes is the
 * shortest period Android allows; in Doze it stretches to the next maintenance window, which
 * is fine because the step counter keeps counting in between and the next reading catches up.
 * Persisted, so it survives a reboot (RECEIVE_BOOT_COMPLETED is already declared for alarms).
 */
class StepSyncJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val repo = (application as? LifeTrackApp)?.repository ?: return false
        work = scope.launch {
            try {
                StepRecorder.syncNow(this@StepSyncJob, repo)
                runCatching { UsageSync.snapshotToday(this@StepSyncJob, repo) }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true   // try again at the next window
    }

    companion object {
        private const val JOB_ID = 4_242

        /** Idempotent: leaves an already-scheduled job alone so its clock isn't reset every launch. */
        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, StepSyncJob::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(job) }
        }
    }
}
