package com.moneymatters.app.core.recurring

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.close.CloseRepository
import com.moneymatters.app.core.close.Period
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun rule(
    frequency: Frequency = Frequency.Monthly,
    start: String = "2026-01-31",
    end: String? = null,
    last: String? = null,
) = RecurringRuleEntity("r", "Rent", "bank", "rent", 25_000_00, frequency, start, end, autoPost = true, lastGeneratedOn = last)

class RecurringScheduleTest {
    @Test fun `monthly keeps the start day and clamps in short months without drifting`() {
        val dates = rule(start = "2026-01-31").dueDates(LocalDate.of(2026, 5, 31))
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31").map(LocalDate::parse),
            dates,
        )
    }

    @Test fun `weekly and yearly step by their own period`() {
        assertEquals(
            listOf("2026-10-05", "2026-10-12", "2026-10-19").map(LocalDate::parse),
            rule(Frequency.Weekly, "2026-10-05").dueDates(LocalDate.of(2026, 10, 25)),
        )
        assertEquals(
            listOf("2024-02-29", "2025-02-28", "2026-02-28").map(LocalDate::parse),
            rule(Frequency.Yearly, "2024-02-29").dueDates(LocalDate.of(2026, 12, 31)),
        )
    }

    @Test fun `nothing is due before the start date`() {
        assertTrue(rule(start = "2026-12-01").dueDates(LocalDate.of(2026, 11, 30)).isEmpty())
        assertEquals(LocalDate.of(2026, 12, 1), rule(start = "2026-12-01").nextDue())
    }

    @Test fun `only dates after the last one generated are due`() {
        val r = rule(start = "2026-01-05", last = "2026-03-05")
        assertEquals(listOf("2026-04-05", "2026-05-05").map(LocalDate::parse), r.dueDates(LocalDate.of(2026, 5, 20)))
        assertEquals(LocalDate.of(2026, 4, 5), r.nextDue())
    }

    @Test fun `the end date stops the schedule and there is no next due after it`() {
        val r = rule(start = "2026-01-05", end = "2026-03-10", last = "2026-03-05")
        assertTrue(r.dueDates(LocalDate.of(2026, 12, 31)).isEmpty())
        assertNull(r.nextDue())
    }

    @Test fun `a run is capped, and the rest follow on the next run`() {
        val r = rule(Frequency.Weekly, "2000-01-03")
        assertEquals(MAX_PER_RUN, r.dueDates(LocalDate.of(2026, 10, 31)).size)
        val resumed = r.copy(lastGeneratedOn = r.dueDates(LocalDate.of(2026, 10, 31)).last().toString())
        assertEquals(LocalDate.of(2000, 1, 3).plusWeeks(MAX_PER_RUN.toLong()), resumed.nextDue())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecurringRepositoryTest {
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var repo: RecurringRepository
    private lateinit var bank: String
    private lateinit var rent: String
    private lateinit var salary: String

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        repo = RecurringRepository(db, ledger)
        runBlocking {
            bank = ledger.createAccount("HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
            rent = ledger.createAccount("Expenses:Rent", AccountType.Expense)
            salary = ledger.createAccount("Income:Salary", AccountType.Income)
        }
    }

    private fun newRule(start: String = "2026-08-05", autoPost: Boolean = true, end: String? = null) = runBlocking {
        repo.create(
            RecurringRuleEntity(
                "", "Rent", bank, rent, 25_000_00, Frequency.Monthly, start, end, autoPost,
                decision = DecisionKind.Need, paymentMethod = PaymentMethod.NetBanking,
            ),
        )
    }

    private fun entries() = runBlocking { ledger.snapshot().first() }

    @Test fun `due occurrences are booked as balanced posted entries carrying the rule's tags`() {
        newRule()
        val result = runBlocking { repo.generateDue(LocalDate.of(2026, 10, 20)) }

        assertEquals(GenerationResult(posted = 3), result)
        val snap = entries()
        assertEquals(3, snap.transactions.size)
        snap.transactions.forEach { t ->
            assertEquals(TransactionStatus.Posted, t.status)
            assertEquals("Rent", t.payee)
            assertEquals(DecisionKind.Need, t.decision)
            assertEquals(PaymentMethod.NetBanking, t.paymentMethod)
            val legs = snap.postings.filter { it.transactionId == t.id }
            assertEquals(0L, legs.sumOf { (it.debitPaise ?: 0L) - (it.creditPaise ?: 0L) }) // every entry balances
        }
        assertEquals(Paise(-75_000_00), runBlocking { ledger.accountBalance(bank) })
        assertEquals(setOf("2026-08-05", "2026-09-05", "2026-10-05"), snap.transactions.map { it.date }.toSet())
    }

    @Test fun `running again books nothing twice, and the next due date moves on`() {
        val id = newRule()
        runBlocking { repo.generateDue(LocalDate.of(2026, 10, 20)) }
        val again = runBlocking { repo.generateDue(LocalDate.of(2026, 10, 20)) }

        assertEquals(0, again.total)
        assertEquals(3, entries().transactions.size)
        assertEquals(LocalDate.of(2026, 11, 5), runBlocking { repo.rule(id)!!.nextDue() })

        runBlocking { repo.generateDue(LocalDate.of(2026, 11, 5)) }
        assertEquals(4, entries().transactions.size)
    }

    @Test fun `a rule that isn't auto-post leaves drafts, which never touch a balance`() {
        newRule(autoPost = false)
        val result = runBlocking { repo.generateDue(LocalDate.of(2026, 9, 10)) }

        assertEquals(GenerationResult(drafts = 2), result)
        assertTrue(entries().transactions.all { it.status == TransactionStatus.Draft })
        assertEquals(Paise.Zero, runBlocking { ledger.accountBalance(bank) })
    }

    @Test fun `dates inside a closed month are skipped and counted, not booked and not retried`() {
        val close = CloseRepository(db, ledger, nowMillis = { 1L })
        runBlocking { close.close(Period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)), "August 2026", LocalDate.of(2026, 9, 2)) }
        newRule(start = "2026-08-05")

        val result = runBlocking { repo.generateDue(LocalDate.of(2026, 9, 10)) }

        assertEquals(GenerationResult(posted = 1, skippedClosed = 1), result)
        assertEquals(listOf("2026-09-05"), entries().transactions.map { it.date })
        assertEquals(0, runBlocking { repo.generateDue(LocalDate.of(2026, 9, 10)) }.skippedClosed)
    }

    @Test fun `a paused rule books nothing, and resuming picks up where it left off`() {
        val id = newRule(start = "2026-09-05")
        runBlocking { repo.generateDue(LocalDate.of(2026, 9, 10)) }
        runBlocking { repo.setActive(id, false) }
        assertEquals(0, runBlocking { repo.generateDue(LocalDate.of(2026, 12, 10)) }.total)

        runBlocking { repo.setActive(id, true) }
        assertEquals(3, runBlocking { repo.generateDue(LocalDate.of(2026, 12, 10)) }.posted) // Oct, Nov, Dec
    }

    @Test fun `a rule pointing at an archived account is skipped, not failed`() {
        newRule()
        runBlocking { ledger.archiveAccount(rent) }

        val result = runBlocking { repo.generateDue(LocalDate.of(2026, 10, 20)) }

        assertEquals(GenerationResult(rulesSkipped = 1), result)
        assertEquals(0, entries().transactions.size)
    }

    @Test fun `deleting a rule keeps the entries it booked`() {
        val id = newRule()
        runBlocking { repo.generateDue(LocalDate.of(2026, 10, 20)) }
        runBlocking { repo.delete(id) }

        assertNull(runBlocking { repo.rule(id) })
        assertEquals(3, entries().transactions.size)
    }

    @Test fun `a rule must make sense before it is saved`() {
        val ok = RecurringRuleEntity("", "Rent", bank, rent, 100, Frequency.Monthly, "2026-08-05", null, true)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.create(ok.copy(name = " ")) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.create(ok.copy(amountPaise = 0)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.create(ok.copy(toAccountId = bank)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.create(ok.copy(endDate = "2026-07-01")) } }
    }

    @Test fun `editing a rule keeps its progress so already-booked dates aren't booked again`() {
        val id = newRule()
        runBlocking { repo.generateDue(LocalDate.of(2026, 10, 20)) }
        runBlocking { repo.update(repo.rule(id)!!.copy(amountPaise = 30_000_00, lastGeneratedOn = null)) }

        assertEquals("2026-10-05", runBlocking { repo.rule(id)!!.lastGeneratedOn })
        assertEquals(0, runBlocking { repo.generateDue(LocalDate.of(2026, 10, 20)) }.total)
    }

    @Test fun `income rules credit income and debit the bank`() {
        runBlocking {
            repo.create(RecurringRuleEntity("", "Salary", salary, bank, 72_000_00, Frequency.Monthly, "2026-10-01", null, true))
            repo.generateDue(LocalDate.of(2026, 10, 31))
        }
        assertEquals(72_000.rupees, runBlocking { ledger.accountBalance(bank) })
    }
}
