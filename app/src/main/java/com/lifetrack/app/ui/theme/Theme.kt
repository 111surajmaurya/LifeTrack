package com.lifetrack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------- palette
//
// Near-black ground with saturated accents, one per thing you track, so a glance at a
// screenshot tells you which tab you are on. Green always means "on track / under budget",
// red always means "over / behind" - never decoration, only state.

val Ink = Color(0xFF0E1116)          // app background, dark
val Slab = Color(0xFF161A21)         // card, dark
val SlabHigh = Color(0xFF1E242D)     // raised card, dark
val Chalk = Color(0xFFF2F4F3)        // text on dark
val Ash = Color(0xFF8E9AA8)          // secondary text on dark
val Hair = Color(0xFF262D37)         // hairline on dark

val Snow = Color(0xFFF7F8F7)         // app background, light
val Card = Color(0xFFFFFFFF)         // card, light
val CardHigh = Color(0xFFF0F2F1)     // raised card, light
val Coal = Color(0xFF11161C)         // text on light
val Stone = Color(0xFF5F6B78)        // secondary text on light
val Wire = Color(0xFFE3E7E9)         // hairline on light

// Semantic. These two carry meaning and are used nowhere decorative.
val PositiveLight = Color(0xFF16A34A)
val PositiveDark = Color(0xFF4ADE80)
val NegativeLight = Color(0xFFDC2626)
val NegativeDark = Color(0xFFFB7185)
val CautionLight = Color(0xFFD97706)
val CautionDark = Color(0xFFFBBF24)

// Per-domain accents.
val LimeLight = Color(0xFF3F9142); val LimeDark = Color(0xFF86EFAC)   // habits
val FlameLight = Color(0xFFE0562B); val FlameDark = Color(0xFFFB923C) // calories
val AquaLight = Color(0xFF0E7490); val AquaDark = Color(0xFF67E8F9)   // activity / steps
val VioletLight = Color(0xFF7C3AED); val VioletDark = Color(0xFFC4B5FD) // screen time
val RoseLight = Color(0xFFBE185D); val RoseDark = Color(0xFFF9A8D4)   // alarms

private val Light = lightColorScheme(
    primary = LimeLight, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCF3DE), onPrimaryContainer = Color(0xFF10331A),
    secondary = FlameLight, onSecondary = Color.White,
    tertiary = AquaLight, onTertiary = Color.White,
    background = Snow, onBackground = Coal,
    surface = Snow, onSurface = Coal,
    surfaceVariant = Card, onSurfaceVariant = Stone,
    surfaceContainerHighest = CardHigh,
    surfaceContainerHigh = CardHigh,
    surfaceContainer = Card,
    outline = Wire, outlineVariant = Wire,
    error = NegativeLight, onError = Color.White
)

private val Dark = darkColorScheme(
    primary = LimeDark, onPrimary = Color(0xFF06210E),
    primaryContainer = Color(0xFF1B3823), onPrimaryContainer = Color(0xFFCDF0D5),
    secondary = FlameDark, onSecondary = Color(0xFF2A1206),
    tertiary = AquaDark, onTertiary = Color(0xFF04262E),
    background = Ink, onBackground = Chalk,
    surface = Ink, onSurface = Chalk,
    surfaceVariant = Slab, onSurfaceVariant = Ash,
    surfaceContainerHighest = SlabHigh,
    surfaceContainerHigh = SlabHigh,
    surfaceContainer = Slab,
    outline = Hair, outlineVariant = Hair,
    error = NegativeDark, onError = Color(0xFF33070C)
)

/**
 * Named colours resolved for the current scheme. Screens read these instead of picking
 * hex values, so light and dark stay in step and "green means good" holds everywhere.
 */
data class Accents(
    val positive: Color,
    val negative: Color,
    val caution: Color,
    val habits: Color,
    val calories: Color,
    val activity: Color,
    val screen: Color,
    val alarms: Color
) {
    /** Green when you are inside the budget, red once you are past it. */
    fun forProgress(progress: Float, over: Boolean = progress > 1f): Color = when {
        over -> negative
        progress > 0.85f -> caution
        else -> positive
    }

    /** Green for a gain, red for a loss, muted for no change. */
    fun forDelta(delta: Double, neutral: Color, higherIsBetter: Boolean = true): Color = when {
        delta == 0.0 -> neutral
        (delta > 0) == higherIsBetter -> positive
        else -> negative
    }

    fun byKey(key: String): Color = when (key) {
        "leaf", "habits" -> habits
        "clay", "calories" -> calories
        "sky", "activity" -> activity
        "plum", "screen" -> screen
        "rose", "alarms" -> alarms
        else -> habits
    }
}

private val LightAccents = Accents(
    positive = PositiveLight, negative = NegativeLight, caution = CautionLight,
    habits = LimeLight, calories = FlameLight, activity = AquaLight,
    screen = VioletLight, alarms = RoseLight
)

private val DarkAccents = Accents(
    positive = PositiveDark, negative = NegativeDark, caution = CautionDark,
    habits = LimeDark, calories = FlameDark, activity = AquaDark,
    screen = VioletDark, alarms = RoseDark
)

val LocalAccents = staticCompositionLocalOf { LightAccents }

/** `accents().positive`, available anywhere under LifeTrackTheme. */
@Composable @ReadOnlyComposable
fun accents(): Accents = LocalAccents.current

// ---------------------------------------------------------------- shape, spacing, elevation

/**
 * One spacing scale, in multiples of 4dp. Screens use [Space] rather than loose numbers so
 * gaps line up between tabs written at different times.
 */
object Space {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 28.dp

    /** Left/right inset shared by every screen. */
    val screen: Dp = 20.dp

    /** Vertical gap between cards in a list. */
    val cards: Dp = 12.dp
}

/**
 * Three levels, and only three:
 *  - [flat] plain content sitting on the background,
 *  - [raised] a normal card,
 *  - [floating] something that needs to lift off the page (sheets, the alarm screen).
 */
object Elevation {
    val flat: Dp = 0.dp
    val raised: Dp = 1.dp
    val floating: Dp = 6.dp
}

private val LifeTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/** A soft two-stop wash used behind headline numbers and hero panels. */
fun accentWash(color: Color) = Brush.verticalGradient(
    listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.04f))
)

/** Left-to-right sweep for progress fills. */
fun accentSweep(color: Color) = Brush.horizontalGradient(
    listOf(color.copy(alpha = 0.75f), color)
)

@Composable
fun LifeTrackTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccents provides if (dark) DarkAccents else LightAccents) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = LifeTrackTypography,
            shapes = LifeTrackShapes,
            content = content
        )
    }
}
