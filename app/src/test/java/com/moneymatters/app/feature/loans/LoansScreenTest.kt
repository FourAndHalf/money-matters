package com.moneymatters.app.feature.loans

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PostingEntity
import com.moneymatters.app.core.ledger.TransactionEntity
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.loan.LoanEntity
import com.moneymatters.app.core.loan.LoanPaymentEntity
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h2400dp-xxhdpi")
class LoansScreenTest {
    @get:Rule val compose = createComposeRule()

    private val loanAccount = AccountEntity("loan", "Home Loan", AccountType.Liability, kind = AccountKind.Loan)
    private val bank = AccountEntity("bank", "HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
    private val equity = AccountEntity("open", "Equity:Opening", AccountType.Equity)
    private val interest = AccountEntity("int", "Expenses:Interest", AccountType.Expense)
    private val terms = LoanEntity("loan", 500_000.rupees.value, 850, 10_259.rupees.value, "2026-11-05", "bank")

    private fun ledger(extra: Pair<TransactionEntity, List<PostingEntity>>? = null): LedgerSnapshot {
        val open = TransactionEntity("o", "2026-10-01", "Opening", null, TransactionStatus.Posted) to listOf(
            PostingEntity(transactionId = "o", accountId = "open", debitPaise = 500_000.rupees.value),
            PostingEntity(transactionId = "o", accountId = "loan", creditPaise = 500_000.rupees.value),
        )
        val all = listOfNotNull(open, extra)
        return LedgerSnapshot(listOf(loanAccount, bank, equity, interest), all.map { it.first }, all.flatMap { it.second }, emptyList())
    }

    private val emiEntry = TransactionEntity("e1", "2026-11-05", "Home Loan EMI", null, TransactionStatus.Posted) to listOf(
        PostingEntity(transactionId = "e1", accountId = "loan", debitPaise = 671_733),
        PostingEntity(transactionId = "e1", accountId = "int", debitPaise = 354_167),
        PostingEntity(transactionId = "e1", accountId = "bank", creditPaise = 1_025_900),
    )
    private val emiPayment = LoanPaymentEntity("p1", "loan", "2026-11-05", "e1", 671_733, 354_167)

    @Test fun `the state shows what is owed from the books and works out the rest`() {
        val s = buildLoans(listOf(terms), emptyList(), ledger())
        val r = s.rows.single()

        assertEquals(500_000.rupees, r.outstanding)
        assertEquals(Paise.Zero, r.principalRepaid)
        assertEquals(60, r.instalmentsLeft)
        assertEquals(Paise(11_548_537), r.interestLeft)
        assertEquals(LocalDate.of(2026, 11, 5), r.nextDue)
        assertEquals(Paise(354_167), r.nextSplitInterest)
        assertEquals(500_000.rupees, s.totalOutstanding)
    }

    @Test fun `a paid EMI shows in the history and moves on the next due date`() {
        val s = buildLoans(listOf(terms), listOf(emiPayment), ledger(emiEntry))
        val r = s.rows.single()

        assertEquals(Paise(49_328_267), r.outstanding)
        assertEquals(Paise(671_733), r.principalRepaid)
        assertEquals(Paise(354_167), r.interestPaid)
        assertEquals(LocalDate.of(2026, 12, 5), r.nextDue)
        assertEquals(59, r.instalmentsLeft)
        assertEquals(1, s.details.getValue("loan").history.size)
    }

    @Test fun `a payment record whose entry was deleted no longer counts`() {
        val s = buildLoans(listOf(terms), listOf(emiPayment), ledger()) // the entry e1 isn't in the books
        assertEquals(LocalDate.of(2026, 11, 5), s.rows.single().nextDue)
        assertEquals(Paise.Zero, s.rows.single().interestPaid)
    }

    @Test fun `a loan is overdue once its EMI date has passed`() {
        val s = buildLoans(listOf(terms), emptyList(), ledger())
        assertFalse(s.asOf(LocalDate.of(2026, 11, 5)).rows.single().isOverdue) // due today isn't overdue
        assertTrue(s.asOf(LocalDate.of(2026, 11, 6)).rows.single().isOverdue)
    }

    @Test fun `a loan paid to zero is cleared and has no next date`() {
        val repaid = TransactionEntity("e2", "2026-11-05", "Payoff", null, TransactionStatus.Posted) to listOf(
            PostingEntity(transactionId = "e2", accountId = "loan", debitPaise = 500_000.rupees.value),
            PostingEntity(transactionId = "e2", accountId = "bank", creditPaise = 500_000.rupees.value),
        )
        val r = buildLoans(listOf(terms), emptyList(), ledger(repaid)).rows.single()
        assertTrue(r.isCleared)
        assertNull(r.nextDue)
        assertEquals(0, r.instalmentsLeft)
    }

    @Test fun `rates are typed as percentages and kept as basis points`() {
        assertEquals(850, parseRateBps("8.5"))
        assertEquals(1200, parseRateBps("12"))
        assertEquals(1050, parseRateBps("10.5%"))
        assertEquals(0, parseRateBps("0"))
        assertNull(parseRateBps("abc"))
        assertNull(parseRateBps("101"))
        assertEquals("8.50%", rateLabel(850))
    }

    @Test fun `the form works out an EMI from the amount, rate and tenure`() {
        val f = LoanForm(LocalDate.of(2026, 11, 5), accountId = "loan", originalText = "500000", rateText = "8.5", tenureText = "60", payFromId = "bank")
        assertEquals(10_259.rupees, f.suggestedEmi)
        assertFalse(f.canSave) // no EMI typed yet
        assertTrue(f.copy(emiText = "10259").canSave)
        assertEquals(850, f.copy(emiText = "10259").toLoan().rateBps)
    }

    @Test fun `the list shows each loan with what is owed and when the next EMI is due`() {
        val state = buildLoans(listOf(terms), emptyList(), ledger()).asOf(LocalDate.of(2026, 10, 29))
        compose.setContent { MoneyMattersTheme { LoansListContent(state, {}, {}, {}) } }
        compose.onNodeWithText("Home Loan").assertIsDisplayed()
        compose.onNodeWithText("8.50% · EMI ₹10,259 · next 5 Nov 2026").assertIsDisplayed()
        compose.onNodeWithText("Owed in total").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/loans_list.png")
    }

    @Test fun `an empty list tells you to add a loan account first`() {
        compose.setContent { MoneyMattersTheme { LoansListContent(LoansState(emptyList(), emptyMap()), {}, {}, {}) } }
        compose.onNodeWithText("Add a loan account under Accounts first, then describe its rate and EMI here to see its schedule and interest.").assertIsDisplayed()
    }

    @Test fun `an overdue loan is flagged on the list`() {
        val state = buildLoans(listOf(terms), emptyList(), ledger()).asOf(LocalDate.of(2026, 11, 20))
        compose.setContent { MoneyMattersTheme { LoansListContent(state, {}, {}, {}) } }
        compose.onNodeWithText("EMI overdue").assertIsDisplayed()
    }

    @Test fun `the detail shows the split of the next EMI, the schedule and lets you pay`() {
        var paid = false
        val detail = buildLoans(listOf(terms), emptyList(), ledger()).details.getValue("loan")
        compose.setContent { MoneyMattersTheme { LoanDetailContent(detail, LocalDate.of(2026, 11, 5), "", null, {}, {}, { paid = true }, {}, {}, {}, {}) } }
        compose.onNodeWithText("Interest still to pay").assertIsDisplayed()
        compose.onNodeWithText("Next EMI due 5 Nov 2026: ₹6,717.33 principal + ₹3,541.67 interest.").assertIsDisplayed()
        compose.onNodeWithText("Pay EMI of ₹10,259.00").performClick()
        assertTrue(paid)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/loans_detail.png")
    }

    @Test fun `a cleared loan offers no payment`() {
        val repaid = TransactionEntity("e2", "2026-11-05", "Payoff", null, TransactionStatus.Posted) to listOf(
            PostingEntity(transactionId = "e2", accountId = "loan", debitPaise = 500_000.rupees.value),
            PostingEntity(transactionId = "e2", accountId = "bank", creditPaise = 500_000.rupees.value),
        )
        val detail = buildLoans(listOf(terms), emptyList(), ledger(repaid)).details.getValue("loan")
        compose.setContent { MoneyMattersTheme { LoanDetailContent(detail, LocalDate.of(2026, 11, 5), "", null, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Paid off").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Prepay").fetchSemanticsNodes().size)
    }

    @Test fun `the form offers undescribed loan accounts and blocks saving until complete`() {
        var saved = false
        val form = LoanForm(LocalDate.of(2026, 11, 5))
        compose.setContent { MoneyMattersTheme { LoanFormContent(form, listOf(loanAccount), listOf(bank), null, {}, {}, { saved = true }, {}) } }
        compose.onNodeWithText("Home Loan").assertIsDisplayed()
        compose.onNodeWithText("Save loan details").performClick()
        assertFalse(saved)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/loans_form.png")
    }

    @Test fun `the form with no loan accounts says to add one`() {
        compose.setContent { MoneyMattersTheme { LoanFormContent(LoanForm(LocalDate.of(2026, 11, 5)), emptyList(), listOf(bank), null, {}, {}, {}, {}) } }
        compose.onNodeWithText("Add a loan account under Accounts first.").assertIsDisplayed()
    }
}
