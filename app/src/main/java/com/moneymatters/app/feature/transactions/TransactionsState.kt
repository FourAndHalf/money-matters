package com.moneymatters.app.feature.transactions

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.money.Paise

/**
 * A transaction's lifecycle status (plan.html → P06). Reconciled becomes Locked once its
 * month closes; a correction to a closed month is posted as its own PriorPeriodAdjustment,
 * never an edit to the original.
 */
enum class TxnStatus { Draft, Posted, Reconciled, Locked, PriorPeriodAdjustment }

/** Stored on each ledger transaction; aliased so screens keep their short names. */
typealias DecisionKind = com.moneymatters.app.core.ledger.DecisionKind
typealias PlanKind = com.moneymatters.app.core.ledger.PlanKind

/** One leg of a journal entry. Exactly one of [debit]/[credit] is set. */
@Immutable
data class Posting(val account: String, val debit: Paise? = null, val credit: Paise? = null)

/** A row in the Transactions list (P06). [amount] is negative for money out, positive for money in. */
@Immutable
data class TransactionSummary(
    val id: String,
    val chip: String,
    val payee: String,
    val category: String,
    val accountName: String,
    val amount: Paise,
    val status: TxnStatus,
    val postings: List<Posting>,
    val decision: DecisionKind? = null,
    val plan: PlanKind? = null,
    val uncategorized: Boolean = false,
)

@Immutable
data class DayGroup(val dateLabel: String, val items: List<TransactionSummary>) {
    val total: Paise get() = items.fold(Paise.Zero) { acc, t -> acc + t.amount }
}

@Immutable
data class TransactionsState(val groups: List<DayGroup>, val accounts: List<String>) {
    val uncategorizedCount: Int get() = groups.sumOf { g -> g.items.count { it.uncategorized } }
}

@Immutable
data class TransactionFilters(val query: String = "", val status: TxnStatus? = null, val account: String? = null)

/** Applies [filters] to every group, dropping days left with nothing to show. Pure, no I/O. */
fun TransactionsState.filtered(filters: TransactionFilters): List<DayGroup> {
    fun matches(t: TransactionSummary): Boolean {
        if (filters.status != null && t.status != filters.status) return false
        if (filters.account != null && t.accountName != filters.account) return false
        val q = filters.query.trim()
        if (q.isNotEmpty() && !"${t.payee} ${t.category} ${t.accountName}".contains(q, ignoreCase = true)) return false
        return true
    }
    return groups.mapNotNull { group ->
        val kept = group.items.filter(::matches)
        if (kept.isEmpty()) null else group.copy(items = kept)
    }
}
