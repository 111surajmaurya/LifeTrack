package com.lifetrack.app.ui.alarms

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Habit
import com.lifetrack.app.data.Reminder
import com.lifetrack.app.data.ReminderMode
import com.lifetrack.app.data.RoutineItem
import com.lifetrack.app.reminders.ReminderScheduler
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.DotSeparated
import com.lifetrack.app.ui.components.EmptyState
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.ScreenPadding
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.SegmentedPicker
import com.lifetrack.app.ui.settings.AccessNeededCard
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents

@Composable
fun AlarmsScreen(
    onPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: AlarmsViewModel = appViewModel { AlarmsViewModel(it) }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = accents().alarms
    var draft by remember { mutableStateOf<Reminder?>(null) }
    var routineDraft by remember { mutableStateOf<RoutineItem?>(null) }

    // The user leaves for Settings to grant a permission and comes straight back: re-check then,
    // or the delivery card would keep showing the old answer.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshHealth(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenPadding, end = ScreenPadding, top = Space.xs, bottom = Space.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            ScreenHeader(
                title = "Alarms",
                subtitle = state.nextAny?.let { "Next ${Dates.untilLabel(it)}" }
                    ?: "Nothing scheduled",
                trailing = {
                    Button(onClick = { draft = blankReminder() }, shape = MaterialTheme.shapes.large) {
                        Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text("New")
                    }
                }
            )
        }

        if (state.health?.allGood == false) {
            item {
                AccessNeededCard("Some alarms will be late or silent - an alarm permission is off.", onOpenSettings)
            }
        }

        if (state.routine.isNotEmpty()) {
            item {
                RoutineCard(
                    rows = state.routine,
                    accent = accent,
                    onPlan = onPlan,
                    onToggle = { item, on -> vm.saveRoutineItem(context, item.copy(enabled = on)) },
                    onEdit = { routineDraft = it }
                )
            }
            item { SectionLabel("Your own alarms", Modifier.padding(top = Space.sm)) }
        }

        if (state.loaded && state.rows.isEmpty()) {
            item {
                EmptyState(
                    "⏰", "No extra alarms",
                    "Your daily routine is above. Add your own here and pick Alarm mode if you " +
                        "want it to ring, or Notification if a quiet nudge is enough."
                )
            }
        }

        items(state.rows, key = { it.reminder.id }) { row ->
            AlarmCard(
                row = row,
                countdown = row === state.soonest,
                accent = accent,
                onToggle = { vm.setEnabled(context, row.reminder, it) },
                onEdit = { draft = row.reminder },
                onDelete = { vm.delete(context, row.reminder) }
            )
        }
    }

    routineDraft?.let { editing ->
        RoutineEditor(
            initial = editing,
            accent = accent,
            onDismiss = { routineDraft = null },
            onSave = {
                vm.saveRoutineItem(context, it)
                routineDraft = null
            }
        )
    }

    draft?.let { editing ->
        AlarmEditor(
            initial = editing,
            habits = state.habits,
            accent = accent,
            onDismiss = { draft = null },
            onSave = {
                vm.save(context, it)
                draft = null
            }
        )
    }
}

// ---------------------------------------------------------------- delivery health

// ---------------------------------------------------------------- daily routine

/**
 * The fixed day. These are the *usual* times; one day's changes go through "Plan tomorrow",
 * which is also what the 10 PM notification opens.
 */
