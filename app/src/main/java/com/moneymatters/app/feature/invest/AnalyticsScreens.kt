package com.moneymatters.app.feature.invest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme
import java.util.Locale

/** Allocation by class against the equity target, with drift and single-stock concentration warnings. */
@Composable
fun AllocationModule(
    analytics: AnalyticsState,
    onNudgeTarget: (delta: Int) -> Unit,
    onOpenHolding: (holdingId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MoneyTheme.colors
    Module(modifier) {
        ModuleHeader("Allocation", subtitle = analytics.targetEquityPercent?.let { "Equity target $it%" } ?: "Set an equity target to track drift")
        analytics.allocation.forEachIndexed { i, slice ->
            ListRow(
                title = slice.label,
                subtitle = String.format(Locale.ROOT, "%.1f%%", slice.fraction * 100),
                divider = i < analytics.allocation.lastIndex,
                trailing = { Text(slice.value.formatInr(), style = MoneyTheme.type.metricTable, color = c.ink) },
            )
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Equity target", style = MoneyTheme.type.bodySm, color = c.muted)
            Pill("−5", { onNudgeTarget(-5) }, height = 28.dp)
            Pill("+5", { onNudgeTarget(5) }, height = 28.dp)
        }
        analytics.driftMessage?.let { StatusBadge(it, Tone.Warning, modifier = Modifier.padding(top = 10.dp), height = 24.dp) }
        analytics.concentration.forEach { row ->
            ListRow(
                title = row.name,
                subtitle = "Over 10% of your equity in one stock",
                trailing = { StatusBadge(row.percentLabel, Tone.Warning) },
                modifier = Modifier.padding(top = 4.dp).clickable(role = Role.Button, onClickLabel = "Open ${row.name}") { onOpenHolding(row.holdingId) },
            )
        }
    }
}

/** "If sold today": the gain by term for everything sellable, nothing recorded. */
@Composable
fun TaxPreviewModule(preview: TaxPreviewRow, modifier: Modifier = Modifier) {
    Module(modifier) {
        ModuleHeader("Tax if sold today", subtitle = "Oldest units first at the latest price. Nothing is recorded.")
        KeyValueRow("Units sold", preview.sellableLabel)
        preview.lockedLabel?.let { KeyValueRow("Still locked", it) }
        KeyValueRow("Proceeds", preview.proceeds.formatInr())
        KeyValueRow("Cost", preview.cost.formatInr())
        preview.lines.forEach { (label, gain) -> KeyValueRow(label + if (gain.value < 0) " loss" else " gain", gain.formatInr()) }
        KeyValueRow("Total gain", preview.gain.formatInr(), divider = false)
    }
}

/** The capital-gains register: realised sales by financial year, split by term, ready for the Tax page (P20). */
@Composable
fun GainsScreen(years: List<YearGains>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = "Capital gains", subtitle = "Realised, by financial year", onBack = onBack)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (years.isEmpty()) {
                item("empty") { Module { ModuleHeader("No sales yet", subtitle = "Gains appear here once you sell a stock or fund.") } }
            }
            items(years, key = { it.label }) { year ->
                Module {
                    ModuleHeader(year.label, subtitle = "${year.sales.size} ${if (year.sales.size == 1) "sale" else "sales"}")
                    KeyValueRow("Short-term", year.short.formatInr())
                    KeyValueRow("Long-term", year.long.formatInr())
                    if (year.slab.value != 0L) KeyValueRow("Slab rate (debt funds)", year.slab.formatInr())
                    KeyValueRow("Total", year.total.formatInr(), divider = year.sales.isNotEmpty())
                    year.sales.forEachIndexed { i, sale ->
                        ListRow(
                            title = sale.name,
                            subtitle = "${sale.dateLabel} · ${sale.unitsLabel} units · ${sale.termLabel}",
                            divider = i < year.sales.lastIndex,
                            trailing = { Text(sale.gain.formatInr(), style = MoneyTheme.type.metricTable, color = if (sale.gain.value >= 0) MoneyTheme.colors.positive else MoneyTheme.colors.negative) },
                        )
                    }
                }
            }
        }
    }
}
