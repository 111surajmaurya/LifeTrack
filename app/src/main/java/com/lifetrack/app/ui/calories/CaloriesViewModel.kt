package com.lifetrack.app.ui.calories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.FoodItem
import com.lifetrack.app.data.DailyValue
import com.lifetrack.app.data.Meal
import com.lifetrack.app.data.Nutrients
import com.lifetrack.app.data.Portion
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Serving
import com.lifetrack.app.data.Settings
import com.lifetrack.app.data.Slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** "1,240" — every kcal figure on the tab goes through this so they all read the same. */
internal fun kcalText(value: Int): String = "%,d".format(value)

/** "48 g" — likewise for every gram figure, protein and fibre alike. */
internal fun gramText(value: Float): String =
    if (value >= 10f) "%,.0f g".format(value) else "%.1f g".format(value)

/** Kept for the places that still hand in a whole number, like a goal. */
internal fun proteinText(value: Int): String = "%,d g".format(value)

/**
 * One day of eating, plus the settings that decide what counts as too much.
 * Everything the tab renders is derived here rather than in the composables, so the
 * "am I over?" question has exactly one answer.
 */
data class CaloriesState(
    val date: String = Dates.today(),
    val meals: List<Meal> = emptyList(),
    val settings: Settings = Settings(),
    val loaded: Boolean = false
) {
    val goal: Int get() = settings.calorieGoal
    val eaten: Int get() = meals.sumOf { it.kcal }
    val remaining: Int get() = goal - eaten
    val over: Boolean get() = eaten > goal

    /** Share of the daily goal eaten so far; can exceed 1f, which is what drives the ring. */
    val ratio: Float get() = if (goal > 0) eaten / goal.toFloat() else 0f

    // ---- everything else the day added up, in one place ----

    /** The whole panel for the day. Summed once; every readout below reads off it. */
    val totals: Nutrients get() = Nutrients.sum(meals.map { it.nutrients })

    // Protein and fibre read the opposite way round to calories: hitting the number is the win.

    val proteinGoal: Int get() = settings.proteinGoal
    val protein: Float get() = totals.protein
    val proteinLeft: Float get() = (proteinGoal - protein).coerceAtLeast(0f)
    val proteinRatio: Float get() = if (proteinGoal > 0) protein / proteinGoal else 0f
    val proteinMet: Boolean get() = proteinGoal > 0 && protein >= proteinGoal

    val fiberGoal: Int get() = settings.fiberGoal
    val fiber: Float get() = totals.fiber
    val fiberLeft: Float get() = (fiberGoal - fiber).coerceAtLeast(0f)
    val fiberRatio: Float get() = if (fiberGoal > 0) fiber / fiberGoal else 0f
    val fiberMet: Boolean get() = fiberGoal > 0 && fiber >= fiberGoal

    /** Micronutrients as a share of a day's reference intake, which is the only readable unit. */
    val vitARatio: Float get() = totals.vitA / DailyValue.VIT_A_UG
    val vitCRatio: Float get() = totals.vitC / DailyValue.VIT_C_MG
    val ironRatio: Float get() = totals.iron / DailyValue.IRON_MG
    val calciumRatio: Float get() = totals.calcium / DailyValue.CALCIUM_MG

    /** The meal everything gets filed under until the user picks a different one. */
    val activeSlot: Slot get() = settings.activeSlotType
    val isToday: Boolean get() = Dates.isToday(date)

    fun mealsIn(slot: Slot): List<Meal> = meals.filter { it.slotType == slot }
    fun kcalIn(slot: Slot): Int = meals.sumOf { if (it.slotType == slot) it.kcal else 0 }
    fun proteinIn(slot: Slot): Float =
        meals.fold(0f) { acc, m -> if (m.slotType == slot) acc + m.protein else acc }

    fun fiberIn(slot: Slot): Float =
        meals.fold(0f) { acc, m -> if (m.slotType == slot) acc + m.fiber else acc }
    fun goalIn(slot: Slot): Int = settings.goalFor(slot)
}

/**
 * Everything the calorie settings dialog can change, in one bundle.
 *
 * Grouped rather than passed as five positional arguments, and applied field by field rather
 * than by writing a whole [Settings] row back - a wholesale write from a snapshot taken when
 * the dialog opened would clobber the running habit timer if one started while it was open.
 *
 * The body profile is deliberately *not* here: it lives on the profile screen, and saving these
 * goals by hand is what switches `autoGoals` off. Two screens writing the same fields would
 * mean whichever was saved last silently won.
 */
