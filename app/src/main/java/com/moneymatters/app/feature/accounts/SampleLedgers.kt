package com.moneymatters.app.feature.accounts

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees

/**
 * Sample ledgers keyed by account id, matching the balances in [SampleAccounts]. Covers both
 * section styles P09 asks for: a flat list (bank, cash, FD, investments, loan) and a credit
 * card's statement-cycle grouping, and all three entry statuses (Draft, Reconciled, Prior-period).
 */
object SampleLedgers {

    private val availablePeriods = listOf("October 2026", "September 2026", "All time")
    private const val PERIOD = "October 2026"

    private fun entry(id: String, date: String, chip: String, payee: String, meta: String, amount: Paise, status: EntryStatus? = null) =
        LedgerEntry(id, date, chip, payee, meta, amount, runningBalance = Paise.Zero, status = status)

    /** One section, no statement cycle: balances continuously from [opening], newest entry first. */
    private fun flatSection(opening: Paise, oldestFirst: List<LedgerEntry>): LedgerSection {
        val balances = runningBalances(opening, oldestFirst.map { it.amount })
        val withBalance = oldestFirst.zip(balances) { e, b -> e.copy(runningBalance = b) }
        return LedgerSection(entries = withBalance.reversed())
    }

    private fun unreconciledCount(sections: List<LedgerSection>) =
        sections.sumOf { s -> s.entries.count { it.status != EntryStatus.Reconciled } }

    // ---------- HDFC Bank (flat; every status appears) ----------

    private val bank = run {
        val oldestFirst = listOf(
            entry("b1", "1 Oct", "₹", "Salary · Oct", "Income · 1 Oct", 72_000.rupees, EntryStatus.Reconciled),
            entry("b2", "3 Oct", "R", "Rent", "Housing · auto-debit", (-18_000).rupees, EntryStatus.Reconciled),
            entry("b3", "5 Oct", "I", "ICICI card payment", "Credit card · late post", (-10_324).rupees, EntryStatus.PriorPeriod),
            entry("b4", "9 Oct", "A", "Amazon", "Shopping · card", (-5_000).rupees),
            entry("b5", "12 Oct", "B", "BigBasket", "Groceries · UPI", (-2_140).rupees, EntryStatus.Draft),
            entry("b6", "15 Oct", "E", "Electricity", "Bills · auto-debit", (-1_800).rupees),
            entry("b7", "19 Oct", "S", "Swiggy", "Dining · UPI · today", (-486).rupees),
        )
        val sections = listOf(flatSection(1_50_000.rupees, oldestFirst))
        AccountLedgerState(
            "hdfc-bank", "HDFC Bank", "Savings · •••• 4821", 1_84_250.rupees, isLiability = false,
            periodLabel = PERIOD, availablePeriods = availablePeriods, unreconciledCount = unreconciledCount(sections), sections = sections,
        )
    }

    // ---------- ICICI Rewards (credit card; two statement cycles) ----------

    private val creditCard = run {
        val previousCycle = listOf(
            entry("c1", "15 Aug", "A", "Amazon", "Shopping · card", 9_500.rupees, EntryStatus.Reconciled),
            entry("c2", "22 Aug", "F", "Fuel", "Transport · card", 4_700.rupees, EntryStatus.Reconciled),
            entry("c3", "28 Aug", "S", "Swiggy", "Dining · card", 4_000.rupees, EntryStatus.Reconciled),
            entry("c4", "5 Sep", "₹", "Payment received", "From HDFC Bank", (-18_200).rupees, EntryStatus.Reconciled),
        )
        val currentCycle = listOf(
            entry("c5", "14 Sep", "A", "Amazon", "Shopping · card", 9_480.rupees, EntryStatus.Reconciled),
            entry("c6", "21 Sep", "F", "Fuel", "Transport · card", 5_200.rupees, EntryStatus.Reconciled),
            entry("c7", "2 Oct", "S", "Swiggy", "Dining · card", 3_200.rupees),
            entry("c8", "8 Oct", "B", "BigBasket", "Groceries · card", 5_600.rupees),
        )
        var opening = Paise.Zero
        val sections = listOf(
            previousCycle to "Statement: 10 Aug – 9 Sep",
            currentCycle to "Statement: 10 Sep – 10 Oct",
        ).map { (cycle, title) ->
            val balances = runningBalances(opening, cycle.map { it.amount })
            val withBalance = cycle.zip(balances) { e, b -> e.copy(runningBalance = b) }
            // Charges only: a payment is negative and isn't a charge against the cycle.
            val subtotal = cycle.filter { it.amount.value > 0 }.fold(Paise.Zero) { acc, e -> acc + e.amount }
            opening = balances.last()
            LedgerSection(title = title, subtotal = subtotal, entries = withBalance.reversed())
        }.reversed()
        AccountLedgerState(
            "icici-credit", "ICICI Rewards", "Credit card · •••• 1907", 23_480.rupees, isLiability = true,
            periodLabel = PERIOD, availablePeriods = availablePeriods, unreconciledCount = unreconciledCount(sections), sections = sections,
        )
    }

