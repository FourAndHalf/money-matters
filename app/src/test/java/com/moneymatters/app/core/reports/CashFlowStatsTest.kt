package com.moneymatters.app.core.reports

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.ledger.TransactionTags
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
class CashFlowTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var bank: String
    private lateinit var wallet: String
    private lateinit var stocks: String
    private lateinit var fd: String
    private lateinit var card: String
    private lateinit var loan: String
    private lateinit var friend: String
    private lateinit var salary: String
    private lateinit var rent: String
    private lateinit var dining: String
    private lateinit var interest: String
    private lateinit var opening: String
    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            wallet = ledger.createAccount("Wallet", AccountType.Asset, kind = AccountKind.Cash)
            stocks = ledger.createAccount("Infosys", AccountType.Asset, kind = AccountKind.Investment)
            fd = ledger.createAccount("FD HDFC", AccountType.Asset, kind = AccountKind.FixedDeposit)
            friend = ledger.createAccount("Assets:Receivable:Ravi", AccountType.Asset)
            card = ledger.createAccount("HDFC Card", AccountType.Liability, kind = AccountKind.CreditCard)
            loan = ledger.createAccount("Home Loan", AccountType.Liability, kind = AccountKind.Loan)
            salary = ledger.createAccount("Income:Salary", AccountType.Income)
            rent = ledger.createAccount("Expenses:Rent", AccountType.Expense)
            dining = ledger.createAccount("Expenses:Dining", AccountType.Expense)
            interest = ledger.createAccount("Expenses:Interest", AccountType.Expense)
            opening = ledger.createAccount("Equity:Opening", AccountType.Equity)
        }
        post("2026-09-01", "Opening", bank, opening, 10_000_000)
        post("2026-10-01", "Salary", bank, salary, 6_000_000)
        post("2026-10-05", "Rent", rent, bank, 2_000_000)
        post("2026-10-06", "Buy", stocks, bank, 1_000_000)
        post("2026-10-07", "Open FD", fd, bank, 500_000)
        post("2026-10-08", "Dinner", dining, card, 120_000) // on the card: no cash moves
        post("2026-10-15", "Card payment", card, bank, 120_000)
        post("2026-10-16", "Withdrawal", wallet, bank, 200_000) // between two cash accounts
        post("2026-10-18", "Lent to Ravi", friend, bank, 100_000)
        post("2026-10-20", "Draft", dining, bank, 99_900, status = TransactionStatus.Draft)
        runBlocking {
            ledger.postTransaction(
                LocalDate.of(2026, 10, 25), "EMI",
                listOf(PostingLine(loan, debit = Paise(671_733)), PostingLine(interest, debit = Paise(354_167)), PostingLine(bank, credit = Paise(1_025_900))),
            )
        }
    }

    private fun post(date: String, payee: String, debit: String, credit: String, paise: Long, status: TransactionStatus = TransactionStatus.Posted) = runBlocking {
        ledger.postTransaction(LocalDate.parse(date), payee, listOf(PostingLine(debit, debit = Paise(paise)), PostingLine(credit, credit = Paise(paise))), status = status)
    }

    private fun snap(): LedgerSnapshot = runBlocking { ledger.snapshot().first() }

    @Test fun `each entry's cash is sorted by what was on the other side`() {
        val c = cashFlow(snap(), october)

        assertEquals(Paise(3_645_833), c.total(CashCategory.Operating)) // 60,000 salary less 20,000 rent less 3,541.67 EMI interest
        assertEquals(Paise(-1_500_000), c.total(CashCategory.Investing)) // 10,000 shares and 5,000 FD
        assertEquals(Paise(-791_733), c.total(CashCategory.Financing)) // 1,200 card payment and 6,717.33 EMI principal
        assertEquals(Paise(-100_000), c.total(CashCategory.Other)) // lent to Ravi
    }

    @Test fun `opening plus the net change is always the closing balance`() {
        val c = cashFlow(snap(), october)
        assertEquals(10_000_000L, c.opening.value) // 1,00,000 at the start of October
        assertEquals(Paise(1_254_100), c.netChange)
        assertEquals(Paise(11_254_100), c.closing)
        assertEquals(c.closing, c.opening + c.netChange)
    }

    @Test fun `money moved between your own cash accounts, card spending and drafts don't appear`() {
        val names = cashFlow(snap(), october).sections.values.flatten().map { it.name }
        assertTrue("Wallet" !in names && "Expenses:Dining" !in names && "HDFC Card" in names)
        assertEquals(1, names.count { it == "HDFC Card" }) // the payment appears; the dinner on the card does not
    }

    @Test fun `the identity holds for every period, including ones with nothing`() {
        for (range in listOf(october, DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), DateRange(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 19)), DateRange(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)))) {
            val c = cashFlow(snap(), range)
            assertEquals("for $range", c.closing, c.opening + c.netChange)
        }
        assertTrue(cashFlow(snap(), DateRange(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31))).sections.isEmpty())
    }

    @Test fun `a salary split shows its parts as operating and keeps the retirement saving apart`() {
        val epf = runBlocking { ledger.createAccount("EPF", AccountType.Asset) }
        val tds = runBlocking { ledger.createAccount("Expenses:Tax:TDS", AccountType.Expense) }
        runBlocking {
            ledger.postTransaction(
                LocalDate.of(2026, 10, 30), "Pay",
                listOf(PostingLine(bank, debit = 72_000.rupees), PostingLine(epf, debit = 3_600.rupees), PostingLine(tds, debit = 8_000.rupees), PostingLine(salary, credit = 83_600.rupees)),
            )
        }
        val c = cashFlow(snap(), october)
        val lines = c.sections.getValue(CashCategory.Operating).associate { it.name to it.amount }
        assertEquals(Paise(6_000_000 + 8_360_000), lines.getValue("Income:Salary")) // both salaries this month, gross
        assertEquals((-8_000).rupees, lines.getValue("Expenses:Tax:TDS"))
        assertEquals((-3_600).rupees, c.sections.getValue(CashCategory.Other).single { it.name == "EPF" }.amount)
        assertEquals(c.closing, c.opening + c.netChange)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var bank: String
    private lateinit var food: String
    private lateinit var travel: String
    private lateinit var salary: String
    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset)
            food = ledger.createAccount("Expenses:Food", AccountType.Expense)
            travel = ledger.createAccount("Expenses:Travel", AccountType.Expense)
            salary = ledger.createAccount("Income:Salary", AccountType.Income)
        }
    }

    private fun spend(account: String, amount: Long, payee: String, date: String = "2026-10-10", method: PaymentMethod? = null, status: TransactionStatus = TransactionStatus.Posted) = runBlocking {
        ledger.postTransaction(LocalDate.parse(date), payee, listOf(PostingLine(account, debit = amount.rupees), PostingLine(bank, credit = amount.rupees)), status = status, tags = TransactionTags(paymentMethod = method))
    }

    private fun snap(): LedgerSnapshot = runBlocking { ledger.snapshot().first() }

    @Test fun `categories are ranked with their share of the total`() {
        spend(food, 3_000, "Swiggy"); spend(travel, 1_000, "Uber")
        val s = categoryShares(snap(), october)
        assertEquals(listOf("Expenses:Food", "Expenses:Travel"), s.map { it.name })
        assertEquals(listOf(7_500, 2_500), s.map { it.shareBps })
        assertEquals(10_000, s.sumOf { it.shareBps })
    }

    @Test fun `shares round down so they never add up to more than the whole`() {
        spend(food, 100, "a"); spend(travel, 100, "b"); spend(ledger.let { runBlocking { it.createAccount("Expenses:Other", AccountType.Expense) } }, 100, "c")
        assertEquals(listOf(3_333, 3_333, 3_333), categoryShares(snap(), october).map { it.shareBps })
    }

    @Test fun `a payee's capitals don't split it, and refunds reduce it`() {
        spend(food, 500, "Swiggy"); spend(food, 300, "swiggy "); spend(food, 200, "Zomato")
        runBlocking { ledger.postTransaction(LocalDate.of(2026, 10, 12), "Swiggy", listOf(PostingLine(bank, debit = 100.rupees), PostingLine(food, credit = 100.rupees))) }
        val p = payeeShares(snap(), october)
        assertEquals(listOf("Swiggy", "Zomato"), p.map { it.name })
        assertEquals(700.rupees, p.first().amount) // 500 + 300 less the 100 refunded
    }

    @Test fun `payment methods are grouped, with unrecorded ones together`() {
        spend(food, 600, "a", method = PaymentMethod.Upi); spend(food, 300, "b", method = PaymentMethod.Upi); spend(food, 100, "c")
        val m = paymentMethodShares(snap(), october)
        assertEquals(listOf("UPI", "Not recorded"), m.map { it.name })
        assertEquals(900.rupees, m.first().amount)
    }

    @Test fun `drafts and other months are left out of every ranking`() {
        spend(food, 999, "Draft", status = TransactionStatus.Draft); spend(food, 500, "Old", date = "2026-09-10")
        assertTrue(categoryShares(snap(), october).isEmpty() && payeeShares(snap(), october).isEmpty() && paymentMethodShares(snap(), october).isEmpty())
    }

    @Test fun `the payee list is capped`() {
        (1..15).forEach { spend(food, 100L * it, "Shop $it") }
        assertEquals(10, payeeShares(snap(), october).size)
        assertEquals("Shop 15", payeeShares(snap(), october).first().name)
    }

    @Test fun `the trend lists the last months, oldest first, with income and spending`() {
        spend(food, 1_000, "a", date = "2026-08-10"); spend(food, 2_000, "b", date = "2026-10-10")
        runBlocking { ledger.postTransaction(LocalDate.of(2026, 10, 1), "Salary", listOf(PostingLine(bank, debit = 50_000.rupees), PostingLine(salary, credit = 50_000.rupees))) }
        val t = monthlyTrend(snap(), LocalDate.of(2026, 10, 29), 1, 4)
        assertEquals(listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)), t.map { it.range.start })
        assertEquals(listOf(Paise.Zero, 1_000.rupees, Paise.Zero, 2_000.rupees), t.map { it.expenses })
        assertEquals(50_000.rupees, t.last().income)
    }

    @Test fun `labels read as words`() {
        assertEquals("UPI", PaymentMethod.Upi.label())
        assertEquals("Not recorded", (null as PaymentMethod?).label())
    }
}
