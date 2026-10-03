package com.lifetrack.app.ui.calories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Slot
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.BarChart
import com.lifetrack.app.ui.components.BreakdownList
import com.lifetrack.app.ui.components.CardGap
import com.lifetrack.app.ui.components.ChartDetailSheet
import com.lifetrack.app.ui.components.ChartFact
import com.lifetrack.app.ui.components.DeltaChip
import com.lifetrack.app.ui.components.DonutChart
import com.lifetrack.app.ui.components.EmptyState
import com.lifetrack.app.ui.components.HeroPanel
import com.lifetrack.app.ui.components.HistoryEntry
import com.lifetrack.app.ui.components.HistoryList
import com.lifetrack.app.ui.components.HistoryMetric
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.OverflowBar
import com.lifetrack.app.ui.components.PeriodPicker
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.Slice
import com.lifetrack.app.ui.components.StatTile
import com.lifetrack.app.ui.components.TileRow
import com.lifetrack.app.ui.components.TrendChart
import com.lifetrack.app.ui.components.dayDetail
import com.lifetrack.app.ui.components.sliceDetail
import com.lifetrack.app.ui.components.percentOf
import com.lifetrack.app.ui.components.toDailyPoints
import com.lifetrack.app.ui.theme.MetricStyle
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents
import kotlin.math.roundToInt

@Composable
fun CaloriesDetailScreen(
    onBack: () -> Unit,
    vm: CaloriesDetailViewModel = appViewModel { CaloriesDetailViewModel(it) }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val a = accents()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen, bottom = Space.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                ScreenHeader(
                    title = "Calorie trends",
                    subtitle = "${Dates.shortDay(state.from)} — ${Dates.shortDay(state.to)}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Showing", Modifier.weight(1f))
                PeriodPicker(
                    options = CalorieWindow.entries.toList(),
                    selected = state.window,
                    onSelect = vm::setWindow,
                    label = { it.label },
                    accent = a.calories
                )
            }
        }

        item { AverageHero(state) }

        if (state.daysLogged == 0) {
            item {
                LifeCard {
                    EmptyState(
                        symbol = "📊",
                        title = if (state.loaded) "No meals in this window" else "Loading…",
                        body = "Log a few days of food and this fills in with your averages, " +
                            "your split across meals and your best and worst days."
                    )
                }
            }
            return@LazyColumn
        }

        item { IntakeChartCard(state) }
        item { MacroChartCard(state, Macro.Protein) }
        item { MacroChartCard(state, Macro.Fibre) }
        item { SlotBreakdownCard(state) }
        item { TopFoodsCard(state) }
        item { SummaryTiles(state) }
        item { HistoryCard(state) }
    }
}

