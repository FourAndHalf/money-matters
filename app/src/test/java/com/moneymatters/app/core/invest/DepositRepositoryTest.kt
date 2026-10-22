package com.moneymatters.app.core.invest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DepositRepositoryTest {

    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24)))
    private val start = LocalDate.of(2026, 1, 1)
    private val maturity = LocalDate.of(2027, 1, 1)
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var invest: InvestRepository
    private lateinit var bank: String
    private lateinit var fd: String

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        invest = InvestRepository(db, ledger, rules)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            fd = invest.openDeposit("HDFC FD 1", 1_00_000.rupees, 700, Compounding.Quarterly, start, maturity, bank, penaltyBps = 100)
        }
    }

    private fun interestIncome() = runBlocking { ledger.accountIdByName("Income:Interest")?.let { ledger.accountBalance(it) } ?: Paise.Zero }

    @Test fun `opening a deposit moves the principal into its own FixedDeposit account and records its terms`() = runBlocking {
        assertEquals(1_00_000.rupees, ledger.accountBalance(fd))
        assertEquals((-1_00_000).rupees, ledger.accountBalance(bank))
        assertEquals(AccountKind.FixedDeposit, db.ledgerDao().getAccount(fd)!!.kind)
        val row = db.investDao().getFixedDeposit(fd)!!
        assertEquals(700, row.rateBps)
        assertEquals(Compounding.Quarterly, row.compounding)
        assertEquals(100, row.penaltyBps)
        assertNotNull(row.openTransactionId)
    }

    @Test fun `an already-running deposit can be opened against opening equity without touching the bank`() = runBlocking {
        val equity = ledger.createAccount("Equity:Opening", AccountType.Equity)
        invest.openDeposit("SBI FD", 50_000.rupees, 650, Compounding.Quarterly, LocalDate.of(2025, 6, 1), LocalDate.of(2027, 6, 1), equity)
        assertEquals((-1_00_000).rupees, ledger.accountBalance(bank)) // only the first deposit's money
    }

    @Test fun `invalid terms are refused`() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.openDeposit("x", Paise.Zero, 700, Compounding.Simple, start, maturity, bank) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.openDeposit("x", 1_000.rupees, 0, Compounding.Simple, start, maturity, bank) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.openDeposit("x", 1_000.rupees, 700, Compounding.Simple, maturity, start, bank) } }
    }

    @Test fun `accruing books the interest earned as income and is exact however it is split up`() = runBlocking {
        val sixMonths = LocalDate.of(2026, 7, 1)
        val first = invest.accrueInterest(fd, sixMonths)
        assertEquals(accruedInterest(1_00_000.rupees, 700, Compounding.Quarterly, start, sixMonths, maturity), first)
        assertEquals(1_00_000.rupees + first, ledger.accountBalance(fd))
        assertEquals(-first, interestIncome())

        assertEquals(Paise.Zero, invest.accrueInterest(fd, sixMonths)) // nothing new: no entry posted
        val txnCount = ledger.snapshot().first().transactions.size
        invest.accrueInterest(fd, sixMonths)
        assertEquals(txnCount, ledger.snapshot().first().transactions.size)

        invest.accrueInterest(fd, maturity.plusYears(2)) // capped at maturity
        assertEquals(maturityValue(1_00_000.rupees, 700, Compounding.Quarterly, start, maturity), ledger.accountBalance(fd))
    }

    @Test fun `maturing pays principal plus interest into the account and closes the deposit`() = runBlocking {
        val payout = invest.closeDeposit(fd, maturity, bank)
        val expected = maturityValue(1_00_000.rupees, 700, Compounding.Quarterly, start, maturity)
        assertEquals(expected, payout)
        assertEquals(Paise.Zero, ledger.accountBalance(fd))
        assertEquals(expected - 1_00_000.rupees, ledger.accountBalance(bank)) // paid 1,00,000 in, got the maturity value back
        val row = db.investDao().getFixedDeposit(fd)!!
        assertEquals(maturity.toString(), row.closedOn)
        assertEquals(expected.value, row.payoutPaise)
        assertThrows(IllegalStateException::class.java) { runBlocking { invest.closeDeposit(fd, maturity, bank) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { invest.accrueInterest(fd, maturity) } }
        Unit
    }

    @Test fun `tax withheld at maturity becomes a receivable and reduces what is paid out`() = runBlocking {
        val full = maturityValue(1_00_000.rupees, 700, Compounding.Quarterly, start, maturity)
        val payout = invest.closeDeposit(fd, maturity, bank, tds = 500.rupees)
        assertEquals(full - 500.rupees, payout)
        assertEquals(500.rupees, ledger.accountBalance(ledger.accountIdByName(TDS_RECEIVABLE)!!))
        val snapshot = ledger.snapshot().first()
        snapshot.postings.groupBy { it.transactionId }.values.forEach { legs -> assertEquals(legs.sumOf { it.debitPaise ?: 0 }, legs.sumOf { it.creditPaise ?: 0 }) }
    }

    @Test fun `breaking early recalculates interest at the penalty rate, trimming interest already booked`() = runBlocking {
        invest.accrueInterest(fd, LocalDate.of(2026, 6, 1)) // booked at the full 7%
        val breakOn = LocalDate.of(2026, 7, 1)
        val payout = invest.closeDeposit(fd, breakOn, bank)
        val expected = breakValue(1_00_000.rupees, 700, Compounding.Quarterly, start, maturity, breakOn, 100).proceeds
        assertEquals(expected, payout)
        assertEquals(Paise.Zero, ledger.accountBalance(fd))
        assertEquals(-(expected - 1_00_000.rupees), interestIncome()) // income is exactly the interest paid out
    }

    @Test fun `breaking within 30 days of maturity has no penalty`() = runBlocking {
        val payout = invest.closeDeposit(fd, maturity.minusDays(10), bank)
        assertEquals(1_00_000.rupees + accruedInterest(1_00_000.rupees, 700, Compounding.Quarterly, start, maturity.minusDays(10), maturity), payout)
    }

    @Test fun `renewing rolls the payout into a new deposit of the same term and moves no bank money`() = runBlocking {
        val bankBefore = ledger.accountBalance(bank)
        val payout = invest.closeDeposit(fd, maturity, toAccountId = null, renew = true)
        assertEquals(bankBefore, ledger.accountBalance(bank))
        val renewed = db.investDao().getAllFixedDeposits().single { it.accountId != fd }
        assertEquals(payout.value, renewed.principalPaise)
        assertEquals(maturity.toString(), renewed.startDate)
        assertEquals(maturity.plusDays(365).toString(), renewed.maturityDate)
        assertEquals(700, renewed.rateBps)
        assertEquals(payout, ledger.accountBalance(renewed.accountId))
        assertEquals(Paise.Zero, ledger.accountBalance(fd))
    }

    @Test fun `a closing entry that is reversed reopens the deposit, and a reversed opening removes it`() = runBlocking {
        invest.closeDeposit(fd, maturity, bank)
        val closing = db.investDao().getFixedDeposit(fd)!!.closeTransactionId!!
        ledger.reconcile(closing)
        ledger.reverse(closing, "mistake")
        assertNull(db.investDao().getFixedDeposit(fd)!!.closedOn)
        assertEquals(maturityValue(1_00_000.rupees, 700, Compounding.Quarterly, start, maturity), ledger.accountBalance(fd))

        val opening = db.investDao().getFixedDeposit(fd)!!.openTransactionId!!
        // The deposit still has booked interest, so only the opening entry's own effect is reversed here.
        ledger.reconcile(opening)
        ledger.reverse(opening, "wrong bank")
        assertNull(db.investDao().getFixedDeposit(fd))
    }

    @Test fun `a deposit cannot be closed on or before its start date, or without a destination`() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.closeDeposit(fd, start, bank) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.closeDeposit(fd, maturity, toAccountId = null) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.closeDeposit(fd, maturity, bank, tds = 5_00_000.rupees) } }
    }

    @Test fun `valuation counts interest earned but not yet booked`() {
        val row = runBlocking { db.investDao().getFixedDeposit(fd)!! }
        val v = depositValuation(row, 1_00_000.rupees, LocalDate.of(2026, 7, 1))
        assertEquals(Paise.Zero, v.postedInterest)
        assertEquals(accruedInterest(1_00_000.rupees, 700, Compounding.Quarterly, start, LocalDate.of(2026, 7, 1), maturity), v.accruedInterest)
        assertEquals(v.accruedInterest, v.unposted)
        assertEquals(1_00_000.rupees + v.accruedInterest, v.value)
        assertEquals(184L, v.daysToMaturity)
        assertTrue(v.breakPenalty.value > 0)
        assertEquals(false, v.isClosed)
    }
}
