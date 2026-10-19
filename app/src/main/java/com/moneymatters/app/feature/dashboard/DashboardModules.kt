package com.moneymatters.app.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.core.money.formatInrCompact
import com.moneymatters.app.ui.components.AmountWithBadge
import com.moneymatters.app.ui.components.BarSegment
import com.moneymatters.app.ui.components.DateChip
import com.moneymatters.app.ui.components.DotMatrix
import com.moneymatters.app.ui.components.ExpandButton
import com.moneymatters.app.ui.components.GlyphChip
import com.moneymatters.app.ui.components.InnerPanel
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.MoneyText
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.ProgressTrack
import com.moneymatters.app.ui.components.RowDivider
import com.moneymatters.app.ui.components.SegmentBar
import com.moneymatters.app.ui.components.Sparkline
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyColors
import com.moneymatters.app.ui.theme.MoneyTheme

/** Tone steps from the anchor toward the surface, used where charts need several neutral shades. */
private fun MoneyColors.step(t: Float): Color = lerp(anchor, surface, t)

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = modifier)
}

// ---------- KPI strip ----------

@Composable
fun KpiStrip(kpis: List<Kpi>, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = state,
        contentPadding = PaddingValues(horizontal = MoneyTheme.spacing.margin),
        horizontalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        flingBehavior = rememberSnapFlingBehavior(state),
    ) {
        items(kpis, key = { it.label }) { k ->
            Module(Modifier.width(172.dp), contentPadding = 14.dp) {
                Muted(k.label)
                Text(k.value, style = MoneyTheme.type.metricCard, color = MoneyTheme.colors.ink, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    StatusBadge(k.delta, k.tone)
                    Sparkline(k.trend)
                }
            }
        }
    }
}

// ---------- Runway ----------

@Composable
fun RunwayModule(runway: Runway, onOpen: () -> Unit) {
    val c = MoneyTheme.colors
    val scale = runway.scaleMonths.toFloat()
    val cash = BarSegment(runway.cashMonths / scale, c.anchor)
    val fd = BarSegment((runway.withFdMonths - runway.cashMonths) / scale, c.step(0.45f))
    val markets = BarSegment((runway.withMarketsMonths - runway.withFdMonths) / scale, c.step(0.62f), hatch = c.step(0.8f))
    Module {
        ModuleHeader(
            "Runway",
            subtitle = "On essential spend · ${runway.essentialMonthly.formatInr()} / mo",
            action = { ExpandButton("Runway details", onOpen) },
        )
        RunwayRung("Cash", listOf(cash), runway.cashMonths)
        RunwayRung("+ FDs", listOf(cash, fd), runway.withFdMonths)
        RunwayRung("+ Markets", listOf(cash, fd, markets), runway.withMarketsMonths)
        Row(Modifier.padding(top = 2.dp)) {
            Spacer(Modifier.width(86.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                val tick = MoneyTheme.type.metricDelta.copy(fontWeight = FontWeight.Normal, fontSize = MoneyTheme.type.metricDelta.fontSize * 0.9f)
                for (m in 0..runway.scaleMonths step runway.scaleMonths / 3) {
                    Text(if (m == runway.scaleMonths) "$m mo" else "$m", style = tick, color = c.muted)
                }
            }
            Spacer(Modifier.width(68.dp))
        }
        RowDivider(Modifier.padding(top = 10.dp))
        KeyValueRow("Trajectory", divider = false) { StatusBadge(runway.trajectoryLabel, runway.trajectoryTone) }
    }
}

@Composable
private fun RunwayRung(label: String, segments: List<BarSegment>, months: Float) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Muted(label, Modifier.width(86.dp))
        SegmentBar(segments, Modifier.weight(1f))
        Text(
            "${oneDecimal(months)} mo",
            style = MoneyTheme.type.metricTable.copy(fontSize = MoneyTheme.type.ledgerMono.fontSize, fontWeight = FontWeight.SemiBold),
            color = MoneyTheme.colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.width(68.dp),
        )
    }
}

private fun oneDecimal(v: Float): String {
    val tenths = kotlin.math.round(v * 10).toInt()
    return "${tenths / 10}.${tenths % 10}"
}

// ---------- Budgets ----------

