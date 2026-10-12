package com.lifetrack.app.ui.settings

import com.lifetrack.app.MainActivity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.layout.size
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
    val states by AccessStore.states.collectAsState()
    var explaining by remember { mutableStateOf<Access?>(null) }
    fun refresh() = scope.launch { AccessStore.refresh(context) }

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

        // One card per part of the app: what it powers and whether that is working, then each
        // access it uses - the required ones first.
        Access.entries.groupBy { it.group }.forEach { (group, items) ->
            item(key = group) {
                LifeCard {
                    SectionLabel(group)
                    Feature.entries.filter { it.group == group }.forEach { feature ->
                        FeatureLine(feature, feature.missing(states))
                    }
                    Spacer(Modifier.height(Space.xs))
                    items.sortedBy { Feature.needing(it).isEmpty() }.forEachIndexed { index, access ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        AccessRow(
                            access = access,
                            state = states[access],
                            onTurnOn = { turnOn(access) },
                            onManage = { open(context, manageIntent(context, access)) },
                            onInfo = { explaining = access }
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

        item { ExportCard() }

        item {
            Text(
                "Everything LifeTrack stores stays on this phone. Turning something off only stops " +
                    "the feature that needs it; the rest of the app keeps working.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.xs)
            )
            TextButton(onClick = { open(context, MainActivity.privacyPolicyIntent()) }) {
                Text("Privacy policy")
            }
        }
    }

    explaining?.let { access ->
        AccessInfoDialog(
            access = access,
            state = states[access],
            onTurnOn = { explaining = null; turnOn(access) },
            onDismiss = { explaining = null }
        )
    }
}

/** "Screen time · On", or "Screen time · Off - needs Usage access" in red. */
@Composable
private fun FeatureLine(feature: Feature, missing: List<Access>?) {
    val a = accents()
    val on = missing?.isEmpty() == true
    Text(
        when {
            missing == null -> feature.title
            on -> "${feature.title}  ·  On"
            else -> "${feature.title}  ·  Off - needs ${feature.needs(missing)}"
        },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = when {
            missing == null -> MaterialTheme.colorScheme.onSurfaceVariant
            on -> a.positive
            else -> a.negative
        },
        modifier = Modifier.padding(top = 2.dp)
    )
}

/** What the info button says: why it is asked for, what LifeTrack does with it, what stops without it. */
@Composable
private fun AccessInfoDialog(access: Access, state: AccessState?, onTurnOn: () -> Unit, onDismiss: () -> Unit) {
    val needing = Feature.needing(access)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text(access.title) },
        text = {
            Column {
                Text(
                    if (needing.isEmpty()) "Optional - makes ${access.group.lowercase()} more reliable."
                    else "Needed for ${needing.joinToString(" and ") { it.title }}.",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(Space.md))
                Text("Why it's needed", style = MaterialTheme.typography.titleSmall)
                Text(access.why, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Space.md))
                Text("Without it", style = MaterialTheme.typography.titleSmall)
                Text(access.without, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            if (state == AccessState.OFF) TextButton(onClick = onTurnOn) { Text("Turn on") }
            else TextButton(onClick = onDismiss) { Text("Got it") }
        },
        dismissButton = {
            if (state == AccessState.OFF) TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
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
private fun AccessRow(
    access: Access,
    state: AccessState?,
    onTurnOn: () -> Unit,
    onManage: () -> Unit,
    onInfo: () -> Unit
) {
    val needing = Feature.needing(access)
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The title gives way (wraps) before the pill does, so "On" never breaks in half.
                Text(access.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Why ${access.title} is needed",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatePill(state)
            }
            Text(
                access.purpose,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (needing.isEmpty()) "Optional"
                else "Needed for ${needing.joinToString(" and ") { it.title }}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
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

/** Whether [feature] is switched off right now. False until access has been read. */
@Composable
fun featureLocked(feature: Feature): Boolean {
    val states by AccessStore.states.collectAsState()
    return feature.locked(states)
}

/**
 * What a section shows instead of itself while [feature] is switched off: what it needs, why,
 * and the way to Settings. Sections show nothing else, so no screen pretends to have numbers
 * it cannot read.
 */
@Composable
fun FeatureOffCard(feature: Feature, onOpenSettings: () -> Unit) {
    val a = accents()
    val states by AccessStore.states.collectAsState()
    val missing = feature.missing(states).orEmpty()
    LifeCard(accent = a.negative) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = a.negative)
            Spacer(Modifier.width(Space.sm))
            Text("${feature.title} is switched off", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "It needs ${feature.needs(missing)}, which is off.",
            style = MaterialTheme.typography.bodyMedium
        )
        missing.forEach { access ->
            Spacer(Modifier.height(Space.sm))
            Text(access.title, style = MaterialTheme.typography.titleSmall)
            Text(
                access.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Space.md))
        Button(onClick = onOpenSettings, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text("Turn on in Settings")
        }
    }
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
