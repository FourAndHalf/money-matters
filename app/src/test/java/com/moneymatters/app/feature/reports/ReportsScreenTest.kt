package com.moneymatters.app.feature.reports

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PostingEntity
import com.moneymatters.app.core.ledger.TransactionEntity
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.core.reports.ReportPeriod
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h2000dp-xxhdpi")
class ReportsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 29)
    private val accounts = listOf(
        AccountEntity("bank", "HDFC Bank", AccountType.Asset), AccountEntity("sal", "Income:Salary", AccountType.Income),
        AccountEntity("rent", "Expenses:Rent", AccountType.Expense), AccountEntity("open", "Equity:Opening", AccountType.Equity),
    )

    private fun entry(id: String, date: String, debit: String, credit: String, amount: Long) =
        TransactionEntity(id, date, id, null, TransactionStatus.Posted) to listOf(
            PostingEntity(transactionId = id, accountId = debit, debitPaise = amount.rupees.value),
            PostingEntity(transactionId = id, accountId = credit, creditPaise = amount.rupees.value),
        )

    private val ledger: LedgerSnapshot = listOf(
        entry("o", "2026-04-01", "bank", "open", 50_000), entry("s", "2026-10-01", "bank", "sal", 72_000), entry("r", "2026-10-05", "rent", "bank", 25_000),
    ).let { e -> LedgerSnapshot(accounts, e.map { it.first }, e.flatMap { it.second }, emptyList()) }

    private fun show() = compose.setContent { MoneyMattersTheme { ReportsContent(ledger, 1, {}, today) } }

    @Test fun `the income statement is the first report, for the current month`() {
        show()
        compose.onNodeWithText("Total income").assertIsDisplayed()
        compose.onNodeWithText("Income:Salary").assertIsDisplayed()
        compose.onNodeWithText("Expenses:Rent").assertIsDisplayed()
        compose.onNodeWithText("Surplus").assertIsDisplayed()
        compose.onNodeWithText("1 Oct – 31 Oct 2026").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/reports_income.png")
    }

    @Test fun `the balance sheet shows it balances`() {
        show()
        compose.onNodeWithText("Balance sheet").performClick()
        compose.onNodeWithText("✓ Assets equal liabilities plus equity").assertIsDisplayed()
        compose.onNodeWithText("Total assets").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/reports_balance.png")
    }

    @Test fun `the trial balance shows debits equal credits`() {
        show()
        compose.onNodeWithText("Trial balance").performClick()
        compose.onNodeWithText("✓ Debits equal credits").assertIsDisplayed()
    }

    @Test fun `the period can step back and can't step past the current one`() {
        show()
        assertEquals(0, compose.onAllNodesWithText("›").fetchSemanticsNodes().size)
        compose.onNodeWithText("‹").performClick()
        compose.onNodeWithText("1 Sep – 30 Sep 2026").assertIsDisplayed()
        compose.onNodeWithText("No income in this period.").assertIsDisplayed()
        compose.onNodeWithText("›").performClick()
        compose.onNodeWithText("1 Oct – 31 Oct 2026").assertIsDisplayed()
    }

    @Test fun `the tax year view labels the April to March year`() {
        show()
        compose.onNodeWithText("Tax year").performClick()
        compose.onNodeWithText("Tax year 2026–27").assertIsDisplayed()
        compose.onNodeWithText("Total income").assertIsDisplayed()
    }

    @Test fun `the cash flow statement shows opening, the categories and closing`() {
        show()
        compose.onNodeWithText("Cash flow").performClick()
        compose.onNodeWithText("Cash at the start").assertIsDisplayed()
        compose.onNodeWithText("Net change in cash").assertIsDisplayed()
        compose.onNodeWithText("Cash at the end").assertIsDisplayed()
    }

    @Test fun `the spending view ranks categories and shows the trend`() {
        show()
        compose.onNodeWithText("Spending").performScrollTo().performClick() // the last pill sits past the edge of a narrow phone
        compose.onNodeWithText("By category").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Last 6 months").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/reports_spending.png")
    }

    @Test fun `export offers a CSV of the report and a Beancount of the ledger`() {
        show()
        compose.onNodeWithText("This report as CSV").assertIsDisplayed()
        compose.onNodeWithText("Whole ledger as Beancount").assertIsDisplayed()
    }

    @Test fun `the range label reads naturally`() {
        assertEquals("Tax year 2026–27", rangeLabel(DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)), ReportPeriod.TaxYear))
        assertEquals("25 Oct – 24 Nov 2026", rangeLabel(DateRange(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)), ReportPeriod.Month))
    }
}
