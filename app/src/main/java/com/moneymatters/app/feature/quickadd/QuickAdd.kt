package com.moneymatters.app.feature.quickadd

import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.PlanKind
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.parseRupees
import java.time.LocalDate

/**
 * What a Quick Add entry moves money between (plan.html → P04's "Types"). Every kind reduces to
 * the same two-leg rule confirmed in `OnboardingSaver`: debit increases Asset/Expense, credit
 * increases Liability/Income — so "to" is always the debited side, "from" the credited side.
 */
enum class EntryKind(val label: String, val toLabel: String, val fromLabel: String) {
    Expense("Expense", "Category", "Paid from"),
    Income("Income", "Received into", "Source"),
    Transfer("Transfer", "To account", "From account"),
    CardPayment("Card payment", "Card", "Paid from"),
    InvestBuy("Investment buy", "Holding", "Paid from"),
    InvestSell("Investment sell", "Received into", "Sold from"),
    Dividend("Dividend", "Received into", "Dividend source"),
}

/** Which accounts the "to" (debited) side may pick from, archived accounts always excluded. */
fun EntryKind.matchesTo(account: AccountEntity): Boolean = when (this) {
    EntryKind.Expense -> account.type == AccountType.Expense
    EntryKind.Income, EntryKind.InvestSell, EntryKind.Dividend -> account.type == AccountType.Asset
    EntryKind.Transfer -> account.type == AccountType.Asset || account.type == AccountType.Liability
    EntryKind.CardPayment -> account.type == AccountType.Liability && account.kind == AccountKind.CreditCard
    EntryKind.InvestBuy -> account.type == AccountType.Asset && account.kind == AccountKind.Investment
}

/** Which accounts the "from" (credited) side may pick from, archived accounts always excluded. */
fun EntryKind.matchesFrom(account: AccountEntity): Boolean = when (this) {
    EntryKind.Expense, EntryKind.CardPayment, EntryKind.InvestBuy, EntryKind.Transfer ->
        account.type == AccountType.Asset || account.type == AccountType.Liability
    EntryKind.Income, EntryKind.Dividend -> account.type == AccountType.Income
    EntryKind.InvestSell -> account.type == AccountType.Asset && account.kind == AccountKind.Investment
}

/**
 * One row of the split/multi-posting editor (plan.html → "Split / multi-posting editor").
 * [debitText]/[creditText] are raw typed rupee text, parsed only when building postings —
 * the same pattern `NewAccountDraft.openingBalance` uses, so a field can show what was typed
 * even mid-edit without round-tripping through [Paise].
 */
data class SplitLine(val accountId: String? = null, val debitText: String = "", val creditText: String = "")

/**
 * The Quick Add form. A non-empty [splits] list replaces the simple two-leg [toAccountId]/
 * [fromAccountId] pair, for salary splits, EMIs and split bills.
 */
data class QuickAddForm(
    val kind: EntryKind = EntryKind.Expense,
    val date: LocalDate,
    val payee: String = "",
    val amount: Paise = Paise.Zero,
    val toAccountId: String? = null,
    val fromAccountId: String? = null,
    val paymentMethod: PaymentMethod? = null,
    val decision: DecisionKind? = null,
    val plan: PlanKind? = null,
    val note: String = "",
    val splits: List<SplitLine> = emptyList(),
) {
    companion object {
        fun empty(today: LocalDate) = QuickAddForm(date = today)
    }
}

/** The two-leg postings for [form], or the split editor's rows when it has any. Never validates balance. */
fun postingsFor(form: QuickAddForm): List<PostingLine> {
    if (form.splits.isNotEmpty()) {
        return form.splits.mapNotNull { line ->
            val accountId = line.accountId ?: return@mapNotNull null
            val debit = parseRupees(line.debitText)?.takeIf { it.value > 0 }
            val credit = parseRupees(line.creditText)?.takeIf { it.value > 0 }
            if (debit != null && credit == null) PostingLine(accountId, debit = debit)
            else if (credit != null && debit == null) PostingLine(accountId, credit = credit)
            else null
        }
    }
    val to = form.toAccountId ?: return emptyList()
    val from = form.fromAccountId ?: return emptyList()
    if (form.amount.value <= 0) return emptyList()
    return listOf(PostingLine(to, debit = form.amount), PostingLine(from, credit = form.amount))
}

/** Live "✓ Balanced" check: at least two legs, every debit-paise equal to every credit-paise, and non-zero. */
fun isBalanced(postings: List<PostingLine>): Boolean {
    if (postings.size < 2) return false
    val debits = postings.fold(Paise.Zero) { acc, p -> acc + (p.debit ?: Paise.Zero) }
    val credits = postings.fold(Paise.Zero) { acc, p -> acc + (p.credit ?: Paise.Zero) }
    return debits == credits && debits.value > 0
}

/**
 * One payee's most recent entry, used both as "payee memory" (typing a known payee prefills the
 * rest of the form) and as a template (the same list, ranked by [useCount], shown before typing).
 */
data class PayeeMemory(
    val payee: String,
    val toAccountId: String?,
    val fromAccountId: String?,
    val paymentMethod: PaymentMethod?,
    val decision: DecisionKind?,
    val plan: PlanKind?,
    val useCount: Int,
)

/**
 * One [PayeeMemory] per distinct payee (case-insensitive), from that payee's most recent simple
 * (two-leg) transaction, ranked most-frequent first and filtered by [query] when it isn't blank.
 */
fun payeeSuggestions(snapshot: LedgerSnapshot, query: String = ""): List<PayeeMemory> {
    val postingsByTxn = snapshot.postings.groupBy { it.transactionId }
    val byPayee = snapshot.transactions.groupBy { it.payee.trim().lowercase() }
    val memories = byPayee.values.mapNotNull { transactions ->
        val latest = transactions.maxByOrNull { it.date } ?: return@mapNotNull null
        val postings = postingsByTxn[latest.id].orEmpty()
        if (postings.size != 2) return@mapNotNull null
        val debitLeg = postings.firstOrNull { it.debitPaise != null } ?: return@mapNotNull null
        val creditLeg = postings.firstOrNull { it.creditPaise != null } ?: return@mapNotNull null
        PayeeMemory(
            payee = latest.payee,
            toAccountId = debitLeg.accountId,
            fromAccountId = creditLeg.accountId,
            paymentMethod = latest.paymentMethod,
            decision = latest.decision,
            plan = latest.plan,
            useCount = transactions.size,
        )
    }
    val filtered = if (query.isBlank()) memories else memories.filter { it.payee.contains(query, ignoreCase = true) }
    return filtered.sortedWith(compareByDescending<PayeeMemory> { it.useCount }.thenBy { it.payee.lowercase() })
}
