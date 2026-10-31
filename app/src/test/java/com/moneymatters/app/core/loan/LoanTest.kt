package com.moneymatters.app.core.loan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.IllegalLifecycleTransitionException
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Expected figures below were worked out independently of this code, in a separate script, with the same half-up rounding. */
class LoanMathTest {
    @Test fun `a month's interest is a twelfth of the annual rate, rounded half up to the paisa`() {
        assertEquals(1_000.rupees, monthlyInterest(100_000.rupees, 1200)) // 12% a year is 1% a month
        assertEquals(Paise(1), monthlyInterest(Paise(50), 1200)) // 0.5 paisa rounds up
        assertEquals(Paise.Zero, monthlyInterest(Paise(49), 1200)) // 0.49 rounds down
        assertEquals(Paise.Zero, monthlyInterest(Paise.Zero, 1200))
        assertEquals(Paise.Zero, monthlyInterest(100_000.rupees, 0))
    }

    @Test fun `the suggested EMI clears the loan and is rounded up to the rupee`() {
        assertEquals(8_885.rupees, suggestEmi(100_000.rupees, 1200, 12)) // exactly 8,884.88
        assertEquals(10_259.rupees, suggestEmi(500_000.rupees, 850, 60))
        assertEquals(100.rupees, suggestEmi(1_200.rupees, 0, 12)) // no interest: an even split
        assertEquals(334.rupees, suggestEmi(1_000.rupees, 0, 3)) // 333.33 rounds up, never short
    }

    @Test fun `the first EMI splits into interest on the whole balance and the rest as principal`() {
        val s = splitEmi(100_000.rupees, 8_885.rupees, 1200)
        assertEquals(1_000.rupees, s.interest)
        assertEquals(7_885.rupees, s.principal)
        assertEquals(8_885.rupees, s.paid)
    }

    @Test fun `the last EMI is whatever clears the balance, and can be smaller`() {
        val s = splitEmi(5_000.rupees, 8_885.rupees, 1200)
        assertEquals(5_000.rupees, s.principal)
        assertEquals(50.rupees, s.interest)
        assertEquals(5_050.rupees, s.paid)
    }

    @Test fun `an EMI that doesn't cover the interest repays nothing and is flagged`() {
        assertEquals(Paise.Zero, splitEmi(100_000.rupees, 1_000.rupees, 1200).principal)
        assertFalse(emiCoversInterest(100_000.rupees, 1_000.rupees, 1200)) // equal to the interest still never ends
        assertTrue(emiCoversInterest(100_000.rupees, 1_001.rupees, 1200))
    }

    @Test fun `the schedule matches an independent calculation`() {
        val plan = schedule(100_000.rupees, 8_885.rupees, 1200, LocalDate.of(2026, 11, 5))

        assertEquals(12, plan.size)
        assertEquals(Instalment(1, LocalDate.of(2026, 11, 5), Paise(788_500), Paise(100_000), Paise(9_211_500)), plan[0])
        assertEquals(Instalment(2, LocalDate.of(2026, 12, 5), Paise(796_385), Paise(92_115), Paise(8_415_115)), plan[1])
        assertEquals(Instalment(12, LocalDate.of(2027, 10, 5), Paise(879_550), Paise(8_796), Paise.Zero), plan.last())
        assertEquals(Paise(10_000_000), Paise(plan.sumOf { it.principal.value })) // principal repaid is exactly what was borrowed
        assertEquals(Paise(661_846), payoff(plan).interestRemaining) // ₹6,618.46
        assertEquals(LocalDate.of(2027, 10, 5), payoff(plan).lastDue)
    }

    @Test fun `a five lakh loan over five years matches too`() {
        val plan = schedule(500_000.rupees, 10_259.rupees, 850, LocalDate.of(2026, 11, 5))
        assertEquals(60, plan.size)
        assertEquals(Paise(671_733), plan.first().principal)
        assertEquals(Paise(354_167), plan.first().interest)
        assertEquals(Paise(11_548_537), payoff(plan).interestRemaining)
        assertEquals(Paise.Zero, plan.last().balanceAfter)
    }

    @Test fun `an EMI that never touches the balance gives an empty schedule, not an endless one`() {
        assertTrue(schedule(100_000.rupees, 1_000.rupees, 1200, LocalDate.of(2026, 11, 5)).isEmpty())
        assertEquals(MAX_INSTALMENTS, schedule(100_000.rupees, 1_001.rupees, 1200, LocalDate.of(2026, 11, 5)).size)
    }

