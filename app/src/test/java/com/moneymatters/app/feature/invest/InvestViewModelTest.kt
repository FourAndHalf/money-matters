package com.moneymatters.app.feature.invest

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.TradeSide
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InvestViewModelTest {

    private val today = LocalDate.of(2026, 10, 20)
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var invest: InvestRepository
    private lateinit var model: InvestViewModel
    private lateinit var bank: String

    @Before fun setUp() {
        // Direct executors so the view model's launches finish synchronously (see QuickAddViewModelTest).
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        ledger = LedgerRepository(db)
        val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24, LocalDate.of(2023, 4, 1))))
        invest = InvestRepository(db, ledger, rules)
        model = InvestViewModel(ledger, invest, rules, today = { today })
        runBlocking { bank = ledger.createAccount("HDFC Bank", AccountType.Asset) }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun fillBuy(symbol: String = "INFY", units: String = "10", amount: String = "1,000") {
        model.startTrade(TradeSide.Buy)
        model.tradeForm = model.tradeForm.copy(symbol = symbol, name = "Infosys", date = LocalDate.of(2025, 1, 10), unitsText = units, amountText = amount, accountId = bank)
    }

    @Test fun `a trade can be saved only when everything is filled in`() {
        model.startTrade(TradeSide.Buy)
        assertFalse(model.tradeForm.canSave)
        fillBuy()
        assertTrue(model.tradeForm.canSave)
        assertFalse(model.tradeForm.copy(unitsText = "abc").canSave)
        assertFalse(model.tradeForm.copy(amountText = "0").canSave)
        assertFalse(model.tradeForm.copy(accountId = null).canSave)
    }

    @Test fun `buying a new stock creates the holding, takes the money from the account and calls back`() = runBlocking {
        var saved = false
        fillBuy()
        model.saveTrade { saved = true }
        idle()
        assertTrue(saved)
        assertNull(model.error)
        val holding = db.investDao().holdingBySymbol("INFY")!!
        assertEquals(Units.whole(10), invest.unitsHeld(holding.id))
        assertEquals((-1_000).rupees, ledger.accountBalance(bank))
    }

    @Test fun `selling more than is held says so in plain words and changes nothing`() = runBlocking {
        fillBuy()
        model.saveTrade {}
        idle()
        val holding = db.investDao().holdingBySymbol("INFY")!!
        model.startTrade(TradeSide.Sell, holding.id)
        model.tradeForm = model.tradeForm.copy(date = LocalDate.of(2026, 3, 1), unitsText = "11", amountText = "1500", accountId = bank)
        var saved = false
        model.saveTrade { saved = true }
        idle()
        assertFalse(saved)
        assertEquals("That's more than you held on that date.", model.error)
        assertEquals(Units.whole(10), invest.unitsHeld(holding.id))
    }

    @Test fun `selling some units pays into the account and keeps the rest`() = runBlocking {
        fillBuy()
        model.saveTrade {}
        idle()
        val holding = db.investDao().holdingBySymbol("INFY")!!
        model.startTrade(TradeSide.Sell, holding.id)
        model.tradeForm = model.tradeForm.copy(date = LocalDate.of(2026, 3, 1), unitsText = "4", amountText = "600", accountId = bank)
        model.saveTrade {}
        idle()
        assertEquals(Units.whole(6), invest.unitsHeld(holding.id))
        assertEquals((-1_000 + 600).rupees, ledger.accountBalance(bank))
    }

    @Test fun `a manual price is stored as Manual and the form closes`() = runBlocking {
        fillBuy()
        model.saveTrade {}
        idle()
        val holding = db.investDao().holdingBySymbol("INFY")!!
        model.startPrice(holding.id)
        assertFalse(model.priceForm!!.canSave)
        model.priceForm = model.priceForm!!.copy(priceText = "1,512.35")
        var saved = false
        model.savePrice { saved = true }
        idle()
        assertTrue(saved)
        assertNull(model.priceForm)
        val price = invest.latestPrice(holding.id)!!
        assertEquals(15_123_500L, price.price)
        assertEquals(PriceStatus.Manual, price.status)
    }

    @Test fun `settlement accounts leave out holdings, investments and archived accounts`() = runBlocking {
        val cash = ledger.createAccount("Cash", AccountType.Asset)
        val old = ledger.createAccount("Old account", AccountType.Asset)
        ledger.archiveAccount(old)
        ledger.createAccount("Mutual fund", AccountType.Asset, kind = com.moneymatters.app.core.ledger.AccountKind.Investment)
        ledger.createAccount("Expenses:Dining", AccountType.Expense)
        fillBuy()
        model.saveTrade {}
        idle()
        assertEquals(listOf("Cash", "HDFC Bank"), settlementAccountsIn(ledger.snapshot().first()).map { it.name })
        assertNotNull(cash)
    }

    @Test fun `a fund buy with no units goes In Transit and can then be allotted from the form`() = runBlocking {
        model.startTrade(TradeSide.Buy, instrument = Instrument.MutualFund)
        model.tradeForm = model.tradeForm.copy(name = "PPFAS Flexi Cap", date = LocalDate.of(2026, 10, 19), amountText = "10,000", accountId = bank)
        assertTrue(model.tradeForm.inTransit)
        assertTrue(model.tradeForm.canSave)
        model.saveTrade {}
        idle()
        assertEquals((-10_000).rupees, ledger.accountBalance(bank))
        val pending = db.investDao().getAllPending().single()
        assertTrue(pending.isOpen)

        model.startAllot(pending.id)
        assertFalse(model.allotForm!!.canSave)
        model.allotForm = model.allotForm!!.copy(date = LocalDate.of(2026, 10, 20), unitsText = "123.456")
        var saved = false
        model.saveAllot { saved = true }
        idle()
        assertTrue(saved)
        val holding = db.investDao().getAllHoldings().single()
        assertEquals(com.moneymatters.app.core.money.parseUnits("123.456"), invest.unitsHeld(holding.id))
        assertNull(model.allotForm)
    }

    @Test fun `a fund buy with units is allotted straight away, and a fund needs a name`() = runBlocking {
        model.startTrade(TradeSide.Buy, instrument = Instrument.MutualFund)
        model.tradeForm = model.tradeForm.copy(date = LocalDate.of(2026, 10, 19), unitsText = "10", amountText = "1000", accountId = bank)
        assertFalse(model.tradeForm.canSave) // no name yet
        model.tradeForm = model.tradeForm.copy(name = "Index Fund", fundClass = com.moneymatters.app.core.invest.FundClass.Equity)
        assertFalse(model.tradeForm.inTransit)
        model.saveTrade {}
        idle()
        assertEquals(0, db.investDao().getAllPending().size)
        assertEquals(Units.whole(10), invest.unitsHeld(db.investDao().getAllHoldings().single().id))
    }

    @Test fun `selling locked ELSS units explains the lock`() = runBlocking {
        model.startTrade(TradeSide.Buy, instrument = Instrument.MutualFund)
        model.tradeForm = model.tradeForm.copy(
            name = "Axis ELSS", fundClass = com.moneymatters.app.core.invest.FundClass.Elss, date = LocalDate.of(2026, 1, 1),
            unitsText = "10", amountText = "1000", accountId = bank,
        )
        model.saveTrade {}
        idle()
        val holding = db.investDao().getAllHoldings().single()
        model.startTrade(TradeSide.Sell, holding.id, Instrument.MutualFund)
        model.tradeForm = model.tradeForm.copy(date = LocalDate.of(2027, 1, 1), unitsText = "5", amountText = "600", accountId = bank)
        model.saveTrade {}
        idle()
        assertEquals("Some of those units are locked until 2029-01-01.", model.error)
        assertEquals(Units.whole(10), invest.unitsHeld(holding.id))
    }

    @Test fun `cancelling an order from the portfolio returns the money`() = runBlocking {
        val fund = invest.findOrCreateHolding(Instrument.MutualFund, null, name = "Flexi Cap", fundClass = com.moneymatters.app.core.invest.FundClass.Equity)
        val pending = invest.orderFund(fund, LocalDate.of(2026, 10, 19), 3_000.rupees, bank, "Buy (in transit)")
        model.cancelOrder(pending)
        idle()
        assertEquals(0.rupees, ledger.accountBalance(bank))
        assertFalse(db.investDao().getPending(pending)!!.isOpen)
    }

    @Test fun `a deposit form needs a name, principal, rate, sensible dates and a source`() {
        model.startDeposit()
        val form = model.depositForm!!
        assertFalse(form.canSave)
        val filled = form.copy(name = "HDFC FD", principalText = "1,00,000", rateText = "7.25", accountId = bank)
        assertTrue(filled.canSave)
        assertFalse(filled.copy(rateText = "0").canSave)
        assertFalse(filled.copy(maturity = filled.start).canSave)
        assertFalse(filled.copy(accountId = null).canSave)
        assertTrue(filled.copy(accountId = null, existing = true).canSave)
        assertFalse(filled.copy(penaltyText = "x").canSave)
    }

    @Test fun `saving a deposit opens it, then interest can be posted and it can be matured`() = runBlocking {
        model.startDeposit()
        model.depositForm = model.depositForm!!.copy(
            name = "HDFC FD", principalText = "100000", rateText = "7", start = LocalDate.of(2026, 1, 1), maturity = LocalDate.of(2026, 12, 31), accountId = bank,
        )
        var saved = false
        model.saveDeposit { saved = true }
        idle()
        assertTrue(saved)
        val fd = db.investDao().getAllFixedDeposits().single()
        assertEquals((-100_000).rupees, ledger.accountBalance(bank))

        model.postInterest(fd.accountId)
        idle()
        assertTrue(ledger.accountBalance(fd.accountId).value > 100_000_00)

        model.startClose(fd.accountId)
        assertFalse(model.closeForm!!.canSave)
        model.closeForm = model.closeForm!!.copy(date = LocalDate.of(2026, 12, 31), toAccountId = bank)
        model.saveClose {}
        idle()
        assertEquals(Paise.Zero, ledger.accountBalance(fd.accountId))
        assertTrue(ledger.accountBalance(bank).value > 0)
        assertNotNull(db.investDao().getFixedDeposit(fd.accountId)!!.closedOn)
    }

    @Test fun `an existing deposit is recorded against opening equity and leaves the bank alone`() = runBlocking {
        model.startDeposit()
        model.depositForm = model.depositForm!!.copy(name = "Old FD", principalText = "50000", rateText = "6.5", start = LocalDate.of(2025, 6, 1), maturity = LocalDate.of(2027, 6, 1), existing = true)
        model.saveDeposit {}
        idle()
        assertEquals(Paise.Zero, ledger.accountBalance(bank))
        assertEquals(1, db.investDao().getAllFixedDeposits().size)
    }

    @Test fun `closing with a TDS that exceeds the deposit explains why it failed`() = runBlocking {
        val fd = invest.openDeposit("FD", 10_000.rupees, 700, com.moneymatters.app.core.invest.Compounding.Simple, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), bank)
        model.startClose(fd)
        model.closeForm = model.closeForm!!.copy(date = LocalDate.of(2026, 1, 1), toAccountId = bank, tdsText = "99999")
        model.saveClose {}
        idle()
        assertEquals("TDS can't be as much as the deposit", model.error)
        assertNull(db.investDao().getFixedDeposit(fd)!!.closedOn)
    }

    @Test fun `a SIP form needs a fund, an amount, a day and an account`() {
        model.startSip()
        val form = model.sipForm!!
        assertFalse(form.canSave)
        val filled = form.copy(holdingId = "h", amountText = "10,000", dayText = "5", accountId = bank)
        assertTrue(filled.canSave)
        assertFalse(filled.copy(dayText = "32").canSave)
        assertFalse(filled.copy(amountText = "0").canSave)
        assertFalse(filled.copy(holdingId = null).canSave)
    }

    @Test fun `saving a SIP, paying an instalment, skipping one and stopping it all update the books`() = runBlocking {
        val fund = invest.findOrCreateHolding(Instrument.MutualFund, null, name = "Flexi Cap", fundClass = com.moneymatters.app.core.invest.FundClass.Equity)
        model.startSip()
        model.sipForm = model.sipForm!!.copy(holdingId = fund, amountText = "10000", dayText = "5", accountId = bank, start = LocalDate.of(2026, 9, 1))
        var saved = false
        model.saveSip { saved = true }
        idle()
        assertTrue(saved)
        val plan = db.investDao().getAllSipPlans().single()

        model.payInstalment(plan.id, LocalDate.of(2026, 9, 5))
        idle()
        assertEquals((-10_000).rupees, ledger.accountBalance(bank))
        model.skipInstalment(plan.id, LocalDate.of(2026, 10, 5))
        idle()
        assertEquals(1, db.investDao().getAllSipSkips().size)
        model.stopSip(plan.id)
        idle()
        assertEquals(today.toString(), db.investDao().getSipPlan(plan.id)!!.endDate)
        assertNull(model.error)
    }

    @Test fun `paying an instalment that isn't due reports it`() = runBlocking {
        val fund = invest.findOrCreateHolding(Instrument.MutualFund, null, name = "Flexi Cap", fundClass = com.moneymatters.app.core.invest.FundClass.Equity)
        val plan = invest.createSipPlan(fund, 10_000.rupees, 5, bank, LocalDate.of(2026, 9, 1))
        model.payInstalment(plan, LocalDate.of(2026, 9, 6))
        idle()
        assertEquals("Couldn't update the SIP.", model.error)
        assertEquals(Paise.Zero, ledger.accountBalance(bank))
    }

    private class StubFetcher(val responses: Map<String, com.moneymatters.app.core.invest.FetchResult>) : com.moneymatters.app.core.invest.PriceFetcher {
        override suspend fun fetch(url: String) = responses[url] ?: com.moneymatters.app.core.invest.FetchResult.Failed(404, "File not found (404)")
    }

    private fun modelWith(fetcher: com.moneymatters.app.core.invest.PriceFetcher, prefs: com.moneymatters.app.core.invest.PriceSourcePrefs? = null): InvestViewModel {
        val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24)))
        val sources = com.moneymatters.app.core.invest.PriceSources("https://nse.test/{yyyyMMdd}.zip", "https://amfi.test/NAV.txt")
        return InvestViewModel(
            ledger, invest, rules, today = { today },
            priceUpdater = com.moneymatters.app.core.invest.PriceUpdater(invest, fetcher, { sources }, { today }),
            sourcePrefs = prefs,
        )
    }

    @Test fun `refreshing downloads prices for held funds and records the attempt`() = runBlocking {
        val fund = invest.findOrCreateHolding(Instrument.MutualFund, null, "INF000000001", "Fund One", com.moneymatters.app.core.invest.FundClass.Equity)
        invest.buy(fund, LocalDate.of(2025, 1, 1), Units.whole(10), com.moneymatters.app.core.money.UnitPrice.rupees(100), 1_000.rupees, bank, "Buy")
        val nav = "Scheme Code;a;b;Scheme Name;Plan;Option;Net Asset Value;Date\n1;INF000000001;-;Fund One;Direct;Growth;81.2345;22-Oct-2026"
        val m = modelWith(StubFetcher(mapOf("https://amfi.test/NAV.txt" to com.moneymatters.app.core.invest.FetchResult.Ok(nav.toByteArray()))))
        m.refreshPrices()
        idle()
        assertFalse(m.isRefreshing)
        assertEquals(812_345L, invest.latestPrice(fund)!!.price)
        assertEquals(1, db.investDao().getAllFetchLog().count { it.source == "amfi" && it.ok })
    }

    @Test fun `without an updater, refresh does nothing`() {
        model.refreshPrices()
        assertFalse(model.isRefreshing)
    }

    @Test fun `source addresses are saved and read back`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = com.moneymatters.app.core.invest.PriceSourcePrefs(context.getSharedPreferences("vm_sources", android.content.Context.MODE_PRIVATE))
        val m = modelWith(StubFetcher(emptyMap()), prefs)
        assertEquals(com.moneymatters.app.core.invest.PriceSources.Default, m.sources)
        val changed = com.moneymatters.app.core.invest.PriceSources("https://x/{yyyyMMdd}.zip", "https://y/nav.txt")
        m.saveSources(changed)
        assertEquals(changed, m.sources)
        assertEquals(changed, prefs.sources())
    }

    @Test fun `confirming and resolving prices go through to the books`() = runBlocking {
        fillBuy()
        model.saveTrade {}
        idle()
        val holding = db.investDao().getAllHoldings().single()
        invest.recordPrice(holding.id, LocalDate.of(2026, 10, 20), com.moneymatters.app.core.money.UnitPrice.rupees(100), "manual", PriceStatus.Manual)
        invest.recordPrice(holding.id, LocalDate.of(2026, 10, 20), com.moneymatters.app.core.money.UnitPrice.rupees(120), "nse", PriceStatus.Mismatch)
        val manual = db.investDao().pricesFor(holding.id, "2026-10-20").first { it.source == "manual" }
        db.investDao().updatePrice(manual.copy(status = PriceStatus.Mismatch))
        model.resolveMismatch(holding.id, "2026-10-20", keepDownloaded = true)
        idle()
        assertEquals(listOf("nse" to PriceStatus.Verified), db.investDao().pricesFor(holding.id, "2026-10-20").map { it.source to it.status })

        invest.recordPrice(holding.id, LocalDate.of(2026, 10, 21), com.moneymatters.app.core.money.UnitPrice.rupees(300), "nse", PriceStatus.Suspicious)
        val suspicious = db.investDao().pricesFor(holding.id, "2026-10-21").single()
        model.confirmPrice(suspicious.id)
        idle()
        assertEquals(PriceStatus.Manual, db.investDao().getPrice(suspicious.id)!!.status)
        assertNull(model.error)
    }

    @Test fun `a saved trade can be reopened, corrected and saved again`() = runBlocking {
        fillBuy()
        model.saveTrade {}
        idle()
        val buyId = ledger.snapshot().first().transactions.first { it.payee == "Buy INFY" }.id
        var ready = false
        model.startEditTrade(buyId) { ready = true }
        idle()
        assertTrue(ready)
        val form = model.tradeForm
        assertEquals(buyId, form.editingTransactionId)
        assertEquals("10", form.unitsText)
        assertEquals("1000", form.amountText)
        assertEquals(bank, form.accountId)
        assertEquals(LocalDate.of(2025, 1, 10), form.date)
        model.tradeForm = form.copy(unitsText = "8", amountText = "960")
        var saved = false
        model.saveTrade { saved = true }
        idle()
        assertTrue(saved)
        assertNull(model.error)
        val holding = db.investDao().holdingBySymbol("INFY")!!
        assertEquals(Units.whole(8), invest.unitsHeld(holding.id))
        assertEquals(1, ledger.snapshot().first().transactions.count { it.payee == "Buy INFY" })
        assertEquals((-960).rupees, ledger.accountBalance(bank))
    }

    @Test fun `deleting a holding clears it everywhere, and a refusal is explained and changes nothing`() = runBlocking {
        fillBuy()
        model.saveTrade {}
        idle()
        val holding = db.investDao().holdingBySymbol("INFY")!!
        val buy = ledger.snapshot().first().transactions.first { it.payee == "Buy INFY" }
        ledger.reconcile(buy.id)
        var done = false
        model.deleteHolding(holding.id) { done = true }
        idle()
        assertFalse(done)
        assertNotNull(model.manageError)
        assertNotNull(db.investDao().getHolding(holding.id))
        assertEquals((-1_000).rupees, ledger.accountBalance(bank))

        // A draft or posted entry goes without a fuss.
        fillBuy(symbol = "TCS", amount = "2,000")
        model.saveTrade {}
        idle()
        val tcs = db.investDao().holdingBySymbol("TCS")!!
        model.deleteHolding(tcs.id) { done = true }
        idle()
        assertTrue(done)
        assertNull(model.manageError)
        assertNull(db.investDao().getHolding(tcs.id))
        assertEquals((-1_000).rupees, ledger.accountBalance(bank))
    }

    @Test fun `a bond is saved with its coupon and maturity, and its coupon is booked as interest`() = runBlocking {
        model.startTrade(TradeSide.Buy, instrument = Instrument.Bond)
        model.tradeForm = model.tradeForm.copy(name = "REC 7.5% 2030", couponText = "7.5", maturity = LocalDate.of(2030, 3, 15), date = LocalDate.of(2025, 4, 1), unitsText = "10", amountText = "10,000", accountId = bank)
        assertTrue(model.tradeForm.canSave)
        model.saveTrade {}
        idle()
        val bond = db.investDao().getAllHoldings().single()
        assertEquals(Instrument.Bond, bond.instrument)
        assertEquals(750, bond.couponBps)
        assertEquals("2030-03-15", bond.maturityDate)
        model.startDividend(bond.id)
        model.dividendForm = model.dividendForm!!.copy(date = LocalDate.of(2025, 10, 1), netText = "375", accountId = bank)
        model.saveDividend("") {}
        idle()
        assertEquals(com.moneymatters.app.core.invest.CorporateActionKind.Coupon, db.investDao().getAllCorporateActions().single().kind)
        assertEquals(375.rupees, db.ledgerDao().findAccountByName("Income:Interest")!!.let { Paise(-ledger.accountBalance(it.id).value) })
        assertFalse(model.tradeForm.copy(couponText = "seven").canSave)
    }

    @Test fun `a holding's details, a deposit's terms and a SIP's amount can all be reopened and saved`() = runBlocking {
        fillBuy()
        model.saveTrade {}
        idle()
        val holding = db.investDao().holdingBySymbol("INFY")!!
        model.startEditHolding(holding.id) {}
        idle()
        assertEquals("INFY", model.holdingEditForm!!.symbol)
        assertEquals("Infosys", model.holdingEditForm!!.name)
        model.holdingEditForm = model.holdingEditForm!!.copy(name = "Infosys Ltd", isin = "INE009A01021")
        model.saveHoldingEdit {}
        idle()
        assertNull(model.holdingEditForm)
        assertEquals("Infosys Ltd (INFY)", db.ledgerDao().getAccount(holding.accountId)!!.name)
        assertEquals("INE009A01021", db.investDao().getHolding(holding.id)!!.isin)

        val acct = invest.openDeposit("SBI FD", 1_00_000.rupees, 700, com.moneymatters.app.core.invest.Compounding.Quarterly, today.minusMonths(1), today.plusMonths(11), bank)
        model.startEditDeposit(acct) {}
        idle()
        assertEquals("7", model.depositForm!!.rateText.removeSuffix(".00"))
        assertEquals("100000", model.depositForm!!.principalText)
        model.depositForm = model.depositForm!!.copy(rateText = "7.5", name = "SBI FD 1")
        model.saveDeposit {}
        idle()
        assertEquals(750, db.investDao().getFixedDeposit(acct)!!.rateBps)
        assertEquals("SBI FD 1", db.ledgerDao().getAccount(acct)!!.name)

        val fund = invest.findOrCreateHolding(Instrument.MutualFund, null, name = "Index Fund", fundClass = com.moneymatters.app.core.invest.FundClass.Equity)
        val plan = invest.createSipPlan(fund, 5_000.rupees, 5, bank, today.minusMonths(1))
        model.startEditSip(plan) {}
        idle()
        assertEquals("5000", model.sipForm!!.amountText)
        model.sipForm = model.sipForm!!.copy(amountText = "6,000", dayText = "12")
        model.saveSip {}
        idle()
        assertEquals(6_000_00L, db.investDao().getSipPlan(plan)!!.amountPaise)
        assertEquals(12, db.investDao().getSipPlan(plan)!!.dayOfMonth)
        model.deleteSip(plan) {}
        idle()
        assertNull(db.investDao().getSipPlan(plan))
    }
}
