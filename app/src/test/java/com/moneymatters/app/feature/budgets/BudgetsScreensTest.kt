package com.moneymatters.app.feature.budgets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.budget.BudgetEntity
import com.moneymatters.app.core.budget.BudgetPeriod
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PostingEntity
import com.moneymatters.app.core.ledger.TransactionEntity
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.feature.dashboard.BudgetPace
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h1400dp-xxhdpi")
class BudgetsScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 20)
    private val dining = AccountEntity("dining", "Expenses:Dining", AccountType.Expense)
    private val travel = AccountEntity("travel", "Expenses:Travel", AccountType.Expense)
    private val bank = AccountEntity("bank", "HDFC Bank", AccountType.Asset)

    private fun ledger(vararg spends: Triple<String, Long, String>): LedgerSnapshot {
        val txns = spends.mapIndexed { i, s -> TransactionEntity("t$i", s.third, "x", null, TransactionStatus.Posted) }
        val posts = spends.flatMapIndexed { i, s ->
            listOf(PostingEntity(transactionId = "t$i", accountId = s.first, debitPaise = s.second.rupees.value), PostingEntity(transactionId = "t$i", accountId = "bank", creditPaise = s.second.rupees.value))
        }
        return LedgerSnapshot(listOf(dining, travel, bank), txns, posts, emptyList())
    }

    private val budgets = listOf(BudgetEntity("b1", "dining", 10_000.rupees.value, BudgetPeriod.Monthly), BudgetEntity("b2", "travel", 5_000.rupees.value, BudgetPeriod.Monthly))

    @Test fun `a budget over its cap is flagged over, with the amount over, and sorts first`() {
        val state = buildBudgets(budgets, ledger(Triple("dining", 4_000, "2026-10-10"), Triple("travel", 6_000, "2026-10-12")), today, 1)

        assertEquals(listOf("Travel", "Dining"), state.rows.map { it.category })
        val travel = state.rows.first()
        assertTrue(travel.isOver)
        assertEquals((-1_000).rupees, travel.left)
        assertEquals(BudgetPace.OverPace, travel.pace)
        assertEquals(10_000.rupees, state.monthlySpent)
        assertEquals(15_000.rupees, state.monthlyBudget)
    }

    @Test fun `spending a share of the budget in step with the month is on pace`() {
        val state = buildBudgets(budgets.take(1), ledger(Triple("dining", 6_500, "2026-10-10")), today, 1)
        val row = state.rows.single()
        assertEquals(BudgetPace.OnPace, row.pace) // 65% used, 20 of 31 days = 65% elapsed
        assertFalse(row.isOver)
        assertEquals(3_500.rupees, row.left)
    }

    @Test fun `a budget for a deleted category is dropped, not shown broken`() {
        val state = buildBudgets(listOf(BudgetEntity("b", "gone", 100, BudgetPeriod.Monthly)), ledger(), today, 1)
        assertTrue(state.rows.isEmpty())
    }

    @Test fun `the list shows each category with what is left and says when it runs out`() {
        val state = buildBudgets(budgets.take(1), ledger(Triple("dining", 6_500, "2026-10-10")), today, 1)
        compose.setContent { MoneyMattersTheme { BudgetsListContent(state, {}, {}, {}) } }
        compose.onNodeWithText("Dining").assertIsDisplayed()
        compose.onNodeWithText("₹6,500 of ₹10,000 · ₹3,500 left").assertIsDisplayed()
        compose.onNodeWithText("This month").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/budgets_list.png")
    }

    @Test fun `an overspent category says by how much`() {
        val state = buildBudgets(budgets.drop(1), ledger(Triple("travel", 6_000, "2026-10-12")), today, 1)
        compose.setContent { MoneyMattersTheme { BudgetsListContent(state, {}, {}, {}) } }
        compose.onNodeWithText("₹6,000 of ₹5,000 · over by ₹1,000").assertIsDisplayed()
        compose.onNodeWithText("Over").assertIsDisplayed()
    }

    @Test fun `an empty list explains what to do`() {
        compose.setContent { MoneyMattersTheme { BudgetsListContent(BudgetsState(emptyList(), Paise.Zero, Paise.Zero), {}, {}, {}) } }
        compose.onNodeWithText("Set a budget for a category to see how your spending is pacing against it.").assertIsDisplayed()
    }

    @Test fun `tapping a budget opens it`() {
        var opened: String? = null
        val state = buildBudgets(budgets.take(1), ledger(), today, 1)
        compose.setContent { MoneyMattersTheme { BudgetsListContent(state, {}, { opened = it }, {}) } }
        compose.onNodeWithText("Dining").performClick()
        assertEquals("b1", opened)
    }

    @Test fun `the form needs a category and an amount`() {
        assertFalse(BudgetForm().canSave)
        assertFalse(BudgetForm(accountId = "dining").canSave)
        assertFalse(BudgetForm(accountId = "dining", amountText = "0").canSave)
        assertTrue(BudgetForm(accountId = "dining", amountText = "15000").canSave)
    }

    @Test fun `the form lists expense categories only`() {
        assertEquals(listOf("Expenses:Dining", "Expenses:Travel"), budgetableCategories(listOf(bank, travel, dining, dining.copy(id = "old", name = "Old", isArchived = true))).map { it.name })
    }

    @Test fun `the form shows its fields, and editing adds delete`() {
        compose.setContent { MoneyMattersTheme { BudgetFormContent(BudgetForm(), budgetableCategories(listOf(dining, travel)), null, {}, {}, {}, {}) } }
        compose.onNodeWithText("Set budget").assertIsDisplayed()
        compose.onNodeWithText("Dining").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Delete budget").fetchSemanticsNodes().size)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/budgets_form.png")
    }

    @Test fun `editing shows delete and keeps the category fixed`() {
        var changed: BudgetForm? = null
        val form = BudgetForm("b1", "dining", "10000", BudgetPeriod.Monthly)
        compose.setContent { MoneyMattersTheme { BudgetFormContent(form, budgetableCategories(listOf(dining, travel)), null, { changed = it }, {}, {}, {}) } }
        compose.onNodeWithText("Delete budget").assertIsDisplayed()
        compose.onNodeWithText("Travel").performClick()
        assertEquals(null, changed) // the category of an existing budget can't be switched
    }

    @Test fun `saving is blocked until the form is complete`() {
        var saved = false
        compose.setContent { MoneyMattersTheme { BudgetFormContent(BudgetForm(), budgetableCategories(listOf(dining)), null, {}, { saved = true }, {}, {}) } }
        compose.onNodeWithText("Set budget").performClick()
        assertFalse(saved)
    }
}