    @Test fun `due dates keep the day and clamp in short months without drifting`() {
        val first = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), dueDate(first, 1))
        assertEquals(LocalDate.of(2026, 3, 31), dueDate(first, 2))
        assertEquals(LocalDate.of(2026, 4, 30), dueDate(first, 3))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoanRepositoryTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var repo: LoanRepository
    private lateinit var loan: String
    private lateinit var bank: String
    private lateinit var opening: String
    private val first = LocalDate.of(2026, 11, 5)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        repo = LoanRepository(db, ledger)
        runBlocking {
            loan = ledger.createAccount("Home Loan", AccountType.Liability, kind = AccountKind.Loan)
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            opening = ledger.createAccount("Equity:Opening", AccountType.Equity)
            ledger.postTransaction(LocalDate.of(2026, 10, 1), "Opening", listOf(PostingLine(opening, debit = 500_000.rupees), PostingLine(loan, credit = 500_000.rupees)))
            ledger.postTransaction(LocalDate.of(2026, 10, 1), "Opening", listOf(PostingLine(bank, debit = 100_000.rupees), PostingLine(opening, credit = 100_000.rupees)))
        }
    }

    private fun terms(emi: Long = 10_259, rateBps: Int = 850) = LoanEntity(loan, 500_000.rupees.value, rateBps, emi.rupees.value, first.toString(), bank)

    private fun add(t: LoanEntity = terms()) = runBlocking { repo.add(t) }

    private fun owed() = runBlocking { repo.outstanding(loan) }

    @Test fun `paying an EMI books one balanced entry that splits principal, interest and the bank outflow`() {
        add()

        val p = runBlocking { repo.payEmi(loan, first) }

        assertEquals(Paise(671_733), Paise(p.principalPaise))
        assertEquals(Paise(354_167), Paise(p.interestPaise))
        assertEquals(Paise(49_328_267), owed()) // 5,00,000 less 6,717.33
        assertEquals(Paise(10_000_000 - 1_025_900), runBlocking { ledger.accountBalance(bank) }) // 1,00,000 less the 10,259 EMI
        val snap = runBlocking { ledger.snapshot().first() }
        val interest = snap.accounts.single { it.name == INTEREST_ACCOUNT }
        assertEquals(AccountType.Expense, interest.type)
        assertEquals(Paise(354_167), runBlocking { ledger.accountBalance(interest.id) })
        val legs = snap.postings.filter { it.transactionId == p.transactionId }
        assertEquals(3, legs.size)
        assertEquals(0L, legs.sumOf { (it.debitPaise ?: 0L) - (it.creditPaise ?: 0L) })
    }

    @Test fun `each EMI moves the due date on a month, and interest falls as the balance does`() {
        add()
        val one = runBlocking { repo.payEmi(loan, first) }
        val two = runBlocking { repo.payEmi(loan, LocalDate.of(2026, 12, 5)) }

        assertEquals("2026-11-05", one.dueDate)
        assertEquals("2026-12-05", two.dueDate)
        assertTrue(two.interestPaise < one.interestPaise)
        val live = livePayments(runBlocking { db.loanDao().paymentsFor(loan) }, runBlocking { ledger.allTransactionIds() })
        assertEquals(LocalDate.of(2027, 1, 5), nextDue(terms(), live))
    }

    @Test fun `a prepayment reduces the balance with no interest, and later interest reflects it`() {
        add()
        runBlocking { repo.payEmi(loan, first) }
        val pre = runBlocking { repo.prepay(loan, 50_000.rupees, LocalDate.of(2026, 11, 20)) }

        assertTrue(pre.isPrepayment)
        assertEquals(0L, pre.interestPaise)
        assertEquals(Paise(44_328_267), owed()) // 4,93,282.67 less 50,000
        assertEquals(Paise(313_992), splitEmi(owed(), 10_259.rupees, 850).interest) // interest on the smaller balance
        val live = livePayments(runBlocking { db.loanDao().paymentsFor(loan) }, runBlocking { ledger.allTransactionIds() })
        assertEquals(LocalDate.of(2026, 12, 5), nextDue(terms(), live)) // a prepayment doesn't use up an instalment
    }

    @Test fun `deleting an EMI entry makes that instalment unpaid again`() {
        add()
        val p = runBlocking { repo.payEmi(loan, first) }
        runBlocking { ledger.deleteEntries(listOf(p.transactionId), "entered by mistake") }

        val live = livePayments(runBlocking { db.loanDao().paymentsFor(loan) }, runBlocking { ledger.allTransactionIds() })
        assertTrue(live.isEmpty())
        assertEquals(first, nextDue(terms(), live))
        assertEquals(500_000.rupees, owed())
    }

    @Test fun `the last EMI clears the loan, and then there is nothing to pay`() {
        add(terms(emi = 600_000, rateBps = 0).copy(emiPaise = 600_000.rupees.value))
        val p = runBlocking { repo.payEmi(loan, first) }

        assertEquals(Paise.Zero, owed())
        assertEquals(500_000.rupees.value, p.principalPaise)
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { repo.payEmi(loan, LocalDate.of(2026, 12, 5)) } }
    }

    @Test fun `a prepayment can't be zero or more than is owed`() {
        add()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.prepay(loan, Paise.Zero, first) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.prepay(loan, 500_001.rupees, first) } }
    }

    @Test fun `terms are checked before they are saved`() {
        assertThrows(IllegalArgumentException::class.java) { add(terms(emi = 0)) }
        assertThrows(IllegalArgumentException::class.java) { add(terms().copy(rateBps = 10_001)) }
        assertThrows(IllegalArgumentException::class.java) { add(terms().copy(payFromAccountId = loan)) }
        assertThrows(IllegalArgumentException::class.java) { add(terms().copy(payFromAccountId = opening)) } // not a bank or cash account
        assertThrows(IllegalArgumentException::class.java) { add(terms().copy(accountId = bank)) } // not a loan account
        assertThrows(IllegalArgumentException::class.java) { add(terms(emi = 3_541)) } // doesn't cover the first month's interest
    }

    @Test fun `a loan has one set of terms`() {
        add()
        assertThrows(IllegalArgumentException::class.java) { add() }
    }

    @Test fun `removing the terms keeps the account and its entries`() {
        add()
        runBlocking { repo.payEmi(loan, first) }
        runBlocking { repo.delete(loan) }

        assertNull(runBlocking { db.loanDao().loan(loan) })
        assertTrue(runBlocking { db.loanDao().paymentsFor(loan) }.isEmpty())
        assertEquals(Paise(49_328_267), owed()) // the books still say what is owed
    }
}
