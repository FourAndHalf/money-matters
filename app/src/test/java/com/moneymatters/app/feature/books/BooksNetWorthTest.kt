package com.moneymatters.app.feature.books

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BooksNetWorthTest {

    @Test fun `an account flagged out of net worth is listed on its own and never summed`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        val repo = LedgerRepository(db)
        val bank = repo.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
        val epf = repo.createAccount("EPF", AccountType.Asset, kind = AccountKind.Investment, inNetWorth = false)
        val equity = repo.createAccount("Equity:Opening", AccountType.Equity)
        val day = LocalDate.of(2026, 9, 30)
        repo.postTransaction(day, "Opening: bank", listOf(PostingLine(bank, debit = 50_000.rupees), PostingLine(equity, credit = 50_000.rupees)))
        repo.postTransaction(day, "Opening: EPF", listOf(PostingLine(epf, debit = 3_00_000.rupees), PostingLine(equity, credit = 3_00_000.rupees)))

        val books = buildBooks(repo.snapshot().first(), LocalDate.of(2026, 10, 20), zone = ZoneOffset.UTC)

        assertEquals(50_000.rupees, books.accounts.netWorth)
        assertEquals(50_000.rupees, books.accounts.assetsTotal)
        assertEquals(50_000.rupees, books.home.netWorth)
        assertEquals(listOf("Bank & cash", NOT_IN_NET_WORTH), books.accounts.groups.map { it.title })
        assertEquals(3_00_000.rupees, books.accounts.groups.last().items.single().balance)
        db.close()
    }
}
