package com.lifetrack.app.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.DayValue
import com.lifetrack.app.data.Dates
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.AccentBadge
import com.lifetrack.app.ui.components.CardGap
import com.lifetrack.app.ui.components.DeltaChip
import com.lifetrack.app.ui.components.HeroPanel
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ProgressBar
import com.lifetrack.app.ui.components.ProgressRing
import com.lifetrack.app.ui.components.ChartDetailSheet
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.WeekStrip
import com.lifetrack.app.ui.components.dayDetail
import com.lifetrack.app.ui.components.toDayRings
import com.lifetrack.app.ui.components.TrendChart
import com.lifetrack.app.ui.components.toDailyPoints
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.data.RoutineStatus
import com.lifetrack.app.ui.routine.routineViewModel
import com.lifetrack.app.ui.theme.accents
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The first screen of the app: one glance should answer "how is my day going".
 * A day score up top, anything live right under it, then one tappable card per tab.
 */
@Composable
fun HomeScreen(
    onOpenCalories: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenRoutine: () -> Unit,
    onOpenScreenTime: () -> Unit,
    onOpenAlarms: () -> Unit,
    onOpenProfile: () -> Unit,
    vm: HomeViewModel = appViewModel { HomeViewModel(it) }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val a = accents()
    val neutral = MaterialTheme.colorScheme.onSurface

    // Only tick when something on screen actually counts up or down.
    val live = state.running != null || state.alarms.nextAt != null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) {
        while (live) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    var stripDay by remember { mutableStateOf<String?>(null) }
    val stripPoints = state.activity.series.toDailyPoints(state.from, state.today)
    val stripIndex = stripPoints.indexOfFirst { it.key == stripDay }
    if (stripIndex >= 0) {
        ChartDetailSheet(
            detail = dayDetail(
                points = stripPoints,
                index = stripIndex,
                accent = a.activity,
                format = { "%,.0f".format(it) },
                goal = state.activity.goal.takeIf { it > 0 }?.toDouble(),
                unitLabel = "steps"
            ),
            onDismiss = { stripDay = null }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
    ) {
        Spacer(Modifier.height(Space.lg))
        Hero(state, onOpenProfile)

        if (live) {
            Spacer(Modifier.height(Space.md))
            RightNow(state, now, onOpenHabits, onOpenAlarms)
        }

        Spacer(Modifier.height(Space.xl))
        SectionLabel("Last seven days")
        // Declared here rather than inside the card so the sheet outlives the card's scope.
        Spacer(Modifier.height(Space.md))
        LifeCard {
            // Steps, because it is the number that varies most day to day and the one a strip
            // of seven rings actually tells you something about.
            WeekStrip(
                days = state.activity.series.toDayRings(
                    state.from, state.today, state.activity.goal.toDouble()
                ),
                accent = a.activity,
                onSelect = { stripDay = it.date }
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                if (state.activity.goal > 0)
                    "Step goal, %,d a day  ·  met on %d of 7".format(
                        state.activity.goal,
                        state.activity.series.count { it.value >= state.activity.goal }
                    )
                else "Set a step goal to fill these in.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Space.xl))
        SectionLabel("Your day")
        Spacer(Modifier.height(Space.md))

        val cal = state.calories
        SummaryCard(
            emoji = "🍽",
            title = "Calories",
            accent = a.calories,
            headline = cal.headline,
            headlineColor = if (cal.goal > 0) a.forProgress(cal.progress, cal.over) else neutral,
            caption = cal.headlineCaption,
            detail = cal.detail,
            progress = cal.progress,
            progressColor = a.forProgress(cal.progress, cal.over),
            delta = cal.delta,
            higherIsBetter = false,
            empty = !cal.logged,
            emptyHint = "Log your first meal  →",
            onClick = onOpenCalories,
            footer = {
                // Protein and fibre are targets to reach rather than budgets to stay under, so
                // they get their own bars and turn green on arrival instead of red.
                Spacer(Modifier.height(Space.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ProgressBar(
                        cal.proteinProgress,
                        if (cal.proteinMet) a.positive else a.activity,
                        modifier = Modifier.weight(1f),
                        height = 5.dp
                    )
                    ProgressBar(
                        cal.fiberProgress,
                        if (cal.fiberMet) a.positive else a.caution,
                        modifier = Modifier.weight(1f),
                        height = 5.dp
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    cal.macroDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        CardGap()

        val act = state.activity
        SummaryCard(
            emoji = "👣",
            title = "Activity",
            accent = a.activity,
            headline = act.headline,
            headlineColor = if (act.met) a.positive else neutral,
            caption = if (act.goal > 0) "of %,d steps".format(act.goal) else "steps",
            detail = act.detail,
            progress = act.progress,
            progressColor = if (act.met) a.positive else a.activity,
            delta = act.delta,
            empty = !act.hasData,
            emptyHint = "Start counting your steps  →",
            onClick = onOpenActivity
        )
        CardGap()

        val hab = state.habits
        SummaryCard(
            emoji = "🌱",
            title = "Habits",
            accent = a.habits,
            headline = "${hab.done}",
            headlineColor = if (hab.met) a.positive else neutral,
            caption = "of ${hab.total} done",
            detail = hab.detail,
            progress = hab.progress,
            progressColor = if (hab.met) a.positive else a.habits,
            delta = hab.delta,
            empty = !hab.hasAny,
            emptyHint = "Add your first habit  →",
            onClick = onOpenHabits
        )
        CardGap()

        val routine by routineViewModel().state.collectAsStateWithLifecycle()
        val share = if (routine.countedToday == 0) 0f else routine.doneToday / routine.countedToday.toFloat()
        val next = routine.todaySlots.firstOrNull { it.status == RoutineStatus.PENDING }
        SummaryCard(
            emoji = "✅",
            title = "Routine",
            accent = a.positive,
            headline = "${routine.doneToday}",
            headlineColor = if (share >= 0.8f) a.positive else neutral,
            caption = "of ${routine.countedToday} done",
            detail = next?.let { "Next: ${it.item.emoji} ${it.item.title} · ${Dates.clockLabel(it.hour, it.minute)}" }
                ?: if (routine.countedToday > 0) "Nothing left for today" else "Starts with tomorrow's wake-up",
            progress = share,
            progressColor = a.positive,
            empty = !routine.loaded,
            emptyHint = "Open your routine  →",
            onClick = onOpenRoutine
        )
        CardGap()

        val scr = state.screen
        SummaryCard(
            emoji = "📱",
            title = "Screen time",
            accent = a.screen,
            headline = scr.headline,
            headlineColor = if (scr.over) a.negative else neutral,
            caption = "tracked today",
            detail = scr.detail,
            progress = scr.progress,
            progressColor = a.forProgress(scr.progress, scr.over),
            delta = scr.delta,
            higherIsBetter = false,
            showBar = scr.limitMinutes > 0,
            empty = !scr.hasData,
            emptyHint = "Turn on usage access  →",
            onClick = onOpenScreenTime
        )
        CardGap()

        val alarms = state.alarms
        SummaryCard(
            emoji = "⏰",
            title = "Alarms",
            accent = a.alarms,
            headline = alarms.nextClock.orEmpty(),
            headlineColor = a.alarms,
            caption = alarms.nextAt?.let { Dates.untilLabel(it, now) }.orEmpty(),
            detail = alarms.detail,
            progress = 0f,
            progressColor = a.alarms,
            showBar = false,
            empty = !alarms.hasNext,
            emptyHint = if (alarms.total == 0) "Set your first alarm  →" else "Nothing scheduled  →",
            onClick = onOpenAlarms
        )

        Spacer(Modifier.height(Space.xl))
        SectionLabel("Last 7 days")
        Spacer(Modifier.height(Space.md))
        Trends(state)
        Spacer(Modifier.height(Space.xxl))
    }
}

// ---------------------------------------------------------------- hero

@Composable
private fun Hero(state: HomeState, onOpenProfile: () -> Unit) {
    val a = accents()
    val goals = state.goals
    // The score is "more is better", the opposite of a budget, so forProgress is fed the
    // shortfall: everything met stays green, nothing met goes amber rather than alarm-red.
    val ringColor = a.forProgress(1f - state.score, over = false)

    val dateLine = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
    val greeting = remember { greetingFor(LocalTime.now().hour) }
    val doneLine = goals.filter { it.met }.joinToString("  ·  ") { it.label }
        .ifBlank { "Every card below is one tap away" }

    HeroPanel(accent = ringColor) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(greeting, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    dateLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // The way in to the profile. A tab of its own would be a sixth thing in the bar for
            // a screen you visit twice a year.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onOpenProfile)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = "Your profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = state.score,
                color = ringColor,
                centerText = "${state.metGoals}",
                caption = "of ${goals.size} goals",
                size = 120.dp,
                stroke = 12.dp
            )
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    state.scoreLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = ringColor
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    doneLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            goals.forEach { goal -> MiniGoal(goal, Modifier.weight(1f)) }
        }
    }
}

/** One small ring per goal, so the score above can be taken apart at a glance. */
@Composable
private fun MiniGoal(goal: DayGoal, modifier: Modifier = Modifier) {
    val a = accents()
    val color = if (goal.budget) a.forProgress(goal.progress, goal.over)
    else if (goal.met) a.positive else a.byKey(goal.accentKey)
    val track = MaterialTheme.colorScheme.outline
    val swept by animateFloatAsState(goal.progress.coerceIn(0f, 1f), tween(700), label = "mini")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 4.dp.toPx()
                val inset = sw / 2
                val arc = Size(size.width - sw, size.height - sw)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(
                    color, -90f, 360f * swept, false, Offset(inset, inset), arc,
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
            Text(goal.emoji, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            goal.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (goal.met) color else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Still up?"
}

// ---------------------------------------------------------------- right now

/** Shows only while something is actually happening: a running timer, the next alarm. */
@Composable
private fun RightNow(
    state: HomeState,
    now: Long,
    onOpenHabits: () -> Unit,
    onOpenAlarms: () -> Unit
) {
    val a = accents()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        state.running?.let { timer ->
            LivePill(
                accent = a.byKey(timer.accentKey),
                emoji = timer.emoji,
                title = timer.name,
                value = Dates.stopwatch((now - timer.since).coerceAtLeast(0)),
                pulse = true,
                onClick = onOpenHabits,
                modifier = Modifier.weight(1f)
            )
        }
        state.alarms.nextAt?.let { at ->
            LivePill(
                accent = a.alarms,
                emoji = "⏰",
                title = state.alarms.nextLabel ?: "Next alarm",
                value = Dates.untilLabel(at, now),
                onClick = onOpenAlarms,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LivePill(
    accent: Color,
    emoji: String,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pulse: Boolean = false
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.14f),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pulse) {
                val transition = rememberInfiniteTransition(label = "pulse")
                val alpha by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "pulseAlpha"
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = alpha))
                )
                Spacer(Modifier.width(Space.sm))
            } else {
                Text(emoji, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(Space.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------- section cards

/**
 * The shape every section gets: badge, title, the one number that matters, a bar, and a
 * one-line read of where it stands. [empty] swaps all of that for an invitation, so a
 * fresh install shows a way in rather than five flat grey bars.
 */
@Composable
private fun SummaryCard(
    emoji: String,
    title: String,
    accent: Color,
    headline: String,
    headlineColor: Color,
    caption: String,
    detail: String,
    progress: Float,
    progressColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    delta: Double? = null,
    higherIsBetter: Boolean = true,
    showBar: Boolean = true,
    empty: Boolean = false,
    emptyHint: String = "",
    /** An extra line under the detail row, for a card that tracks a second number. */
    footer: @Composable (() -> Unit)? = null
) {
    LifeCard(modifier = modifier, accent = accent, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AccentBadge(emoji, accent)
            Spacer(Modifier.width(Space.md))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!empty) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        headline,
                        style = MaterialTheme.typography.headlineMedium,
                        color = headlineColor,
                        maxLines = 1
                    )
                    if (caption.isNotBlank()) {
                        Text(
                            caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (empty) {
            Spacer(Modifier.height(Space.md))
            Text(emptyHint, style = MaterialTheme.typography.labelLarge, color = accent)
            return@LifeCard
        }

        if (showBar) {
            Spacer(Modifier.height(Space.md))
            ProgressBar(progress, progressColor)
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (delta != null) {
                Spacer(Modifier.width(Space.sm))
                DeltaChip(delta, higherIsBetter = higherIsBetter)
            }
        }
        footer?.invoke()
    }
}

// ---------------------------------------------------------------- trends

/** The two numbers people actually watch, over the week, small enough to stay a footnote. */
@Composable
private fun Trends(state: HomeState) {
    val a = accents()
    LifeCard {
        TrendRow(
            title = "Calories",
            average = averageLabel(state.calories.series) { "%,.0f kcal".format(it) },
            series = state.calories.series,
            from = state.from,
            to = state.today,
            accent = a.calories,
            goal = state.calories.goal.takeIf { it > 0 }?.toDouble(),
            format = { "%,.0f kcal".format(it) },
            // The calorie goal is a ceiling, so being under it is the win.
            goalMeansGood = false
        )
        Spacer(Modifier.height(Space.lg))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Spacer(Modifier.height(Space.lg))
        TrendRow(
            title = "Steps",
            average = averageLabel(state.activity.series) { "%,.0f steps".format(it) },
            series = state.activity.series,
            from = state.from,
            to = state.today,
            accent = a.activity,
            goal = state.activity.goal.takeIf { it > 0 }?.toDouble(),
            format = { "%,.0f steps".format(it) },
            goalMeansGood = true
        )
    }
}

@Composable
private fun TrendRow(
    title: String,
    average: String,
    series: List<DayValue>,
    from: String,
    to: String,
    accent: Color,
    goal: Double?,
    format: (Double) -> String,
    goalMeansGood: Boolean
) {
    var selected by remember { mutableStateOf<String?>(null) }
    val points = series.toDailyPoints(from, to)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            average,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(Space.sm))
    TrendChart(
        points = points,
        accent = accent,
        goal = goal,
        height = 84.dp,
        selectedKey = selected,
        onSelect = { selected = if (it.key == selected) null else it.key }
    )

    val index = points.indexOfFirst { it.key == selected }
    if (index >= 0) {
        ChartDetailSheet(
            detail = dayDetail(
                points = points,
                index = index,
                accent = accent,
                format = format,
                goal = goal,
                goalMeansGood = goalMeansGood
            ),
            onDismiss = { selected = null }
        )
    }
}

/** "avg 1,840 kcal" over the seven-day window, zeros included so a skipped day counts. */
private fun averageLabel(series: List<DayValue>, format: (Double) -> String): String {
    if (series.isEmpty()) return "no data yet"
    return "avg " + format(series.sumOf { it.value } / 7.0)
}