    // ---------- Cash wallet (flat) ----------

    private val cash = run {
        val oldestFirst = listOf(
            entry("w1", "2 Oct", "A", "ATM withdrawal", "Cash in", 5_000.rupees, EntryStatus.Reconciled),
            entry("w2", "5 Oct", "A", "Auto rickshaw", "Transport", (-200).rupees),
            entry("w3", "9 Oct", "L", "Lunch", "Dining", (-350).rupees),
            entry("w4", "14 Oct", "V", "Vegetables", "Groceries", (-800).rupees),
            entry("w5", "17 Oct", "M", "Misc", "Uncategorized", (-450).rupees),
        )
        val sections = listOf(flatSection(Paise.Zero, oldestFirst))
        AccountLedgerState(
            "cash", "Cash Wallet", "Cash · counted 17 Oct", 3_200.rupees, isLiability = false,
            periodLabel = PERIOD, availablePeriods = availablePeriods, unreconciledCount = unreconciledCount(sections), sections = sections,
        )
    }

    // ---------- FD ladder (flat; three deposits) ----------

    private val fdLadder = run {
        val oldestFirst = listOf(
            entry("f1", "12 Jan", "1", "FD opened · 7.10%", "Matures 12 Jan 2027", 1_00_000.rupees, EntryStatus.Reconciled),
            entry("f2", "3 Jun", "2", "FD opened · 7.25%", "Matures 3 Jun 2027", 1_00_000.rupees, EntryStatus.Reconciled),
            entry("f3", "18 Nov", "3", "FD opened · 7.40%", "Matures 18 Nov 2027", 1_00_000.rupees, EntryStatus.Reconciled),
        )
        val sections = listOf(flatSection(Paise.Zero, oldestFirst))
        AccountLedgerState(
            "fd-ladder", "Emergency Ladder", "Fixed deposits · 3", 3_00_000.rupees, isLiability = false,
            periodLabel = PERIOD, availablePeriods = availablePeriods, unreconciledCount = unreconciledCount(sections), sections = sections,
        )
    }

    // ---------- Investments (flat; buys plus a revaluation) ----------

    private val investments = run {
        val oldestFirst = listOf(
            entry("i1", "2 Mar", "N", "Buy · Nifty 50 Index Fund", "Investment", 20_000.rupees, EntryStatus.Reconciled),
            entry("i2", "14 May", "H", "Buy · HDFC Bank shares", "Investment", 15_200.rupees, EntryStatus.Reconciled),
            entry("i3", "9 Jul", "F", "Buy · Flexi Cap Fund", "Investment · SIP", 12_000.rupees, EntryStatus.Reconciled),
            entry("i4", "20 Aug", "T", "Buy · TCS shares", "Investment", 9_000.rupees, EntryStatus.Reconciled),
            entry("i5", "16 Oct", "↗", "Revaluation", "Mark-to-market · NSE/AMFI Fri 16 Oct", 4_700.rupees),
        )
        val sections = listOf(flatSection(Paise.Zero, oldestFirst))
        AccountLedgerState(
            "investments", "Stocks & MFs", "Investments · 7 holdings", 60_900.rupees, isLiability = false,
            periodLabel = PERIOD, availablePeriods = availablePeriods, unreconciledCount = unreconciledCount(sections), sections = sections,
        )
    }

    // ---------- Car loan (flat; EMI principal portions reduce what's owed) ----------

    private val carLoan = run {
        val oldestFirst = listOf(
            entry("l1", "5 Jul", "E", "EMI · principal portion", "Auto-debit", (-19_000).rupees, EntryStatus.Reconciled),
            entry("l2", "5 Aug", "E", "EMI · principal portion", "Auto-debit", (-19_500).rupees, EntryStatus.Reconciled),
            entry("l3", "5 Sep", "E", "EMI · principal portion", "Auto-debit", (-19_500).rupees, EntryStatus.Reconciled),
            entry("l4", "5 Oct", "E", "EMI · principal portion", "Auto-debit", (-20_000).rupees),
        )
        val sections = listOf(flatSection(4_20_000.rupees, oldestFirst))
        AccountLedgerState(
            "car-loan", "Car Loan", "Loan · HDFC Bank", 3_42_000.rupees, isLiability = true,
            periodLabel = PERIOD, availablePeriods = availablePeriods, unreconciledCount = unreconciledCount(sections), sections = sections,
        )
    }

    val byAccountId: Map<String, AccountLedgerState> = listOf(bank, creditCard, cash, fdLadder, investments, carLoan)
        .associateBy { it.accountId }
}