/** Day by day, newest first - the numbers the charts above draw, written out. */
@Composable
private fun HistoryCard(state: CaloriesDetailState) {
    val a = accents()
    val protein = state.proteinPerDay.associate { it.date to it.value }
    val fiber = state.fiberPerDay.associate { it.date to it.value }
    val entries = state.perDay
        .filter { it.value > 0.0 }
        .sortedByDescending { it.date }
        .take(HISTORY_ROWS)
        .map { day ->
            HistoryEntry(
                date = day.date,
                metrics = listOfNotNull(
                    HistoryMetric("\uD83C\uDF7D", kcalText(day.value.roundToInt())),
                    protein[day.date]?.takeIf { it > 0.0 }?.let {
                        HistoryMetric("P", "%.0fg".format(it), a.activity)
                    },
                    fiber[day.date]?.takeIf { it > 0.0 }?.let {
                        HistoryMetric("F", "%.0fg".format(it), a.caution)
                    }
                )
            )
        }

    Column(Modifier.fillMaxWidth()) {
        CardGap()
        SectionLabel("Day by day")
        Spacer(Modifier.height(Space.md))
        LifeCard {
            if (entries.isEmpty()) {
                Text(
                    "Nothing logged in this window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HistoryList(entries)
                if (state.daysLogged > entries.size) {
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "Showing the most recent ${entries.size} of ${state.daysLogged} logged days.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Long enough to scroll through, short enough not to turn six months into a wall. */
private const val HISTORY_ROWS = 30

// ---------------------------------------------------------------- hero

@Composable
private fun AverageHero(state: CaloriesDetailState) {
    val a = accents()
    val over = state.average > state.goal
    val tint = if (over) a.negative else a.calories

    HeroPanel(accent = tint) {
        SectionLabel("Daily average")
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(kcalText(state.average), style = MetricStyle, color = tint)
            Spacer(Modifier.width(Space.sm))
            Text(
                "kcal",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            // higherIsBetter = false: eating more than the window before is the bad direction,
            // so "+9%" comes out red and "-9%" green without flipping the number itself.
            if (state.hasComparison) DeltaChip(state.delta, higherIsBetter = false)
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            if (over) "over your ${kcalText(state.goal)} goal on an average day"
            else "under your ${kcalText(state.goal)} goal on an average day",
            style = MaterialTheme.typography.bodyMedium,
            color = if (over) a.negative else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.md))
        OverflowBar(state.average.toFloat(), state.goal.toFloat())
        Spacer(Modifier.height(Space.sm))
        Text(
            "${state.daysLogged} of ${state.window.days} days logged  ·  " +
                "${kcalText(state.total)} kcal in total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- charts

@Composable
private fun IntakeChartCard(state: CaloriesDetailState) {
    val a = accents()
    var selected by remember { mutableStateOf<String?>(null) }
    // A week gets weekday initials; longer windows only label the ends, so use a real date.
    val labelFor: (String) -> String =
        if (state.window.barred) Dates::weekdayInitial else Dates::shortDay
    val points = state.perDay.toDailyPoints(state.from, state.to, labelFor)

    LifeCard {
        SectionLabel("What you ate")
        Spacer(Modifier.height(Space.lg))
        if (state.window.barred) {
            // goalMeansGood = false: the daily goal is a ceiling here, so a bar that reaches
            // the line is a day you went over, not a day you won.
            BarChart(
                points = points,
                accent = a.calories,
                goal = state.goal.toDouble(),
                goalMeansGood = false,
                valueLabel = { "%,.0f kcal".format(it) },
                selectedKey = selected,
                onSelect = { selected = if (it.key == selected) null else it.key }
            )
        } else {
            TrendChart(
                points = points,
                accent = a.calories,
                goal = state.goal.toDouble(),
                selectedKey = selected,
                onSelect = { selected = if (it.key == selected) null else it.key }
            )
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "The line is your ${kcalText(state.goal)} kcal daily goal. Tap any day for the detail.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // The tapped day, answered properly. Calories carry their macros along, since a heavy day
    // that was heavy on protein is a different day from one that was heavy on sugar.
    val index = points.indexOfFirst { it.key == selected }
    if (index >= 0) {
        val protein = state.proteinPerDay.firstOrNull { it.date == points[index].key }?.value ?: 0.0
        val fiber = state.fiberPerDay.firstOrNull { it.date == points[index].key }?.value ?: 0.0
        ChartDetailSheet(
            detail = dayDetail(
                points = points,
                index = index,
                accent = a.calories,
                format = { "%,.0f".format(it) },
                goal = state.goal.toDouble(),
                goalMeansGood = false,
                unitLabel = "kcal",
                extra = listOfNotNull(
                    protein.takeIf { it > 0.0 }?.let {
                        ChartFact("Protein that day", "%.0f g".format(it), a.activity)
                    },
                    fiber.takeIf { it > 0.0 }?.let {
                        ChartFact("Fibre that day", "%.0f g".format(it), a.caution)
                    }
                )
            ),
            onDismiss = { selected = null }
        )
    }
}

/** The two macros that are floors rather than ceilings. They chart identically. */
private enum class Macro(val label: String) { Protein("Protein"), Fibre("Fibre") }

/**
 * The same window, counted the other way up: the goal line is a floor to clear rather than a
 * ceiling to stay under, which is why these charts pass `goalMeansGood = true`.
 */
@Composable
private fun MacroChartCard(state: CaloriesDetailState, macro: Macro) {
    val a = accents()
    var selected by remember { mutableStateOf<String?>(null) }
    val labelFor: (String) -> String =
        if (state.window.barred) Dates::weekdayInitial else Dates::shortDay

    val series = if (macro == Macro.Protein) state.proteinPerDay else state.fiberPerDay
    val goal = if (macro == Macro.Protein) state.proteinGoal else state.fiberGoal
    val average = if (macro == Macro.Protein) state.proteinAverage else state.fiberAverage
    val daysMet = if (macro == Macro.Protein) state.proteinDaysMet else state.fiberDaysMet
    val has = if (macro == Macro.Protein) state.hasProtein else state.hasFiber
    val accent = if (macro == Macro.Protein) a.activity else a.caution
    val points = series.toDailyPoints(state.from, state.to, labelFor)

    LifeCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(macro.label, Modifier.weight(1f))
            Text(
                "avg ${proteinText(average)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        Spacer(Modifier.height(Space.lg))
        if (!has) {
            Text(
                "Nothing with ${macro.label.lowercase()} logged in this window. Meals logged " +
                    "before the catalogue moved to measured data carry none, so the chart " +
                    "fills in from here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@LifeCard
        }
        if (state.window.barred) {
            BarChart(
                points = points,
                accent = accent,
                goal = goal.toDouble(),
                goalMeansGood = true,
                valueLabel = { "%,.0f g".format(it) },
                selectedKey = selected,
                onSelect = { selected = if (it.key == selected) null else it.key }
            )
        } else {
            TrendChart(
                points = points,
                accent = accent,
                goal = goal.toDouble(),
                selectedKey = selected,
                onSelect = { selected = if (it.key == selected) null else it.key }
            )
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "The line is your ${proteinText(goal)} daily goal  ·  " +
                "hit on $daysMet of ${state.daysLogged} logged days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val index = points.indexOfFirst { it.key == selected }
    if (index >= 0) {
        ChartDetailSheet(
            detail = dayDetail(
                points = points,
                index = index,
                accent = accent,
                format = { "%,.0f".format(it) },
                goal = goal.toDouble(),
                goalMeansGood = true,
                unitLabel = "g"
            ),
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun SlotBreakdownCard(state: CaloriesDetailState) {
    val slices = Slot.entries.mapNotNull { slot ->
        val value = state.perSlot.firstOrNull { it.date == slot.name }?.value ?: 0.0
        if (value <= 0.0) null else Slice("${slot.emoji}  ${slot.label}", value, slotColor(slot))
    }
    if (slices.isEmpty()) return
    var picked by remember { mutableStateOf<Slice?>(null) }

    LifeCard {
        SectionLabel("Where the calories go")
        Spacer(Modifier.height(Space.lg))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                slices = slices,
                centerValue = kcalText(state.total),
                centerLabel = "kcal",
                onSelect = { picked = it }
            )
        }
        Spacer(Modifier.height(Space.xl))
        BreakdownList(
            slices,
            valueLabel = { "%,.0f kcal".format(it) },
            onClick = { picked = it }
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Tap a slice or a row for the detail.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    picked?.let { slice ->
        // A meal's share of the window is only half the story; what it costs on a day you
        // actually ate it is the number you can act on.
        val days = state.daysLogged.coerceAtLeast(1)
        ChartDetailSheet(
            detail = sliceDetail(
                slices = slices,
                slice = slice,
                format = { "%,.0f kcal".format(it) },
                subtitle = "Across ${state.window.label.lowercase()}",
                extra = listOf(
                    ChartFact("Average a day", "%,.0f kcal".format(slice.value / days))
                )
            ),
            onDismiss = { picked = null }
        )
    }
}

@Composable
private fun TopFoodsCard(state: CaloriesDetailState) {
    if (state.topFoods.isEmpty()) return
    val a = accents()
    // One hue, stepped down the ranking — these are quantities, not states, so no red/green.
    val slices = state.topFoods.mapIndexed { i, food ->
        Slice(food.date, food.value, a.calories.copy(alpha = 1f - (i * 0.09f).coerceAtMost(0.6f)))
    }

    var picked by remember { mutableStateOf<Slice?>(null) }

    LifeCard {
        SectionLabel("Most eaten")
        Spacer(Modifier.height(Space.lg))
        BreakdownList(
            slices,
            valueLabel = { "%,.0f kcal".format(it) },
            onClick = { picked = it }
        )
    }

    picked?.let { slice ->
        val days = state.daysLogged.coerceAtLeast(1)
        ChartDetailSheet(
            detail = sliceDetail(
                slices = slices,
                slice = slice,
                format = { "%,.0f kcal".format(it) },
                subtitle = "Across ${state.window.label.lowercase()}",
                extra = listOf(
                    ChartFact("Average a day", "%,.0f kcal".format(slice.value / days)),
                    ChartFact("Share of everything eaten", "%d%%".format(
                        percentOf(slice.value, state.total.toDouble())
                    ))
                )
            ),
            onDismiss = { picked = null }
        )
    }
}

// ---------------------------------------------------------------- summary

@Composable
private fun SummaryTiles(state: CaloriesDetailState) {
    val a = accents()
    Column(Modifier.fillMaxWidth()) {
        CardGap()
        SectionLabel("Summary")
        Spacer(Modifier.height(Space.md))
        TileRow {
            StatTile(
                label = "Days logged",
                value = state.daysLogged.toString(),
                accent = a.calories,
                footnote = "of ${state.window.days}",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Over goal",
                value = state.daysOver.toString(),
                accent = a.negative,
                footnote = "days",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Under goal",
                value = state.daysUnder.toString(),
                accent = a.positive,
                footnote = "days",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(Space.sm))
        TileRow {
            val best = state.bestDay
            val worst = state.worstDay
            StatTile(
                label = "Lightest day",
                value = if (best != null) kcalText(best.value.toInt()) else "—",
                accent = a.positive,
                footnote = best?.let { Dates.shortDay(it.date) } ?: "no data",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Heaviest day",
                value = if (worst != null) kcalText(worst.value.toInt()) else "—",
                accent = a.negative,
                footnote = worst?.let { Dates.shortDay(it.date) } ?: "no data",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(Space.sm))
        TileRow {
            StatTile(
                label = "Protein a day",
                value = if (state.hasProtein) proteinText(state.proteinAverage) else "—",
                accent = a.activity,
                footnote = "goal ${proteinText(state.proteinGoal)}",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Fibre a day",
                value = if (state.hasFiber) proteinText(state.fiberAverage) else "—",
                accent = a.caution,
                footnote = "goal ${proteinText(state.fiberGoal)}",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(Space.sm))
        TileRow {
            StatTile(
                label = "Protein goal hit",
                value = state.proteinDaysMet.toString(),
                accent = if (state.proteinDaysMet > 0) a.positive else a.activity,
                footnote = "of ${state.daysLogged} days",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Fibre goal hit",
                value = state.fiberDaysMet.toString(),
                accent = if (state.fiberDaysMet > 0) a.positive else a.caution,
                footnote = "of ${state.daysLogged} days",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
