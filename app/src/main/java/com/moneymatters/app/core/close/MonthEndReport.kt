package com.moneymatters.app.core.close

import com.moneymatters.app.core.budget.BudgetEntity
import com.moneymatters.app.core.budget.BudgetPeriod
import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.budget.spentIn
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PlanKind
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.reports.balanceSheet
import com.moneymatters.app.core.reports.incomeStatement
import java.time.LocalDate

/** An expense category compared with last month and with the average of the three months before it. */
data class CategoryChange(val name: String, val current: Paise, val previous: Paise, val average: Paise) {
    val change: Paise get() = current - previous
}

data class BudgetResult(val category: String, val budget: Paise, val spent: Paise) {
    val isOver: Boolean get() = spent.value > budget.value
}

data class EntryCounts(val posted: Int, val reconciled: Int, val locked: Int, val drafts: Int, val adjustments: Int)

/**
 * The month-end report (plan.html → P17, P18) for [period]. Rates are in basis points of income (1,250 is 12.5%) so no float is
 * ever stored, and are null when there was no income to measure against.
 */
data class MonthEndReport(
    val period: Period,
    val income: Paise,
    val expenses: Paise,
    val savingsRateBps: Int?,
    val netInvested: Paise,
    val investmentRateBps: Int?,
    val previousExpenses: Paise,
    /** Average monthly expenses over the three months before, over only those months that had any. Zero if none did. */
    val averageExpenses: Paise,
    val movers: List<CategoryChange>,
    val needs: Paise,
    val wants: Paise,
    val investing: Paise,
    val untagged: Paise,
    val planned: Paise,
    val unplanned: Paise,
    val budgets: List<BudgetResult>,
    val entries: EntryCounts,
    val netWorthStart: Paise,
    val netWorthEnd: Paise,
) {
    val surplus: Paise get() = income - expenses
}

private fun rangeOf(p: Period) = DateRange(p.start, p.end)

private fun bps(part: Paise, whole: Paise): Int? = if (whole.value > 0) (part.value * 10_000 / whole.value).toInt() else null

/** Net money moved into investments and deposits in [period]: purchases and deposits added, sales taken off, interest and dividends left out. */
private fun netInvested(ledger: LedgerSnapshot, period: Period): Paise {
    val investIds = ledger.accounts.filter { it.kind == AccountKind.Investment || it.kind == AccountKind.FixedDeposit }.map { it.id }.toSet()
    val incomeIds = ledger.accounts.filter { it.type == AccountType.Income }.map { it.id }.toSet()
    val byTxn = ledger.postings.groupBy { it.transactionId }
    var total = 0L
    for (t in ledger.transactions) {
        if (t.status == TransactionStatus.Draft || LocalDate.parse(t.date) !in period) continue
        val legs = byTxn[t.id].orEmpty()
        val net = legs.filter { it.accountId in investIds }.sumOf { (it.debitPaise ?: 0L) - (it.creditPaise ?: 0L) }
        if (net == 0L) continue
        // Interest accrued or a dividend reinvested adds to an investment but is income, not money put in.
        if (net > 0 && legs.any { it.accountId in incomeIds }) continue
        total += net
    }
    return Paise(total)
}

