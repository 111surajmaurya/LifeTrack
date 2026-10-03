package com.lifetrack.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.lifetrack.app.data.Dates
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifetrack.app.ui.theme.Elevation
import com.lifetrack.app.ui.theme.MetricStyle
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accentWash
import com.lifetrack.app.ui.theme.accents

/** Standard horizontal inset for every screen. Kept for call sites that predate [Space]. */
val ScreenPadding: Dp = Space.screen

// ---------------------------------------------------------------- containers

/**
 * The one card look used everywhere: rounded, hairline border, barely-there lift.
 * Pass [accent] to tint the border, which is how a card signals state without shouting.
 */
@Composable
fun LifeCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Space.lg),
    tonal: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (tonal) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = Elevation.raised,
        shadowElevation = Elevation.flat,
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.35f) ?: MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** A hero panel with an accent wash behind it, for the top of a detail screen. */
@Composable
fun HeroPanel(
    accent: Color,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Space.xl),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(accentWash(accent))
            .padding(padding)
    ) {
        Column(content = content)
    }
}

// ---------------------------------------------------------------- text bits

/** Small uppercase label that opens a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Screen title plus optional subtitle, consistent across every tab. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * A signed change with its own colour: "+12%" green, "-8%" red.
 * [higherIsBetter] flips the meaning for things like screen time, where less is the win.
 */
@Composable
fun DeltaChip(
    delta: Double,
    suffix: String = "%",
    higherIsBetter: Boolean = true,
    modifier: Modifier = Modifier
) {
    val a = accents()
    val color = a.forDelta(delta, MaterialTheme.colorScheme.onSurfaceVariant, higherIsBetter)
    val sign = if (delta > 0) "+" else ""
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        modifier = modifier
    ) {
        Text(
            if (delta == 0.0) "no change" else "$sign${"%.0f".format(delta)}$suffix",
            Modifier.padding(horizontal = Space.sm, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ---------------------------------------------------------------- progress

/** Circular progress with a big number in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    centerText: String,
    caption: String,
    modifier: Modifier = Modifier,
    captionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 200.dp,
    stroke: Dp = 16.dp,
    overflow: Float = 0f,   // 0f..1f of a second lap, drawn thinner when you go past the goal
    below: @Composable (() -> Unit)? = null
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    val animatedOver by animateFloatAsState(overflow.coerceIn(0f, 1f), tween(700), label = "ringOver")
    val animatedColor by animateColorAsState(color, tween(400), label = "ringColor")
    val track = MaterialTheme.colorScheme.outline

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(
                animatedColor, -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            if (animatedOver > 0f) {
                drawArc(
                    animatedColor.copy(alpha = 0.42f), -90f, 360f * animatedOver, false,
                    Offset(inset, inset), arcSize, style = Stroke(sw * 0.5f, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, style = MetricStyle, color = MaterialTheme.colorScheme.onSurface)
            Text(
                caption, style = MaterialTheme.typography.bodyMedium,
                color = captionColor, textAlign = TextAlign.Center
            )
            below?.invoke()
        }
    }
}

/** Rounded bar used for goals inside cards. */
@Composable
fun ProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color? = null
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "bar")
    val animatedColor by animateColorAsState(color, tween(400), label = "barColor")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track ?: MaterialTheme.colorScheme.outline)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(animatedColor.copy(alpha = 0.8f), animatedColor)))
        )
    }
}

/**
 * A bar that keeps going once you pass the goal, with the overflow drawn in the
 * over-budget colour on top of the filled track. Used for per-meal calorie budgets.
 */
@Composable
fun OverflowBar(
    value: Float,
    goal: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val a = accents()
    val ratio = if (goal > 0f) value / goal else 0f
    if (ratio <= 1f) {
        ProgressBar(ratio, a.forProgress(ratio), modifier, height)
    } else {
        val overFraction = ((ratio - 1f) / ratio).coerceIn(0f, 1f)
        Box(
            modifier.fillMaxWidth().height(height).clip(CircleShape).background(a.negative.copy(alpha = 0.35f))
        ) {
            Box(
                Modifier.fillMaxWidth(1f - overFraction).height(height)
                    .clip(CircleShape).background(a.negative)
            )
        }
    }
}

// ---------------------------------------------------------------- small pieces

/** Round tinted badge holding an emoji or short glyph. */
@Composable
fun AccentBadge(symbol: String, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 32))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * A compact number tile: label on top, value big, optional footnote.
 * Three or four of these in a row is the standard summary strip.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.12f),
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.md)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1)
            if (footnote != null) {
                Text(
                    footnote, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                )
            }
        }
    }
}

