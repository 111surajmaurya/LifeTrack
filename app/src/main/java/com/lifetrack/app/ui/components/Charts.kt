package com.lifetrack.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.roundToInt
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents

/**
 * One point on a chart. [label] is what goes under the bar, [value] is the height,
 * and [key] is the ISO date (or package name) the caller can act on when it is tapped.
 */
data class ChartPoint(
    val key: String,
    val label: String,
    val value: Double,
    val highlight: Boolean = false
)

/** Pads a sparse `date -> value` series so every day in the window gets a bar, zeros included. */
fun List<DayValue>.toDailyPoints(
    from: String,
    to: String,
    labelFor: (String) -> String = { Dates.weekdayInitial(it) },
    highlightToday: Boolean = true
): List<ChartPoint> {
    val byDate = associate { it.date to it.value }
    val today = Dates.today()
    return Dates.range(from, to).map { d ->
        ChartPoint(d, labelFor(d), byDate[d] ?: 0.0, highlight = highlightToday && d == today)
    }
}

/**
 * Pads a sparse series the same way [toDailyPoints] does, but expresses each day as a share of
 * [goal] for [WeekStrip]. A goal of zero leaves every ring empty rather than dividing by it.
 */
fun List<DayValue>.toDayRings(from: String, to: String, goal: Double): List<DayRing> {
    val byDate = associate { it.date to it.value }
    val today = Dates.today()
    return Dates.range(from, to).map { d ->
        val value = byDate[d] ?: 0.0
        DayRing(
            date = d,
            initial = Dates.weekdayInitial(d),
            dayOfMonth = Dates.dayOfMonth(d).toString(),
            progress = if (goal > 0.0) (value / goal).toFloat() else 0f,
            met = goal > 0.0 && value >= goal,
            today = d == today
        )
    }
}

// ---------------------------------------------------------------- bars