@Composable
fun BudgetsModule(budgets: Budgets, onPeriod: () -> Unit) {
    Module {
        ModuleHeader(
            "Budgets",
            subtitle = "Marker = where you should be today",
            action = { Pill(budgets.periodLabel, onPeriod, trailingIcon = MoneyIcons.ChevronDown) },
        )
        budgets.lines.forEachIndexed { i, b ->
            val pace = budgetPace(b.spent, b.cap, budgets.elapsedFraction)
            val (label, tone) = when (pace) {
                BudgetPace.OnPace -> "On pace" to Tone.Positive
                BudgetPace.Watch -> "Watch" to Tone.Warning
                BudgetPace.OverPace -> "Over pace" to Tone.Negative
            }
            Column(Modifier.padding(vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, style = MoneyTheme.type.ledgerMono, color = MoneyTheme.colors.ink)
                    StatusBadge(label, tone, Modifier.padding(start = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = MoneyTheme.colors.ink, fontWeight = FontWeight.Medium)) { append(b.spent.formatInr()) }
                            append(" / ${b.cap.formatInr()}")
                        },
                        style = MoneyTheme.type.bodySm,
                        color = MoneyTheme.colors.muted,
                    )
                }
                ProgressTrack(
                    fraction = (b.spent.value.toFloat() / b.cap.value).coerceAtMost(1f),
                    pace = budgets.elapsedFraction,
                    over = pace == BudgetPace.OverPace,
                )
            }
            if (i < budgets.lines.lastIndex) RowDivider()
        }
    }
}

// ---------- Where it went ----------

@Composable
fun SpendingModule(spending: Spending, onViewAll: () -> Unit) {
    val c = MoneyTheme.colors
    val total = spending.categories.sumOf { it.amount.value }.toFloat()
    val shades = listOf(
        BarSegment(0f, c.step(0f)),
        BarSegment(0f, c.step(0.25f)),
        BarSegment(0f, c.step(0.45f)),
        BarSegment(0f, c.step(0.6f)),
        BarSegment(0f, c.step(0.75f)),
        BarSegment(0f, c.step(0.78f), hatch = c.step(0.9f)),
        BarSegment(0f, c.step(0.84f), hatch = c.step(0.94f)),
    )
    val segments = spending.categories.mapIndexed { i, cat ->
        shades[i.coerceAtMost(shades.lastIndex)].copy(fraction = cat.amount.value / total)
    }
    val largest = segments.firstOrNull()?.fraction ?: 0f
    Module {
        ModuleHeader("Where it went", subtitle = "${spending.total.formatInr()} spent this month", action = { Pill("View all", onViewAll) })
        // Callout pill pinned over the largest share.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0))
                    val x = (constraints.maxWidth * largest / 2 - p.width / 2f).toInt().coerceIn(0, constraints.maxWidth - p.width)
                    layout(constraints.maxWidth, p.height) { p.place(x, 0) }
                },
        ) {
            StatusBadge("${(largest * 100).toInt()}%", Tone.Anchor, height = 22.dp)
        }
        SegmentBar(segments, height = 26.dp, gap = 2.dp, corner = 8.dp)
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            spending.categories.take(6).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { cat ->
                        val seg = segments[spending.categories.indexOf(cat)]
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(MoneyTheme.shapes.sm).background(seg.color))
                            Column {
                                Muted(cat.name)
                                Text(cat.amount.formatInr(), style = MoneyTheme.type.metricTable, color = c.ink)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        RowDivider(Modifier.padding(top = 12.dp))
        KeyValueRow("Need / want", divider = false) {
            Text(spending.needWantLabel, style = MoneyTheme.type.metricTable.copy(fontSize = MoneyTheme.type.ledgerMono.fontSize), color = c.ink)
            Muted("· ${spending.unplannedCount} unplanned")
        }
    }
}

// ---------- Next 14 days ----------

@Composable
fun UpcomingModule(items: List<UpcomingItem>, onCalendar: () -> Unit) {
    Module {
        ModuleHeader("Next 14 days", subtitle = "Bills, dues and SIPs", action = { Pill("Calendar", onCalendar) })
        items.forEachIndexed { i, item ->
            ListRow(
                title = item.title,
                subtitle = item.meta,
                divider = i < items.lastIndex,
                leading = { DateChip(item.day, item.month) },
                trailing = { AmountWithBadge(item.amount.formatInr()) { StatusBadge(item.badge, item.tone) } },
            )
        }
    }
}

// ---------- Goals ----------

