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

    @Test fun `the portfolio groups stocks and funds and lists orders In Transit with their actions`() {
        val fund = row("h9", "PPFAS Flexi Cap", "", "123.456", 10_000, 11_000).copy(instrument = com.moneymatters.app.core.invest.Instrument.MutualFund, classLabel = "Equity")
        val withFund = state.copy(holdings = state.holdings + fund, pending = listOf(PendingRow("p1", "PPFAS Flexi Cap", "19 Oct 2026", 5_000.rupees)))
        var allotted: String? = null
        var cancelled: String? = null
        compose.setContent {
            MoneyMattersTheme { InvestScreen(withFund, onOpenHolding = {}, onBuy = {}, onImport = {}, onAllot = { allotted = it }, onCancelOrder = { cancelled = it }) }
        }
        compose.onNodeWithText("Stocks").assertIsDisplayed()
        compose.onNodeWithText("Mutual funds").assertIsDisplayed()
        compose.onNodeWithText("In transit").assertIsDisplayed()
        compose.onNodeWithText("Allot").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("p1", allotted)
        assertEquals("p1", cancelled)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_funds_portfolio.png")
    }

    @Test fun `the fund buy form asks for a name and a type, and says when it goes In Transit`() {
        val form = TradeForm(TradeSide.Buy, LocalDate.of(2026, 10, 20), instrument = com.moneymatters.app.core.invest.Instrument.MutualFund, name = "PPFAS Flexi Cap", amountText = "10,000", accountId = "bank")
        compose.setContent { MoneyMattersTheme { TradeContent(form, emptyList(), null, false, {}, {}, {}) } }
        compose.onNodeWithText("Buy fund").assertIsDisplayed()
        compose.onNodeWithText("Fund name").assertIsDisplayed()
        compose.onNodeWithText("ELSS").assertIsDisplayed()
        compose.onNodeWithText("The money moves to In Transit until the units are allotted.").assertIsDisplayed()
        compose.onNodeWithText("Send to In Transit").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_fund_buy_form.png")
    }

    @Test fun `the allot form renders`() {
        compose.setContent {
            MoneyMattersTheme { AllotContent(PendingRow("p1", "PPFAS Flexi Cap", "19 Oct 2026", 5_000.rupees), AllotForm("p1", LocalDate.of(2026, 10, 20), "61.5"), null, {}, {}, {}) }
        }
        compose.onNodeWithText("Units allotted").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("Allot units").fetchSemanticsNodes().size) // the title and the button
    }

    @Test fun `a lot shows its lock note`() {
        val locked = detail.copy(lots = listOf(LotRow("10 Jan 2025", "10", 40_000.rupees, "Long-term", true, note = "Locked until 10 Jan 2028")))
        compose.setContent { MoneyMattersTheme { HoldingDetailScreen(locked, onBack = {}, onBuy = {}, onSell = {}, onUpdatePrice = {}) } }
        compose.onNodeWithText("10 Jan 2025 · Locked until 10 Jan 2028").assertIsDisplayed()
    }

    private val depositRow = DepositRow("d1", "HDFC FD 1", "7.00% · quarterly", 1_00_000.rupees, 1_03_500.rupees, PriceTag("Matures in 21 days", Tone.Warning))
    private val depositDetail = DepositDetail(
        row = depositRow, rateLabel = "7.00%", compoundingLabel = "Quarterly", startLabel = "1 Jan 2026", maturityLabel = "1 Jan 2027",
        accruedInterest = 3_500.rupees, postedInterest = 2_000.rupees, unpostedInterest = 1_500.rupees,
        maturityValue = 1_07_186.rupees, breakProceeds = 1_02_900.rupees, breakPenalty = 600.rupees,
        autoRenew = true, isClosed = false, matured = false, closedLabel = null, payout = null,
    )

    @Test fun `the portfolio lists fixed deposits and opens one`() {
        val withDeposit = state.copy(deposits = listOf(depositRow))
        var opened: String? = null
        var added = false
        compose.setContent { MoneyMattersTheme { InvestScreen(withDeposit, onOpenHolding = {}, onBuy = {}, onImport = {}, onNewDeposit = { added = true }, onOpenDeposit = { opened = it }) } }
        compose.onNodeWithText("Fixed deposits").assertIsDisplayed()
        compose.onNodeWithText("Matures in 21 days").assertIsDisplayed()
        compose.onNodeWithText("HDFC FD 1").performClick()
        compose.onNodeWithText("+ FD").performClick()
        assertEquals("d1", opened)
        assertTrue(added)
    }

    @Test fun `a portfolio with only a deposit still shows its summary`() {
        val onlyDeposit = InvestState(emptyList(), 1_00_000.rupees, 1_03_500.rupees, 3_500.rupees, 0.035, 0.07, 0, emptyMap(), deposits = listOf(depositRow))
        compose.setContent { MoneyMattersTheme { InvestScreen(onlyDeposit, onOpenHolding = {}, onBuy = {}, onImport = {}) } }
        compose.onNodeWithText("Portfolio value").assertIsDisplayed()
    }

    @Test fun `the deposit form validates dates and switches funding for a running deposit`() {
        val form = DepositForm(name = "HDFC FD", start = LocalDate.of(2026, 1, 1), maturity = LocalDate.of(2026, 1, 1), principalText = "1,00,000", rateText = "7.25")
        compose.setContent { MoneyMattersTheme { DepositContent(form, emptyList(), null, false, {}, {}, {}) } }
        compose.onNodeWithText("New fixed deposit").assertIsDisplayed()
        compose.onNodeWithText("Must be after the start date").assertIsDisplayed()
        compose.onNodeWithText("Paid from").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_deposit_form.png")
    }

    @Test fun `an existing deposit's form explains that no money moves`() {
        val form = DepositForm(start = LocalDate.of(2025, 1, 1), maturity = LocalDate.of(2027, 1, 1), existing = true)
        compose.setContent { MoneyMattersTheme { DepositContent(form, emptyList(), null, false, {}, {}, {}) } }
        compose.onNodeWithText("Recorded against your opening balances, so no bank balance changes. Interest earned so far is counted from the start date.").assertIsDisplayed()
    }

    @Test fun `deposit detail shows value, terms, break value and its actions`() {
        var posted = false
        var closed = false
        compose.setContent { MoneyMattersTheme { DepositDetailScreen(depositDetail, onBack = {}, onPostInterest = { posted = true }, onClose = { closed = true }) } }
        compose.onNodeWithText("Value today").assertIsDisplayed()
        compose.onNodeWithText("Breaking early costs ₹600.00").assertIsDisplayed()
        compose.onNodeWithText("Post interest").performClick()
        compose.onNodeWithText("Break early").performClick()
        assertTrue(posted && closed)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_deposit_detail.png")
    }

    @Test fun `a matured deposit offers Mature and a closed one offers no actions`() {
        compose.setContent { MoneyMattersTheme { DepositDetailScreen(depositDetail.copy(matured = true, unpostedInterest = 0.rupees), onBack = {}, onPostInterest = {}, onClose = {}) } }
        compose.onNodeWithText("Mature").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Post interest").fetchSemanticsNodes().size)
    }

    @Test fun `a closed deposit shows what it paid out`() {
        val closed = depositDetail.copy(isClosed = true, closedLabel = "1 Jan 2027", payout = 1_07_186.rupees, row = depositRow.copy(value = 1_07_186.rupees, badge = PriceTag("Closed", Tone.Neutral)))
        compose.setContent { MoneyMattersTheme { DepositDetailScreen(closed, onBack = {}, onPostInterest = {}, onClose = {}) } }
        compose.onNodeWithText("Paid out").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Break early").fetchSemanticsNodes().size)
    }

    @Test fun `the close form words itself for maturing or breaking`() {
        val form = DepositCloseForm("d1", LocalDate.of(2026, 7, 1))
        compose.setContent { MoneyMattersTheme { DepositCloseContent(depositDetail, form, emptyList(), null, false, {}, {}, {}) } }
        assertEquals(2, compose.onAllNodesWithText("Break deposit").fetchSemanticsNodes().size) // the title and the button
        compose.onNodeWithText("Renew into a new deposit").assertIsDisplayed()
        compose.onNodeWithText("Paid into").assertIsDisplayed()
    }

    private val sipRow = SipRow("s1", "PPFAS Flexi Cap", 10_000.rupees, "on the 5th of every month", "5 Nov 2026", dueCount = 2, missedCount = 1, ended = false)
    private val sipDetail = SipDetail(
        row = sipRow, fromAccountName = "HDFC Bank",
        dues = listOf(DueRow(LocalDate.of(2026, 10, 5), "5 Oct 2026", com.moneymatters.app.core.invest.InstalmentState.Missed), DueRow(LocalDate.of(2026, 11, 5), "5 Nov 2026", com.moneymatters.app.core.invest.InstalmentState.Due)),
        paidCount = 4, missedImpact = 52_000.rupees, goalLabel = "20 Nov 2029",
    )

    @Test fun `the portfolio lists SIPs with their status and offers a new one`() {
        var opened: String? = null
        var added = false
        val withSip = state.copy(sips = listOf(sipRow, sipRow.copy(planId = "s2", fundName = "Index Fund", missedCount = 0, dueCount = 0)), hasFunds = true)
        compose.setContent { MoneyMattersTheme { InvestScreen(withSip, onOpenHolding = {}, onBuy = {}, onImport = {}, onNewSip = { added = true }, onOpenSip = { opened = it }) } }
        compose.onNodeWithText("SIPs").assertIsDisplayed()
        compose.onNodeWithText("1 missed").assertIsDisplayed()
        compose.onNodeWithText("On track").assertIsDisplayed()
        compose.onNodeWithText("PPFAS Flexi Cap").performClick()
        compose.onNodeWithText("+ New").performClick()
        assertEquals("s1", opened)
        assertTrue(added)
    }

    @Test fun `a portfolio with a fund but no SIP invites starting one`() {
        compose.setContent { MoneyMattersTheme { InvestScreen(state.copy(hasFunds = true), onOpenHolding = {}, onBuy = {}, onImport = {}) } }
        compose.onNodeWithText("Monthly investments into a fund").assertIsDisplayed()
    }

    @Test fun `SIP detail shows what's waiting, the goal impact, and Paid, Skip and Stop work`() {
        var paid: LocalDate? = null
        var skipped: LocalDate? = null
        var stopped = false
        compose.setContent { MoneyMattersTheme { SipDetailScreen(sipDetail, onBack = {}, onPay = { paid = it.due }, onSkip = { skipped = it.due }, onStop = { stopped = true }) } }
        compose.onNodeWithText("Missed instalments cost your goal").assertIsDisplayed()
        compose.onNodeWithText("They would have grown to about ₹52,000 by 20 Nov 2029.").assertIsDisplayed()
        compose.onNodeWithText("Missed").assertIsDisplayed()
        compose.onAllNodesWithText("Paid")[0].performClick()
        compose.onAllNodesWithText("Skip")[0].performClick()
        compose.onNodeWithText("Stop this SIP").performClick()
        assertEquals(false, stopped) // one tap only asks for confirmation
        compose.onNodeWithText("Tap again to stop this SIP").performClick()
        assertEquals(LocalDate.of(2026, 10, 5), paid)
        assertEquals(LocalDate.of(2026, 10, 5), skipped)
        assertTrue(stopped)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_sip_detail.png")
    }

    @Test fun `an ended SIP offers no stop button, and a skipped instalment offers no skip`() {
        val ended = sipDetail.copy(
            row = sipRow.copy(ended = true),
            dues = listOf(DueRow(LocalDate.of(2026, 7, 5), "5 Jul 2026", com.moneymatters.app.core.invest.InstalmentState.Skipped)),
        )
        compose.setContent { MoneyMattersTheme { SipDetailScreen(ended, onBack = {}, onPay = {}, onSkip = {}, onStop = {}) } }
        assertEquals(0, compose.onAllNodesWithText("Stop this SIP").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Skip").fetchSemanticsNodes().size)
        compose.onNodeWithText("Skipped").assertIsDisplayed()
    }

    @Test fun `the SIP form lists funds to choose from, or says to buy one first`() {
        val form = SipForm(LocalDate.of(2026, 10, 20))
        compose.setContent { MoneyMattersTheme { SipFormContent(form, listOf(infy.copy(name = "PPFAS Flexi Cap")), emptyList(), null, {}, {}, {}) } }
        compose.onNodeWithText("PPFAS Flexi Cap").assertIsDisplayed()
        compose.onNodeWithText("Start SIP").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_sip_form.png")
    }

    @Test fun `the SIP form with no funds says to buy one first`() {
        compose.setContent { MoneyMattersTheme { SipFormContent(SipForm(LocalDate.of(2026, 10, 20)), emptyList(), emptyList(), null, {}, {}, {}) } }
        compose.onNodeWithText("Buy a mutual fund first, then set up its SIP.").assertIsDisplayed()
    }

    private val pricesState = PricesState(
        sources = listOf(
            SourceStatus(com.moneymatters.app.core.invest.PriceSourceId.Nse, "NSE", Tone.Negative, "✕ Failing since 19 Oct", "Last success 15 Oct · File not found (404)"),
            SourceStatus(com.moneymatters.app.core.invest.PriceSourceId.Amfi, "AMFI", Tone.Positive, "✓ Updated 22 Oct", "NAVs for 2026-10-22: matched 2 of 2 funds"),
        ),
        rows = listOf(
            PriceRow("h1", "Infosys (INFY)", "₹1,168.00", "22 Oct 2026 · manual", PriceTag("⚠ Mismatch", Tone.Warning), 5, "2026-10-22", com.moneymatters.app.core.invest.PriceStatus.Mismatch, true),
            PriceRow("h2", "Tata Consultancy (TCS)", "₹2,529.00", "22 Oct 2026 · nse", PriceTag("✕ Suspicious", Tone.Negative), 7, "2026-10-22", com.moneymatters.app.core.invest.PriceStatus.Suspicious, true),
            PriceRow("h3", "PPFAS Flexi Cap", "₹81.2345", "22 Oct 2026 · amfi", PriceTag("✓ Verified", Tone.Positive), 9, "2026-10-22", com.moneymatters.app.core.invest.PriceStatus.Verified, false),
        ),
        log = listOf(LogRow("22 Oct, 09:00", "AMFI", true, "NAVs for 2026-10-22: matched 2 of 2 funds"), LogRow("19 Oct, 09:00", "NSE", false, "File not found (404)")),
    )

    @Test fun `the prices screen shows source health, statuses and the log, and its actions work`() {
        var refreshed = false
        var confirmed: Long? = null
        var resolved: Triple<String, String, Boolean>? = null
        var manual: String? = null
        compose.setContent {
            MoneyMattersTheme {
                PricesScreen(
                    pricesState, com.moneymatters.app.core.invest.PriceSources.Default, isRefreshing = false,
                    onBack = {}, onRefresh = { refreshed = true }, onUpdateManual = { manual = it },
                    onConfirm = { confirmed = it }, onResolve = { h, d, keep -> resolved = Triple(h, d, keep) }, onSaveSources = {},
                )
            }
        }
        compose.onNodeWithText("2 to review").assertIsDisplayed()
        compose.onNodeWithText("✕ Failing since 19 Oct").assertIsDisplayed()
        compose.onNodeWithText("Last success 15 Oct · File not found (404)").assertIsDisplayed()
        compose.onNodeWithText("✓ Updated 22 Oct").assertIsDisplayed()
        compose.onNodeWithText("Download log").assertIsDisplayed()
        compose.onNodeWithText("Refresh").performClick()
        compose.onNodeWithText("Use download").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Tata Consultancy (TCS)").performClick()
        assertTrue(refreshed)
        assertEquals(Triple("h1", "2026-10-22", true), resolved)
        assertEquals(7L, confirmed)
        assertEquals("h2", manual)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_prices.png")
    }

    @Test fun `keeping your own price resolves the other way`() {
        var resolved: Triple<String, String, Boolean>? = null
        compose.setContent {
            MoneyMattersTheme {
                PricesScreen(pricesState, com.moneymatters.app.core.invest.PriceSources.Default, false, {}, {}, {}, {}, { h, d, keep -> resolved = Triple(h, d, keep) }, {})
            }
        }
        compose.onNodeWithText("Keep mine").performClick()
        assertEquals(Triple("h1", "2026-10-22", false), resolved)
    }

    @Test fun `the prices screen says when everything is verified, and shows refreshing while it works`() {
        val clean = pricesState.copy(rows = pricesState.rows.filter { !it.needsAttention })
        compose.setContent { MoneyMattersTheme { PricesScreen(clean, com.moneymatters.app.core.invest.PriceSources.Default, true, {}, {}, {}, {}, { _, _, _ -> }, {}) } }
        compose.onNodeWithText("All prices verified").assertIsDisplayed()
        compose.onNodeWithText("Refreshing…").assertIsDisplayed()
    }

    @Test fun `source addresses can be edited, saved, and reset to the defaults`() {
        var saved: com.moneymatters.app.core.invest.PriceSources? = null
        val custom = com.moneymatters.app.core.invest.PriceSources("https://x/{yyyyMMdd}.zip", "https://y/nav.txt")
        compose.setContent { MoneyMattersTheme { PricesScreen(pricesState, custom, false, {}, {}, {}, {}, { _, _, _ -> }, { saved = it }) } }
        compose.onNodeWithText("Change addresses").performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(com.moneymatters.app.core.invest.PriceSources.Default, saved)
    }

    @Test fun `the portfolio links to prices and counts what needs review`() {
        var opened = false
        val withPrices = state.copy(prices = pricesState)
        compose.setContent { MoneyMattersTheme { InvestScreen(withPrices, onOpenHolding = {}, onBuy = {}, onImport = {}, onOpenPrices = { opened = true }) } }
        compose.onNodeWithText("2 to review").assertIsDisplayed()
        compose.onNodeWithText("Prices").performClick()
        assertTrue(opened)
    }

    @Test fun `allocation shows drift and concentration, and the gains register totals each year`() {
        val analytics = AnalyticsState(
            allocation = listOf(AllocationRow("Equity", 1_20_000.rupees, 0.7), AllocationRow("Debt & FDs", 51_000.rupees, 0.3)),
            targetEquityPercent = 60,
            driftMessage = "Equity is 10.0 points above your 60% target",
            concentration = listOf(ConcentrationRow("h1", "Infosys (INFY)", "62.0% of equity")),
        )
        var nudged = 0
        compose.setContent {
            MoneyMattersTheme {
                InvestScreen(state.copy(analytics = analytics), onOpenHolding = {}, onBuy = {}, onImport = {}, onNudgeTarget = { nudged += it })
            }
        }
        compose.onNodeWithText("Equity target 60%").assertIsDisplayed()
        compose.onNodeWithText("Equity is 10.0 points above your 60% target").assertIsDisplayed()
        compose.onNodeWithText("62.0% of equity").assertIsDisplayed()
        compose.onNodeWithText("+5").performClick()
        assertEquals(5, nudged)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_allocation_light.png")
    }

    @Test fun `the capital gains screen lists sales under their financial year`() {
        val sale = SaleRow("t1", "1 Mar 2026", "Infosys (INFY)", "4", 600.rupees, 400.rupees, 200.rupees, "Long-term")
        compose.setContent { MoneyMattersTheme { GainsScreen(listOf(YearGains("FY 2025-26", 0.rupees, 200.rupees, 0.rupees, listOf(sale))), onBack = {}) } }
        compose.onNodeWithText("FY 2025-26").assertIsDisplayed()
        compose.onNodeWithText("1 Mar 2026 · 4 units · Long-term").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/invest_gains_light.png")
    }
}
