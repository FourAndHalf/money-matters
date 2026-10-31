package com.moneymatters.app.core.reports

import com.moneymatters.app.core.budget.BudgetPeriod
import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.budget.rangeContaining
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import java.time.LocalDate

/** One account's amount in a statement, in the account's own terms: a positive income is earned, a positive asset is owned. */
data class StatementLine(val accountId: String, val name: String, val amount: Paise)

data class IncomeStatement(val range: DateRange, val income: List<StatementLine>, val expenses: List<StatementLine>) {
    val totalIncome: Paise get() = Paise(income.sumOf { it.amount.value })
    val totalExpenses: Paise get() = Paise(expenses.sumOf { it.amount.value })
    val net: Paise get() = totalIncome - totalExpenses
}

/**
 * [currentEarnings] is income less expenses to date that year-end closing hasn't yet moved into equity; it is what
 * makes assets equal liabilities plus equity at any date.
 */
data class BalanceSheet(
    val asOf: LocalDate,
    val assets: List<StatementLine>,
    val liabilities: List<StatementLine>,
    val equity: List<StatementLine>,
    val currentEarnings: Paise,
) {
    val totalAssets: Paise get() = Paise(assets.sumOf { it.amount.value })
    val totalLiabilities: Paise get() = Paise(liabilities.sumOf { it.amount.value })
    val totalEquity: Paise get() = Paise(equity.sumOf { it.amount.value }) + currentEarnings
    val balanced: Boolean get() = totalAssets == totalLiabilities + totalEquity
}

data class TrialRow(val accountId: String, val name: String, val type: AccountType, val debit: Paise, val credit: Paise)

data class TrialBalance(val asOf: LocalDate, val rows: List<TrialRow>) {
    val totalDebit: Paise get() = Paise(rows.sumOf { it.debit.value })
    val totalCredit: Paise get() = Paise(rows.sumOf { it.credit.value })
    val balanced: Boolean get() = totalDebit == totalCredit
}

/** Debits and credits per account over a date window, from booked entries only: drafts are never counted. */
private fun totals(ledger: LedgerSnapshot, from: LocalDate?, to: LocalDate): Map<String, Pair<Long, Long>> {
    val booked = ledger.transactions
        .filter { it.status != TransactionStatus.Draft }
        .filter { val d = LocalDate.parse(it.date); !d.isAfter(to) && (from == null || !d.isBefore(from)) }
        .map { it.id }.toSet()
    val out = HashMap<String, Pair<Long, Long>>()
    for (p in ledger.postings) {
        if (p.transactionId !in booked) continue
        val (d, c) = out[p.accountId] ?: (0L to 0L)
        out[p.accountId] = (d + (p.debitPaise ?: 0L)) to (c + (p.creditPaise ?: 0L))
    }
    return out
}

private fun lines(ledger: LedgerSnapshot, type: AccountType, t: Map<String, Pair<Long, Long>>, normalDebit: Boolean): List<StatementLine> =
    ledger.accounts.filter { it.type == type }.mapNotNull { a ->
        val (d, c) = t[a.id] ?: return@mapNotNull null
        val amount = if (normalDebit) d - c else c - d
        if (amount == 0L) null else StatementLine(a.id, a.name, Paise(amount))
    }.sortedBy { it.name.lowercase() }

fun incomeStatement(ledger: LedgerSnapshot, range: DateRange): IncomeStatement {
    val t = totals(ledger, range.start, range.end)
    return IncomeStatement(range, lines(ledger, AccountType.Income, t, normalDebit = false), lines(ledger, AccountType.Expense, t, normalDebit = true))
}

fun balanceSheet(ledger: LedgerSnapshot, asOf: LocalDate): BalanceSheet {
    val t = totals(ledger, null, asOf)
    val income = lines(ledger, AccountType.Income, t, normalDebit = false).sumOf { it.amount.value }
    val expenses = lines(ledger, AccountType.Expense, t, normalDebit = true).sumOf { it.amount.value }
    return BalanceSheet(
        asOf, lines(ledger, AccountType.Asset, t, true), lines(ledger, AccountType.Liability, t, false),
        lines(ledger, AccountType.Equity, t, false), Paise(income - expenses),
    )
}

/** Each account's net balance on its natural side, so every row carries a debit or a credit and the two totals must agree. */
fun trialBalance(ledger: LedgerSnapshot, asOf: LocalDate): TrialBalance {
    val t = totals(ledger, null, asOf)
    val rows = ledger.accounts.mapNotNull { a ->
        val (d, c) = t[a.id] ?: return@mapNotNull null
        val net = d - c
        if (net == 0L) null else TrialRow(a.id, a.name, a.type, Paise(if (net > 0) net else 0), Paise(if (net < 0) -net else 0))
    }.sortedWith(compareBy({ it.type.ordinal }, { it.name.lowercase() }))
    return TrialBalance(asOf, rows)
}

/** How a report period is chosen: a month (following your month start day) or the April–March tax year. */
enum class ReportPeriod { Month, TaxYear }

fun ReportPeriod.rangeContaining(date: LocalDate, monthStartDay: Int): DateRange =
    when (this) {
        ReportPeriod.Month -> BudgetPeriod.Monthly.rangeContaining(date, monthStartDay)
        ReportPeriod.TaxYear -> BudgetPeriod.Annual.rangeContaining(date, monthStartDay)
    }
