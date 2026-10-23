package com.moneymatters.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.theme.MoneyTheme

enum class CardFinish { Pearl, Graphite, Silver, Champagne, Smoke }

private data class Finish(val from: Color, val to: Color, val ink: Color, val highlight: Float)

private fun CardFinish.colors() = when (this) {
    CardFinish.Pearl -> Finish(Color(0xFFF8F7F3), Color(0xFFE7E4DC), Color(0xFF1C1C1E), 0.55f)
    CardFinish.Graphite -> Finish(Color(0xFF3A3A3E), Color(0xFF1E1E20), Color(0xFFF2F2F3), 0.12f)
    CardFinish.Silver -> Finish(Color(0xFFF0F0F3), Color(0xFFD6D6DB), Color(0xFF1C1C1E), 0.55f)
    CardFinish.Champagne -> Finish(Color(0xFFF2ECDF), Color(0xFFD9CCAE), Color(0xFF2B2415), 0.55f)
    CardFinish.Smoke -> Finish(Color(0xFFE4E4E8), Color(0xFFC4C4CA), Color(0xFF1C1C1E), 0.55f)
}

/** Physical-looking card face. Finishes are fixed materials, the same in light and dark themes. */
@Composable
fun BoxScope.AccountCardFace(
    finish: CardFinish,
    typeLabel: String,
    name: String,
    amountLabel: String,
    amount: Paise,
    footLeft: String,
    footRight: String,
    glyph: String?,
) {
    val f = finish.colors()
    val t = MoneyTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val base = Brush.linearGradient(listOf(f.from, f.to), Offset.Zero, Offset(size.width, size.height * 1.4f))
                val sheen = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = f.highlight), Color.Transparent),
                    center = Offset(size.width * 0.15f, 0f),
                    radius = size.width * 0.7f,
                )
                onDrawBehind {
                    drawRect(base)
                    drawRect(sheen)
                }
            }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(typeLabel.uppercase(), style = t.labelUppercase, color = f.ink.copy(alpha = 0.62f))
                Text(name, style = t.headlineMd, color = f.ink, modifier = Modifier.padding(top = 3.dp))
            }
            if (glyph == null) {
                EmvChip(f.ink)
            } else {
                Text(glyph, style = t.metricCard, color = f.ink.copy(alpha = 0.7f))
            }
        }
        Column {
            Text(amountLabel, style = t.bodySm, color = f.ink.copy(alpha = 0.62f))
            Text(
                amount.formatInr(),
                style = t.metricCard.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = t.metricHero.letterSpacing),
                color = f.ink,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(footLeft, style = t.bodySm.copy(letterSpacing = t.labelUppercase.letterSpacing * 0.75f), color = f.ink.copy(alpha = 0.75f))
            Text(footRight, style = t.bodySm.copy(fontWeight = FontWeight.SemiBold), color = f.ink)
        }
    }
}

@Composable
private fun EmvChip(ink: Color) {
    Canvas(Modifier.size(34.dp, 26.dp)) {
        val color = ink.copy(alpha = 0.55f)
        val s = Stroke(1.dp.toPx())
        drawRoundRect(color, style = s, cornerRadius = CornerRadius(6.dp.toPx()))
        val third = size.width * 0.31f
        listOf(size.height * 0.35f, size.height * 0.65f).forEach { y ->
            drawLine(color, Offset(0f, y), Offset(third, y), s.width)
            drawLine(color, Offset(size.width - third, y), Offset(size.width, y), s.width)
        }
        drawLine(color, Offset(third, 0f), Offset(third, size.height), s.width)
        drawLine(color, Offset(size.width - third, 0f), Offset(size.width - third, size.height), s.width)
    }
}

