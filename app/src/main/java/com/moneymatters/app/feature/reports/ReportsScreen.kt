package com.moneymatters.app.feature.reports

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.moneymatters.app.core.export.balanceSheetCsv
import com.moneymatters.app.core.export.beancount
import com.moneymatters.app.core.export.cashFlowCsv
import com.moneymatters.app.core.export.incomeStatementCsv
import com.moneymatters.app.core.export.sharesCsv
import com.moneymatters.app.core.export.trialBalanceCsv
import com.moneymatters.app.core.reports.CashCategory
import com.moneymatters.app.core.reports.CashFlowStatement
import com.moneymatters.app.core.reports.MonthTrend
import com.moneymatters.app.core.reports.Share
import com.moneymatters.app.core.reports.cashFlow
import com.moneymatters.app.core.reports.categoryShares
import com.moneymatters.app.core.reports.monthlyTrend
import com.moneymatters.app.core.reports.payeeShares
import com.moneymatters.app.core.reports.paymentMethodShares
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.core.reports.BalanceSheet
import com.moneymatters.app.core.reports.IncomeStatement
import com.moneymatters.app.core.reports.ReportPeriod
import com.moneymatters.app.core.reports.StatementLine
import com.moneymatters.app.core.reports.TrialBalance
import com.moneymatters.app.core.reports.balanceSheet
import com.moneymatters.app.core.reports.incomeStatement
import com.moneymatters.app.core.reports.rangeContaining
import com.moneymatters.app.core.reports.trialBalance
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.ProgressTrack
import com.moneymatters.app.ui.components.SegmentedControl
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Day = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val DayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

internal enum class Statement(val label: String) { Income("Income"), Balance("Balance sheet"), CashFlow("Cash flow"), Trial("Trial balance"), Stats("Spending") }

/** Reports (plan.html → P18): the income statement, balance sheet and trial balance for a month or tax year. */
@Composable
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ledger by viewModel.snapshot.collectAsState()
    ledger?.let { ReportsContent(it, viewModel.monthStartDay(), onBack, modifier = modifier) }
}

@Composable
internal fun ReportsContent(ledger: LedgerSnapshot, monthStartDay: Int, onBack: () -> Unit, today: LocalDate = LocalDate.now(), modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    var statement by rememberSaveable { mutableStateOf(Statement.Income) }
    var period by rememberSaveable { mutableStateOf(ReportPeriod.Month) }
    var anchor by rememberSaveable { mutableStateOf(today.toString()) }
    val range = remember(period, anchor, monthStartDay) { period.rangeContaining(LocalDate.parse(anchor), monthStartDay) }
    val current = remember(period, today, monthStartDay) { period.rangeContaining(today, monthStartDay) }
    // The balance sheet and trial balance are as at the end of the period, but never later than today.
    val asOf = if (range.end.isAfter(today)) today else range.end
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var csvPending by remember { mutableStateOf("") }
    var csvName by remember { mutableStateOf("") }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    fun save(uri: Uri?, text: String) {
        if (uri == null) return
        scope.launch {
            exportMessage = try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("Couldn't open $uri") }
                "Saved"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "Couldn't save the file"
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> save(uri, csvPending) }
    val beancountLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> save(uri, beancount(ledger)) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Reports", onBack = onBack) }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("pick") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Statement.entries.forEach { st -> Pill(st.label, { statement = st }, style = if (statement == st) PillStyle.Primary else PillStyle.Secondary) }
                    }
                    SegmentedControl(ReportPeriod.entries, period, { period = it; anchor = today.toString() }, { if (it == ReportPeriod.Month) "Month" else "Tax year" })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Pill("‹", { anchor = range.start.minusDays(1).toString() }, height = 28.dp, modifier = Modifier.padding(end = 8.dp))
                        Text(rangeLabel(range, period), style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.ink)
                        if (range.end.isBefore(current.start)) Pill("›", { anchor = range.end.plusDays(1).toString() }, height = 28.dp) else Spacer(Modifier.width(36.dp))
                    }
                }
            }
            when (statement) {
                Statement.Income -> item("income") { IncomeModule(incomeStatement(ledger, range)) }
                Statement.Balance -> item("balance") { BalanceModule(balanceSheet(ledger, asOf)) }
                Statement.CashFlow -> item("cash") { CashFlowModule(cashFlow(ledger, range)) }
                Statement.Trial -> item("trial") { TrialModule(trialBalance(ledger, asOf)) }
                Statement.Stats -> item("stats") {
                    StatsModule(categoryShares(ledger, range), payeeShares(ledger, range), paymentMethodShares(ledger, range), monthlyTrend(ledger, today, monthStartDay))
                }
            }
            item("export") {
                ExportModule(
                    onCsv = { csvPending = csvFor(statement, ledger, range, asOf, today, monthStartDay); csvName = "${statement.name.lowercase()}-${range.start}.csv"; csvLauncher.launch(csvName) },
                    onBeancount = { beancountLauncher.launch("money-matters.beancount") },
                    message = exportMessage,
                )
            }
            item("note") {
                Text(
                    "Built from posted entries only; drafts are not counted. Holdings are carried at cost, as in the ledger.",
                    style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted,
                )
            }
        }
    }
}

