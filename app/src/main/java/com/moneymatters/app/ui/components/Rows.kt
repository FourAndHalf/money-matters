package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.theme.MoneyTheme

/** Hairline divider used between rows inside a module. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = MoneyTheme.colors.divider)
}

/** Muted key on the left, value on the right. Set [divider] false on the last row of a group. */
@Composable
fun KeyValueRow(
    key: String,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    value: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(key, style = MoneyTheme.type.ledgerMono, color = MoneyTheme.colors.muted, modifier = Modifier.weight(1f))
            value()
        }
        if (divider) RowDivider()
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier, divider: Boolean = true) {
    KeyValueRow(key, modifier, divider) {
        Text(
            value,
            style = MoneyTheme.type.ledgerMono.copy(fontWeight = MoneyTheme.type.metricTable.fontWeight),
            color = MoneyTheme.colors.ink,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Generic list row: leading chip, title over subtitle, trailing content.
 * Transaction, bill and to-do rows are all built from this.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MoneyTheme.type.ledgerMono.copy(fontWeight = MoneyTheme.type.metricTable.fontWeight),
                    color = MoneyTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MoneyTheme.type.bodySm.copy(fontSize = MoneyTheme.type.metricDelta.fontSize),
                        color = MoneyTheme.colors.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing?.invoke()
        }
        if (divider) RowDivider()
    }
}

/** Right-aligned amount with an optional badge beneath (transaction and bill rows). */
@Composable
fun AmountWithBadge(amount: String, badge: (@Composable () -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(amount, style = MoneyTheme.type.ledgerMono.copy(fontWeight = MoneyTheme.type.metricTable.fontWeight), color = MoneyTheme.colors.ink, softWrap = false)
        badge?.invoke()
    }
}

/** 34dp circular chip holding an icon or a short glyph; [background]/[content] override for tones. */
@Composable
fun GlyphChip(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    size: Dp = 34.dp,
    background: Color = MoneyTheme.colors.surfaceLowest,
    content: Color = MoneyTheme.colors.ink,
    bordered: Boolean = true,
) {
    val shape = MoneyTheme.shapes.full
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, MoneyTheme.colors.outline, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
            text != null -> Text(text, style = MoneyTheme.type.labelRegular.copy(fontWeight = MoneyTheme.type.metricDelta.fontWeight), color = content)
        }
    }
}

/** 40dp rounded-square date chip: day over three-letter month. */
@Composable
fun DateChip(day: String, month: String, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(MoneyTheme.colors.surfaceLowest)
            .border(1.dp, MoneyTheme.colors.outline, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(day, style = MoneyTheme.type.metricTable.copy(fontWeight = MoneyTheme.type.metricDelta.fontWeight), color = MoneyTheme.colors.ink)
        Text(
            month.uppercase(),
            style = MoneyTheme.type.labelUppercase.copy(fontSize = MoneyTheme.type.labelUppercase.fontSize * 0.8f),
            color = MoneyTheme.colors.muted,
        )
    }
}