/** Pill switcher used for Week / Month and similar two-or-three-way choices. */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    val tint = accent ?: MaterialTheme.colorScheme.primary
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { option ->
                val on = option == selected
                Surface(
                    shape = CircleShape,
                    color = if (on) tint else Color.Transparent,
                    modifier = Modifier.weight(1f).clickable { onSelect(option) }
                ) {
                    Text(
                        label(option),
                        Modifier.padding(vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * A dropdown for choosing how far back a screen looks.
 *
 * Replaces the three-way [SegmentedPicker] on the detail screens: a pill switcher stops being
 * readable past about four options, and the windows worth offering (today through six months)
 * are more than that. Six months is the last useful stop because [Retention] prunes past it -
 * offering "this year" would show half a year of blank.
 */
@Composable
fun <T> PeriodPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    var open by remember { mutableStateOf(false) }
    val tint = accent ?: MaterialTheme.colorScheme.primary

    Box(modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.clip(CircleShape).clickable { open = true }
        ) {
            Row(
                Modifier.padding(start = Space.md, end = Space.sm, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label(selected),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    maxLines = 1
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = "Change period",
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                val on = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) tint else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

/** One day in a [WeekStrip]: a ring filled to [progress], under a day initial and date. */
data class DayRing(
    val date: String,
    val initial: String,
    val dayOfMonth: String,
    val progress: Float,
    val met: Boolean,
    val today: Boolean = false
)

/**
 * Seven days at a glance - one small ring each, filled to that day's share of the goal.
 *
 * A week of history in the height of a single row, which is the point: the trend charts answer
 * "how much", this answers "did I, on each of the last seven days". Today is outlined so the
 * strip reads as a calendar rather than a chart with no axis.
 */
@Composable
fun WeekStrip(
    days: List<DayRing>,
    accent: Color,
    modifier: Modifier = Modifier,
    onSelect: ((DayRing) -> Unit)? = null
) {
    val a = accents()
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        days.forEach { day ->
            val ring = if (day.met) a.positive else accent
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .then(if (onSelect != null) Modifier.clickable { onSelect(day) } else Modifier)
                    .padding(vertical = Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    day.initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.xs))
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    DayRingArc(day.progress, ring, day.today)
                    Text(
                        day.dayOfMonth,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (day.today) FontWeight.Bold else FontWeight.Medium,
                        color = if (day.today) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DayRingArc(progress: Float, color: Color, today: Boolean) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "dayRing")
    val track = MaterialTheme.colorScheme.outline
    val outline = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxSize()) {
        val sw = 3.dp.toPx()
        val inset = sw / 2
        val arc = Size(size.width - sw, size.height - sw)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
        if (animated > 0f) {
            drawArc(
                color, -90f, 360f * animated, false, Offset(inset, inset), arc,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        // Today gets a hairline halo so the strip has a "you are here" without a second colour.
        if (today) {
            drawArc(
                outline.copy(alpha = 0.35f), 0f, 360f, false,
                Offset(0f, 0f), Size(size.width, size.height), style = Stroke(1.dp.toPx())
            )
        }
    }
}

/** One measurement on a [HistoryList] row: a symbol and a formatted value. */
data class HistoryMetric(val symbol: String, val value: String, val accent: Color? = null)

/** One day on a [HistoryList]. */
data class HistoryEntry(val date: String, val metrics: List<HistoryMetric>)

/**
 * The plain list the charts cannot replace: one row per day, newest first, with the day's
 * numbers spelled out.
 *
 * A chart answers "what is the shape of this"; this answers "what did I actually do on the
 * 14th". Every screen here had the former and none had the latter, so a day you wanted to
 * check could only be read off a bar by eye.
 */
@Composable
fun HistoryList(entries: List<HistoryEntry>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    Modifier.padding(vertical = Space.xs),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    Dates.label(entry.date),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (Dates.isToday(entry.date)) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                entry.metrics.forEach { metric ->
                    Row(
                        Modifier.padding(start = Space.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(metric.symbol, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            metric.value,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = metric.accent ?: MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** What a screen shows when there is nothing to show yet. */
@Composable
fun EmptyState(symbol: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 44.dp, horizontal = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        Text(symbol, style = MaterialTheme.typography.displayMedium)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}

/** "1,240 kcal · 3 items" - dot-joined footnotes. */
@Composable
fun DotSeparated(vararg parts: String) {
    Text(
        parts.filter { it.isNotBlank() }.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Row of two or three tiles with the standard gap. */
@Composable
fun TileRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm), content = content)
}

/** Vertical gap matching the card rhythm. */
@Composable
fun CardGap() = Spacer(Modifier.height(Space.cards))
