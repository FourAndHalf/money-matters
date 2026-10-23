package com.moneymatters.app.core.invest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.ClosedPeriodEntity
import com.moneymatters.app.core.ledger.IllegalLifecycleTransitionException
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.feature.books.buildBooks
import com.moneymatters.app.feature.invest.buildInvest
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class InvestEditDeleteTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24, LocalDate.of(2023, 4, 1))))
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var invest: InvestRepository
    private lateinit var bank: String

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        invest = InvestRepository(db, ledger, rules)
        bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
        val equity = ledger.createAccount("Equity:Opening", AccountType.Equity)
        ledger.postTransaction(LocalDate.of(2025, 1, 1), "Opening balance", listOf(PostingLine(bank, debit = 10_00_000.rupees), PostingLine(equity, credit = 10_00_000.rupees)))
        Unit
    }

    private suspend fun books() = buildBooks(ledger.snapshot().first(), today, invest = invest.snapshot().first())
    private suspend fun balance() = ledger.accountBalance(bank)

    /** A holding with every kind of entry: buys, a sale, a dividend, a split, a price, a SIP and an order In Transit. */
    private suspend fun busyFund(): String {
        val h = invest.findOrCreateHolding(Instrument.Stock, "INFY", name = "Infosys")
        invest.buy(h, LocalDate.of(2025, 2, 1), Units.whole(10), UnitPrice.rupees(1_000), 10_000.rupees, bank, "Buy INFY")
        invest.buy(h, LocalDate.of(2025, 3, 1), Units.whole(10), UnitPrice.rupees(1_100), 11_000.rupees, bank, "Buy INFY")
        invest.sell(h, LocalDate.of(2025, 9, 1), Units.whole(5), UnitPrice.rupees(1_500), 7_500.rupees, bank, "Sell INFY")
        invest.recordDividend(h, LocalDate.of(2025, 10, 1), 200.rupees, 20.rupees, bank, "Dividend INFY")
        invest.recordPrice(h, today, UnitPrice.rupees(1_600), "manual", PriceStatus.Manual)
        invest.applyCorporateAction(h, RestateKind.Split, Units.whole(2), LocalDate.of(2026, 1, 1))
        return h
    }

    @Test fun `deleting a holding removes every entry from the books and puts every balance back`() = runBlocking {
        val before = books()
        val h = busyFund()
        assertTrue(balance() != 10_00_000.rupees)
        invest.deleteHolding(h)

        assertEquals(10_00_000.rupees, balance())
        val l = ledger.snapshot().first()
        val i = invest.snapshot().first()
        // Only the opening balance is left: no trades, dividend, split, gain or TDS entries anywhere.
        assertEquals(listOf("Opening balance"), l.transactions.map { it.payee })
        assertTrue(l.accounts.none { it.name.startsWith("Infosys") })
        assertTrue(i.holdings.isEmpty() && i.lots.isEmpty() && i.consumptions.isEmpty() && i.prices.isEmpty() && i.corporateActions.isEmpty())
        val after = books()
        assertEquals(before.accounts.netWorth, after.accounts.netWorth)
        assertEquals(1, after.transactions.groups.sumOf { it.items.size })
        // Income and tax-receivable accounts the holding's entries touched have nothing left either.
        for (a in l.accounts.filter { it.type == AccountType.Income || it.name == TDS_RECEIVABLE }) assertEquals(Paise.Zero, ledger.accountBalance(a.id))
        // The audit trail keeps a record of every deletion.
        assertEquals(5, l.audit.count { it.action == "Deleted" })
    }

    @Test fun `deleting a holding is refused, changing nothing, when one of its entries is reconciled`() = runBlocking {
        val h = busyFund()
        val buy = ledger.snapshot().first().transactions.first { it.payee == "Buy INFY" }
        ledger.reconcile(ledger.snapshot().first().transactions.first { it.payee == "Sell INFY" }.id)
        val txnsBefore = ledger.snapshot().first().transactions.size
        val balanceBefore = balance()
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { invest.deleteHolding(h) } }
        assertEquals(txnsBefore, ledger.snapshot().first().transactions.size)
        assertEquals(balanceBefore, balance())
        assertNotNull(db.investDao().getHolding(h))
        assertNotNull(buy)
    }

    @Test fun `deleting a holding is refused when an entry falls in a closed month`() = runBlocking {
        val h = busyFund()
        db.ledgerDao().insertClosedPeriod(ClosedPeriodEntity("2025-02-01", "2025-02-28", 0L, "0".repeat(64), "h", ""))
        assertThrows(Exception::class.java) { runBlocking { invest.deleteHolding(h) } }
        assertNotNull(db.investDao().getHolding(h))
    }

    @Test fun `a sale can be deleted, handing its units back, and a buy only once nothing sold from it`() = runBlocking {
        val h = invest.findOrCreateHolding(Instrument.Stock, "TCS", name = "TCS")
        invest.buy(h, LocalDate.of(2025, 1, 10), Units.whole(10), UnitPrice.rupees(100), 1_000.rupees, bank, "Buy TCS")
        invest.sell(h, LocalDate.of(2025, 6, 1), Units.whole(4), UnitPrice.rupees(150), 600.rupees, bank, "Sell TCS")
        val snap = ledger.snapshot().first()
        val buyId = snap.transactions.first { it.payee == "Buy TCS" }.id
        val sellId = snap.transactions.first { it.payee == "Sell TCS" }.id
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { invest.deleteTrade(buyId) } }
        invest.deleteTrade(sellId)
        assertEquals(Units.whole(10), invest.unitsHeld(h))
        assertEquals(1_000.rupees, ledger.accountBalance(db.investDao().getHolding(h)!!.accountId))
        invest.deleteTrade(buyId)
        assertEquals(Units.Zero, invest.unitsHeld(h))
        assertEquals(10_00_000.rupees, balance())
    }

    @Test fun `editing a trade rebooks it with the new numbers, or changes nothing if the new numbers can't work`() = runBlocking {
        val h = invest.findOrCreateHolding(Instrument.Stock, "TCS", name = "TCS")
        invest.buy(h, LocalDate.of(2025, 1, 10), Units.whole(10), UnitPrice.rupees(100), 1_000.rupees, bank, "Buy TCS")
        val buyId = ledger.snapshot().first().transactions.first { it.payee == "Buy TCS" }.id
        invest.editTrade(buyId, LocalDate.of(2025, 1, 12), Units.whole(8), 960.rupees, bank)
        assertEquals(Units.whole(8), invest.unitsHeld(h))
        assertEquals(10_00_000.rupees - 960.rupees, balance())
        assertEquals("2025-01-12", db.investDao().lotsForHolding(h).single().acquiredOn)
        assertEquals(1, ledger.snapshot().first().transactions.count { it.payee == "Buy TCS" })

        val newBuy = ledger.snapshot().first().transactions.first { it.payee == "Buy TCS" }.id
        invest.sell(h, LocalDate.of(2025, 6, 1), Units.whole(5), UnitPrice.rupees(150), 750.rupees, bank, "Sell TCS")
        // Cutting the buy to 3 units would leave the later sale of 5 short: refused, and the buy is as it was.
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { invest.editTrade(newBuy, LocalDate.of(2025, 1, 12), Units.whole(3), 360.rupees, bank) } }
        assertEquals(Units.whole(3), invest.unitsHeld(h))
        assertEquals(1, ledger.snapshot().first().transactions.count { it.payee == "Buy TCS" })
        assertEquals(LocalDate.of(2025, 1, 12).toString(), db.investDao().lotsForHolding(h).single().acquiredOn)
    }

    @Test fun `a holding can be renamed and reclassified, but not onto another holding's symbol`() = runBlocking {
        val a = invest.findOrCreateHolding(Instrument.Stock, "TCS", name = "Tata Consultancy")
        val b = invest.findOrCreateHolding(Instrument.MutualFund, null, name = "Flexi Cap", fundClass = FundClass.Equity)
        invest.updateHolding(b, "Parag Parikh Flexi Cap", null, "INF879O01027", FundClass.Debt, 365)
        val fund = db.investDao().getHolding(b)!!
        assertEquals(FundClass.Debt, fund.fundClass)
        assertEquals("INF879O01027", fund.isin)
        assertEquals("Parag Parikh Flexi Cap", db.ledgerDao().getAccount(fund.accountId)!!.name)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { invest.updateHolding(b, "Parag Parikh Flexi Cap", "TCS", null, FundClass.Debt, 365) } }
        assertNotNull(a)
    }

    @Test fun `a deposit's terms and its principal can be edited until interest is booked, and it can be deleted`() = runBlocking {
        val acct = invest.openDeposit("SBI FD", 1_00_000.rupees, 700, Compounding.Quarterly, today.minusMonths(2), today.plusMonths(10), bank)
        invest.updateDeposit(acct, "SBI FD 1", 1_50_000.rupees, 725, Compounding.Monthly, today.minusMonths(3), today.plusMonths(12), true, 100)
        val d = db.investDao().getFixedDeposit(acct)!!
        assertEquals(1_50_000_00L, d.principalPaise)
        assertEquals(725, d.rateBps)
        assertEquals(1_50_000.rupees, ledger.accountBalance(acct))
        assertEquals(10_00_000.rupees - 1_50_000.rupees, balance())
        assertEquals("SBI FD 1", db.ledgerDao().getAccount(acct)!!.name)

        invest.accrueInterest(acct, today)
        assertThrows(IllegalLifecycleTransitionException::class.java) {
            runBlocking { invest.updateDeposit(acct, "SBI FD 1", 2_00_000.rupees, 725, Compounding.Monthly, today.minusMonths(3), today.plusMonths(12), true, 100) }
        }
        // Rate and maturity still change after interest is booked.
        invest.updateDeposit(acct, "SBI FD 1", 1_50_000.rupees, 700, Compounding.Monthly, today.minusMonths(3), today.plusMonths(14), true, 100)

        invest.deleteDeposit(acct)
        assertNull(db.investDao().getFixedDeposit(acct))
        assertNull(db.ledgerDao().getAccount(acct))
        assertEquals(10_00_000.rupees, balance())
        assertEquals(listOf("Opening balance"), ledger.snapshot().first().transactions.map { it.payee })
    }

    @Test fun `a SIP's amount and day can change and it can be deleted without touching its orders`() = runBlocking {
        val fund = invest.findOrCreateHolding(Instrument.MutualFund, null, name = "Index Fund", fundClass = FundClass.Equity)
        val plan = invest.createSipPlan(fund, 5_000.rupees, 5, bank, today.minusMonths(2))
        invest.payInstalment(plan, LocalDate.of(2026, 9, 5))
        invest.updateSip(plan, 7_500.rupees, 10, bank)
        assertEquals(7_500_00L, db.investDao().getSipPlan(plan)!!.amountPaise)
        assertEquals(10, db.investDao().getSipPlan(plan)!!.dayOfMonth)
        invest.deleteSip(plan)
        assertNull(db.investDao().getSipPlan(plan))
        val order = db.investDao().getAllPending().single()
        assertNull(order.sipPlanId)
        assertTrue(order.isOpen)
    }

    @Test fun `a bond is held like any other, pays coupons as interest income and counts as debt`() = runBlocking {
        val bond = invest.findOrCreateHolding(Instrument.Bond, null, name = "REC 7.5% 2030", couponBps = 750, maturityDate = LocalDate.of(2030, 3, 15))
        invest.buy(bond, LocalDate.of(2025, 4, 1), Units.whole(10), UnitPrice.rupees(1_000), 10_000.rupees, bank, "Buy REC 7.5% 2030")
        invest.recordDividend(bond, LocalDate.of(2025, 10, 1), 375.rupees, Paise.Zero, bank, "Coupon REC", kind = CorporateActionKind.Coupon)
        assertEquals(750, db.investDao().getHolding(bond)!!.couponBps)
        val interest = db.ledgerDao().findAccountByName("Income:Interest")!!.id
        assertEquals(Paise(-37_500), ledger.accountBalance(interest))
        val state = buildInvest(ledger.snapshot().first(), invest.snapshot().first(), today, rules)
        assertEquals(AssetClass.Debt, db.investDao().getHolding(bond)!!.assetClass)
        assertEquals("7.50% coupon", state.holdings.single().classLabel)
        val detail = state.details.getValue(bond)
        assertEquals("7.50%", detail.bond!!.couponLabel)
        assertEquals("Coupon", detail.actions.single().label)
        // Deleting the coupon removes its income entry; deleting the bond removes the rest.
        invest.deleteIncome(detail.actions.single().incomeTransactionId!!)
        assertEquals(Paise.Zero, ledger.accountBalance(interest))
        invest.deleteHolding(bond)
        assertEquals(10_00_000.rupees, balance())
        assertFalse(invest.snapshot().first().holdings.any { it.id == bond })
    }
}
