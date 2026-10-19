package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.theme.MoneyColors
import com.moneymatters.app.ui.theme.MoneyTheme

/** Meaning carried by a badge. Colors come from the semantic table in design/DESIGN.md. */
enum class Tone { Positive, Negative, Warning, Info, Champagne, Anchor, Neutral }

fun Tone.colors(c: MoneyColors): Pair<Color, Color> = when (this) {
    Tone.Positive -> c.positiveContainer to c.positive
    Tone.Negative -> c.negativeContainer to c.negative
    Tone.Warning -> c.warningContainer to c.warning
    Tone.Info -> c.infoContainer to c.info
    Tone.Champagne -> c.champagneContainer to c.champagne
    Tone.Anchor -> c.anchor to c.onAnchor
    Tone.Neutral -> c.surfaceHigh to c.muted
}

/**
 * Status pill. Pair color with a glyph (`▲`, `▼`, `✓`) so meaning never depends on color alone.
 * An [icon] (e.g. the lock on a reconciled entry) sits to the left of the text.
 */
@Composable
fun StatusBadge(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    style: TextStyle = MoneyTheme.type.metricDelta,
    icon: ImageVector? = null,
) {
    val (bg, fg) = tone.colors(MoneyTheme.colors)
    Box(
        modifier = modifier
            .height(height)
            .clip(MoneyTheme.shapes.full)
            .background(bg)
            .padding(horizontal = if (height > 20.dp) 10.dp else 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == null) {
            Text(text, style = style, color = fg, maxLines = 1)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = fg, modifier = Modifier.height(10.dp))
                Text(text, style = style, color = fg, maxLines = 1)
            }
        }
    }
}
