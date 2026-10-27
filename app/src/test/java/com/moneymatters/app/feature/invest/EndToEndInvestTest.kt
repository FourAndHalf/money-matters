package com.moneymatters.app.feature.invest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.Compounding
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.feature.books.buildBooks
import com.moneymatters.app.feature.onboarding.NewAccountDraft
import com.moneymatters.app.feature.onboarding.OnboardingForm
import com.moneymatters.app.feature.onboarding.postSetupToLedger
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The whole path a real user takes, on real repositories: set up, invest every way, import, then build every screen's state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EndToEndInvestTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24, LocalDate.of(2023, 4, 1))))

    @Test fun `setup with savings and investment accounts, then every kind of investment, builds every state without failing`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        val ledger = LedgerRepository(db)
        val invest = InvestRepository(db, ledger, rules)
        // Onboarding offers every kind, including FD and Invest, each with an opening balance.
        postSetupToLedger(
            ledger,
            OnboardingForm(
                accounts = listOf(
                    NewAccountDraft("HDFC Bank", AccountKind.Bank, "1,00,000"),
                    NewAccountDraft("Savings", AccountKind.Bank, "25,000"),
                    NewAccountDraft("Investments", AccountKind.Investment, "2,00,000"),
                    NewAccountDraft("Bank FD", AccountKind.FixedDeposit, "50,000"),
                    NewAccountDraft("Cash", AccountKind.Cash, "2,000"),
                ),
            ),
            today,
        )
        val bank = ledger.accountIdByName("HDFC Bank")!!

        // A stock, a fund bought and allotted, a fund in transit, a deposit and a SIP.
        val infy = invest.findOrCreateHolding(Instrument.Stock, "INFY", name = "Infosys")
        invest.buy(infy, LocalDate.of(2025, 5, 1), Units.whole(10), UnitPrice.rupees(1_500), 15_000.rupees, bank, "Buy INFY")
        invest.recordPrice(infy, today, UnitPrice.rupees(1_600), "manual", PriceStatus.Manual)
        val fund = invest.findOrCreateHolding(Instrument.MutualFund, null, name = "Index Fund", fundClass = com.moneymatters.app.core.invest.FundClass.Equity)
        val order = invest.orderFund(fund, today.minusDays(5), 10_000.rupees, bank, "Buy Index Fund")
        val pending = invest.snapshot().first().pending.single().id
        invest.allot(pending, today.minusDays(2), Units.whole(100), UnitPrice.rupees(100))
        invest.orderFund(fund, today, 5_000.rupees, bank, "Buy Index Fund")
        invest.openDeposit("SBI FD", 1_00_000.rupees, 700, Compounding.Quarterly, today.minusMonths(3), today.plusMonths(9), bank)
        invest.openDeposit("HDFC FD 2", 50_000.rupees, 710, Compounding.Simple, today.minusMonths(1), today.plusMonths(14), bank)
        invest.createSipPlan(fund, 5_000.rupees, 5, bank, today.minusMonths(2))

        // A sale of part of an earlier holding, and a second buy of the same stock.
        val tcs = invest.findOrCreateHolding(Instrument.Stock, "TCS", name = "Tata Consultancy")
        invest.buy(tcs, LocalDate.of(2024, 1, 10), Units.whole(10), UnitPrice.rupees(3_500), 35_000.rupees, bank, "Buy TCS")
        invest.sell(tcs, LocalDate.of(2024, 6, 10), Units.whole(4), UnitPrice.rupees(3_750), 15_000.rupees, bank, "Sell TCS")
        invest.buy(infy, LocalDate.of(2025, 5, 2), Units.whole(5), UnitPrice.rupees(1_400), 7_000.rupees, bank, "Buy INFY")

        val l = ledger.snapshot().first()
        val i = invest.snapshot().first()
        val invState = buildInvest(l, i, today, rules)
        val books = buildBooks(l, today, invest = i)
        assertTrue(books.home.accounts.isEmpty()) // nothing pinned yet
        assertTrue(invState.holdings.isNotEmpty())
        assertEquals(3, invState.holdings.size)
        assertEquals(2, invState.deposits.size)
        assertEquals(2, invState.ladder.size)
        ledger.setPinned(bank, true)
        assertEquals(listOf(bank), buildBooks(ledger.snapshot().first(), today, invest = i).home.accounts.map { it.id })
    }
}
