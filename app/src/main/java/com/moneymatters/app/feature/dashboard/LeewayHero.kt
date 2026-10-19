package com.moneymatters.app.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.ExpandButton
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.MoneyText
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme

/** The one decorative surface on the dashboard: a soft pearl gradient (design/DESIGN.md → Cards & modules). */
@Composable
private fun heroBrush(): Brush =
    if (MoneyTheme.colors.isDark) {
        Brush.linearGradient(listOf(Color(0xFF1E1D1B), Color(0xFF161615)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFF9F8F4), Color(0xFFECE9E1)))
    }

@Composable
fun LeewayHero(leeway: Leeway, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    val t = MoneyTheme.type
    Module(modifier, shape = MoneyTheme.shapes.xl, background = heroBrush(), contentPadding = 18.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Safe to spend", style = t.labelRegular.copy(fontSize = t.ledgerMono.fontSize, fontWeight = FontWeight.Normal), color = c.muted)
            StatusBadge("until payday · ${leeway.paydayLabel}", Tone.Champagne)
        }
        MoneyText(
            leeway.safeToSpend,
            style = t.metricHero,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusBadge("${leeway.dailyAllowance.formatInr()} / day", Tone.Anchor, height = 24.dp, style = t.labelRegular.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${leeway.daysLeft} days left  ·  ${(leeway.usedFraction * 100).toInt()}% used",
                style = t.bodySm,
                color = c.muted,
            )
        }
        DailySpendChart(leeway, Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(c.ink.copy(alpha = 0.07f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${leeway.reserved.formatInr()} reserved for ${leeway.reservedCount} dues before payday",
                style = t.bodySm,
                color = c.muted,
                modifier = Modifier.weight(1f),
            )
            ExpandButton("Leeway breakdown", onOpen)
        }
    }
}

/**
 * Thin daily bars against the allowance line. Today is the anchor color; days above the
 * allowance are one step darker; future days are dots.
 */
@Composable
private fun DailySpendChart(leeway: Leeway, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    val measurer = rememberTextMeasurer()
    val label = MoneyTheme.type.metricDelta.copy(fontSize = MoneyTheme.type.metricDelta.fontSize * 0.9f, fontWeight = FontWeight.Normal, color = c.muted)
    val labelStrong = label.copy(color = c.ink, fontWeight = FontWeight.SemiBold)
    val allowance = leeway.dailyAllowance
    val today = leeway.dailySpend.size
    val allowanceText = "${allowance.formatInr()}/day"
    val maxValue = maxOf(5_000_00L, (leeway.dailySpend.maxOfOrNull { it.value } ?: 0L) * 21 / 20)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { contentDescription = "Daily spending this period against ${allowance.formatInr()} a day" },
    ) {
        val labelBand = 14.dp.toPx()
        val h = size.height - labelBand
        val slot = size.width / leeway.daysInPeriod
        val bw = 4.dp.toPx()
        fun y(v: Long) = h - (v.toFloat() / maxValue) * h

        val ay = y(allowance.value)
        drawLine(c.anchor.copy(alpha = 0.35f), Offset(0f, ay), Offset(size.width, ay), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))

        for (d in 1..leeway.daysInPeriod) {
            val x = (d - 1) * slot + (slot - bw) / 2
            if (d <= today) {
                val v = leeway.dailySpend[d - 1].value
                val bh = maxOf(2.dp.toPx(), (v.toFloat() / maxValue) * h)
                val fill = when {
                    d == today -> c.anchor
                    v > allowance.value -> c.barMid
                    else -> c.barIdle
                }
                drawRoundRect(fill, Offset(x, h - bh), Size(bw, bh), CornerRadius(2.dp.toPx()))
            } else {
                drawCircle(c.barIdle, 1.6.dp.toPx(), Offset(x + bw / 2, h - 2.dp.toPx()))
            }
        }

        val start = measurer.measure(leeway.periodStartLabel, label)
        drawText(start, topLeft = Offset(0f, size.height - start.size.height))
        val end = measurer.measure(leeway.periodEndLabel, label)
        drawText(end, topLeft = Offset(size.width - end.size.width, size.height - end.size.height))
        val todayLabel = measurer.measure("Today", labelStrong)
        val lo = start.size.width + 4f
        val hi = maxOf(lo, size.width - end.size.width - todayLabel.size.width - 4f)
        val tx = ((today - 1) * slot + slot / 2 - todayLabel.size.width / 2).coerceIn(lo, hi)
        drawText(todayLabel, topLeft = Offset(tx, size.height - todayLabel.size.height))
        val allow = measurer.measure(allowanceText, label)
        drawText(allow, topLeft = Offset(size.width - allow.size.width, ay - allow.size.height - 2.dp.toPx()))
    }
}
