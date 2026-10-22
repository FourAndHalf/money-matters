package com.moneymatters.app.feature.invest

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.TradeSide
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
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
        invest = InvestRepository(db, ledger)
        model = InvestViewModel(ledger, invest, CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24))), today = { today })
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

    @Test fun `loading a CSV previews it and only a clean file can be imported`() = runBlocking {
        model.loadCsv("date,symbol,type,units,amount\n2025-01-10,TCS,buy,10,40000\n2026-03-01,TCS,sell,4,18000")
        idle()
        assertTrue(model.canImport)
        assertEquals(Units.whole(6), model.importPreview!!.symbols.single().unitsHeld)

        model.loadCsv("date,symbol,type,units,amount\n2025-01-10,TCS,buy,10,40000\n2026-03-01,TCS,sell,40,18000")
        idle()
        assertFalse(model.canImport)
        assertEquals(1, model.importPreview!!.errors.size)

        model.loadCsv("date,symbol,type,units,amount\nbad,TCS,buy,10,40000")
        idle()
        assertFalse(model.canImport)
        assertEquals(1, model.importParse!!.errors.size)
    }

    @Test fun `confirming an import rebuilds the holdings against opening equity and leaves the bank alone`() = runBlocking {
        model.loadCsv("date,symbol,type,units,amount,name\n2025-01-10,TCS,buy,10,40000,Tata Consultancy\n2026-03-01,TCS,sell,4,18000,")
        idle()
        model.confirmImport()
        idle()
        assertEquals(2, model.importDone)
        assertNull(model.error)
        val holding = db.investDao().holdingBySymbol("TCS")!!
        assertEquals(Units.whole(6), invest.unitsHeld(holding.id))
        assertEquals(0.rupees, ledger.accountBalance(bank))
        assertNotNull(ledger.accountIdByName("Equity:Opening"))
        assertNotNull(ledger.accountIdByName("Tata Consultancy (TCS)"))
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
}
