package com.lifetrack.app.ui.activity

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Metric
import com.lifetrack.app.steps.HealthConnectSteps
import com.lifetrack.app.steps.HealthSync
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.BarChart
import com.lifetrack.app.ui.components.ChartPoint
import com.lifetrack.app.ui.components.DotSeparated
import com.lifetrack.app.ui.components.EmptyState
import com.lifetrack.app.ui.components.HeroPanel
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ProgressRing
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.ScreenPadding
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.StatTile
import com.lifetrack.app.ui.components.TileRow
import com.lifetrack.app.ui.components.toDailyPoints
import com.lifetrack.app.ui.settings.AccessNeededCard
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents

/**
 * The Activity tab: today's steps as a ring, the other metrics Health Connect shares as tiles,
 * and the last seven days as bars.
 *
 * Health Connect permissions are granted inside Health Connect's own UI, so this screen refreshes
 * on every resume rather than trusting a result callback.
 */
@Composable
fun ActivityScreen(
    onOpenMetric: (Metric) -> Unit,
    onOpenSettings: () -> Unit,
    vm: ActivityViewModel = appViewModel { ActivityViewModel(it) }
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var launchError by remember { mutableStateOf<String?>(null) }

    val healthPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { vm.refresh(context) }

    val sensorPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refresh(context) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh(context) }

    val link = ui.link
    val open: (Intent) -> Unit = { intent ->
        launchError = try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "Nothing on this phone can open that. Install Health Connect from the Play Store."
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenPadding, end = ScreenPadding, bottom = Space.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            ScreenHeader(
                title = "Activity",
                subtitle = sourceLine(ui)
            )
        }

        (link.error ?: launchError)?.let { message ->
            item { ProblemCard(message, onDismiss = { launchError = null; vm.dismissMessage() }) }
        }

        // Access is asked for in Settings; here we only say what is missing and point there.
        if (link.checked && !link.connected && !(link.sensorPresent && link.sensorGranted)) {
            item {
                AccessNeededCard(
                    "Steps need Health Connect or Physical activity access.",
                    onOpenSettings
                )
            }
        } else if (link.connected && link.backgroundMissing) {
            item {
                AccessNeededCard(
                    "Allow Health Connect in background so steps update while LifeTrack is closed.",
                    onOpenSettings
                )
            }
        }

        if (ui.hasAnyData) {
            item { StepsHero(ui) }
            item { HoursCard(ui, onShift = vm::shiftHourDay) }
            if (ui.tiles.isNotEmpty()) item { MetricTiles(ui, onOpenMetric) }
            item { WeekCard(ui, onOpenMetric) }
        } else {
            item {
                EmptyState(
                    symbol = "👣",
                    title = "No activity yet",
                    body = "Connect Health Connect - or let LifeTrack use the phone's step counter - " +
                        "and today's steps will appear here."
                )
            }
        }

        if (link.connected) item { HistoryCard(ui, onImport = { vm.importHistory(context) }, onOpen = open) }
    }
}

private fun sourceLine(ui: ActivityUi): String = when {
    !ui.link.checked -> "Checking…"
    ui.link.connected -> "Synced from Health Connect"
    ui.stepSource == "SENSOR" -> "Counted by this phone"
    ui.link.status != HealthConnectSteps.Status.AVAILABLE -> "Health Connect unavailable"
    else -> "Not connected"
}

// ---------------------------------------------------------------- hero

