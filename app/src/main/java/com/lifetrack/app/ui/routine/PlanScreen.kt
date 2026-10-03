package com.lifetrack.app.ui.routine

import android.text.format.DateFormat
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.RoutinePlan
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.ScreenPadding
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents

/**
 * The 10 PM job: look at tomorrow, move what needs moving, switch off what isn't happening,
 * save. Saving with nothing changed still counts - it is the act of deciding that matters, and
 * it is what ticks "Plan tomorrow" on the tracker. The usual times are the starting point.
 */
@Composable
fun PlanScreen(
    onBack: () -> Unit,
    vm: RoutineViewModel = routineViewModel()
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val rows = remember { mutableStateListOf<RoutinePlan>() }
    var editing by remember { mutableStateOf<Int?>(null) }
    val date = Dates.shift(ui.today, 1)

    // Seed once from what tomorrow currently looks like (a saved plan, or the defaults).
    LaunchedEffect(ui.loaded) {
        if (ui.loaded && rows.isEmpty()) {
            rows += ui.tomorrow.map { RoutinePlan(date, it.item.id, it.hour, it.minute, it.enabled) }
        }
    }
    val byId = ui.items.associateBy { it.id }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(Space.xs))
                ScreenHeader(
                    title = "Plan tomorrow",
                    subtitle = Dates.longLabel(date),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Text(
                "Tap a time to move it, or switch an item off for tomorrow only. Your usual times " +
                    "stay as they are - change those in Alarms → Daily routine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(rows.size) { index ->
            val row = rows[index]
            val item = byId[row.itemId] ?: return@items
            val moved = row.hour != item.hour || row.minute != item.minute
            LifeCard(accent = if (row.enabled) accents().habits else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append(if (item.isAlarm) "Alarm" else "Notification")
                                if (moved) append("  ·  usual ${Dates.clockLabel(item.hour, item.minute)}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { editing = index }, enabled = row.enabled) {
                        Text(
                            Dates.clockLabel(row.hour, row.minute),
                            fontWeight = if (moved) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Spacer(Modifier.width(Space.sm))
                    Switch(checked = row.enabled, onCheckedChange = { rows[index] = row.copy(enabled = it) })
                }
            }
        }

        item {
            Spacer(Modifier.height(Space.md))
            Button(
                onClick = {
                    vm.savePlan(context, date, rows.toList())
                    onBack()
                },
                enabled = rows.isNotEmpty(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Save plan", style = MaterialTheme.typography.titleMedium) }
            TextButton(
                onClick = {
                    rows.replaceAll { r -> byId[r.itemId]?.let { r.copy(hour = it.hour, minute = it.minute, enabled = it.enabled) } ?: r }
                },
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs)
            ) { Text("Reset to usual times") }
        }
    }

    editing?.let { index ->
        val row = rows[index]
        TimeDialog(
            hour = row.hour,
            minute = row.minute,
            onDismiss = { editing = null },
            onPick = { h, m ->
                rows[index] = row.copy(hour = h, minute = m)
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(hour, minute, DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}
