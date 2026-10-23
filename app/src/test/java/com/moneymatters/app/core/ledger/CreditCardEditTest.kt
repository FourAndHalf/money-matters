package com.moneymatters.app.core.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CreditCardEditTest {
    private lateinit var repo: LedgerRepository
    private lateinit var card: String

    @Before fun setUp() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        repo = LedgerRepository(db)
        card = repo.createAccount("HDFC Regalia", AccountType.Liability, kind = AccountKind.CreditCard)
        Unit
    }

    private suspend fun cards() = repo.snapshot().first().creditCards

    @Test fun `a card's limit and statement day can be changed, leaving its balance alone`() = runBlocking {
        repo.addCreditCardDetails(card, 1_00_000.rupees, 22)
        repo.updateCreditCardDetails(card, 1_50_000.rupees, 5)
        assertEquals(1, cards().size)
        assertEquals(1_50_000_00L, cards().single().limitPaise)
        assertEquals(5, cards().single().statementDay)
        assertEquals(Paise.Zero, repo.accountBalance(card))
    }

    @Test fun `a card added without details can be given them later`() = runBlocking {
        assertEquals(0, cards().size)
        repo.updateCreditCardDetails(card, 75_000.rupees, 12)
        assertEquals(75_000_00L, cards().single().limitPaise)
    }

    @Test fun `a bad limit, a bad day or an account that isn't a card is refused`() = runBlocking {
        val bank = repo.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateCreditCardDetails(card, Paise.Zero, 5) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateCreditCardDetails(card, 1_000.rupees, 29) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateCreditCardDetails(bank, 1_000.rupees, 5) } }
        assertEquals(0, cards().size)
    }
}
