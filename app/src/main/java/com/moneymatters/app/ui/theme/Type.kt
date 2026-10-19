package com.moneymatters.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moneymatters.app.R

/**
 * JetBrains Mono, bundled (OFL) so the app never depends on downloadable fonts.
 * Weights 400/500/600 only; nothing heavier (design/DESIGN.md → Typography).
 * The font has no ₹ glyph, so Android falls back to the system font for that one character.
 */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = JetBrainsMono,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    fontFeatureSettings = "tnum",
)

/** Type scale; names match the `typography` tokens in design/DESIGN.md. */
@Immutable
data class MoneyTypography(
    val headlineXl: TextStyle = style(30, 38, FontWeight.Medium, -0.03),
    val headlineXlMobile: TextStyle = style(22, 28, FontWeight.Medium, -0.03),
    val headlineLg: TextStyle = style(18, 24, FontWeight.Medium, -0.02),
    val headlineMd: TextStyle = style(14, 20, FontWeight.SemiBold, -0.01),
    val bodyLg: TextStyle = style(15, 22, FontWeight.Normal),
    val bodyMd: TextStyle = style(13, 19, FontWeight.Normal),
    val bodySm: TextStyle = style(11, 15, FontWeight.Normal),
    val metricHero: TextStyle = style(34, 40, FontWeight.Medium, -0.045),
    val metricHeroMobile: TextStyle = style(32, 38, FontWeight.Medium, -0.045),
    val metricCard: TextStyle = style(18, 24, FontWeight.Medium, -0.03),
    val metricTable: TextStyle = style(13, 18, FontWeight.Medium, -0.01),
    val metricDelta: TextStyle = style(10, 12, FontWeight.SemiBold),
    val ledgerMono: TextStyle = style(12, 18, FontWeight.Normal),
    val labelUppercase: TextStyle = style(10, 12, FontWeight.SemiBold, 0.08),
    val labelRegular: TextStyle = style(11, 14, FontWeight.Medium),
)
