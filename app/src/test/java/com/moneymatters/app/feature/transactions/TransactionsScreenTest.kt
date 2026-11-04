package com.moneymatters.app.feature.transactions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h3000dp-xxhdpi")
class TransactionsScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `shows every day group and the suspense inbox`() {
        compose.setContent { MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}) } }
        compose.onNodeWithText("Transactions").assertIsDisplayed()
        compose.onNodeWithText("Suspense inbox").assertIsDisplayed()
        compose.onNodeWithText("19 Oct").assertIsDisplayed()
        compose.onNodeWithText("1 Sep").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transactions_light.png")
    }

    @Test fun `dark theme renders`() {
        compose.setContent { MoneyMattersTheme(darkTheme = true) { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transactions_dark.png")
    }

    @Test fun `tapping a transaction opens its detail`() {
        var opened: String? = null
        compose.setContent { MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = { opened = it }) } }
        compose.onNodeWithText("Swiggy").performClick()
        assertEquals("t1", opened)
    }

    @Test fun `searching narrows the list to matching transactions`() {
        compose.setContent { MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}) } }
        compose.onNodeWithTag("search-field").performTextInput("fuel")
        compose.onNodeWithText("Fuel").assertIsDisplayed()
        compose.onAllNodesWithText("Swiggy").assertCountEquals(0)
    }

    @Test fun `filtering to Locked status shows only locked transactions`() {
        compose.setContent { MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}) } }
        compose.onNodeWithTag("status-filter-Locked").performScrollTo().performClick()
        compose.onNodeWithText("Salary · Sep").assertIsDisplayed()
        compose.onAllNodesWithText("Swiggy").assertCountEquals(0)
    }

    @Test fun `the last status filter is reachable by scrolling the row`() {
        compose.setContent { MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}) } }
        compose.onNodeWithTag("status-filter-Prior-period").performScrollTo().assertIsDisplayed()
    }

    @Test fun `with accountant mode off there is no Accountant view to pick`() {
        compose.setContent { MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}) } }
        compose.onAllNodesWithText("Accountant").assertCountEquals(0)
        compose.onAllNodesWithText("✓ Balanced").assertCountEquals(0)
    }

    @Test fun `accountant mode shows Dr and Cr postings and can flip back to everyday`() {
        compose.setContent { MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}, accountantMode = true) } }
        compose.onNodeWithText("Everyday").performClick()
        compose.onAllNodesWithText("✓ Balanced").assertCountEquals(0)
        compose.onNodeWithText("Accountant").performClick()
        compose.onNodeWithText("Expenses:Dining").assertIsDisplayed()
        // Every day's journal panel shows its own balanced footer.
        compose.onAllNodesWithText("✓ Balanced").onFirst().assertIsDisplayed()
    }

    @Test fun `the Settings accountant switch opens Transactions in the Dr and Cr view`() {
        compose.setContent { MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}, accountantMode = true) } }
        compose.onNodeWithText("Expenses:Dining").assertIsDisplayed()
        compose.onAllNodesWithText("✓ Balanced").onFirst().assertIsDisplayed()
    }

    @Test fun `turning the switch off and on moves the view with it`() {
        val on = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}, accountantMode = on.value) } }
        compose.onNodeWithText("Expenses:Dining").assertIsDisplayed()
        on.value = false
        compose.waitForIdle()
        compose.onAllNodesWithText("✓ Balanced").assertCountEquals(0)
        on.value = true
        compose.waitForIdle()
        compose.onNodeWithText("Expenses:Dining").assertIsDisplayed()
    }
}
