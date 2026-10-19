package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme

enum class PillStyle { Secondary, Primary, OnDark, Destructive }

/**
 * Compact pill button used for module actions ("View all", "Close", "October ▾").
 * [PillStyle.Primary] is the charcoal anchor; use it at most once per module.
 */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Secondary,
    height: Dp = 30.dp,
    trailingIcon: ImageVector? = null,
) {
    val c = MoneyTheme.colors
    val (bg, fg, stroke) = when (style) {
        PillStyle.Secondary -> Triple(c.surfaceLowest, c.ink, c.outline)
        PillStyle.Primary -> Triple(c.anchor, c.onAnchor, c.anchor)
        PillStyle.OnDark -> Triple(androidx.compose.ui.graphics.Color.Transparent, c.onAnchor, c.onAnchor.copy(alpha = 0.3f))
        // Irreversible actions (DESIGN.md → Buttons): white pill, red border and text, never one tap alone.
        PillStyle.Destructive -> Triple(c.surfaceLowest, c.negative, c.negative)
    }
    val shape = MoneyTheme.shapes.full
    Row(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .border(1.dp, stroke, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Centered, not just spaced: a compact pill is unaffected, but a fillMaxWidth() action
        // button (Edit / Reverse / Post prior-period adjustment) would otherwise hug the left edge.
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Text(text, style = MoneyTheme.type.labelRegular, color = fg, maxLines = 1)
        if (trailingIcon != null) Icon(trailingIcon, null, tint = fg, modifier = Modifier.size(10.dp))
    }
}

/** 44dp white circle with an outline icon; optional unread dot (notifications). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    showDot: Boolean = false,
) {
    val c = MoneyTheme.colors
    val shape = MoneyTheme.shapes.full
    Box(
        modifier = modifier
            .size(size)
            .softShadow(shape, strength = 0.6f)
            .clip(shape)
            .background(c.surfaceLowest)
            .border(1.dp, c.outline, shape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c.ink, modifier = Modifier.size(iconSize))
        if (showDot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-11).dp, y = 10.dp)
                    .size(8.dp)
                    .clip(shape)
                    .background(c.surfaceLowest)
                    .padding(1.5.dp)
                    .clip(shape)
                    .background(c.negative),
            )
        }
    }
}

/** Small 32dp `↗` button that opens the full page behind a module. */
@Composable
fun ExpandButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CircleIconButton(
        icon = MoneyIcons.ArrowUpRight,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = 32.dp,
        iconSize = 14.dp,
    )
}

/** Quick Add: 56dp charcoal circle with a white plus. */
@Composable
fun QuickAddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    val shape = MoneyTheme.shapes.full
    Box(
        modifier = modifier
            .size(56.dp)
            .softShadow(shape, strength = 3f)
            .clip(shape)
            .background(c.anchor)
            .clickable(role = Role.Button, onClickLabel = "Quick add", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(MoneyIcons.Plus, "Quick add", tint = c.onAnchor, modifier = Modifier.size(24.dp))
    }
}
