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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BooksViewModelTest {

    @Test fun `screens rebuild after every ledger write`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).build()
        val repo = LedgerRepository(db)
        val books = BooksViewModel(repo, monthStartDay = { 1 }, today = { LocalDate.of(2026, 10, 20) }).books

        withTimeout(10_000) {
            assertEquals(0, books.filterNotNull().first().accounts.groups.size)
            val bank = repo.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            val dining = repo.createAccount("Expenses:Dining", AccountType.Expense)
            repo.postTransaction(LocalDate.of(2026, 10, 19), "Swiggy", listOf(PostingLine(dining, debit = 486.rupees), PostingLine(bank, credit = 486.rupees)))
            val updated = books.filterNotNull().first { it.transactions.groups.isNotEmpty() }
            assertEquals("Swiggy", updated.home.recent.single().payee)
            assertEquals(486.rupees, updated.home.spent)
        }
        db.close()
    }
}
