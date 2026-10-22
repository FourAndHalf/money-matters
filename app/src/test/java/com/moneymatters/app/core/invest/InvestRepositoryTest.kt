package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.parseUnits
import com.moneymatters.app.core.money.impliedPrice
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

    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24, LocalDate.of(2023, 4, 1))))
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var invest: InvestRepository
    private lateinit var bank: String
    private lateinit var stockAccount: String
    private lateinit var holding: String

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        invest = InvestRepository(db, ledger, rules)
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
        invest.sell(holding, date, Units.whole(units), UnitPrice.rupees(proceeds.value / 100 / units), proceeds, bank, "Sell INFY")
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

    private fun trades(vararg lines: String) = parseTrades("date,symbol,type,units,amount\n" + lines.joinToString("\n")).rows

    @Test fun `an import creates holdings, lots and sales against opening equity without touching the bank`() = runBlocking {
        val equity = ledger.createAccount("Equity:Opening", AccountType.Equity)
        val count = invest.importTrades(
            trades("2025-01-10,TCS,buy,10,40000", "2025-06-01,TCS,buy,5,21000", "2026-03-01,TCS,sell,12,60000", "2025-02-01,WIPRO,buy,100,25000"),
            equity,
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
        invest.importTrades(trades("2025-01-10,TCS,buy,10,40000"), equity)
        invest.importTrades(trades("2025-02-10,tcs,buy,5,21000"), equity)
        assertEquals(1, db.investDao().getAllHoldings().count { it.symbol == "TCS" })
        assertEquals(Units.whole(15), invest.unitsHeld(db.investDao().holdingBySymbol("TCS")!!.id))
    }

    @Test fun `an import with an impossible sale changes nothing at all`() = runBlocking {
        val equity = ledger.createAccount("Equity:Opening", AccountType.Equity)
        val accountsBefore = ledger.snapshot().first().accounts.size
        assertThrows(InsufficientUnitsException::class.java) {
            runBlocking { invest.importTrades(trades("2025-01-10,TCS,buy,10,40000", "2025-03-01,TCS,sell,11,50000"), equity) }
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

    // ---- dividends and corporate actions ----

    @Test fun `a dividend pays the net into the account, keeps the TDS as a receivable and books the gross as income`() = runBlocking {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        val id = invest.recordDividend(holding, LocalDate.of(2026, 6, 1), 1_800.rupees, 200.rupees, bank, "Dividend INFY")
        val snapshot = ledger.snapshot().first()
        val byName = snapshot.accounts.associateBy { it.name }
        assertEquals((-1_000 + 1_800).rupees, ledger.accountBalance(bank))
        assertEquals(200.rupees, ledger.accountBalance(byName.getValue(TDS_RECEIVABLE).id))
        assertEquals((-2_000).rupees, ledger.accountBalance(byName.getValue("Income:Dividends").id))
        val legs = snapshot.postings.filter { it.transactionId == id }
        assertEquals(legs.sumOf { it.debitPaise ?: 0 }, legs.sumOf { it.creditPaise ?: 0 })
        val action = db.investDao().getAllCorporateActions().single()
        assertEquals(CorporateActionKind.Dividend, action.kind)
        assertEquals(2_000_00L, action.amountPaise)
    }

    @Test fun `a dividend with no tax needs no receivable account`() = runBlocking {
        invest.recordDividend(holding, LocalDate.of(2026, 6, 1), 500.rupees, Paise.Zero, bank, "Dividend INFY")
        assertNull(ledger.accountIdByName(TDS_RECEIVABLE))
    }

    @Test fun `a split posts a no-money entry, multiplies the units and keeps cost and holding period`() = runBlocking {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        val id = invest.applyCorporateAction(holding, RestateKind.Split, Units.whole(5), LocalDate.of(2026, 1, 1))
        assertEquals(Units.whole(50), invest.unitsHeld(holding))
        assertEquals(1_000.rupees, ledger.accountBalance(stockAccount)) // cost unchanged
        assertEquals((-1_000).rupees, ledger.accountBalance(bank))      // no money moved
        val lot = invest.openLots(holding).single()
        assertEquals(LocalDate.of(2025, 1, 10), lot.acquiredOn)
        assertEquals(1_000.rupees, lot.remainingCost)
        val snapshot = ledger.snapshot().first()
        val ledgerUnits = snapshot.postings.filter { it.accountId == stockAccount }.sumOf { if (it.debitPaise != null) it.units ?: 0 else -(it.units ?: 0) }
        assertEquals(Units.whole(50).micro, ledgerUnits)
        assertEquals(1, snapshot.postings.count { it.transactionId == id && it.debitPaise != null })
    }

    @Test fun `a bonus adds a zero-cost lot that starts its own holding period`() = runBlocking {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        invest.applyCorporateAction(holding, RestateKind.Bonus, Units.whole(1), LocalDate.of(2026, 1, 1))
        assertEquals(Units.whole(20), invest.unitsHeld(holding))
        val lots = invest.openLots(holding)
        assertEquals(listOf(LocalDate.of(2025, 1, 10), LocalDate.of(2026, 1, 1)), lots.map { it.acquiredOn })
        assertEquals(listOf(1_000.rupees, Paise.Zero), lots.map { it.remainingCost })
        assertEquals(1_000.rupees, ledger.accountBalance(stockAccount))
    }

    @Test fun `selling after a split uses the restated lots and splits the gain`() = runBlocking {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        invest.applyCorporateAction(holding, RestateKind.Split, Units.whole(5), LocalDate.of(2025, 6, 1))
        sell(LocalDate.of(2026, 3, 1), 25, 900.rupees) // half the 50 units: cost 500, proceeds 900
        assertEquals(Units.whole(25), invest.unitsHeld(holding))
        assertEquals(500.rupees, ledger.accountBalance(stockAccount))
        assertEquals(Paise(-40_000), ledger.accountBalance(ledger.accountIdByName("Income:Capital Gains:LTCG:Equity")!!))
    }

    @Test fun `a trade dated before a recorded split is refused`() {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        runBlocking { invest.applyCorporateAction(holding, RestateKind.Split, Units.whole(5), LocalDate.of(2025, 6, 1)) }
        assertThrows(IllegalArgumentException::class.java) { buy(LocalDate.of(2025, 3, 1), 1, 100.rupees) }
        assertThrows(IllegalArgumentException::class.java) { sell(LocalDate.of(2025, 5, 1), 1, 100.rupees) }
        buy(LocalDate.of(2025, 6, 1), 1, 100.rupees) // on the day is fine: it is in the new terms
    }

    @Test fun `reversing a split restores the original lots and lifts the date restriction`() = runBlocking {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000.rupees)
        val id = invest.applyCorporateAction(holding, RestateKind.Split, Units.whole(5), LocalDate.of(2025, 6, 1))
        ledger.reconcile(id)
        ledger.reverse(id, "wrong ratio")
        assertEquals(Units.whole(10), invest.unitsHeld(holding))
        assertEquals(0, db.investDao().getAllCorporateActions().size)
        buy(LocalDate.of(2025, 3, 1), 1, 100.rupees)
        Unit
    }

    @Test fun `an import replays a split and a bonus in date order`() = runBlocking {
        val equity = ledger.createAccount("Equity:Opening", AccountType.Equity)
        invest.importTrades(
            trades("2025-01-10,TCS,buy,10,40000", "2025-06-01,TCS,split,5,", "2025-07-01,TCS,bonus,1,", "2026-03-01,TCS,sell,95,90000"),
            equity,
        )
        val tcs = db.investDao().holdingBySymbol("TCS")!!
        assertEquals(Units.whole(5), invest.unitsHeld(tcs.id))
        assertEquals(listOf(CorporateActionKind.Split, CorporateActionKind.Bonus), db.investDao().getAllCorporateActions().map { it.kind })
    }

    // ---- mutual funds ----

    private fun fund(name: String, fundClass: FundClass, exitLoadDays: Int? = null): Pair<String, String> = runBlocking {
        val id = invest.findOrCreateHolding(Instrument.MutualFund, symbol = null, name = name, fundClass = fundClass, exitLoadDays = exitLoadDays)
        id to db.investDao().getHolding(id)!!.accountId
    }

    private fun fundBuy(id: String, date: LocalDate, units: Long, amount: Paise) = runBlocking {
        invest.buy(id, date, Units.whole(units), UnitPrice.rupees(amount.value / 100 / units), amount, bank, "Buy fund")
    }

    private fun fundSell(id: String, date: LocalDate, units: Long, proceeds: Paise) = runBlocking {
        invest.sell(id, date, Units.whole(units), UnitPrice.rupees(proceeds.value / 100 / units), proceeds, bank, "Sell fund")
    }

    @Test fun `a fund order waits In Transit, then allotment moves it into the holding as units`() = runBlocking {
        val (fundId, fundAccount) = fund("PPFAS Flexi Cap", FundClass.Equity)
        val pending = invest.orderFund(fundId, LocalDate.of(2026, 10, 1), 10_000.rupees, bank, "Buy PPFAS (in transit)")
        val inTransit = ledger.accountIdByName(IN_TRANSIT)!!
        assertEquals(10_000.rupees, ledger.accountBalance(inTransit))
        assertEquals((-10_000).rupees, ledger.accountBalance(bank))
        assertEquals(Paise.Zero, ledger.accountBalance(fundAccount))
        assertEquals(Units.Zero, invest.unitsHeld(fundId))
        assertEquals(true, db.investDao().getPending(pending)!!.isOpen)

        val txn = invest.allot(pending, LocalDate.of(2026, 10, 2), parseUnits("123.456")!!)
        assertEquals(Paise.Zero, ledger.accountBalance(inTransit))
        assertEquals(10_000.rupees, ledger.accountBalance(fundAccount))
        assertEquals(parseUnits("123.456"), invest.unitsHeld(fundId))
        assertEquals(false, db.investDao().getPending(pending)!!.isOpen)
        val nav = db.ledgerDao().getPostingsForTransaction(txn).single { it.accountId == fundAccount }.unitPrice
        assertEquals(impliedPrice(parseUnits("123.456")!!, 10_000.rupees).tenThousandths, nav) // about ₹81.00
        assertEquals(LocalDate.of(2026, 10, 2), invest.openLots(fundId).single().acquiredOn)
    }

    @Test fun `an allotment cannot predate its order or happen twice`() = runBlocking {
        val (fundId, _) = fund("PPFAS Flexi Cap", FundClass.Equity)
        val pending = invest.orderFund(fundId, LocalDate.of(2026, 10, 1), 10_000.rupees, bank, "Buy (in transit)")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.allot(pending, LocalDate.of(2026, 9, 30), Units.whole(100)) } }
        invest.allot(pending, LocalDate.of(2026, 10, 2), Units.whole(100))
        assertThrows(IllegalStateException::class.java) { runBlocking { invest.allot(pending, LocalDate.of(2026, 10, 3), Units.whole(100)) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { invest.cancelPending(pending, LocalDate.of(2026, 10, 3)) } }
        Unit
    }

    @Test fun `cancelling an order puts the money back where it came from`() = runBlocking {
        val (fundId, _) = fund("PPFAS Flexi Cap", FundClass.Equity)
        val pending = invest.orderFund(fundId, LocalDate.of(2026, 10, 1), 10_000.rupees, bank, "Buy (in transit)")
        invest.cancelPending(pending, LocalDate.of(2026, 10, 2))
        assertEquals(Paise.Zero, ledger.accountBalance(bank))
        assertEquals(Paise.Zero, ledger.accountBalance(ledger.accountIdByName(IN_TRANSIT)!!))
        assertEquals(false, db.investDao().getPending(pending)!!.isOpen)
    }

    @Test fun `reversing an allotment takes the units back and reopens the order`() = runBlocking {
        val (fundId, _) = fund("PPFAS Flexi Cap", FundClass.Equity)
        val pending = invest.orderFund(fundId, LocalDate.of(2026, 10, 1), 10_000.rupees, bank, "Buy (in transit)")
        val txn = invest.allot(pending, LocalDate.of(2026, 10, 2), Units.whole(100))
        ledger.reconcile(txn)
        ledger.reverse(txn, "wrong units")
        assertEquals(Units.Zero, invest.unitsHeld(fundId))
        assertEquals(true, db.investDao().getPending(pending)!!.isOpen)
        assertEquals(10_000.rupees, ledger.accountBalance(ledger.accountIdByName(IN_TRANSIT)!!))
    }

    @Test fun `only funds go through In Transit`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { invest.orderFund(holding, LocalDate.of(2026, 10, 1), 1_000.rupees, bank, "Buy INFY") }
        }
    }

    @Test fun `ELSS units are locked three years from allotment`() = runBlocking {
        val (fundId, _) = fund("Axis ELSS", FundClass.Elss)
        fundBuy(fundId, LocalDate.of(2023, 1, 10), 10, 1_000.rupees)
        fundBuy(fundId, LocalDate.of(2025, 6, 1), 10, 1_500.rupees)
        assertEquals(listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2028, 6, 1)), invest.openLots(fundId).map { it.lockUntil })

        // On 2026-02-01 only the first lot's 10 units are free.
        fundSell(fundId, LocalDate.of(2026, 2, 1), 10, 2_000.rupees)
        val error = assertThrows(LockedUnitsException::class.java) { fundSell(fundId, LocalDate.of(2026, 2, 2), 1, 200.rupees) }
        assertEquals("Some of those units are locked until 2028-06-01", error.message)
        assertEquals(Units.whole(10), invest.unitsHeld(fundId))
        fundSell(fundId, LocalDate.of(2028, 6, 2), 10, 2_500.rupees)
        assertEquals(Units.Zero, invest.unitsHeld(fundId))
    }

    @Test fun `asking for more than is held is still an insufficient-units error, not a lock error`() {
        val (fundId, _) = fund("Axis ELSS", FundClass.Elss)
        fundBuy(fundId, LocalDate.of(2025, 6, 1), 10, 1_500.rupees)
        assertThrows(InsufficientUnitsException::class.java) { fundSell(fundId, LocalDate.of(2025, 7, 1), 11, 1_800.rupees) }
    }

    @Test fun `an allotted ELSS lot is locked too`() = runBlocking {
        val (fundId, _) = fund("Axis ELSS", FundClass.Elss)
        val pending = invest.orderFund(fundId, LocalDate.of(2026, 10, 1), 5_000.rupees, bank, "Buy (in transit)")
        invest.allot(pending, LocalDate.of(2026, 10, 2), Units.whole(50))
        assertEquals(LocalDate.of(2029, 10, 2), invest.openLots(fundId).single().lockUntil)
    }

    @Test fun `debt fund gains on units bought after the cutoff are slab-taxed, whatever the holding period`() = runBlocking {
        val (fundId, _) = fund("HDFC Short Term Debt", FundClass.Debt)
        fundBuy(fundId, LocalDate.of(2023, 6, 1), 10, 1_000.rupees)
        fundSell(fundId, LocalDate.of(2026, 10, 1), 10, 1_400.rupees)
        val slab = ledger.accountIdByName("Income:Capital Gains:Slab")!!
        assertEquals((-400).rupees, ledger.accountBalance(slab))
        assertNull(ledger.accountIdByName("Income:Capital Gains:LTCG:Other"))
    }

    @Test fun `debt fund units bought before the cutoff keep the 24 month long-term rule`() = runBlocking {
        val (fundId, _) = fund("HDFC Short Term Debt", FundClass.Debt)
        fundBuy(fundId, LocalDate.of(2022, 6, 1), 10, 1_000.rupees)
        fundSell(fundId, LocalDate.of(2026, 10, 1), 10, 1_400.rupees)
        assertEquals((-400).rupees, ledger.accountBalance(ledger.accountIdByName("Income:Capital Gains:LTCG:Other")!!))
        assertNull(ledger.accountIdByName("Income:Capital Gains:Slab"))
    }

    @Test fun `an equity fund uses the 12 month rule and a non-equity hybrid the 24 month rule`() = runBlocking {
        val (equityFund, _) = fund("Flexi Cap", FundClass.Equity)
        val (hybridFund, _) = fund("Hybrid Fund", FundClass.Hybrid)
        fundBuy(equityFund, LocalDate.of(2025, 1, 1), 10, 1_000.rupees)
        fundBuy(hybridFund, LocalDate.of(2025, 1, 1), 10, 1_000.rupees)
        fundSell(equityFund, LocalDate.of(2026, 10, 1), 10, 1_300.rupees) // 21 months: long-term for equity
        fundSell(hybridFund, LocalDate.of(2026, 10, 1), 10, 1_300.rupees) // 21 months: still short-term for other
        assertEquals((-300).rupees, ledger.accountBalance(ledger.accountIdByName("Income:Capital Gains:LTCG:Equity")!!))
        assertEquals((-300).rupees, ledger.accountBalance(ledger.accountIdByName("Income:Capital Gains:STCG:Other")!!))
    }

    @Test fun `finding a fund by name reuses it instead of creating a second one`() = runBlocking {
        val first = invest.findOrCreateHolding(Instrument.MutualFund, null, name = "PPFAS Flexi Cap", fundClass = FundClass.Equity)
        val second = invest.findOrCreateHolding(Instrument.MutualFund, null, name = "PPFAS Flexi Cap")
        assertEquals(first, second)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.findOrCreateHolding(Instrument.MutualFund, null) } }
        Unit
    }

    @Test fun `a trade import can carry funds with their class`() = runBlocking {
        val equity = ledger.createAccount("Equity:Opening", AccountType.Equity)
        val rows = parseTrades(
            "date,symbol,type,units,amount,instrument,class,name\n" +
                "2023-01-10,ELSS1,buy,100,10000,mf,elss,Axis ELSS\n" +
                "2024-02-01,TCS,buy,5,20000,stock,,\n",
        ).rows
        invest.importTrades(rows, equity)
        val elss = db.investDao().holdingBySymbol("ELSS1")!!
        assertEquals(Instrument.MutualFund, elss.instrument)
        assertEquals(FundClass.Elss, elss.fundClass)
        assertEquals(LocalDate.of(2026, 1, 10), invest.openLots(elss.id).single().lockUntil)
        assertEquals(Instrument.Stock, db.investDao().holdingBySymbol("TCS")!!.instrument)
    }

    // ---- SIPs ----

    private fun sipPlan(start: LocalDate = LocalDate.of(2026, 1, 1)): Pair<String, String> = runBlocking {
        val (fundId, _) = fund("PPFAS Flexi Cap", FundClass.Equity)
        fundId to invest.createSipPlan(fundId, 10_000.rupees, 5, bank, start)
    }

    @Test fun `confirming an instalment sends the money In Transit against the plan`() = runBlocking {
        val (fundId, plan) = sipPlan()
        val order = invest.payInstalment(plan, LocalDate.of(2026, 2, 5))
        val pending = db.investDao().getPending(order)!!
        assertEquals(plan, pending.sipPlanId)
        assertEquals("2026-02-05", pending.orderDate)
        assertEquals(10_000_00L, pending.amountPaise)
        assertEquals((-10_000).rupees, ledger.accountBalance(bank))
        assertEquals(10_000.rupees, ledger.accountBalance(ledger.accountIdByName(IN_TRANSIT)!!))
        assertEquals(fundId, pending.holdingId)
    }

    @Test fun `an instalment can't be paid twice, off-schedule or after the plan ended`() = runBlocking {
        val (_, plan) = sipPlan()
        invest.payInstalment(plan, LocalDate.of(2026, 2, 5))
        assertThrows(IllegalStateException::class.java) { runBlocking { invest.payInstalment(plan, LocalDate.of(2026, 2, 5)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.payInstalment(plan, LocalDate.of(2026, 2, 6)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.payInstalment(plan, LocalDate.of(2025, 12, 5)) } }
        invest.stopSip(plan, LocalDate.of(2026, 3, 1))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.payInstalment(plan, LocalDate.of(2026, 4, 5)) } }
        Unit
    }

    @Test fun `cancelling a SIP order makes its instalment payable again`() = runBlocking {
        val (_, plan) = sipPlan()
        val order = invest.payInstalment(plan, LocalDate.of(2026, 2, 5))
        invest.cancelPending(order, LocalDate.of(2026, 2, 6))
        invest.payInstalment(plan, LocalDate.of(2026, 2, 5)) // no longer "already paid"
        assertEquals(2, db.investDao().getAllPending().size)
    }

    @Test fun `a skipped instalment can still be paid later`() = runBlocking {
        val (_, plan) = sipPlan()
        invest.skipInstalment(plan, LocalDate.of(2026, 2, 5))
        invest.skipInstalment(plan, LocalDate.of(2026, 2, 5)) // idempotent
        assertEquals(1, db.investDao().getAllSipSkips().size)
        invest.payInstalment(plan, LocalDate.of(2026, 2, 5))
        Unit
    }

    @Test fun `a SIP needs a positive amount, a real day, a fund and a sensible end`() {
        val (fundId, _) = fund("Some Fund", FundClass.Equity)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.createSipPlan(fundId, Paise.Zero, 5, bank, LocalDate.of(2026, 1, 1)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.createSipPlan(fundId, 1_000.rupees, 0, bank, LocalDate.of(2026, 1, 1)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.createSipPlan(fundId, 1_000.rupees, 5, bank, LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1)) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { invest.createSipPlan(holding, 1_000.rupees, 5, bank, LocalDate.of(2026, 1, 1)) } }
    }

    @Test fun `an order's own entry can't be edited or reversed, but reversing its cancellation reopens it`() = runBlocking {
        val (fundId, _) = fund("PPFAS Flexi Cap", FundClass.Equity)
        val order = invest.orderFund(fundId, LocalDate.of(2026, 10, 1), 5_000.rupees, bank, "Buy (in transit)")
        val orderTxn = db.investDao().getPending(order)!!.orderTransactionId
        assertThrows(IllegalLifecycleTransitionException::class.java) {
            runBlocking { ledger.edit(orderTxn, LocalDate.of(2026, 10, 1), "x", listOf(PostingLine(bank, credit = 1.rupees), PostingLine(ledger.accountIdByName(IN_TRANSIT)!!, debit = 1.rupees))) }
        }
        ledger.reconcile(orderTxn)
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { ledger.reverse(orderTxn, "oops") } }

        val cancel = invest.cancelPending(order, LocalDate.of(2026, 10, 2))
        ledger.reconcile(cancel)
        ledger.reverse(cancel, "cancelled by mistake")
        assertEquals(true, db.investDao().getPending(order)!!.isOpen)
        Unit
    }
}
