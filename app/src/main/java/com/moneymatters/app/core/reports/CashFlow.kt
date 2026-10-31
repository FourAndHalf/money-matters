package com.moneymatters.app.core.reports

import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.LiquidityTier
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.ledger.effectiveTier
import com.moneymatters.app.core.money.Paise
import java.time.LocalDate

/** Where money came from or went, by what the other side of the entry was. */
enum class CashCategory { Operating, Investing, Financing, Other }

/**
 * Cash flow for [range], on the cash accounts (bank and cash, or any asset you've set to the Cash tier). Inflows are positive.
 * Opening plus the net change always equals closing, because every entry balances: whatever the cash leg moves, the other legs moved the opposite way.
 */
data class CashFlowStatement(
    val range: DateRange,
    val opening: Paise,
    val closing: Paise,
    val sections: Map<CashCategory, List<StatementLine>>,
) {
    fun total(category: CashCategory): Paise = Paise(sections[category].orEmpty().sumOf { it.amount.value })
    val netChange: Paise get() = Paise(CashCategory.entries.sumOf { total(it).value })
}

fun cashFlow(ledger: LedgerSnapshot, range: DateRange): CashFlowStatement {
    val cashIds = ledger.accounts.filter { effectiveTier(it) == LiquidityTier.Cash }.map { it.id }.toSet()
    val byId = ledger.accounts.associateBy { it.id }
    val booked = ledger.transactions.filter { it.status != TransactionStatus.Draft }
    val dates = booked.associate { it.id to LocalDate.parse(it.date) }
    val byTxn = ledger.postings.groupBy { it.transactionId }

    fun cashBalanceOn(day: LocalDate) =
        Paise(ledger.postings.filter { it.accountId in cashIds && dates[it.transactionId]?.let { d -> !d.isAfter(day) } == true }.sumOf { (it.debitPaise ?: 0L) - (it.creditPaise ?: 0L) })

    fun category(accountId: String): CashCategory {
        val a = byId[accountId] ?: return CashCategory.Other
        return when {
            a.type == AccountType.Income || a.type == AccountType.Expense -> CashCategory.Operating
            a.type == AccountType.Asset && (a.kind == AccountKind.Investment || a.kind == AccountKind.FixedDeposit) -> CashCategory.Investing
            a.type == AccountType.Liability -> CashCategory.Financing
            else -> CashCategory.Other
        }
    }

    val totals = HashMap<String, Long>()
    for (t in booked) {
        if (dates.getValue(t.id) !in range) continue
        val legs = byTxn[t.id].orEmpty()
        if (legs.none { it.accountId in cashIds }) continue
        // The cash an entry moves is the opposite of what its other legs net to, so each other leg carries its share.
        for (leg in legs) {
            if (leg.accountId in cashIds) continue
            totals.merge(leg.accountId, -((leg.debitPaise ?: 0L) - (leg.creditPaise ?: 0L)), Long::plus)
        }
    }
    val sections = totals.filterValues { it != 0L }.map { (id, amount) -> category(id) to StatementLine(id, byId[id]?.name ?: id, Paise(amount)) }
        .groupBy({ it.first }, { it.second }).mapValues { (_, lines) -> lines.sortedBy { it.name.lowercase() } }
    return CashFlowStatement(range, cashBalanceOn(range.start.minusDays(1)), cashBalanceOn(range.end), sections)
}
