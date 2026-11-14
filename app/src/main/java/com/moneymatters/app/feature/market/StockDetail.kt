package com.moneymatters.app.feature.market

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
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.market.AlertKind
import com.moneymatters.app.core.market.Verdict
import com.moneymatters.app.core.market.scorecardFor
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.InnerPanel
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.PriceChart
import com.moneymatters.app.ui.components.ProgressTrack
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme

private val NEEDS_AMOUNT = setOf(AlertKind.PriceAbove, AlertKind.PriceBelow, AlertKind.MoveOneDay)

/** One stock: price and range, scorecard against the market, red flags, and its alerts. */
@Composable
internal fun StockDetail(vm: MarketViewModel, symbol: String, onBack: () -> Unit, onNeedNotificationPermission: () -> Unit, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val m = vm.metricsOf(symbol)
    val watched = vm.watch.any { it.symbol == symbol }
    val rules = vm.rules.filter { it.symbol == symbol }
    val card = remember(m, vm.universe) { m?.let { scorecardFor(it, vm.universe) } }
    var adding by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(AlertKind.PriceAbove) }
    var amount by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(
                title = symbol, subtitle = m?.name, onBack = onBack,
                action = {
                    Pill(if (watched) "Watching ✓" else "+ Watch", { if (watched) vm.unwatch(symbol) else vm.watch(symbol, m?.name ?: symbol) }, style = if (watched) PillStyle.Secondary else PillStyle.Primary)
                },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (m == null) item("none") { Module { Text("No stored price for $symbol. Download price history first.", style = MoneyTheme.type.bodyMd, color = c.muted) } }
            else {
                item("price") {
                    Module {
                        Text(rupeesLabel(m.last), style = MoneyTheme.type.metricHero, color = c.ink)
                        Text("${percentLabel(m.dayChangePct)} on ${dayLabel(m.date)}", style = MoneyTheme.type.bodyMd, color = changeColor(m.dayChangePct))
                        val closes = vm.openBars.takeLast(250).map { it.close.toFloat() }
                        if (closes.size >= 2) PriceChart(closes, Modifier.padding(top = 12.dp))
                        Column(Modifier.padding(top = 8.dp)) {
                            KeyValueRow("52-week range", "${rupeesLabel(m.low52)} – ${rupeesLabel(m.high52)}")
                            KeyValueRow("From 52-week high", percentLabel(m.fromHighPct))
                            KeyValueRow("1 month", percentLabel(m.ret1m))
                            KeyValueRow("3 months", percentLabel(m.ret3m))
                            KeyValueRow("1 year", percentLabel(m.ret1y))
                            KeyValueRow("RSI (14)", m.rsi14?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "—")
                            KeyValueRow("50-day average", m.sma50?.let(::rupeesLabel) ?: "needs 50 days")
                            KeyValueRow("200-day average", m.sma200?.let(::rupeesLabel) ?: "needs 200 days", divider = false)
                        }
                        if (m.historyDays < 340) Text(
                            "Only ${m.historyDays} days of history are stored, so the 52-week range and long averages may be incomplete. Download a year from the Markets screen.",
                            style = MoneyTheme.type.bodySm, color = c.warning, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                card?.let { sc ->
                    item("scorecard") {
                        Module {
                            ModuleHeader("Scorecard", subtitle = "Scored 0 to 10 against every NSE stock, on price and volume only")
                            sc.cards.forEachIndexed { i, cd ->
                                Column(Modifier.padding(vertical = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(cd.title, style = MoneyTheme.type.headlineMd, color = c.ink, modifier = Modifier.weight(1f))
                                        if (cd.score != null) StatusBadge(String.format(java.util.Locale.US, "%.1f / 10", cd.score), when (cd.verdict) { Verdict.Good -> Tone.Positive; Verdict.Fair -> Tone.Warning; else -> Tone.Negative })
                                        else StatusBadge("Not enough data", Tone.Neutral)
                                    }
                                    if (cd.score != null) ProgressTrack((cd.score / 10).toFloat(), Modifier.padding(top = 6.dp), over = cd.verdict == Verdict.Weak)
                                    cd.lines.forEach { Text(it, style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 3.dp)) }
                                }
                            }
                        }
                    }
                    item("flags") {
                        Module {
                            ModuleHeader("Red flags", subtitle = "From price, volume and NSE series only")
                            if (sc.redFlags.isEmpty()) Text("None found in the price data.", style = MoneyTheme.type.bodyMd, color = c.muted)
                            sc.redFlags.forEachIndexed { i, f -> ListRow(f.title, subtitle = f.detail, divider = i < sc.redFlags.lastIndex, leading = { StatusBadge("▼", Tone.Negative) }) }
                            Text("Promoter pledging, exchange surveillance lists and default risk need data this app does not have.", style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            item("alerts") {
                Module {
                    ModuleHeader("Alerts", subtitle = "Checked after each price download", action = { Pill(if (adding) "Cancel" else "+ Alert", { adding = !adding }, height = 28.dp) })
                    rules.forEachIndexed { i, r ->
                        ListRow(r.kind.label + (r.threshold?.let { " " + (if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()) } ?: ""), divider = i < rules.lastIndex,
                            trailing = { Pill("Delete", { vm.deleteRule(r.id) }, style = PillStyle.Destructive, height = 26.dp) })
                    }
                    if (rules.isEmpty() && !adding) Text("No alerts for this stock.", style = MoneyTheme.type.bodyMd, color = c.muted)
                    if (adding) InnerPanel(Modifier.padding(top = 8.dp)) {
                        AlertKind.entries.forEach { k ->
                            Row(Modifier.fillMaxWidth().clickable { kind = k }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text((if (k == kind) "● " else "○ ") + k.label, style = MoneyTheme.type.bodyMd, color = if (k == kind) c.ink else c.muted)
                            }
                        }
                        if (kind in NEEDS_AMOUNT) FormField(if (kind == AlertKind.MoveOneDay) "Percent" else "Price (₹)", amount, { amount = it }, Modifier.padding(top = 8.dp), keyboardType = KeyboardType.Decimal)
                        Pill("Set alert", {
                            val t = amount.toDoubleOrNull()
                            if (kind !in NEEDS_AMOUNT || (t != null && t > 0)) {
                                vm.addRule(symbol, kind, if (kind in NEEDS_AMOUNT) t else null)
                                onNeedNotificationPermission()
                                adding = false; amount = ""
                            }
                        }, style = PillStyle.Primary, modifier = Modifier.padding(top = 10.dp).fillMaxWidth())
                    }
                }
            }
        }
    }
}
