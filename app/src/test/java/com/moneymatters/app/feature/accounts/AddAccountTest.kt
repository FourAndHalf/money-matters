package com.moneymatters.app.feature.accounts

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.feature.onboarding.NewAccountDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddAccountTest {

    private val date = LocalDate.of(2026, 10, 23)
    private lateinit var ledger: LedgerRepository

    @Before fun setUp() {
        ledger = LedgerRepository(Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build())
    }

    private fun add(draft: NewAccountDraft) = runBlocking { addAccountWithOpening(ledger, draft, date) }

    private fun balance(name: String) = runBlocking { ledger.accountBalance(ledger.accountIdByName(name)!!) }

    @Test fun `a bank account with a balance is an asset booked against opening equity`() {
        add(NewAccountDraft("ICICI Bank", AccountKind.Bank, "25,000"))
        assertEquals(25_000.rupees, balance("ICICI Bank"))
        assertEquals((-25_000).rupees, balance("Equity:Opening"))
        assertEquals(AccountType.Asset, runBlocking { ledger.snapshot().first().accounts.single { it.name == "ICICI Bank" }.type })
    }

    @Test fun `a credit card is a liability and the amount owed is a credit`() {
        add(NewAccountDraft("HDFC Regalia", AccountKind.CreditCard, "8,000"))
        val card = runBlocking { ledger.snapshot().first().accounts.single { it.name == "HDFC Regalia" } }
        assertEquals(AccountType.Liability, card.type)
        assertEquals(AccountKind.CreditCard, card.kind)
        assertEquals((-8_000).rupees, balance("HDFC Regalia"))
    }

    @Test fun `no opening balance creates the account and no entry`() {
        add(NewAccountDraft("Wallet", AccountKind.Cash, ""))
        assertNotNull(runBlocking { ledger.accountIdByName("Wallet") })
        assertEquals(0, runBlocking { ledger.snapshot().first().transactions.size })
    }

    @Test fun `a blank name, a bad amount and a taken name are refused and change nothing`() {
        assertThrows(IllegalArgumentException::class.java) { add(NewAccountDraft("  ", AccountKind.Bank, "")) }
        assertThrows(IllegalArgumentException::class.java) { add(NewAccountDraft("Bad", AccountKind.Bank, "abc")) }
        add(NewAccountDraft("Savings", AccountKind.Bank, "100"))
        assertThrows(IllegalArgumentException::class.java) { add(NewAccountDraft("Savings", AccountKind.Bank, "999")) }
        assertEquals(100.rupees, balance("Savings"))
        assertEquals(1, runBlocking { ledger.snapshot().first().transactions.size })
    }
}
