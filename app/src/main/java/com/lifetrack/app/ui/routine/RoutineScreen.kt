package com.lifetrack.app.ui.routine

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.Routine
import com.lifetrack.app.data.RoutineKind
import com.lifetrack.app.data.RoutineSlot
import com.lifetrack.app.data.RoutineStart
import com.lifetrack.app.data.RoutineStatus
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.DotSeparated
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ProgressRing
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.ScreenPadding
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.StatTile
import com.lifetrack.app.ui.components.TileRow
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents

/**
 * The routine tracker: today's fixed points with a hit or a miss against each, tomorrow's
 * plan, and a month of dots per item so a slipping habit is visible before it is gone.
 *
 * Items with evidence (meals, the walk, the plan, the wake-up alarm) tick themselves; the
 * rest are a tap here or "Done" on their notification.
 */
@Composable
fun RoutineScreen(
    onPlan: () -> Unit,
    vm: RoutineViewModel = routineViewModel()
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val accent = accents().habits

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            ScreenHeader(
                title = "Routine",
                subtitle = if (ui.loaded) "${ui.doneToday} of ${ui.countedToday} done today" else null,
                trailing = { Button(onClick = onPlan, shape = MaterialTheme.shapes.large) { Text("Plan tomorrow") } }
            )
        }
        if (!ui.loaded) return@LazyColumn

        item { ScoreCard(ui, accent) }
        item {
            TodayCard(
                slots = ui.todaySlots,
                accent = accent,
                onDone = { vm.mark(it, if (it.status == RoutineStatus.DONE && !it.auto) null else Routine.DONE) },
                onMissed = { vm.mark(it, if (it.status == RoutineStatus.MISSED) null else Routine.MISSED) }
            )
        }
        item { TomorrowCard(ui, onPlan) }
        item { HistoryCard(ui) }
    }
}

@Composable
internal fun routineViewModel(): RoutineViewModel {
    val context = LocalContext.current
    return appViewModel { RoutineViewModel(it, RoutineStart.ensure(context)) }
}

// ---------------------------------------------------------------- score

@Composable
private fun ScoreCard(ui: RoutineUi, accent: Color) {
    val a = accents()
    val share = if (ui.countedToday == 0) 0f else ui.doneToday / ui.countedToday.toFloat()
    LifeCard {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = share,
                color = if (share >= 0.8f) a.positive else accent,
                centerText = "${ui.doneToday}/${ui.countedToday}",
                caption = "today",
                size = 150.dp,
                stroke = 12.dp
            )
        }
        Spacer(Modifier.height(Space.md))
        TileRow {
            StatTile("7 days", percent(ui.week), rateColor(ui.week), Modifier.weight(1f))
            StatTile("30 days", percent(ui.month), rateColor(ui.month), Modifier.weight(1f))
            val best = ui.tracks.maxByOrNull { it.streak }
            StatTile(
                "Best streak",
                "${best?.streak ?: 0}d",
                accent,
                Modifier.weight(1f),
                footnote = best?.takeIf { it.streak > 0 }?.item?.title
            )
        }
    }
}

// ---------------------------------------------------------------- today

@Composable
private fun TodayCard(
    slots: List<RoutineSlot>,
    accent: Color,
    onDone: (RoutineSlot) -> Unit,
    onMissed: (RoutineSlot) -> Unit
) {
    val a = accents()
    LifeCard {
        SectionLabel("Today")
        Spacer(Modifier.height(Space.sm))
        slots.forEachIndexed { index, slot ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(slot.item.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        slot.item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (slot.status == RoutineStatus.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        listOfNotNull(
                            Dates.clockLabel(slot.hour, slot.minute),
                            if (slot.item.isAlarm) "alarm" else null,
                            statusNote(slot)
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(slot.status, a.positive, a.negative)
                    )
                }
                if (slot.status != RoutineStatus.OFF) {
                    FilledTonalIconToggleButton(
                        checked = slot.status == RoutineStatus.DONE,
                        onCheckedChange = { onDone(slot) },
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            checkedContainerColor = a.positive.copy(alpha = 0.85f),
                            checkedContentColor = MaterialTheme.colorScheme.surface
                        )
                    ) { Text("✓", fontWeight = FontWeight.Bold) }
                    FilledTonalIconToggleButton(
                        checked = slot.status == RoutineStatus.MISSED,
                        onCheckedChange = { onMissed(slot) },
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            checkedContainerColor = a.negative.copy(alpha = 0.85f),
                            checkedContentColor = MaterialTheme.colorScheme.surface
                        )
                    ) { Text("✗", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun statusNote(slot: RoutineSlot): String? = when (slot.status) {
    RoutineStatus.DONE -> if (slot.auto) autoReason(slot) else "done"
    RoutineStatus.MISSED -> "missed"
    RoutineStatus.OFF -> "off today"
    RoutineStatus.PENDING -> null
}

private fun autoReason(slot: RoutineSlot): String = when (slot.item.kindType) {
    RoutineKind.MEAL -> "logged"
    RoutineKind.WALK -> "walked"
    RoutineKind.PLAN -> "planned"
    else -> "done"
}

// ---------------------------------------------------------------- tomorrow

@Composable
private fun TomorrowCard(ui: RoutineUi, onPlan: () -> Unit) {
    val a = accents()
    LifeCard(accent = if (ui.tomorrowPlanned) a.positive else a.caution, onClick = onPlan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Tomorrow", Modifier.weight(1f))
            Text(
                if (ui.tomorrowPlanned) "Planned ✓" else "Not planned yet",
                style = MaterialTheme.typography.labelLarge,
                color = if (ui.tomorrowPlanned) a.positive else a.caution
            )
        }
        Spacer(Modifier.height(Space.xs))
        val on = ui.tomorrow.filter { it.enabled }
        Text(
            if (on.isEmpty()) "Everything is switched off for tomorrow."
            else on.joinToString("   ") { "${it.item.emoji} ${Dates.clockLabel(it.hour, it.minute)}" },
            style = MaterialTheme.typography.bodyMedium
        )
        if (!ui.tomorrowPlanned) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "Usual times apply unless you change them. Tap to plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- history

@Composable
private fun HistoryCard(ui: RoutineUi) {
    val a = accents()
    LifeCard {
        SectionLabel("Last $HISTORY_DAYS days")
        Spacer(Modifier.height(Space.xs))
        Text(
            "One dot a day: green hit, red missed, grey off or not yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.md))
        ui.tracks.forEach { track ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${track.item.emoji} ${track.item.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                DotSeparated(
                    if (track.streak > 0) "🔥 ${track.streak}d" else "no streak",
                    percent(track.hitRate)
                )
            }
            Spacer(Modifier.height(Space.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                track.days.forEach { day ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(statusColor(day.status, a.positive, a.negative, idle = MaterialTheme.colorScheme.outline))
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
        }
    }
}

// ---------------------------------------------------------------- helpers

@Composable
private fun statusColor(
    status: RoutineStatus,
    good: Color,
    bad: Color,
    idle: Color = MaterialTheme.colorScheme.onSurfaceVariant
): Color = when (status) {
    RoutineStatus.DONE -> good
    RoutineStatus.MISSED -> bad
    else -> idle
}

@Composable
private fun rateColor(rate: Float?): Color {
    val a = accents()
    return when {
        rate == null -> MaterialTheme.colorScheme.onSurfaceVariant
        rate >= 0.8f -> a.positive
        rate >= 0.5f -> a.caution
        else -> a.negative
    }
}

private fun percent(rate: Float?): String = rate?.let { "${(it * 100).toInt()}%" } ?: "–"
