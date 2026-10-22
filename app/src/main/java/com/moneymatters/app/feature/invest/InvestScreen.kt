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
    onBuy: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
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
                    Pill("+ Buy", onBuy, style = PillStyle.Primary)
                }
            }
        }
        if (state.holdings.isEmpty()) {
            item("empty") {
                Module(Modifier.padding(horizontal = margin)) {
                    ModuleHeader("No stocks yet", subtitle = "Buy one, or import your trade history from a CSV to rebuild your holdings exactly.")
                }
            }
        } else {
            item("summary") { SummaryModule(state, Modifier.padding(horizontal = margin)) }
            item("holdings") {
                Module(Modifier.padding(horizontal = margin)) {
                    ModuleHeader("Stocks", subtitle = "${state.holdings.size} ${if (state.holdings.size == 1) "holding" else "holdings"}")
                    state.holdings.forEachIndexed { i, row -> HoldingListRow(row, divider = i < state.holdings.lastIndex, onClick = { onOpenHolding(row.id) }) }
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
                Text(state.cost.formatInr(), style = MoneyTheme.type.metricTable, color = c.ink)
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
        subtitle = "${row.unitsLabel} units · avg ${row.avgCostLabel}",
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
