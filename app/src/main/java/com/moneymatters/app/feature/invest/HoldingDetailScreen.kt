package com.moneymatters.app.feature.invest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.ui.components.ConfirmDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.MoneyText
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.PriceChart
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme

private const val PRICE_HISTORY_ROWS = 8

/** Holding detail (P14) for a stock: value and return, lots with their holding period, transactions and prices. */
@Composable
fun HoldingDetailScreen(
    detail: HoldingDetail,
    onBack: () -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onUpdatePrice: () -> Unit,
    modifier: Modifier = Modifier,
    onDividend: () -> Unit = {},
    onCorporateAction: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEditTrade: (transactionId: String) -> Unit = {},
    onDeleteTrade: (transactionId: String) -> Unit = {},
    onDeleteIncome: (transactionId: String) -> Unit = {},
    /** Why the last edit or delete was refused, if it was. */
    error: String? = null,
) {
    val row = detail.row
    val isBond = row.instrument == Instrument.Bond
    var confirm by remember { mutableStateOf<DeleteTarget?>(null) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = row.name, subtitle = row.symbol, onBack = onBack)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (error != null) item("error") { Module { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) } }
            item("summary") {
                Module(shape = MoneyTheme.shapes.xl) {
                    Text("Current value", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted)
                    MoneyText(row.marketValue ?: row.cost, style = MoneyTheme.type.metricHero, showPaise = false, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        GainLine(row.gain, row.gainPercent)
                    }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Buy more", onBuy, style = PillStyle.Primary)
                        if (row.hasUnits) Pill("Sell", onSell)
                        Pill("Update price", onUpdatePrice)
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(if (isBond) "Coupon" else "Dividend", onDividend)
                        if (!isBond) Pill("Split / bonus", onCorporateAction)
                    }
                }
            }
            detail.chart?.let { chart -> item("chart") { PriceChartModule(chart) } }
            item("facts") {
                Module {
                    KeyValueRow("Units held", row.unitsLabel)
                    KeyValueRow("Average cost", row.avgCostLabel)
                    KeyValueRow("Invested", row.cost.formatInr(showPaise = true))
                    KeyValueRow("Latest price") {
                        Text(row.priceLabel ?: "—", style = MoneyTheme.type.ledgerMono, color = MoneyTheme.colors.ink)
                        StatusBadge(row.priceTag.text, row.priceTag.tone)
                    }
                    detail.bond?.let { bond ->
                        KeyValueRow("Coupon", bond.couponLabel ?: "—")
                        KeyValueRow("Matures", bond.maturityLabel ?: "—")
                    }
                    KeyValueRow("XIRR", detail.xirr?.let(::formatPercent) ?: "—", divider = false)
                }
            }
            detail.taxPreview?.let { item("tax-preview") { TaxPreviewModule(it) } }
            if (detail.lots.isNotEmpty()) {
                item("lots") {
                    Module {
                        ModuleHeader("Lots", subtitle = "Sold oldest first")
                        detail.lots.forEachIndexed { i, lot ->
                            ListRow(
                                title = "${lot.unitsLabel} units",
                                subtitle = lot.dateLabel + (lot.note?.let { " · $it" } ?: ""),
                                divider = i < detail.lots.lastIndex,
                                trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        StatusBadge(lot.termLabel, if (lot.isLongTerm) Tone.Positive else Tone.Neutral)
                                        Text(lot.cost.formatInr(), style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink, softWrap = false)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (detail.actions.isNotEmpty()) {
                item("actions") {
                    Module {
                        ModuleHeader("Dividends & actions", subtitle = if (detail.dividendTotal.value > 0) "${detail.dividendTotal.formatInr()} received so far" else null)
                        detail.actions.forEachIndexed { i, a ->
                            ListRow(
                                title = a.label,
                                subtitle = a.dateLabel,
                                divider = i < detail.actions.lastIndex,
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        a.amount?.let { Text(it.formatInr(), style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink, softWrap = false) }
                                        a.incomeTransactionId?.let { id -> Pill("Delete", { confirm = DeleteTarget.Income(id) }, height = 26.dp) }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (detail.transactions.isNotEmpty()) {
                item("transactions") {
                    Module {
                        ModuleHeader("Trades")
                        detail.transactions.forEachIndexed { i, t ->
                            ListRow(
                                title = "${if (t.isBuy) "Bought" else "Sold"} ${t.unitsLabel}",
                                subtitle = t.dateLabel,
                                divider = i < detail.transactions.lastIndex,
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(t.amount.formatInr(), style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink, softWrap = false)
                                        if (t.canEdit || t.canDelete) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (t.canEdit) Pill("Edit", { onEditTrade(t.id) }, height = 26.dp)
                                                if (t.canDelete) Pill("Delete", { confirm = DeleteTarget.Trade(t.id) }, height = 26.dp)
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item("manage") {
                Module {
                    ModuleHeader("Manage", subtitle = "Correct what you entered, or remove it everywhere")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Edit details", onEdit)
                        Pill("Delete ${if (isBond) "bond" else if (row.instrument == Instrument.MutualFund) "fund" else "stock"}", { confirm = DeleteTarget.Holding }, style = PillStyle.Destructive)
                    }
                }
            }
            if (detail.prices.isNotEmpty()) {
                item("prices") {
                    Module {
                        ModuleHeader("Price history")
                        detail.prices.take(PRICE_HISTORY_ROWS).forEachIndexed { i, p ->
                            ListRow(
                                title = p.priceLabel,
                                subtitle = "${p.dateLabel} · ${p.source}",
                                divider = i < minOf(detail.prices.size, PRICE_HISTORY_ROWS) - 1,
                                trailing = { StatusBadge(p.tag.text, p.tag.tone) },
                            )
                        }
                    }
                }
            }
        }
    }

    confirm?.let { target ->
        when (target) {
            DeleteTarget.Holding -> ConfirmDialog(
                "Delete ${row.name}?",
                "This removes it and every trade, dividend, price and SIP for it, along with their entries on the Transactions and Accountant pages. Balances and net worth are worked out again without it. It can't be undone.",
                "Delete", onDelete, { confirm = null },
            )
            is DeleteTarget.Trade -> ConfirmDialog(
                "Delete this trade?",
                "The entry disappears from the Transactions and Accountant pages, and the units, cost and gains are worked out again without it.",
                "Delete", { onDeleteTrade(target.id) }, { confirm = null },
            )
            is DeleteTarget.Income -> ConfirmDialog(
                "Delete this ${if (isBond) "coupon" else "dividend"}?",
                "Its income entry is removed from the Transactions and Accountant pages.",
                "Delete", { onDeleteIncome(target.id) }, { confirm = null },
            )
        }
    }
}

@Composable
private fun PriceChartModule(chart: PriceChartData) {
    val c = MoneyTheme.colors
    Module {
        ModuleHeader("Price", subtitle = "${chart.values.size} prices, ${chart.firstLabel} to ${chart.lastLabel}")
        PriceChart(chart.values, reference = chart.averageCost)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Low ${chart.lowLabel}", style = MoneyTheme.type.bodySm, color = c.muted)
            Text("High ${chart.highLabel}", style = MoneyTheme.type.bodySm, color = c.muted)
        }
        chart.averageCostLabel?.let {
            Text("- - Your average cost $it", style = MoneyTheme.type.bodySm, color = c.champagne, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** What a confirmed deletion on the holding page will remove. */
private sealed interface DeleteTarget {
    data object Holding : DeleteTarget
    data class Trade(val id: String) : DeleteTarget
    data class Income(val id: String) : DeleteTarget
}
