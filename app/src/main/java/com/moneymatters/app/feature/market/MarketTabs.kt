package com.moneymatters.app.feature.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
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
import com.moneymatters.app.core.market.MoodZone
import com.moneymatters.app.core.market.PresetScreens
import com.moneymatters.app.core.market.ScreenCondition
import com.moneymatters.app.core.market.ScreenMetric
import com.moneymatters.app.core.market.ScreenOp
import com.moneymatters.app.core.market.StockMetrics
import com.moneymatters.app.core.market.decodeConditions
import com.moneymatters.app.core.market.runScreen
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.InnerPanel
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.ProgressTrack
import com.moneymatters.app.ui.components.SearchField
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.components.Toggle
import com.moneymatters.app.ui.theme.MoneyTheme

private const val MAX_ROWS = 40

@Composable
private fun StockRow(m: StockMetrics, divider: Boolean, detail: String? = null, onClick: () -> Unit) {
    val c = MoneyTheme.colors
    ListRow(
        title = m.symbol,
        subtitle = detail ?: m.name,
        divider = divider,
        modifier = Modifier.clickable(onClick = onClick),
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(rupeesLabel(m.last), style = MoneyTheme.type.bodyMd, color = c.ink)
                Text(percentLabel(m.dayChangePct), style = MoneyTheme.type.bodySm, color = changeColor(m.dayChangePct))
            }
        },
    )
}

