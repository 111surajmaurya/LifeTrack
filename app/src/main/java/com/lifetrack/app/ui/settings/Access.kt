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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every kind of access LifeTrack can ask for, in one list. The Settings tab is the only place
 * any of them is requested: sections just point here when something they need is off.
 *
 * [purpose] is the one line under the title; [why] and [without] are what the info button
 * says - why Android makes LifeTrack ask, what it does with it, and what stops without it.
 */
enum class Access(
    val title: String,
    val purpose: String,
    val group: String,
    val why: String,
    val without: String
) {
    Notifications(
        "Notifications", "Routine nudges, reminders and the missed-alarm note.", GROUP_ALARMS,
        why = "Android only lets an app reach you outside itself through notifications. LifeTrack " +
            "uses them for your routine nudges (breakfast, the walk, \"plan tomorrow\"), the " +
            "reminders you create, and the note that tells you an alarm was missed.",
        without = "Alarms & reminders are switched off - nothing could reach you."
    ),
    ExactAlarms(
        "Exact alarms", "Alarms ring on the minute instead of whenever the phone wakes.", GROUP_ALARMS,
        why = "Since Android 12 an app has to be allowed to wake the phone at an exact minute. " +
            "LifeTrack uses the same kind of alarm a clock app does - set for 7:00, it rings at " +
            "7:00 - which battery saving is not allowed to postpone.",
        without = "Alarms & reminders are switched off: Android would deliver them whenever it " +
            "next woke up, sometimes many minutes late."
    ),
    FullScreen(
        "Full-screen alarms", "A ringing alarm takes over the lock screen instead of a small banner.", GROUP_ALARMS,
        why = "Lets a ringing alarm fill the screen, even on the lock screen, with big Snooze and " +
            "Dismiss buttons - the way your clock app does.",
        without = "Alarms still ring, but appear as a small banner at the top of the screen."
    ),
    Battery(
        "Unrestricted battery", "Stops the phone from dropping alarms and the 15-minute step sync.", GROUP_ALARMS,
        why = "Some phones stop apps in the background to save battery, and their alarms and " +
            "syncs with them. Unrestricted keeps LifeTrack on Android's list of apps allowed to " +
            "run. In the background it only does a short check every 15 minutes.",
        without = "Everything still works, but on some phones an alarm can be dropped and steps " +
            "only update when you open the app."
    ),
    HealthConnect(
        "Health Connect", "Steps, distance, calories burned, heart rate and sleep from your phone or watch.", GROUP_ACTIVITY,
        why = "Health Connect is Android's shared store for health data. LifeTrack only reads from " +
            "it - steps, distance, calories burned, exercise, heart rate and sleep, from whatever " +
            "your phone, watch or fitness app records - and never writes anything back. A phone " +
            "and a watch are not counted twice.",
        without = "Steps come from the phone's own step counter instead, if Physical activity is " +
            "on. With neither, Activity is switched off."
    ),
    HealthBackground(
        "Health Connect in background", "Keeps steps updating every 15 minutes while LifeTrack is closed.", GROUP_ACTIVITY,
        why = "Health Connect only answers apps that are open unless they are allowed to read in " +
            "the background. This lets the 15-minute sync keep today's steps current while " +
            "LifeTrack is closed.",
        without = "Steps still work; they update when you open LifeTrack."
    ),
    PhysicalActivity(
        "Physical activity", "Counts steps with the phone's own sensor when Health Connect isn't used.", GROUP_ACTIVITY,
        why = "Lets LifeTrack read the phone's built-in step counter, a chip that counts steps " +
            "using almost no battery. It is only used when Health Connect isn't connected.",
        without = "Steps need Health Connect instead. With neither, Activity is switched off."
    ),
    UsageAccess(
        "Usage access", "Reads how long each tracked app was open today. Nothing else.", GROUP_SCREEN,
        why = "Android keeps a record of which app was on screen and when. Usage access lets " +
            "LifeTrack add up today's time for the apps you choose to track. It cannot see what " +
            "you do inside an app, read your messages, or see which websites you visit.",
        without = "Screen time is switched off - there is no other way for an app to know how " +
            "long another app was open."
    ),
    LimitBlocking(
        "Limit blocking", "Display over other apps: lets LifeTrack put the limit screen over a tracked app once its daily limit is used up.", GROUP_SCREEN,
        why = "Display over other apps lets LifeTrack put its \"limit reached\" screen on top of " +
            "an app once you have used up the daily limit you set for it. That screen is all it " +
            "ever shows; nothing on your screen is read.",
        without = "Limit blocking is switched off: limits still turn the bar red, but the app is " +
            "not closed when you reach one."
    );
}

