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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.invest.PriceSources
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme

/** Prices (P15): how each source is doing, every held price with its status, the download log, and the source addresses. */
@Composable
fun PricesScreen(
    state: PricesState,
    sources: PriceSources,
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateManual: (holdingId: String) -> Unit,
    onConfirm: (priceId: Long) -> Unit,
    onResolve: (holdingId: String, dateIso: String, keepDownloaded: Boolean) -> Unit,
    onSaveSources: (PriceSources) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var nseText by remember(sources) { mutableStateOf(sources.nseUrlTemplate) }
    var amfiText by remember(sources) { mutableStateOf(sources.amfiUrl) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(
                title = "Prices",
                subtitle = if (state.attentionCount == 0) "All prices verified" else "${state.attentionCount} to review",
                onBack = onBack,
                action = { Pill(if (isRefreshing) "Refreshing…" else "Refresh", { if (!isRefreshing) onRefresh() }, style = PillStyle.Primary) },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("sources") {
                Module {
                    ModuleHeader("Sources", subtitle = "Downloaded weekly, and whenever you refresh")
                    state.sources.forEachIndexed { i, s ->
                        ListRow(
                            title = s.name,
                            subtitle = s.detail,
                            divider = i < state.sources.lastIndex,
                            trailing = { StatusBadge(s.headline, s.tone) },
                        )
                    }
                }
            }
            if (state.rows.isNotEmpty()) {
                item("prices") {
                    Module {
                        ModuleHeader("Latest prices", subtitle = "Tap a price to enter one by hand")
                        state.rows.forEachIndexed { i, row ->
                            ListRow(
                                title = row.name,
                                subtitle = listOfNotNull(row.priceLabel, row.detailLabel).joinToString(" · ").ifEmpty { "No price yet" },
                                divider = i < state.rows.lastIndex,
                                modifier = Modifier.clickableRow { onUpdateManual(row.holdingId) },
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StatusBadge(row.tag.text, row.tag.tone)
                                        PriceActions(row, onConfirm, onResolve)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (state.log.isNotEmpty()) {
                item("log") {
                    Module {
                        ModuleHeader("Download log", subtitle = "Every attempt, so a failure is never hidden behind an old price")
                        state.log.forEachIndexed { i, entry ->
                            ListRow(
                                title = "${entry.source} · ${entry.whenLabel}",
                                subtitle = entry.message,
                                divider = i < state.log.lastIndex,
                                trailing = { StatusBadge(if (entry.ok) "OK" else "Failed", if (entry.ok) Tone.Positive else Tone.Negative) },
                            )
                        }
                    }
                }
            }
            item("addresses") {
                Module {
                    ModuleHeader("Source addresses", subtitle = "NSE has moved its file before; change the address here if a download starts failing")
                    if (!editing) {
                        Pill("Change addresses", { editing = true })
                    } else {
                        FormField("NSE closing prices (use {yyyyMMdd} for the trading day)", nseText, { nseText = it })
                        Column(Modifier.padding(top = 10.dp)) { FormField("AMFI NAVs", amfiText, { amfiText = it }) }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Save", { onSaveSources(PriceSources(nseText.trim(), amfiText.trim())); editing = false }, style = PillStyle.Primary)
                            Pill("Reset", { nseText = PriceSources.Default.nseUrlTemplate; amfiText = PriceSources.Default.amfiUrl })
                            Pill("Cancel", { editing = false; nseText = sources.nseUrlTemplate; amfiText = sources.amfiUrl })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceActions(row: PriceRow, onConfirm: (Long) -> Unit, onResolve: (String, String, Boolean) -> Unit) {
    val priceId = row.priceId
    val dateIso = row.dateIso
    when {
        row.status == PriceStatus.Mismatch && dateIso != null ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Keep mine", { onResolve(row.holdingId, dateIso, false) }, height = 26.dp)
                Pill("Use download", { onResolve(row.holdingId, dateIso, true) }, style = PillStyle.Primary, height = 26.dp)
            }
        (row.status == PriceStatus.Suspicious || row.status == PriceStatus.Stale) && priceId != null ->
            Pill("Confirm", { onConfirm(priceId) }, height = 26.dp)
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick))
