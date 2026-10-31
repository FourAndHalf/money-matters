package com.moneymatters.app.core.reports

import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import java.time.LocalDate

/** One slice of spending: [shareBps] is its part of the total in basis points (2,500 is 25%), rounded down. */
data class Share(val name: String, val amount: Paise, val shareBps: Int)

private fun shares(totals: Map<String, Long>): List<Share> {
    val positive = totals.filterValues { it > 0 }
    val sum = positive.values.sum()
    if (sum <= 0) return emptyList()
    return positive.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key.lowercase() })
        .map { Share(it.key, Paise(it.value), (it.value * 10_000 / sum).toInt()) }
}

/** Where the spending went, by expense category, biggest first. A refund can bring a category to nothing. */
fun categoryShares(ledger: LedgerSnapshot, range: DateRange): List<Share> =
    shares(incomeStatement(ledger, range).expenses.associate { it.name to it.amount.value })

/** Each entry's spending: the net debit on expense accounts, which is what refunds reduce. */
private fun spendByTxn(ledger: LedgerSnapshot, range: DateRange): List<Pair<com.moneymatters.app.core.ledger.TransactionEntity, Long>> {
    val expenseIds = ledger.accounts.filter { it.type == AccountType.Expense }.map { it.id }.toSet()
    val byTxn = ledger.postings.groupBy { it.transactionId }
    return ledger.transactions.filter { it.status != TransactionStatus.Draft && LocalDate.parse(it.date) in range }
        .map { t -> t to byTxn[t.id].orEmpty().filter { it.accountId in expenseIds }.sumOf { (it.debitPaise ?: 0L) - (it.creditPaise ?: 0L) } }
        .filter { it.second != 0L }
}

/** Spending by payee, biggest first, with the same name in different capitals counted together. */
fun payeeShares(ledger: LedgerSnapshot, range: DateRange, limit: Int = 10): List<Share> {
    val grouped = HashMap<String, Long>()
    val display = HashMap<String, String>()
    for ((t, spend) in spendByTxn(ledger, range)) {
        val key = t.payee.trim().lowercase()
        display.putIfAbsent(key, t.payee.trim())
        grouped.merge(key, spend, Long::plus)
    }
    return shares(grouped.mapKeys { display.getValue(it.key) }).take(limit)
}

fun PaymentMethod?.label() = when (this) {
    PaymentMethod.Upi -> "UPI"
    PaymentMethod.Card -> "Card"
    PaymentMethod.NetBanking -> "Net banking"
    PaymentMethod.Cash -> "Cash"
    null -> "Not recorded"
}

/** Spending by how it was paid. */
fun paymentMethodShares(ledger: LedgerSnapshot, range: DateRange): List<Share> {
    val grouped = HashMap<String, Long>()
    for ((t, spend) in spendByTxn(ledger, range)) grouped.merge(t.paymentMethod.label(), spend, Long::plus)
    return shares(grouped)
}

/** Income and spending for one month. */
data class MonthTrend(val range: DateRange, val income: Paise, val expenses: Paise)

/** The last [months] months up to and including the one holding [today], oldest first. */
fun monthlyTrend(ledger: LedgerSnapshot, today: LocalDate, monthStartDay: Int, months: Int = 6): List<MonthTrend> {
    var range = ReportPeriod.Month.rangeContaining(today, monthStartDay)
    val out = ArrayDeque<MonthTrend>()
    repeat(months) {
        val s = incomeStatement(ledger, range)
        out.addFirst(MonthTrend(range, s.totalIncome, s.totalExpenses))
        range = ReportPeriod.Month.rangeContaining(range.start.minusDays(1), monthStartDay)
    }
    return out.toList()
}
