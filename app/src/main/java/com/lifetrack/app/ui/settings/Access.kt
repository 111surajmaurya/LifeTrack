package com.lifetrack.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.lifetrack.app.reminders.Notifications
import com.lifetrack.app.reminders.ReminderScheduler
import com.lifetrack.app.screentime.LimitWatchService
import com.lifetrack.app.screentime.UsageReader
import com.lifetrack.app.steps.HealthConnectSteps
import com.lifetrack.app.steps.HealthSync
import com.lifetrack.app.steps.StepRecorder

/**
 * Every kind of access LifeTrack can ask for, in one list. The Settings tab is the only place
 * any of them is requested: sections just point here when something they need is off.
 */
enum class Access(val title: String, val purpose: String, val group: String) {
    Notifications(
        "Notifications", "Routine nudges, reminders and the missed-alarm note.", GROUP_ALARMS
    ),
    ExactAlarms(
        "Exact alarms", "Alarms ring on the minute instead of whenever the phone wakes.", GROUP_ALARMS
    ),
    FullScreen(
        "Full-screen alarms", "A ringing alarm takes over the lock screen instead of a small banner.", GROUP_ALARMS
    ),
    Battery(
        "Unrestricted battery", "Stops the phone from dropping alarms and the 15-minute step sync.", GROUP_ALARMS
    ),
    HealthConnect(
        "Health Connect", "Steps, distance, calories burned, heart rate and sleep from your phone or watch.", GROUP_ACTIVITY
    ),
    HealthBackground(
        "Health Connect in background", "Keeps steps updating every 15 minutes while LifeTrack is closed.", GROUP_ACTIVITY
    ),
    PhysicalActivity(
        "Physical activity", "Counts steps with the phone's own sensor when Health Connect isn't used.", GROUP_ACTIVITY
    ),
    UsageAccess(
        "Usage access", "Reads how long each tracked app was open today. Nothing else.", GROUP_SCREEN
    ),
    LimitBlocking(
        "Limit blocking", "Display over other apps: lets LifeTrack put the limit screen over a tracked app once its daily limit is used up.", GROUP_SCREEN
    );
}

const val GROUP_ALARMS = "Alarms & reminders"
const val GROUP_ACTIVITY = "Steps & activity"
const val GROUP_SCREEN = "Screen time"

enum class AccessState { ON, OFF, UNAVAILABLE }

/** What each access is right now. Health Connect needs a suspend call, hence the separate step. */
suspend fun readAccess(context: Context): Map<Access, AccessState> {
    val app = context.applicationContext
    fun on(b: Boolean) = if (b) AccessState.ON else AccessState.OFF
    val sync = HealthSync(app, (app as com.lifetrack.app.LifeTrackApp).repository)
    val hcStatus = sync.status()
    val hcReady = hcStatus == HealthConnectSteps.Status.AVAILABLE
    return mapOf(
        Access.Notifications to on(Notifications.enabled(app)),
        Access.ExactAlarms to on(ReminderScheduler.canScheduleExact(app)),
        Access.FullScreen to on(ReminderScheduler.canUseFullScreenIntent(app)),
        Access.Battery to on(ReminderScheduler.ignoringBatteryOptimizations(app)),
        Access.HealthConnect to when {
            !hcReady -> AccessState.UNAVAILABLE
            else -> on(sync.hasPermission())
        },
        Access.HealthBackground to when {
            !hcReady || !sync.backgroundSupported() -> AccessState.UNAVAILABLE
            else -> on(sync.backgroundAllowed())
        },
        Access.PhysicalActivity to on(StepRecorder.sensorGranted(app)),
        Access.UsageAccess to on(UsageReader.hasPermission(app)),
        Access.LimitBlocking to on(LimitWatchService.canShowOverApps(app))
    )
}

/**
 * Where to go to change [access] by hand - which is the only way to switch one *off*: Android
 * never lets an app revoke its own access, so "Manage" always lands on the system page for it.
 */
fun manageIntent(context: Context, access: Access): Intent? {
    val pkg = context.packageName
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    return when (access) {
        Access.Notifications -> ReminderScheduler.appNotificationSettingsIntent(context)
        Access.ExactAlarms -> ReminderScheduler.exactAlarmSettingsIntent(context) ?: appDetails
        Access.FullScreen -> ReminderScheduler.fullScreenIntentSettingsIntent(context) ?: appDetails
        Access.Battery -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        Access.HealthConnect, Access.HealthBackground -> HealthConnectSteps.openIntent()
        Access.PhysicalActivity -> appDetails
        Access.UsageAccess -> UsageReader.settingsIntent()
        Access.LimitBlocking -> LimitWatchService.overlaySettingsIntent(context)
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** First launch opens on Settings so a new user decides about access before anything asks. */
object FirstRun {
    private const val PREFS = "first_run"
    private const val KEY = "setup_done"

    fun needed(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun done(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
}
