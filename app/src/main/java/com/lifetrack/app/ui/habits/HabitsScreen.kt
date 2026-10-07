package com.lifetrack.app.ui.habits

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Habit
import com.lifetrack.app.data.HabitKind
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.AccentBadge
import com.lifetrack.app.ui.components.EmptyState
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ProgressBar
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.SegmentedPicker
import com.lifetrack.app.ui.components.StatTile
import com.lifetrack.app.ui.components.TileRow
import com.lifetrack.app.ui.components.WeekDots
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.StopwatchStyle
import com.lifetrack.app.ui.theme.StopwatchTailStyle
import com.lifetrack.app.ui.theme.accents
import kotlinx.coroutines.flow.StateFlow

/** What the per-habit overflow menu can ask for. */
private enum class RowAction { AddMinutes, Edit, ResetToday, Remove }

@Composable
fun HabitsScreen(
    onOpenHabit: (Long) -> Unit,
    vm: HabitsViewModel = appViewModel { HabitsViewModel(it) }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val a = accents()

    var sheetFor by remember { mutableStateOf<Habit?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var minutesFor by remember { mutableStateOf<Habit?>(null) }
    var resetFor by remember { mutableStateOf<Habit?>(null) }
    var removeFor by remember { mutableStateOf<Habit?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            ScreenHeader(
                title = "Habits",
                subtitle = when {
                    !state.ready -> "Warming up…"
                    state.rows.isEmpty() -> "Nothing tracked yet"
                    else -> "${Dates.formatDuration(state.trackedTodayMillis)} today  ·  " +
                        "${state.doneToday} of ${state.rows.size} done"
                },
                trailing = {
                    Button(onClick = { sheetFor = null; sheetOpen = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text("New")
                    }
                }
            )
        }

        if (notice != null) {
            item { NoticePill(notice.orEmpty(), a.positive, vm::dismissNotice) }
        }

        item {
            TileRow {
                StatTile(
                    label = "Done today",
                    value = "${state.doneToday}/${state.rows.size}",
                    accent = if (state.rows.isNotEmpty() && state.doneToday == state.rows.size)
                        a.positive else a.habits,
                    modifier = Modifier.weight(1f),
                    footnote = "habits"
                )
                TrackedTile(
                    banked = state.trackedTodayMillis,
                    runningSince = state.runningSince,
                    now = vm.now,
                    accent = a.activity,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Streak",
                    value = "${state.streakDays}",
                    accent = if (state.streakDays > 0) a.positive else a.habits,
                    modifier = Modifier.weight(1f),
                    footnote = if (state.streakDays == 1) "day" else "days"
                )
            }
        }

        if (state.ready && state.rows.isEmpty()) {
            item {
                EmptyState(
                    symbol = "🌱",
                    title = "No habits yet",
                    body = "Add one and it starts counting from today. Timed habits get a stopwatch, " +
                        "counted ones get a tap."
                )
            }
        }

        items(state.rows, key = { it.habit.id }) { row ->
            HabitCard(
                row = row,
                runningSince = state.runningSince.takeIf { state.runningHabitId == row.habit.id },
                now = vm.now,
                onOpen = { onOpenHabit(row.habit.id) },
                onStart = { vm.startTimer(row.habit.id) },
                onAddTime = { minutesFor = row.habit },
                onStop = vm::stopTimer,
                onReset = vm::resetTimer,
                onDiscard = vm::discardTimer,
                onBump = { delta -> vm.bumpCheck(row.habit.id, delta) },
                onAction = { action ->
                    when (action) {
                        RowAction.AddMinutes -> minutesFor = row.habit
                        RowAction.Edit -> { sheetFor = row.habit; sheetOpen = true }
                        RowAction.ResetToday -> resetFor = row.habit
                        RowAction.Remove -> removeFor = row.habit
                    }
                }
            )
        }

        if (state.archived.isNotEmpty()) {
            item { SectionLabel("Archived", Modifier.padding(top = Space.md)) }
            items(state.archived, key = { "archived-${it.id}" }) { habit ->
                ArchivedCard(habit) { vm.restore(habit) }
            }
        }

        // Clearance for the bottom navigation bar.
        item { Spacer(Modifier.height(72.dp)) }
    }

    if (sheetOpen) {
        HabitSheet(
            existing = sheetFor,
            onDismiss = { sheetOpen = false },
            onSave = { name, kind, goal, emoji, accent ->
                vm.save(sheetFor, name, kind, goal, emoji, accent)
                sheetOpen = false
            }
        )
    }

    minutesFor?.let { habit ->
        AddMinutesDialog(
            habit = habit,
            onDismiss = { minutesFor = null },
            onAdd = { minutes, date -> vm.addMinutes(habit.id, minutes, date); minutesFor = null }
        )
    }

    resetFor?.let { habit ->
        ConfirmDialog(
            title = "Reset today?",
            body = "Clears everything ${habit.name} banked today. Every other day stays exactly as it is.",
            confirmLabel = "Reset today",
            destructive = true,
            onConfirm = { vm.resetToday(habit); resetFor = null },
            onDismiss = { resetFor = null }
        )
    }

    removeFor?.let { habit ->
        ConfirmDialog(
            title = if (habit.seeded) "Archive ${habit.name}?" else "Remove ${habit.name}?",
            body = if (habit.seeded)
                "${habit.name} came with the app, so it is archived rather than deleted - all of its " +
                    "history is kept and you can restore it whenever you like."
            else
                "This deletes ${habit.name} along with every session and tick it has. That cannot be undone.",
            confirmLabel = if (habit.seeded) "Archive" else "Remove",
            destructive = !habit.seeded,
            onConfirm = { vm.remove(habit); removeFor = null },
            onDismiss = { removeFor = null }
        )
    }
}

// ---------------------------------------------------------------- summary

/**
 * The tracked-time tile is the only part of the strip that has to move while a timer runs,
 * so it - and nothing above it - subscribes to the 50ms clock.
 */
@Composable
private fun TrackedTile(
    banked: Long,
    runningSince: Long?,
    now: StateFlow<Long>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val tick by now.collectAsStateWithLifecycle()
    val live = banked + if (runningSince != null) runningToday(runningSince, tick) else 0L
    StatTile(
        label = "Tracked",
        value = Dates.formatDuration(live),
        accent = accent,
        modifier = modifier,
        footnote = if (runningSince != null) "running" else "today"
    )
}

@Composable
private fun NoticePill(message: String, accent: Color, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss)
    ) {
        Row(
            Modifier.padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message, Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge, color = accent
            )
            Icon(Icons.Rounded.Close, "Dismiss", Modifier.size(16.dp), tint = accent)
        }
    }
}

