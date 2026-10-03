package com.lifetrack.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrack.app.data.ActivityLevel
import com.lifetrack.app.data.BodyMath
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.Settings
import com.lifetrack.app.data.Sex
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The profile screen's state.
 *
 * [draft] is what the fields currently hold, which is not the same as what is saved: the
 * numbers below it update as you type so you can see what a different weight or activity level
 * would do before committing to it.
 */
data class ProfileState(
    val settings: Settings = Settings(),
    val loaded: Boolean = false
) {
    val sex: Sex get() = settings.sexType
    val level: ActivityLevel get() = settings.activity
    val hasProfile: Boolean get() = settings.hasBodyProfile
    val autoGoals: Boolean get() = settings.autoGoals

    /** Live preview for whatever the fields currently say. */
    fun previewOf(heightCm: Float, weightKg: Float, age: Int, sex: Sex, level: ActivityLevel) =
        BodyPreview(
            bmr = BodyMath.bmr(sex, weightKg, heightCm, age),
            kcal = BodyMath.tdee(sex, weightKg, heightCm, age, level),
            protein = BodyMath.protein(weightKg, level),
            fiber = BodyMath.fiber(BodyMath.tdee(sex, weightKg, heightCm, age, level)),
            bmi = bmiOf(weightKg, heightCm)
        )

    companion object {
        fun bmiOf(weightKg: Float, heightCm: Float): Float {
            if (weightKg <= 0f || heightCm <= 0f) return 0f
            val m = heightCm / 100f
            return weightKg / (m * m)
        }
    }
}

/** What the formula says for a given body, recomputed on every keystroke. */
data class BodyPreview(
    val bmr: Int = 0,
    val kcal: Int = 0,
    val protein: Int = 0,
    val fiber: Int = 0,
    val bmi: Float = 0f
) {
    val ready: Boolean get() = kcal > 0

    /**
     * WHO categories. Shown because it falls out of height and weight for free, with the caveat
     * attached: BMI says nothing about what the weight is made of, and reads high for anyone
     * carrying real muscle.
     */
    val bmiLabel: String get() = when {
        bmi <= 0f -> ""
        bmi < 18.5f -> "underweight"
        bmi < 25f -> "healthy range"
        bmi < 30f -> "overweight"
        else -> "obese"
    }

    val bmiText: String get() = if (bmi <= 0f) "—" else "%.1f".format(bmi)
}

class ProfileViewModel(private val repo: Repository) : ViewModel() {

    val state: StateFlow<ProfileState> = repo.settings
        .map { ProfileState(settings = it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState())

    fun save(heightCm: Float, weightKg: Float, age: Int, sex: Sex, level: ActivityLevel) =
        viewModelScope.launch {
            repo.setBodyProfile(heightCm, weightKg, age, sex, level)
        }

    fun setAutoGoals(on: Boolean) = viewModelScope.launch { repo.setAutoGoals(on) }

    /** Clears the profile. Goals keep whatever value they had; only the body is forgotten. */
    fun clear() = viewModelScope.launch {
        repo.setAutoGoals(false)
        repo.setBodyProfile(0f, 0f, 0, Sex.Unspecified, ActivityLevel.Moderate)
    }

    companion object {
        /** Rounded to whole units - nobody enters their height to a tenth of a centimetre. */
        fun display(value: Float): String = if (value <= 0f) "" else value.roundToInt().toString()
    }
}
