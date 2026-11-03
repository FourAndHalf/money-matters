package com.moneymatters.app.feature.recurring

import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.PlanKind
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.parseRupees
import com.moneymatters.app.core.recurring.Frequency
import com.moneymatters.app.core.recurring.RecurringRuleEntity
import com.moneymatters.app.core.recurring.nextDue
import java.time.LocalDate

/** One rule as the list shows it. [needsAttention] is set when an account it uses has been archived or deleted, so the rule can't book. */
data class RecurringRow(
    val id: String,
    val name: String,
    val amount: Paise,
    val frequency: Frequency,
    val fromName: String,
    val toName: String,
    val next: LocalDate?,
    val autoPost: Boolean,
    val isActive: Boolean,
    val needsAttention: Boolean,
)

fun buildRows(rules: List<RecurringRuleEntity>, accounts: List<AccountEntity>): List<RecurringRow> {
    val byId = accounts.associateBy { it.id }
    return rules.map { r ->
        val from = byId[r.fromAccountId]
        val to = byId[r.toAccountId]
        RecurringRow(
            id = r.id, name = r.name, amount = Paise(r.amountPaise), frequency = r.frequency,
            fromName = from?.name ?: "Missing account", toName = to?.name ?: "Missing account",
            next = r.nextDue(), autoPost = r.autoPost, isActive = r.isActive,
            needsAttention = from == null || from.isArchived || to == null || to.isArchived,
        )
    }
}

/** Accounts the money can come from: where it sits or where it is earned. */
fun fromChoices(accounts: List<AccountEntity>): List<AccountEntity> =
    accounts.filter { !it.isArchived && it.type in setOf(AccountType.Asset, AccountType.Liability, AccountType.Income) }.sortedBy { it.name.lowercase() }

/** Accounts the money can go to: where it ends up or what it was spent on. */
fun toChoices(accounts: List<AccountEntity>): List<AccountEntity> =
    accounts.filter { !it.isArchived && it.type in setOf(AccountType.Asset, AccountType.Liability, AccountType.Expense) }.sortedBy { it.name.lowercase() }

/** The new/edit form. Tags the screen doesn't show are carried through so editing a rule never drops them. */
data class RecurringForm(
    val start: LocalDate,
    val editingId: String? = null,
    val name: String = "",
    val amountText: String = "",
    val fromId: String? = null,
    val toId: String? = null,
    val frequency: Frequency = Frequency.Monthly,
    val hasEnd: Boolean = false,
    val end: LocalDate? = null,
    val autoPost: Boolean = true,
    val isActive: Boolean = true,
    val decision: DecisionKind? = null,
    val plan: PlanKind? = null,
    val paymentMethod: PaymentMethod? = null,
    /** Set when the form was started from a posted entry, so the screen can say why the first date is not that entry's date. */
    val sourceDate: LocalDate? = null,
) {
    val amount: Paise? get() = parseRupees(amountText)?.takeIf { it.value > 0 }
    private val endOk: Boolean get() = !hasEnd || (end != null && !end.isBefore(start))
    val endError: String? get() = if (hasEnd && end != null && end.isBefore(start)) "The end can't be before the start" else null
    val canSave: Boolean get() = name.isNotBlank() && amount != null && fromId != null && toId != null && fromId != toId && endOk

    fun toRule(): RecurringRuleEntity = RecurringRuleEntity(
        id = editingId ?: "", name = name.trim(), fromAccountId = requireNotNull(fromId), toAccountId = requireNotNull(toId),
        amountPaise = requireNotNull(amount).value, frequency = frequency, startDate = start.toString(),
        endDate = if (hasEnd) end?.toString() else null, autoPost = autoPost,
        decision = decision, plan = plan, paymentMethod = paymentMethod, isActive = isActive,
    )

    companion object {
        fun from(rule: RecurringRuleEntity) = RecurringForm(
            start = LocalDate.parse(rule.startDate), editingId = rule.id, name = rule.name,
            amountText = Paise(rule.amountPaise).toPlainRupees(), fromId = rule.fromAccountId, toId = rule.toAccountId,
            frequency = rule.frequency, hasEnd = rule.endDate != null, end = rule.endDate?.let(LocalDate::parse),
            autoPost = rule.autoPost, isActive = rule.isActive, decision = rule.decision, plan = rule.plan, paymentMethod = rule.paymentMethod,
        )
    }
}

/** Rupees as the amount field takes them back: "25000" or "25000.50", no grouping. */
internal fun Paise.toPlainRupees(): String {
    val rupees = value / 100
    val paise = value % 100
    return if (paise == 0L) "$rupees" else "$rupees.${paise.toString().padStart(2, '0')}"
}

/**
 * A new-rule form filled from an entry already in the books: its payee, amount, both accounts and tags. Only an entry
 * of one amount moving between two accounts can repeat that way; a split or investment entry returns null.
 * The entry itself is already booked, so the first date is a month after it rather than on it (no double booking).
 */
fun recurringFormFor(snapshot: LedgerSnapshot, transactionId: String): RecurringForm? {
    val txn = snapshot.transactions.firstOrNull { it.id == transactionId } ?: return null
    val lines = snapshot.postings.filter { it.transactionId == transactionId }
    val debit = lines.singleOrNull { it.debitPaise != null } ?: return null
    val credit = lines.singleOrNull { it.creditPaise != null } ?: return null
    if (lines.size != 2 || lines.any { it.units != null } || debit.debitPaise != credit.creditPaise) return null
    val date = runCatching { LocalDate.parse(txn.date) }.getOrNull() ?: return null
    val from = snapshot.accounts.firstOrNull { it.id == credit.accountId }?.takeIf { it in fromChoices(snapshot.accounts) }
    val to = snapshot.accounts.firstOrNull { it.id == debit.accountId }?.takeIf { it in toChoices(snapshot.accounts) }
    return RecurringForm(
        start = date.plusMonths(1), name = txn.payee, amountText = Paise(requireNotNull(debit.debitPaise)).toPlainRupees(),
        fromId = from?.id, toId = to?.id, decision = txn.decision, plan = txn.plan, paymentMethod = txn.paymentMethod, sourceDate = date,
    )
}
