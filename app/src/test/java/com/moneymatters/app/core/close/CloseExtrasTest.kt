package com.moneymatters.app.core.close

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.budget.BudgetEntity
import com.moneymatters.app.core.budget.BudgetPeriod
import com.moneymatters.app.core.invest.HoldingEntity
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.invest.LotEntity
import com.moneymatters.app.core.invest.PendingAllotmentEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PlanKind
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.SUSPENSE_ACCOUNT
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.ledger.TransactionTags
import com.moneymatters.app.core.loan.LoanEntity
import com.moneymatters.app.core.loan.LoanRepository
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.core.recurring.Frequency
import com.moneymatters.app.core.recurring.RecurringRepository
import com.moneymatters.app.core.recurring.RecurringRuleEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloseExtrasTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var bank: String
    private lateinit var food: String
    private lateinit var rent: String
    private lateinit var suspense: String
    private val october = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val today = LocalDate.of(2026, 11, 2)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            food = ledger.createAccount("Expenses:Food", AccountType.Expense)
            rent = ledger.createAccount("Expenses:Rent", AccountType.Expense)
            suspense = ledger.createAccount(SUSPENSE_ACCOUNT, AccountType.Expense)
        }
    }

    private fun spend(account: String, amount: Long, date: String = "2026-10-10") = runBlocking {
        ledger.postTransaction(LocalDate.parse(date), "x", listOf(PostingLine(account, debit = amount.rupees), PostingLine(bank, credit = amount.rupees)))
    }

    private fun check(extras: ChecklistExtras = ChecklistExtras(), invest: InvestSnapshot = InvestSnapshot.Empty): Checklist =
        buildChecklist(runBlocking { ledger.snapshot().first() }, invest, october, today, emptySet(), extras)

    private fun item(list: Checklist, title: String) = list.items.single { it.title == title }

    // ---- Suspense ----

    @Test fun `an entry left in suspense blocks the close, and categorizing it clears the item`() {
        spend(suspense, 300)
        assertFalse(item(check(), "Nothing in suspense").ok)
        assertTrue(item(check(), "Nothing in suspense").detail.contains("1 entry is uncategorized"))
    }

    @Test fun `with every entry categorized, suspense is clear`() {
        spend(food, 300)
        assertTrue(item(check(), "Nothing in suspense").ok)
    }

    @Test fun `suspense from another month doesn't block this one`() {
        spend(suspense, 300, date = "2026-09-10")
        assertTrue(item(check(), "Nothing in suspense").ok)
    }

    // ---- Recurring ----

    private fun rule(active: Boolean = true) = RecurringRuleEntity("", "Rent", bank, rent, 20_000_00, Frequency.Monthly, "2026-10-05", null, autoPost = true, isActive = active)

    @Test fun `a recurring entry that is due and not booked blocks the close until it is booked`() {
        val repo = RecurringRepository(db, ledger)
        runBlocking { repo.create(rule()) }
        val rules = { runBlocking { repo.rules().first() } }

        val before = item(check(ChecklistExtras(recurringRules = rules())), "Recurring entries booked")
        assertFalse(before.ok)
        assertEquals(com.moneymatters.app.core.close.CheckFix.Recurring, before.fix)

        runBlocking { repo.generateDue(today) }
        assertTrue(item(check(ChecklistExtras(recurringRules = rules())), "Recurring entries booked").ok)
    }

    @Test fun `a paused rule doesn't block, and nor does one not yet due`() {
        val repo = RecurringRepository(db, ledger)
        runBlocking { repo.create(rule(active = false)); repo.create(rule().copy(name = "Later", startDate = "2026-11-05")) }
        assertTrue(item(check(ChecklistExtras(recurringRules = runBlocking { repo.rules().first() })), "Recurring entries booked").ok)
    }

    // ---- Loans ----

    private fun loanSetup(firstDue: String): LoanRepository {
        val repo = LoanRepository(db, ledger)
        runBlocking {
            val loan = ledger.createAccount("Home Loan", AccountType.Liability, kind = AccountKind.Loan)
            val opening = ledger.createAccount("Equity:Opening", AccountType.Equity)
            ledger.postTransaction(LocalDate.of(2026, 9, 1), "Opening", listOf(PostingLine(opening, debit = 500_000.rupees), PostingLine(loan, credit = 500_000.rupees)))
            repo.add(LoanEntity(loan, 500_000.rupees.value, 850, 10_259.rupees.value, firstDue, bank))
        }
        return repo
    }

    private fun loanExtras(repo: LoanRepository) = ChecklistExtras(loans = runBlocking { repo.loans().first() }, loanPayments = runBlocking { repo.payments().first() })

    @Test fun `an EMI due this month and unpaid blocks the close until it is paid`() {
        val repo = loanSetup("2026-10-05")
        assertFalse(item(check(loanExtras(repo)), "Loan EMIs paid").ok)

        runBlocking { repo.payEmi(repo.loans().first().single().accountId, LocalDate.of(2026, 10, 5)) }
        assertTrue(item(check(loanExtras(repo)), "Loan EMIs paid").ok)
    }

    @Test fun `an EMI due next month doesn't block this one`() {
        assertTrue(item(check(loanExtras(loanSetup("2026-11-05"))), "Loan EMIs paid").ok)
    }

    @Test fun `a loan paid off doesn't block`() {
        val repo = loanSetup("2026-10-05")
        runBlocking { repo.prepay(repo.loans().first().single().accountId, 500_000.rupees, LocalDate.of(2026, 10, 2)) }
        assertTrue(item(check(loanExtras(repo)), "Loan EMIs paid").ok)
    }

    // ---- In Transit ----

    private fun pending(orderDate: String, open: Boolean = true) =
        PendingAllotmentEntity("p", "h", orderDate, 5_000_00, "t", allotmentTransactionId = if (open) null else "a")

    @Test fun `an order waiting longer than a week at month end is stale`() {
        val old = check(invest = InvestSnapshot.Empty.copy(pending = listOf(pending("2026-10-10"))))
        assertFalse(item(old, "In Transit is clear").ok)
        val recent = check(invest = InvestSnapshot.Empty.copy(pending = listOf(pending("2026-10-28"))))
        assertTrue(item(recent, "In Transit is clear").ok)
    }

    @Test fun `an exact week is stale, and an allotted order is not waiting`() {
        assertFalse(item(check(invest = InvestSnapshot.Empty.copy(pending = listOf(pending("2026-10-24")))), "In Transit is clear").ok)
        assertTrue(item(check(invest = InvestSnapshot.Empty.copy(pending = listOf(pending("2026-10-10", open = false)))), "In Transit is clear").ok)
    }

    // ---- Units ----

    private val held = InvestSnapshot.Empty.copy(
        holdings = listOf(HoldingEntity("h", "acc", Instrument.Stock, symbol = "INFY")),
        lots = listOf(LotEntity("l", "h", "2026-09-01", 10_000_000, 1_000_00, sourceTransactionId = "t")),
    )

    @Test fun `holding units must be checked when something is held`() {
        val list = check(ChecklistExtras(unitsConfirmed = false), held)
        assertFalse(item(list, "Holding units checked").ok)
        assertEquals(CheckFix.Units, item(list, "Holding units checked").fix)
        assertTrue(item(check(ChecklistExtras(unitsConfirmed = true), held), "Holding units checked").ok)
    }

    @Test fun `with nothing held there are no units to check`() {
        assertNull(check(ChecklistExtras(unitsConfirmed = false)).items.firstOrNull { it.title == "Holding units checked" })
    }

    @Test fun `a book with none of these things still has a clean checklist apart from what is genuinely open`() {
        val list = check()
        listOf("Nothing in suspense", "Recurring entries booked", "Loan EMIs paid", "In Transit is clear").forEach { assertTrue(it, item(list, it).ok) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MonthEndReportTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var bank: String
    private lateinit var stocks: String
    private lateinit var fd: String
    private lateinit var salary: String
    private lateinit var interest: String
    private lateinit var rent: String
    private lateinit var food: String
    private lateinit var opening: String
    private val october = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            stocks = ledger.createAccount("Infosys", AccountType.Asset, kind = AccountKind.Investment)
            fd = ledger.createAccount("FD HDFC", AccountType.Asset, kind = AccountKind.FixedDeposit)
            salary = ledger.createAccount("Income:Salary", AccountType.Income)
            interest = ledger.createAccount("Income:Interest", AccountType.Income)
            rent = ledger.createAccount("Expenses:Rent", AccountType.Expense)
            food = ledger.createAccount("Expenses:Food", AccountType.Expense)
            opening = ledger.createAccount("Equity:Opening", AccountType.Equity)
        }
        post("2026-07-01", "Opening", bank, opening, 100_000)
        for ((month, foodSpend) in listOf("07" to 5_000L, "08" to 8_000L, "09" to 6_000L)) {
            post("2026-$month-01", "Salary", bank, salary, 50_000)
            post("2026-$month-05", "Rent", rent, bank, 20_000)
            post("2026-$month-10", "Food", food, bank, foodSpend)
        }
        post("2026-10-01", "Salary", bank, salary, 60_000)
        post("2026-10-05", "Rent", rent, bank, 20_000, tags = TransactionTags(DecisionKind.Need, PlanKind.Planned))
        post("2026-10-10", "Food", food, bank, 9_000, tags = TransactionTags(DecisionKind.Want, PlanKind.Unplanned))
        post("2026-10-12", "Buy Infosys", stocks, bank, 10_000)
        post("2026-10-31", "FD interest", fd, interest, 500)
        post("2026-10-20", "Draft", food, bank, 1_000, status = TransactionStatus.Draft)
    }

    private fun post(date: String, payee: String, debit: String, credit: String, amount: Long, tags: TransactionTags = TransactionTags(), status: TransactionStatus = TransactionStatus.Posted) = runBlocking {
        ledger.postTransaction(LocalDate.parse(date), payee, listOf(PostingLine(debit, debit = amount.rupees), PostingLine(credit, credit = amount.rupees)), status = status, tags = tags)
    }

    private fun report(budgets: List<BudgetEntity> = emptyList()) = monthEndReport(runBlocking { ledger.snapshot().first() }, october, 1, budgets)

    @Test fun `income, expenses and the two rates are worked out from the books`() {
        val r = report()
        assertEquals(60_500.rupees, r.income) // salary plus 500 interest; the draft is left out
        assertEquals(29_000.rupees, r.expenses)
        assertEquals(31_500.rupees, r.surplus)
        assertEquals(5_206, r.savingsRateBps) // 31,500 of 60,500 is 52.06%, rounded down
        assertEquals("52.0%", formatBps(5_206))
        assertEquals(10_000.rupees, r.netInvested) // the stock purchase counts; the FD's interest is income, not money put in
        assertEquals(1_652, r.investmentRateBps)
    }

    @Test fun `spending is compared with last month and the three months before`() {
        val r = report()
        assertEquals(26_000.rupees, r.previousExpenses)
        assertEquals(Paise(2_633_333), r.averageExpenses) // (26,000 + 28,000 + 25,000) / 3
        val food = r.movers.single()
        assertEquals("Expenses:Food", food.name)
        assertEquals(9_000.rupees, food.current)
        assertEquals(3_000.rupees, food.change) // rent didn't move, so it isn't listed
    }

    @Test fun `spending is split by how it was tagged`() {
        val r = report()
        assertEquals(20_000.rupees, r.needs)
        assertEquals(9_000.rupees, r.wants)
        assertEquals(Paise.Zero, r.investing)
        assertEquals(Paise.Zero, r.untagged)
        assertEquals(20_000.rupees, r.planned)
        assertEquals(9_000.rupees, r.unplanned)
    }

    @Test fun `monthly budgets are measured against the month, others are ignored`() {
        val foodId = runBlocking { ledger.accountIdByName("Expenses:Food")!! }
        val r = report(listOf(BudgetEntity("b", foodId, 8_000.rupees.value, BudgetPeriod.Monthly), BudgetEntity("w", rent, 1.rupees.value, BudgetPeriod.Weekly)))
        val b = r.budgets.single()
        assertEquals("Food", b.category)
        assertTrue(b.isOver)
        assertEquals(9_000.rupees, b.spent)
    }

    @Test fun `net worth runs from the start of the month to its end and the change equals the surplus`() {
        val r = report()
        assertEquals(171_000.rupees, r.netWorthStart)
        assertEquals(202_500.rupees, r.netWorthEnd)
        assertEquals(r.surplus, r.netWorthEnd - r.netWorthStart)
    }

    @Test fun `entries are counted by status, drafts apart`() {
        val e = report().entries
        assertEquals(5, e.posted)
        assertEquals(1, e.drafts)
        assertEquals(0, e.reconciled)
    }

    @Test fun `a month with no income has no rates, not a divide by zero`() {
        val empty = monthEndReport(runBlocking { ledger.snapshot().first() }, Period(LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30)), 1)
        assertNull(empty.savingsRateBps)
        assertNull(empty.investmentRateBps)
        assertEquals(Paise.Zero, empty.averageExpenses) // none of the three months before had any spending
    }

    @Test fun `the average only counts the months that had spending`() {
        // January 2027: of Oct, Nov and Dec only October spent anything (29,000), so the average is 29,000, not a third of it.
        val january = monthEndReport(runBlocking { ledger.snapshot().first() }, Period(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)), 1)
        assertEquals(29_000.rupees, january.averageExpenses)
    }

    @Test fun `rates format with one decimal and keep a minus`() {
        assertEquals("0.0%", formatBps(0))
        assertEquals("12.5%", formatBps(1_250))
        assertEquals("−5.5%", formatBps(-550))
    }
}
