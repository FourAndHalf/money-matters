package com.moneymatters.app.feature.books

import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.feature.accounts.SampleAccounts
import com.moneymatters.app.feature.accounts.SampleLedgers
import com.moneymatters.app.feature.dashboard.HomeState
import com.moneymatters.app.feature.transactions.SampleTransactions

/** The sample pages bundled as one [BooksState], for previews and screen tests. */
object SampleBooks {
    private val accounts = SampleAccounts.state

    val state = BooksState(
        home = HomeState(
            dateLabel = "Tuesday, 20 October",
            netWorth = accounts.netWorth,
            assetsTotal = accounts.assetsTotal,
            liabilitiesTotal = accounts.liabilitiesTotal,
            periodLabel = "Since 1 Oct",
            income = 72_000.rupees,
            spent = 34_326.rupees,
            accounts = accounts.groups.flatMap { it.items }.filter { it.kind in setOf(AccountKind.Bank, AccountKind.Cash, AccountKind.CreditCard) }.map { it.copy(isPinned = true) },
            recent = SampleTransactions.state.groups.flatMap { it.items }.take(5),
            draftCount = 1,
        ),
        accounts = accounts,
        ledgers = SampleLedgers.byAccountId,
        transactions = SampleTransactions.state,
        details = SampleTransactions.byId,
    )
}
