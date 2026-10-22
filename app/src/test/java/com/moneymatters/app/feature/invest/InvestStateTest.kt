package com.moneymatters.app.feature.invest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.feature.books.buildBooks
import com.moneymatters.app.ui.components.Tone
import java.time.LocalDate
import java.time.ZoneOffset
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
class InvestStateTest {

    private val today = LocalDate.of(2026, 10, 20)
    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24)))
    private lateinit var ledger: LedgerRepository
    private lateinit var invest: InvestRepository
    private lateinit var bank: String
    private lateinit var holding: String

    @Before fun setUp() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        invest = InvestRepository(db, ledger)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            val account = ledger.createAccount("Infosys (INFY)", AccountType.Asset, kind = AccountKind.Investment)
            holding = invest.addHolding(account, Instrument.Stock, symbol = "INFY")
        }
    }

    private fun buy(date: LocalDate, units: Long, amount: Long) = runBlocking {
        invest.buy(holding, date, Units.whole(units), UnitPrice.rupees(amount / units), amount.rupees, bank, "Buy INFY")
    }

    private fun price(price: Long, date: LocalDate = today) = runBlocking {
        invest.recordPrice(holding, date, UnitPrice.rupees(price), "manual", PriceStatus.Manual)
    }

    private fun state() = runBlocking { buildInvest(ledger.snapshot().first(), invest.snapshot().first(), today, rules) }

    @Test fun `with no price the holding counts at cost and says so`() {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000)
        val s = state()
        val row = s.holdings.single()
        assertEquals(1_000.rupees, s.value)
        assertEquals(1_000.rupees, s.cost)
        assertEquals(1, s.unpricedCount)
        assertNull(row.marketValue)
        assertNull(s.xirr)
        assertEquals("No price", row.priceTag.text)
        assertEquals(Tone.Neutral, row.priceTag.tone)
        assertEquals("10", row.unitsLabel)
        assertEquals("₹100.00", row.avgCostLabel)
    }

    @Test fun `a price gives market value, gain, percent and a return`() {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000)
        price(120)
        val s = state()
        val row = s.holdings.single()
        assertEquals(1_200.rupees, row.marketValue)
        assertEquals(200.rupees, row.gain)
        assertEquals(0.2, row.gainPercent!!, 1e-9)
        assertEquals(1_200.rupees, s.value)
        assertEquals(0, s.unpricedCount)
        assertEquals("✍ Manual", row.priceTag.text)
        assertEquals("₹120.00", row.priceLabel)
        val xirr = s.xirr
        assertNotNull(xirr)
        assertTrue("20% over ~1.8 years is roughly 11%, got $xirr", xirr!! > 0.08 && xirr < 0.15)
    }

    @Test fun `the newest price date wins, not the last one entered`() {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000)
        price(130, LocalDate.of(2026, 10, 17))
        price(90, LocalDate.of(2026, 9, 1))
        assertEquals(1_300.rupees, state().holdings.single().marketValue)
    }

    @Test fun `a part sale leaves the unsold lot's cost and counts the proceeds in the return`() {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000)
        runBlocking {
            invest.sell(holding, LocalDate.of(2026, 3, 1), Units.whole(4), UnitPrice.rupees(150), 600.rupees, bank, "Sell INFY", rules.listedEquity(today))
        }
        price(150)
        val s = state()
        assertEquals(600.rupees, s.cost)
        assertEquals(900.rupees, s.value)
        assertEquals("6", s.holdings.single().unitsLabel)
        val detail = s.details.getValue(holding)
        assertEquals(listOf("Sold 4", "Bought 10"), detail.transactions.map { (if (it.isBuy) "Bought " else "Sold ") + it.unitsLabel })
        assertEquals(600.rupees, detail.transactions.first().amount) // proceeds, not cost (cost was 400)
        assertEquals("Long-term", detail.lots.single().termLabel)
        assertEquals(600.rupees, detail.lots.single().cost)
    }

    @Test fun `a lot held under a year is short-term`() {
        buy(LocalDate.of(2026, 6, 1), 10, 1_000)
        assertEquals("Short-term", state().details.getValue(holding).lots.single().termLabel)
    }

    @Test fun `a fully sold holding leaves the list but keeps its detail and realised return`() {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000)
        runBlocking {
            invest.sell(holding, LocalDate.of(2026, 3, 1), Units.whole(10), UnitPrice.rupees(130), 1_300.rupees, bank, "Sell INFY", rules.listedEquity(today))
        }
        val s = state()
        assertTrue(s.holdings.isEmpty())
        assertEquals(0.rupees, s.value)
        assertTrue(s.details.getValue(holding).xirr!! > 0)
        assertTrue(s.details.getValue(holding).lots.isEmpty())
    }

    @Test fun `net worth counts a priced holding at market and leaves the ledger at cost`() {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000)
        val equity = runBlocking { ledger.createAccount("Equity:Opening", AccountType.Equity) }
        runBlocking {
            ledger.postTransaction(
                LocalDate.of(2025, 1, 1), "Opening: bank",
                listOf(com.moneymatters.app.core.ledger.PostingLine(bank, debit = 5_000.rupees), com.moneymatters.app.core.ledger.PostingLine(equity, credit = 5_000.rupees)),
            )
        }
        val ledgerSnapshot = runBlocking { ledger.snapshot().first() }
        val atCost = buildBooks(ledgerSnapshot, today, zone = ZoneOffset.UTC, invest = runBlocking { invest.snapshot().first() })
        // bank 5,000 - 1,000 paid + holding 1,000 at cost
        assertEquals(5_000.rupees, atCost.accounts.netWorth)

        price(150)
        val atMarket = buildBooks(ledgerSnapshot, today, zone = ZoneOffset.UTC, invest = runBlocking { invest.snapshot().first() })
        assertEquals(5_500.rupees, atMarket.accounts.netWorth)
        assertEquals(5_500.rupees, atMarket.home.netWorth)
        val row = atMarket.accounts.groups.flatMap { it.items }.single { it.name == "Infosys (INFY)" }
        assertEquals(1_500.rupees, row.balance)
        assertTrue(row.subtitle, row.subtitle.endsWith("at market"))
        // The ledger itself still holds cost; only the displayed totals use market value.
        assertEquals(1_000.rupees, runBlocking { ledger.accountBalance(ledgerSnapshot.accounts.single { it.name == "Infosys (INFY)" }.id) })
    }

    @Test fun `price text formats with up to four decimals`() {
        assertEquals("₹81.00", formatUnitPrice(810_000))
        assertEquals("₹81.23", formatUnitPrice(812_300))
        assertEquals("₹81.2345", formatUnitPrice(812_345))
        assertEquals("+8.40%", formatPercent(0.084))
        assertEquals("-2.50%", formatPercent(-0.025))
    }

    @Test fun `dividends and corporate actions are listed and dividends lift the return`() {
        buy(LocalDate.of(2025, 1, 10), 10, 1_000)
        price(100)
        val withoutDividend = state().xirr
        runBlocking {
            invest.recordDividend(holding, LocalDate.of(2026, 6, 1), 160.rupees, 40.rupees, bank, "Dividend INFY")
            invest.applyCorporateAction(holding, com.moneymatters.app.core.invest.RestateKind.Bonus, Units.whole(1), LocalDate.of(2026, 7, 1))
        }
        val s = state()
        val detail = s.details.getValue(holding)
        assertEquals(200.rupees, detail.dividendTotal)
        assertEquals(listOf("Bonus 1 per unit", "Dividend"), detail.actions.map { it.label })
        assertEquals(200.rupees, detail.actions.last().amount)
        assertTrue("a 20% dividend should raise a flat-price return", detail.xirr!! > (withoutDividend ?: 0.0))
        assertEquals("20", s.holdings.single().unitsLabel)
    }
}
