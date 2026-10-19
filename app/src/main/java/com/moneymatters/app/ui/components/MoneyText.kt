package com.moneymatters.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.theme.MoneyTheme

/**
 * An amount in Indian format with the paise portion one tone lighter: `₹18,420`**`.00`**.
 * Use for hero and card metrics; plain lists can use [formatInr] directly.
 */
@Composable
fun MoneyText(
    amount: Paise,
    style: TextStyle,
    modifier: Modifier = Modifier,
    showPaise: Boolean = true,
    color: Color = MoneyTheme.colors.ink,
    paiseColor: Color = MoneyTheme.colors.muted,
) {
    val full = amount.formatInr(showPaise = showPaise)
    val text = if (!showPaise) {
        buildAnnotatedString { append(full) }
    } else {
        val dot = full.lastIndexOf('.')
        buildAnnotatedString {
            append(full.substring(0, dot))
            withStyle(SpanStyle(color = paiseColor)) { append(full.substring(dot)) }
        }
    }
    Text(text, style = style, color = color, modifier = modifier, maxLines = 1)
}
