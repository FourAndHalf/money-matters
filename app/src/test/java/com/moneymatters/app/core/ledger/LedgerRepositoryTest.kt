package com.moneymatters.app.core.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
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
class LedgerRepositoryTest {

    private lateinit var db: MoneyMattersDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var bank: String
    private lateinit var dining: String

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
        runBlocking {
            bank = repo.createAccount("HDFC Bank", AccountType.Asset)
            dining = repo.createAccount("Expenses:Dining", AccountType.Expense)
        }
    }

    private fun spend(amount: Paise = 486.rupees, status: TransactionStatus = TransactionStatus.Posted): String = runBlocking {
        repo.postTransaction(
            date = LocalDate.of(2026, 10, 19),
            payee = "Swiggy",
            postings = listOf(PostingLine(dining, debit = amount), PostingLine(bank, credit = amount)),
            status = status,
        )
    }

    @Test fun `a balanced transaction posts and its balance is queryable`() {
        val id = spend()
        runBlocking {
            assertEquals(TransactionStatus.Posted, db.ledgerDao().getTransaction(id)!!.status)
            assertEquals((-486).rupees, repo.accountBalance(bank))
            assertEquals(486.rupees, repo.accountBalance(dining))
        }
    }

    @Test fun `an unbalanced transaction is rejected`() {
        assertThrows(UnbalancedTransactionException::class.java) {
            runBlocking {
                repo.postTransaction(
                    LocalDate.now(),
                    "Oops",
                    listOf(PostingLine(dining, debit = 500.rupees), PostingLine(bank, credit = 400.rupees)),
                )
            }
        }
    }

    @Test fun `fewer than two postings is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.postTransaction(LocalDate.now(), "Oops", listOf(PostingLine(dining, debit = 100.rupees))) }
        }
    }

    @Test fun `a posting with both debit and credit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.postTransaction(
                    LocalDate.now(),
                    "Oops",
                    listOf(PostingLine(dining, debit = 100.rupees, credit = 100.rupees), PostingLine(bank, credit = 100.rupees)),
                )
            }
        }
    }

    @Test fun `editing a posted transaction changes its postings and is audited`() {
        val id = spend()
        runBlocking {
            repo.edit(id, LocalDate.of(2026, 10, 20), "Swiggy (corrected)", listOf(PostingLine(dining, debit = 500.rupees), PostingLine(bank, credit = 500.rupees)))
            assertEquals(500.rupees, repo.accountBalance(dining))
            val audit = repo.auditLogFor(id).first()
            assertEquals(listOf("Created", "Edited"), audit.map { it.action })
        }
    }

    @Test fun `a reconciled transaction cannot be edited`() {
        val id = spend()
        runBlocking { repo.reconcile(id) }
        assertThrows(IllegalLifecycleTransitionException::class.java) {
            runBlocking { repo.edit(id, LocalDate.now(), "x", listOf(PostingLine(dining, debit = 1.rupees), PostingLine(bank, credit = 1.rupees))) }
        }
    }

    @Test fun `the lifecycle only moves forward one step at a time`() {
        val id = spend()
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { repo.lock(id) } }
        runBlocking {
            repo.reconcile(id)
            assertEquals(TransactionStatus.Reconciled, db.ledgerDao().getTransaction(id)!!.status)
        }
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { repo.reconcile(id) } }
        runBlocking {
            repo.lock(id)
            assertEquals(TransactionStatus.Locked, db.ledgerDao().getTransaction(id)!!.status)
        }
    }

    @Test fun `reversing a reconciled transaction posts a balanced, inverted entry`() {
        val id = spend()
        runBlocking {
            repo.reconcile(id)
            val reversalId = repo.reverse(id, "duplicate charge")
            assertEquals(Paise.Zero, repo.accountBalance(bank))
            assertEquals(Paise.Zero, repo.accountBalance(dining))
            assertEquals(TransactionStatus.Posted, db.ledgerDao().getTransaction(reversalId)!!.status)
            assertEquals(TransactionStatus.Reconciled, db.ledgerDao().getTransaction(id)!!.status)
        }
    }

    @Test fun `only a reconciled transaction can be reversed`() {
        val id = spend(status = TransactionStatus.Draft)
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { repo.reverse(id, "nope") } }
    }

    @Test fun `a prior-period adjustment needs a locked original and posts as its own entry`() {
        val id = spend()
        runBlocking {
            repo.reconcile(id)
            repo.lock(id)
            val adjustmentId = repo.postPriorPeriodAdjustment(
                originalTransactionId = id,
                date = LocalDate.of(2026, 11, 1),
                payee = "Swiggy (missed price rise)",
                postings = listOf(PostingLine(dining, debit = 50.rupees), PostingLine(bank, credit = 50.rupees)),
                reason = "price rise missed last month",
            )
            assertEquals(TransactionStatus.PriorPeriodAdjustment, db.ledgerDao().getTransaction(adjustmentId)!!.status)
            assertEquals(536.rupees, repo.accountBalance(dining))
        }
    }

    @Test fun `a prior-period adjustment needs a locked original`() {
        val id = spend()
        assertThrows(IllegalLifecycleTransitionException::class.java) {
            runBlocking {
                repo.postPriorPeriodAdjustment(id, LocalDate.now(), "x", listOf(PostingLine(dining, debit = 1.rupees), PostingLine(bank, credit = 1.rupees)), "nope")
            }
        }
    }

    @Test fun `createAccountIfAbsent reuses an account with the same name`() = runBlocking {
        val first = repo.createAccountIfAbsent("Equity:Opening", AccountType.Equity)
        val second = repo.createAccountIfAbsent("Equity:Opening", AccountType.Equity)
        assertEquals(first, second)
        assertEquals(bank, repo.createAccountIfAbsent("HDFC Bank", AccountType.Asset))
        assertEquals(3, repo.accounts().first().size)
    }

    @Test fun `a failure inside inTransaction rolls back every write in it`() {
        assertThrows(UnbalancedTransactionException::class.java) {
            runBlocking {
                repo.inTransaction {
                    repo.createAccount("Cash", AccountType.Asset)
                    repo.postTransaction(LocalDate.of(2026, 10, 19), "Good", listOf(PostingLine(dining, debit = 1.rupees), PostingLine(bank, credit = 1.rupees)))
                    repo.postTransaction(LocalDate.of(2026, 10, 19), "Bad", listOf(PostingLine(dining, debit = 1.rupees), PostingLine(bank, credit = 2.rupees)))
                }
            }
        }
        runBlocking {
            assertEquals(null, repo.accountIdByName("Cash"))
            assertEquals(Paise.Zero, repo.accountBalance(bank))
            assertEquals(0, repo.transactions().first().size)
        }
    }
}
