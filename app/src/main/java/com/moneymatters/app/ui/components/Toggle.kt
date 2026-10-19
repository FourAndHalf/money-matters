package com.moneymatters.app.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneymatters.app.ui.theme.MoneyTheme

/** Themed on/off switch (DESIGN.md has no bespoke toggle; this maps the palette onto Material3's). */
@Composable
fun Toggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAnchor,
            checkedTrackColor = c.anchor,
            uncheckedThumbColor = c.surfaceLowest,
            uncheckedTrackColor = c.surfaceHigh,
            uncheckedBorderColor = c.outline,
        ),
    )
}
