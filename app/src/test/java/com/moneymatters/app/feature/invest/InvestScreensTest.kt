package com.moneymatters.app.feature.invest

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymatters.app.core.invest.RowError
import com.moneymatters.app.core.invest.SymbolPreview
import com.moneymatters.app.core.invest.TradeParse
import com.moneymatters.app.core.invest.TradePreview
import com.moneymatters.app.core.invest.TradeSide
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val Manual = PriceTag("✍ Manual", Tone.Info)

private fun row(id: String, name: String, symbol: String, units: String, cost: Long, market: Long?, tag: PriceTag = Manual) = HoldingRow(
    id = id, name = name, symbol = symbol, unitsLabel = units, cost = cost.rupees, avgCostLabel = "₹1,234.00",
    marketValue = market?.rupees, gain = market?.let { (it - cost).rupees }, gainPercent = market?.let { (it - cost).toDouble() / cost },
    priceLabel = market?.let { "₹1,512.35" }, priceTag = if (market == null) PriceTag("No price", Tone.Neutral) else tag, hasUnits = true,
)

private val infy = row("h1", "Infosys (INFY)", "INFY", "12.5", 1_00_000, 1_20_000)
private val tcs = row("h2", "Tata Consultancy (TCS)", "TCS", "4", 60_000, 54_000)
private val wipro = row("h3", "Wipro (WIPRO)", "WIPRO", "100", 25_000, null)

