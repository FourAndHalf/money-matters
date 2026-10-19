package com.moneymatters.app.feature.dashboard

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.ui.components.Tone

/**
 * Sample data matching design/mockups/dashboard.html. Used by previews, screenshot tests and the
 * app until the ledger is built; replace with a real source, don't grow this file.
 */
object SampleDashboard {

    private val essentialMonthly = 40_000.rupees
    private val emergencySaved = 3_00_000.rupees
    private val safeToSpend = 18_420.rupees
    private const val DAYS_LEFT = 13

    val state = DashboardState(
        greeting = "Good morning",
        dateLabel = "Mon, 19 Oct 2026",
        profileInitial = "F",
        hasUnreadAlerts = true,
        leeway = Leeway(
            safeToSpend = safeToSpend,
            paydayLabel = "1 Nov",
            daysLeft = DAYS_LEFT,
            usedFraction = 0.62f,
            reserved = 49_030.rupees,
            reservedCount = 4,
            dailySpend = listOf(
                820, 1240, 2890, 640, 1100, 3720, 450, 980, 1530, 760,
                2210, 1340, 590, 3100, 880, 1460, 2140, 4944, 486,
            ).map { it.rupees },
            daysInPeriod = 31,
            periodStartLabel = "1 Oct",
            periodEndLabel = "31 Oct",
        ),
        kpis = listOf(
            Kpi("Net worth", "₹6,14,870", "▲ 2.1%", Tone.Positive, listOf(5.71f, 5.74f, 5.80f, 5.78f, 5.86f, 5.92f, 5.95f, 6.01f, 5.98f, 6.06f, 6.11f, 6.15f)),
            Kpi("Runway · cash", "4.7 mo", "▼ 0.2", Tone.Negative, listOf(5.2f, 5.1f, 5.3f, 5.0f, 4.9f, 5.1f, 4.9f, 4.8f, 4.9f, 4.8f, 4.7f, 4.7f)),
            Kpi("Spent this month", "₹31,280", "▲ 8.4%", Tone.Negative, listOf(2f, 4f, 7f, 8f, 9f, 13f, 14f, 15f, 17f, 18f, 20f, 22f, 23f, 26f, 27f, 28f, 30f, 31f)),
            Kpi("Savings rate", "38%", "▲ 3 pts", Tone.Positive, listOf(29f, 31f, 30f, 33f, 32f, 35f, 34f, 36f, 35f, 38f)),
        ),
        accounts = listOf(
            AccountCard(
                CardFinish.Pearl, "Savings · salary", "HDFC Bank", "Available balance", 1_84_250.rupees,
                "•••• 4821", "✓ Reconciled 15 Oct", glyph = null,
                detail = AccountDetail.Bank(
                    "HDFC Bank · savings", "October so far", "Open ledger",
                    moneyIn = 72_000.rupees, moneyOut = 41_830.rupees,
                    recent = listOf(
                        TxnLine("S", "Swiggy", "Dining · UPI · today", (-486).rupees, "Want", Tone.Warning),
                        TxnLine("B", "BigBasket", "Groceries · UPI · Sat", (-2_140).rupees, "Draft", Tone.Info),
                        TxnLine("₹", "Salary · Oct", "Income · 1 Oct", 72_000.rupees, "Reconciled", Tone.Neutral),
                    ),
                ),
            ),
            AccountCard(
                CardFinish.Graphite, "Credit card", "ICICI Rewards", "Outstanding", 23_480.rupees,
                "•••• 1907", "Due 28 Oct", glyph = null,
                detail = AccountDetail.CreditCard(
                    "ICICI Rewards · credit", "Statement closed 10 Oct", "Schedule payment",
                    utilisation = 0.28f, limit = 1_00_000.rupees, statement = 23_480.rupees,
                    unbilled = 4_920.rupees, dueLabel = "28 Oct · 9 days", coveredBy = "HDFC",
                ),
            ),
            AccountCard(
                CardFinish.Silver, "Cash", "Wallet", "On hand", 3_200.rupees,
                "Counted 17 Oct", "Count due", glyph = "₹",
                detail = AccountDetail.Cash(
                    "Cash wallet", "Last counted 17 Oct", "Count & reconcile",
                    onHand = 3_200.rupees, spentThisMonth = 1_800.rupees, withdrawnThisMonth = 5_000.rupees,
                    status = "Count due before close",
                ),
            ),
            AccountCard(
                CardFinish.Champagne, "Fixed deposits · 3", "Emergency ladder", "Principal", 3_00_000.rupees,
                "Next: 12 Jan 2027", "+₹3,600 accrued", glyph = "◈",
                detail = AccountDetail.FdLadder(
                    "FD ladder · emergency", "Counts toward runway step 2", "Add FD",
                    deposits = listOf(
                        Fd(1_00_000.rupees, "7.10%", "Matures 12 Jan 2027", 1_420.rupees),
                        Fd(1_00_000.rupees, "7.25%", "Matures 3 Jun 2027", 1_210.rupees),
                        Fd(1_00_000.rupees, "7.40%", "Matures 18 Nov 2027", 970.rupees),
                    ),
                    breakTodayValue = 2_95_500.rupees,
                ),
            ),
            AccountCard(
                CardFinish.Smoke, "Investments · 7", "Stocks & MFs", "Market value", 60_900.rupees,
                "Prices ✓ 16 Oct", "▲ 8.4%", glyph = "↗",
                detail = AccountDetail.Investments(
                    "Stocks & mutual funds", "4 stocks · 3 funds", "Open portfolio",
                    cost = 56_200.rupees, unrealized = 4_700.rupees, unrealizedPct = "8.4%", xirr = "11.2%",
                    ltcgUsed = Paise.Zero, ltcgLimit = 1_25_000.rupees, pricesLabel = "✓ Verified · Fri 16 Oct",
                ),
            ),
        ),
        runway = Runway(
            essentialMonthly = essentialMonthly,
            cashMonths = 4.7f,
            withFdMonths = 12.1f,
            withMarketsMonths = 13.1f,
            scaleMonths = 18,
            trajectoryLabel = "▲ Net positive · never runs out",
            trajectoryTone = Tone.Positive,
        ),
        budgets = Budgets(
            periodLabel = "October",
            elapsedFraction = 18 / 31f,
            lines = listOf(
                BudgetLine("Groceries", 6_240.rupees, 9_000.rupees),
                BudgetLine("Dining", 4_880.rupees, 5_000.rupees),
                BudgetLine("Shopping", 7_100.rupees, 8_000.rupees),
                BudgetLine("Transport", 2_150.rupees, 4_000.rupees),
            ),
        ),
        spending = Spending(
            total = 31_280.rupees,
            categories = listOf(
                CategoryShare("Shopping", 7_100.rupees),
                CategoryShare("Groceries", 6_240.rupees),
                CategoryShare("Others", 5_500.rupees),
                CategoryShare("Dining", 4_880.rupees),
                CategoryShare("Bills", 3_410.rupees),
                CategoryShare("Transport", 2_150.rupees),
                CategoryShare("Health", 2_000.rupees),
            ),
            needWantLabel = "61% / 39%",
            unplannedCount = 12,
        ),
        upcoming = listOf(
            UpcomingItem("25", "Oct", "SIP · Flexi Cap Fund", "From HDFC · auto", 5_000.rupees, "Reserved", Tone.Info),
            UpcomingItem("28", "Oct", "ICICI card bill", "Statement 10 Oct", 23_480.rupees, "✓ Covered", Tone.Positive),
            UpcomingItem("29", "Oct", "Electricity", "Recurring · monthly", 1_800.rupees, "Reserved", Tone.Info),
            UpcomingItem("30", "Oct", "Streaming subscription", "Price up ₹150 since Aug", 649.rupees, "▲ Price rise", Tone.Warning),
            UpcomingItem("01", "Nov", "Rent", "After payday · not reserved", 18_000.rupees, "Next cycle", Tone.Neutral),
        ),
        goals = Goals(
            emergency = EmergencyGoal(
                saved = emergencySaved,
                target = 5_00_000.rupees,
                blockSize = 25_000.rupees,
                coverageLabel = "Covers ${monthsOf(emergencySaved, essentialMonthly)} mo of essentials",
            ),
            corpus = CorpusGoal(
                title = "₹1 Crore by 30",
                timeLeftLabel = "3y 1m",
                deadlineLabel = "20 Nov 2029",
                invested = 60_900.rupees,
                target = 1_00_00_000.rupees,
                monthlySip = 5_000.rupees,
                monthsLeft = 37,
                annualReturn = 0.10,
                startLabel = "Oct 2026",
            ),
        ),
        portfolio = Portfolio(
            value = 60_900.rupees,
            unrealized = 4_700.rupees,
            unrealizedPct = "8.4%",
            weekChange = 1_240.rupees,
            xirr = "11.2%",
            equityFraction = 1f,
            targetEquityFraction = 0.5f,
            sources = listOf(
                PriceSource("NSE", "Fri 16 Oct", "✓ Verified · 4/4", Tone.Positive),
                PriceSource("AMFI", "Fri 16 Oct", "✓ Verified · 3/3", Tone.Positive),
            ),
        ),
        insights = Insights(
            digestDate = "Sun 18 Oct",
            digest = "A steady week: you spent ₹1,180/day, under your " +
                "${dailyAllowance(safeToSpend, DAYS_LEFT).formatInr()} allowance. Dining is the exception. " +
                "It's up 34% on your 3-month average, and most of it was unplanned.",
            items = listOf(
                InsightItem(InsightGlyph.Alert, "Dining is 98% used with 13 days left", "At this pace you'll overshoot by ~₹3,900 around 22 Oct.", "See dining"),
                InsightItem(InsightGlyph.Recurring, "3 subscriptions cost ₹14,988 a year", "One raised its price by ₹150 in August.", "Review"),
            ),
        ),
        attention = listOf(
            AttentionItem(AttentionKind.CloseMonth, "September is ready to close", "7 of 9 checklist items done"),
            AttentionItem(AttentionKind.Drafts, "Drafts", "Unfinished entries", "2", Tone.Warning),
            AttentionItem(AttentionKind.Uncategorized, "Uncategorized", "Sitting in suspense", "3", Tone.Warning),
            AttentionItem(AttentionKind.InTransit, "SIP in transit", "Awaiting NAV allotment", "1", Tone.Info),
            AttentionItem(AttentionKind.WeeklyReview, "Weekly review", "Worth it or regret?", "6 spends", Tone.Info),
            AttentionItem(AttentionKind.AdvanceTax, "Advance tax · 15 Dec", "Tax year 2026-27 · est. on freelance income", "57 days", Tone.Warning, 8_400.rupees),
        ),
    )

    /** Whole and tenth months, e.g. "7.5". */
    private fun monthsOf(amount: Paise, monthly: Paise): String {
        val tenths = amount.value * 10 / monthly.value
        return "${tenths / 10}.${tenths % 10}"
    }
}
