package com.moneymatters.app.feature.quickadd

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickAddViewModelTest {

    private val today = LocalDate.of(2026, 10, 20)
    private lateinit var db: MoneyMattersDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var model: QuickAddViewModel
    private lateinit var bank: String
    private lateinit var dining: String

    @Before
    fun setUp() {
        // Direct (same-thread) executors, so `viewModelScope.launch` in save() has no real async
        // gap to wait out — it runs to completion synchronously, same as Dispatchers.Main.immediate
        // would with no real suspension points.
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repo = LedgerRepository(db)
        model = QuickAddViewModel(repo, today = { today })
        runBlocking {
            bank = repo.createAccount("HDFC Bank", AccountType.Asset)
            dining = repo.createAccount("Expenses:Dining", AccountType.Expense)
        }
    }

    /** [QuickAddViewModel.save] runs on `viewModelScope` (`Dispatchers.Main`); flush the shadow looper to let it finish. */
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun `canSave is false without a payee or a balanced posting`() {
        assertFalse(model.canSave())
        model.form = model.form.copy(payee = "Swiggy", amount = 100.rupees, toAccountId = dining, fromAccountId = bank)
        assertTrue(model.canSave())
    }

    @Test fun `save posts a balanced transaction with the chosen status`() = runBlocking {
        model.form = model.form.copy(payee = "Swiggy", amount = 486.rupees, toAccountId = dining, fromAccountId = bank)
        var saved = false
        model.save(asDraft = false) { saved = true }
        idle()
        assertTrue(saved)
        assertEquals(486.rupees, repo.accountBalance(dining))
        val txn = repo.transactions().first().first { it.payee == "Swiggy" }
        assertEquals(TransactionStatus.Posted, txn.status)
    }

    @Test fun `save as draft does not affect balances`() = runBlocking {
        model.form = model.form.copy(payee = "Swiggy", amount = 100.rupees, toAccountId = dining, fromAccountId = bank)
        model.save(asDraft = true) {}
        idle()
        assertEquals(Paise.Zero, repo.accountBalance(bank))
    }

    @Test fun `save does nothing while unbalanced`() {
        var saved = false
        model.save(asDraft = false) { saved = true }
        idle()
        assertFalse(saved)
    }

    @Test fun `reset clears the form back to empty`() {
        model.form = model.form.copy(payee = "Swiggy")
        model.reset()
        assertEquals("", model.form.payee)
    }
}