internal fun LazyListScope.watchlistItems(vm: MarketViewModel) {
    item("watch") {
        val c = MoneyTheme.colors
        var query by remember { mutableStateOf("") }
        val q = query.trim()
        Module {
            ModuleHeader("Watchlist", subtitle = "Stocks you follow but may not own")
            SearchField(query, { query = it }, placeholder = "Find a stock by symbol or name")
            Column(Modifier.padding(top = 8.dp)) {
                if (q.length >= 2) {
                    val hits = vm.universe.filter { it.symbol.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
                        .sortedByDescending { it.turnoverLakh ?: 0.0 }.take(20)
                    if (hits.isEmpty()) Text(
                        if (vm.universe.isEmpty()) "Download prices first, then search the market." else "No stock matches that.",
                        style = MoneyTheme.type.bodyMd, color = c.muted, modifier = Modifier.padding(top = 4.dp),
                    )
                    hits.forEachIndexed { i, m -> StockRow(m, i < hits.lastIndex) { query = ""; vm.open(m.symbol) } }
                } else {
                    val watched = vm.watch
                    if (watched.isEmpty()) Text(
                        "Nothing here yet. Search for a stock and tap it to see its scorecard, then add it to your watchlist.",
                        style = MoneyTheme.type.bodyMd, color = c.muted, modifier = Modifier.padding(top = 4.dp),
                    )
                    watched.forEachIndexed { i, w ->
                        val m = vm.metricsOf(w.symbol)
                        if (m != null) StockRow(m, i < watched.lastIndex) { vm.open(w.symbol) }
                        else ListRow(w.symbol, subtitle = "${w.name} · no recent price", divider = i < watched.lastIndex, modifier = Modifier.clickable { vm.open(w.symbol) })
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.screenerItems(vm: MarketViewModel) {
    item("screener") {
        val c = MoneyTheme.colors
        var metric by remember { mutableStateOf(ScreenMetric.Ret3m) }
        var op by remember { mutableStateOf(ScreenOp.Above) }
        var value by remember { mutableStateOf("") }
        Module {
            ModuleHeader("Screener", subtitle = "${vm.universe.size} stocks · all conditions must hold")
            Text("Start from a screen", style = MoneyTheme.type.labelUppercase, color = c.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetScreens.forEach { p -> Pill(p.name, { vm.conditions = p.conditions }) }
                vm.saved.forEach { s -> Pill("★ ${s.name}", { vm.conditions = decodeConditions(s.conditions) }) }
            }
            if (vm.conditions.isEmpty()) Text("No conditions yet. Pick a screen above or add your own below.", style = MoneyTheme.type.bodyMd, color = c.muted)
            vm.conditions.forEachIndexed { i, cond ->
                KeyValueRow(
                    "${cond.metric.label} ${cond.op.symbol} ${trim(cond.value)}${cond.metric.unit.let { if (it.isEmpty() || it.startsWith("₹")) "" else " $it" }}",
                    divider = i < vm.conditions.lastIndex,
                ) { Pill("Remove", { vm.conditions = vm.conditions - cond }, height = 26.dp) }
            }
            InnerPanel(Modifier.padding(top = 12.dp)) {
                Text("Add a condition", style = MoneyTheme.type.labelUppercase, color = c.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenMetric.entries.forEach { m -> Pill(m.label, { metric = m }, style = if (m == metric) PillStyle.Primary else PillStyle.Secondary, height = 28.dp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Pill(if (op == ScreenOp.Above) "Above  >" else "Below  <", { op = if (op == ScreenOp.Above) ScreenOp.Below else ScreenOp.Above }, height = 40.dp)
                    FormField("Value${metric.unit.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""}", value, { value = it }, Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                }
                Pill("Add condition", {
                    value.toDoubleOrNull()?.let { vm.conditions = vm.conditions + ScreenCondition(metric, op, it); value = "" }
                }, style = PillStyle.Primary, modifier = Modifier.padding(top = 10.dp).fillMaxWidth())
            }
        }
    }
    item("results") {
        val c = MoneyTheme.colors
        val results = remember(vm.universe, vm.conditions) { runScreen(vm.universe, vm.conditions, vm.conditions.firstOrNull()?.metric ?: ScreenMetric.Turnover) }
        var saveName by remember { mutableStateOf("") }
        if (vm.conditions.isNotEmpty()) Module {
            ModuleHeader("Matches", subtitle = "${results.size} of ${vm.universe.size}" + if (results.size > MAX_ROWS) " · showing $MAX_ROWS" else "")
            if (vm.universe.isEmpty()) Text("Download prices first.", style = MoneyTheme.type.bodyMd, color = c.muted)
            val lead = vm.conditions.first().metric
            results.take(MAX_ROWS).forEachIndexed { i, m ->
                StockRow(m, i < minOf(results.size, MAX_ROWS) - 1, detail = "${lead.label}: ${lead.read(m)?.let { trim(it) } ?: "—"}") { vm.open(m.symbol) }
            }
            if (results.isEmpty() && vm.universe.isNotEmpty()) Text("No stock passes every condition. Loosen one.", style = MoneyTheme.type.bodyMd, color = c.muted)
            InnerPanel(Modifier.padding(top = 12.dp)) {
                FormField("Save this screen as", saveName, { saveName = it }, placeholder = "e.g. My momentum picks")
                Pill("Save screen", { vm.saveScreen(saveName); saveName = "" }, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
            }
            vm.saved.forEach { s -> Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("★ ${s.name}", style = MoneyTheme.type.bodyMd, color = c.ink, modifier = Modifier.weight(1f))
                Pill("Delete", { vm.deleteScreen(s.id) }, style = PillStyle.Destructive, height = 26.dp)
            } }
        }
    }
}

private fun trim(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

internal fun LazyListScope.moodItems(vm: MarketViewModel) {
    item("mood") {
        val c = MoneyTheme.colors
        val mood = vm.mood
        Module {
            ModuleHeader("Market mood", subtitle = "Fear or greed, 0 to 100")
            if (mood == null || mood.score == null) {
                Text(
                    if (vm.status.days == 0) "Download price history to see the mood." else "Not enough history yet. The gauge needs a few weeks of daily prices; download a year to fill it.",
                    style = MoneyTheme.type.bodyMd, color = c.muted,
                )
            } else {
                val score = mood.score
                val zone = mood.zone!!
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(score.toInt().toString(), style = MoneyTheme.type.metricHero, color = c.ink)
                    Column {
                        StatusBadge(zone.label, when (zone) { MoodZone.ExtremeFear -> Tone.Info; MoodZone.Fear -> Tone.Warning; MoodZone.Greed -> Tone.Positive; MoodZone.ExtremeGreed -> Tone.Negative })
                        Text("as of ${dayLabel(mood.date)}", style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                ProgressTrack((score / 100).toFloat(), Modifier.padding(vertical = 10.dp))
                Text(zone.meaning, style = MoneyTheme.type.bodyMd, color = c.ink)
                Text(
                    "Built from ${mood.factors.count { it.score != null }} of 6 factors. Each is compared with its own last 45 trading days and the results are averaged equally. It is a short-term gauge of how today compares with the recent past, not a forecast.",
                    style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    item("factors") {
        val c = MoneyTheme.colors
        val factors = vm.mood?.factors ?: return@item
        Module {
            ModuleHeader("The six factors")
            factors.forEachIndexed { i, f ->
                ListRow(
                    f.name, subtitle = f.note ?: f.reads, divider = i < factors.lastIndex,
                    trailing = {
                        if (f.score != null) Column(horizontalAlignment = Alignment.End) {
                            Text(f.score.toInt().toString(), style = MoneyTheme.type.metricTable, color = c.ink)
                            Text(f.today?.let { "${trim(it)}%" } ?: "", style = MoneyTheme.type.bodySm, color = c.muted)
                        } else StatusBadge("Missing", Tone.Neutral)
                    },
                )
            }
        }
    }
}

internal fun LazyListScope.alertItems(vm: MarketViewModel) {
    item("events") {
        val c = MoneyTheme.colors
        Module {
            ModuleHeader("Fired alerts", subtitle = "Checked after each price download")
            if (vm.events.isEmpty()) Text("Nothing has fired yet.", style = MoneyTheme.type.bodyMd, color = c.muted)
            vm.events.take(30).forEachIndexed { i, e ->
                ListRow(e.message, subtitle = dayLabel(java.time.LocalDate.parse(e.priceDate)), divider = i < minOf(vm.events.size, 30) - 1,
                    modifier = Modifier.clickable { vm.open(e.symbol) },
                    trailing = { if (!e.seen) StatusBadge("New", Tone.Info) })
            }
        }
    }
    item("rules") {
        val c = MoneyTheme.colors
        Module {
            ModuleHeader("Your alert rules", subtitle = "Add one from a stock's page")
            if (vm.rules.isEmpty()) Text("No rules yet. Open a stock, then choose an alert.", style = MoneyTheme.type.bodyMd, color = c.muted)
            vm.rules.forEachIndexed { i, r ->
                ListRow(
                    r.symbol, subtitle = r.kind.label + (r.threshold?.let { " " + trim(it) } ?: ""), divider = i < vm.rules.lastIndex,
                    modifier = Modifier.clickable { vm.open(r.symbol) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Toggle(r.active, { vm.setRuleActive(r, it) })
                            Pill("Delete", { vm.deleteRule(r.id) }, style = PillStyle.Destructive, height = 26.dp)
                        }
                    },
                )
            }
        }
    }
}

internal fun LazyListScope.portfolioItems(vm: MarketViewModel) {
    item("portfolio") {
        val c = MoneyTheme.colors
        val p = vm.portfolio
        Module {
            ModuleHeader("Portfolio checks", subtitle = "Spread and red flags across what you hold")
            if (p == null) Text("Add investments in the Invest tab to see how spread out they are.", style = MoneyTheme.type.bodyMd, color = c.muted)
            else {
                KeyValueRow("Spread score", "${p.spreadScore ?: 0} / 100")
                KeyValueRow("Largest holding", "${trim(p.topHoldingPct)}%")
                KeyValueRow("Top three holdings", "${trim(p.topThreePct)}%")
                KeyValueRow("Holdings with flags", "${p.flaggedCount} (${trim(p.flaggedPct)}% of value)", divider = false)
                Text(
                    "The spread score is 100 when value is shared evenly over ten or more holdings and 0 when it is all in one. It is our own measure, not Tickertape's. Funds carry no price flags here because only stock prices are stored.",
                    style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    item("positions") {
        val c = MoneyTheme.colors
        val p = vm.portfolio ?: return@item
        Module {
            ModuleHeader("By holding")
            p.weights.forEachIndexed { i, w ->
                Column(Modifier.fillMaxWidth().then(if (w.symbol.isNotBlank() && vm.metricsOf(w.symbol) != null) Modifier.clickable { vm.open(w.symbol) } else Modifier)) {
                    ListRow(
                        w.name, subtitle = w.flags.firstOrNull()?.let { f -> f.title + if (w.flags.size > 1) " +${w.flags.size - 1}" else "" } ?: "No flags", divider = false,
                        trailing = { Text("${trim(w.weightPct)}%", style = MoneyTheme.type.metricTable, color = if (w.flags.isEmpty()) c.ink else c.negative) },
                    )
                    ProgressTrack((w.weightPct / 100).toFloat(), over = w.flags.isNotEmpty())
                    w.flags.forEach { f -> Text("• ${f.title}: ${f.detail}", style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 4.dp)) }
                    if (i < p.weights.lastIndex) Column(Modifier.padding(vertical = 6.dp)) { com.moneymatters.app.ui.components.RowDivider() }
                }
            }
        }
    }
}
