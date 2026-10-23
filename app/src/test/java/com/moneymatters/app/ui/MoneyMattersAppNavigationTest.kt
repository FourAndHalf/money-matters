package com.moneymatters.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.close.CloseRepository
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.feature.close.CloseViewModel
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.profile.ProfileStore
import com.moneymatters.app.core.profile.TaxRegime
import com.moneymatters.app.core.profile.UserProfile
import com.moneymatters.app.core.security.PasscodeStore
import com.moneymatters.app.feature.books.SampleBooks
import com.moneymatters.app.feature.invest.InvestViewModel
import com.moneymatters.app.feature.quickadd.QuickAddPrefs
import com.moneymatters.app.feature.quickadd.QuickAddViewModel
import com.moneymatters.app.feature.settings.AiSettingsStore
import com.moneymatters.app.feature.settings.SettingsPrefs
import com.moneymatters.app.feature.settings.SettingsViewModel
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
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

    private fun quickAddModel(): QuickAddViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        return QuickAddViewModel(LedgerRepository(db))
    }

    private fun quickAddPrefs() = QuickAddPrefs(ApplicationProvider.getApplicationContext())

    private fun closeModel(): CloseViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        val ledger = LedgerRepository(db)
        val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24)))
        return CloseViewModel(ledger, InvestRepository(db, ledger, rules), CloseRepository(db, ledger), monthStartDay = { 1 })
    }

    private fun investModel(): InvestViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        val ledger = LedgerRepository(db)
        val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24)))
        return InvestViewModel(ledger, InvestRepository(db, ledger, rules), rules)
    }

    private fun settingsModel(): SettingsViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profiles = ProfileStore(context.getSharedPreferences("nav_test_profile", Context.MODE_PRIVATE))
        profiles.completeSetup(UserProfile(LocalDate.of(1995, 1, 1), 1, TaxRegime.New, null, false, emptySet(), false, false))
        val passcodes = PasscodeStore(context.getSharedPreferences("nav_test_passcode", Context.MODE_PRIVATE))
        val db = Room.inMemoryDatabaseBuilder(context, MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        val ai = AiSettingsStore(context.getSharedPreferences("nav_test_ai", Context.MODE_PRIVATE))
        return SettingsViewModel(LedgerRepository(db), profiles, passcodes, SettingsPrefs(context), ai)
    }

    @Test fun `View all on Home switches to the Accounts tab`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel(), investModel(), closeModel()) }
        }
        compose.onAllNodesWithText("View all")[0].performClick()
        compose.onNodeWithText("Net worth").assertIsDisplayed()
    }

    @Test fun `tapping an account opens its ledger and back returns to the tab bar`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel(), investModel(), closeModel()) }
        }
        compose.onAllNodesWithText("View all")[0].performClick()
        // The name also appears on the stack card and its panel; the list row comes last.
        compose.onAllNodesWithText("HDFC Bank").onLast().performClick()

        compose.onNodeWithText("Current balance").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsNotDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Net worth").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test fun `tapping Quick add opens the entry screen and back returns to the tab bar`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel(), investModel(), closeModel()) }
        }
        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.onAllNodesWithText("Quick add")[0].assertIsDisplayed()
        compose.onNodeWithContentDescription("Quick add").assertIsNotDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Net worth").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quick add").assertIsDisplayed()
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun `the Invest tab opens the portfolio and shows its empty state`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel(), investModel(), closeModel()) }
        }
        compose.onNodeWithText("Invest").performClick()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasText("No stocks yet"), 5_000)
        compose.onNodeWithText("No stocks yet").assertIsDisplayed()
    }

    @Test fun `the plus on Accounts adds an account, not an expense`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel(), investModel(), closeModel()) }
        }
        compose.onAllNodesWithText("View all")[0].performClick()
        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.onNodeWithText("Add account").assertIsDisplayed()
        compose.onNodeWithText("Card").performClick()
        compose.onNodeWithText("Amount owed today (₹)").assertIsDisplayed()
        compose.onNodeWithText("Credit limit (₹)").assertIsDisplayed()
        compose.onNodeWithText("Statement day of the month").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Net worth").assertIsDisplayed()
    }

    @Test fun `the plus on Transactions still opens Quick add`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel(), investModel(), closeModel()) }
        }
        compose.onNodeWithText("Txns").performClick()
        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.onAllNodesWithText("Quick add")[0].assertIsDisplayed()
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun `the plus on Invest opens one page with a stock, fund and FD toggle`() {
        compose.setContent {
            MoneyMattersTheme { MoneyMattersApp(SampleBooks.state, quickAddModel(), quickAddPrefs(), settingsModel(), investModel(), closeModel()) }
        }
        compose.onNodeWithText("Invest").performClick()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasText("No stocks yet"), 5_000)
        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.onNodeWithText("Add investment").assertIsDisplayed()
        compose.onNodeWithText("Symbol").assertIsDisplayed()
        compose.onNodeWithText("Fund").performClick()
        compose.onNodeWithText("Fund name").assertIsDisplayed()
        compose.onNodeWithText("Bond").performClick()
        compose.onNodeWithText("Bond name").assertIsDisplayed()
        compose.onNodeWithText("FD").performClick()
        compose.onNodeWithText("Principal (₹)").assertIsDisplayed()
        compose.onNodeWithText("Add investment").assertIsDisplayed()
    }
}
