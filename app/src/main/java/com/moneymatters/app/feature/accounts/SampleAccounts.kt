package com.moneymatters.app.feature.accounts

import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.feature.dashboard.SampleDashboard
import com.moneymatters.app.ui.components.Tone

/**
 * Sample data for the Accounts page. The five accounts also shown in the dashboard's card stack
 * (same ids, same balances) are read from [SampleDashboard] so the two pages never disagree;
 * the car loan only appears here, since it doesn't fit the card stack's curated set.
 */
object SampleAccounts {

    private val dashboardAccounts = SampleDashboard.state.accounts.associateBy { it.id }
    private val hdfcBank = dashboardAccounts.getValue("hdfc-bank")
    private val iciciCredit = dashboardAccounts.getValue("icici-credit")
    private val cash = dashboardAccounts.getValue("cash")
    private val fdLadder = dashboardAccounts.getValue("fd-ladder")
    private val investments = dashboardAccounts.getValue("investments")

    private val carLoanOutstanding = 3_42_000.rupees
    private val carLoanEmi = 14_200.rupees

    private val bankCash = AccountGroup(
        "Bank & cash",
        listOf(
            AccountSummary("hdfc-bank", AccountKind.Bank, hdfcBank.name, "Savings · •••• 4821", hdfcBank.amount),
            AccountSummary("cash", AccountKind.Cash, cash.name, "Counted 17 Oct", cash.amount),
        ),
    )

    private val creditCards = AccountGroup(
        "Credit cards",
        listOf(
            AccountSummary(
                "icici-credit", AccountKind.CreditCard, iciciCredit.name, "Due 28 Oct · 9 days", iciciCredit.amount,
                isLiability = true, badge = AccountBadge("Interest risk: none", Tone.Positive),
            ),
        ),
    )

    private val fixedDeposits = AccountGroup(
        "Fixed deposits",
        listOf(AccountSummary("fd-ladder", AccountKind.FixedDeposit, fdLadder.name, "3 deposits · emergency ladder", fdLadder.amount)),
    )

    private val investmentsGroup = AccountGroup(
        "Investments",
        listOf(
            AccountSummary(
                "investments", AccountKind.Investment, investments.name, "4 stocks · 3 funds", investments.amount,
                badge = AccountBadge("▲ 8.4%", Tone.Positive),
            ),
        ),
    )

    private val loans = AccountGroup(
        "Loans",
        listOf(
            AccountSummary(
                "car-loan", AccountKind.Loan, "Car loan", "EMI ${carLoanEmi.formatInr()} · next 5 Nov",
                carLoanOutstanding, isLiability = true, badge = AccountBadge("On track", Tone.Positive),
            ),
        ),
    )

    private val groups = listOf(bankCash, creditCards, fixedDeposits, investmentsGroup, loans)

    private val assetsTotal = groups.filter { g -> g.items.none { it.isLiability } }
        .flatMap { it.items }.fold(0L.rupees) { acc, a -> acc + a.balance }
    private val liabilitiesTotal = groups.flatMap { it.items }.filter { it.isLiability }
        .fold(0L.rupees) { acc, a -> acc + a.balance }

    val state = AccountsState(
        netWorth = assetsTotal - liabilitiesTotal,
        assetsTotal = assetsTotal,
        liabilitiesTotal = liabilitiesTotal,
        asOfLabel = "As of today",
        groups = groups,
        earmarks = listOf(
            Earmark("Emergency fund", SampleDashboard.state.goals.emergency.saved, SampleDashboard.state.goals.emergency.target),
            Earmark("Diwali gifts", 8_000.rupees, 15_000.rupees),
        ),
    )
}