fun monthEndReport(ledger: LedgerSnapshot, period: Period, monthStartDay: Int, budgets: List<BudgetEntity> = emptyList()): MonthEndReport {
    val now = incomeStatement(ledger, rangeOf(period))
    val previousPeriod = periodContaining(period.start.minusDays(1), monthStartDay)
    val before = (1..3).runningFold(previousPeriod) { p, _ -> periodContaining(p.start.minusDays(1), monthStartDay) }.take(3)
    val last = incomeStatement(ledger, rangeOf(previousPeriod))
    val earlier = before.map { incomeStatement(ledger, rangeOf(it)) }.filter { it.totalExpenses.value > 0 }
    val average = if (earlier.isEmpty()) Paise.Zero else Paise(earlier.sumOf { it.totalExpenses.value } / earlier.size)

    val names = (now.expenses + last.expenses + earlier.flatMap { it.expenses }).associate { it.accountId to it.name }
    val movers = names.map { (id, name) ->
        CategoryChange(
            name,
            now.expenses.firstOrNull { it.accountId == id }?.amount ?: Paise.Zero,
            last.expenses.firstOrNull { it.accountId == id }?.amount ?: Paise.Zero,
            if (earlier.isEmpty()) Paise.Zero else Paise(earlier.sumOf { s -> s.expenses.firstOrNull { it.accountId == id }?.amount?.value ?: 0L } / earlier.size),
        )
    }.filter { it.change.value != 0L }.sortedByDescending { kotlin.math.abs(it.change.value) }.take(5)

    // Expenses by how each entry was tagged: every expense-account leg of an entry takes that entry's tag.
    val expenseIds = ledger.accounts.filter { it.type == AccountType.Expense }.map { it.id }.toSet()
    val byTxn = ledger.postings.groupBy { it.transactionId }
    val inPeriod = ledger.transactions.filter { it.status != TransactionStatus.Draft && LocalDate.parse(it.date) in period }
    fun spendOf(id: String) = byTxn[id].orEmpty().filter { it.accountId in expenseIds }.sumOf { (it.debitPaise ?: 0L) - (it.creditPaise ?: 0L) }
    fun sumWhere(match: (com.moneymatters.app.core.ledger.TransactionEntity) -> Boolean) = Paise(inPeriod.filter(match).sumOf { spendOf(it.id) })

    val invested = netInvested(ledger, period)
    val allTxns = ledger.transactions.filter { LocalDate.parse(it.date) in period }
    return MonthEndReport(
        period = period, income = now.totalIncome, expenses = now.totalExpenses,
        savingsRateBps = bps(now.net, now.totalIncome), netInvested = invested, investmentRateBps = bps(invested, now.totalIncome),
        previousExpenses = last.totalExpenses, averageExpenses = average, movers = movers,
        needs = sumWhere { it.decision == DecisionKind.Need }, wants = sumWhere { it.decision == DecisionKind.Want },
        investing = sumWhere { it.decision == DecisionKind.Investment }, untagged = sumWhere { it.decision == null },
        planned = sumWhere { it.plan == PlanKind.Planned }, unplanned = sumWhere { it.plan == PlanKind.Unplanned },
        budgets = budgets.filter { it.period == BudgetPeriod.Monthly }.mapNotNull { b ->
            val account = ledger.accounts.firstOrNull { it.id == b.accountId } ?: return@mapNotNull null
            BudgetResult(account.name.substringAfterLast(':'), Paise(b.amountPaise), spentIn(b.accountId, ledger.accounts, ledger.transactions, ledger.postings, rangeOf(period)))
        }.sortedByDescending { it.spent.value - it.budget.value },
        entries = EntryCounts(
            allTxns.count { it.status == TransactionStatus.Posted }, allTxns.count { it.status == TransactionStatus.Reconciled },
            allTxns.count { it.status == TransactionStatus.Locked }, allTxns.count { it.status == TransactionStatus.Draft },
            allTxns.count { it.status == TransactionStatus.PriorPeriodAdjustment },
        ),
        netWorthStart = balanceSheet(ledger, period.start.minusDays(1)).let { it.totalAssets - it.totalLiabilities },
        netWorthEnd = balanceSheet(ledger, period.end).let { it.totalAssets - it.totalLiabilities },
    )
}

/** 1,250 bps as "12.5%"; negative rates keep their minus sign. */
fun formatBps(bps: Int): String {
    val sign = if (bps < 0) "−" else ""
    val a = kotlin.math.abs(bps)
    return "$sign${a / 100}.${(a % 100) / 10}%"
}
