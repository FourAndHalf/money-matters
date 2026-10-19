package com.moneymatters.app.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.AmountWithBadge
import com.moneymatters.app.ui.components.CardStack
import com.moneymatters.app.ui.components.Gauge
import com.moneymatters.app.ui.components.GlyphChip
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.StackDots
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme

/** Accounts header, swipeable card stack, position dots and the context panel for the front card. */
@Composable
fun AccountsSection(accounts: List<AccountCard>, onViewAll: () -> Unit, onAction: (AccountCard) -> Unit) {
    var front by rememberSaveable { mutableIntStateOf(0) }
    Column {
        ModuleHeader(
            title = "Accounts",
            subtitle = "Swipe the stack · ${front + 1} / ${accounts.size}",
            modifier = Modifier.padding(start = 2.dp, top = 8.dp),
            action = { Pill("View all", onViewAll) },
        )
        CardStack(
            items = accounts,
            frontIndex = front,
            onFrontIndexChange = { front = it },
            cardLabel = { "${it.name}, ${it.amountLabel} ${it.amount.formatInr()}" },
        ) { AccountCardFace(it) }
        StackDots(
            count = accounts.size,
            selected = front,
            onSelect = { front = it },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp),
        )
        Module {
            AnimatedContent(
                targetState = accounts[front],
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(140)) },
                label = "account-context",
            ) { account -> AccountContext(account, onAction = { onAction(account) }) }
        }
    }
}

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
private fun BoxScope.AccountCardFace(card: AccountCard) {
    val f = card.finish.colors()
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
                Text(card.typeLabel.uppercase(), style = t.labelUppercase, color = f.ink.copy(alpha = 0.62f))
                Text(card.name, style = t.headlineMd, color = f.ink, modifier = Modifier.padding(top = 3.dp))
            }
            if (card.glyph == null) {
                EmvChip(f.ink)
            } else {
                Text(card.glyph, style = t.metricCard, color = f.ink.copy(alpha = 0.7f))
            }
        }
        Column {
            Text(card.amountLabel, style = t.bodySm, color = f.ink.copy(alpha = 0.62f))
            Text(
                card.amount.formatInr(),
                style = t.metricCard.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = t.metricHero.letterSpacing),
                color = f.ink,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(card.footLeft, style = t.bodySm.copy(letterSpacing = t.labelUppercase.letterSpacing * 0.75f), color = f.ink.copy(alpha = 0.75f))
            Text(card.footRight, style = t.bodySm.copy(fontWeight = FontWeight.SemiBold), color = f.ink)
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

/** Facts, a mini visual and one action for the front card's account. */
@Composable
private fun AccountContext(card: AccountCard, onAction: () -> Unit) {
    val d = card.detail
    val c = MoneyTheme.colors
    Column {
        ModuleHeader(d.title, subtitle = d.subtitle, action = { Pill(d.action, onAction) })
        when (d) {
            is AccountDetail.Bank -> {
                KeyValueRow("Money in", "▲ ${d.moneyIn.formatInr()}")
                KeyValueRow("Money out", "▼ ${d.moneyOut.formatInr()}")
                d.recent.forEachIndexed { i, txn ->
                    ListRow(
                        title = txn.payee,
                        subtitle = txn.meta,
                        divider = i < d.recent.lastIndex,
                        leading = { GlyphChip(text = txn.chip) },
                        trailing = {
                            AmountWithBadge(txn.amount.formatInr(signed = true)) {
                                StatusBadge(txn.badge, txn.tone)
                            }
                        },
                    )
                }
            }
            is AccountDetail.CreditCard -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Gauge(d.utilisation, "of ${d.limit.formatInr()}")
                    Column(Modifier.weight(1f)) {
                        KeyValueRow("Statement", d.statement.formatInr())
                        KeyValueRow("Unbilled", d.unbilled.formatInr())
                        KeyValueRow("Due", d.dueLabel, divider = false)
                    }
                }
                KeyValueRow("Payment source") {
                    if (d.coveredBy != null) {
                        StatusBadge("✓ Covered by ${d.coveredBy}", Tone.Positive)
                    } else {
                        StatusBadge("▼ Not covered", Tone.Negative)
                    }
                }
                KeyValueRow("Interest risk", divider = false) { StatusBadge("None", Tone.Positive) }
            }
            is AccountDetail.Cash -> {
                KeyValueRow("On hand (books)", d.onHand.formatInr())
                KeyValueRow("Spent from cash · Oct", d.spentThisMonth.formatInr())
                KeyValueRow("ATM withdrawals · Oct", d.withdrawnThisMonth.formatInr())
                KeyValueRow("Status", divider = false) { StatusBadge(d.status, Tone.Warning) }
            }
            is AccountDetail.FdLadder -> {
                d.deposits.forEachIndexed { i, fd ->
                    ListRow(
                        title = "${fd.principal.formatInr()} · ${fd.rate}",
                        subtitle = fd.maturesLabel,
                        leading = { GlyphChip(text = "${i + 1}") },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fd.accrued.formatInr(signed = true), style = MoneyTheme.type.metricTable.copy(fontSize = MoneyTheme.type.ledgerMono.fontSize), color = c.ink)
                                Text("accrued", style = MoneyTheme.type.metricDelta.copy(fontWeight = FontWeight.Normal), color = c.muted)
                            }
                        },
                    )
                }
                KeyValueRow("Break-today value", divider = false) {
                    Text(d.breakTodayValue.formatInr(), style = MoneyTheme.type.metricTable.copy(fontSize = MoneyTheme.type.ledgerMono.fontSize), color = c.ink)
                    Text("after penalty", style = MoneyTheme.type.bodySm, color = c.muted)
                }
            }
            is AccountDetail.Investments -> {
                KeyValueRow("Cost", d.cost.formatInr())
                KeyValueRow("Unrealized") { StatusBadge("▲ ${d.unrealized.formatInr()} · ${d.unrealizedPct}", Tone.Positive) }
                KeyValueRow("XIRR", d.xirr)
                KeyValueRow("LTCG exemption used", "${d.ltcgUsed.formatInr()} of ${d.ltcgLimit.formatInr()}")
                KeyValueRow("Prices", divider = false) { StatusBadge(d.pricesLabel, Tone.Positive) }
            }
        }
    }
}