data class GoalEdits(
    val dailyGoal: Int,
    val slotGoals: Map<Slot, Int>,
    val itemWarnKcal: Int,
    val proteinGoal: Int,
    val fiberGoal: Int
)

/**
 * The Calories tab.
 *
 * Two independent streams: the day being viewed (meals + settings) and the food search.
 * They are kept apart because the search re-queries on every keystroke and the day does not.
 */
class CaloriesViewModel(private val repo: Repository) : ViewModel() {

    private val date = MutableStateFlow(Dates.today())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The date travels with its own meals, so switching days can never pair a new date with
     * the previous day's rows for a frame.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayMeals: Flow<Pair<String, List<Meal>>> =
        date.flatMapLatest { day -> repo.mealsOn(day).map { day to it } }

    val state: StateFlow<CaloriesState> =
        combine(dayMeals, repo.settings) { (day, meals), settings ->
            CaloriesState(date = day, meals = meals, settings = settings, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaloriesState())

    /**
     * Search results, or the most-eaten suggestions while the box is empty. The debounce only
     * applies to real typing — clearing the field should snap straight back to suggestions.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<FoodItem>> = _query
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { q -> if (q.isBlank()) repo.foodSuggestions() else repo.searchFoods(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------------------------------------------------------- day

    fun shiftDay(days: Long) {
        val next = Dates.shift(date.value, days)
        if (!Dates.isFuture(next)) date.value = next
    }

    fun goToToday() { date.value = Dates.today() }

    // ---------------------------------------------------------------- search

    fun setQuery(text: String) { _query.value = text }

    // ---------------------------------------------------------------- logging

    /** The control the whole tab hangs off: what the next thing you add gets filed under. */
    fun setActiveSlot(slot: Slot) = viewModelScope.launch { repo.setActiveSlot(slot) }

    fun log(item: FoodItem, qty: Float, portion: Portion, slot: Slot) = viewModelScope.launch {
        repo.logFood(date.value, item, qty, portion, slot)
    }

    /** One tap from a result row: a single medium serving into the active slot. */
    fun quickLog(item: FoodItem) = viewModelScope.launch {
        repo.logFood(date.value, item, 1f, Portion.Medium, state.value.activeSlot)
    }

    fun addCustom(name: String, kcal: Int, protein: Float, fiber: Float, serving: Serving, slot: Slot) =
        viewModelScope.launch {
            repo.addCustomFoodAndLog(
                date.value, name, kcal, protein.coerceAtLeast(0f), fiber.coerceAtLeast(0f),
                serving, 1f, Portion.Medium, slot
            )
        }

    fun delete(meal: Meal) = viewModelScope.launch { repo.deleteMeal(meal) }

    /** Filed it under the wrong meal — move it without retyping anything. */
    fun move(meal: Meal, slot: Slot) = viewModelScope.launch {
        repo.updateMeal(meal.copy(slot = slot.name))
    }

    // ---------------------------------------------------------------- settings

    fun saveSettings(edits: GoalEdits) = viewModelScope.launch {
        repo.setCalorieGoal(edits.dailyGoal.coerceIn(GOAL_MIN, GOAL_MAX))
        edits.slotGoals.forEach { (slot, kcal) -> repo.setSlotGoal(slot, kcal.coerceIn(0, GOAL_MAX)) }
        repo.setItemWarnKcal(edits.itemWarnKcal.coerceIn(WARN_MIN, GOAL_MAX))
        repo.setProteinGoal(edits.proteinGoal.coerceIn(PROTEIN_MIN, PROTEIN_MAX))
        repo.setFiberGoal(edits.fiberGoal.coerceIn(FIBER_MIN, FIBER_MAX))
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 180L
        const val GOAL_MIN = 800
        const val GOAL_MAX = 6000
        const val WARN_MIN = 50
        const val PROTEIN_MIN = 10
        const val PROTEIN_MAX = 400
        const val FIBER_MIN = 5
        const val FIBER_MAX = 100

        /** 25 / 35 / 10 / 30 of the daily goal — the helper behind "auto-split". */
        fun autoSplit(dailyGoal: Int): Map<Slot, Int> =
            Slot.entries.associateWith { (dailyGoal * Slot.defaultShare(it)).roundToInt() }
    }
}
