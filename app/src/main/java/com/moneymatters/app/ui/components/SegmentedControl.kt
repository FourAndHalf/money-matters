package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.theme.MoneyTheme

/**
 * A small, fixed set of mutually exclusive options in one pill container (design/DESIGN.md →
 * Segmented controls). The selected segment is a raised white/surface pill; others are muted
 * text. Prefer this over toggled [Pill]s for a short, fixed choice like "Everyday / Accountant".
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val c = MoneyTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(MoneyTheme.shapes.full)
            .background(c.surface)
            .padding(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(MoneyTheme.shapes.full)
                    .then(if (isSelected) Modifier.background(c.surfaceLowest).softShadow(MoneyTheme.shapes.full, strength = 0.5f) else Modifier)
                    .semantics { selected = isSelected }
                    .clickable(role = Role.Tab, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = MoneyTheme.type.labelRegular, color = if (isSelected) c.ink else c.muted)
            }
        }
    }
}
