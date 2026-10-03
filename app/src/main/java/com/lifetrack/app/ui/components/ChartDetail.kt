package com.lifetrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifetrack.app.data.Dates
import com.lifetrack.app.ui.theme.MetricStyle
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents
import kotlin.math.abs
import kotlin.math.roundToInt

/** One line of context under the headline number on a [ChartDetailSheet]. */
data class ChartFact(val label: String, val value: String, val accent: Color? = null)

/**
 * Everything a tapped chart element has to say about itself.
 *
 * Built by [dayDetail] or [sliceDetail] rather than by hand at each call site, so a bar, a point
 * on a line and a slice of a donut all answer the same questions in the same order.
 */
data class ChartDetail(
    val title: String,
    val subtitle: String?,
    val value: String,
    val accent: Color,
    val facts: List<ChartFact>
)

/**
 * The sheet a chart opens when you tap it.
 *
 * Charts are good at shape and bad at specifics: you can see that Tuesday was the heavy day and
 * still have no idea what Tuesday actually was. Rather than cram numbers onto the chart itself,
 * every chart in the app hands the tapped element to this, which has room to answer properly -
 * the value, how it sat against the goal, against the average, and where it ranked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartDetailSheet(detail: ChartDetail, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xxl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(detail.accent))
                Spacer(Modifier.size(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(detail.title, style = MaterialTheme.typography.titleLarge)
                    if (detail.subtitle != null) {
                        Text(
                            detail.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.lg))
            Text(detail.value, style = MetricStyle, color = detail.accent)

            if (detail.facts.isNotEmpty()) {
                Spacer(Modifier.height(Space.lg))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(Modifier.fillMaxWidth().padding(Space.md)) {
                        detail.facts.forEachIndexed { index, fact ->
                            if (index > 0) {
                                HorizontalDivider(
                                    Modifier.padding(vertical = Space.sm),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    fact.label,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    fact.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = fact.accent ?: MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- builders

/**
 * The standard answer for one day of a daily series.
 *
 * [goalMeansGood] flips what red means: a step goal is a floor to clear, a calorie goal is a
 * ceiling to stay under, and the same "12% above" is a win in one and a miss in the other.
 */
@Composable
fun dayDetail(
    points: List<ChartPoint>,
    index: Int,
    accent: Color,
    format: (Double) -> String,
    goal: Double? = null,
    goalMeansGood: Boolean = true,
    unitLabel: String? = null,
    extra: List<ChartFact> = emptyList()
): ChartDetail {
    val a = accents()
    val point = points[index]
    val value = point.value
    val logged = points.filter { it.value > 0.0 }
    val average = if (logged.isEmpty()) 0.0 else logged.sumOf { it.value } / logged.size

    val facts = buildList {
        if (goal != null && goal > 0) {
            val diff = value - goal
            val hit = if (goalMeansGood) value >= goal else value <= goal
            add(
                ChartFact(
                    label = if (goalMeansGood) "Against your goal" else "Against your budget",
                    value = when {
                        diff == 0.0 -> "exactly on ${format(goal)}"
                        diff > 0 -> "${format(abs(diff))} over ${format(goal)}"
                        else -> "${format(abs(diff))} under ${format(goal)}"
                    },
                    accent = if (hit) a.positive else a.negative
                )
            )
        }

        if (average > 0.0 && value > 0.0) {
            val pct = (value - average) / average * 100
            add(
                ChartFact(
                    label = "Against your average",
                    value = when {
                        abs(pct) < 1 -> "about average (${format(average)})"
                        pct > 0 -> "%.0f%% above average".format(pct)
                        else -> "%.0f%% below average".format(abs(pct))
                    }
                )
            )
        }

        // Rank only means something once there is a field to rank against.
        if (logged.size > 2 && value > 0.0) {
            val rank = logged.count { it.value > value } + 1
            add(ChartFact("Rank", "${ordinal(rank)} highest of ${logged.size} logged days"))
        }

        if (index > 0) {
            val previous = points[index - 1].value
            val delta = value - previous
            add(
                ChartFact(
                    label = "Against the day before",
                    value = when {
                        previous == 0.0 -> "nothing logged that day"
                        delta == 0.0 -> "no change"
                        delta > 0 -> "+${format(delta)}"
                        else -> "-${format(abs(delta))}"
                    }
                )
            )
        }

        addAll(extra)
    }

    return ChartDetail(
        title = Dates.label(point.key).replaceFirstChar { it.uppercase() },
        subtitle = Dates.longLabel(point.key),
        value = if (unitLabel != null) "${format(value)} $unitLabel" else format(value),
        accent = accent,
        facts = facts
    )
}

/** The standard answer for one slice of a donut or one row of a breakdown. */
fun sliceDetail(
    slices: List<Slice>,
    slice: Slice,
    format: (Double) -> String,
    subtitle: String? = null,
    extra: List<ChartFact> = emptyList()
): ChartDetail {
    val total = slices.sumOf { it.value }
    val rank = slices.count { it.value > slice.value } + 1
    val facts = buildList {
        if (total > 0) {
            add(ChartFact("Share of the total", "%.0f%%".format(slice.value / total * 100)))
            add(ChartFact("Total across all", format(total)))
        }
        if (slices.size > 1) {
            add(ChartFact("Rank", "${ordinal(rank)} of ${slices.size}"))
            val biggest = slices.maxByOrNull { it.value }
            if (biggest != null && biggest.label != slice.label) {
                add(ChartFact("Biggest", "${biggest.label} (${format(biggest.value)})"))
            }
        }
        addAll(extra)
    }
    return ChartDetail(
        title = slice.label,
        subtitle = subtitle,
        value = format(slice.value),
        accent = slice.color,
        facts = facts
    )
}

/** "1st", "2nd", "3rd", "4th" — including the 11th/12th/13th exceptions. */
fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/** Rounds a fraction to a percentage, guarding the zero-total case. */
internal fun percentOf(value: Double, total: Double): Int =
    if (total <= 0.0) 0 else (value / total * 100).roundToInt()
