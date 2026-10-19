package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.theme.MoneyTheme

/** Soft two-layer resting shadow for raised tiles (design/DESIGN.md → Elevation). */
fun Modifier.softShadow(shape: Shape, strength: Float = 1f): Modifier = this
    .dropShadow(shape, Shadow(radius = 2.dp, color = ShadowInk.copy(alpha = 0.04f * strength), offset = DpOffset(0.dp, 1.dp)))
    .dropShadow(shape, Shadow(radius = 24.dp, color = ShadowInk.copy(alpha = 0.06f * strength), offset = DpOffset(0.dp, 8.dp)))

internal val ShadowInk = Color(0xFF141419)

/** Hairline highlight on module edges; nearly invisible in dark theme. */
@Composable
private fun highlight(): Color = if (MoneyTheme.colors.isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.75f)

/**
 * A bento module: rounded tile on the canvas. Use one per dashboard section.
 * Pass [background] to swap the surface for a gradient (only the Leeway hero does this).
 */
@Composable
fun Module(
    modifier: Modifier = Modifier,
    shape: Shape = MoneyTheme.shapes.lg,
    background: Brush? = null,
    contentPadding: Dp = MoneyTheme.spacing.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MoneyTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(shape)
            .clip(shape)
            .then(if (background != null) Modifier.background(background) else Modifier.background(colors.surfaceLow))
            .border(1.dp, highlight(), shape)
            .padding(contentPadding),
        content = content,
    )
}

/** A panel nested inside a module: one tone darker, no shadow. */
@Composable
fun InnerPanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MoneyTheme.shapes.md)
            .background(MoneyTheme.colors.surface)
            .padding(contentPadding),
        content = content,
    )
}

/** Module title, optional muted subtitle and a top-right action (pill or round icon button). */
@Composable
fun ModuleHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MoneyTheme.type.headlineMd, color = MoneyTheme.colors.ink)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MoneyTheme.type.bodySm,
                    color = MoneyTheme.colors.muted,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        action?.invoke()
    }
}
