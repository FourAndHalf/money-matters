package com.moneymatters.app.feature.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.feature.onboarding.NewAccountDraft
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
@Config(sdk = [35], qualifiers = "w392dp-h900dp-xxhdpi")
class AccountStackTest {

    @get:Rule val compose = createComposeRule()

    private val bank = AccountSummary("b", AccountKind.Bank, "HDFC Bank", "", 1_84_250.rupees, entryCount = 12)
    private val card = AccountSummary("c", AccountKind.CreditCard, "HDFC Regalia", "", 25_000.rupees, isLiability = true, entryCount = 3, cardLimit = 1_00_000.rupees, statementDay = 22)
    private val cash = AccountSummary("w", AccountKind.Cash, "Wallet", "", 3_200.rupees, entryCount = 0)

    @Test fun `the stack shows the front account and its panel, and the dots bring another forward`() {
        var opened: String? = null
        compose.setContent { MoneyMattersTheme { AccountStack(listOf(bank, card, cash), onOpenAccount = { opened = it }) } }
        compose.onNodeWithText("12 entries").assertIsDisplayed()
        compose.onNodeWithText("Open ledger").performClick()
        assertEquals("b", opened)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/accounts_stack_bank.png")
    }

    @Test fun `a card's panel shows utilisation, what is available and its statement day`() {
        compose.setContent { MoneyMattersTheme { AccountStack(listOf(card), onOpenAccount = {}) } }
        compose.onNodeWithText("25% used").assertIsDisplayed()
        compose.onNodeWithText("₹75,000").assertIsDisplayed()
        compose.onNodeWithText("22nd of the month").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/accounts_stack_card.png")
    }

    @Test fun `the add account screen previews the card as it is typed`() {
        compose.setContent {
            MoneyMattersTheme {
                AddAccountScreen(NewAccountDraft("HDFC Regalia", AccountKind.CreditCard, "8,000", "1,00,000", "22"), null, false, {}, {}, {})
            }
        }
        compose.onNodeWithText("Limit ₹1,00,000").assertIsDisplayed()
        compose.onNodeWithText("Statement 22").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/add_account_card.png")
    }

    @Test fun `only a card's panel offers to edit it, and a card without a limit offers to add one`() {
        var edited: AccountSummary? = null
        compose.setContent { MoneyMattersTheme { AccountStack(listOf(card), onOpenAccount = {}, onEditCard = { edited = it }) } }
        compose.onNodeWithText("Edit card").performClick()
        assertEquals("c", edited?.id)
    }

    @Test fun `a bank account has no edit card button`() {
        compose.setContent { MoneyMattersTheme { AccountStack(listOf(bank), onOpenAccount = {}) } }
        compose.onNodeWithText("Edit card").assertDoesNotExist()
    }

    @Test fun `a card added without a limit says Add limit`() {
        compose.setContent { MoneyMattersTheme { AccountStack(listOf(card.copy(cardLimit = null, statementDay = null)), onOpenAccount = {}) } }
        compose.onNodeWithText("Add limit").assertIsDisplayed()
    }

    @Test fun `the edit card screen needs a limit above zero and a day from 1 to 28`() {
        assertEquals(true, CardEditForm("c", "HDFC", "1,50,000", "5").canSave)
        assertEquals(false, CardEditForm("c", "HDFC", "0", "5").canSave)
        assertEquals(false, CardEditForm("c", "HDFC", "1,50,000", "29").canSave)
        assertEquals(false, CardEditForm("c", "HDFC", "", "").canSave)
        var saved = false
        compose.setContent { MoneyMattersTheme { EditCardScreen(CardEditForm("c", "HDFC Regalia", "1,50,000", "5"), null, false, {}, { saved = true }, {}) } }
        compose.onNodeWithText("Edit card").assertIsDisplayed()
        compose.onNodeWithText("Save changes").performClick()
        assertEquals(true, saved)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/edit_card.png")
    }
}
