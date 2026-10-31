package com.moneymatters.app.core.budget

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountType
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

class BudgetPeriodTest {
    @Test fun `weekly runs Monday to Sunday`() {
        val wed = LocalDate.of(2026, 10, 28)
        assertEquals(DateRange(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)), BudgetPeriod.Weekly.rangeContaining(wed, 1))
        val monday = LocalDate.of(2026, 10, 26)
        assertEquals(monday, BudgetPeriod.Weekly.rangeContaining(monday, 1).start)
    }

    @Test fun `monthly follows the month start day`() {
        assertEquals(DateRange(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)), BudgetPeriod.Monthly.rangeContaining(LocalDate.of(2026, 11, 3), 25))
        assertEquals(DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), BudgetPeriod.Monthly.rangeContaining(LocalDate.of(2026, 10, 15), 1))
    }

    @Test fun `annual is the April to March tax year`() {
        assertEquals(DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)), BudgetPeriod.Annual.rangeContaining(LocalDate.of(2026, 4, 1), 1))
        assertEquals(DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)), BudgetPeriod.Annual.rangeContaining(LocalDate.of(2027, 3, 31), 1))
        assertEquals(DateRange(LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31)), BudgetPeriod.Annual.rangeContaining(LocalDate.of(2026, 3, 31), 1))
    }

    private val range = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Test fun `a budget heading for overspend names the day it runs out`() {
        // 10,000 spent in 10 days against 25,000: at that pace it is gone after 25 days, on 25 Oct.
        assertEquals(LocalDate.of(2026, 10, 25), runsOutOn(10_000.rupees, 25_000.rupees, range, LocalDate.of(2026, 10, 10)))
    }

    @Test fun `no projection when it lasts, is already over, is empty or is too early`() {
        assertNull(runsOutOn(5_000.rupees, 25_000.rupees, range, LocalDate.of(2026, 10, 10))) // lasts past the 31st
        assertNull(runsOutOn(26_000.rupees, 25_000.rupees, range, LocalDate.of(2026, 10, 10))) // already over
        assertNull(runsOutOn(Paise.Zero, 25_000.rupees, range, LocalDate.of(2026, 10, 10)))
        assertNull(runsOutOn(10_000.rupees, 25_000.rupees, range, LocalDate.of(2026, 10, 2))) // day two: too early to tell
    }

    @Test fun `the day count rounds up so it never promises a longer budget than it has`() {
        // 7,000 in 7 days is 1,000 a day against 10,000: gone after exactly 10 days.
        assertEquals(LocalDate.of(2026, 10, 10), runsOutOn(7_000.rupees, 10_000.rupees, range, LocalDate.of(2026, 10, 7)))
        // 3,000 in 7 days against 10,000 is 3.33 days short of 24; rounds up to 24, not down.
        assertEquals(LocalDate.of(2026, 10, 24), runsOutOn(3_000.rupees, 10_000.rupees, range, LocalDate.of(2026, 10, 7)))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BudgetRepositoryTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var repo: BudgetRepository
    private lateinit var bank: String
    private lateinit var food: String
    private lateinit var dining: String
    private lateinit var rent: String

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        repo = BudgetRepository(db)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset)
            food = ledger.createAccount("Expenses:Food", AccountType.Expense)
            dining = ledger.createAccount("Expenses:Food:Dining", AccountType.Expense, parentId = food)
            rent = ledger.createAccount("Expenses:Rent", AccountType.Expense)
        }
    }

    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    private fun spend(account: String, amount: Long, date: String = "2026-10-10", status: TransactionStatus = TransactionStatus.Posted) = runBlocking {
        ledger.postTransaction(LocalDate.parse(date), "x", listOf(PostingLine(account, debit = amount.rupees), PostingLine(bank, credit = amount.rupees)), status = status)
    }

    private fun spent(account: String, range: DateRange = october): Paise = runBlocking {
        val s = ledger.snapshot().first()
        spentIn(account, s.accounts, s.transactions, s.postings, range)
    }

    @Test fun `a category's spend includes its sub-categories`() {
        spend(food, 1_000)
        spend(dining, 2_500)
        spend(rent, 9_000)
        assertEquals(3_500.rupees, spent(food))
        assertEquals(2_500.rupees, spent(dining))
    }

    @Test fun `a refund reduces what was spent`() {
        spend(dining, 2_500)
        runBlocking { ledger.postTransaction(LocalDate.of(2026, 10, 12), "refund", listOf(PostingLine(bank, debit = 500.rupees), PostingLine(dining, credit = 500.rupees))) }
        assertEquals(2_000.rupees, spent(dining))
    }

    @Test fun `drafts and entries outside the period are not counted`() {
        spend(dining, 2_500, status = TransactionStatus.Draft)
        spend(dining, 700, date = "2026-09-30")
        spend(dining, 800, date = "2026-11-01")
        spend(dining, 100, date = "2026-10-01")
        spend(dining, 200, date = "2026-10-31")
        assertEquals(300.rupees, spent(dining))
    }

    @Test fun `setting a budget twice for a category changes it rather than adding another`() {
        val first = runBlocking { repo.set(food, 10_000_00, BudgetPeriod.Monthly) }
        val second = runBlocking { repo.set(food, 12_000_00, BudgetPeriod.Weekly) }

        assertEquals(first, second)
        val all = runBlocking { repo.budgets().first() }
        assertEquals(1, all.size)
        assertEquals(12_000_00L, all.single().amountPaise)
        assertEquals(BudgetPeriod.Weekly, all.single().period)
    }

    @Test fun `only expense categories can be budgeted, with a positive amount`() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.set(bank, 100, BudgetPeriod.Monthly) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.set(food, 0, BudgetPeriod.Monthly) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.set("nope", 100, BudgetPeriod.Monthly) } }
    }

    @Test fun `deleting a budget leaves the entries alone`() {
        spend(food, 1_000)
        val id = runBlocking { repo.set(food, 5_000_00, BudgetPeriod.Monthly) }
        runBlocking { repo.delete(id) }
        assertNull(runBlocking { repo.budget(id) })
        assertEquals(1_000.rupees, spent(food))
    }

    @Test fun `archived or unrelated accounts don't leak into a category's tree`() {
        val tree = categoryTree(food, listOf(AccountEntity(food, "Expenses:Food", AccountType.Expense), AccountEntity("d", "Dining", AccountType.Expense, parentId = food), AccountEntity("r", "Rent", AccountType.Expense)))
        assertEquals(setOf(food, "d"), tree)
    }
}
