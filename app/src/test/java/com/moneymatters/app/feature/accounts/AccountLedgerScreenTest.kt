package com.moneymatters.app.feature.accounts

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h2000dp-xxhdpi")
class AccountLedgerScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `bank ledger is a flat list with a running balance`() {
        compose.setContent { MoneyMattersTheme { AccountLedgerScreen(SampleLedgers.byAccountId.getValue("hdfc-bank"), onBack = {}) } }
        compose.onNodeWithText("HDFC Bank").assertIsDisplayed()
        compose.onNodeWithText("Current balance").assertIsDisplayed()
        compose.onNodeWithText("Bal ₹1,84,250").assertIsDisplayed()
        // Salary and Rent are both reconciled.
        compose.onAllNodesWithText("Reconciled").assertCountEquals(2)
        compose.onNodeWithText("Draft").assertIsDisplayed()
        compose.onNodeWithText("Prior-period").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_ledger_bank_light.png")
    }

    @Test fun `credit card ledger groups entries by statement cycle`() {
        compose.setContent { MoneyMattersTheme { AccountLedgerScreen(SampleLedgers.byAccountId.getValue("icici-credit"), onBack = {}) } }
        compose.onNodeWithText("Outstanding").assertIsDisplayed()
        compose.onNodeWithText("Statement: 10 Sep – 10 Oct").assertIsDisplayed()
        compose.onNodeWithText("Statement: 10 Aug – 9 Sep").assertIsDisplayed()
        compose.onNodeWithText("Charges ₹23,480").assertIsDisplayed()
        // That cycle's purchases were fully paid off; its charges must still show, not net to zero.
        compose.onNodeWithText("Charges ₹18,200").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_ledger_card_light.png")
    }

    @Test fun `dark theme renders`() {
        compose.setContent { MoneyMattersTheme(darkTheme = true) { AccountLedgerScreen(SampleLedgers.byAccountId.getValue("icici-credit"), onBack = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_ledger_card_dark.png")
    }

    @Test fun `back button calls onBack`() {
        var backPressed = false
        compose.setContent { MoneyMattersTheme { AccountLedgerScreen(SampleLedgers.byAccountId.getValue("cash"), onBack = { backPressed = true }) } }
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(backPressed)
    }

    @Test fun `reconcile button calls onReconcile`() {
        var reconciled = false
        compose.setContent {
            MoneyMattersTheme { AccountLedgerScreen(SampleLedgers.byAccountId.getValue("cash"), onBack = {}, onReconcile = { reconciled = true }) }
        }
        compose.onNodeWithText("Reconcile").performClick()
        assertTrue(reconciled)
    }
}
