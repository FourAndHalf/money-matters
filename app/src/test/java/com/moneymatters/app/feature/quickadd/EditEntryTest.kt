package com.moneymatters.app.feature.quickadd

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionStatus
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
class EditEntryTest {
    private val today = LocalDate.of(2026, 10, 20)
    private lateinit var repo: LedgerRepository
    private lateinit var model: QuickAddViewModel
    private lateinit var bank: String
    private lateinit var equity: String
    private lateinit var dining: String

    @Before fun setUp() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        repo = LedgerRepository(db)
        model = QuickAddViewModel(repo, today = { today })
        runBlocking {
            bank = repo.createAccount("State Bank of India", AccountType.Asset)
            equity = repo.createAccount("Equity:Opening", AccountType.Equity)
            dining = repo.createAccount("Expenses:Dining", AccountType.Expense)
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun opening(status: TransactionStatus = TransactionStatus.Posted) = runBlocking {
        repo.postTransaction(today, "Opening balance: State Bank of India", listOf(PostingLine(bank, debit = 22_658.rupees), PostingLine(equity, credit = 22_658.rupees)), status = status)
    }

    @Test fun `an opening balance can be edited, payee and amount, and stays balanced`() = runBlocking {
        val id = opening()
        model.startEdit(id)
        idle()
        val form = model.editForm!!
        assertTrue(form.amountEditable)
        assertEquals("22658", form.amountText)
        model.editForm = form.copy(payee = "Opening balance: SBI", amountText = "25,000.50")
        var saved = false
        model.saveEdit { saved = true }
        idle()
        assertTrue(saved)
        assertNull(model.editForm)
        assertEquals(2_500_050L, repo.accountBalance(bank).value)
        assertEquals("Opening balance: SBI", repo.transactions().first().first { it.id == id }.payee)
    }

    @Test fun `a split entry keeps its postings and only the date, payee and note change`() = runBlocking {
        val id = runBlocking {
            repo.postTransaction(today, "Dinner", listOf(PostingLine(dining, debit = 300.rupees), PostingLine(equity, debit = 200.rupees), PostingLine(bank, credit = 500.rupees)))
        }
        model.startEdit(id)
        idle()
        val form = model.editForm!!
        assertFalse(form.amountEditable)
        assertEquals(form.postings().size, 3)
        model.editForm = form.copy(note = "with friends")
        model.saveEdit {}
        idle()
        assertEquals(-50_000L, repo.accountBalance(bank).value)
        assertEquals("with friends", repo.transactions().first().first { it.id == id }.memo)
    }

    @Test fun `a reconciled entry can't be edited and says why`() = runBlocking {
        val id = opening()
        repo.reconcile(id)
        model.startEdit(id)
        idle()
        model.saveEdit {}
        idle()
        assertNotNull(model.editError)
        assertNotNull(model.editForm)
    }

    @Test fun `the form needs a payee, a real date and a positive amount`() {
        val id = opening()
        model.startEdit(id)
        idle()
        val form = model.editForm!!
        assertTrue(form.canSave)
        assertFalse(form.copy(payee = " ").canSave)
        assertFalse(form.copy(dateText = "20/10/2026").canSave)
        assertFalse(form.copy(amountText = "0").canSave)
        assertFalse(form.copy(amountText = "abc").canSave)
    }
}