// ---------------------------------------------------------------- habit card

@Composable
private fun HabitCard(
    row: HabitRow,
    runningSince: Long?,
    now: StateFlow<Long>,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onAddTime: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onDiscard: () -> Unit,
    onBump: (Int) -> Unit,
    onAction: (RowAction) -> Unit
) {
    val a = accents()
    val accent = a.byKey(row.habit.accent)
    val habit = row.habit

    LifeCard(accent = if (row.done) a.positive else accent, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentBadge(habit.emoji, accent)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    goalLabel(habit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HabitMenu(habit, onAction)
        }

        Spacer(Modifier.height(Space.md))

        when {
            row.timed && runningSince != null ->
                RunningBlock(row, runningSince, now, accent, onReset, onDiscard, onStop)

            row.timed -> TimedIdleBlock(row, accent, onStart, onAddTime)
            else -> CountBlock(row, accent, onBump)
        }

        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeekDots(row.week, if (row.done) a.positive else accent)
            Spacer(Modifier.weight(1f))
            Text(
                "last 7 days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HabitMenu(habit: Habit, onAction: (RowAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (habit.kindType == HabitKind.TIMED) {
                DropdownMenuItem(
                    text = { Text("Add time") },
                    onClick = { open = false; onAction(RowAction.AddMinutes) }
                )
            }
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = { open = false; onAction(RowAction.Edit) }
            )
            DropdownMenuItem(
                text = { Text("Reset today") },
                onClick = { open = false; onAction(RowAction.ResetToday) }
            )
            DropdownMenuItem(
                // Seeded habits cannot be deleted, only put away - say so in the menu itself.
                text = { Text(if (habit.seeded) "Archive" else "Remove") },
                onClick = { open = false; onAction(RowAction.Remove) }
            )
        }
    }
}

