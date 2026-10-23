package com.moneymatters.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.theme.MoneyTheme

/** 1.25dp anchor-colored sparkline, no fill, no axes. */
@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val color = MoneyTheme.colors.anchor
    Canvas(modifier.size(64.dp, 24.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0f } ?: 1f
        val inset = 2.dp.toPx()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i / (values.size - 1f) * size.width
            val y = size.height - inset - (v - min) / span * (size.height - 2 * inset)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Price line with a soft fill under it and a dot on the latest value. [reference] draws a dashed line
 * (average cost, so you can see at a glance whether the price is above or below what you paid).
 */
@Composable
fun PriceChart(values: List<Float>, modifier: Modifier = Modifier, reference: Float? = null) {
    val c = MoneyTheme.colors
    Canvas(modifier.fillMaxWidth().height(132.dp)) {
        if (values.size < 2) return@Canvas
        val all = if (reference != null) values + reference else values
        val min = all.min()
        val span = (all.max() - min).takeIf { it > 0f } ?: 1f
        val inset = 8.dp.toPx()
        fun y(v: Float) = size.height - inset - (v - min) / span * (size.height - 2 * inset)
        fun x(i: Int) = i / (values.size - 1f) * size.width
        val line = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(c.anchor.copy(alpha = 0.16f), c.anchor.copy(alpha = 0f))))
        if (reference != null) {
            val ry = y(reference)
            drawLine(c.champagne, Offset(0f, ry), Offset(size.width, ry), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
        }
        drawPath(line, c.anchor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(c.surface, 5.dp.toPx(), Offset(x(values.lastIndex), y(values.last())))
        drawCircle(c.anchor, 3.5.dp.toPx(), Offset(x(values.lastIndex), y(values.last())))
    }
}

/** 270° gauge with the value centered (budget used, leeway used, card utilisation). */
@Composable
fun Gauge(
    fraction: Float,
    caption: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 104.dp,
) {
    val c = MoneyTheme.colors
    val percent = (fraction * 100).toInt()
    Box(
        modifier
            .size(diameter)
            .semantics { contentDescription = "$percent% $caption" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(c.outline, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(c.anchor, 135f, 270f * fraction.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", style = MoneyTheme.type.metricCard, color = c.ink)
            Text(caption, style = MoneyTheme.type.metricDelta.copy(fontWeight = MoneyTheme.type.bodySm.fontWeight), color = c.muted)
        }
    }
}

/**
 * Thin progress track. [pace] draws a marker where you should be today;
 * [over] switches the fill to negative (overspend is the one place red fills a bar).
 */
@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    pace: Float? = null,
    over: Boolean = false,
) {
    val c = MoneyTheme.colors
    Canvas(
        modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val h = 6.dp.toPx()
        val top = (size.height - h) / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(c.outline, Offset(0f, top), Size(size.width, h), r)
        drawRoundRect(if (over) c.negative else c.anchor, Offset(0f, top), Size(size.width * fraction.coerceIn(0f, 1f), h), r)
        if (pace != null) {
            val x = size.width * pace.coerceIn(0f, 1f)
            drawRoundRect(c.ink.copy(alpha = 0.55f), Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height), CornerRadius(1.dp.toPx()))
        }
    }
}

/** One part of a [SegmentBar]. A non-null [hatch] draws diagonal stripes of that color over [color]. */
@Immutable
data class BarSegment(val fraction: Float, val color: Color, val hatch: Color? = null)

/**
 * Horizontal bar made of consecutive segments (runway ladder, allocation, category split).
 * [gap] separates segments; [marker] draws a target line at that fraction.
 */
@Composable
fun SegmentBar(
    segments: List<BarSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    gap: Dp = 0.dp,
    corner: Dp = height / 2,
    track: Color = MoneyTheme.colors.outline,
    marker: Float? = null,
    markerColor: Color = MoneyTheme.colors.champagne,
) {
    val markerExtra = if (marker != null) 5.dp else 0.dp
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height + markerExtra * 2),
    ) {
        val h = height.toPx()
        val top = markerExtra.toPx()
        val outline = Path().apply { addRoundRect(RoundRect(Rect(0f, top, size.width, top + h), CornerRadius(corner.toPx()))) }
        clipPath(outline) {
            drawRect(track, Offset(0f, top), Size(size.width, h))
            var x = 0f
            val g = gap.toPx()
            segments.forEachIndexed { i, s ->
                val w = size.width * s.fraction - if (i < segments.lastIndex) g else 0f
                if (w > 0f) drawSegment(s, Offset(x, top), Size(w, h))
                x += size.width * s.fraction
            }
        }
        if (marker != null) {
            val mx = size.width * marker.coerceIn(0f, 1f)
            drawRoundRect(markerColor, Offset(mx - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height), CornerRadius(1.dp.toPx()))
        }
    }
}

private fun DrawScope.drawSegment(s: BarSegment, topLeft: Offset, size: Size) {
    drawRect(s.color, topLeft, size)
    val hatch = s.hatch ?: return
    val step = 4.dp.toPx()
    val stroke = 2.dp.toPx()
    clipPath(Path().apply { addRect(Rect(topLeft, size)) }) {
        var x = topLeft.x - size.height
        while (x < topLeft.x + size.width) {
            drawLine(hatch, Offset(x, topLeft.y + size.height), Offset(x + size.height, topLeft.y), stroke)
            x += step
        }
    }
}

/** Grid of round dots, [filled] of [total] in the anchor color (emergency fund, budget usage). */
@Composable
fun DotMatrix(
    filled: Int,
    total: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    gap: Dp = 4.dp,
) {
    val c = MoneyTheme.colors
    val rows = (total + columns - 1) / columns
    Canvas(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // Height follows width so dots stay round.
                val w = constraints.maxWidth
                val g = gap.roundToPx()
                val d = (w - g * (columns - 1)) / columns
                val h = rows * d + (rows - 1) * g
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .semantics { contentDescription = "$filled of $total" },
    ) {
        val g = gap.toPx()
        val d = (size.width - g * (columns - 1)) / columns
        for (i in 0 until total) {
            val col = i % columns
            val row = i / columns
            drawCircle(
                if (i < filled) c.anchor else c.dotEmpty,
                radius = d / 2,
                center = Offset(col * (d + g) + d / 2, row * (d + g) + d / 2),
            )
        }
    }
}
