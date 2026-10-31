package com.moneymatters.app.core.export

import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PostingEntity
import com.moneymatters.app.core.ledger.TransactionEntity
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.core.reports.StatementLine
import com.moneymatters.app.core.reports.Share
import com.moneymatters.app.core.reports.IncomeStatement
import com.moneymatters.app.core.reports.TrialBalance
import com.moneymatters.app.core.reports.TrialRow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeancountTest {
    private val bank = AccountEntity("bank", "HDFC Bank", AccountType.Asset)
    private val rent = AccountEntity("rent", "Expenses:Rent", AccountType.Expense)
    private val salary = AccountEntity("sal", "Income:Salary", AccountType.Income)
    private val open = AccountEntity("open", "Equity:Opening", AccountType.Equity)
    private val card = AccountEntity("card", "HDFC Card", AccountType.Liability)

    private fun txn(id: String, date: String, payee: String, memo: String? = null, status: TransactionStatus = TransactionStatus.Posted, decision: DecisionKind? = null) =
        TransactionEntity(id, date, payee, memo, status, decision)

    private fun leg(txn: String, account: String, debit: Long? = null, credit: Long? = null) = PostingEntity(transactionId = txn, accountId = account, debitPaise = debit, creditPaise = credit)

    @Test fun `names get a Beancount root and are not doubled`() {
        val names = beancountNames(listOf(bank, rent, salary, open, card))
        assertEquals("Assets:HDFC-Bank", names["bank"])
        assertEquals("Expenses:Rent", names["rent"])
        assertEquals("Income:Salary", names["sal"])
        assertEquals("Equity:Opening", names["open"])
        assertEquals("Liabilities:HDFC-Card", names["card"])
    }

    @Test fun `names that come out alike are numbered, and every name is valid`() {
        val a = AccountEntity("a", "Home loan", AccountType.Liability)
        val b = AccountEntity("b", "home-loan", AccountType.Liability)
        val c = AccountEntity("c", "Cash & Wallet (old)", AccountType.Asset)
        val d = AccountEntity("d", "???", AccountType.Asset)
        val names = beancountNames(listOf(a, b, c, d))
        assertEquals(4, names.values.toSet().size)
        names.values.forEach { assertTrue(it, Regex("^(Assets|Liabilities|Equity|Income|Expenses)(:[A-Z0-9][A-Za-z0-9-]*)+$").matches(it)) }
        assertEquals("Assets:Cash-Wallet-old", names["c"])
    }

    @Test fun `a ledger exports as Beancount, matching a hand-written file`() {
        val ledger = LedgerSnapshot(
            listOf(bank, rent, salary, open),
            listOf(txn("t0", "2026-04-01", "Opening"), txn("t1", "2026-10-05", "Rent", "October \"flat\"", decision = DecisionKind.Need), txn("t2", "2026-10-20", "Lunch", status = TransactionStatus.Draft)),
            listOf(
                leg("t0", "bank", debit = 5_000_000), leg("t0", "open", credit = 5_000_000),
                leg("t1", "rent", debit = 2_500_050), leg("t1", "bank", credit = 2_500_050),
                leg("t2", "rent", debit = 10_000), leg("t2", "bank", credit = 10_000),
            ),
            emptyList(),
        )

        val expected = """
            ; Money Matters export
            ; Amounts are in rupees; entries marked ! are drafts.
            option "title" "Money Matters"
            option "operating_currency" "INR"

            2026-04-01 open Assets:HDFC-Bank INR
            2026-04-01 open Equity:Opening INR
            2026-10-05 open Expenses:Rent INR

            2026-04-01 * "Opening"
              status: "Posted"
              Assets:HDFC-Bank  50000.00 INR
              Equity:Opening  -50000.00 INR

            2026-10-05 * "Rent" "October \"flat\""
              decision: "Need"
              status: "Posted"
              Expenses:Rent  25000.50 INR
              Assets:HDFC-Bank  -25000.50 INR

            2026-10-20 ! "Lunch"
              status: "Draft"
              Expenses:Rent  100.00 INR
              Assets:HDFC-Bank  -100.00 INR
        """.trimIndent() + "\n"

        assertEquals(expected, beancount(ledger))
    }

    @Test fun `every exported entry sums to zero`() {
        val ledger = LedgerSnapshot(
            listOf(bank, rent, card),
            listOf(txn("t1", "2026-10-05", "Dinner")),
            listOf(leg("t1", "rent", debit = 123_457), leg("t1", "card", credit = 123_457)),
            emptyList(),
        )
        val sums = beancount(ledger).lines().filter { it.startsWith("  ") && it.contains(" INR") }
            .map { Regex("(-?\\d+\\.\\d{2}) INR").find(it)!!.groupValues[1].replace(".", "").toLong() }
        assertEquals(0L, sums.sum())
        assertEquals(listOf(123_457L, -123_457L), sums)
    }

    @Test fun `an empty ledger is still a valid file`() {
        val text = beancount(LedgerSnapshot(emptyList(), emptyList(), emptyList(), emptyList()))
        assertTrue(text.startsWith("; Money Matters export"))
        assertTrue(!text.contains(" open "))
    }
}

class StatementCsvTest {
    @Test fun `fields with commas, quotes or line breaks are quoted`() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
        assertEquals("\"two\nlines\"", csvField("two\nlines"))
    }

    @Test fun `a field a spreadsheet would run as a formula is defused`() {
        assertEquals("'=SUM(A1)", csvField("=SUM(A1)"))
        assertEquals("'+91 98765", csvField("+91 98765"))
        assertEquals("'@cmd", csvField("@cmd"))
        assertEquals("-5.00", plainAmount(Paise(-500))) // a real negative amount stays a number
    }

    @Test fun `amounts are plain numbers with two decimals`() {
        assertEquals("1234567.89", plainAmount(Paise(123_456_789)))
        assertEquals("0.05", plainAmount(Paise(5)))
        assertEquals("-0.99", plainAmount(Paise(-99)))
    }

    @Test fun `an income statement exports its sections and totals`() {
        val s = IncomeStatement(
            DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
            listOf(StatementLine("s", "Income:Salary", 60_000.rupees)), listOf(StatementLine("r", "Expenses:Rent, flat", 20_000.rupees)),
        )
        assertEquals(
            "Section,Account,Amount (INR)\nIncome,Income:Salary,60000.00\nTotal,Total income,60000.00\nExpenses,\"Expenses:Rent, flat\",20000.00\nTotal,Total expenses,20000.00\nResult,Income less expenses,40000.00\n",
            incomeStatementCsv(s),
        )
    }

    @Test fun `a trial balance exports debits and credits, blank on the other side`() {
        val t = TrialBalance(LocalDate.of(2026, 10, 31), listOf(TrialRow("b", "HDFC Bank", AccountType.Asset, 100.rupees, Paise.Zero), TrialRow("o", "Equity:Opening", AccountType.Equity, Paise.Zero, 100.rupees)))
        assertEquals("Account,Debit (INR),Credit (INR)\nHDFC Bank,100.00,\nEquity:Opening,,100.00\nTotals,100.00,100.00\n", trialBalanceCsv(t))
    }

    @Test fun `shares export with a readable percentage`() {
        assertEquals("Category,Amount (INR),Share\nFood,3000.00,75.00%\n", sharesCsv("Category", listOf(Share("Food", 3_000.rupees, 7_500))))
    }
}
