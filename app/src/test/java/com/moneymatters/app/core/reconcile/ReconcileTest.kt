package com.moneymatters.app.core.reconcile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.IllegalLifecycleTransitionException
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReconcileTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var repo: ReconcileRepository
    private lateinit var bank: String
    private lateinit var card: String
    private lateinit var food: String
    private lateinit var salary: String
    private val day = LocalDate.of(2026, 10, 10)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        repo = ReconcileRepository(ledger)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            card = ledger.createAccount("HDFC Card", AccountType.Liability, kind = AccountKind.CreditCard)
            food = ledger.createAccount("Expenses:Food", AccountType.Expense)
            salary = ledger.createAccount("Income:Salary", AccountType.Income)
        }
    }

    private fun post(payee: String, debit: String, credit: String, amount: Long, on: LocalDate = day, status: TransactionStatus = TransactionStatus.Posted) = runBlocking {
        ledger.postTransaction(on, payee, listOf(PostingLine(debit, debit = amount.rupees), PostingLine(credit, credit = amount.rupees)), status = status)
    }

    private fun view(account: String) = runBlocking { reconcileView(ledger.snapshot().first(), account)!! }

    private fun status(id: String) = runBlocking { ledger.transaction(id)!!.status }

    @Test fun `an asset's lines are signed by what they do to the bank balance`() {
        val pay = post("Salary", bank, salary, 72_000)
        val lunch = post("Lunch", food, bank, 500)

        val v = view(bank)

        assertEquals(Paise.Zero, v.cleared)
        assertEquals(72_000.rupees, v.lines.single { it.transactionId == pay }.amount)
        assertEquals((-500).rupees, v.lines.single { it.transactionId == lunch }.amount)
    }

    @Test fun `a card's lines count amounts owed as positive`() {
        val dinner = post("Dinner", food, card, 1_200)
        val payment = post("Card payment", card, bank, 1_200)

        val v = view(card)

        assertEquals(1_200.rupees, v.lines.single { it.transactionId == dinner }.amount)
        assertEquals((-1_200).rupees, v.lines.single { it.transactionId == payment }.amount)
    }

    @Test fun `already reconciled entries form the cleared balance, and drafts count for nothing`() {
        val a = post("Salary", bank, salary, 50_000)
        runBlocking { ledger.reconcile(a) }
        post("Draft spend", food, bank, 999, status = TransactionStatus.Draft)
        post("Lunch", food, bank, 500)

        val v = view(bank)

        assertEquals(50_000.rupees, v.cleared)
        assertEquals(1, v.lines.size)
    }

    @Test fun `the difference is the statement less cleared and ticked entries`() {
        val a = post("Salary", bank, salary, 50_000)
        val b = post("Lunch", food, bank, 500)
        val v = view(bank)

        assertEquals(49_500.rupees, v.difference(49_500.rupees, emptySet()))
        assertEquals(Paise.Zero, v.difference(49_500.rupees, setOf(a, b)))
        assertEquals(500.rupees, v.difference(50_000.rupees, setOf(a, b)))
        assertEquals((-500).rupees, v.difference(49_000.rupees, setOf(a, b)))
    }

    @Test fun `only assets and liabilities can be reconciled`() {
        assertNull(runBlocking { reconcileView(ledger.snapshot().first(), food) })
        assertNull(runBlocking { reconcileView(ledger.snapshot().first(), "nope") })
    }

    @Test fun `finishing reconciles exactly the ticked entries when the books agree with the statement`() {
        val a = post("Salary", bank, salary, 50_000)
        val b = post("Lunch", food, bank, 500)
        val later = post("Dinner", food, bank, 800)

        val done = runBlocking { repo.finish(bank, 49_500.rupees, setOf(a, b)) }

        assertEquals(2, done)
        assertEquals(TransactionStatus.Reconciled, status(a))
        assertEquals(TransactionStatus.Reconciled, status(b))
        assertEquals(TransactionStatus.Posted, status(later)) // not on this statement
    }

    @Test fun `finishing refuses when the books are out, and changes nothing`() {
        val a = post("Salary", bank, salary, 50_000)
        val b = post("Lunch", food, bank, 500)

        val e = assertThrows(ReconcileMismatchException::class.java) { runBlocking { repo.finish(bank, 49_000.rupees, setOf(a, b)) } }

        assertEquals((-500).rupees, e.difference)
        assertEquals(TransactionStatus.Posted, status(a))
        assertEquals(TransactionStatus.Posted, status(b))
    }

    @Test fun `finishing refuses an entry that is no longer waiting`() {
        val a = post("Salary", bank, salary, 50_000)
        runBlocking { ledger.reconcile(a) }
        assertThrows(IllegalLifecycleTransitionException::class.java) { runBlocking { repo.finish(bank, Paise.Zero, setOf(a)) } }
    }

    @Test fun `a difference on a bank account is booked to Unaccounted so the books then agree`() {
        val a = post("Salary", bank, salary, 50_000)
        // The statement shows 300 less than the books: a charge we never entered.
        val diff = view(bank).difference(49_700.rupees, setOf(a))
        assertEquals((-300).rupees, diff)

        val fix = runBlocking { repo.postDifference(bank, diff, "Bank charge", day) }
        val after = view(bank)

        assertEquals(49_700.rupees, Paise(after.lines.sumOf { it.amount.value }))
        assertEquals(Paise.Zero, after.difference(49_700.rupees, setOf(a, fix)))
        val snap = runBlocking { ledger.snapshot().first() }
        val unaccounted = snap.accounts.single { it.name == UNACCOUNTED }
        assertEquals(AccountType.Expense, unaccounted.type)
        assertEquals(300.rupees, runBlocking { ledger.accountBalance(unaccounted.id) })
        assertEquals("Bank charge", snap.transactions.single { it.id == fix }.memo)
    }

    @Test fun `a difference in the bank's favour is booked the other way`() {
        val a = post("Salary", bank, salary, 50_000)
        val diff = view(bank).difference(50_250.rupees, setOf(a))
        assertEquals(250.rupees, diff)

        val fix = runBlocking { repo.postDifference(bank, diff, "Interest not entered", day) }

        assertEquals(Paise.Zero, view(bank).difference(50_250.rupees, setOf(a, fix)))
    }

    @Test fun `a card statement that owes more than the books is booked as more owed`() {
        val dinner = post("Dinner", food, card, 1_200)
        val diff = view(card).difference(1_450.rupees, setOf(dinner)) // 250 more owed than we have
        assertEquals(250.rupees, diff)

        val fix = runBlocking { repo.postDifference(card, diff, "Annual fee", day) }

        assertEquals(Paise.Zero, view(card).difference(1_450.rupees, setOf(dinner, fix)))
        assertEquals(1_450.rupees, Paise(-runBlocking { ledger.accountBalance(card) }.value))
    }

    @Test fun `a difference needs an amount and a note`() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.postDifference(bank, Paise.Zero, "x", day) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.postDifference(bank, 5.rupees, " ", day) } }
    }

    @Test fun `posting two differences reuses the one Unaccounted account`() {
        runBlocking { repo.postDifference(bank, 5.rupees, "one", day); repo.postDifference(bank, (-3).rupees, "two", day) }
        assertEquals(1, runBlocking { ledger.snapshot().first() }.accounts.count { it.name == UNACCOUNTED })
    }
}
