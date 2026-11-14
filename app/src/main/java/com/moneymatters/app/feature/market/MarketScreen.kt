package com.moneymatters.app.feature.market

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.feature.invest.HoldingRow
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.theme.MoneyTheme

internal enum class MarketTab(val label: String) { Watchlist("Watchlist"), Screener("Screener"), Mood("Mood"), Alerts("Alerts"), Portfolio("Portfolio") }

/**
 * Markets: stock prices kept on the phone from NSE's daily files, with a watchlist, a screener, a market mood gauge,
 * price alerts and checks on what the investor holds. Nothing here is advice, and nothing is invented: a measure with
 * too little history says so.
 */
@Composable
fun MarketScreen(
    viewModel: MarketViewModel,
    held: List<HoldingRow>,
    onBack: () -> Unit,
    onNeedNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = MoneyTheme.colors
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    var tab by rememberSaveable { mutableStateOf(MarketTab.Watchlist) }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(held, viewModel.universe) { viewModel.checkPortfolio(held) }
    LaunchedEffect(tab) { if (tab == MarketTab.Alerts) viewModel.markEventsSeen() }

    val open = viewModel.openSymbol
    if (open != null) {
        BackHandler { viewModel.close() }
        StockDetail(viewModel, open, onBack = viewModel::close, onNeedNotificationPermission = onNeedNotificationPermission, modifier = modifier)
        return
    }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Markets", onBack = onBack, subtitle = "NSE prices, kept on this phone") }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = margin).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarketTab.entries.forEach { t ->
                val unseen = if (t == MarketTab.Alerts) viewModel.events.count { !it.seen } else 0
                Pill(if (unseen > 0) "${t.label} ($unseen)" else t.label, { tab = t }, style = if (t == tab) PillStyle.Primary else PillStyle.Secondary, height = 32.dp)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("data") { DataStrip(viewModel) }
            when (tab) {
                MarketTab.Watchlist -> watchlistItems(viewModel)
                MarketTab.Screener -> screenerItems(viewModel)
                MarketTab.Mood -> moodItems(viewModel)
                MarketTab.Alerts -> alertItems(viewModel)
                MarketTab.Portfolio -> portfolioItems(viewModel)
            }
            item("disclaimer") {
                Text(
                    "For information and learning, not investment advice. Figures come from NSE's end-of-day files and may lag or contain errors. Scores compare a stock with the rest of the market on price and volume only; they say nothing about the business.",
                    style = MoneyTheme.type.bodySm, color = c.muted,
                )
            }
        }
    }
}

@Composable
private fun DataStrip(vm: MarketViewModel) {
    val c = MoneyTheme.colors
    val s = vm.status
    Module {
        ModuleHeader(
            "Price history",
            subtitle = if (s.days == 0) "Nothing downloaded yet" else "${s.days} trading days, to ${s.newest?.let(::dayLabel)}",
        )
        if (s.days == 0) Text(
            "Prices come from NSE's free end-of-day files. Download a year to unlock 52-week ranges, averages, the screener and the mood gauge. It takes a few minutes on a good connection.",
            style = MoneyTheme.type.bodyMd, color = c.muted, modifier = Modifier.padding(bottom = 10.dp),
        )
        vm.progress?.let { (done, total) -> Text("Downloading day $done of $total…", style = MoneyTheme.type.bodyMd, color = c.ink, modifier = Modifier.padding(bottom = 8.dp)) }
        vm.message?.let { Text(it, style = MoneyTheme.type.bodyMd, color = c.ink, modifier = Modifier.padding(bottom = 8.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.days > 0) Pill(if (vm.busy) "Working…" else "Update", { vm.download(CATCH_UP_WEEKDAYS) }, style = PillStyle.Primary)
            Pill(if (s.days == 0) "Download a year" else "Fill gaps (a year)", { vm.download(FULL_HISTORY_WEEKDAYS) }, style = if (s.days == 0) PillStyle.Primary else PillStyle.Secondary)
        }
    }
}
