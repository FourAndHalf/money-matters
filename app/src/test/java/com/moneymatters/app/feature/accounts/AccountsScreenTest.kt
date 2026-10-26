package com.moneymatters.app.feature.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h2600dp-xxhdpi")
class AccountsScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `shows net worth and every account group`() {
        compose.setContent { MoneyMattersTheme { AccountsScreen(SampleAccounts.state, onOpenAccount = {}) } }
        compose.onNodeWithText("Net worth").assertIsDisplayed()
        compose.onNodeWithText("Bank & cash").assertIsDisplayed()
        compose.onNodeWithText("Credit cards").assertIsDisplayed()
        compose.onNodeWithText("Loans").assertIsDisplayed()
        compose.onNodeWithText("Earmarks").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/accounts_light.png")
    }

    @Test fun `dark theme renders`() {
        compose.setContent { MoneyMattersTheme(darkTheme = true) { AccountsScreen(SampleAccounts.state, onOpenAccount = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/accounts_dark.png")
    }

    @Test fun `tapping an account row opens its ledger`() {
        var opened: String? = null
        compose.setContent { MoneyMattersTheme { AccountsScreen(SampleAccounts.state, onOpenAccount = { opened = it }) } }
        // The name also appears on the stack card and its panel; the list row comes last.
        compose.onAllNodesWithText("HDFC Bank").onLast().performClick()
        assertEquals("hdfc-bank", opened)
    }

    @Test fun `only the group of the account up front is open, the rest are collapsed`() {
        var opened: String? = null
        compose.setContent { MoneyMattersTheme { AccountsScreen(SampleAccounts.state, onOpenAccount = { opened = it }) } }
        // HDFC Bank leads the stack, so Bank & cash is open and Loans is not: its last "Car loan" is only a stack card.
        compose.onAllNodesWithText("Car loan").onLast().performClick()
        assertNull(opened)
        compose.onAllNodesWithText("HDFC Bank").onLast().performClick()
        assertEquals("hdfc-bank", opened)
    }

    @Test fun `tapping a group header opens it and closes the one that was open`() {
        var opened: String? = null
        compose.setContent { MoneyMattersTheme { AccountsScreen(SampleAccounts.state, onOpenAccount = { opened = it }) } }
        compose.onNodeWithText("Loans").performClick()
        compose.onAllNodesWithText("Car loan").onLast().performClick()
        assertEquals("car-loan", opened)
        opened = null
        compose.onAllNodesWithText("HDFC Bank").onLast().performClick()
        assertNull(opened)
        compose.onNodeWithText("Loans").performClick()
        compose.onAllNodesWithText("Car loan").onLast().performClick()
        assertNull(opened)
    }

    @Test fun `net worth is assets minus liabilities`() {
        val s = SampleAccounts.state
        assertEquals(s.assetsTotal - s.liabilitiesTotal, s.netWorth)
    }
}
