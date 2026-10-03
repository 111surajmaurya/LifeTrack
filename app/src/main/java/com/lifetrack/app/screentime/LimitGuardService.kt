package com.lifetrack.app.screentime

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.data.TrackedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Makes a screen-time limit an actual limit. Until this, a limit only turned a bar red.
 *
 * Android gives an ordinary app no way to stop another one, so this is an accessibility
 * service - the same mechanism Digital Wellbeing-style blockers use. It is kept as narrow as
 * the API allows: it listens only for "a window changed", only for the packages on the Screen
 * time list (set in [watch], refreshed whenever that list changes), and never reads content.
 *
 * When a tracked app comes to the front it checks today's foreground time. Over the limit:
 * back to the home screen and [LimitReachedActivity] explains why. Under it: a check is queued
 * for the moment the remaining time runs out, so scrolling past the limit is cut off too.
 */
class LimitGuardService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var limits: Map<String, TrackedApp> = emptyMap()
    private var pendingCheck: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val repo = (application as? LifeTrackApp)?.repository ?: return
        scope.launch {
            repo.trackedApps.collect { apps ->
                limits = apps.filter { it.dailyLimitMin > 0 }.associateBy { it.packageName }
                watch(limits.keys)
            }
        }
    }

    /** Narrow the event stream to just the tracked apps. Empty would mean "every app", so never send it. */
    private fun watch(packages: Set<String>) {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 500
        info.packageNames = packages.ifEmpty { setOf(packageName) }.toTypedArray()
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in limits) return
        check(pkg, opening = true)
    }

    /** [opening]: the app just came to the front, as opposed to the limit running out mid-use. */
    private fun check(pkg: String, opening: Boolean) {
        val app = limits[pkg] ?: return
        pendingCheck?.let(handler::removeCallbacks)
        pendingCheck = null
        scope.launch {
            val usedMs = withContext(Dispatchers.IO) { usedToday(pkg) }
            val limitMs = app.dailyLimitMin * 60_000L
            if (usedMs >= limitMs) {
                block(app, usedMs, opening)
            } else {
                // Re-check when the remaining time runs out - only acted on if it is still open.
                val again = Runnable {
                    scope.launch {
                        val open = withContext(Dispatchers.IO) { UsageReader.isForeground(this@LimitGuardService, pkg) }
                        if (open) check(pkg, opening = false)
                    }
                }
                pendingCheck = again
                handler.postDelayed(again, (limitMs - usedMs).coerceAtLeast(5_000L))
            }
        }
    }

    private suspend fun usedToday(pkg: String): Long {
        val live = UsageReader.foregroundTodayMillis(this, setOf(pkg))[pkg]
        if (live != null) return live
        // No usage access: fall back to the last snapshot the app stored (taken every 15 minutes).
        val repo = (application as? LifeTrackApp)?.repository ?: return 0
        return (repo.usageMinutesToday(pkg) ?: 0) * 60_000L
    }

    private fun block(app: TrackedApp, usedMs: Long, opening: Boolean) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        // Said immediately, before the explanation screen has drawn, so the close never looks
        // like a crash of the other app.
        Toast.makeText(
            this,
            if (opening) "LifeTrack stopped ${app.label} - daily time limit reached"
            else "LifeTrack is closing ${app.label} - daily time limit reached",
            Toast.LENGTH_LONG
        ).show()
        runCatching {
            startActivity(
                LimitReachedActivity.intent(this, app.label, usedMs / 60_000L, app.dailyLimitMin, opening)
            )
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pendingCheck?.let(handler::removeCallbacks)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Whether the user has switched the guard on in Settings > Accessibility. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, LimitGuardService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun settingsIntent() =
            android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
