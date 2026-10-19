package com.moneymatters.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Pearl Ledger color roles (design/DESIGN.md → Colors).
 * Hierarchy comes from tone; semantic colors are for meaning only and always appear small.
 */
@Immutable
data class MoneyColors(
    val isDark: Boolean,
    /** Page behind all modules. */
    val canvas: Color,
    /** Raised controls: icon buttons, pills, chips. */
    val surfaceLowest: Color,
    /** Module (card) surface. */
    val surfaceLow: Color,
    /** Inner panel inside a module. */
    val surface: Color,
    val surfaceHigh: Color,
    val ink: Color,
    val muted: Color,
    val outline: Color,
    /** List dividers inside modules. */
    val divider: Color,
    /** The single strong tone: selected nav, primary button, active bar. Inverts to light in dark theme. */
    val anchor: Color,
    val anchorPressed: Color,
    val onAnchor: Color,
    /** Inactive chart bars and progress tracks. */
    val barIdle: Color,
    /** Bars over a threshold (e.g. days above the daily allowance). */
    val barMid: Color,
    /** Empty dot in dot-matrix visuals. */
    val dotEmpty: Color,
    val positive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val negativeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    /** Champagne accent: premium moments only, one per screen. */
    val champagne: Color,
    val champagneContainer: Color,
)

val LightMoneyColors = MoneyColors(
    isDark = false,
    canvas = Color(0xFFE8E8EA),
    surfaceLowest = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFF1F1F3),
    surface = Color(0xFFECECEE),
    surfaceHigh = Color(0xFFE6E6E9),
    ink = Color(0xFF1C1C1E),
    muted = Color(0xFF626267),
    outline = Color(0xFFDADADD),
    divider = Color(0xFFE0E0E3),
    anchor = Color(0xFF2A2A2D),
    anchorPressed = Color(0xFF3A3A3D),
    onAnchor = Color(0xFFFFFFFF),
    barIdle = Color(0xFFC9C9CD),
    barMid = Color(0xFF8E8E93),
    dotEmpty = Color(0xFFD4D4D8),
    positive = Color(0xFF23784A),
    positiveContainer = Color(0xFFDDF0E4),
    negative = Color(0xFFB33A35),
    negativeContainer = Color(0xFFF9DEDC),
    warning = Color(0xFF8A5A0F),
    warningContainer = Color(0xFFF6E7CC),
    info = Color(0xFF3F5F80),
    infoContainer = Color(0xFFDFE8F1),
    champagne = Color(0xFF7A6A47),
    champagneContainer = Color(0xFFEDE6D6),
)

val DarkMoneyColors = MoneyColors(
    isDark = true,
    canvas = Color(0xFF0F0F11),
    surfaceLowest = Color(0xFF232326),
    surfaceLow = Color(0xFF161618),
    surface = Color(0xFF1C1C1F),
    surfaceHigh = Color(0xFF232326),
    ink = Color(0xFFECECEE),
    muted = Color(0xFFA0A0A6),
    outline = Color(0xFF2C2C30),
    divider = Color(0xFF26262A),
    anchor = Color(0xFFECECEE),
    anchorPressed = Color(0xFFD6D6DA),
    onAnchor = Color(0xFF161618),
    barIdle = Color(0xFF3A3A3E),
    barMid = Color(0xFF6A6A70),
    dotEmpty = Color(0xFF34343A),
    positive = Color(0xFF5BBF86),
    positiveContainer = Color(0xFF1C3326),
    negative = Color(0xFFE5726B),
    negativeContainer = Color(0xFF3A1F1D),
    warning = Color(0xFFD9A54A),
    warningContainer = Color(0xFF362A14),
    info = Color(0xFF8FB1D4),
    infoContainer = Color(0xFF1D2834),
    champagne = Color(0xFFCDBB92),
    champagneContainer = Color(0xFF302A1E),
)
