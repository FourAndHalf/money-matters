package com.moneymatters.app.feature.invest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
    val row = detail.row
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
            item("summary") {
                Module(shape = MoneyTheme.shapes.xl) {
                    Text("Current value", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted)
                    MoneyText(row.marketValue ?: row.cost, style = MoneyTheme.type.metricHero, showPaise = false, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                    GainLine(row.gain, row.gainPercent)
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Buy more", onBuy, style = PillStyle.Primary)
                        if (row.hasUnits) Pill("Sell", onSell)
                        Pill("Update price", onUpdatePrice)
                    }
                }
            }
            item("facts") {
                Module {
                    KeyValueRow("Units held", row.unitsLabel)
                    KeyValueRow("Average cost", row.avgCostLabel)
                    KeyValueRow("Invested", row.cost.formatInr(showPaise = true))
                    KeyValueRow("Latest price") {
                        Text(row.priceLabel ?: "—", style = MoneyTheme.type.ledgerMono, color = MoneyTheme.colors.ink)
                        StatusBadge(row.priceTag.text, row.priceTag.tone)
                    }
                    KeyValueRow("XIRR", detail.xirr?.let(::formatPercent) ?: "—", divider = false)
                }
            }
            if (detail.lots.isNotEmpty()) {
                item("lots") {
                    Module {
                        ModuleHeader("Lots", subtitle = "Sold oldest first")
                        detail.lots.forEachIndexed { i, lot ->
                            ListRow(
                                title = "${lot.unitsLabel} units",
                                subtitle = lot.dateLabel,
                                divider = i < detail.lots.lastIndex,
                                trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        StatusBadge(lot.termLabel, if (lot.isLongTerm) Tone.Positive else Tone.Neutral)
                                        Text(lot.cost.formatInr(), style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink)
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
                                trailing = { Text(t.amount.formatInr(), style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink) },
                            )
                        }
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
}
