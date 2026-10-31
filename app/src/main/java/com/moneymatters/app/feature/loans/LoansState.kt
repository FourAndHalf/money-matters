package com.moneymatters.app.feature.loans

import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.loan.Instalment
import com.moneymatters.app.core.loan.LoanEntity
import com.moneymatters.app.core.loan.LoanPaymentEntity
import com.moneymatters.app.core.loan.livePayments
import com.moneymatters.app.core.loan.owedOn
import com.moneymatters.app.core.loan.nextDue
import com.moneymatters.app.core.loan.payoff
import com.moneymatters.app.core.loan.schedule
import com.moneymatters.app.core.loan.splitEmi
import com.moneymatters.app.core.loan.suggestEmi
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.parseRupees
import java.time.LocalDate

data class LoanRow(
    val accountId: String,
    val name: String,
    val rateBps: Int,
    val emi: Paise,
    val original: Paise,
    val outstanding: Paise,
    val principalRepaid: Paise,
    val interestPaid: Paise,
    val nextDue: LocalDate?,
    val isOverdue: Boolean,
    val instalmentsLeft: Int,
    val interestLeft: Paise,
    val lastDue: LocalDate?,
    val payFromName: String,
    val nextSplitPrincipal: Paise,
    val nextSplitInterest: Paise,
) {
    val isCleared: Boolean get() = outstanding.value <= 0
}

data class PaymentRow(val date: LocalDate, val principal: Paise, val interest: Paise, val isPrepayment: Boolean)

data class LoanDetail(val row: LoanRow, val schedule: List<Instalment>, val history: List<PaymentRow>)

data class LoansState(val rows: List<LoanRow>, val details: Map<String, LoanDetail>) {
    val totalOutstanding: Paise get() = Paise(rows.sumOf { it.outstanding.value })
    val totalEmi: Paise get() = Paise(rows.filter { !it.isCleared }.sumOf { it.emi.value })
}

fun buildLoans(loans: List<LoanEntity>, payments: List<LoanPaymentEntity>, ledger: LedgerSnapshot): LoansState {
    val names = ledger.accounts.associate { it.id to it.name }
    val existing = ledger.transactions.map { it.id }.toSet()
    val details = loans.mapNotNull { l ->
        val name = names[l.accountId] ?: return@mapNotNull null
        val live = livePayments(payments.filter { it.loanAccountId == l.accountId }, existing)
        val owed = owedOn(ledger, l.accountId)
        val due = nextDue(l, live)
        val emi = Paise(l.emiPaise)
        val plan = schedule(owed, emi, l.rateBps, due)
        val tail = payoff(plan)
        val next = splitEmi(owed, emi, l.rateBps)
        val row = LoanRow(
            accountId = l.accountId, name = name, rateBps = l.rateBps, emi = emi, original = Paise(l.originalPaise), outstanding = owed,
            principalRepaid = Paise((l.originalPaise - owed.value).coerceAtLeast(0)), interestPaid = Paise(live.sumOf { it.interestPaise }),
            nextDue = if (owed.value > 0) due else null, isOverdue = false, instalmentsLeft = tail.instalments, interestLeft = tail.interestRemaining,
            lastDue = tail.lastDue, payFromName = names[l.payFromAccountId] ?: "Missing account", nextSplitPrincipal = next.principal, nextSplitInterest = next.interest,
        )
        l.accountId to LoanDetail(
            row, plan,
            live.sortedByDescending { it.dueDate }.map { PaymentRow(LocalDate.parse(it.dueDate), Paise(it.principalPaise), Paise(it.interestPaise), it.isPrepayment) },
        )
    }.toMap()
    return LoansState(details.values.map { it.row }.sortedBy { it.name.lowercase() }, details)
}

/** Marks a loan overdue once its next EMI date has passed. Kept apart from [buildLoans] so that stays free of today's date. */
fun LoansState.asOf(today: LocalDate): LoansState {
    val rows = rows.map { it.copy(isOverdue = it.nextDue?.isBefore(today) == true) }
    return LoansState(rows, details.mapValues { (id, d) -> d.copy(row = rows.first { it.accountId == id }) })
}

/** Parses an annual rate typed as a percentage ("8.5") into basis points (850). Null for anything else. */
fun parseRateBps(text: String): Int? {
    val m = Regex("""(\d{1,3})(?:\.(\d{1,2}))?""").matchEntire(text.trim().removeSuffix("%").trim()) ?: return null
    val (whole, fraction) = m.destructured
    return (whole.toInt() * 100 + fraction.padEnd(2, '0').toInt()).takeIf { it <= 10_000 }
}

/** The new/edit form. [tenureText] only helps work out an EMI; it isn't stored. */
data class LoanForm(
    val firstDue: LocalDate,
    val editingAccountId: String? = null,
    val accountId: String? = null,
    val originalText: String = "",
    val rateText: String = "",
    val emiText: String = "",
    val tenureText: String = "",
    val payFromId: String? = null,
) {
    val original: Paise? get() = parseRupees(originalText)?.takeIf { it.value > 0 }
    val rateBps: Int? get() = parseRateBps(rateText)
    val emi: Paise? get() = parseRupees(emiText)?.takeIf { it.value > 0 }
    val tenure: Int? get() = tenureText.trim().toIntOrNull()?.takeIf { it in 1..600 }
    val suggestedEmi: Paise? get() = if (original != null && rateBps != null && tenure != null) suggestEmi(original!!, rateBps!!, tenure!!) else null
    val canSave: Boolean get() = accountId != null && original != null && rateBps != null && emi != null && payFromId != null

    fun toLoan(): LoanEntity = LoanEntity(requireNotNull(accountId), requireNotNull(original).value, requireNotNull(rateBps), requireNotNull(emi).value, firstDue.toString(), requireNotNull(payFromId))
}