@Composable
fun GoalsModule(goals: Goals, onOpen: () -> Unit) {
    val c = MoneyTheme.colors
    val t = MoneyTheme.type
    val ef = goals.emergency
    val corpus = goals.corpus
    Module {
        ModuleHeader("Goals", subtitle = "Emergency fund comes first", action = { ExpandButton("Goals", onOpen) })

        InnerPanel {
            val blocks = (ef.target.value / ef.blockSize.value).toInt()
            val filled = (ef.saved.value / ef.blockSize.value).toInt()
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Emergency fund", style = t.ledgerMono.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                    GoalFigure(ef.saved.formatInr(), " / ${ef.target.formatInr()}")
                }
                StatusBadge("${(ef.saved.value * 100 / ef.target.value)}%", Tone.Champagne)
            }
            DotMatrix(filled = filled, total = blocks, columns = 20, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Muted("1 dot = ${ef.blockSize.formatInr()}")
                Muted("✓ ${ef.coverageLabel}")
            }
        }

        Spacer(Modifier.height(MoneyTheme.spacing.gutter))

        InnerPanel {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(corpus.title, style = t.ledgerMono.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                    GoalFigure(corpus.timeLeftLabel, " · ${corpus.deadlineLabel}")
                }
                if (corpus.onTrack) StatusBadge("▲ On track", Tone.Positive) else StatusBadge("▼ Behind", Tone.Negative)
            }
            GoalProjectionChart(corpus)
            KeyValueRow("Invested now", corpus.invested.formatInr())
            KeyValueRow("Required SIP", "${corpus.requiredSip.formatInr()} / mo")
            KeyValueRow("Your SIP", "${corpus.monthlySip.formatInr()} / mo")
            KeyValueRow("Projected at 30", divider = false) {
                Text(corpus.projected.formatInrCompact(), style = t.metricTable.copy(fontSize = t.ledgerMono.fontSize), color = c.ink)
                if (!corpus.onTrack) Muted("· ${(corpus.target - corpus.projected).formatInrCompact()} short")
            }
        }
    }
}

@Composable
private fun GoalFigure(big: String, small: String) {
    Text(
        buildAnnotatedString {
            append(big)
            withStyle(SpanStyle(color = MoneyTheme.colors.muted, fontSize = MoneyTheme.type.ledgerMono.fontSize, letterSpacing = MoneyTheme.type.ledgerMono.letterSpacing)) { append(small) }
        },
        style = MoneyTheme.type.metricCard.copy(fontSize = MoneyTheme.type.metricCard.fontSize * 1.1f),
        color = MoneyTheme.colors.ink,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Required path (solid) against the current-SIP path (dashed), with the target as a champagne rule. */
@Composable
private fun GoalProjectionChart(goal: CorpusGoal) {
    val c = MoneyTheme.colors
    val measurer = rememberTextMeasurer()
    val label = MoneyTheme.type.metricDelta.copy(fontWeight = FontWeight.Normal, fontSize = MoneyTheme.type.metricDelta.fontSize * 0.9f, color = c.muted)
    val required = goal.requiredSip
    val months = goal.monthsLeft
    val targetRupees = goal.target.value / 100.0
    val projected = goal.projected
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(106.dp)
            .semantics { contentDescription = "Required path versus your current pace to ${goal.target.formatInrCompact()}" },
    ) {
        val labelBand = 14.dp.toPx()
        val h = size.height - labelBand
        fun x(m: Int) = m / months.toFloat() * size.width
        fun y(v: Double) = (h - 4.dp.toPx() - (v / targetRupees) * (h - 16.dp.toPx())).toFloat()
        fun path(sip: com.moneymatters.app.core.money.Paise) = Path().apply {
            for (m in 0..months) {
                val px = x(m)
                val py = y(projectedRupees(goal.invested, sip, m, goal.annualReturn))
                if (m == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        val ty = y(targetRupees)
        drawLine(c.champagne, Offset(0f, ty), Offset(size.width, ty), 1.dp.toPx(), pathEffect = dash)
        val tLabel = measurer.measure("${goal.target.formatInrCompact()} target", label.copy(color = c.champagne))
        drawText(tLabel, topLeft = Offset(0f, ty - tLabel.size.height - 2.dp.toPx()))

        drawPath(path(required), c.anchor, style = Stroke(1.5.dp.toPx()))
        val rLabel = measurer.measure("required", label.copy(color = c.ink))
        // The required path ends on the target line, so its label sits above the line, clear of the curve.
        drawText(rLabel, topLeft = Offset(size.width - rLabel.size.width, ty - rLabel.size.height - 2.dp.toPx()))

        drawPath(path(goal.monthlySip), c.muted, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        val pLabel = measurer.measure("your pace · ${projected.formatInrCompact()}", label)
        drawText(pLabel, topLeft = Offset(size.width - pLabel.size.width, y(projected.value / 100.0) - pLabel.size.height - 4.dp.toPx()))

        val start = measurer.measure(goal.startLabel, label)
        drawText(start, topLeft = Offset(0f, size.height - start.size.height))
        val end = measurer.measure(goal.deadlineLabel.substringAfter(' '), label)
        drawText(end, topLeft = Offset(size.width - end.size.width, size.height - end.size.height))
    }
}

// ---------- Portfolio ----------

@Composable
fun PortfolioModule(p: Portfolio, onInvest: () -> Unit) {
    val c = MoneyTheme.colors
    Module {
        ModuleHeader("Portfolio", subtitle = "Stocks & mutual funds · excl. emergency FDs", action = { Pill("Invest", onInvest) })
        MoneyText(p.value, style = MoneyTheme.type.metricCard.copy(fontSize = MoneyTheme.type.metricCard.fontSize * 1.33f, letterSpacing = MoneyTheme.type.metricHero.letterSpacing))
        Row(Modifier.padding(top = 8.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge("▲ ${p.unrealized.formatInr()} · ${p.unrealizedPct}", Tone.Positive)
            StatusBadge("▲ ${p.weekChange.formatInr()} wk", Tone.Positive)
            StatusBadge("XIRR ${p.xirr}", Tone.Neutral)
        }
        SegmentBar(listOf(BarSegment(p.equityFraction, c.anchor)), marker = p.targetEquityFraction)
        val drift = ((p.equityFraction - p.targetEquityFraction) * 100).toInt()
        val target = (p.targetEquityFraction * 100).toInt()
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("Equity ${(p.equityFraction * 100).toInt()}%")
            Text(
                "Target $target / ${100 - target} · drift ${if (drift >= 0) "+" else "−"}${kotlin.math.abs(drift)}%",
                style = MoneyTheme.type.bodySm,
                color = if (kotlin.math.abs(drift) > 5) c.warning else c.muted,
            )
        }
        InnerPanel(Modifier.padding(top = 14.dp), contentPadding = 12.dp) {
            p.sources.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) { append(s.name) }
                            append(" · ${s.asOf}")
                        },
                        style = MoneyTheme.type.bodySm,
                        color = c.muted,
                    )
                    StatusBadge(s.status, s.tone)
                }
                if (i < p.sources.lastIndex) RowDivider()
            }
        }
    }
}