@Composable
private fun StepsHero(ui: ActivityUi) {
    val a = accents()
    val met = ui.steps >= ui.stepGoal
    val color = if (met) a.positive else a.activity

    HeroPanel(color) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Steps", Modifier.weight(1f))
            Text(
                Dates.label(ui.data.today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Space.lg))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = ui.progress,
                color = color,
                centerText = "%,.0f".format(ui.steps),
                caption = "of %,d".format(ui.stepGoal),
                overflow = (ui.progress - 1f).coerceIn(0f, 1f),
                below = {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        if (met) "Goal met" else "%,d to go".format(ui.stepsLeft),
                        style = MaterialTheme.typography.labelLarge,
                        color = color
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------- tiles

@Composable
private fun MetricTiles(ui: ActivityUi, onOpenMetric: (Metric) -> Unit) {
    val a = accents()
    val settings = ui.data.settings
    val rows = remember(ui.tiles) { ui.tiles.chunked(2) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        rows.forEach { row ->
            TileRow {
                row.forEach { metric ->
                    val value = ui.valueOf(metric) ?: 0.0
                    StatTile(
                        label = metric.shortLabel,
                        value = metric.pretty(value),
                        accent = metric.tint(a, value, settings),
                        modifier = Modifier.weight(1f),
                        footnote = metric.storedGoal(settings)?.let { "goal ${metric.pretty(it)}" },
                        onClick = { onOpenMetric(metric) }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- week chart

@Composable
private fun WeekCard(ui: ActivityUi, onOpenMetric: (Metric) -> Unit) {
    val a = accents()
    val goal = ui.stepGoal.toDouble()
    val points = remember(ui.data.weekSteps, ui.data.today) {
        ui.data.weekSteps.toDailyPoints(ui.data.weekFrom, ui.data.today)
    }
    val withData = points.filter { it.value > 0 }
    val average = if (withData.isEmpty()) 0.0 else withData.sumOf { it.value } / withData.size
    val best = points.maxOfOrNull { it.value } ?: 0.0
    val hits = points.count { it.value >= goal }

    LifeCard(onClick = { onOpenMetric(Metric.Steps) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Last 7 days", Modifier.weight(1f))
            Text(
                "Details ›",
                style = MaterialTheme.typography.labelMedium,
                color = a.activity
            )
        }
        Spacer(Modifier.height(Space.md))
        BarChart(points = points, accent = a.activity, goal = goal)
        Spacer(Modifier.height(Space.sm))
        DotSeparated(
            "Avg %,.0f".format(average),
            "Best %,.0f".format(best),
            "$hits of 7 at goal"
        )
    }
}

// ---------------------------------------------------------------- steps by hour

/**
 * Twenty-four bars, one per clock hour: *when* the walking happened, not just how much.
 * The arrows walk back through earlier days; there is no tap-for-detail here because every
 * other chart's detail sheet is keyed by date, and these bars are hours.
 */
@Composable
private fun HoursCard(ui: ActivityUi, onShift: (Long) -> Unit) {
    val a = accents()
    val day = ui.data.hourDay
    val byHour = remember(ui.data.hours) { ui.data.hours.associate { it.hour to it.steps } }
    val points = remember(byHour) {
        (0..23).map { h ->
            ChartPoint(key = "h$h", label = "", value = byHour[h] ?: 0.0)
        }
    }
    val total = byHour.values.sum()
    val peak = byHour.maxByOrNull { it.value }
    val activeHours = byHour.count { it.value >= ACTIVE_HOUR_STEPS }

    LifeCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("When you walked", Modifier.weight(1f))
            TextButton(onClick = { onShift(-1) }) { Text("‹") }
            Text(
                Dates.label(day),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { onShift(1) }, enabled = !Dates.isToday(day)) { Text("›") }
        }
        Spacer(Modifier.height(Space.sm))
        if (total <= 0.0) {
            Text(
                "No steps recorded by the hour for this day. Hours are kept from this version on, " +
                    "and filled in from Health Connect when it is connected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            BarChart(points = points, accent = a.activity, height = 120.dp)
            // The bars are too narrow for their own labels, so the axis is drawn once, every 6 hours.
            Row(Modifier.fillMaxWidth()) {
                listOf(0, 6, 12, 18).forEach { h ->
                    Text(
                        hourTick(h),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
            DotSeparated(
                "%,.0f steps".format(total),
                peak?.let { "Most at ${hourRange(it.key)}" } ?: "",
                "$activeHours active ${if (activeHours == 1) "hour" else "hours"}"
            )
        }
    }
}

/** An hour with this many steps counts as an "active" one in the summary line. */
private const val ACTIVE_HOUR_STEPS = 250.0

private fun hourTick(h: Int): String = when (h) {
    0 -> "12a"
    12 -> "12p"
    else -> if (h < 12) "${h}a" else "${h - 12}p"
}

private fun hourRange(h: Int): String = "${Dates.clockLabel(h, 0)}–${Dates.clockLabel((h + 1) % 24, 0)}"

// ---------------------------------------------------------------- connection

@Composable
private fun ProblemCard(message: String, onDismiss: () -> Unit) {
    val a = accents()
    LifeCard(accent = a.negative) {
        SectionLabel("Couldn't read your activity", color = a.negative)
        Spacer(Modifier.height(Space.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.sm))
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

// ---------------------------------------------------------------- history import

@Composable
private fun HistoryCard(ui: ActivityUi, onImport: () -> Unit, onOpen: (Intent) -> Unit) {
    val a = accents()
    val link = ui.link

    LifeCard {
        SectionLabel("History")
        Spacer(Modifier.height(Space.xs))
        Text(
            if (link.storedFrom != null) {
                "Stored from ${Dates.label(link.storedFrom)} - ${link.storedDays} days of steps."
            } else {
                "Nothing stored yet. Importing pulls up to six months out of Health Connect."
            },
            style = MaterialTheme.typography.bodyMedium
        )

        if (link.missing.isNotEmpty()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "Not shared yet: ${link.missing.joinToString { it.shortLabel.lowercase() }}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Space.md))
        if (link.importing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = a.activity)
                Spacer(Modifier.width(Space.sm))
                Text(
                    "Importing six months…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilledTonalButton(onClick = onImport) { Text("Import history") }
                TextButton(onClick = { onOpen(HealthConnectSteps.openIntent()) }) {
                    Text("Health Connect")
                }
            }
        }

        link.importMessage?.let {
            Spacer(Modifier.height(Space.sm))
            Text(it, style = MaterialTheme.typography.bodySmall, color = a.positive)
        }
    }
}
