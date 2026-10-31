package com.moneymatters.app.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Gives Reports the whole ledger, live, and the investor's month start day. The statements themselves are pure functions of it. */
class ReportsViewModel(ledger: LedgerRepository, val monthStartDay: () -> Int) : ViewModel() {
    val snapshot: StateFlow<LedgerSnapshot?> = ledger.snapshot().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
