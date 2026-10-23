package com.moneymatters.app.feature.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionTags
import com.moneymatters.app.core.money.rupees
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
@Config(sdk = [35], qualifiers = "w392dp-h900dp-xxhdpi")
class EditEntryScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun form(editable: Boolean) = EditEntryForm(
        "t1", "2026-10-23", "Opening balance: State Bank of India", "", if (editable) "22658" else "",
        listOf("Dr State Bank of India", "Cr Equity:Opening"), editable,
        listOf(PostingLine("a", debit = 22_658.rupees), PostingLine("b", credit = 22_658.rupees)), TransactionTags(),
    )

    @Test fun `a plain entry offers its amount and saves`() {
        var saved = false
        compose.setContent {
            var f by remember { mutableStateOf(form(true)) }
            MoneyMattersTheme { EditEntryScreen(f, null, false, { f = it }, { saved = true }, {}) }
        }
        compose.onNodeWithText("Amount (₹)").assertIsDisplayed()
        compose.onNodeWithText("Dr State Bank of India").assertIsDisplayed()
        compose.onNodeWithText("Save changes").performClick()
        assertTrue(saved)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/edit_entry.png")
    }

    @Test fun `a complex entry explains why its amounts are fixed`() {
        compose.setContent { MoneyMattersTheme { EditEntryScreen(form(false), "That date is in a closed month", false, {}, {}, {}) } }
        compose.onNodeWithText("The amounts and accounts of this entry can't be changed here. To change them, reverse it and enter it again.").assertIsDisplayed()
        compose.onNodeWithText("That date is in a closed month").assertIsDisplayed()
    }
}