/**
 * Vertical bars, the workhorse chart. Bars at or above [goal] take the positive colour,
 * everything else stays in [accent], and a dashed goal line runs across if one is given.
 * Tapping a bar selects it and swaps the caption for that day's value.
 */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    accent: Color,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    height: Dp = 160.dp,
    valueLabel: (Double) -> String = { "%,.0f".format(it) },
    goalMeansGood: Boolean = true,
    selectedKey: String? = null,
    onSelect: ((ChartPoint) -> Unit)? = null,
    // Off when the keys aren't dates and the caller prints the selection itself.
    showFocus: Boolean = true
) {
    if (points.isEmpty()) {
        EmptyChart(height, "No data yet")
        return
    }
    val a = accents()
    val max = maxOf(points.maxOf { it.value }, goal ?: 0.0, 1.0)
    val outline = MaterialTheme.colorScheme.outline

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height)) {
            // Goal line sits behind the bars.
            if (goal != null && goal > 0) {
                val frac = (goal / max).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .padding(bottom = height * frac)
                        .align(Alignment.BottomStart)
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(1.dp)
                            .align(Alignment.BottomStart)
                            .background(outline)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().height(height),
                horizontalArrangement = Arrangement.spacedBy(if (points.size > 14) 2.dp else 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                points.forEach { p ->
                    val frac by animateFloatAsState(
                        (p.value / max).toFloat().coerceIn(0f, 1f), tween(500), label = "bar-${p.key}"
                    )
                    val hit = goal != null && goal > 0 && p.value >= goal
                    val barColor = when {
                        p.value == 0.0 -> outline
                        hit && goalMeansGood -> a.positive
                        hit && !goalMeansGood -> a.negative
                        else -> accent
                    }
                    val selected = p.key == selectedKey
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (onSelect != null) Modifier.clickable { onSelect(p) } else Modifier),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (points.size > 14) 0.9f else 0.72f)
                                .fillMaxHeight(frac.coerceAtLeast(0.012f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(
                                    Brush.verticalGradient(
                                        if (selected) listOf(barColor, barColor)
                                        else listOf(barColor, barColor.copy(alpha = 0.55f))
                                    )
                                )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (points.size > 14) 2.dp else 6.dp)) {
            points.forEach { p ->
                Text(
                    if (points.size > 14 && p.key != selectedKey && !p.highlight) "" else p.label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (p.highlight || p.key == selectedKey) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (p.highlight || p.key == selectedKey) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        val focus = points.firstOrNull { it.key == selectedKey }
        if (showFocus && focus != null) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "${Dates.label(focus.key)}  ·  ${valueLabel(focus.value)}",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
    }
}

/** A smoothed line with a soft fill underneath. Better than bars for a long month. */
@Composable
fun TrendChart(
    points: List<ChartPoint>,
    accent: Color,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    height: Dp = 150.dp,
    selectedKey: String? = null,
    onSelect: ((ChartPoint) -> Unit)? = null
) {
    if (points.size < 2) {
        EmptyChart(height, "Not enough data yet")
        return
    }
    val max = maxOf(points.maxOf { it.value }, goal ?: 0.0, 1.0)
    val outline = MaterialTheme.colorScheme.outline
    val marker = MaterialTheme.colorScheme.onSurface
    val selectedIndex = points.indexOfFirst { it.key == selectedKey }.takeIf { it >= 0 }

    // A line has no bars to tap, so the whole canvas is the target and the x position picks the
    // nearest day. Six months of points are a couple of pixels apart; asking for a precise hit
    // would make the longer windows untappable.
    val tap = if (onSelect == null) Modifier else Modifier.pointerInput(points) {
        detectTapGestures { offset ->
            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
            val index = (fraction * (points.size - 1)).roundToInt().coerceIn(points.indices)
            onSelect(points[index])
        }
    }

    Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(height).then(tap)) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = w * i / (points.size - 1).toFloat()
            fun y(v: Double) = h - (h * (v / max)).toFloat()

            if (goal != null && goal > 0) {
                drawLine(
                    outline, Offset(0f, y(goal)), Offset(w, y(goal)),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                    )
                )
            }

            val line = Path().apply {
                moveTo(x(0), y(points[0].value))
                for (i in 1 until points.size) {
                    val prevX = x(i - 1); val prevY = y(points[i - 1].value)
                    val curX = x(i); val curY = y(points[i].value)
                    val midX = (prevX + curX) / 2
                    cubicTo(midX, prevY, midX, curY, curX, curY)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0f))))
            drawPath(line, accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

            val lastX = x(points.size - 1)
            val lastY = y(points.last().value)
            drawCircle(accent, radius = 4.dp.toPx(), center = Offset(lastX, lastY))

            // The selected day gets a full-height rule and a ringed dot, so it stays findable
            // once the sheet covering the bottom of the screen is dismissed.
            if (selectedIndex != null) {
                val sx = x(selectedIndex)
                val sy = y(points[selectedIndex].value)
                drawLine(
                    marker.copy(alpha = 0.35f), Offset(sx, 0f), Offset(sx, h),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(marker, radius = 6.dp.toPx(), center = Offset(sx, sy))
                drawCircle(accent, radius = 4.dp.toPx(), center = Offset(sx, sy))
            }
        }
        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                points.first().label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                points.last().label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- breakdown

/** One slice of a [DonutChart] or row of a [BreakdownList]. */
data class Slice(val label: String, val value: Double, val color: Color)

/** Ring split by category, with the total in the middle. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    stroke: Dp = 20.dp,
    onSelect: ((Slice) -> Unit)? = null
) {
    val total = slices.sumOf { it.value }
    val track = MaterialTheme.colorScheme.outline
    val animated by animateFloatAsState(if (total > 0) 1f else 0f, tween(700), label = "donut")

    // Which slice a tap landed on, from the angle around the centre. Drawing starts at -90
    // (twelve o'clock) and runs clockwise, so the same offset is applied here.
    val tap = if (onSelect == null || total <= 0.0) Modifier else Modifier.pointerInput(slices) {
        detectTapGestures { offset ->
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val degrees = (Math.toDegrees(
                atan2((offset.y - cy).toDouble(), (offset.x - cx).toDouble())
            ) + 90.0 + 360.0) % 360.0
            var start = 0.0
            slices.firstOrNull { slice ->
                val sweep = 360.0 * slice.value / total
                val hit = degrees >= start && degrees < start + sweep
                start += sweep
                hit
            }?.let(onSelect)
        }
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().aspectRatio(1f).then(tap)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            if (total <= 0) return@Canvas
            var start = -90f
            slices.forEach { s ->
                val sweep = (360f * (s.value / total)).toFloat() * animated
                if (sweep > 0f) {
                    drawArc(
                        s.color, start, sweep - 1.5f, false, Offset(inset, inset), arcSize,
                        style = Stroke(sw, cap = StrokeCap.Butt)
                    )
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerValue, style = MaterialTheme.typography.headlineSmall)
            Text(
                centerLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Ranked horizontal bars — "where did the calories / the hours actually go". */
@Composable
fun BreakdownList(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    valueLabel: (Double) -> String = { "%,.0f".format(it) },
    onClick: ((Slice) -> Unit)? = null
) {
    val max = slices.maxOfOrNull { it.value } ?: 0.0
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        slices.forEach { s ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable { onClick(s) } else Modifier)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(Space.sm))
                    Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(valueLabel(s.value), style = MaterialTheme.typography.labelMedium, color = s.color)
                }
                Spacer(Modifier.height(5.dp))
                ProgressBar(
                    progress = if (max > 0) (s.value / max).toFloat() else 0f,
                    color = s.color,
                    height = 6.dp
                )
            }
        }
    }
}

