package com.moneymatters.app.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.AccountCardFace
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
        ) { AccountCardFace(it.finish, it.typeLabel, it.name, it.amountLabel, it.amount, it.footLeft, it.footRight, it.glyph) }
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
