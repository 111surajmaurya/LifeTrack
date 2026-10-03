package com.lifetrack.app.ui.calories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.FoodItem
import com.lifetrack.app.data.Catalogue
import com.lifetrack.app.data.FoodSeed
import com.lifetrack.app.data.Meal
import com.lifetrack.app.data.Portion
import com.lifetrack.app.data.ActivityLevel
import com.lifetrack.app.data.BodyMath
import com.lifetrack.app.data.Serving
import com.lifetrack.app.data.Sex
import com.lifetrack.app.data.Settings
import com.lifetrack.app.data.Slot
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.AccentBadge
import com.lifetrack.app.ui.components.CardGap
import com.lifetrack.app.ui.components.DotSeparated
import com.lifetrack.app.ui.components.EmptyState
import com.lifetrack.app.ui.components.HeroPanel
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.OverflowBar
import com.lifetrack.app.ui.components.ProgressBar
import com.lifetrack.app.ui.components.ProgressRing
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents
import androidx.compose.foundation.lazy.LazyColumn
import kotlin.math.abs
import kotlin.math.roundToInt

/** How many search results fit before the list stops being scannable. */
private const val MAX_RESULTS = 6

/**
 * A colour per meal slot, for the donut and breakdown on the detail screen.
 * Deliberately skips green and the semantic red: in this app those two only ever mean
 * "under budget" and "over budget", and a slice is neither.
 */
@Composable
internal fun slotColor(slot: Slot): Color {
    val a = accents()
    return when (slot) {
        Slot.Breakfast -> a.activity
        Slot.Lunch -> a.calories
        Slot.Snacks -> a.screen
        Slot.Dinner -> a.alarms
    }
}

@Composable
fun CaloriesScreen(
    onOpenDetail: () -> Unit,
    vm: CaloriesViewModel = appViewModel { CaloriesViewModel(it) }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()

    var picking by remember { mutableStateOf<FoodItem?>(null) }
    var addingCustom by remember { mutableStateOf(false) }
    var editingSettings by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen, bottom = Space.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            ScreenHeader(
                title = "Calories",
                subtitle = "${kcalText(state.eaten)} of ${kcalText(state.goal)} kcal",
                trailing = {
                    IconButton(onClick = { editingSettings = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Calorie settings")
                    }
                }
            )
        }

        item { DayStrip(state, onShift = vm::shiftDay, onToday = vm::goToToday) }

        item { CaloriesHero(state) }

        item {
            SlotSelector(
                state = state,
                onSelect = vm::setActiveSlot
            )
        }

        item {
            SearchCard(
                query = query,
                results = results,
                activeSlot = state.activeSlot,
                onQuery = vm::setQuery,
                onQuickAdd = vm::quickLog,
                onOpen = { picking = it },
                onAddCustom = { addingCustom = true }
            )
        }

        item {
            SectionLabel("Logged  ·  ${Dates.label(state.date)}")
        }

        if (state.meals.isEmpty()) {
            item {
                LifeCard {
                    EmptyState(
                        symbol = "🍽",
                        title = if (state.loaded) "Nothing logged yet" else "Loading…",
                        body = "Pick the meal you're eating above, then search a food and tap +."
                    )
                }
            }
        } else {
            Slot.entries.filter { state.mealsIn(it).isNotEmpty() }.forEach { slot ->
                item(key = "slot-${slot.name}") {
                    SlotLogCard(
                        slot = slot,
                        meals = state.mealsIn(slot),
                        kcal = state.kcalIn(slot),
                        protein = state.proteinIn(slot),
                        goal = state.goalIn(slot),
                        warnKcal = state.settings.itemWarnKcal,
                        onDelete = vm::delete,
                        onMove = vm::move
                    )
                }
            }
        }

        item {
            Column {
                CardGap()
                TrendsCard(onOpenDetail)
            }
        }
    }

    picking?.let { item ->
        FoodPickerSheet(
            item = item,
            activeSlot = state.activeSlot,
            onDismiss = { picking = null },
            onConfirm = { qty, portion, slot ->
                vm.log(item, qty, portion, slot)
                picking = null
            }
        )
    }

    if (addingCustom) {
        CustomFoodDialog(
            defaultSlot = state.activeSlot,
            onDismiss = { addingCustom = false },
            onAdd = { name, kcal, protein, fiber, serving, slot ->
                vm.addCustom(name, kcal, protein, fiber, serving, slot)
                addingCustom = false
            }
        )
    }

    if (editingSettings) {
        CalorieSettingsDialog(
            settings = state.settings,
            onDismiss = { editingSettings = false },
            onSave = { edits ->
                vm.saveSettings(edits)
                editingSettings = false
            }
        )
    }
}

