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
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme

private val KeySize = 72.dp

/**
 * A 3×4 digit keypad for passcode entry: 1–9, then [onBiometric] (when given), 0 and backspace.
 * Keys are 72dp white circles, matching [CircleIconButton]'s look at a thumb-friendly size.
 */
@Composable
fun NumberKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onBiometric: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.md)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.lg)) {
                row.forEach { digit -> DigitKey(digit, enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.lg)) {
            if (onBiometric != null) {
                CircleIconButton(MoneyIcons.Fingerprint, "Use biometrics", onBiometric, size = KeySize, iconSize = 26.dp)
            } else {
                Spacer(Modifier.size(KeySize))
            }
            DigitKey('0', enabled) { onDigit('0') }
            CircleIconButton(MoneyIcons.Backspace, "Delete", { if (enabled) onBackspace() }, size = KeySize, iconSize = 24.dp)
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    val c = MoneyTheme.colors
    val shape = MoneyTheme.shapes.full
    Box(
        Modifier
            .size(KeySize)
            .softShadow(shape, strength = 0.6f)
            .clip(shape)
            .background(c.surfaceLowest)
            .border(1.dp, c.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(digit.toString(), style = MoneyTheme.type.headlineXlMobile, color = if (enabled) c.ink else c.muted)
    }
}

/** One dot per passcode digit: filled as digits are typed; all in [isError]'s negative color after a miss. */
@Composable
fun PasscodeDots(length: Int, filled: Int, modifier: Modifier = Modifier, isError: Boolean = false) {
    val c = MoneyTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(length) { i ->
            val color = when {
                isError -> c.negative
                i < filled -> c.anchor
                else -> c.dotEmpty
            }
            Box(Modifier.size(12.dp).clip(MoneyTheme.shapes.full).background(color))
        }
    }
}
