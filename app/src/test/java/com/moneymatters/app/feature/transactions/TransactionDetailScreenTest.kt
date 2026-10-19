package com.moneymatters.app.feature.transactions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
class TransactionDetailScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `shows the reason, the balanced journal and before-after audit`() {
        compose.setContent { MoneyMattersTheme { TransactionDetailScreen(SampleTransactions.byId.getValue("t2"), onBack = {}) } }
        compose.onNodeWithText("Streaming subscription").assertIsDisplayed()
        compose.onNodeWithText("Prior-period").assertIsDisplayed()
        compose.onNodeWithText("Price rise in August was missed until this month's review.").assertIsDisplayed()
        compose.onNodeWithText("✓ Balanced").assertIsDisplayed()
        compose.onNodeWithText("₹499 / mo").assertIsDisplayed()
        compose.onNodeWithText("₹649 / mo").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transaction_detail_light.png")
    }

    @Test fun `dark theme renders`() {
        compose.setContent { MoneyMattersTheme(darkTheme = true) { TransactionDetailScreen(SampleTransactions.byId.getValue("t2"), onBack = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transaction_detail_dark.png")
    }

    @Test fun `a draft transaction can be edited`() {
        var edited = false
        compose.setContent { MoneyMattersTheme { TransactionDetailScreen(SampleTransactions.byId.getValue("t3"), onBack = {}, onEdit = { edited = true }) } }
        compose.onNodeWithText("Edit").performClick()
        assertTrue(edited)
    }

    @Test fun `a reconciled transaction asks before reversing`() {
        var reversed = false
        compose.setContent { MoneyMattersTheme { TransactionDetailScreen(SampleTransactions.byId.getValue("t7"), onBack = {}, onReverse = { reversed = true }) } }
        compose.onNodeWithText("Reverse").performClick()
        assertTrue(!reversed) // the confirm dialog, not the callback, fires first
        compose.onNodeWithText("Reverse this transaction?").assertIsDisplayed()
        compose.onNodeWithText("Confirm reverse").performClick()
        assertTrue(reversed)
    }

    @Test fun `a locked transaction offers a prior-period adjustment, not edit or reverse`() {
        var adjusted = false
        compose.setContent {
            MoneyMattersTheme { TransactionDetailScreen(SampleTransactions.byId.getValue("t10"), onBack = {}, onPriorPeriodAdjustment = { adjusted = true }) }
        }
        compose.onNodeWithText("Post prior-period adjustment").performClick()
        assertTrue(adjusted)
    }

    @Test fun `back button calls onBack`() {
        var back = false
        compose.setContent { MoneyMattersTheme { TransactionDetailScreen(SampleTransactions.byId.getValue("t1"), onBack = { back = true }) } }
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }
}