@Composable
private fun TimedIdleBlock(row: HabitRow, accent: Color, onStart: () -> Unit, onAddTime: () -> Unit) {
    val a = accents()
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Stacked, so a long "16m 38s" still leaves room for both buttons.
        Column(Modifier.weight(1f)) {
            Text(
                Dates.formatDuration(row.millisToday),
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1
            )
            Text(
                "of ${Dates.formatMinutes(row.habit.dailyGoalMin)} today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        // Two ways in: run the stopwatch now, or type in time already done ("read for 20 min").
        OutlinedButton(onClick = onAddTime, contentPadding = PaddingValues(horizontal = Space.md)) {
            Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(Space.xs))
            Text("Time")
        }
        Spacer(Modifier.width(Space.sm))
        Button(onClick = onStart) { Text("Start") }
    }
    Spacer(Modifier.height(Space.md))
    ProgressBar(row.progress, if (row.done) a.positive else accent)
}

@Composable
private fun RunningBlock(
    row: HabitRow,
    since: Long,
    now: StateFlow<Long>,
    accent: Color,
    onReset: () -> Unit,
    onDiscard: () -> Unit,
    onStop: () -> Unit
) {
    val a = accents()
    val tick by now.collectAsStateWithLifecycle()
    val elapsed = (tick - since).coerceAtLeast(0L)
    val today = row.millisToday + runningToday(since, tick)
    val progress = if (row.goal > 0) (today / row.goal).toFloat() else 0f

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            Dates.stopwatch(elapsed),
            style = StopwatchStyle,
            color = accent,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            Dates.hundredths(elapsed),
            style = StopwatchTailStyle,
            color = accent.copy(alpha = 0.65f),
            modifier = Modifier.alignByBaseline()
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${Dates.formatDuration(today)} today",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline()
        )
    }

    Spacer(Modifier.height(Space.md))
    ProgressBar(progress, if (progress >= 1f) a.positive else accent)
    Spacer(Modifier.height(Space.md))

    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
        OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Discard") }
        Button(onClick = onStop, modifier = Modifier.weight(1.4f)) { Text("Stop & save") }
    }
}

@Composable
private fun CountBlock(row: HabitRow, accent: Color, onBump: (Int) -> Unit) {
    val a = accents()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${row.countToday}",
            style = MaterialTheme.typography.headlineMedium,
            color = if (row.done) a.positive else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            " / ${row.habit.dailyTarget}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline()
        )
        Spacer(Modifier.weight(1f))
        StepButton(Icons.Rounded.Remove, "One less", accent, row.countToday > 0) { onBump(-1) }
        Spacer(Modifier.width(Space.sm))
        StepButton(Icons.Rounded.Add, "One more", accent, true) { onBump(1) }
    }
    Spacer(Modifier.height(Space.md))
    ProgressBar(row.progress, if (row.done) a.positive else accent)
}

/** Round tap target for the counted stepper. Hand-rolled so it matches [AccentBadge]'s weight. */
@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(20.dp), tint = tint)
    }
}

@Composable
private fun ArchivedCard(habit: Habit, onRestore: () -> Unit) {
    LifeCard(padding = PaddingValues(horizontal = Space.lg, vertical = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentBadge(habit.emoji, MaterialTheme.colorScheme.onSurfaceVariant, size = 34.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    "History kept  ·  ${goalLabel(habit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRestore) { Text("Restore") }
        }
    }
}

// ---------------------------------------------------------------- dialogs and sheet

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val a = accents()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) a.negative else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Banks time done away from the stopwatch. Hours and minutes are typed, the common lengths are
 * one tap, and it can go on yesterday for a session you forgot to log before bed.
 */
