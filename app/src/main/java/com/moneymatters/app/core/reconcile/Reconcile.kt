package com.moneymatters.app.core.reconcile

import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.IllegalLifecycleTransitionException
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * One booked entry waiting to be matched against a statement. [amount] is the entry's effect on what the bank
 * (or card issuer) would show: money in is positive for an asset, and an amount owed is positive for a liability.
 */
data class ReconcileLine(val transactionId: String, val date: LocalDate, val payee: String, val amount: Paise)

/** What reconciling an account starts from: the balance already confirmed, and the entries still to confirm. */
data class ReconcileView(
    val accountId: String,
    val accountName: String,
    val isLiability: Boolean,
    /** Everything already Reconciled, Locked or adjusted: the part of the balance a past statement confirmed. */
    val cleared: Paise,
    val lines: List<ReconcileLine>,
) {
    /** How far the statement is from the books once the ticked entries are counted: zero means they agree. */
    fun difference(statement: Paise, ticked: Set<String>): Paise =
        statement - (cleared + Paise(lines.filter { it.transactionId in ticked }.sumOf { it.amount.value }))
}

/** The account's reconcile view, or null if it isn't an asset or liability (income, expense and equity have no statement). */
fun reconcileView(ledger: LedgerSnapshot, accountId: String): ReconcileView? {
    val account = ledger.accounts.firstOrNull { it.id == accountId } ?: return null
    val liability = when (account.type) {
        AccountType.Asset -> false
        AccountType.Liability -> true
        else -> return null
    }
    fun natural(debit: Long, credit: Long) = Paise(if (liability) credit - debit else debit - credit)
    val byTransaction = ledger.postings.filter { it.accountId == accountId }.groupBy { it.transactionId }
    val net = byTransaction.mapValues { (_, legs) -> natural(legs.sumOf { it.debitPaise ?: 0L }, legs.sumOf { it.creditPaise ?: 0L }) }
    var cleared = Paise.Zero
    val lines = mutableListOf<ReconcileLine>()
    for (t in ledger.transactions) {
        val amount = net[t.id] ?: continue
        when (t.status) {
            TransactionStatus.Draft -> Unit // never counted
            TransactionStatus.Posted -> lines += ReconcileLine(t.id, LocalDate.parse(t.date), t.payee, amount)
            TransactionStatus.Reconciled, TransactionStatus.Locked, TransactionStatus.PriorPeriodAdjustment -> cleared += amount
        }
    }
    return ReconcileView(account.id, account.name, liability, cleared, lines.sortedWith(compareBy({ it.date }, { it.payee })))
}

/** The statement doesn't agree with the books and the ticked entries, so nothing was reconciled. */
class ReconcileMismatchException(val difference: Paise) : IllegalStateException("The balance is still ${difference.value} paise out")

const val UNACCOUNTED = "Expenses:Unaccounted"

/** Reconciliation (plan.html → P10): confirm entries against a statement balance, and resolve what doesn't match. */
class ReconcileRepository(private val ledger: LedgerRepository) {

    /**
     * Reconciles the ticked entries, but only if the books then agree with [statement] to the paisa. The check is
     * redone here against the live ledger, so a stale screen can't lock entries that no longer add up. Returns how many.
     */
    suspend fun finish(accountId: String, statement: Paise, ticked: Set<String>): Int = ledger.inTransaction {
        val view = requireNotNull(reconcileView(ledger.snapshot().first(), accountId)) { "This account has no statement to reconcile" }
        val known = view.lines.map { it.transactionId }.toSet()
        if (!known.containsAll(ticked)) throw IllegalLifecycleTransitionException("An entry you ticked is no longer waiting to be reconciled")
        val difference = view.difference(statement, ticked)
        if (difference != Paise.Zero) throw ReconcileMismatchException(difference)
        ticked.forEach { ledger.reconcile(it) }
        ticked.size
    }

    /**
     * Books [difference] (what the statement shows beyond the books, in the account's own terms) against
     * [UNACCOUNTED] with a note, so the books agree with the bank. The entry is Posted and shows up ready to tick.
     */
    suspend fun postDifference(accountId: String, difference: Paise, note: String, date: LocalDate): String {
        require(difference != Paise.Zero) { "There is no difference to post" }
        require(note.isNotBlank()) { "Say what the difference is" }
        val view = requireNotNull(reconcileView(ledger.snapshot().first(), accountId)) { "This account has no statement to reconcile" }
        val unaccounted = ledger.createAccountIfAbsent(UNACCOUNTED, AccountType.Expense)
        val amount = Paise(kotlin.math.abs(difference.value))
        // More on the statement than in the books raises the account's own balance: an asset gains (debit), a liability owes more (credit).
        val raisesBalance = difference.value > 0
        val accountDebited = if (view.isLiability) !raisesBalance else raisesBalance
        val legs = if (accountDebited) {
            listOf(PostingLine(accountId, debit = amount), PostingLine(unaccounted, credit = amount))
        } else {
            listOf(PostingLine(unaccounted, debit = amount), PostingLine(accountId, credit = amount))
        }
        return ledger.postTransaction(date, "Unaccounted difference", legs, memo = note.trim())
    }
}
