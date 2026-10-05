package com.lifetrack.app.ui.screentime

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.screentime.UsageSync
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.BarChart
import com.lifetrack.app.ui.components.CardGap
import com.lifetrack.app.ui.components.DeltaChip
import com.lifetrack.app.ui.components.EmptyState
import com.lifetrack.app.ui.components.HeroPanel
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ChartDetailSheet
import com.lifetrack.app.ui.components.OverflowBar
import com.lifetrack.app.ui.components.ScreenPadding
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.PeriodPicker
import com.lifetrack.app.ui.components.StatTile
import com.lifetrack.app.ui.components.TileRow
import com.lifetrack.app.ui.components.TrendChart
import com.lifetrack.app.ui.components.dayDetail
import com.lifetrack.app.ui.components.toDailyPoints
import com.lifetrack.app.ui.theme.MetricStyle
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents

/**
 * One app's story: what it costs today, what the week and the month look like, and — the point
 * of the screen — the pattern underneath. Bars that cross the limit are red, never green.
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    vm: AppDetailViewModel = appViewModel { AppDetailViewModel(it) }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val a = accents()

    LaunchedEffect(packageName) { vm.load(packageName) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showLimit by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<String?>(null) }

    // A week reads as M T W …; longer windows need the date to mean anything. Computed here
    // rather than inside the list item so the chart and its detail sheet share one series.
    val week = state.window.barred
    val points = remember(state.series, state.from, state.to, week) {
        state.series.toDailyPoints(
            from = state.from,
            to = state.to,
            labelFor = { date -> if (week) Dates.weekdayInitial(date) else Dates.shortDay(date) }
        )
    }

    val label = rememberAppLabel(packageName, state.label.ifBlank { packageName.substringAfterLast('.') })
    val accent = if (state.overToday) a.negative else a.screen

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(Space.xs))
                AppIcon(packageName, label, accent, size = 40.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                    Text(
                        packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        if (!state.permission) {
            item {
                LifeCard(accent = a.caution) {
                    SectionLabel("Usage access is off", color = a.caution)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "Today's number stops updating until usage access is allowed again. " +
                            "Everything already recorded stays.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Space.md))
                    FilledTonalButton(onClick = { context.startActivity(UsageSync.settingsIntent()) }) {
                        Text("Open usage access")
                    }
                }
            }
        }

        item {
            HeroPanel(accent = accent) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Today", Modifier.weight(1f))
                    if (state.hasDelta) DeltaChip(delta = state.deltaPct, higherIsBetter = false)
                }
                Spacer(Modifier.height(Space.xs))
                Text(Dates.formatMinutes(state.minutesToday), style = MetricStyle, color = accent)
                Text(
                    if (state.limitMin > 0) "of a ${Dates.formatMinutes(state.limitMin)} daily limit"
                    else "No daily limit set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.limitMin > 0) {
                    Spacer(Modifier.height(Space.md))
                    OverflowBar(state.minutesToday.toFloat(), state.limitMin.toFloat())
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        if (state.overToday)
                            "over by ${Dates.formatMinutes(state.minutesToday - state.limitMin)}"
                        else
                            "${Dates.formatMinutes(state.limitMin - state.minutesToday)} left today",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.overToday) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.hasPrevious) {
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "${state.window.label}: ${Dates.formatMinutes(state.windowTotal)} " +
                            "vs ${Dates.formatMinutes(state.previousTotal)} before",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Showing", Modifier.weight(1f))
                PeriodPicker(
                    options = DetailWindow.entries.toList(),
                    selected = state.window,
                    onSelect = {
                        selectedDay = null
                        vm.setWindow(it)
                    },
                    label = { it.label },
                    accent = a.screen
                )
            }
        }

        item {
            LifeCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Daily time", Modifier.weight(1f))
                    if (state.limitMin > 0) {
                        Text(
                            "limit ${Dates.formatMinutes(state.limitMin)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
                // Half a year of daily bars is a smear; the trend line stays readable at that width.
                if (state.window.days > 30) {
                    TrendChart(
                        points = points,
                        accent = a.screen,
                        goal = state.limitMin.takeIf { it > 0 }?.toDouble(),
                        selectedKey = selectedDay,
                        onSelect = { selectedDay = if (it.key == selectedDay) null else it.key }
                    )
                } else {
                    BarChart(
                        points = points,
                        accent = a.screen,
                        goal = state.limitMin.takeIf { it > 0 }?.toDouble(),
                        goalMeansGood = false,
                        valueLabel = { Dates.formatMinutes(it.toInt()) },
                        selectedKey = selectedDay,
                        onSelect = { selectedDay = if (it.key == selectedDay) null else it.key }
                    )
                }
                if (!state.hasHistory) {
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "No time recorded in this window yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { PatternCard(state) }

        item {
            LifeCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Daily limit")
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            if (state.limitMin > 0) Dates.formatMinutes(state.limitMin) else "Not set",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    if (state.app != null) {
                        TextButton(onClick = { showLimit = true }) { Text("Edit") }
                    }
                }
                if (state.app == null) {
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "This app is no longer tracked, so only its stored history is shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.hasHistory) item { RecentDaysCard(state) }

        item { CardGap() }
    }

    val index = points.indexOfFirst { it.key == selectedDay }
    if (index >= 0) {
        ChartDetailSheet(
            detail = dayDetail(
                points = points,
                index = index,
                accent = a.screen,
                format = { Dates.formatMinutes(it.toInt()) },
                goal = state.limitMin.takeIf { it > 0 }?.toDouble(),
                // A screen-time limit is a ceiling, so under it is the win.
                goalMeansGood = false
            ),
            onDismiss = { selectedDay = null }
        )
    }

    if (showLimit && state.app != null) {
        LimitDialog(
            label = label,
            current = state.limitMin,
            onDismiss = { showLimit = false },
            onSave = {
                vm.setLimit(it)
                showLimit = false
            }
        )
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun PatternCard(state: AppDetailState) {
    val a = accents()
    val p = state.pattern
    LifeCard {
        SectionLabel("Pattern")
        Spacer(Modifier.height(Space.md))
        if (p.recordedDays == 0) {
            EmptyState(
                "📈",
                "No pattern yet",
                "Once a few days are recorded, this shows the days you lean on this app the most."
            )
        } else {
            TileRow {
                StatTile(
                    label = "Daily avg",
                    value = Dates.formatMinutes(p.dailyAverageMin),
                    accent = a.screen,
                    footnote = "${p.recordedDays} day${if (p.recordedDays == 1) "" else "s"}",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Busiest",
                    value = p.busiestDay ?: "—",
                    accent = a.negative,
                    footnote = Dates.formatMinutes(p.busiestMin),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Quietest",
                    value = p.quietestDay ?: "—",
                    accent = a.positive,
                    footnote = Dates.formatMinutes(p.quietestMin),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Space.sm))
            TileRow {
                StatTile(
                    label = "Days over",
                    value = p.daysOverLimit.toString(),
                    accent = if (p.daysOverLimit > 0) a.negative else a.positive,
                    footnote = if (state.limitMin > 0) "past ${Dates.formatMinutes(state.limitMin)}" else "no limit set",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Best streak",
                    value = "${p.longestUnderStreak}d",
                    accent = a.positive,
                    footnote = "in a row under limit",
                    modifier = Modifier.weight(1f)
                )
            }
            if (p.recordedDays < 3) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Only ${p.recordedDays} day${if (p.recordedDays == 1) "" else "s"} recorded so far — " +
                        "the weekday pattern gets meaningful after a week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The last recorded days, newest first — the "was yesterday actually bad" check. */
@Composable
private fun RecentDaysCard(state: AppDetailState) {
    val a = accents()
    LifeCard {
        SectionLabel("Recent days")
        Spacer(Modifier.height(Space.sm))
        state.series
            .sortedByDescending { it.date }
            .take(7)
            .forEach { day ->
                val minutes = day.value.toInt()
                val over = state.limitMin > 0 && minutes > state.limitMin
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Dates.label(day.date),
                        Modifier.width(96.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    OverflowBar(
                        value = minutes.toFloat(),
                        goal = state.limitMin.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.weight(1f),
                        height = 6.dp
                    )
                    Spacer(Modifier.size(Space.md))
                    Text(
                        Dates.formatMinutes(minutes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (over) a.negative else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
    }
}