// ---------------------------------------------------------------- day + hero

@Composable
private fun DayStrip(state: CaloriesState, onShift: (Long) -> Unit, onToday: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onShift(-1L) }) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous day")
        }
        Text(
            Dates.label(state.date),
            Modifier
                .clip(CircleShape)
                .clickable(enabled = !state.isToday, onClick = onToday)
                .padding(horizontal = Space.md, vertical = Space.xs),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = { onShift(1L) }, enabled = !state.isToday) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next day")
        }
    }
}

/**
 * The ring answers "how much have I eaten today", because that is the number you are actually
 * tracking. What is left is the derived figure, so it sits underneath in small type - the old
 * layout had them the other way round and a glance at the tab told you nothing about intake.
 */
@Composable
private fun CaloriesHero(state: CaloriesState) {
    val a = accents()
    // The tab keeps its own accent right up to the goal; only going over changes the meaning,
    // and forProgress is what decides what "over" looks like.
    val ring = if (state.over) a.forProgress(state.ratio, over = true) else a.calories

    HeroPanel(accent = ring) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = state.ratio,
                color = ring,
                centerText = kcalText(state.eaten),
                caption = "eaten of ${kcalText(state.goal)}",
                overflow = (state.ratio - 1f).coerceIn(0f, 1f),
                below = {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        if (state.over) "${kcalText(abs(state.remaining))} over"
                        else "${kcalText(state.remaining)} left",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.over) a.negative else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${state.meals.size} item${if (state.meals.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
        Spacer(Modifier.height(Space.md))
        GoalMeter(
            label = "Protein",
            value = state.protein,
            goal = state.proteinGoal,
            ratio = state.proteinRatio,
            met = state.proteinMet,
            left = state.proteinLeft
        )
        Spacer(Modifier.height(Space.md))
        GoalMeter(
            label = "Fibre",
            value = state.fiber,
            goal = state.fiberGoal,
            ratio = state.fiberRatio,
            met = state.fiberMet,
            left = state.fiberLeft
        )
        Spacer(Modifier.height(Space.md))
        MicronutrientRow(state)
    }
}

/**
 * Protein and fibre read the opposite way to calories: reaching the number is the win, so
 * these turn green on arrival instead of red, and there is no "over".
 */
@Composable
private fun GoalMeter(
    label: String,
    value: Float,
    goal: Int,
    ratio: Float,
    met: Boolean,
    left: Float
) {
    val a = accents()
    val colour = if (met) a.positive else a.activity

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                gramText(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colour
            )
            Text(
                " / ${proteinText(goal)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Space.xs))
        ProgressBar(ratio, colour)
        Spacer(Modifier.height(Space.xs))
        Text(
            if (met) "target met" else "${gramText(left)} to go",
            style = MaterialTheme.typography.labelSmall,
            color = if (met) a.positive else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Vitamins and minerals as a share of a day, because nobody reads 340 ug of vitamin A as
 * anything. Four small dials rather than four more bars: they are context, not targets you
 * are meant to chase, and a day that lands short of one is not a day you failed.
 */
@Composable
private fun MicronutrientRow(state: CaloriesState) {
    val a = accents()
    Column(Modifier.fillMaxWidth()) {
        SectionLabel("Vitamins & minerals")
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            MicroDial("Vit A", state.vitARatio, a.caution, Modifier.weight(1f))
            MicroDial("Vit C", state.vitCRatio, a.positive, Modifier.weight(1f))
            MicroDial("Iron", state.ironRatio, a.negative, Modifier.weight(1f))
            MicroDial("Calcium", state.calciumRatio, a.activity, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            "Share of a day's reference intake (ICMR-NIN adult).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MicroDial(label: String, ratio: Float, accent: Color, modifier: Modifier = Modifier) {
    val pct = (ratio * 100f).roundToInt()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$pct%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (ratio >= 1f) accents().positive else accent
        )
        Spacer(Modifier.height(2.dp))
        ProgressBar(ratio, accent, height = 4.dp)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- slot selector

@Composable
private fun SlotSelector(state: CaloriesState, onSelect: (Slot) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Filing under", Modifier.weight(1f))
            Text(
                state.activeSlot.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accents().calories
            )
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Slot.entries.forEach { slot ->
                SlotPill(
                    slot = slot,
                    kcal = state.kcalIn(slot),
                    goal = state.goalIn(slot),
                    active = slot == state.activeSlot,
                    onClick = { onSelect(slot) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * One of the four meal buttons. This is a *control*, not a readout — the selected one is
 * where the next food lands — so the active state is a filled, outlined, lifted pill rather
 * than a tint you have to hunt for.
 */
@Composable
private fun SlotPill(
    slot: Slot,
    kcal: Int,
    goal: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val a = accents()
    val over = goal > 0 && kcal > goal
    val tint = if (over) a.negative else a.calories

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (active) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            if (active) 2.dp else 1.dp,
            if (active) tint else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(slot.emoji, style = MaterialTheme.typography.titleMedium)
            Text(
                slot.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                kcalText(kcal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                color = if (over) a.negative else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(5.dp))
            OverflowBar(kcal.toFloat(), goal.toFloat(), height = 5.dp)
            Spacer(Modifier.height(5.dp))
            Text(
                if (over) "over by ${kcal - goal}" else "of ${kcalText(goal)}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = if (over) a.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------- search

@Composable
private fun SearchCard(
    query: String,
    results: List<FoodItem>,
    activeSlot: Slot,
    onQuery: (String) -> Unit,
    onQuickAdd: (FoodItem) -> Unit,
    onOpen: (FoodItem) -> Unit,
    onAddCustom: () -> Unit
) {
    val a = accents()
    LifeCard(padding = PaddingValues(Space.md)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search food — try \"da\"") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(Modifier.height(Space.sm))
        Text(
            if (query.isBlank()) "What you eat most  ·  + adds one medium to ${activeSlot.label}"
            else "Tap a row for portions  ·  + adds one medium to ${activeSlot.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.xs))

        if (results.isEmpty()) {
            Text(
                if (query.isBlank()) "The catalogue is still loading."
                else "Nothing matched \"$query\". Add it below and it stays in the list.",
                Modifier.padding(vertical = Space.md),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            results.take(MAX_RESULTS).forEach { item ->
                FoodResultRow(item, onQuickAdd = { onQuickAdd(item) }, onOpen = { onOpen(item) })
            }
        }

        HorizontalDivider(Modifier.padding(vertical = Space.sm))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onAddCustom)
                .padding(vertical = Space.sm, horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentBadge("➕", a.calories, size = 34.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Add a food that isn't listed", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "It joins the catalogue, so next time you just type it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FoodResultRow(item: FoodItem, onQuickAdd: () -> Unit, onOpen: () -> Unit) {
    val a = accents()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .padding(vertical = Space.sm, horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccentBadge(Catalogue.emojiFor(item.category), a.calories, size = 34.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.kcal} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.protein > 0f) {
                    Text(
                        "  ·  ${gramText(item.protein)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = a.activity
                    )
                }
                Text(
                    "  ·  ${item.describe(1f, Portion.Medium)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(Space.sm))
        Surface(
            shape = CircleShape,
            color = a.calories.copy(alpha = 0.16f),
            modifier = Modifier
                .size(34.dp)
                .clickable(onClick = onQuickAdd)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Add one ${item.name}",
                    tint = a.calories,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- the log

@Composable
private fun SlotLogCard(
    slot: Slot,
    meals: List<Meal>,
    kcal: Int,
    protein: Float,
    goal: Int,
    warnKcal: Int,
    onDelete: (Meal) -> Unit,
    onMove: (Meal, Slot) -> Unit
) {
    val a = accents()
    val over = goal > 0 && kcal > goal
    val headline = if (over) a.negative else MaterialTheme.colorScheme.onSurface

    LifeCard(accent = if (over) a.negative else null, padding = PaddingValues(Space.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(slot.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(slot.label, style = MaterialTheme.typography.titleLarge, color = headline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${kcalText(kcal)} / ${kcalText(goal)} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) a.negative else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (protein > 0) {
                        Text(
                            "  ·  ${gramText(protein)} protein",
                            style = MaterialTheme.typography.bodySmall,
                            color = a.activity
                        )
                    }
                }
            }
            if (over) OverChip("over by ${kcalText(kcal - goal)}")
        }

        Spacer(Modifier.height(Space.md))
        OverflowBar(kcal.toFloat(), goal.toFloat())
        Spacer(Modifier.height(Space.sm))

        meals.forEach { meal ->
            MealRow(
                meal = meal,
                warnKcal = warnKcal,
                onDelete = { onDelete(meal) },
                onMove = { onMove(meal, it) }
            )
        }
    }
}

@Composable
private fun OverChip(text: String) {
    val a = accents()
    Surface(shape = CircleShape, color = a.negative.copy(alpha = 0.16f)) {
        Text(
            text,
            Modifier.padding(horizontal = Space.sm, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = a.negative
        )
    }
}

/**
 * One logged item. Anything at or past the heavy threshold is called out — red number,
 * faint red bed, and a word for it — because a 600 kcal snack hiding in a tidy grey list
 * was the thing that made the old log useless.
 */
@Composable
private fun MealRow(meal: Meal, warnKcal: Int, onDelete: () -> Unit, onMove: (Slot) -> Unit) {
    val a = accents()
    val heavy = warnKcal > 0 && meal.kcal >= warnKcal
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (heavy) a.negative.copy(alpha = 0.09f) else Color.Transparent)
            .padding(start = Space.sm, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meal.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (heavy) {
                    Spacer(Modifier.width(Space.sm))
                    Surface(shape = CircleShape, color = a.negative.copy(alpha = 0.18f)) {
                        Text(
                            "heavy",
                            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = a.negative
                        )
                    }
                }
            }
            Text(
                meal.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Space.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                kcalText(meal.kcal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (heavy) a.negative else MaterialTheme.colorScheme.onSurface
            )
            if (meal.protein > 0f) {
                Text(
                    gramText(meal.protein),
                    style = MaterialTheme.typography.labelSmall,
                    color = a.activity
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Options for ${meal.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Slot.entries.filter { it != meal.slotType }.forEach { target ->
                    DropdownMenuItem(
                        text = { Text("Move to ${target.label}") },
                        leadingIcon = { Text(target.emoji) },
                        onClick = {
                            menu = false
                            onMove(target)
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = a.negative) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = a.negative)
                    },
                    onClick = {
                        menu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun TrendsCard(onOpenDetail: () -> Unit) {
    val a = accents()
    LifeCard(accent = a.calories, onClick = onOpenDetail) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AccentBadge("📈", a.calories)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("See weekly & monthly trends", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Daily average, where the calories go, best and worst days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- picker sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodPickerSheet(
    item: FoodItem,
    activeSlot: Slot,
    onDismiss: () -> Unit,
    onConfirm: (Float, Portion, Slot) -> Unit
) {
    val a = accents()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var qty by remember(item.id) { mutableStateOf(1f) }
    var portion by rememberSaveable(item.id) { mutableStateOf(Portion.Medium) }
    var slot by rememberSaveable(item.id) { mutableStateOf(activeSlot) }
    val kcal = item.kcalFor(qty, portion)
    val protein = item.proteinFor(qty, portion)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xxl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentBadge(Catalogue.emojiFor(item.category), a.calories)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${item.kcal} kcal" +
                            (if (item.protein > 0f) " · ${gramText(item.protein)}" else "") +
                            (if (item.fiber > 0f) " · ${gramText(item.fiber)} fibre" else "") +
                            " per ${item.describe(1f, Portion.Medium).removePrefix("1 ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Both totals move with the stepper, so you can see the cost before committing.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${kcalText(kcal)} kcal",
                        style = MaterialTheme.typography.headlineSmall,
                        color = a.calories
                    )
                    if (protein > 0f) {
                        Text(
                            gramText(protein),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = a.activity
                        )
                    }
                }
            }

            // Only bowls, plates, glasses and cups have a size; rotis and eggs are just counted.
            if (item.servingType.sized) {
                Spacer(Modifier.height(Space.xl))
                SectionLabel("Portion")
                Spacer(Modifier.height(Space.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Portion.entries.forEach { p ->
                        ChoiceChip(
                            label = p.label,
                            selected = p == portion,
                            onClick = { portion = p },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.xl))
            SectionLabel("How many")
            Spacer(Modifier.height(Space.sm))
            Stepper(
                value = qty,
                unit = item.servingType.unit,
                onChange = { qty = it }
            )

            Spacer(Modifier.height(Space.xl))
            SectionLabel("Goes to")
            Spacer(Modifier.height(Space.sm))
            SlotChips(selected = slot, onSelect = { slot = it })

            Spacer(Modifier.height(Space.xl))
            Button(
                onClick = { onConfirm(qty, portion, slot) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "Add ${item.describe(qty, portion)}  ·  ${kcalText(kcal)} kcal" +
                        (if (protein > 0f) "  ·  ${gramText(protein)}" else ""),
                    Modifier.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun Stepper(value: Float, unit: String, onChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(Icons.Rounded.Remove, "One less", enabled = value > 1f) {
            onChange((value - 1f).coerceAtLeast(1f))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (value % 1f == 0f) value.toInt().toString() else value.toString(),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                unit + (if (value > 1f) "s" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StepButton(Icons.Rounded.Add, "One more", enabled = value < 20f) {
            onChange((value + 1f).coerceAtMost(20f))
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val a = accents()
    val tint = if (enabled) a.calories else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = if (enabled) 0.16f else 0.06f),
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint)
        }
    }
}

// ---------------------------------------------------------------- dialogs

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomFoodDialog(
    defaultSlot: Slot,
    onDismiss: () -> Unit,
    onAdd: (String, Int, Float, Float, Serving, Slot) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var fiber by rememberSaveable { mutableStateOf("") }
    var serving by rememberSaveable { mutableStateOf(Serving.Serve) }
    var slot by rememberSaveable { mutableStateOf(defaultSlot) }
    val kcalValue = kcal.toIntOrNull() ?: 0
    // Protein and fibre are optional - plenty of foods are close enough to zero that forcing
    // a number would just teach you to type 0.
    val proteinValue = protein.toFloatOrNull() ?: 0f
    val fiberValue = fiber.toFloatOrNull() ?: 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New food") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Saved to the catalogue, so you only type it once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.lg))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    NumberField(
                        label = "Calories",
                        value = kcal,
                        onValue = { kcal = it },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "Protein (g)",
                        value = protein,
                        onValue = { protein = it },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "Fibre (g)",
                        value = fiber,
                        onValue = { fiber = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "All per serving. Leave protein or fibre blank if you do not know them.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.lg))
                SectionLabel("Served as")
                Spacer(Modifier.height(Space.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Serving.entries.forEach { s ->
                        ChoiceChip(
                            label = s.unit.replaceFirstChar { it.uppercase() },
                            selected = s == serving,
                            onClick = { serving = s }
                        )
                    }
                }
                Spacer(Modifier.height(Space.lg))
                SectionLabel("Goes to")
                Spacer(Modifier.height(Space.sm))
                SlotChips(selected = slot, onSelect = { slot = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), kcalValue, proteinValue, fiberValue, serving, slot) },
                enabled = name.isNotBlank() && kcalValue > 0
            ) { Text("Add & log") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalorieSettingsDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onSave: (GoalEdits) -> Unit
) {
    var daily by rememberSaveable { mutableStateOf(settings.calorieGoal.toString()) }
    var warn by rememberSaveable { mutableStateOf(settings.itemWarnKcal.toString()) }
    var proteinGoal by rememberSaveable { mutableStateOf(settings.proteinGoal.toString()) }
    var fiberGoal by rememberSaveable { mutableStateOf(settings.fiberGoal.toString()) }

    val slotGoals = remember {
        mutableStateMapOf<Slot, String>().apply {
            Slot.entries.forEach { put(it, settings.goalFor(it).toString()) }
        }
    }
    val slotSum = Slot.entries.sumOf { slotGoals[it]?.toIntOrNull() ?: 0 }
    val dailyValue = daily.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calorie settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NumberField(
                    label = "Daily goal (kcal)",
                    value = daily,
                    onValue = { daily = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Space.lg))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Per-meal budgets", Modifier.weight(1f))
                    TextButton(onClick = {
                        CaloriesViewModel.autoSplit(dailyValue).forEach { (slot, kcal) ->
                            slotGoals[slot] = kcal.toString()
                        }
                    }) { Text("Auto-split") }
                }
                Slot.entries.forEach { slot ->
                    Spacer(Modifier.height(Space.sm))
                    NumberField(
                        label = "${slot.emoji}  ${slot.label}",
                        value = slotGoals[slot].orEmpty(),
                        onValue = { slotGoals[slot] = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Budgets add up to ${kcalText(slotSum)} of ${kcalText(dailyValue)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (slotSum > dailyValue && dailyValue > 0) accents().negative
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Space.lg))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    NumberField(
                        label = "Protein goal (g)",
                        value = proteinGoal,
                        onValue = { proteinGoal = it },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "Fibre goal (g)",
                        value = fiberGoal,
                        onValue = { fiberGoal = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (settings.goalsAreCalculated)
                        "Currently calculated from your profile. Typing a goal here takes over " +
                            "and turns that off."
                    else "Whole-day numbers, not per meal. ICMR-NIN suggests 30 g of fibre.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (settings.goalsAreCalculated) accents().positive
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Space.xl))
                NumberField(
                    label = "Flag single items over (kcal)",
                    value = warn,
                    onValue = { warn = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        GoalEdits(
                            dailyGoal = dailyValue,
                            slotGoals = Slot.entries.associateWith { slotGoals[it]?.toIntOrNull() ?: 0 },
                            itemWarnKcal = warn.toIntOrNull() ?: settings.itemWarnKcal,
                            proteinGoal = proteinGoal.toIntOrNull() ?: settings.proteinGoal,
                            fiberGoal = fiberGoal.toIntOrNull() ?: settings.fiberGoal,
                        )
                    )
                },
                enabled = dailyValue >= CaloriesViewModel.GOAL_MIN &&
                    (warn.toIntOrNull() ?: 0) >= CaloriesViewModel.WARN_MIN &&
                    (proteinGoal.toIntOrNull() ?: 0) >= CaloriesViewModel.PROTEIN_MIN &&
                    (fiberGoal.toIntOrNull() ?: 0) >= CaloriesViewModel.FIBER_MIN
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------- small shared bits

@Composable
private fun SlotChips(selected: Slot, onSelect: (Slot) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Slot.entries.forEach { s ->
            ChoiceChip(
                label = s.label,
                selected = s == selected,
                onClick = { onSelect(s) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val a = accents()
    Surface(
        shape = CircleShape,
        color = if (selected) a.calories.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, if (selected) a.calories else Color.Transparent),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = Space.md, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) a.calories else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Digits only, so a stray letter can never turn a goal into zero. */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValue(text.filter { it.isDigit() }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
