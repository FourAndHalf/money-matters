package com.moneymatters.app.core.reports

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatementsTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var bank: String
    private lateinit var card: String
    private lateinit var salary: String
    private lateinit var rent: String
    private lateinit var dining: String
    private lateinit var opening: String

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            card = ledger.createAccount("HDFC Card", AccountType.Liability, kind = AccountKind.CreditCard)
            salary = ledger.createAccount("Income:Salary", AccountType.Income)
            rent = ledger.createAccount("Expenses:Rent", AccountType.Expense)
            dining = ledger.createAccount("Expenses:Dining", AccountType.Expense)
            opening = ledger.createAccount("Equity:Opening", AccountType.Equity)
        }
        post("2026-04-01", "Opening", bank, opening, 50_000)
        post("2026-09-01", "Salary", bank, salary, 72_000)
        post("2026-09-05", "Rent", rent, bank, 25_000)
        post("2026-10-01", "Salary", bank, salary, 72_000)
        post("2026-10-05", "Rent", rent, bank, 25_000)
        post("2026-10-12", "Dinner", dining, card, 1_200)
        post("2026-10-20", "Draft lunch", dining, bank, 999, TransactionStatus.Draft)
    }

    private fun post(date: String, payee: String, debit: String, credit: String, amount: Long, status: TransactionStatus = TransactionStatus.Posted) = runBlocking {
        ledger.postTransaction(LocalDate.parse(date), payee, listOf(PostingLine(debit, debit = amount.rupees), PostingLine(credit, credit = amount.rupees)), status = status)
    }

    private fun snap(): LedgerSnapshot = runBlocking { ledger.snapshot().first() }
    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Test fun `the income statement covers only the period and leaves out drafts`() {
        val s = incomeStatement(snap(), october)

        assertEquals(72_000.rupees, s.totalIncome)
        assertEquals(26_200.rupees, s.totalExpenses) // rent 25,000 + dinner 1,200; the draft lunch isn't counted
        assertEquals(45_800.rupees, s.net)
        assertEquals(listOf("Expenses:Dining", "Expenses:Rent"), s.expenses.map { it.name })
    }

    @Test fun `a longer period adds up its months`() {
        val s = incomeStatement(snap(), DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)))
        assertEquals(144_000.rupees, s.totalIncome)
        assertEquals(51_200.rupees, s.totalExpenses)
    }

    @Test fun `a period with nothing in it is empty, not an error`() {
        val s = incomeStatement(snap(), DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        assertTrue(s.income.isEmpty() && s.expenses.isEmpty())
        assertEquals(Paise.Zero, s.net)
    }

    @Test fun `the balance sheet balances at any date, with unclosed earnings making up the gap`() {
        for (date in listOf("2026-04-01", "2026-09-30", "2026-10-31", "2027-03-31")) {
            val b = balanceSheet(snap(), LocalDate.parse(date))
            assertTrue("not balanced on $date", b.balanced)
        }
        val b = balanceSheet(snap(), LocalDate.of(2026, 10, 31))
        // Bank: 50,000 opening + 72,000 - 25,000 + 72,000 - 25,000 = 144,000. Card owes 1,200. Equity 50,000 + earnings 92,800 (144,000 income - 51,200 expenses).
        assertEquals(144_000.rupees, b.totalAssets)
        assertEquals(1_200.rupees, b.totalLiabilities)
        assertEquals(92_800.rupees, b.currentEarnings)
        assertEquals(142_800.rupees, b.totalEquity)
    }

    @Test fun `assets and liabilities are shown on their own side`() {
        val b = balanceSheet(snap(), LocalDate.of(2026, 10, 31))
        assertEquals(144_000.rupees, b.assets.single { it.name == "HDFC Bank" }.amount)
    }

    @Test fun `a card balance is a liability owed, not a negative asset`() {
        val b = balanceSheet(snap(), LocalDate.of(2026, 10, 31))
        assertEquals(1_200.rupees, b.liabilities.single { it.name == "HDFC Card" }.amount)
    }

    @Test fun `the trial balance has equal debits and credits, and nothing counts after its date`() {
        val t = trialBalance(snap(), LocalDate.of(2026, 10, 31))
        assertTrue(t.balanced)
        assertEquals(t.totalDebit, t.totalCredit)
        val early = trialBalance(snap(), LocalDate.of(2026, 4, 30))
        assertTrue(early.balanced)
        assertEquals(listOf("HDFC Bank", "Equity:Opening"), early.rows.map { it.name })
    }

    @Test fun `the report period follows the month start day and the tax year`() {
        assertEquals(DateRange(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)), ReportPeriod.Month.rangeContaining(LocalDate.of(2026, 11, 3), 25))
        assertEquals(DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)), ReportPeriod.TaxYear.rangeContaining(LocalDate.of(2026, 11, 3), 25))
    }
}
