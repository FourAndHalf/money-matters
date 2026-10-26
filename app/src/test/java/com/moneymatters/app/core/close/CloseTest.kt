package com.moneymatters.app.core.close

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.Compounding
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.ClosedPeriodException
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloseTest {

    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24, LocalDate.of(2023, 4, 1))))
    private val october = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val november = Period(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
    private val today = LocalDate.of(2026, 12, 2)
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var invest: InvestRepository
    private lateinit var close: CloseRepository
    private lateinit var bank: String
    private lateinit var food: String

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        invest = InvestRepository(db, ledger, rules)
        close = CloseRepository(db, ledger, nowMillis = { 1_000L })
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            food = ledger.createAccount("Food", AccountType.Expense)
        }
    }

    private fun spend(date: LocalDate, amount: Long) = runBlocking {
        ledger.postTransaction(date, "Lunch", listOf(PostingLine(food, debit = amount.rupees), PostingLine(bank, credit = amount.rupees)))
    }

    private fun checklist(period: Period, confirmed: Set<String> = emptySet(), on: LocalDate = today) =
        runBlocking { buildChecklist(ledger.snapshot().first(), invest.snapshot().first(), period, on, confirmed) }

    private fun item(list: com.moneymatters.app.core.close.Checklist, title: String) = list.items.single { it.title == title }

    /** Confirms every balance and reconciles, the two steps a close needs before it can lock. */
    private fun ready(period: Period) = runBlocking { close.reconcileAll(period) }

    private fun closePeriod(period: Period, on: LocalDate = today) = runBlocking { close.close(period, period.label, on) }

    private fun status(id: String) = runBlocking { db.ledgerDao().getTransaction(id)!!.status }

    // ---- Periods ----

    @Test fun `periods follow the month start day`() {
        assertEquals(Period(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 24)), periodContaining(LocalDate.of(2026, 10, 10), 25))
        assertEquals(Period(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)), periodContaining(LocalDate.of(2026, 10, 25), 25))
        assertEquals(october, latestEndedPeriod(LocalDate.of(2026, 11, 5), 1))
        assertEquals("October 2026", october.label)
    }

    @Test fun `the next month to close is the one with the oldest open entry, else the latest ended`() {
        val snapshot = { runBlocking { ledger.snapshot().first() } }
        assertEquals(november, nextPeriodToClose(snapshot(), LocalDate.of(2026, 12, 2), 1))
        spend(LocalDate.of(2026, 1, 15), 100)
        spend(LocalDate.of(2026, 5, 3), 100)
        assertEquals(Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), nextPeriodToClose(snapshot(), today, 1))
        // An open entry in the current month isn't a month that can close yet, so only that one leaves November.
        spend(LocalDate.of(2026, 12, 1), 100)
        val decemberOnly = snapshot().let { s -> s.copy(transactions = s.transactions.filter { it.date >= "2026-12" }) }
        assertEquals(november, nextPeriodToClose(decemberOnly, today, 1))
    }

    @Test fun `a backlog closes oldest month first, January then May, then the latest`() {
        spend(LocalDate.of(2026, 1, 15), 100)
        spend(LocalDate.of(2026, 5, 3), 100)
        spend(LocalDate.of(2026, 11, 3), 100)
        val january = Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        val may = Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
        fun next() = runBlocking { nextPeriodToClose(ledger.snapshot().first(), today, 1) }
        assertThrows(CloseNotAllowedException::class.java) { closePeriod(may) } // January is still open
        for (period in listOf(january, may, november)) {
            assertEquals(period, next())
            ready(period)
            closePeriod(period)
        }
        assertEquals(3, runBlocking { db.ledgerDao().getAllClosedPeriods() }.size)
    }

    // ---- Checklist ----

    @Test fun `an unreconciled, unconfirmed month fails and a prepared one passes`() {
        spend(LocalDate.of(2026, 10, 5), 200)
        val before = checklist(october)
        assertFalse(before.canClose)
        assertEquals(1, before.items.count { !it.ok && it.title == "Balances confirmed" })
        assertFalse(item(before, "Entries reconciled").ok)

        ready(october)
        val lines = runBlocking { balanceLines(ledger.snapshot().first(), october) }
        assertEquals(listOf("HDFC Bank"), lines.map { it.name })
        assertEquals((-200).rupees, lines.single().balance)
        assertTrue(checklist(october, lines.map { it.accountId }.toSet()).canClose)
    }

    @Test fun `a draft, an unfinished month and an open earlier month each block the close`() {
        runBlocking { ledger.postTransaction(LocalDate.of(2026, 10, 6), "Maybe", listOf(PostingLine(food, debit = 5.rupees), PostingLine(bank, credit = 5.rupees)), status = TransactionStatus.Draft) }
        assertFalse(item(checklist(october), "No drafts").ok)
        assertFalse(item(checklist(october, on = LocalDate.of(2026, 10, 31)), "Month has ended").ok)
        spend(LocalDate.of(2026, 10, 7), 10)
        assertFalse(item(checklist(november), "Earlier months are closed").ok)
    }

    @Test fun `a holding needs a verified or confirmed price from the last days of the month`() {
        val account = runBlocking { ledger.createAccount("Infosys (INFY)", AccountType.Asset, kind = AccountKind.Investment) }
        val holding = runBlocking { invest.addHolding(account, Instrument.Stock, symbol = "INFY") }
        runBlocking { invest.buy(holding, LocalDate.of(2026, 10, 3), Units.whole(10), UnitPrice.rupees(100), 1_000.rupees, bank, "Buy") }
        assertFalse(item(checklist(october), "Holdings priced").ok)
        runBlocking { invest.recordPrice(holding, LocalDate.of(2026, 10, 10), UnitPrice.rupees(110), "manual", PriceStatus.Manual) }
        assertFalse("a price from the 10th is stale by the 31st", item(checklist(october), "Holdings priced").ok)
        runBlocking { invest.recordPrice(holding, LocalDate.of(2026, 10, 30), UnitPrice.rupees(120), "manual", PriceStatus.Manual) }
        assertTrue(item(checklist(october), "Holdings priced").ok)
    }

    @Test fun `interest owed on an open deposit blocks the close until it is posted`() {
        runBlocking { invest.openDeposit("SBI FD", 1_00_000.rupees, 700, Compounding.Quarterly, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 9, 1), bank) }
        val fd = runBlocking { ledger.accountIdByName("SBI FD")!! }
        assertFalse(item(checklist(october), "FD interest posted").ok)
        runBlocking { invest.accrueInterest(fd, october.end) }
        assertTrue(item(checklist(october), "FD interest posted").ok)
    }

    // ---- Closing ----

    @Test fun `closing locks the month's entries and starts the chain`() {
        val a = spend(LocalDate.of(2026, 10, 5), 200)
        val outside = spend(LocalDate.of(2026, 11, 2), 50)
        ready(october)
        closePeriod(october)
        assertEquals(TransactionStatus.Locked, status(a))
        assertEquals(TransactionStatus.Posted, status(outside))
        val closed = runBlocking { ledger.snapshot().first().closedPeriods.single() }
        assertEquals(GENESIS_HASH, closed.prevHash)
        assertEquals(64, closed.hash.length)
        assertTrue(runBlocking { ledger.snapshot().first().audit.any { it.action == "MonthClosed" } })
    }

    @Test fun `the next close links to the previous hash`() {
        spend(LocalDate.of(2026, 10, 5), 200)
        spend(LocalDate.of(2026, 11, 5), 300)
        ready(october); closePeriod(october)
        ready(november); closePeriod(november)
        val closed = runBlocking { ledger.snapshot().first().closedPeriods }
        assertEquals(closed[0].hash, closed[1].prevHash)
        assertNotEquals(closed[0].hash, closed[1].hash)
        assertTrue(runBlocking { verifyChain(closed, ledger.snapshot().first()) }.all { it.ok })
    }

    @Test fun `a close needs the typed name, an ended month, reconciled entries and order`() {
        spend(LocalDate.of(2026, 10, 5), 200)
        spend(LocalDate.of(2026, 11, 5), 300)
        assertThrows(CloseNotAllowedException::class.java) { runBlocking { close.close(october, "oct", today) } }
        assertThrows(CloseNotAllowedException::class.java) { runBlocking { close.close(october, "October 2026", LocalDate.of(2026, 10, 31)) } }
        assertThrows("posted but not reconciled", CloseNotAllowedException::class.java) { closePeriod(october) }
        ready(november)
        assertThrows("October is still open", CloseNotAllowedException::class.java) { closePeriod(november) }
        ready(october); closePeriod(october)
        assertThrows("already closed", CloseNotAllowedException::class.java) { closePeriod(october) }
    }

    @Test fun `a failed close changes nothing`() {
        val a = spend(LocalDate.of(2026, 10, 5), 200)
        runBlocking { ledger.reconcile(a) }
        val b = spend(LocalDate.of(2026, 10, 6), 20)
        assertThrows(CloseNotAllowedException::class.java) { closePeriod(october) }
        assertEquals(TransactionStatus.Reconciled, status(a))
        assertEquals(TransactionStatus.Posted, status(b))
        assertTrue(runBlocking { ledger.snapshot().first().closedPeriods.isEmpty() })
    }

    // ---- A closed month stays closed ----

    @Test fun `new entries and edits dated in a closed month are refused, an adjustment is allowed`() {
        val a = spend(LocalDate.of(2026, 10, 5), 200)
        val open = spend(LocalDate.of(2026, 11, 5), 10)
        ready(october); closePeriod(october)
        assertThrows(ClosedPeriodException::class.java) { spend(LocalDate.of(2026, 10, 31), 1) }
        assertThrows(ClosedPeriodException::class.java) {
            runBlocking { ledger.edit(open, LocalDate.of(2026, 10, 15), "Moved", listOf(PostingLine(food, debit = 10.rupees), PostingLine(bank, credit = 10.rupees))) }
        }
        runBlocking {
            ledger.postPriorPeriodAdjustment(a, LocalDate.of(2026, 11, 20), "Lunch fix", listOf(PostingLine(food, credit = 20.rupees), PostingLine(bank, debit = 20.rupees)), "Wrong amount")
        }
        spend(LocalDate.of(2026, 11, 1), 5)
    }

    @Test fun `changing a closed entry behind the app's back breaks the chain check`() {
        spend(LocalDate.of(2026, 10, 5), 200)
        ready(october); closePeriod(october)
        assertTrue(runBlocking { verifyChain(ledger.snapshot().first().closedPeriods, ledger.snapshot().first()) }.single().ok)
        db.openHelper.writableDatabase.execSQL("UPDATE postings SET debitPaise = 1 WHERE debitPaise = 20000")
        val check = runBlocking { verifyChain(ledger.snapshot().first().closedPeriods, ledger.snapshot().first()) }.single()
        assertFalse(check.ok)
        assertEquals("Entries in this month have changed since it was closed", check.problem)
    }
}
