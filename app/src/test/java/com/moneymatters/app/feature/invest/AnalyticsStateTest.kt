package com.moneymatters.app.feature.invest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.Compounding
import com.moneymatters.app.core.invest.FundClass
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.RestateKind
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalyticsStateTest {

    private val today = LocalDate.of(2026, 10, 20)
    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24, LocalDate.of(2023, 4, 1))))
    private lateinit var ledger: LedgerRepository
    private lateinit var invest: InvestRepository
    private lateinit var bank: String

    @Before fun setUp() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        invest = InvestRepository(db, ledger, rules)
        runBlocking { bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank) }
    }

    private fun stock(symbol: String, class_: FundClass? = null, instrument: Instrument = Instrument.Stock): String = runBlocking {
        val account = ledger.createAccount("$symbol holding", AccountType.Asset, kind = AccountKind.Investment)
        invest.addHolding(account, instrument, symbol = symbol, fundClass = class_)
    }

    private fun buy(id: String, date: LocalDate, units: Long, amount: Long) = runBlocking {
        invest.buy(id, date, Units.whole(units), UnitPrice.rupees(amount / units), amount.rupees, bank, "Buy")
    }

    private fun sell(id: String, date: LocalDate, units: Long, amount: Long) = runBlocking {
        invest.sell(id, date, Units.whole(units), UnitPrice.rupees(amount / units), amount.rupees, bank, "Sell")
    }

    private fun price(id: String, rupees: Long) = runBlocking { invest.recordPrice(id, today, UnitPrice.rupees(rupees), "manual", PriceStatus.Manual) }

    private fun state(target: Int? = null) = runBlocking { buildInvest(ledger.snapshot().first(), invest.snapshot().first(), today, rules, targetEquityPercent = target) }

    // ---- Allocation and drift ----

    @Test fun `stocks count as equity and a deposit as debt, at market value`() {
        val infy = stock("INFY")
        buy(infy, LocalDate.of(2025, 1, 10), 10, 1_000)
        price(infy, 150)
        runBlocking { invest.openDeposit("SBI FD", 1_500.rupees, 700, Compounding.Quarterly, today, today.plusYears(1), bank) }
        val rows = state().analytics.allocation
        assertEquals(listOf("Equity", "Debt & FDs"), rows.map { it.label })
        assertEquals(1_500.rupees, rows[0].value)
        assertEquals(1_500.rupees, rows[1].value)
        assertEquals(0.5, rows[0].fraction, 1e-9)
    }

    @Test fun `equity five or more points off target raises the drift message, closer does not`() {
        val infy = stock("INFY")
        buy(infy, LocalDate.of(2025, 1, 10), 10, 1_000)
        price(infy, 100)
        runBlocking { invest.openDeposit("SBI FD", 1_000.rupees, 700, Compounding.Quarterly, today, today.plusYears(1), bank) }
        assertEquals("Equity is 10.0 points below your 60% target", state(60).analytics.driftMessage)
        assertEquals("Equity is 5.0 points above your 45% target", state(45).analytics.driftMessage)
        assertNull(state(54).analytics.driftMessage)
        assertNull(state(null).analytics.driftMessage)
        assertEquals(54, state(54).analytics.targetEquityPercent)
    }

    @Test fun `a debt fund is debt and a plain hybrid is other`() {
        val debt = stock("DEBTF", FundClass.Debt, Instrument.MutualFund)
        val hybrid = stock("HYB", FundClass.Hybrid, Instrument.MutualFund)
        buy(debt, LocalDate.of(2025, 1, 10), 10, 1_000)
        buy(hybrid, LocalDate.of(2025, 1, 10), 10, 500)
        assertEquals(listOf("Debt & FDs", "Other"), state().analytics.allocation.map { it.label })
    }

    // ---- Concentration ----

    @Test fun `a stock above ten percent of equity is flagged and a funds-heavy portfolio is not`() {
        val big = stock("BIG")
        val small = stock("SMALL")
        buy(big, LocalDate.of(2025, 1, 10), 10, 1_000)
        buy(small, LocalDate.of(2025, 1, 10), 10, 100)
        val flagged = state().analytics.concentration
        assertEquals(listOf("BIG holding"), flagged.map { it.name })
        assertEquals("90.9% of equity", flagged.single().percentLabel)

        val fund = stock("EQF", FundClass.Equity, Instrument.MutualFund)
        buy(fund, LocalDate.of(2025, 1, 10), 10, 20_000)
        assertTrue("1,000 of 21,100 is under 10%", state().analytics.concentration.isEmpty())
    }

    // ---- Tax if sold today ----

    @Test fun `tax if sold today splits the gain by lot age and records nothing`() {
        val infy = stock("INFY")
        buy(infy, LocalDate.of(2025, 1, 10), 10, 1_000)
        buy(infy, LocalDate.of(2026, 6, 1), 10, 2_000)
        price(infy, 300)
        val before = runBlocking { ledger.snapshot().first().transactions.size }
        val preview = state().details.getValue(infy).taxPreview!!
        assertEquals("20", preview.sellableLabel)
        assertEquals(6_000.rupees, preview.proceeds)
        assertEquals(3_000.rupees, preview.cost)
        assertEquals(3_000.rupees, preview.gain)
        assertEquals(listOf("Short-term" to 1_000.rupees, "Long-term" to 2_000.rupees), preview.lines)
        assertEquals(before, runBlocking { ledger.snapshot().first().transactions.size })
    }

    @Test fun `locked ELSS units are left out of the tax preview`() {
        val elss = stock("ELSS", FundClass.Elss, Instrument.MutualFund)
        buy(elss, LocalDate.of(2023, 1, 10), 10, 1_000)
        buy(elss, LocalDate.of(2025, 6, 1), 10, 1_500)
        price(elss, 200)
        val preview = state().details.getValue(elss).taxPreview!!
        assertEquals("10", preview.sellableLabel)
        assertEquals("10", preview.lockedLabel)
        assertEquals(2_000.rupees, preview.proceeds)
        assertEquals(listOf("Long-term" to 1_000.rupees), preview.lines)
    }

    @Test fun `no preview without a price or without units`() {
        val infy = stock("INFY")
        buy(infy, LocalDate.of(2025, 1, 10), 10, 1_000)
        assertNull(state().details.getValue(infy).taxPreview)
        price(infy, 150)
        assertNotNull(state().details.getValue(infy).taxPreview)
        sell(infy, LocalDate.of(2026, 3, 1), 10, 1_400)
        assertNull(state().details.getValue(infy).taxPreview)
    }

    // ---- Capital-gains register ----

    @Test fun `the register groups sales by financial year with gain by term`() {
        val infy = stock("INFY")
        buy(infy, LocalDate.of(2025, 1, 10), 10, 1_000)
        sell(infy, LocalDate.of(2026, 3, 1), 4, 600)
        sell(infy, LocalDate.of(2026, 5, 1), 2, 400)
        val years = state().gainsByYear
        assertEquals(listOf("FY 2026-27", "FY 2025-26"), years.map { it.label })
        val older = years[1]
        assertEquals(200.rupees, older.long)
        assertEquals(0.rupees, older.short)
        assertEquals(1, older.sales.size)
        assertEquals("INFY holding", older.sales.single().name)
        assertEquals(600.rupees, older.sales.single().proceeds)
        assertEquals(400.rupees, older.sales.single().cost)
        assertEquals("Long-term", older.sales.single().termLabel)
        assertEquals(200.rupees, years[0].total)
    }

    @Test fun `a split is not a sale`() {
        val infy = stock("INFY")
        buy(infy, LocalDate.of(2025, 1, 10), 10, 1_000)
        runBlocking { invest.applyCorporateAction(infy, RestateKind.Split, Units.whole(2), LocalDate.of(2026, 1, 5)) }
        assertTrue(state().gainsByYear.isEmpty())
        sell(infy, LocalDate.of(2026, 3, 1), 10, 700)
        val sale = state().gainsByYear.single().sales.single()
        assertEquals(200.rupees, sale.gain)
        assertEquals(500.rupees, sale.cost)
    }

    @Test fun `register totals equal the gain accounts`() {
        val infy = stock("INFY")
        buy(infy, LocalDate.of(2025, 1, 10), 10, 1_000)
        buy(infy, LocalDate.of(2026, 1, 10), 10, 1_500)
        sell(infy, LocalDate.of(2026, 5, 1), 15, 2_700)
        val year = state().gainsByYear.single()
        val gains = runBlocking { ledger.accountBalance(ledger.accountIdByName("Income:Capital Gains:LTCG:Equity")!!) } + runBlocking { ledger.accountBalance(ledger.accountIdByName("Income:Capital Gains:STCG:Equity")!!) }
        assertEquals(-year.total.value, gains.value)
        // 10 units at 100 plus 5 at 150 cost 1,750, so 2,700 of proceeds is a 950 gain.
        assertEquals(950.rupees, year.total)
    }
}