// ---------- Insights ----------

@Composable
fun InsightsModule(insights: Insights, onAll: () -> Unit, onAction: (InsightItem) -> Unit, onDismiss: (InsightItem) -> Unit) {
    val c = MoneyTheme.colors
    Module {
        ModuleHeader("Insights", subtitle = "Ranked by rupee impact", action = { Pill("All", onAll) })
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MoneyTheme.shapes.lg)
                .background(c.anchor)
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("WEEKLY SUMMARY", style = MoneyTheme.type.labelUppercase, color = c.onAnchor.copy(alpha = 0.6f))
                Text(insights.digestDate, style = MoneyTheme.type.metricDelta.copy(fontWeight = FontWeight.Normal), color = c.onAnchor.copy(alpha = 0.6f))
            }
            Text(
                insights.digest,
                style = MoneyTheme.type.ledgerMono.copy(lineHeight = MoneyTheme.type.bodyLg.lineHeight),
                color = c.onAnchor,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Pill("Read summary", onAll, style = PillStyle.OnDark)
        }
        insights.items.forEachIndexed { i, item ->
            Row(Modifier.padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (item.glyph) {
                    InsightGlyph.Alert -> GlyphChip(icon = MoneyIcons.Alert, content = c.negative)
                    InsightGlyph.Recurring -> GlyphChip(icon = MoneyIcons.Refresh)
                }
                Column {
                    Text(item.title, style = MoneyTheme.type.ledgerMono.copy(fontWeight = FontWeight.Medium), color = c.ink)
                    Muted(item.detail, Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(item.action, { onAction(item) }, height = 26.dp)
                        Pill("Dismiss", { onDismiss(item) }, height = 26.dp)
                    }
                }
            }
            if (i < insights.items.lastIndex) RowDivider()
        }
    }
}

// ---------- Needs attention ----------

@Composable
fun AttentionModule(items: List<AttentionItem>, onItem: (AttentionItem) -> Unit) {
    val c = MoneyTheme.colors
    Module {
        ModuleHeader("Needs attention", subtitle = "Keep the books clean")
        items.forEachIndexed { i, item ->
            ListRow(
                title = item.title,
                subtitle = item.detail,
                divider = i < items.lastIndex,
                leading = {
                    when (item.kind) {
                        AttentionKind.CloseMonth -> GlyphChip(icon = MoneyIcons.Check, background = c.champagneContainer, content = c.champagne, bordered = false)
                        AttentionKind.Drafts -> GlyphChip(icon = MoneyIcons.Pencil)
                        AttentionKind.Uncategorized -> GlyphChip(icon = MoneyIcons.Question)
                        AttentionKind.InTransit -> GlyphChip(icon = MoneyIcons.Transfer)
                        AttentionKind.WeeklyReview -> GlyphChip(icon = MoneyIcons.Checklist)
                        AttentionKind.AdvanceTax -> GlyphChip(text = "₹")
                    }
                },
                trailing = {
                    when {
                        item.kind == AttentionKind.CloseMonth -> Pill("Close", { onItem(item) }, style = PillStyle.Primary)
                        item.amount != null -> AmountWithBadge(item.amount.formatInr()) { item.badge?.let { StatusBadge(it, item.tone) } }
                        item.badge != null -> StatusBadge(item.badge, item.tone)
                    }
                },
            )
        }
    }
}
