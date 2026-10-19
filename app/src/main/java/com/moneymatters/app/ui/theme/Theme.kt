package com.moneymatters.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }
private val LocalMoneyTypography = staticCompositionLocalOf { MoneyTypography() }
private val LocalMoneyShapes = staticCompositionLocalOf { MoneyShapes() }
private val LocalMoneySpacing = staticCompositionLocalOf { MoneySpacing() }

/** Access point for Pearl Ledger tokens: `MoneyTheme.colors.ink`, `MoneyTheme.type.metricHero`, … */
object MoneyTheme {
    val colors: MoneyColors
        @Composable @ReadOnlyComposable get() = LocalMoneyColors.current
    val type: MoneyTypography
        @Composable @ReadOnlyComposable get() = LocalMoneyTypography.current
    val shapes: MoneyShapes
        @Composable @ReadOnlyComposable get() = LocalMoneyShapes.current
    val spacing: MoneySpacing
        @Composable @ReadOnlyComposable get() = LocalMoneySpacing.current
}

@Composable
fun MoneyMattersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkMoneyColors else LightMoneyColors
    val type = MoneyTypography()
    CompositionLocalProvider(
        LocalMoneyColors provides colors,
        LocalMoneyTypography provides type,
    ) {
        // Material 3 is mapped from the same tokens so stock components (sheets, dialogs, text fields) match.
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = type.toMaterial(),
            content = content,
        )
    }
}

private fun MoneyColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = anchor,
        onPrimary = onAnchor,
        primaryContainer = anchorPressed,
        onPrimaryContainer = onAnchor,
        secondary = muted,
        onSecondary = surfaceLowest,
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = ink,
        tertiary = champagne,
        onTertiary = surfaceLowest,
        tertiaryContainer = champagneContainer,
        onTertiaryContainer = ink,
        error = negative,
        onError = surfaceLowest,
        errorContainer = negativeContainer,
        onErrorContainer = negative,
        background = canvas,
        onBackground = ink,
        surface = surfaceLow,
        onSurface = ink,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = muted,
        surfaceContainerLowest = surfaceLowest,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh,
        outline = outline,
        outlineVariant = divider,
        inverseSurface = anchor,
        inverseOnSurface = onAnchor,
        scrim = ink.copy(alpha = 0.28f),
    )
}

private fun MoneyTypography.toMaterial() = Typography(
    displayLarge = metricHero,
    displayMedium = metricHeroMobile,
    displaySmall = headlineXl,
    headlineLarge = headlineXl,
    headlineMedium = headlineXlMobile,
    headlineSmall = headlineLg,
    titleLarge = headlineLg,
    titleMedium = headlineMd,
    titleSmall = headlineMd,
    bodyLarge = bodyLg,
    bodyMedium = bodyMd,
    bodySmall = bodySm,
    labelLarge = labelRegular,
    labelMedium = labelRegular,
    labelSmall = labelUppercase,
)
