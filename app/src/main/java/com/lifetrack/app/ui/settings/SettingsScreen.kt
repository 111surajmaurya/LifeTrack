package com.lifetrack.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lifetrack.app.reminders.ReminderScheduler
import com.lifetrack.app.steps.HealthConnectSteps
import com.lifetrack.app.steps.HealthSync
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.ScreenPadding
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents
import kotlinx.coroutines.launch

/**
 * The one place LifeTrack asks for anything. Each access says what it is for, whether it is on,
 * and offers "Turn on" or "Manage" - Manage being the system page where it can be switched off,
 * because Android never lets an app take its own access away.
 *
 * On a fresh install the app opens here first ([firstRun]), so nothing pops up a permission
 * dialog before the user has seen what it is for.
 */
@Composable
fun SettingsScreen(
    firstRun: Boolean,
    onFinishSetup: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var states by remember { mutableStateOf<Map<Access, AccessState>>(emptyMap()) }
    fun refresh() = scope.launch { states = readAccess(context) }

    // Every grant happens in another screen (a system dialog, Settings, Health Connect), so the
    // truth is re-read whenever we come back rather than trusted from a callback.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val appDetails = { open(context, manageIntent(context, Access.PhysicalActivity)) }
    val notificationAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) open(context, ReminderScheduler.appNotificationSettingsIntent(context))
        refresh()
    }
    val activityAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Once denied twice Android stops showing the dialog; the app page is the only way left.
        if (!granted) appDetails()
        refresh()
    }
    val healthAsk = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { refresh() }

    fun turnOn(access: Access) {
        when (access) {
            Access.Notifications ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else open(context, ReminderScheduler.appNotificationSettingsIntent(context))
            Access.ExactAlarms -> open(context, ReminderScheduler.exactAlarmSettingsIntent(context))
            Access.FullScreen -> open(context, ReminderScheduler.fullScreenIntentSettingsIntent(context))
            Access.Battery -> open(context, ReminderScheduler.batterySettingsIntent(context))
            Access.HealthConnect ->
                if (states[access] == AccessState.UNAVAILABLE) open(context, HealthConnectSteps.installIntent())
                else healthAsk.launch(HealthSync.PERMISSIONS)
            Access.HealthBackground -> healthAsk.launch(setOf(HealthConnectSteps.BACKGROUND_PERMISSION))
            Access.PhysicalActivity ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityAsk.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else appDetails()
            Access.UsageAccess, Access.LimitBlocking -> open(context, manageIntent(context, access))
        }
    }

    val onCount = states.count { it.value == AccessState.ON }
    val possible = states.count { it.value != AccessState.UNAVAILABLE }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            ScreenHeader(
                title = if (firstRun) "Welcome" else "Settings",
                subtitle = if (states.isEmpty()) "Checking…" else "$onCount of $possible kinds of access on"
            )
        }

        if (firstRun) {
            item { WelcomeCard(onFinishSetup) }
        }

        Access.entries.groupBy { it.group }.forEach { (group, items) ->
            item(key = group) {
                LifeCard {
                    SectionLabel(group)
                    Spacer(Modifier.height(Space.xs))
                    items.forEachIndexed { index, access ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        AccessRow(
                            access = access,
                            state = states[access],
                            onTurnOn = { turnOn(access) },
                            onManage = { open(context, manageIntent(context, access)) }
                        )
                    }
                }
            }
        }

        item {
            LifeCard(onClick = onOpenProfile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Profile & goals")
                        Text(
                            "Height, weight and activity level - the calorie, protein and fibre goals come from these.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        item {
            Text(
                "Everything LifeTrack stores stays on this phone. Turning something off only stops " +
                    "the feature that needs it; the rest of the app keeps working.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.xs)
            )
        }
    }
}

@Composable
private fun WelcomeCard(onFinish: () -> Unit) {
    val a = accents()
    LifeCard(accent = a.positive) {
        Text("Set up LifeTrack", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Turn on only what you want. Each item below says what it is for, and every one can be " +
                "changed later from this Settings tab. Nothing is uploaded anywhere.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Space.md))
        Button(onClick = onFinish, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text("Done - go to Home")
        }
    }
}

@Composable
private fun AccessRow(access: Access, state: AccessState?, onTurnOn: () -> Unit, onManage: () -> Unit) {
    val a = accents()
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The title gives way (wraps) before the pill does, so "On" never breaks in half.
                Text(access.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(Space.sm))
                StatePill(state)
            }
            Text(
                access.purpose,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Space.sm))
        when (state) {
            AccessState.OFF -> FilledTonalButton(onClick = onTurnOn) { Text("Turn on") }
            AccessState.ON -> OutlinedButton(onClick = onManage) { Text("Manage") }
            AccessState.UNAVAILABLE ->
                if (access == Access.HealthConnect) FilledTonalButton(onClick = onTurnOn) { Text("Install") }
            null -> Unit
        }
    }
}

@Composable
private fun StatePill(state: AccessState?) {
    val a = accents()
    val (label, tint) = when (state) {
        AccessState.ON -> "On" to a.positive
        AccessState.OFF -> "Off" to a.negative
        AccessState.UNAVAILABLE -> "Not on this phone" to MaterialTheme.colorScheme.onSurfaceVariant
        null -> return
    }
    Surface(shape = MaterialTheme.shapes.extraSmall, color = tint.copy(alpha = 0.16f)) {
        Text(
            label,
            Modifier.padding(horizontal = Space.sm, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** OEM builds sometimes lack a Settings page; a missing one must not crash the app. */
private fun open(context: Context, intent: Intent?) {
    if (intent == null) return
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** A one-line pointer used by sections when something they need is switched off. */
@Composable
fun AccessNeededCard(message: String, onOpenSettings: () -> Unit) {
    val a = accents()
    LifeCard(accent = a.caution, onClick = onOpenSettings) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Space.sm))
            Text("Settings ›", style = MaterialTheme.typography.labelLarge, color = a.caution)
        }
    }
}
