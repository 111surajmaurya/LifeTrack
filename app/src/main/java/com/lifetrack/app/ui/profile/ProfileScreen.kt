package com.lifetrack.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.ActivityLevel
import com.lifetrack.app.data.Sex
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.CardGap
import com.lifetrack.app.ui.components.HeroPanel
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.StatTile
import com.lifetrack.app.ui.components.TileRow
import com.lifetrack.app.ui.theme.MetricStyle
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents

/**
 * You, as far as the app is concerned: height, weight, age, sex and how much you move.
 *
 * It exists so the calorie and protein goals can be *calculated* rather than guessed at. With
 * "keep my goals in step" on — which is the default — saving here rewrites the daily targets
 * and the four meal budgets immediately; there is no second screen to visit and no button to
 * remember to press.
 *
 * Everything on it is optional. An empty profile is a perfectly normal state: the app then
 * behaves exactly as it did before this screen existed, with whatever goals you typed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel = appViewModel { ProfileViewModel(it) }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val a = accents()

    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(Sex.Unspecified) }
    var level by rememberSaveable { mutableStateOf(ActivityLevel.Moderate) }
    var seeded by rememberSaveable { mutableStateOf(false) }

    // Fill the fields once, from whatever is stored. After that the fields are the source of
    // truth - re-seeding on every emission would fight the keyboard as saves come back.
    LaunchedEffect(state.loaded) {
        if (state.loaded && !seeded) {
            height = ProfileViewModel.display(state.settings.heightCm)
            weight = ProfileViewModel.display(state.settings.weightKg)
            age = state.settings.age.takeIf { it > 0 }?.toString().orEmpty()
            sex = state.sex
            level = state.level
            seeded = true
        }
    }

    val heightValue = height.toFloatOrNull() ?: 0f
    val weightValue = weight.toFloatOrNull() ?: 0f
    val ageValue = age.toIntOrNull() ?: 0
    val preview = state.previewOf(heightValue, weightValue, ageValue, sex, level)

    // Saving on every keystroke would be noisy and would fight the auto-recalculation, so the
    // profile commits when a field settles rather than on a Save button. The field that just
    // changed passes its new text in: the state write has not recomposed yet, so reading the
    // derived values here would save the previous keystroke ("175" would store 17).
    fun commit(h: String = height, w: String = weight, a: String = age) = vm.save(
        h.toFloatOrNull() ?: 0f, w.toFloatOrNull() ?: 0f, a.toIntOrNull() ?: 0, sex, level
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen, bottom = Space.xxl
        )
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                ScreenHeader(
                    title = "Profile",
                    subtitle = "Used to work out your daily targets",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            CardGap()
            BodyHero(preview, state.autoGoals && preview.ready)
        }

        item {
            CardGap()
            SectionLabel("Your body")
            Spacer(Modifier.height(Space.md))
            LifeCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ProfileField("Height", "cm", height, { height = it; commit(h = it) }, Modifier.weight(1f))
                    ProfileField("Weight", "kg", weight, { weight = it; commit(w = it) }, Modifier.weight(1f))
                    ProfileField("Age", "years", age, { age = it; commit(a = it) }, Modifier.weight(1f))
                }

                Spacer(Modifier.height(Space.lg))
                SectionLabel("Sex")
                Spacer(Modifier.height(Space.xs))
                Text(
                    "The BMR formula genuinely differs. Leave it unanswered and it sits midway.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Sex.entries.forEach { option ->
                        PickChip(option.label, option == sex) {
                            sex = option
                            commit()
                        }
                    }
                }

                Spacer(Modifier.height(Space.lg))
                SectionLabel("How active you are")
                Spacer(Modifier.height(Space.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ActivityLevel.entries.forEach { option ->
                        PickChip(option.label, option == level) {
                            level = option
                            commit()
                        }
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    level.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            CardGap()
            SectionLabel("Goals")
            Spacer(Modifier.height(Space.md))
            LifeCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep my goals in step", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.autoGoals)
                                "Calorie, protein and fibre goals follow this profile."
                            else "Your goals are set by hand and stay where they are.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.autoGoals, onCheckedChange = vm::setAutoGoals)
                }

                Spacer(Modifier.height(Space.md))
                TileRow {
                    StatTile(
                        label = "Calories",
                        value = "%,d".format(state.settings.calorieGoal),
                        accent = a.calories,
                        footnote = "a day",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Protein",
                        value = "${state.settings.proteinGoal} g",
                        accent = a.activity,
                        footnote = "a day",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Fibre",
                        value = "${state.settings.fiberGoal} g",
                        accent = a.caution,
                        footnote = "a day",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!state.autoGoals && preview.ready) {
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "This profile would suggest ${"%,d".format(preview.kcal)} kcal, " +
                            "${preview.protein} g protein and ${preview.fiber} g fibre.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.hasProfile) {
            item {
                CardGap()
                TextButton(
                    onClick = {
                        height = ""; weight = ""; age = ""
                        sex = Sex.Unspecified
                        level = ActivityLevel.Moderate
                        vm.clear()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear my profile", color = a.negative)
                }
            }
        }

        item {
            CardGap()
            Text(
                "Mifflin-St Jeor is the formula most nutrition software uses, and the one that " +
                    "validates best against measured resting burn. It is still a population " +
                    "average: two people of the same height, weight and age can differ by a few " +
                    "hundred calories. Treat it as a starting point to adjust from.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/** The number the whole screen exists to produce, with the working shown underneath. */
@Composable
private fun BodyHero(preview: BodyPreview, driving: Boolean) {
    val a = accents()
    HeroPanel(accent = a.calories) {
        if (!preview.ready) {
            Text("Your daily energy", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Space.sm))
            Text(
                "Fill in height, weight and age below and this works out what a day costs you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@HeroPanel
        }

        SectionLabel("Your daily energy")
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%,d".format(preview.kcal), style = MetricStyle, color = a.calories)
            Spacer(Modifier.height(Space.sm))
            Text(
                "  kcal a day",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            "Resting burn ${"%,d".format(preview.bmr)} kcal, before you move.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Space.lg))
        TileRow {
            StatTile(
                label = "Protein",
                value = "${preview.protein} g",
                accent = a.activity,
                footnote = "a day",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Fibre",
                value = "${preview.fiber} g",
                accent = a.caution,
                footnote = "a day",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "BMI",
                value = preview.bmiText,
                accent = a.habits,
                footnote = preview.bmiLabel,
                modifier = Modifier.weight(1f)
            )
        }

        if (driving) {
            Spacer(Modifier.height(Space.md))
            Surface(shape = CircleShape, color = a.positive.copy(alpha = 0.16f)) {
                Text(
                    "Your goals are set from this",
                    Modifier.padding(horizontal = Space.md, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = a.positive
                )
            }
        }
    }
}

/** A number field that saves as soon as focus moves on, rather than on a Save button. */
@Composable
private fun ProfileField(
    label: String,
    unit: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValue(text.filter { it.isDigit() }.take(3)) },
        label = { Text(label) },
        suffix = { Text(unit, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val a = accents()
    Surface(
        shape = CircleShape,
        color = if (selected) a.calories.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, if (selected) a.calories else Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = Space.md, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) a.calories else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
