package com.moneymatters.app.feature.transactions

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionTags
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.parseRupees
import com.moneymatters.app.feature.books.displayName
import java.time.DateTimeException
import java.time.LocalDate

/**
 * An entry being edited in place (Draft or Posted only; the ledger refuses the rest). The date, payee and note can
 * always change. The amount can too when the entry is one amount moving between two accounts, which moves both
 * legs together so it stays balanced; a split or investment entry keeps its postings as they are.
 */
@Immutable
data class EditEntryForm(
    val transactionId: String,
    val dateText: String,
    val payee: String,
    val note: String,
    val amountText: String,
    /** Each leg as "Dr State Bank of India", shown read-only. */
    val legs: List<String>,
    val amountEditable: Boolean,
    internal val original: List<PostingLine>,
    internal val tags: TransactionTags,
) {
    val date: LocalDate? get() = try { LocalDate.parse(dateText.trim()) } catch (_: DateTimeException) { null }
    val amount: Paise? get() = parseRupees(amountText)?.takeIf { it.value > 0 }

    val canSave: Boolean get() = payee.isNotBlank() && date != null && (!amountEditable || amount != null)

    /** The postings to write: the originals, or both legs moved to the typed amount. */
    fun postings(): List<PostingLine> {
        val to = amount?.takeIf { amountEditable } ?: return original
        return original.map { if (it.debit != null) it.copy(debit = to) else it.copy(credit = to) }
    }
}

/** Plain rupees for a text field: `22658` or `22658.50`, no grouping, no symbol. */
internal fun Paise.plainRupees(): String {
    val whole = value / 100
    val paise = value % 100
    return if (paise == 0L) whole.toString() else "$whole.${paise.toString().padStart(2, '0')}".trimEnd('0')
}

/** Builds the edit form for [transactionId] from the ledger, or null when it isn't there. */
fun editFormFor(snapshot: LedgerSnapshot, transactionId: String): EditEntryForm? {
    val txn = snapshot.transactions.firstOrNull { it.id == transactionId } ?: return null
    val names = snapshot.accounts.associate { it.id to it.displayName() }
    val postings = snapshot.postings.filter { it.transactionId == transactionId }.sortedBy { it.id }
    val lines = postings.map {
        PostingLine(
            accountId = it.accountId,
            debit = it.debitPaise?.let(::Paise), credit = it.creditPaise?.let(::Paise),
            units = it.units?.let(::Units), unitPrice = it.unitPrice?.let(::UnitPrice),
        )
    }
    val simple = lines.size == 2 && lines.all { it.units == null } &&
        lines.count { it.debit != null } == 1 && lines.count { it.credit != null } == 1 &&
        lines.first { it.debit != null }.debit == lines.first { it.credit != null }.credit
    return EditEntryForm(
        transactionId = txn.id,
        dateText = txn.date,
        payee = txn.payee,
        note = txn.memo.orEmpty(),
        amountText = if (simple) (lines[0].debit ?: lines[0].credit)!!.plainRupees() else "",
        legs = lines.map { (if (it.debit != null) "Dr " else "Cr ") + names[it.accountId].orEmpty() },
        amountEditable = simple,
        original = lines,
        tags = TransactionTags(txn.decision, txn.plan, txn.paymentMethod),
    )
}
