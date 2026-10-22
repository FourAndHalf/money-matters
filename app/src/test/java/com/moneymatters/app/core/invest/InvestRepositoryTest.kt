package com.moneymatters.app.core.invest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.IllegalLifecycleTransitionException
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InvestRepositoryTest {

    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var invest: InvestRepository
    private lateinit var bank: String
    private lateinit var stockAccount: String
    private lateinit var holding: String
    private val rule = HoldingPeriodRule(12)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        invest = InvestRepository(db, ledger)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset)
            stockAccount = ledger.createAccount("Assets:Stocks:Infosys", AccountType.Asset)
            holding = invest.addHolding(stockAccount, Instrument.Stock, isin = "INE009A01021", symbol = "INFY")
        }
    }

    private fun buy(date: LocalDate, units: Long, amount: Paise) = runBlocking {
        invest.buy(holding, date, Units.whole(units), UnitPrice.rupees(amount.value / 100 / units), amount, bank, "Buy INFY")
    }

    private fun sell(date: LocalDate, units: Long, proceeds: Paise) = runBlocking {
        invest.sell(holding, date, Units.whole(units), UnitPrice.rupees(proceeds.value / 100 / units), proceeds, bank, "Sell INFY", rule)
    }

    @Test fun `a buy posts a balanced entry, carries units on the holding leg and opens a lot`() = runBlocking {
        val id = buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        assertEquals(1_000.rupees, ledger.accountBalance(stockAccount))
        assertEquals((-1_000).rupees, ledger.accountBalance(bank))
        val leg = db.ledgerDao().getPostingsForTransaction(id).single { it.accountId == stockAccount }
        assertEquals(Units.whole(10).micro, leg.units)
        assertEquals(Units.whole(10), invest.unitsHeld(holding))
    }

    @Test fun `a sale takes the oldest lots, posts cost and gain per term, and stays balanced`() = runBlocking {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        buy(LocalDate.of(2025, 6, 1), 10, 1_200.rupees)
        val id = sell(LocalDate.of(2026, 3, 1), 15, 2_400.rupees)

        // Cost used: 1,000 + 600. Proceeds 2,400 → gain 800: ₹600 long (old lot), ₹200 short.
        assertEquals(600.rupees, ledger.accountBalance(stockAccount)) // cost of the 5 units still held
        assertEquals(Units.whole(5), invest.unitsHeld(holding))
        val snapshot = ledger.snapshot().first()
        val byName = snapshot.accounts.associateBy { it.name }
        assertEquals(Paise(-600_00), ledger.accountBalance(byName.getValue("Income:Capital Gains:LTCG:Equity").id))
        assertEquals(Paise(-200_00), ledger.accountBalance(byName.getValue("Income:Capital Gains:STCG:Equity").id))
        val legs = snapshot.postings.filter { it.transactionId == id }
        assertEquals(legs.sumOf { it.debitPaise ?: 0 }, legs.sumOf { it.creditPaise ?: 0 })
    }

    @Test fun `a loss debits the gains account`() = runBlocking {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        sell(LocalDate.of(2025, 3, 1), 10, 700.rupees)
        val account = ledger.accountIdByName("Income:Capital Gains:STCG:Equity")!!
        assertEquals(Paise(30_000), ledger.accountBalance(account))
        assertEquals(Paise.Zero, ledger.accountBalance(stockAccount))
    }

    @Test fun `every trade in a mixed history leaves every transaction balanced and units equal to lots`() = runBlocking {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        sell(LocalDate.of(2025, 2, 1), 4, 500.rupees)
        buy(LocalDate.of(2025, 3, 1), 6, 900.rupees)
        sell(LocalDate.of(2026, 5, 1), 9, 1_500.rupees)
        val snapshot = ledger.snapshot().first()
        snapshot.postings.groupBy { it.transactionId }.values.forEach { legs ->
            assertEquals(legs.sumOf { it.debitPaise ?: 0 }, legs.sumOf { it.creditPaise ?: 0 })
        }
        val onBooks = snapshot.postings.filter { it.accountId == stockAccount }
        val ledgerUnits = onBooks.sumOf { if (it.debitPaise != null) it.units ?: 0 else -(it.units ?: 0) }
        assertEquals(ledgerUnits, invest.unitsHeld(holding).micro)
        // Book value left equals the cost of the lots still held.
        assertEquals(invest.openLots(holding).sumOf { it.remainingCost.value }, ledger.accountBalance(stockAccount).value)
    }

    @Test fun `a sale cannot use units bought after the sale date or more than are held`() {
        buy(LocalDate.of(2025, 6, 1), 10, 1_000.rupees)
        assertThrows(InsufficientUnitsException::class.java) { sell(LocalDate.of(2025, 5, 1), 1, 100.rupees) }
        assertThrows(InsufficientUnitsException::class.java) { sell(LocalDate.of(2025, 7, 1), 11, 1_100.rupees) }
        assertEquals(Units.whole(10), runBlocking { invest.unitsHeld(holding) })
    }

    @Test fun `reversing a sale gives its lots their units back`() = runBlocking {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        val sale = sell(LocalDate.of(2026, 3, 1), 4, 600.rupees)
        assertEquals(Units.whole(6), invest.unitsHeld(holding))
        ledger.reconcile(sale)
        ledger.reverse(sale, "wrong quantity")
        assertEquals(Units.whole(10), invest.unitsHeld(holding))
        assertEquals(1_000.rupees, ledger.accountBalance(stockAccount))
        assertEquals((-1_000).rupees, ledger.accountBalance(bank))
    }

    @Test fun `a purchase cannot be reversed once a sale used its lot, and an unused one disappears`() = runBlocking {
        val first = buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        val sale = sell(LocalDate.of(2026, 3, 1), 4, 600.rupees)
        ledger.reconcile(first)
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { ledger.reverse(first, "typo") } }

        ledger.reconcile(sale)
        ledger.reverse(sale, "undo")
        ledger.reverse(first, "typo")
        assertEquals(Units.Zero, invest.unitsHeld(holding))
        assertEquals(Paise.Zero, ledger.accountBalance(stockAccount))
    }

    @Test fun `an investment entry cannot be edited in place`() {
        val id = buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        assertThrows(IllegalLifecycleTransitionException::class.java) {
            runBlocking {
                ledger.edit(id, LocalDate.of(2025, 1, 10), "Buy INFY", listOf(PostingLine(stockAccount, debit = 900.rupees), PostingLine(bank, credit = 900.rupees)))
            }
        }
    }

    @Test fun `prices are recorded and the newest date wins`() = runBlocking {
        invest.recordPrice(holding, LocalDate.of(2026, 10, 10), UnitPrice(15_000_000), "manual", PriceStatus.Manual)
        invest.recordPrice(holding, LocalDate.of(2026, 10, 17), UnitPrice(15_500_000), "manual", PriceStatus.Manual)
        invest.recordPrice(holding, LocalDate.of(2026, 10, 3), UnitPrice(14_000_000), "manual", PriceStatus.Manual)
        assertEquals(15_500_000L, invest.latestPrice(holding)!!.price)
        assertNull(runBlocking { db.investDao().getFixedDeposit(bank) })
        assertNotNull(db.investDao().holdingForAccount(stockAccount))
    }

    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24)))

    private fun trades(vararg lines: String) = parseTrades("date,symbol,type,units,amount\n" + lines.joinToString("\n")).rows

    @Test fun `an import creates holdings, lots and sales against opening equity without touching the bank`() = runBlocking {
        val equity = ledger.createAccount("Equity:Opening", AccountType.Equity)
        val count = invest.importTrades(
            trades("2025-01-10,TCS,buy,10,40000", "2025-06-01,TCS,buy,5,21000", "2026-03-01,TCS,sell,12,60000", "2025-02-01,WIPRO,buy,100,25000"),
            equity, rules,
        )
        assertEquals(4, count)
        assertEquals(Paise.Zero, ledger.accountBalance(bank))
        val tcs = db.investDao().holdingBySymbol("tcs")!!
        assertEquals(Units.whole(3), invest.unitsHeld(tcs.id))
        // 12 sold FIFO: 10 from the first lot (₹40,000) and 2 from the second (₹8,400): cost left on 3 units is ₹12,600.
        assertEquals(12_600.rupees, ledger.accountBalance(tcs.accountId))
        assertEquals(Units.whole(100), invest.unitsHeld(db.investDao().holdingBySymbol("WIPRO")!!.id))
        val snapshot = ledger.snapshot().first()
        snapshot.postings.groupBy { it.transactionId }.values.forEach { legs ->
            assertEquals(legs.sumOf { it.debitPaise ?: 0 }, legs.sumOf { it.creditPaise ?: 0 })
        }
    }

    @Test fun `importing again by ISIN or symbol reuses the holding instead of creating another`() = runBlocking {
        val equity = ledger.createAccount("Equity:Opening", AccountType.Equity)
        invest.importTrades(trades("2025-01-10,TCS,buy,10,40000"), equity, rules)
        invest.importTrades(trades("2025-02-10,tcs,buy,5,21000"), equity, rules)
        assertEquals(1, db.investDao().getAllHoldings().count { it.symbol == "TCS" })
        assertEquals(Units.whole(15), invest.unitsHeld(db.investDao().holdingBySymbol("TCS")!!.id))
    }

    @Test fun `an import with an impossible sale changes nothing at all`() = runBlocking {
        val equity = ledger.createAccount("Equity:Opening", AccountType.Equity)
        val accountsBefore = ledger.snapshot().first().accounts.size
        assertThrows(InsufficientUnitsException::class.java) {
            runBlocking { invest.importTrades(trades("2025-01-10,TCS,buy,10,40000", "2025-03-01,TCS,sell,11,50000"), equity, rules) }
        }
        val after = ledger.snapshot().first()
        assertEquals(accountsBefore, after.accounts.size)
        assertEquals(0, after.transactions.size)
        assertEquals(0, db.investDao().getAllLots().size)
        assertNull(db.investDao().holdingBySymbol("TCS"))
    }

    @Test fun `a posting on a holding account without units is refused, so Quick Add cannot desync lots`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                ledger.postTransaction(
                    LocalDate.of(2025, 1, 10), "Plain transfer",
                    listOf(PostingLine(stockAccount, debit = 500.rupees), PostingLine(bank, credit = 500.rupees)),
                )
            }
        }
        assertEquals(Paise.Zero, runBlocking { ledger.accountBalance(stockAccount) })
    }

    @Test fun `the snapshot lists holding accounts so pickers can leave them out`() = runBlocking {
        assertEquals(setOf(stockAccount), ledger.snapshot().first().holdingAccountIds)
    }

    @Test fun `an account can be taken out of net worth and put back`() = runBlocking {
        ledger.setInNetWorth(bank, false)
        assertEquals(false, db.ledgerDao().getAccount(bank)!!.inNetWorth)
        ledger.setInNetWorth(bank, true)
        assertEquals(true, db.ledgerDao().getAccount(bank)!!.inNetWorth)
    }
}
