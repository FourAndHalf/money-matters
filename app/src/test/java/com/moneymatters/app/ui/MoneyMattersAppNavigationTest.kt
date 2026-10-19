package com.moneymatters.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.moneymatters.app.feature.accounts.SampleAccounts
import com.moneymatters.app.feature.accounts.SampleLedgers
import com.moneymatters.app.feature.transactions.SampleTransactions
import com.moneymatters.app.feature.dashboard.SampleDashboard
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the dashboard → accounts → ledger → back path through the real app shell. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h852dp-xxhdpi")
class MoneyMattersAppNavigationTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `View all on the dashboard switches to the Accounts tab`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleDashboard.state, SampleAccounts.state, SampleLedgers.byAccountId, SampleTransactions.state, SampleTransactions.byId) }
        }
        compose.onNodeWithText("View all").performClick()
        compose.onNodeWithText("Net worth").assertIsDisplayed()
    }

    @Test fun `tapping an account opens its ledger and back returns to the tab bar`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleDashboard.state, SampleAccounts.state, SampleLedgers.byAccountId, SampleTransactions.state, SampleTransactions.byId) }
        }
        compose.onNodeWithText("View all").performClick()
        compose.onNodeWithText("HDFC Bank").performClick()

        compose.onNodeWithText("Current balance").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsNotDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Net worth").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
    }
}
