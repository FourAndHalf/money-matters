package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.theme.MoneyTheme

@Immutable
data class NavItem(val label: String, val icon: ImageVector)

/**
 * Floating bottom bar. The active item sits on an anchor pill; inactive items are muted.
 * The surface is a near-opaque pearl; true backdrop blur is not available in Compose without
 * extra libraries, and DESIGN.md allows a solid fallback.
 */
@Composable
fun MoneyBottomBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MoneyTheme.colors
    val shape = MoneyTheme.shapes.xl
    val glass = if (c.isDark) c.surfaceLow.copy(alpha = 0.98f) else Color(0xFFF7F7F8).copy(alpha = 0.98f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .softShadow(shape, strength = 2f)
            .clip(shape)
            .background(glass)
            .border(1.dp, if (c.isDark) c.outline else Color.White.copy(alpha = 0.9f), shape)
            .padding(horizontal = 6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val on = i == selected
            val fg = if (on) c.onAnchor else c.muted
            Column(
                modifier = Modifier
                    .size(64.dp, 54.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) c.anchor else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Icon(item.icon, null, tint = fg, modifier = Modifier.size(20.dp))
                Text(item.label, style = MoneyTheme.type.metricDelta.copy(fontWeight = MoneyTheme.type.labelRegular.fontWeight, fontSize = MoneyTheme.type.metricDelta.fontSize * 0.9f), color = fg)
            }
        }
    }
}
