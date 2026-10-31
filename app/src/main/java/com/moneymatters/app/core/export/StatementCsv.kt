package com.moneymatters.app.core.export

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.reports.BalanceSheet
import com.moneymatters.app.core.reports.CashCategory
import com.moneymatters.app.core.reports.CashFlowStatement
import com.moneymatters.app.core.reports.IncomeStatement
import com.moneymatters.app.core.reports.Share
import com.moneymatters.app.core.reports.StatementLine
import com.moneymatters.app.core.reports.TrialBalance
import java.util.Locale

/** A CSV field, quoted when it needs to be, with a leading = + - or @ defused so a spreadsheet won't read it as a formula. */
fun csvField(value: String): String {
    val safe = if (value.firstOrNull() in setOf('=', '+', '@')) "'$value" else value
    return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}

/** Rupees as a plain number with two decimals and no grouping, which every spreadsheet reads. */
fun plainAmount(p: Paise): String {
    val a = kotlin.math.abs(p.value)
    return String.format(Locale.ROOT, "%s%d.%02d", if (p.value < 0) "-" else "", a / 100, a % 100)
}

private class Csv {
    private val sb = StringBuilder()
    fun row(vararg cells: String) { sb.append(cells.joinToString(",") { csvField(it) }).append("\n") }
    fun lines(section: String, lines: List<StatementLine>) = lines.forEach { row(section, it.name, plainAmount(it.amount)) }
    override fun toString() = sb.toString()
}

fun incomeStatementCsv(s: IncomeStatement) = Csv().apply {
    row("Section", "Account", "Amount (INR)")
    lines("Income", s.income); row("Total", "Total income", plainAmount(s.totalIncome))
    lines("Expenses", s.expenses); row("Total", "Total expenses", plainAmount(s.totalExpenses))
    row("Result", "Income less expenses", plainAmount(s.net))
}.toString()

fun balanceSheetCsv(b: BalanceSheet) = Csv().apply {
    row("Section", "Account", "Amount (INR)")
    lines("Assets", b.assets); row("Total", "Total assets", plainAmount(b.totalAssets))
    lines("Liabilities", b.liabilities); row("Total", "Total liabilities", plainAmount(b.totalLiabilities))
    lines("Equity", b.equity); row("Equity", "Earnings not yet closed to equity", plainAmount(b.currentEarnings)); row("Total", "Total equity", plainAmount(b.totalEquity))
}.toString()

fun trialBalanceCsv(t: TrialBalance) = Csv().apply {
    row("Account", "Debit (INR)", "Credit (INR)")
    t.rows.forEach { row(it.name, if (it.debit.value > 0) plainAmount(it.debit) else "", if (it.credit.value > 0) plainAmount(it.credit) else "") }
    row("Totals", plainAmount(t.totalDebit), plainAmount(t.totalCredit))
}.toString()

fun cashFlowCsv(c: CashFlowStatement) = Csv().apply {
    row("Section", "Account", "Amount (INR)")
    row("Opening", "Cash at the start", plainAmount(c.opening))
    CashCategory.entries.forEach { cat -> c.sections[cat].orEmpty().let { ls -> if (ls.isNotEmpty()) { lines(cat.name, ls); row("Total", "Net ${cat.name.lowercase()}", plainAmount(c.total(cat))) } } }
    row("Net", "Net change", plainAmount(c.netChange))
    row("Closing", "Cash at the end", plainAmount(c.closing))
}.toString()

fun sharesCsv(title: String, shares: List<Share>) = Csv().apply {
    row(title, "Amount (INR)", "Share")
    shares.forEach { row(it.name, plainAmount(it.amount), "${it.shareBps / 100}.${(it.shareBps % 100).toString().padStart(2, '0')}%") }
}.toString()
