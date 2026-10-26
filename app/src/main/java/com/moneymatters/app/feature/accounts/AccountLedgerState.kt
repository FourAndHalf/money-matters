package com.moneymatters.app.feature.accounts

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.money.Paise

/** Everything the Account ledger page (P09) renders for one account. */
@Immutable
data class AccountLedgerState(
    val accountId: String,
    val accountName: String,
    val accountTypeLabel: String,
    val currentBalance: Paise,
    val isLiability: Boolean,
    val periodLabel: String,
    val availablePeriods: List<String>,
    val unreconciledCount: Int,
    /** One section per statement cycle for a credit card; a single untitled section otherwise. */
    val sections: List<LedgerSection>,
)

enum class EntryStatus { Draft, Reconciled, PriorPeriod }

/** A group of entries. [title] and [subtotal] are null for accounts with no statement cycle. */
@Immutable
data class LedgerSection(val title: String? = null, val subtotal: Paise? = null, val entries: List<LedgerEntry>)

/** One posted (or draft) entry, newest first. [runningBalance] is the balance right after it posted. */
@Immutable
data class LedgerEntry(
    val id: String,
    val dateLabel: String,
    val chip: String,
    val payee: String,
    val meta: String,
    val amount: Paise,
    val runningBalance: Paise,
    val status: EntryStatus? = null,
    val isTransfer: Boolean = false,
)