private val state = InvestState(
    holdings = listOf(infy, tcs, wipro),
    cost = 1_85_000.rupees, value = (1_20_000 + 54_000 + 25_000).rupees, gain = 14_000.rupees, gainPercent = 14_000.0 / 1_60_000,
    xirr = 0.1234, unpricedCount = 1, details = emptyMap(),
)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h1400dp-xxhdpi")
class InvestScreensTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `portfolio shows value, return, each stock with its price status and a warning for unpriced ones`() {
        compose.setContent { MoneyMattersTheme { InvestScreen(state, onOpenHolding = {}, onBuy = {}, onImport = {}) } }
        compose.onNodeWithText("Portfolio value").assertIsDisplayed()
        compose.onNodeWithText("Infosys (INFY)").assertIsDisplayed()
        compose.onNodeWithText("Wipro (WIPRO)").assertIsDisplayed()
        compose.onNodeWithText("No price").assertIsDisplayed()
        compose.onNodeWithText("1 without a price, counted at cost").assertIsDisplayed()
        compose.onNodeWithText("+12.34%").assertIsDisplayed().also { }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_portfolio_light.png")
    }

    @Test fun `portfolio renders in dark`() {
        compose.setContent { MoneyMattersTheme(darkTheme = true) { InvestScreen(state, onOpenHolding = {}, onBuy = {}, onImport = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_portfolio_dark.png")
    }

    @Test fun `an empty portfolio invites a buy or an import`() {
        val empty = InvestState(emptyList(), 0.rupees, 0.rupees, 0.rupees, null, null, 0, emptyMap())
        var bought = false
        var imported = false
        compose.setContent { MoneyMattersTheme { InvestScreen(empty, onOpenHolding = {}, onBuy = { bought = true }, onImport = { imported = true }) } }
        compose.onNodeWithText("No stocks yet").assertIsDisplayed()
        compose.onNodeWithText("+ Buy").performClick()
        compose.onNodeWithText("Import").performClick()
        assertTrue(bought && imported)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_empty.png")
    }

    @Test fun `tapping a stock opens it`() {
        var opened: String? = null
        compose.setContent { MoneyMattersTheme { InvestScreen(state, onOpenHolding = { opened = it }, onBuy = {}, onImport = {}) } }
        compose.onNodeWithText("Tata Consultancy (TCS)").performClick()
        assertEquals("h2", opened)
    }

    private val detail = HoldingDetail(
        row = infy,
        lots = listOf(
            LotRow("10 Jan 2025", "10", 40_000.rupees, "Long-term", true),
            LotRow("1 Jun 2026", "2.5", 10_000.rupees, "Short-term", false),
        ),
        transactions = listOf(
            HoldingTxn("t2", "1 Jun 2026", true, "2.5", 10_000.rupees),
            HoldingTxn("t1", "10 Jan 2025", true, "10", 40_000.rupees),
        ),
        prices = listOf(PricePoint("17 Oct 2026", "₹1,512.35", "manual", Manual)),
        xirr = 0.1234,
    )

    @Test fun `holding detail shows facts, lots with holding period, trades and prices`() {
        var sell = false
        compose.setContent { MoneyMattersTheme { HoldingDetailScreen(detail, onBack = {}, onBuy = {}, onSell = { sell = true }, onUpdatePrice = {}) } }
        compose.onNodeWithText("Current value").assertIsDisplayed()
        compose.onNodeWithText("Long-term").assertIsDisplayed()
        compose.onNodeWithText("Short-term").assertIsDisplayed()
        compose.onNodeWithText("Price history").assertIsDisplayed()
        compose.onNodeWithText("Sell").performClick()
        assertTrue(sell)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_holding_light.png")
    }

    @Test fun `a fully sold holding offers no sell button`() {
        val sold = detail.copy(row = infy.copy(hasUnits = false))
        compose.setContent { MoneyMattersTheme { HoldingDetailScreen(sold, onBack = {}, onBuy = {}, onSell = {}, onUpdatePrice = {}) } }
        assertEquals(0, compose.onAllNodesWithText("Sell").fetchSemanticsNodes().size)
    }

    @Test fun `the buy form shows the implied price once units and amount are typed`() {
        val form = TradeForm(TradeSide.Buy, LocalDate.of(2026, 10, 20), symbol = "INFY", unitsText = "10", amountText = "15,123.50", accountId = "bank")
        compose.setContent { MoneyMattersTheme { TradeContent(form, emptyList(), null, false, {}, {}, {}) } }
        compose.onNodeWithText("Buy stock").assertIsDisplayed()
        compose.onNodeWithText("≈ ₹1,512.35 per unit").assertIsDisplayed()
        compose.onNodeWithText("Add a bank or cash account first.").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_buy_form.png")
    }

    @Test fun `the sell form for a held stock asks for no symbol`() {
        val form = TradeForm(TradeSide.Sell, LocalDate.of(2026, 10, 20), holdingId = "h1")
        compose.setContent { MoneyMattersTheme { TradeContent(form, emptyList(), "That's more than you held on that date.", false, {}, {}, {}) } }
        compose.onNodeWithText("Sell stock").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Symbol").fetchSemanticsNodes().size)
        compose.onNodeWithText("That's more than you held on that date.").assertIsDisplayed()
    }

    @Test fun `the import screen lists what will be held and what must be fixed`() {
        val parse = TradeParse(emptyList(), listOf(RowError(4, "Type must be buy or sell")))
        val preview = TradePreview(
            listOf(SymbolPreview("TCS", "Tata Consultancy", 3, Units.whole(6), 24_000.rupees)),
            listOf(RowError(9, "Sells 11 INFY on 2026-03-01 but only 10 were held")),
        )
        compose.setContent { MoneyMattersTheme { ImportContent(parse, preview, canImport = false, importedCount = null, isSaving = false, error = null, onChoose = {}, onImport = {}, onClose = {}) } }
        compose.onNodeWithText("Tata Consultancy").assertIsDisplayed()
        compose.onNodeWithText("Line 4: Type must be buy or sell").assertIsDisplayed()
        compose.onNodeWithText("Line 9: Sells 11 INFY on 2026-03-01 but only 10 were held").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_import.png")
    }

    @Test fun `a finished import says how many trades went in`() {
        compose.setContent { MoneyMattersTheme { ImportContent(null, null, false, importedCount = 42, isSaving = false, error = null, onChoose = {}, onImport = {}, onClose = {}) } }
        compose.onNodeWithText("Imported 42 trades").assertIsDisplayed()
    }

    @Test fun `the price form renders`() {
        compose.setContent { MoneyMattersTheme { PriceContent("Infosys (INFY)", PriceForm("h1", LocalDate.of(2026, 10, 20), "1512.35"), null, {}, {}, {}) } }
        compose.onNodeWithText("Update price").assertIsDisplayed()
        compose.onNodeWithText("Save price").assertIsDisplayed()
    }

    @Test fun `the dividend and corporate action forms render`() {
        compose.setContent {
            MoneyMattersTheme {
                DividendContent("Infosys (INFY)", DividendForm("h1", LocalDate.of(2026, 10, 20), "1,800", "200", "bank"), emptyList(), null, {}, {}, {})
            }
        }
        compose.onNodeWithText("Received in your account (₹)").assertIsDisplayed()
        compose.onNodeWithText("Save dividend").assertIsDisplayed()
    }

    @Test fun `the corporate action form words its ratio for a split and for a bonus`() {
        var form by androidx.compose.runtime.mutableStateOf(ActionForm("h1", com.moneymatters.app.core.invest.RestateKind.Split, LocalDate.of(2026, 10, 20), "5"))
        compose.setContent { MoneyMattersTheme { ActionContent("Infosys (INFY)", form, null, { form = it }, {}, {}) } }
        compose.onNodeWithText("New units for each unit held").assertIsDisplayed()
        compose.onNodeWithText("Bonus").performClick()
        compose.onNodeWithText("Bonus units for each unit held").assertIsDisplayed()
    }

    @Test fun `holding detail lists dividends and actions`() {
        val withActions = detail.copy(
            actions = listOf(ActionRow("1 Jun 2026", "Dividend", 2_000.rupees), ActionRow("1 Jan 2026", "Split ×5", null)),
            dividendTotal = 2_000.rupees,
        )
        compose.setContent { MoneyMattersTheme { HoldingDetailScreen(withActions, onBack = {}, onBuy = {}, onSell = {}, onUpdatePrice = {}) } }
        compose.onNodeWithText("Dividends & actions").assertIsDisplayed()
        compose.onNodeWithText("Split ×5").assertIsDisplayed()
        compose.onNodeWithText("₹2,000 received so far").assertIsDisplayed()
    }
}
