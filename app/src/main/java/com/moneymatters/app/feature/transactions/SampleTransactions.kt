package com.moneymatters.app.feature.transactions

import com.moneymatters.app.core.money.rupees

/**
 * A small, curated transaction history — not derived from [com.moneymatters.app.feature.accounts.SampleLedgers],
 * since a cross-account transaction list and a single account's ledger are different views that a
 * real ledger would both read from one source. Chosen to cover every status, every decision/plan
 * tag, a suspense (uncategorized) entry and both accounts in the sample set.
 */
object SampleTransactions {

    private fun detail(
        id: String,
        payee: String,
        amount: com.moneymatters.app.core.money.Paise,
        date: String,
        time: String,
        account: String,
        category: String,
        status: TxnStatus,
        postings: List<Posting>,
        decision: DecisionKind? = null,
        plan: PlanKind? = null,
        reason: String? = null,
        audit: List<AuditEntry> = emptyList(),
    ) = TransactionDetail(id, payee, amount, date, time, account, category, status, postings, decision, plan, reason, audit)

    private fun expensePostings(category: String, account: String, amount: com.moneymatters.app.core.money.Paise) = listOf(
        Posting("Expenses:$category", debit = amount),
        Posting(account, credit = amount),
    )

    private val details = listOf(
        detail(
            "t1", "Swiggy", (-486).rupees, "19 Oct", "14:02", "HDFC Bank", "Dining", TxnStatus.Posted,
            expensePostings("Dining", "HDFC Bank", 486.rupees), DecisionKind.Want, PlanKind.Unplanned,
            reason = "Too tired to cook after a long day.",
            audit = listOf(AuditEntry("19 Oct, 14:02", "Entered via Quick Add")),
        ),
        detail(
            "t2", "Streaming subscription", (-649).rupees, "19 Oct", "09:10", "HDFC Bank", "Subscriptions", TxnStatus.PriorPeriodAdjustment,
            expensePostings("Subscriptions", "HDFC Bank", 649.rupees), DecisionKind.Want, PlanKind.Unplanned,
            reason = "Price rise in August was missed until this month's review.",
            audit = listOf(
                AuditEntry("19 Oct, 09:10", "Entered as a prior-period adjustment for August", before = "₹499 / mo", after = "₹649 / mo"),
            ),
        ),
        detail(
            "t3", "UPI payment · 98214XXXXX", (-250).rupees, "19 Oct", "08:15", "HDFC Bank", "Uncategorized", TxnStatus.Draft,
            listOf(Posting("Suspense", debit = 250.rupees), Posting("HDFC Bank", credit = 250.rupees)),
            audit = listOf(AuditEntry("19 Oct, 08:15", "Imported from statement · needs a category")),
        ),
        detail(
            "t4", "Movie tickets", (-700).rupees, "18 Oct", "20:45", "ICICI Rewards", "Entertainment", TxnStatus.Posted,
            expensePostings("Entertainment", "ICICI Rewards", 700.rupees), DecisionKind.Want, PlanKind.Unplanned,
            reason = "Weekend outing.",
            audit = listOf(AuditEntry("18 Oct, 20:47", "Entered via Quick Add")),
        ),
        detail(
            "t5", "BigBasket", (-2_140).rupees, "18 Oct", "11:20", "HDFC Bank", "Groceries", TxnStatus.Posted,
            expensePostings("Groceries", "HDFC Bank", 2_140.rupees), DecisionKind.Need, PlanKind.Planned,
            audit = listOf(AuditEntry("18 Oct, 11:20", "Entered via Quick Add")),
        ),
        detail(
            "t6", "Buy · Nifty 50 Index Fund", (-3_000).rupees, "16 Oct", "10:05", "HDFC Bank", "Investment", TxnStatus.Posted,
            listOf(Posting("Assets:Investments", debit = 3_000.rupees), Posting("HDFC Bank", credit = 3_000.rupees)),
            DecisionKind.Investment, PlanKind.Planned,
            audit = listOf(AuditEntry("16 Oct, 10:05", "Entered via Quick Add")),
        ),
        detail(
            "t7", "Electricity", (-1_800).rupees, "15 Oct", "09:00", "HDFC Bank", "Bills", TxnStatus.Reconciled,
            expensePostings("Bills", "HDFC Bank", 1_800.rupees), DecisionKind.Need, PlanKind.Planned,
            audit = listOf(
                AuditEntry("15 Oct, 09:00", "Entered via auto-debit import"),
                AuditEntry("17 Oct, 21:30", "Reconciled against the HDFC Bank statement"),
            ),
        ),
        detail(
            "t8", "Amazon", (-5_000).rupees, "12 Oct", "19:30", "HDFC Bank", "Shopping", TxnStatus.Posted,
            expensePostings("Shopping", "HDFC Bank", 5_000.rupees), DecisionKind.Want, PlanKind.Unplanned,
            reason = "Gift for a friend's housewarming.",
            audit = listOf(AuditEntry("12 Oct, 19:30", "Entered via Quick Add")),
        ),
        detail(
            "t9", "Fuel", (-1_200).rupees, "2 Oct", "08:40", "ICICI Rewards", "Transport", TxnStatus.Posted,
            expensePostings("Transport", "ICICI Rewards", 1_200.rupees), DecisionKind.Need, PlanKind.Planned,
            audit = listOf(AuditEntry("2 Oct, 08:40", "Entered via Quick Add")),
        ),
        detail(
            "t10", "ICICI card payment", (-10_324).rupees, "5 Sep", "10:00", "HDFC Bank", "Credit card", TxnStatus.Locked,
            listOf(Posting("ICICI Rewards", debit = 10_324.rupees), Posting("HDFC Bank", credit = 10_324.rupees)),
            DecisionKind.Need, PlanKind.Planned,
            audit = listOf(
                AuditEntry("5 Sep, 10:00", "Entered via Quick Add"),
                AuditEntry("1 Oct, 00:00", "September closed · entry locked"),
            ),
        ),
        detail(
            "t11", "Salary · Sep", 70_000.rupees, "1 Sep", "00:05", "HDFC Bank", "Income", TxnStatus.Locked,
            listOf(Posting("HDFC Bank", debit = 70_000.rupees), Posting("Income:Salary", credit = 70_000.rupees)),
            plan = PlanKind.Planned,
            audit = listOf(
                AuditEntry("1 Sep, 00:05", "Entered via recurring rule"),
                AuditEntry("1 Oct, 00:00", "September closed · entry locked"),
            ),
        ),
    )

    private fun TransactionDetail.toSummary() = TransactionSummary(
        id, chipFor(payee), payee, category, accountName, amount, status, postings, decision, plan,
        uncategorized = category == "Uncategorized",
    )

    private fun chipFor(payee: String) = payee.trimStart().take(1).uppercase()

    val byId: Map<String, TransactionDetail> = details.associateBy { it.id }

    val state = TransactionsState(
        groups = details.groupBy { it.dateLabel }.map { (date, items) -> DayGroup(date, items.map { it.toSummary() }) },
        accounts = details.map { it.accountName }.distinct(),
    )
}
