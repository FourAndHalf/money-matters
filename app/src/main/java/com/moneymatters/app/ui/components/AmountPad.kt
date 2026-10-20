package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.parseRupees
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme

private val KeySize = 64.dp
private val PAD_ROWS = listOf(
    listOf("7", "8", "9", "⌫"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf(".", "0", "00", ""),
)

/**
 * The amount-entry calculator sheet called for by design/DESIGN.md ("amount entry uses
 * metric-hero-mobile and a calculator keypad sheet"). Only `+`/`−` chain multiple amounts
 * (e.g. an itemized receipt) — there's no `×`/`÷`, since those would multiply or divide by a
 * plain count, not another rupee amount, and this keeps every key here a rupee digit or an
 * operator between rupee amounts (never a float).
 */
@Composable
fun AmountPad(
    onDigit: (String) -> Unit,
    onOperator: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.sm)) {
        PAD_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.sm)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(KeySize))
                        "⌫" -> CircleIconButton(MoneyIcons.Backspace, "Delete", onBackspace, size = KeySize, iconSize = 22.dp)
                        "+", "−" -> PadKey(key) { onOperator(key[0]) }
                        else -> PadKey(key) { onDigit(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(label: String, onClick: () -> Unit) {
    val c = MoneyTheme.colors
    val shape = MoneyTheme.shapes.md
    Box(
        Modifier
            .size(KeySize)
            .softShadow(shape, strength = 0.6f)
            .clip(shape)
            .background(c.surfaceLowest)
            .border(1.dp, c.outline, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MoneyTheme.type.headlineMd, color = c.ink)
    }
}

private val TOKEN = Regex("""\d+(?:\.\d{0,2})?|[+−]""")

/**
 * Evaluates a chain of rupee amounts typed on [AmountPad] (e.g. "120+45+15") left to right.
 * Null for anything that doesn't parse, or that ends mid-operator.
 */
fun evaluateAmountExpression(expression: String): Paise? {
    if (expression.isBlank()) return null
    val tokens = TOKEN.findAll(expression).map { it.value }.toList()
    if (tokens.isEmpty() || tokens.size % 2 == 0) return null
    var total = parseRupees(tokens[0]) ?: return null
    var i = 1
    while (i < tokens.size) {
        val op = tokens[i]
        val operand = tokens.getOrNull(i + 1)?.let { parseRupees(it) } ?: return null
        total = if (op == "+") total + operand else total - operand
        i += 2
    }
    return total
}
