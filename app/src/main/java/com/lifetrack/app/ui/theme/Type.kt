package com.lifetrack.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.lifetrack.app.R

/**
 * Type is three Google Fonts, bundled as variable TTFs in `res/font` rather than fetched
 * through the downloadable-fonts provider. Bundling costs about 470 KB and buys a font that
 * is correct on first frame, offline, and on devices without Play Services - the downloadable
 * path silently falls back to Roboto in all three of those cases.
 *
 * They are variable fonts (one file, a continuous weight axis), so each weight below is the
 * same file with a different `wght` setting. That needs API 26, which is already the minimum.
 *
 *  - **Outfit** for numbers and headings: geometric and wide, so "1,840 kcal" reads as a
 *    display figure rather than a form field.
 *  - **Plus Jakarta Sans** for body: rounder than Roboto and easier to read at 13-14sp.
 *  - **JetBrains Mono** for the stopwatch, because in a proportional font the digits shift
 *    sideways a hundred times a second while the timer runs.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, vararg weights: FontWeight): FontFamily = FontFamily(
    weights.map { w ->
        Font(
            resId = resId,
            weight = w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))
        )
    }
)

val DisplayFamily: FontFamily = variable(
    R.font.outfit,
    FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold
)

val BodyFamily: FontFamily = variable(
    R.font.plus_jakarta_sans,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold
)

val MonoFamily: FontFamily = variable(
    R.font.jetbrains_mono,
    FontWeight.Normal, FontWeight.Medium, FontWeight.Bold
)

/** Centres the glyphs in their line box, which large display figures need. */
private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

val LifeTrackTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFamily, fontSize = 56.sp, lineHeight = 60.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, lineHeightStyle = Trim
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFamily, fontSize = 40.sp, lineHeight = 46.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp, lineHeightStyle = Trim
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily, fontSize = 30.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, lineHeightStyle = Trim
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFamily, fontSize = 30.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily, fontSize = 26.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily, fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily, fontSize = 20.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFamily, fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFamily, fontSize = 11.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
    ),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(
        fontFamily = BodyFamily, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFamily, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium
    )
)

/** The running stopwatch. Monospaced so the digits sit still. */
val StopwatchStyle = TextStyle(
    fontFamily = MonoFamily, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp
)

/** The hundredths tail after the stopwatch, deliberately smaller and quieter. */
val StopwatchTailStyle = TextStyle(
    fontFamily = MonoFamily, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp
)

/** Big numbers inside rings and stat tiles. */
val MetricStyle = TextStyle(
    fontFamily = DisplayFamily, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.8).sp
)