internal fun rangeLabel(range: DateRange, period: ReportPeriod): String =
    if (period == ReportPeriod.TaxYear) "Tax year ${range.start.year}–${(range.end.year % 100).toString().padStart(2, '0')}"
    else "${range.start.format(DayMonth)} – ${range.end.format(Day)}"

@Composable
private fun Lines(lines: List<StatementLine>) {
    lines.forEachIndexed { i, l -> KeyValueRow(l.name, l.amount.formatInr(showPaise = true), divider = i < lines.lastIndex) }
}

@Composable
private fun Total(label: String, amount: Paise) {
    KeyValueRow(label, amount.formatInr(showPaise = true), valueColor = MoneyTheme.colors.ink, divider = false, modifier = Modifier.padding(top = 4.dp))
}

@Composable
internal fun IncomeModule(s: IncomeStatement) {
    Module {
        ModuleHeader("Income")
        if (s.income.isEmpty()) Text("No income in this period.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted)
        Lines(s.income)
        Total("Total income", s.totalIncome)
        ModuleHeader("Expenses", modifier = Modifier.padding(top = 16.dp))
        if (s.expenses.isEmpty()) Text("No expenses in this period.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted)
        Lines(s.expenses)
        Total("Total expenses", s.totalExpenses)
        ModuleHeader(if (s.net.value >= 0) "Surplus" else "Deficit", modifier = Modifier.padding(top = 16.dp), action = { })
        Total("Income less expenses", s.net)
    }
}

@Composable
internal fun BalanceModule(b: BalanceSheet) {
    Module {
        ModuleHeader("Assets", subtitle = "As at ${b.asOf.format(Day)}")
        Lines(b.assets)
        Total("Total assets", b.totalAssets)
        ModuleHeader("Liabilities", modifier = Modifier.padding(top = 16.dp))
        if (b.liabilities.isEmpty()) Text("No liabilities.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted)
        Lines(b.liabilities)
        Total("Total liabilities", b.totalLiabilities)
        ModuleHeader("Equity", modifier = Modifier.padding(top = 16.dp))
        Lines(b.equity)
        KeyValueRow("Earnings not yet closed to equity", b.currentEarnings.formatInr(showPaise = true))
        Total("Total equity", b.totalEquity)
        Row(Modifier.padding(top = 12.dp)) {
            if (b.balanced) StatusBadge("✓ Assets equal liabilities plus equity", Tone.Positive) else StatusBadge("Out of balance", Tone.Negative)
        }
    }
}

@Composable
internal fun TrialModule(t: TrialBalance) {
    Module {
        ModuleHeader("Trial balance", subtitle = "As at ${t.asOf.format(Day)}")
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Account", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.weight(1f))
            Text("Debit", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.width(96.dp))
            Text("Credit", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.width(96.dp))
        }
        t.rows.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.ink, modifier = Modifier.weight(1f))
                Text(if (r.debit.value > 0) r.debit.formatInr() else "", style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink, modifier = Modifier.width(96.dp), softWrap = false)
                Text(if (r.credit.value > 0) r.credit.formatInr() else "", style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink, modifier = Modifier.width(96.dp), softWrap = false)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Totals", style = MoneyTheme.type.bodyLg, color = MoneyTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(t.totalDebit.formatInr(), style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink, modifier = Modifier.width(96.dp), softWrap = false)
            Text(t.totalCredit.formatInr(), style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink, modifier = Modifier.width(96.dp), softWrap = false)
        }
        Row(Modifier.padding(top = 12.dp)) {
            if (t.balanced) StatusBadge("✓ Debits equal credits", Tone.Positive) else StatusBadge("Out of balance", Tone.Negative)
        }
    }
}

