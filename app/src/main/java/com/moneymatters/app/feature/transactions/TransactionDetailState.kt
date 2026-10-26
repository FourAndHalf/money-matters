package com.moneymatters.app.feature.transactions

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.money.Paise

/** One line of audit history. [before]/[after] are set only when a field's value actually changed. */
@Immutable
data class AuditEntry(val whenLabel: String, val summary: String, val before: String? = null, val after: String? = null)

/** Everything the Transaction detail page (P07) renders for one transaction. */
@Immutable
data class TransactionDetail(
    val id: String,
    val payee: String,
    val amount: Paise,
    val dateLabel: String,
    val timeLabel: String,
    val accountName: String,
    val category: String,
    val status: TxnStatus,
    val postings: List<Posting>,
    val decision: DecisionKind? = null,
    val plan: PlanKind? = null,
    val reason: String? = null,
    val audit: List<AuditEntry> = emptyList(),
    val isTransfer: Boolean = false,
) {
    /** Dr total = Cr total for every entry in this sample set; a real ledger enforces it at write time. */
    val isBalanced: Boolean get() {
        val debits = postings.fold(Paise.Zero) { acc, p -> acc + (p.debit ?: Paise.Zero) }
        val credits = postings.fold(Paise.Zero) { acc, p -> acc + (p.credit ?: Paise.Zero) }
        return debits == credits
    }
}

/** What a transaction's primary action does, chosen from its status (plan.html → P07). */
enum class TxnAction { Edit, Reverse, PriorPeriodAdjustment }

fun TxnStatus.primaryAction(): TxnAction = when (this) {
    TxnStatus.Draft, TxnStatus.Posted -> TxnAction.Edit
    TxnStatus.Reconciled -> TxnAction.Reverse
    // A closed month is immutable; correcting it takes a new prior-period entry, not an edit or a reversal.
    TxnStatus.Locked, TxnStatus.PriorPeriodAdjustment -> TxnAction.PriorPeriodAdjustment
}