@Composable
private fun RoutineCard(
    rows: List<RoutineRow>,
    accent: Color,
    onPlan: () -> Unit,
    onToggle: (RoutineItem, Boolean) -> Unit,
    onEdit: (RoutineItem) -> Unit
) {
    LifeCard(accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Daily routine", Modifier.weight(1f))
            TextButton(onClick = onPlan) { Text("Plan tomorrow") }
        }
        Text(
            "Every day at these times unless tomorrow's plan says otherwise. Tap one to change its usual time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.sm))
        rows.forEach { row ->
            val item = row.item
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(item) }
                    .padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Dates.clockLabel(item.hour, item.minute),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (item.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(Space.sm))
                        ModeBadge(item.modeType, accent, item.enabled)
                    }
                    Text(
                        item.title + (row.nextAt?.let { "  ·  next ${Dates.whenLabel(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = item.enabled, onCheckedChange = { onToggle(item, it) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditor(
    initial: RoutineItem,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (RoutineItem) -> Unit
) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(initial) }
    val time = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text("${initial.emoji} ${initial.title}", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The usual time, every day. To move just tomorrow, use Plan tomorrow instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }
            SectionLabel("How it arrives")
            SegmentedPicker(
                options = ReminderMode.entries.toList(),
                selected = draft.modeType,
                onSelect = { draft = draft.copy(mode = it.name) },
                label = { it.label },
                accent = accent,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSave(draft.copy(hour = time.hour, minute = time.minute)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

// ---------------------------------------------------------------- one alarm

@Composable
private fun AlarmCard(
    row: AlarmRow,
    countdown: Boolean,
    accent: Color,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val r = row.reminder
    val live = r.enabled
    LifeCard(accent = if (live) accent else null, onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Dates.clockLabel(r.hour, r.minute),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (live) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(Space.sm))
                    ModeBadge(r.modeType, accent, live)
                }
                Text(
                    r.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (live) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                DotSeparated(
                    daysLabel(r.daysMask),
                    row.nextAt?.let { Dates.whenLabel(it) } ?: "Off"
                )
                if (countdown && row.nextAt != null) {
                    Text(
                        Dates.untilLabel(row.nextAt),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = r.enabled, onCheckedChange = onToggle)
                Row {
                    IconAction(Icons.Rounded.Edit, "Edit ${r.label}", onEdit)
                    IconAction(Icons.Rounded.DeleteOutline, "Delete ${r.label}", onDelete)
                }
            }
        }
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(Space.xs)) {
        Icon(icon, contentDescription = description, Modifier.size(20.dp))
    }
}

@Composable
private fun ModeBadge(mode: ReminderMode, accent: Color, live: Boolean) {
    val tint = if (mode == ReminderMode.ALARM) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = tint.copy(alpha = if (live) 0.16f else 0.08f)
    ) {
        Text(
            mode.label.uppercase(),
            Modifier.padding(horizontal = Space.sm, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

// ---------------------------------------------------------------- editor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    initial: Reminder,
    habits: List<Habit>,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(initial) }
    val time = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context)
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text(
                if (initial.id == 0L) "New alarm" else "Edit alarm",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it) },
                label = { Text("Label") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )

            if (habits.isNotEmpty()) {
                SectionLabel("Habit (optional)")
                ChipFlow {
                    FilterChip(
                        selected = draft.habitId == null,
                        onClick = { draft = draft.copy(habitId = null) },
                        label = { Text("None") }
                    )
                    habits.forEach { habit ->
                        FilterChip(
                            selected = draft.habitId == habit.id,
                            onClick = { draft = draft.copy(habitId = habit.id) },
                            label = { Text("${habit.emoji} ${habit.name}") }
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = time)
            }

            SectionLabel("Days")
            ChipFlow {
                Dates.weekdayInitials.forEachIndexed { index, initialLetter ->
                    val bit = 1 shl index
                    FilterChip(
                        selected = draft.daysMask and bit != 0,
                        onClick = { draft = draft.copy(daysMask = draft.daysMask xor bit) },
                        label = { Text(initialLetter) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TextButton(onClick = { draft = draft.copy(daysMask = EVERY_DAY) }) { Text("Every day") }
                TextButton(onClick = { draft = draft.copy(daysMask = WEEKDAYS) }) { Text("Weekdays") }
                TextButton(onClick = { draft = draft.copy(daysMask = WEEKENDS) }) { Text("Weekends") }
            }

            SectionLabel("How it arrives")
            SegmentedPicker(
                options = ReminderMode.entries.toList(),
                selected = draft.modeType,
                onSelect = { draft = draft.copy(mode = it.name) },
                label = { it.label },
                accent = accent,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (draft.modeType == ReminderMode.ALARM)
                    "Rings out loud and takes over the screen, even locked."
                else "A single notification. Nothing rings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (draft.modeType == ReminderMode.ALARM) {
                SectionLabel("Ring for")
                ChipFlow {
                    RING_CHOICES.forEach { seconds ->
                        FilterChip(
                            selected = draft.ringSeconds == seconds,
                            onClick = { draft = draft.copy(ringSeconds = seconds) },
                            label = { Text("${seconds}s") }
                        )
                    }
                }

                SectionLabel("Snooze")
                ChipFlow {
                    SNOOZE_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = draft.snoozeMinutes == minutes,
                            onClick = { draft = draft.copy(snoozeMinutes = minutes) },
                            label = { Text("$minutes min") }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibrate", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = draft.vibrate,
                        onCheckedChange = { draft = draft.copy(vibrate = it) }
                    )
                }
            }

            Button(
                onClick = {
                    val label = draft.label.trim().ifBlank { "Reminder" }
                    onSave(
                        draft.copy(
                            label = label,
                            hour = time.hour,
                            minute = time.minute,
                            // Saving a row with no days would leave a switched-on alarm that can
                            // never fire, so fall back to every day.
                            daysMask = draft.daysMask.takeIf { it != 0 } ?: EVERY_DAY
                        )
                    )
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Save", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Chips wrap onto as many lines as they need; FlowRow keeps the day strip on one line on phones. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    // FlowRowScope stays inside this helper: exposing it would make every caller opt in too.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) { content() }
}

// ---------------------------------------------------------------- helpers

private const val EVERY_DAY = 0b1111111
private const val WEEKDAYS = 0b0011111
private const val WEEKENDS = 0b1100000
private val RING_CHOICES = listOf(10, 20, 30, 60)
private val SNOOZE_CHOICES = listOf(5, 10, 15)
private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun blankReminder() = Reminder(
    label = "", hour = 7, minute = 0, daysMask = EVERY_DAY,
    mode = ReminderMode.ALARM.name, ringSeconds = 20, snoozeMinutes = 5, vibrate = true
)

private fun daysLabel(mask: Int): String = when (mask) {
    EVERY_DAY -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKENDS -> "Weekends"
    0 -> "No days"
    else -> (0..6).filter { mask and (1 shl it) != 0 }.joinToString(" ") { DAY_NAMES[it] }
}