/**
 * A part of the app that cannot work without some access. While [missing] is non-empty the
 * feature is switched off: its section shows what to turn on instead of numbers it can't have.
 *
 * Every access in [required] must be on; of [anyOf], at least one. Access not named here
 * (full-screen alarms, battery, background Health Connect) only makes a feature work better.
 */
enum class Feature(
    val title: String,
    val group: String,
    val required: List<Access> = emptyList(),
    val anyOf: List<Access> = emptyList()
) {
    Alarms("Alarms & reminders", GROUP_ALARMS, required = listOf(Access.Notifications, Access.ExactAlarms)),
    Steps("Steps & activity", GROUP_ACTIVITY, anyOf = listOf(Access.HealthConnect, Access.PhysicalActivity)),
    ScreenTime("Screen time", GROUP_SCREEN, required = listOf(Access.UsageAccess)),
    LimitBlocking("Limit blocking", GROUP_SCREEN, required = listOf(Access.UsageAccess, Access.LimitBlocking));

    /**
     * What still has to be turned on, empty when the feature works. Null while access hasn't
     * been read yet, so a screen never flashes "switched off" on the way in.
     */
    fun missing(states: Map<Access, AccessState>): List<Access>? {
        if (states.isEmpty()) return null
        val off = required.filter { states[it] != AccessState.ON }
        val noneOfAny = anyOf.isNotEmpty() && anyOf.none { states[it] == AccessState.ON }
        return if (noneOfAny) off + anyOf else off
    }

    /** True only once access has been read and something is missing. */
    fun locked(states: Map<Access, AccessState>): Boolean = missing(states)?.isNotEmpty() == true

    /** "Usage access", "Notifications and Exact alarms", "Health Connect or Physical activity". */
    fun needs(missing: List<Access>): String {
        val names = missing.map { it.title }
        val joiner = if (missing.isNotEmpty() && missing.all { it in anyOf }) " or " else " and "
        return when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + joiner + names.last()
        }
    }

    companion object {
        /** Features [access] is essential to; empty means it only improves things. */
        fun needing(access: Access): List<Feature> =
            entries.filter { access in it.required || access in it.anyOf }

        fun lockedIn(states: Map<Access, AccessState>): Set<Feature> =
            entries.filter { it.locked(states) }.toSet()
    }
}

const val GROUP_ALARMS = "Alarms & reminders"
const val GROUP_ACTIVITY = "Steps & activity"
const val GROUP_SCREEN = "Screen time"

enum class AccessState { ON, OFF, UNAVAILABLE }

/**
 * The last reading of every access, shared by every screen so a feature locks and unlocks
 * everywhere at once. Refreshed whenever the app comes to the front (MainActivity.onResume)
 * and by the Settings tab after each change - every grant happens in another screen, so the
 * truth is re-read on return rather than trusted from a callback.
 */
object AccessStore {
    private val _states = MutableStateFlow<Map<Access, AccessState>>(emptyMap())
    val states: StateFlow<Map<Access, AccessState>> = _states.asStateFlow()

    suspend fun refresh(context: Context): Map<Access, AccessState> =
        readAccess(context).also { _states.value = it }
}

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