@Composable
private fun AddMinutesDialog(habit: Habit, onDismiss: () -> Unit, onAdd: (Int, String) -> Unit) {
    val a = accents()
    val accent = a.byKey(habit.accent)
    val today = Dates.today()
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
    // A day only has so many minutes; anything past that is a typo.
    val valid = total in 1..MAX_ADD_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add time to ${habit.name}", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "For time you did without the timer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Minutes first: "read for 20 minutes" is the usual case.
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hours") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    )
                }
                listOf(5, 10, 15, 20, 30, 45, 60, 90).chunked(4).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        chunk.forEach { m ->
                            OutlinedButton(
                                onClick = { hours = (m / 60).takeIf { it > 0 }?.toString().orEmpty(); minutes = (m % 60).toString() },
                                contentPadding = PaddingValues(horizontal = Space.xs),
                                modifier = Modifier.weight(1f)
                            ) { Text(Dates.formatMinutes(m), maxLines = 1) }
                        }
                    }
                }
                SegmentedPicker(
                    options = listOf(today, Dates.shift(today, -1)),
                    selected = date,
                    onSelect = { date = it },
                    label = { Dates.label(it) },
                    accent = accent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(total, date) }, enabled = valid) {
                Text(if (valid) "Add ${Dates.formatMinutes(total)}" else "Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** 16 hours: more than that in one entry is almost certainly a typo. */
private const val MAX_ADD_MINUTES = 16 * 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitSheet(
    existing: Habit?,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: HabitKind, goal: Int, emoji: String, accent: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val a = accents()

    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember(existing) { mutableStateOf(existing?.kindType ?: HabitKind.TIMED) }
    var minutes by remember(existing) { mutableStateOf((existing?.dailyGoalMin ?: 30).toString()) }
    var target by remember(existing) { mutableStateOf((existing?.dailyTarget ?: 8).toString()) }
    var emoji by remember(existing) { mutableStateOf(existing?.emoji ?: HabitEmoji.first()) }
    var accentKey by remember(existing) { mutableStateOf(existing?.accent ?: HabitAccents.first().first) }

    val accent = a.byKey(accentKey)
    val timed = kind == HabitKind.TIMED
    val goalText = if (timed) minutes else target
    val goal = goalText.toIntOrNull() ?: 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            Text(
                if (existing == null) "New habit" else "Edit habit",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionLabel("How it is tracked")
                SegmentedPicker(
                    options = listOf(HabitKind.TIMED, HabitKind.COUNT),
                    selected = kind,
                    onSelect = { kind = it },
                    label = { if (it == HabitKind.TIMED) "Timed" else "Counted" },
                    modifier = Modifier.fillMaxWidth(),
                    accent = accent
                )
                Text(
                    if (timed) "A stopwatch you start and stop. Time banks in milliseconds."
                    else "One tap per rep. Water, pills, pages - anything you count.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = goalText,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(4)
                    if (timed) minutes = digits else target = digits
                },
                label = { Text(if (timed) "Minutes a day" else "Times a day") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionLabel("Icon")
                HabitEmoji.chunked(8).forEach { chunk ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        chunk.forEach { candidate ->
                            val on = candidate == emoji
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (on) accent.copy(alpha = 0.24f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .clickable { emoji = candidate },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(candidate, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionLabel("Colour")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    HabitAccents.forEach { (key, label) ->
                        val color = a.byKey(key)
                        val on = key == accentKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { accentKey = key }
                        ) {
                            Box(
                                Modifier
                                    .size(if (on) 36.dp else 28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (on) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onSave(name, kind, goal, emoji, accentKey) },
                enabled = name.isNotBlank() && goal > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (existing == null) "Add habit" else "Save changes")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", textAlign = TextAlign.Center)
            }
        }
    }
}
