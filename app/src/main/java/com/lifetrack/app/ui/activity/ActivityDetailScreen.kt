package com.lifetrack.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Metric
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.BarChart
import com.lifetrack.app.ui.components.DeltaChip
import com.lifetrack.app.ui.components.ChartDetailSheet
import com.lifetrack.app.ui.components.DotSeparated
import com.lifetrack.app.ui.components.EmptyState
import com.lifetrack.app.ui.components.HeroPanel
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ScreenHeader
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
 * One metric, in depth: today's number against the window before it, a chart over week / month /
 * six months, the numbers that summarise it, and the day-by-day history underneath.
 */
@Composable
fun ActivityDetailScreen(
    metric: Metric,
    onBack: () -> Unit,
    vm: ActivityDetailViewModel = appViewModel { ActivityDetailViewModel(it) }
) {
    LaunchedEffect(metric) { vm.load(metric) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                ScreenHeader(
                    title = ui.metric.label,
                    subtitle = "${ui.metric.emoji}  ${Dates.label(ui.to)}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item { DetailHero(ui) }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Showing", Modifier.weight(1f))
                PeriodPicker(
                    options = DetailWindow.entries,
                    selected = ui.window,
                    onSelect = vm::select,
                    label = { it.label },
                    accent = accents().activity
                )
            }
        }

        if (ui.isEmpty) {
            item {
                EmptyState(
                    symbol = ui.metric.emoji,
                    title = "Nothing recorded yet",
                    body = "Once ${ui.metric.label.lowercase()} shows up in Health Connect, this " +
                        "window will fill in. Try importing history from the Activity tab."
                )
            }
        } else {
            item { ChartCard(ui) }
            item { StatsBlock(ui) }
            item { SectionLabel("History", Modifier.padding(top = Space.sm)) }
            historyItems(ui)
        }
    }
}

// ---------------------------------------------------------------- hero

@Composable
private fun DetailHero(ui: ActivityDetailUi) {
    val a = accents()
    val metric = ui.metric
    val value = ui.today ?: 0.0
    val color = metric.tint(a, value, ui.settings)

    HeroPanel(color) {
        SectionLabel("Today")
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(metric.bare(value), style = MetricStyle, color = MaterialTheme.colorScheme.onSurface)
            val unit = metric.displayUnit()
            if (unit.isNotEmpty()) {
                Text(
                    "  $unit",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // higherIsBetter comes from the metric: a falling heart rate is the good direction.
            DeltaChip(ui.delta, higherIsBetter = metric.higherIsBetter)
            Text(
                "  vs previous ${ui.window.label.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ui.goal?.let { goal ->
            Spacer(Modifier.height(Space.xs))
            Text(
                if (value >= goal) "Goal of ${metric.pretty(goal)} met"
                else "${metric.pretty(goal - value)} to the goal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- chart

@Composable
private fun ChartCard(ui: ActivityDetailUi) {
    val a = accents()
    val metric = ui.metric
    val week = ui.window.barred
    val points = remember(ui.series, ui.window, metric) {
        ui.series.map { DayValue(it.date, metric.chartValue(it.value)) }
            .toDailyPoints(
                from = ui.from,
                to = ui.to,
                labelFor = if (week) Dates::weekdayInitial else Dates::shortDay
            )
    }
    val goal = metric.chartGoal(ui.settings)
    var selected by remember { mutableStateOf<String?>(null) }

    LifeCard {
        SectionLabel(ui.window.label)
        Spacer(Modifier.height(Space.md))
        if (week) {
            BarChart(
                points = points,
                accent = a.activity,
                goal = goal,
                valueLabel = { metric.chartLabel(it) },
                selectedKey = selected,
                onSelect = { selected = if (it.key == selected) null else it.key }
            )
        } else {
            TrendChart(
                points = points,
                accent = a.activity,
                goal = goal,
                selectedKey = selected,
                onSelect = { selected = if (it.key == selected) null else it.key }
            )
        }
        Spacer(Modifier.height(Space.sm))
        DotSeparated(
            "${ui.stats.daysWithData} days recorded",
            Dates.label(ui.from) + " → " + Dates.label(ui.to)
        )
    }

    val index = points.indexOfFirst { it.key == selected }
    if (index >= 0) {
        ChartDetailSheet(
            detail = dayDetail(
                points = points,
                index = index,
                accent = a.activity,
                // The chart works in display units already, so the label formatter is the
                // same one the bars use - no second conversion to get out of step with.
                format = { metric.chartLabel(it) },
                goal = goal,
                goalMeansGood = metric.higherIsBetter
            ),
            onDismiss = { selected = null }
        )
    }
}

// ---------------------------------------------------------------- stats

@Composable
private fun StatsBlock(ui: ActivityDetailUi) {
    val a = accents()
    val metric = ui.metric
    val stats = ui.stats
    val best = stats.best(metric)

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        TileRow {
            StatTile(
                label = "Average",
                value = metric.pretty(stats.average),
                accent = a.activity,
                modifier = Modifier.weight(1f),
                footnote = "per day recorded"
            )
            StatTile(
                label = if (metric.higherIsBetter) "Best day" else "Lowest",
                value = best?.let { metric.pretty(it.value) } ?: "—",
                accent = a.activity,
                modifier = Modifier.weight(1f),
                footnote = best?.let { Dates.label(it.date) }
            )
        }
        TileRow {
            // A total heart rate is meaningless, so that tile shows the other end of the range.
            if (metric == Metric.HeartRate) {
                StatTile(
                    label = "Highest",
                    value = stats.high?.let { metric.pretty(it.value) } ?: "—",
                    accent = a.activity,
                    modifier = Modifier.weight(1f),
                    footnote = stats.high?.let { Dates.label(it.date) }
                )
            } else {
                StatTile(
                    label = "Total",
                    value = metric.pretty(stats.total),
                    accent = a.activity,
                    modifier = Modifier.weight(1f),
                    footnote = ui.window.label.lowercase()
                )
            }
            if (ui.goal != null) {
                StatTile(
                    label = "At goal",
                    value = "${stats.daysAtGoal} days",
                    accent = if (stats.daysAtGoal > 0) a.positive else a.activity,
                    modifier = Modifier.weight(1f),
                    footnote = "of ${ui.window.days}"
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- history list

private fun LazyListScope.historyItems(ui: ActivityDetailUi) {
    val newestFirst = ui.series.asReversed()
    items(newestFirst.size, key = { newestFirst[it].date }) { index ->
        HistoryRow(ui, newestFirst[index])
    }
}

@Composable
private fun HistoryRow(ui: ActivityDetailUi, day: DayValue) {
    val a = accents()
    val goal = ui.goal
    val color = ui.metric.tint(a, day.value, ui.settings)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(Dates.label(day.date), style = MaterialTheme.typography.bodyLarge)
            if (goal != null && goal > 0) {
                Text(
                    "%.0f%% of goal".format(day.value / goal * 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            ui.metric.pretty(day.value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
