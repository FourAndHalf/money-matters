package com.moneymatters.app.feature.market

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.invest.PriceFetcher
import com.moneymatters.app.core.invest.PriceSources
import com.moneymatters.app.core.invest.FetchResult
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.market.MarketBarEntity
import com.moneymatters.app.core.market.MarketRepository
import com.moneymatters.app.core.market.MarketUpdater
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h3000dp-xxhdpi")
class MarketScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: MoneyMattersDatabase
    private lateinit var vm: MarketViewModel
    private val today = LocalDate.of(2026, 11, 6)

    private fun bars(symbol: String, name: String, n: Int, step: Double): List<MarketBarEntity> {
        val dates = generateSequence(today) { it.minusDays(1) }.filter { it.dayOfWeek.value <= 5 }.take(n).toList().reversed()
        return dates.mapIndexed { i, d ->
            val c = ((100 + i * step) * 10_000).toLong()
            MarketBarEntity(symbol, d.toString(), name, "EQ", c, (c * 1.01).toLong(), (c * 0.99).toLong(), c, if (i == 0) c else ((100 + (i - 1) * step) * 10_000).toLong(), 500_000)
        }
    }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        val repo = MarketRepository(db, today = { today })
        runBlocking {
            db.marketDao().upsertBars(bars("RELIANCE", "Reliance Industries Ltd", 80, 1.0) + bars("TCS", "Tata Consultancy Services Ltd", 80, -0.3) + (1..25).flatMap { bars("S$it", "Filler $it", 80, 0.2) })
        }
        vm = MarketViewModel(repo, MarketUpdater(repo, PriceFetcher { FetchResult.Failed(404, "none") }, { PriceSources("x/{yyyyMMdd}", "y") }, { today }))
    }

    @After fun tearDown() = db.close()

    @Test fun `empty store asks for a download`() {
        runBlocking { db.clearAllTables() }
        compose.setContent { MoneyMattersTheme { MarketScreen(vm, held = emptyList(), onBack = {}) } }
        compose.onNodeWithText("Nothing downloaded yet").assertIsDisplayed()
        compose.onNodeWithText("Download a year").assertIsDisplayed()
    }

    @Test fun `search finds a stock and its page shows a scorecard and red flags`() {
        compose.setContent { MoneyMattersTheme { MarketScreen(vm, held = emptyList(), onBack = {}) } }
        compose.waitUntil(5_000) { vm.loaded }
        compose.onNodeWithTag("search-field").performTextInput("reliance")
        compose.onNodeWithText("RELIANCE").assertIsDisplayed()
        compose.onNodeWithText("RELIANCE").performClick()
        compose.waitUntil(5_000) { vm.openBars.isNotEmpty() }
        compose.onNodeWithText("Scorecard").assertIsDisplayed()
        compose.onNodeWithText("Red flags").assertIsDisplayed()
        compose.onNodeWithText("+ Watch").performClick()
        compose.waitUntil(5_000) { vm.watch.any { it.symbol == "RELIANCE" } }
        compose.onNodeWithText("Watching ✓").assertIsDisplayed()
    }

    @Test fun `mood tab explains which factors are missing`() {
        compose.setContent { MoneyMattersTheme { MarketScreen(vm, held = emptyList(), onBack = {}) } }
        compose.waitUntil(5_000) { vm.loaded }
        compose.onNodeWithText("Mood").performClick()
        compose.onNodeWithText("Market mood").assertIsDisplayed()
        compose.onNodeWithText("FII activity").assertIsDisplayed()
        assertTrue(vm.mood!!.factors.count { it.score != null } >= 1)
    }

    @Test fun `screener preset lists matching stocks`() {
        compose.setContent { MoneyMattersTheme { MarketScreen(vm, held = emptyList(), onBack = {}) } }
        compose.waitUntil(5_000) { vm.loaded }
        compose.onNodeWithText("Screener").performClick()
        compose.onNodeWithText("Strong momentum").performClick()
        compose.onNodeWithText("Matches").assertIsDisplayed()
        assertEquals(3, vm.conditions.size)
    }
}
