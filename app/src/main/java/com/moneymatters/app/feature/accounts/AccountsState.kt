package com.moneymatters.app.feature.accounts

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.ui.components.Tone

/** Everything the Accounts page (P08) renders. Pure data; amounts are always [Paise]. */
@Immutable
data class AccountsState(
    val netWorth: Paise,
    val assetsTotal: Paise,
    val liabilitiesTotal: Paise,
    val asOfLabel: String,
    val groups: List<AccountGroup>,
    val earmarks: List<Earmark>,
)

enum class AccountKind { Bank, Cash, CreditCard, Loan, FixedDeposit, Investment }

/** One row in the account tree. [balance] is unsigned; [isLiability] decides the sign shown. */
@Immutable
data class AccountSummary(
    val id: String,
    val kind: AccountKind,
    val name: String,
    val subtitle: String,
    val balance: Paise,
    val isLiability: Boolean = false,
    val badge: AccountBadge? = null,
)

@Immutable
data class AccountBadge(val text: String, val tone: Tone)

/** A section of the account tree, grouped by type (design/plan.html → P08: "grouped by type"). */
@Immutable
data class AccountGroup(val title: String, val items: List<AccountSummary>)

/** A sinking fund earmarked inside a liquid balance, e.g. "Diwali gifts" (plan.html → P08). */
@Immutable
data class Earmark(val name: String, val saved: Paise, val target: Paise)
