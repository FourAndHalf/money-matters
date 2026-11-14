package com.moneymatters.app.feature.market

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun rupeesLabel(v: Double) = "₹" + String.format(Locale.US, "%,.2f", v)

internal fun percentLabel(v: Double?) = v?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—"

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun dayLabel(d: LocalDate) = d.format(DAY)

/** Green for up, red for down, plain ink for flat or unknown. */
@Composable
internal fun changeColor(v: Double?): Color {
    val c = MoneyTheme.colors
    return when {
        v == null || v == 0.0 -> c.ink
        v > 0 -> c.positive
        else -> c.negative
    }
}
