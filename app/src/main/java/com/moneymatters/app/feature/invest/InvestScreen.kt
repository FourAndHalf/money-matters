package com.moneymatters.app.feature.invest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.AmountWithBadge
import com.moneymatters.app.ui.components.GlyphChip
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.MoneyText
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme

/** Invest tab (P13): portfolio value and return, then the stocks held, each with its price status. */
@Composable
fun InvestScreen(
    state: InvestState,
    onOpenHolding: (holdingId: String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onAllot: (pendingId: String) -> Unit = {},
    onCancelOrder: (pendingId: String) -> Unit = {},
    onOpenDeposit: (accountId: String) -> Unit = {},
    onNewSip: () -> Unit = {},
    onOpenSip: (planId: String) -> Unit = {},
    onOpenPrices: () -> Unit = {},
    onOpenGains: () -> Unit = {},
    onNudgeTarget: (delta: Int) -> Unit = {},
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = status.calculateTopPadding() + contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
    ) {
        item("header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Invest", style = MoneyTheme.type.headlineXlMobile, color = MoneyTheme.colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Import", onImport)
                }
            }
        }
        if (state.holdings.isEmpty() && state.pending.isEmpty() && state.deposits.isEmpty()) {
            item("empty") {
                Module(Modifier.padding(horizontal = margin)) {
                    ModuleHeader("No stocks yet", subtitle = "Buy a stock or fund, add a fixed deposit, or import your trade history from a CSV to rebuild your holdings exactly.")
                }
            }
        } else {
            if (state.holdings.isNotEmpty() || state.deposits.isNotEmpty()) item("summary") { SummaryModule(state, Modifier.padding(horizontal = margin)) }
            if (state.pending.isNotEmpty()) {
                item("in-transit") {
                    Module(Modifier.padding(horizontal = margin)) {
                        ModuleHeader("In transit", subtitle = "Paid for, waiting for units to be allotted")
                        state.pending.forEachIndexed { i, p ->
                            ListRow(
                                title = p.holdingName,
                                subtitle = "Ordered ${p.dateLabel}",
                                divider = i < state.pending.lastIndex,
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(p.amount.formatInr(), style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink, softWrap = false)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Pill("Cancel", { onCancelOrder(p.id) }, height = 26.dp)
                                            Pill("Allot", { onAllot(p.id) }, style = PillStyle.Primary, height = 26.dp)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (state.hasFunds || state.sips.isNotEmpty()) {
                item("sips") {
                    Module(Modifier.padding(horizontal = margin)) {
                        ModuleHeader("SIPs", subtitle = if (state.sips.isEmpty()) "Monthly investments into a fund" else "${state.sips.size} ${if (state.sips.size == 1) "SIP" else "SIPs"}", action = { Pill("+ New", onNewSip, height = 28.dp) })
                        state.sipCalendar?.let { SipCalendarBody(it, Modifier.padding(bottom = 12.dp)) }
                        state.sips.forEachIndexed { i, sip ->
                            ListRow(
                                title = sip.fundName,
                                subtitle = "${sip.amount.formatInr()} ${sip.scheduleLabel}" + (sip.nextLabel?.takeIf { !sip.ended }?.let { " · next $it" } ?: ""),
                                divider = i < state.sips.lastIndex,
                                modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Open ${sip.fundName} SIP") { onOpenSip(sip.planId) },
                                leading = { GlyphChip(text = "↻") },
                                trailing = {
                                    when {
                                        sip.ended -> StatusBadge("Ended", Tone.Neutral)
                                        sip.missedCount > 0 -> StatusBadge("${sip.missedCount} missed", Tone.Negative)
                                        sip.dueCount > 0 -> StatusBadge("${sip.dueCount} due", Tone.Warning)
                                        else -> StatusBadge("On track", Tone.Positive)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (state.ladder.isNotEmpty()) item("ladder") { MaturityLadderModule(state.ladder, Modifier.padding(horizontal = margin)) }
            if (state.deposits.isNotEmpty()) {
                item("deposits") {
                    Module(Modifier.padding(horizontal = margin)) {
                        ModuleHeader("Fixed deposits", subtitle = "${state.deposits.size} ${if (state.deposits.size == 1) "deposit" else "deposits"}")
                        state.deposits.forEachIndexed { i, d ->
                            ListRow(
                                title = d.name,
                                subtitle = d.subtitle,
                                divider = i < state.deposits.lastIndex,
                                modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Open ${d.name}") { onOpenDeposit(d.accountId) },
                                leading = { GlyphChip(text = "◈") },
                                trailing = { AmountWithBadge(d.value.formatInr()) { StatusBadge(d.badge.text, d.badge.tone) } },
                            )
                        }
                    }
                }
            }
            if (state.analytics.allocation.isNotEmpty()) {
                item("allocation") { AllocationModule(state.analytics, onNudgeTarget, onOpenHolding, Modifier.padding(horizontal = margin)) }
            }
            if (state.gainsByYear.isNotEmpty()) {
                item("gains") {
                    Module(Modifier.padding(horizontal = margin)) {
                        ListRow(
                            title = "Capital gains",
                            subtitle = state.gainsByYear.first().let { "${it.label}: ${it.total.formatInr()} realised" },
                            divider = false,
                            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Open capital gains", onClick = onOpenGains),
                        )
                    }
                }
            }
            if (state.holdings.isNotEmpty()) {
                item("prices") {
                    Module(Modifier.padding(horizontal = margin)) {
                        ListRow(
                            title = "Prices",
                            subtitle = state.prices.sources.filter { it.tone != Tone.Neutral }.joinToString(" · ") { "${it.name} ${it.headline}" }.ifEmpty { "Not downloaded yet" },
                            divider = false,
                            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Open prices", onClick = onOpenPrices),
                            trailing = {
                                if (state.prices.attentionCount > 0) StatusBadge("${state.prices.attentionCount} to review", Tone.Warning) else StatusBadge("All set", Tone.Positive)
                            },
                        )
                    }
                }
            }
            listOf(Instrument.Stock to "Stocks", Instrument.MutualFund to "Mutual funds").forEach { (instrument, title) ->
                val rows = state.holdings.filter { it.instrument == instrument }
                if (rows.isNotEmpty()) {
                    item(title) {
                        Module(Modifier.padding(horizontal = margin)) {
                            ModuleHeader(title, subtitle = "${rows.size} ${if (rows.size == 1) "holding" else "holdings"}")
                            rows.forEachIndexed { i, row -> HoldingListRow(row, divider = i < rows.lastIndex, onClick = { onOpenHolding(row.id) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryModule(state: InvestState, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    Module(modifier, shape = MoneyTheme.shapes.xl) {
        Text("Portfolio value", style = MoneyTheme.type.bodySm, color = c.muted)
        MoneyText(state.value, style = MoneyTheme.type.metricHero, showPaise = false, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
        GainLine(state.gain, state.gainPercent)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Invested", style = MoneyTheme.type.bodySm, color = c.muted)
                Text(state.cost.formatInr(), style = MoneyTheme.type.metricTable, color = c.ink, softWrap = false)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("XIRR", style = MoneyTheme.type.bodySm, color = c.muted)
                Text(state.xirr?.let(::formatPercent) ?: "—", style = MoneyTheme.type.metricTable, color = c.ink)
            }
        }
        if (state.unpricedCount > 0) {
            StatusBadge(
                "${state.unpricedCount} without a price, counted at cost",
                Tone.Warning,
                modifier = Modifier.padding(top = 12.dp),
                height = 24.dp,
            )
        }
    }
}

/** `▲ ₹12,000 (+8.40%)` in the positive tone, `▼` in the negative one; the glyph carries the meaning too. */
@Composable
internal fun GainLine(gain: Paise?, percent: Double?, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    if (gain == null) {
        Text("Add a price to see gain", style = MoneyTheme.type.bodySm, color = c.muted, modifier = modifier)
        return
    }
    val up = gain.value >= 0
    val text = "${if (up) "▲" else "▼"} ${Paise(kotlin.math.abs(gain.value)).formatInr()}" + (percent?.let { " (${formatPercent(it)})" } ?: "")
    Text(text, style = MoneyTheme.type.metricTable, color = if (up) c.positive else c.negative, modifier = modifier)
}

@Composable
private fun HoldingListRow(row: HoldingRow, divider: Boolean, onClick: () -> Unit) {
    ListRow(
        title = row.name,
        subtitle = "${row.unitsLabel} units · avg ${row.avgCostLabel}" + (row.classLabel?.let { " · $it" } ?: ""),
        divider = divider,
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Open ${row.name}", onClick = onClick),
        leading = { GlyphChip(text = row.symbol.take(1)) },
        trailing = {
            AmountWithBadge((row.marketValue ?: row.cost).formatInr()) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GainLine(row.gain, row.gainPercent)
                    StatusBadge(row.priceTag.text, row.priceTag.tone)
                }
            }
        },
    )
}
