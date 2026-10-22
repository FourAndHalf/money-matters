package com.moneymatters.app.feature.invest

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.invest.TradeParse
import com.moneymatters.app.core.invest.TradePreview
import com.moneymatters.app.core.invest.formatUnits
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.theme.MoneyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ERRORS_SHOWN = 8

/** Import a broker's trade history from a CSV file (picked with the system file chooser). */
@Composable
fun ImportScreen(viewModel: InvestViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                viewModel.loadCsv(text.orEmpty())
            }
        }
    }
    ImportContent(
        parse = viewModel.importParse,
        preview = viewModel.importPreview,
        canImport = viewModel.canImport,
        importedCount = viewModel.importDone,
        isSaving = viewModel.isSaving,
        error = viewModel.error,
        onChoose = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv")) },
        onImport = viewModel::confirmImport,
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
internal fun ImportContent(
    parse: TradeParse?,
    preview: TradePreview?,
    canImport: Boolean,
    importedCount: Int?,
    isSaving: Boolean,
    error: String?,
    onChoose: () -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val problems = (parse?.errors.orEmpty().map { "Line ${it.line}: ${it.message}" } + preview?.errors.orEmpty().map { "Line ${it.line}: ${it.message}" })
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = "Import trades", onBack = onClose)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("help") {
                Module {
                    ModuleHeader("Your trade history", subtitle = "A CSV with the columns date, symbol, type, units, amount (and optionally isin, name). Type is buy or sell; amount is the net ₹ paid or received after all charges. For a split or bonus, type it as split or bonus and put the ratio in units.")
                    Text(
                        "Trades are recorded as history against opening balances, so no bank balance changes. Every lot and past sale is rebuilt oldest-first. If any row is wrong, nothing is imported.",
                        style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted,
                    )
                    Pill("Choose CSV file", onChoose, modifier = Modifier.padding(top = 12.dp))
                }
            }
            if (importedCount != null) {
                item("done") {
                    Module { ModuleHeader("Imported $importedCount ${if (importedCount == 1) "trade" else "trades"}", subtitle = "Your holdings are on the Invest tab. Add a price to each to see its value.") }
                }
            } else if (preview != null && preview.symbols.isNotEmpty()) {
                item("preview") {
                    Module {
                        ModuleHeader("After import", subtitle = "${preview.symbols.size} ${if (preview.symbols.size == 1) "stock" else "stocks"}, ${parse?.rows?.size ?: 0} trades")
                        preview.symbols.forEachIndexed { i, s ->
                            ListRow(
                                title = s.name ?: s.symbol,
                                subtitle = "${s.trades} ${if (s.trades == 1) "trade" else "trades"} · ${s.symbol}",
                                divider = i < preview.symbols.lastIndex,
                                trailing = {
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                        Text("${formatUnits(s.unitsHeld)} units", style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink)
                                        Text(s.costHeld.formatInr(), style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (problems.isNotEmpty()) {
                item("problems") {
                    Module {
                        ModuleHeader("${problems.size} ${if (problems.size == 1) "problem" else "problems"} to fix", subtitle = "Correct the file and choose it again.")
                        problems.take(MAX_ERRORS_SHOWN).forEach { Text(it, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative, modifier = Modifier.padding(bottom = 6.dp)) }
                        if (problems.size > MAX_ERRORS_SHOWN) Text("…and ${problems.size - MAX_ERRORS_SHOWN} more", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted)
                    }
                }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) }
        }
        if (importedCount == null && parse != null) {
            Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
                val enabled = canImport && !isSaving
                Pill(
                    if (isSaving) "Importing…" else "Import",
                    onClick = { if (enabled) onImport() },
                    style = PillStyle.Primary,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
                )
            }
        }
    }
}