// ---------------------------------------------------------------- calendar

/** One day in the habit calendar. [intensity] is 0f..1f of the day's goal. */
data class CalendarDay(
    val date: String,
    val intensity: Float,
    val done: Boolean,
    val future: Boolean = false
)

/**
 * Month grid, Monday first, shaded by how much of the goal each day hit -
 * the "did I actually keep this up" view.
 */
@Composable
fun MonthCalendar(
    monthAnchor: String,
    days: Map<String, CalendarDay>,
    accent: Color,
    modifier: Modifier = Modifier,
    onDayClick: ((String) -> Unit)? = null,
    selected: String? = null
) {
    val cells = Dates.monthGrid(monthAnchor)
    val today = Dates.today()
    val outline = MaterialTheme.colorScheme.outline

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(Modifier.fillMaxWidth()) {
            Dates.weekdayInitials.forEach { d ->
                Text(
                    d, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val day = days[date]
                        val future = Dates.isFuture(date)
                        val intensity = day?.intensity?.coerceIn(0f, 1f) ?: 0f
                        val fill = when {
                            future -> Color.Transparent
                            intensity <= 0f -> outline.copy(alpha = 0.4f)
                            else -> accent.copy(alpha = 0.25f + 0.75f * intensity)
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(fill)
                                .then(
                                    if (date == today || date == selected)
                                        Modifier.background(Color.Transparent)
                                    else Modifier
                                )
                                .then(
                                    if (onDayClick != null && !future)
                                        Modifier.clickable { onDayClick(date) } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Dates.dayOfMonth(date).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    intensity > 0.55f -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The four-step key under a calendar. */
@Composable
fun CalendarLegend(accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Less", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Space.xs))
        listOf(0f, 0.35f, 0.7f, 1f).forEach { i ->
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (i <= 0f) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        else accent.copy(alpha = 0.25f + 0.75f * i)
                    )
            )
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.width(Space.xs))
        Text(
            "More", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Seven dots for the current week, filled where the goal was met. */
@Composable
fun WeekDots(days: List<CalendarDay>, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        days.forEach { d ->
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        if (d.done) accent
                        else MaterialTheme.colorScheme.outline.copy(alpha = if (d.future) 0.3f else 0.7f)
                    )
            )
        }
    }
}

@Composable
private fun EmptyChart(height: Dp, message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
