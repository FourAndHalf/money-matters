package com.moneymatters.app.feature.books

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.ledger.TransactionTags
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.feature.accounts.EntryStatus
import com.moneymatters.app.feature.transactions.TxnStatus
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BooksTest {

    private val today = LocalDate.of(2026, 10, 20)
    private lateinit var repo: LedgerRepository
    private var clock = 1_760_900_000_000L // 2025-10-19T18:53:20Z; only order and the HH:mm label matter
    private lateinit var bank: String
    private lateinit var card: String
    private lateinit var dining: String
    private lateinit var salary: String
    private lateinit var equity: String

    @Before fun setUp() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        repo = LedgerRepository(db, now = { clock++ })
        runBlocking {
            bank = repo.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            card = repo.createAccount("ICICI Rewards", AccountType.Liability, kind = AccountKind.CreditCard)
            dining = repo.createAccount("Expenses:Dining", AccountType.Expense)
            salary = repo.createAccount("Income:Salary", AccountType.Income)
            equity = repo.createAccount("Equity:Opening", AccountType.Equity)
            post(LocalDate.of(2026, 9, 30), "Opening balance: HDFC Bank", PostingLine(bank, debit = 50_000.rupees), PostingLine(equity, credit = 50_000.rupees))
            post(LocalDate.of(2026, 10, 1), "Salary · Oct", PostingLine(bank, debit = 72_000.rupees), PostingLine(salary, credit = 72_000.rupees))
            post(
                LocalDate.of(2026, 10, 19), "Swiggy", PostingLine(dining, debit = 486.rupees), PostingLine(card, credit = 486.rupees),
                tags = TransactionTags(DecisionKind.Want, paymentMethod = PaymentMethod.Card),
            )
            post(LocalDate.of(2026, 10, 19), "Card bill", PostingLine(card, debit = 486.rupees), PostingLine(bank, credit = 486.rupees))
            post(LocalDate.of(2026, 10, 20), "Maybe dinner", PostingLine(dining, debit = 1_200.rupees), PostingLine(bank, credit = 1_200.rupees), status = TransactionStatus.Draft)
        }
    }

    private suspend fun post(
        date: LocalDate, payee: String, vararg lines: PostingLine,
        status: TransactionStatus = TransactionStatus.Posted, tags: TransactionTags = TransactionTags(),
    ) = repo.postTransaction(date, payee, lines.toList(), status = status, tags = tags)

    private fun books(monthStartDay: Int = 1) = runBlocking { buildBooks(repo.snapshot().first(), today, monthStartDay, ZoneOffset.UTC) }

    @Test fun `Home's account stack holds only bank, cash and credit card accounts, while Accounts lists the rest too`() = runBlocking {
        val cash = repo.createAccount("Wallet", AccountType.Asset, kind = AccountKind.Cash)
        val fd = repo.createAccount("SBI FD", AccountType.Asset, kind = AccountKind.FixedDeposit)
        val loan = repo.createAccount("Car loan", AccountType.Liability, kind = AccountKind.Loan)
        val fund = repo.createAccount("Index fund", AccountType.Asset, kind = AccountKind.Investment)
        post(LocalDate.of(2026, 10, 2), "Fund, FD and loan", PostingLine(cash, debit = 1.rupees), PostingLine(equity, credit = 1.rupees))
        val books = books()
        assertEquals(setOf(bank, card, cash), books.home.accounts.map { it.id }.toSet())
        assertEquals(setOf(fd, loan, fund), books.accounts.groups.flatMap { it.items }.map { it.id }.toSet() - books.home.accounts.map { it.id }.toSet())
    }

    @Test fun `account balances count booked entries only, and match the ledger`() = runBlocking {
        val b = books()
        val bankRow = b.accounts.groups.flatMap { it.items }.single { it.id == bank }
        assertEquals(1_21_514.rupees, bankRow.balance) // 50,000 + 72,000 − 486; the ₹1,200 draft doesn't count
        assertEquals(repo.accountBalance(bank), bankRow.balance)
        assertEquals("Bank account · 4 entries", bankRow.subtitle)
        val cardRow = b.accounts.groups.flatMap { it.items }.single { it.id == card }
        assertEquals(Paise.Zero, cardRow.balance)
        assertEquals(listOf("Bank & cash", "Credit cards"), b.accounts.groups.map { it.title })
        assertEquals(b.accounts.assetsTotal - b.accounts.liabilitiesTotal, b.accounts.netWorth)
    }

    @Test fun `list rows read as spend, income, transfer and opening balance`() {
        val rows = books().transactions.groups.flatMap { it.items }.associateBy { it.payee }
        rows.getValue("Swiggy").let {
            assertEquals((-486).rupees, it.amount)
            assertEquals("Dining", it.category)
            assertEquals("ICICI Rewards", it.accountName)
            assertEquals(DecisionKind.Want, it.decision)
        }
        assertEquals(72_000.rupees, rows.getValue("Salary · Oct").amount)
        assertEquals("Salary", rows.getValue("Salary · Oct").category)
        rows.getValue("Card bill").let {
            assertEquals((-486).rupees, it.amount)
            assertEquals("HDFC Bank", it.accountName)
            assertEquals("Transfer to ICICI Rewards", it.category)
        }
        assertEquals("Opening balance", rows.getValue("Opening balance: HDFC Bank").category)
        assertEquals(TxnStatus.Draft, rows.getValue("Maybe dinner").status)
    }

    @Test fun `days are newest first, and entries within a day newest first`() {
        val groups = books().transactions.groups
        assertEquals(listOf("20 Oct", "19 Oct", "1 Oct", "30 Sep"), groups.map { it.dateLabel })
        assertEquals(listOf("Card bill", "Swiggy"), groups[1].items.map { it.payee })
    }

    @Test fun `an account ledger runs its balance oldest to newest, skipping drafts`() {
        val ledger = books().ledgers.getValue(bank)
        val entries = ledger.sections.single().entries
        assertEquals(listOf("Maybe dinner", "Card bill", "Salary · Oct", "Opening balance: HDFC Bank"), entries.map { it.payee })
        assertEquals(EntryStatus.Draft, entries[0].status)
        assertEquals(1_21_514.rupees, entries[0].runningBalance) // the draft doesn't move it
        assertEquals(1_21_514.rupees, entries[1].runningBalance)
        assertEquals(50_000.rupees, entries[3].runningBalance)
        assertEquals(1_21_514.rupees, ledger.currentBalance)
        assertEquals(4, ledger.unreconciledCount)
        // A card's ledger shows spends as positive (more owed) and payments as negative.
        assertEquals(listOf(Paise(-48600), Paise(48600)), books().ledgers.getValue(card).sections.single().entries.map { it.amount })
    }

    @Test fun `ledger meta shows category and payment method`() {
        val swiggy = books().ledgers.getValue(card).sections.single().entries.last()
        assertEquals("Dining · Card", swiggy.meta)
    }

    @Test fun `detail carries postings, tags, time and audit history`() {
        val b = books()
        val id = b.transactions.groups.flatMap { it.items }.single { it.payee == "Swiggy" }.id
        val d = b.details.getValue(id)
        assertEquals(2, d.postings.size)
        assertEquals(true, d.isBalanced)
        assertEquals(DecisionKind.Want, d.decision)
        assertEquals("18:53", d.timeLabel)
        assertEquals("Created · Swiggy (Posted), 2 postings", d.audit.single().summary)
        assertNull(d.reason)
    }

    @Test fun `home totals cover the open period and skip drafts`() {
        val home = books().home
        assertEquals("Since 1 Oct", home.periodLabel)
        assertEquals(72_000.rupees, home.income)
        assertEquals(486.rupees, home.spent)
        assertEquals(1, home.draftCount)
        assertEquals(books().accounts.netWorth, home.netWorth)

        // With payday on the 2nd, the open period starts 2 Oct and the salary on 1 Oct falls outside it.
        val fromSecond = books(monthStartDay = 2).home
        assertEquals("Since 2 Oct", fromSecond.periodLabel)
        assertEquals(Paise.Zero, fromSecond.income)
    }

    @Test fun `the open period starts on the latest month start day`() {
        assertEquals(LocalDate.of(2026, 10, 1), periodStart(today, 1))
        assertEquals(LocalDate.of(2026, 10, 20), periodStart(today, 20))
        assertEquals(LocalDate.of(2026, 9, 25), periodStart(today, 25))
        assertEquals(LocalDate.of(2025, 12, 28), periodStart(LocalDate.of(2026, 1, 5), 28))
    }
}