@Composable
internal fun CashFlowModule(c: CashFlowStatement) {
    Module {
        ModuleHeader("Cash flow", subtitle = "Bank and cash accounts")
        KeyValueRow("Cash at the start", c.opening.formatInr(showPaise = true))
        CashCategory.entries.forEach { cat ->
            val lines = c.sections[cat].orEmpty()
            if (lines.isNotEmpty()) {
                Text(cat.title(), style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                lines.forEach { KeyValueRow(it.name, it.amount.formatInr(showPaise = true, signed = true)) }
                KeyValueRow("Net ${cat.name.lowercase()}", c.total(cat).formatInr(showPaise = true, signed = true), divider = false)
            }
        }
        if (c.sections.isEmpty()) Text("No money moved through your cash accounts in this period.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
        Text("Net change", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
        KeyValueRow("Net change in cash", c.netChange.formatInr(showPaise = true, signed = true))
        KeyValueRow("Cash at the end", c.closing.formatInr(showPaise = true), divider = false)
        Text("Operating is income and spending, investing is money into or out of investments and deposits, financing is loans and cards. Moves between your own cash accounts net to nothing.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 10.dp))
    }
}

private fun CashCategory.title() = when (this) {
    CashCategory.Operating -> "Operating"
    CashCategory.Investing -> "Investing"
    CashCategory.Financing -> "Financing"
    CashCategory.Other -> "Other"
}

private fun percent(bps: Int) = "${bps / 100}.${(bps % 100 / 10)}%"

@Composable
private fun ShareRows(title: String, shares: List<Share>, empty: String) {
    Text(title, style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    if (shares.isEmpty()) Text(empty, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted)
    shares.forEach { s ->
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.name.substringAfterLast(':'), style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.ink, modifier = Modifier.weight(1f))
                Text("${s.amount.formatInr()} · ${percent(s.shareBps)}", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, softWrap = false)
            }
            ProgressTrack(s.shareBps / 10_000f)
        }
    }
}

@Composable
internal fun StatsModule(categories: List<Share>, payees: List<Share>, methods: List<Share>, trend: List<MonthTrend>) {
    Module {
        ModuleHeader("Where the spending went")
        ShareRows("By category", categories, "No spending in this period.")
        ShareRows("By payee", payees, "No spending in this period.")
        ShareRows("By how it was paid", methods, "No spending in this period.")
        Text("Last ${trend.size} months", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        val top = trend.maxOfOrNull { it.expenses.value }?.coerceAtLeast(1L) ?: 1L
        trend.forEach { m ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(m.range.start.format(MonthShort), style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.ink)
                    Text("In ${m.income.formatInr()} · Out ${m.expenses.formatInr()}", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, softWrap = false)
                }
                ProgressTrack((m.expenses.value.toFloat() / top).coerceIn(0f, 1f))
            }
        }
    }
}

private val MonthShort = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

@Composable
internal fun ExportModule(onCsv: () -> Unit, onBeancount: () -> Unit, message: String?) {
    Module {
        ModuleHeader("Export", subtitle = "Choose where to save the file")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("This report as CSV", onCsv)
            Pill("Whole ledger as Beancount", onBeancount)
        }
        message?.let { Text(it, style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** The CSV text for whichever report is showing. */
internal fun csvFor(statement: Statement, ledger: LedgerSnapshot, range: DateRange, asOf: LocalDate, today: LocalDate, monthStartDay: Int): String = when (statement) {
    Statement.Income -> incomeStatementCsv(incomeStatement(ledger, range))
    Statement.Balance -> balanceSheetCsv(balanceSheet(ledger, asOf))
    Statement.CashFlow -> cashFlowCsv(cashFlow(ledger, range))
    Statement.Trial -> trialBalanceCsv(trialBalance(ledger, asOf))
    Statement.Stats -> sharesCsv("Category", categoryShares(ledger, range))
}
