package com.moneymatters.app.feature.recurring

import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.PostingEntity
import com.moneymatters.app.core.ledger.TransactionEntity
import com.moneymatters.app.core.ledger.TransactionStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringFromEntryTest {
    private val bank = AccountEntity("bank", "HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
    private val rent = AccountEntity("rent", "Expenses:Rent", AccountType.Expense)
    private val food = AccountEntity("food", "Expenses:Food", AccountType.Expense)

    private fun snapshot(vararg postings: PostingEntity, archived: Boolean = false) = LedgerSnapshot(
        listOf(bank, rent.copy(isArchived = archived), food),
        listOf(TransactionEntity("t1", "2026-10-05", "Landlord", "Oct rent", TransactionStatus.Posted, DecisionKind.Need, null, PaymentMethod.Upi)),
        postings.toList(), emptyList(),
    )

    private fun leg(account: String, debit: Long? = null, credit: Long? = null, units: Long? = null) =
        PostingEntity(transactionId = "t1", accountId = account, debitPaise = debit, creditPaise = credit, units = units)

    @Test fun `a two-leg entry fills the form with payee, amount, accounts and tags`() {
        val form = recurringFormFor(snapshot(leg("rent", debit = 25_000_00), leg("bank", credit = 25_000_00)), "t1")!!
        assertEquals("Landlord", form.name)
        assertEquals("25000", form.amountText)
        assertEquals("bank", form.fromId)
        assertEquals("rent", form.toId)
        assertEquals(DecisionKind.Need, form.decision)
        assertEquals(PaymentMethod.Upi, form.paymentMethod)
        assertTrue(form.canSave)
    }

    @Test fun `the first date is a month after the entry so it is not booked twice`() {
        val form = recurringFormFor(snapshot(leg("rent", debit = 100), leg("bank", credit = 100)), "t1")!!
        assertEquals(LocalDate.of(2026, 11, 5), form.start)
        assertEquals(LocalDate.of(2026, 10, 5), form.sourceDate)
    }

    @Test fun `paise are kept`() {
        val form = recurringFormFor(snapshot(leg("rent", debit = 1_234_50), leg("bank", credit = 1_234_50)), "t1")!!
        assertEquals("1234.50", form.amountText)
    }

    @Test fun `a split entry cannot repeat as one rule`() {
        val s = snapshot(leg("rent", debit = 60), leg("food", debit = 40), leg("bank", credit = 100))
        assertNull(recurringFormFor(s, "t1"))
    }

    @Test fun `an investment entry cannot repeat as one rule`() {
        val s = snapshot(leg("rent", debit = 100, units = 5), leg("bank", credit = 100))
        assertNull(recurringFormFor(s, "t1"))
    }

    @Test fun `an unknown entry gives nothing`() {
        assertNull(recurringFormFor(snapshot(leg("rent", debit = 100), leg("bank", credit = 100)), "nope"))
    }

    @Test fun `an archived account is left for the user to choose again`() {
        val form = recurringFormFor(snapshot(leg("rent", debit = 100), leg("bank", credit = 100), archived = true), "t1")!!
        assertNull(form.toId)
        assertEquals(false, form.canSave)
    }
}
