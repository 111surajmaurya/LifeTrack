package com.lifetrack.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Habit
import com.lifetrack.app.data.HabitKind
import com.lifetrack.app.data.Session
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.AccentBadge
import com.lifetrack.app.ui.components.BarChart
import com.lifetrack.app.ui.components.CalendarLegend
import com.lifetrack.app.ui.components.EmptyState
import com.lifetrack.app.ui.components.HeroPanel
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ChartDetailSheet
import com.lifetrack.app.ui.components.MonthCalendar
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.SegmentedPicker
import com.lifetrack.app.ui.components.StatTile
import com.lifetrack.app.ui.components.TileRow
import com.lifetrack.app.ui.components.dayDetail
import com.lifetrack.app.ui.components.TrendChart
import com.lifetrack.app.ui.theme.MetricStyle
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents

/** Which window the charts are showing. */
private enum class Span(val label: String) { Week("Week"), Month("Month") }

@Composable
fun HabitDetailScreen(
    habitId: Long,
    onBack: () -> Unit,
    vm: HabitDetailViewModel = appViewModel { HabitDetailViewModel(it) }
) {
    LaunchedEffect(habitId) { vm.load(habitId) }

    val state by vm.state.collectAsStateWithLifecycle()
    val a = accents()
    var span by rememberSaveable { mutableStateOf(Span.Week) }
    var deleting by remember { mutableStateOf<Session?>(null) }

    val habit = state.habit
    val accent = a.byKey(habit?.accent ?: "leaf")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.width(Space.xs))
                if (habit != null) AccentBadge(habit.emoji, accent, size = 34.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        habit?.name ?: "Habit",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1
                    )
                    if (habit != null) {
                        Text(
                            goalLabel(habit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (habit == null) {
            item {
                if (state.ready) {
                    EmptyState(
                        symbol = "🔍",
                        title = "Habit not found",
                        body = "It was removed while you were looking at it."
                    )
                } else {
                    Spacer(Modifier.height(120.dp))
                }
            }
            return@LazyColumn
        }

        item { Hero(habit, state, accent) }

        item {
            SegmentedPicker(
                options = listOf(Span.Week, Span.Month),
                selected = span,
                onSelect = { span = it },
                label = { it.label },
                modifier = Modifier.fillMaxWidth(),
                accent = accent
            )
        }

        item { ChartCard(habit, state, span, accent, vm::selectDay) }

        item { CalendarCard(habit, state, accent, vm::shiftMonth, vm::selectDay) }

        item { StatsGrid(habit, state, accent) }

        item { SectionLabel("Recent sessions", Modifier.padding(top = Space.sm)) }

        if (state.sessions.isEmpty()) {
            item {
                LifeCard {
                    Text(
                        if (habit.kindType == HabitKind.COUNT)
                            "Counted habits keep a daily tally rather than sessions - the calendar above is the record."
                        else
                            "No sessions yet. Start the stopwatch on the Habits tab and the first one lands here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.sessions, key = { it.id }) { session ->
                SessionRow(session) { deleting = session }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this session?") },
            text = {
                Text(
                    "${Dates.formatDuration(session.millis)} on ${Dates.label(session.date)} " +
                        "comes off the total for that day."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteSession(session); deleting = null }) {
                    Text("Delete", color = a.negative)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun Hero(habit: Habit, state: HabitDetailUiState, accent: Color) {
    val a = accents()
    val progress = if (state.goal > 0) (state.todayValue / state.goal).toFloat() else 0f
    HeroPanel(accent = accent) {
        Text(
            "TODAY",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            amountLabel(habit, state.todayValue),
            style = MetricStyle,
            color = if (progress >= 1f) a.positive else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Goal ${goalLabel(habit)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (state.streak > 0) "🔥 ${state.streak} day streak" else "No streak yet",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.streak > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChartCard(
    habit: Habit,
    state: HabitDetailUiState,
    span: Span,
    accent: Color,
    onSelect: (String) -> Unit
) {
    val goal = state.goal.takeIf { it > 0 }

    LifeCard {
        SectionLabel(if (span == Span.Week) "Last 7 days" else Dates.monthTitle(state.anchor))
        Spacer(Modifier.height(Space.md))
        if (span == Span.Week) {
            BarChart(
                points = state.weekPoints,
                accent = accent,
                goal = goal,
                valueLabel = { amountLabel(habit, it) },
                selectedKey = state.selected,
                onSelect = { point -> onSelect(point.key) }
            )
        } else {
            TrendChart(
                points = state.monthPoints,
                accent = accent,
                goal = goal,
                selectedKey = state.selected,
                onSelect = { point -> onSelect(point.key) }
            )
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "Tap any day for the detail.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // The calendar below keeps its own inline selection; this sheet is for the charts, where
    // there is no room to say anything useful next to a bar.
    val points = if (span == Span.Week) state.weekPoints else state.monthPoints
    val selectedDay = state.selected
    val index = points.indexOfFirst { it.key == selectedDay }
    if (index >= 0 && selectedDay != null) {
        ChartDetailSheet(
            detail = dayDetail(
                points = points,
                index = index,
                accent = accent,
                format = { amountLabel(habit, it) },
                goal = goal
            ),
            // selectDay toggles, so re-selecting the same day is what clears it.
            onDismiss = { onSelect(selectedDay) }
        )
    }
}

@Composable
private fun CalendarCard(
    habit: Habit,
    state: HabitDetailUiState,
    accent: Color,
    onShiftMonth: (Long) -> Unit,
    onSelect: (String) -> Unit
) {
    LifeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShiftMonth(-1) }) {
                Icon(Icons.Rounded.ChevronLeft, "Previous month")
            }
            Text(
                Dates.monthTitle(state.anchor),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { onShiftMonth(1) }, enabled = !state.atCurrentMonth) {
                Icon(Icons.Rounded.ChevronRight, "Next month")
            }
        }
        Spacer(Modifier.height(Space.sm))
        MonthCalendar(
            monthAnchor = state.anchor,
            days = state.calendar,
            accent = accent,
            onDayClick = onSelect,
            selected = state.selected
        )
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalendarLegend(accent)
            Spacer(Modifier.weight(1f))
        }
        val picked = state.selected
        if (picked != null) {
            Spacer(Modifier.height(Space.md))
            Text(
                "${Dates.label(picked)}  ·  ${amountLabel(habit, state.selectedValue)} of ${goalLabel(habit)}",
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
    }
}

@Composable
private fun StatsGrid(habit: Habit, state: HabitDetailUiState, accent: Color) {
    val a = accents()
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        TileRow {
            StatTile(
                label = "Streak",
                value = "${state.streak}",
                accent = if (state.streak > 0) a.positive else accent,
                modifier = Modifier.weight(1f),
                footnote = if (state.streak == 1) "day" else "days"
            )
            StatTile(
                label = "Best",
                value = "${state.bestStreak}",
                accent = accent,
                modifier = Modifier.weight(1f),
                footnote = if (state.bestStreak == 1) "day" else "days"
            )
        }
        TileRow {
            StatTile(
                label = "Month total",
                value = amountLabel(habit, state.monthTotal),
                accent = accent,
                modifier = Modifier.weight(1f),
                footnote = Dates.monthTitle(state.anchor)
            )
            StatTile(
                label = "Daily average",
                value = amountLabel(habit, state.dailyAverage),
                accent = accent,
                modifier = Modifier.weight(1f),
                footnote = "per day so far"
            )
        }
    }
}

@Composable
private fun SessionRow(session: Session, onDelete: () -> Unit) {
    LifeCard(padding = PaddingValues(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Dates.formatDuration(session.millis), style = MaterialTheme.typography.titleMedium)
                Text(
                    Dates.whenLabel(session.endedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete, "Delete session",
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
